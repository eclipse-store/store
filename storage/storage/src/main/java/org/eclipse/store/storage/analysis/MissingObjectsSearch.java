package org.eclipse.store.storage.analysis;

/*-
 * #%L
 * EclipseStore Storage
 * %%
 * Copyright (C) 2023 - 2025 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.serializer.util.logging.Logging;
import org.eclipse.store.storage.types.StorageAdjacencyDataExporter.AdjacencyFiles;
import org.slf4j.Logger;

/**
 * Searched the collected object reference data form the {@link org.eclipse.store.storage.types.StorageAdjacencyDataExporter}
 * for missing objects.
 */
public interface MissingObjectsSearch {
	/**
	 * Search the reference data for missing objects.
	 * 
	 * @return a set of the missing objets objectIDs.
	 */
	public MissingObjects searchMissingEntities();
	
	public interface Configuration {}
	
	/**
	 * Create a new MissingObjectsSearch.Default instance.
	 * 
	 * @param adjacencyFiles the input adjacency files.
	 * @param referenceSetsPaths list of path's to the reference set files.
	 * @param configuration configuration, can be null.
	 * @return a new MissingObjectsSearch.Default instance.
	 */
	public static MissingObjectsSearch New(
		final List<AdjacencyFiles> adjacencyFiles,
		final List<Path> referenceSetsPaths,
		final MissingObjectsSearch.Default.DefaultConfiguration configuration)
	{
		if(configuration != null)
			return new MissingObjectsSearch.Default(adjacencyFiles, referenceSetsPaths, configuration);
		
		return new MissingObjectsSearch.Default(adjacencyFiles, referenceSetsPaths);
	}
	
	public final class Default implements MissingObjectsSearch
	{
		public static final class DefaultConfiguration implements MissingObjectsSearch.Configuration
		{
			private final static int THREAD_MINIMUM =  1;
			private final static int THREAD_MAXIMUM = 12;
			private static final int QUEUE_SIZE = 1;
						
			private final int reduceStage_MapLoaders;
			private final int reduceStage_Initializers;
			private final int reduceStage_SetReducers;
			private final int reduceStage_TotalThreads;

			/**
			 * Create a configuration object using default values.
			 */
			public DefaultConfiguration()
			{
				super();
							
				this.reduceStage_MapLoaders = 1;
				this.reduceStage_Initializers = 2;
				this.reduceStage_SetReducers = 4;
								
				this.reduceStage_TotalThreads = this.reduceStage_MapLoaders + this.reduceStage_Initializers + this.reduceStage_SetReducers + 1;
			}

			/**
			 * Create a new Configuration for the MissingObjectsSearch.
			 * This config requires a minimum of one thread and a maximum of 12 for each configuration value.
			 * 
			 * @param reduceStage_MapLoaders The number of threads used to load adjacency maps during search stage.
			 * @param reduceStage_Initializers The number of threads used to load adjacency sets during search stage.
			 * @param reduceStage_SetReducers The number of threads used to process adjacency sets during search stage.
			 */
			public DefaultConfiguration(
				final int reduceStage_MapLoaders,
				final int reduceStage_Initializers,
				final int reduceStage_SetReducers)
			{
				super();
												
				this.reduceStage_MapLoaders = this.verifyThreadCount(reduceStage_MapLoaders);
				this.reduceStage_Initializers = this.verifyThreadCount(reduceStage_Initializers);
				this.reduceStage_SetReducers = this.verifyThreadCount(reduceStage_SetReducers);
				
				this.reduceStage_TotalThreads = this.reduceStage_MapLoaders + this.reduceStage_Initializers + this.reduceStage_SetReducers + 1;
			}
			
			public final int getReduceStage_MapLoaders()
			{
				return this.reduceStage_MapLoaders;
			}

			public final int getReduceStage_Initializers()
			{
				return this.reduceStage_Initializers;
			}

			public final int getReduceStage_SetReducers()
			{
				return this.reduceStage_SetReducers;
			}

			public final int getReduceStage_TotalThreads()
			{
				return this.reduceStage_TotalThreads;
			}

			public final int getQUEUE_SIZE()
			{
				return DefaultConfiguration.QUEUE_SIZE;
			}
			
			private int verifyThreadCount(final int numThreads)
			{
				if(numThreads < THREAD_MINIMUM)
				{
					logger.info("The requested thread count {} is below the required minimum, using minimum default value of {} !", numThreads, THREAD_MINIMUM);
					return THREAD_MINIMUM;
				}
				
				if(numThreads > THREAD_MAXIMUM)
				{
					logger.info("The requested thread count {} is greater then the maximum, using maximum default value of {} !", numThreads, THREAD_MAXIMUM);
					return THREAD_MAXIMUM;
				}
				
				return numThreads;
			}
			
		}
								
		///////////////////////////////////////////////////////////////////////////
		// Fields //
		///////////
		
		private final static Logger logger = Logging.getLogger(MissingObjectsSearch.class);
		
		private final DefaultConfiguration configuration;
		
		private final List<AdjacencyFiles> adjacencyFiles;
		private final List<Path> referenceSetsPaths;
		private long  fileCount;
						
		private final ThreadFactory threadFactory;
		
		private final AtomicInteger mapsTaken = new AtomicInteger();
		
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		/**
		 * Create a new MissingObjectsSearch.Default instance
		 * using the default configuration.
		 * 
		 * @param adjacencyFiles the input adjacency files.
		 * @param referenceSetsPaths list of path's to the reference set files.
         */
		public Default(final List<AdjacencyFiles > adjacencyFiles, final List<Path> referenceSetsPaths)
		{
			this.adjacencyFiles = adjacencyFiles;
			this.referenceSetsPaths = referenceSetsPaths;
			this.configuration = new DefaultConfiguration();
		
			this.threadFactory = new ThreadFactory()
			{
				final AtomicInteger counter = new AtomicInteger();
				
				@Override
				public Thread newThread(final Runnable r)
				{
					return new Thread(r, "Eclipse-Store-WorkerThread-" + this.counter.getAndIncrement());
				}
			};
			
			for(final AdjacencyFiles channel : this.adjacencyFiles)
			{
				this.fileCount += channel.get().size();
			}
		}
		
		/**
		 * Create a new MissingObjectsSearch.Default instance.
		 * 
		 * @param adjacencyFiles the input adjacency files.
		 * @param referenceSetsPaths list of path's to the reference set files.
		 * @param configuration configuration, can be null.
         */
		public Default(final List<AdjacencyFiles> adjacencyFiles, final List<Path> referenceSetsPaths, final DefaultConfiguration configuration)
		{
			this.adjacencyFiles = adjacencyFiles;
			this.referenceSetsPaths = referenceSetsPaths;
			this.configuration = configuration;
		
			this.threadFactory = new ThreadFactory()
			{
				final AtomicInteger counter = new AtomicInteger();
				
				@Override
				public Thread newThread(final Runnable r)
				{
					return new Thread(r, "Eclipse-Store-WorkerThread-" + this.counter.getAndIncrement());
				}
			};
			
			for(final AdjacencyFiles channel : this.adjacencyFiles)
			{
				this.fileCount += channel.get().size();
			}
		}

		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////
		
		@Override
		public MissingObjects searchMissingEntities()
		{
			final LinkedBlockingQueue<AdjacencySet> refSetsQueueDone = this.searchMissingObjectIDs(this.referenceSetsPaths);
			
			return new MissingObjects.Default(this.gatherMissingObjectIDs(refSetsQueueDone));
		}
		
		private Set<Long> gatherMissingObjectIDs(final LinkedBlockingQueue<AdjacencySet> refSetsQueue)
		{
			final Set<Long> missingObjectIds = new HashSet<>();
			refSetsQueue.forEach((set) ->
			{
				set.load(false);
				if(!set.getReferences().isEmpty())
				{
					logger.info("file {} has {} missing object(s)!", set.getPath().getFileName(), set.getReferences().size());
				}
				missingObjectIds.addAll(set.getReferences());
				set.unload();
			});

			return missingObjectIds;
		}
			
		private static Void shutdownExceptional(final ExecutorService ex, final Throwable t) {
			if(ex != null && !ex.isTerminated())
			{
				ex.shutdownNow();
				logger.error("Shutting down exceptionally: ", t);
				throw new RuntimeException("abnormal termination", t);
			}
			
			return null;
		}
		
		private static Void shutdown(final ExecutorService ex) {
			if(ex != null && !ex.isTerminated())
			{
				ex.shutdownNow();
			}
			return null;
		}
					
		private LinkedBlockingQueue<AdjacencySet> searchMissingObjectIDs(final List<Path> referenceSetsPaths)
		{
			final ExecutorService executor = Executors.newFixedThreadPool(this.configuration.getReduceStage_TotalThreads(), this.threadFactory);
		
			
			final LinkedBlockingQueue<AdjacencySet> refSetsQueue = new LinkedBlockingQueue<>();
			referenceSetsPaths.forEach(p -> refSetsQueue.add(new AdjacencySet(p)));
			
			final LinkedBlockingQueue<Path>         adjacentMapsPathsQueue      = new LinkedBlockingQueue<>();
			final LinkedBlockingQueue<AdjacencyMap> adjacencyMapsQueue          = new LinkedBlockingQueue<>(this.configuration.getReduceStage_MapLoaders());
			final LinkedBlockingQueue<AdjacencySet> initOutQueue                = new LinkedBlockingQueue<>(this.configuration.getQUEUE_SIZE());
			final LinkedBlockingQueue<AdjacencySet> adjacencySetsQueueFinished  = new LinkedBlockingQueue<>();
			LinkedBlockingQueue<AdjacencySet> reducerInQueue;  //created later on demand
			
			for(final AdjacencyFiles channel : this.adjacencyFiles)
			{
				for(final Entry<Long, Path> entry : channel.get().entrySet())
				{
					adjacentMapsPathsQueue.add(entry.getValue());
				}
			}
			
			final List<CompletableFuture<Void>> futures = new ArrayList<>();
			
			for(int i = 0; i < this.configuration.getReduceStage_MapLoaders(); i++)
			{
				futures.add(
					CompletableFuture
						.runAsync(() -> AdjacencyDataConverter.Default.adjacencyMapLoader(adjacentMapsPathsQueue, List.of(adjacencyMapsQueue)), executor)
						.exceptionally((t) -> shutdownExceptional(executor, t))
				);
			}
					
			for(int i = 0; i < this.configuration.getReduceStage_Initializers(); i++)
			{
				futures.add(
					CompletableFuture
						.runAsync(() -> this.stageInit(refSetsQueue, initOutQueue), executor)
						.exceptionally((t) -> shutdownExceptional(executor, t))
				);
			}
								
			reducerInQueue = initOutQueue;
			for(int i = 0; i < this.configuration.getReduceStage_SetReducers(); i++) {
				
				final LinkedBlockingQueue<AdjacencySet> inQueue = reducerInQueue;
				final LinkedBlockingQueue<AdjacencySet> outQueue = new LinkedBlockingQueue<>(this.configuration.getQUEUE_SIZE());
				
				
				futures.add(
					CompletableFuture
						.runAsync(() -> this.stageReduce(adjacencyMapsQueue, inQueue, outQueue), executor)
						.exceptionally((t) -> shutdownExceptional(executor, t))
				);
				
				reducerInQueue = outQueue;
			}
						
			final LinkedBlockingQueue<AdjacencySet> stageEndInQueue = reducerInQueue;
			futures.add(
				CompletableFuture
				.runAsync(() -> this.stageEnd(stageEndInQueue, refSetsQueue, adjacencySetsQueueFinished), executor)
				.whenComplete((v, t) -> {
					if(t!=null) {
						shutdownExceptional(executor, t);
					} else {
						shutdown(executor);
					}
				})
			);
								
			final CompletableFuture<?> reduceStage = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
			
			try
			{
				reduceStage.get();
			}
			catch(InterruptedException | ExecutionException e)
			{
				logger.error("searchMissingObjectIDs failed: ", e);
				throw new RuntimeException(e);
			}
			finally
			{
				executor.shutdownNow();
			}
							
			return adjacencySetsQueueFinished;
		}
					
		private void stageInit(
			final LinkedBlockingQueue<AdjacencySet> queueIn,
			final LinkedBlockingQueue<AdjacencySet> queueOut)
		{
			do
			{
				try
				{
					final AdjacencySet set = queueIn.take();
					set.load(true);
					queueOut.put(set);
				}
				catch(final InterruptedException e)
				{
					//stop processing
					logger.debug("Init stage thread ending stopped of interruption");
					return;
				}
				catch(final Exception e)
				{
					throw new RuntimeException(e);
				}
			} while(true);
		}
				
		private void stageReduce(
				final LinkedBlockingQueue<AdjacencyMap> adjacencyMapsQueue,
				final LinkedBlockingQueue<AdjacencySet> queueIn,
				final LinkedBlockingQueue<AdjacencySet> queueOut)
		{
			while(true)
			{
				if(this.mapsTaken.getAndIncrement() < this.fileCount)
				{
					try
					{
						final AdjacencyMap map = adjacencyMapsQueue.take();
										
						long counter = 0;
						
						while(counter < this.fileCount)
						{

							final AdjacencySet set = queueIn.take();
							if(!set.isEmpty())
							{
								set.reduce(map);
							}
														
							queueOut.put(set);
							counter++;
						}
					}
					catch(final InterruptedException e)
					{
						//stop processing
						logger.debug("Reduce stage thread stopped because of interruption");
						return;
					}
					catch(final Exception e)
					{
						throw new RuntimeException(e);
					}
				}
				else
				{
					//simply forward
					try
					{
						final AdjacencySet set = queueIn.take();
						queueOut.put(set);
					}
					catch(final InterruptedException e)
					{
						//stop processing
						logger.debug("Reduce stage thread stopped because of interruption");
						return;
					}
					catch(final Exception e)
					{
						throw new RuntimeException(e);
					}
				}
			}
		}
		
		private void stageEnd(final LinkedBlockingQueue<AdjacencySet> queueIn,
				final LinkedBlockingQueue<AdjacencySet> queueOut,
				final LinkedBlockingQueue<AdjacencySet> queueDone)
		{
			long processedFiles = 0;
			final List<AdjacencySet> tmp = new ArrayList<>();
			
			do
			{
				try
				{
					final AdjacencySet set = queueIn.take();
					
					set.store();
					set.unload();
												
					tmp.add(set);
										
					//every time a set reaches this point it was reduced by
					//n reduces stages. We are done as soon as very file was
					//reduced by every file, which results in a total of
					//files^2 reduce operations.
					processedFiles += this.configuration.getReduceStage_SetReducers();
					
					if(tmp.size() == this.fileCount)
					{
						logger.debug("Processed {} of {}", processedFiles, this.fileCount * this.fileCount);
												
						if(processedFiles >= (this.fileCount * this.fileCount))
						{
							if(queueOut.isEmpty())
							{
								queueDone.addAll(tmp);
								return;
							} else
								throw new RuntimeException("Something went wrong, there are more items to be processed then expected.");
						}
						else
						{
							queueOut.addAll(tmp);
							tmp.clear();
						}
					}
					
				}
				catch(final InterruptedException e)
				{
					//stop processing
					logger.debug("End stage thread stopped because of interruption");
					return;
				}
				catch(final Exception e)
				{
					throw new RuntimeException(e);
				}
				
			}
			while(true);
		}
											
	}
	
}
