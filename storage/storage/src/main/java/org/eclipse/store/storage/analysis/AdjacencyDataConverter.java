package org.eclipse.store.storage.analysis;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

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

import org.eclipse.serializer.util.logging.Logging;
import org.eclipse.store.storage.types.StorageAdjacencyDataExporter;
import org.eclipse.store.storage.types.StorageAdjacencyDataExporter.AdjacencyFiles;
import org.slf4j.Logger;



/**
 * Converts the data exported from {@link StorageAdjacencyDataExporter}
 * to different formats used for further processing.
 * These are a set of all references in an input file and
 * a map of all and there reverencing parents objects.
 */
public interface AdjacencyDataConverter
{
	/**
	 * Start the data conversion.
	 * 
	 * @return ConvertedAdjacencyFiles object providing path's to the generated files.
	 */
	public ConvertedAdjacencyFiles convert();
	
	/**
	 * Holds the path's to adjacency data files produced by the {@link AdjacencyDataConverter}.
	 */
	public interface ConvertedAdjacencyFiles
	{
		 public List<Path> getReferenceSets();
		 
		 public List<Path> getReverseReferenceMaps();
	}
		
	public interface Configuration
	{

	}
	
	public static AdjacencyDataConverter New(final List<AdjacencyFiles > adjacencyFiles)
	{
		return new AdjacencyDataConverter.Default(adjacencyFiles);
	}

	public static class Default implements AdjacencyDataConverter
	{
		public static class DefaultConvertedAdjacencyFiles implements ConvertedAdjacencyFiles
		{
			private final List<Path> referenceSets;
			private final List<Path> reverseReferenceMaps;

			public DefaultConvertedAdjacencyFiles(final List<Path> referenceSets, final List<Path> referenceMaps)
			{
				this.referenceSets = referenceSets;
				this.reverseReferenceMaps = referenceMaps;
			}

			@Override
			public final List<Path> getReferenceSets()
			{
				return this.referenceSets;
			}

			@Override
			public final List<Path> getReverseReferenceMaps()
			{
				return this.reverseReferenceMaps;
			}
		}
		
		public static class DefaultConfiguration implements Configuration
		{
			private final static int THREAD_MINIMUM =  1;
			private final static int THREAD_MAXIMUM = 12;
			
			private final int mapLoaders;
			private final int setCreators;
			private final int reverseMapCreators;
			private final int threadsTotal;
				
			/**
			 * Create a configuration object using default values.
			 */
			public DefaultConfiguration()
			{
				super();
				this.mapLoaders = 3;
				this.setCreators = 3;
				this.reverseMapCreators = 3;
				
				this.threadsTotal = this.mapLoaders + this.setCreators + this.reverseMapCreators;
			}
			
			/**
			 * Create a new Configuration for the AdjacencyDataConverter.
			 * This config requires a minimum of one thread and a maximum of 12 for each configuration value.
			 * 
			 * @param mapLoaders The number of threads used to load adjacency maps during initialisation.
			 * @param setCreators The number of threads used to create adjacency sets during initialisation.
			 * @param reverseMapCreators The number of threads used to create reverse adjacency maps during initialisation.
			 */
			public DefaultConfiguration(
				final int mapLoaders,
				final int setCreators,
				final int reverseMapCreators)
			{
				super();
								
				this.mapLoaders = this.verifyThreadCount(mapLoaders);
				this.setCreators = this.verifyThreadCount(setCreators);
				this.reverseMapCreators = this.verifyThreadCount(reverseMapCreators);
				
				this.threadsTotal = this.mapLoaders + this.setCreators + this.reverseMapCreators;
			}

			public final int getMapLoaders()
			{
				return this.mapLoaders;
			}

			public final int getSetCreators()
			{
				return this.setCreators;
			}

			public final int getThreadsTotal()
			{
				return this.threadsTotal;
			}
			
			public final int getReverseMapCreators()
			{
				return this.reverseMapCreators;
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
		
		private final static Logger logger = Logging.getLogger(MissingObjectsSearch.class);
		
		private final DefaultConfiguration configuration;
		private final ThreadFactory        threadFactory;
		private final List<AdjacencyFiles> adjacencyFiles;
		private final int                  fileCount;
		private final AtomicInteger        setsCreated        = new AtomicInteger();
		private final AtomicInteger        reverseMapsCreated = new AtomicInteger();
		
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////
		
		public Default(final List<AdjacencyFiles > adjacencyFiles)
		{
			this(adjacencyFiles, new DefaultConfiguration());
		}
		
		public Default(final List<AdjacencyFiles > adjacencyFiles, final DefaultConfiguration configuration)
		{
			this.adjacencyFiles = adjacencyFiles;
			this.configuration = configuration;
		
			this.threadFactory = new ThreadFactory()
			{
				final AtomicInteger counter = new AtomicInteger();
				
				@Override
				public Thread newThread(final Runnable r)
				{
					return new Thread(r, "Eclipse-Store-AdjacencyDataConverter-WorkerThread-" + this.counter.getAndIncrement());
				}};
			
			int tempFileCount = 0;
			for(final AdjacencyFiles channel : this.adjacencyFiles)
			{
				tempFileCount += channel.get().size();
			}
			this.fileCount = tempFileCount;
		}
		
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////
	
		public static Path derivePath(final Path other, final String newExtension)
		{
			final String fn = other.toString();
			final int index = fn.lastIndexOf('.');
			final String newName = fn.substring(0, index) + newExtension;
			return Paths.get(newName);
		}
		
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////
		
		@Override
		public ConvertedAdjacencyFiles convert()
		{
			final ExecutorService executor = Executors.newFixedThreadPool(this.configuration.getThreadsTotal() , this.threadFactory);
			
			final LinkedBlockingQueue<Path> adjacencyMapsPathsQueue     = new LinkedBlockingQueue<>();
			final LinkedBlockingQueue<AdjacencyMap> adjacencyMapsQueue1 = new LinkedBlockingQueue<>(this.configuration.getSetCreators());
			final LinkedBlockingQueue<AdjacencyMap> adjacencyMapsQueue2 = new LinkedBlockingQueue<>(this.configuration.getReverseMapCreators());
					
			for(final AdjacencyFiles channel : this.adjacencyFiles)
			{
				for(final Entry<Long, Path> entry : channel.get().entrySet())
				{
					adjacencyMapsPathsQueue.add(entry.getValue());
				}
			}
			
			final List<CompletableFuture<Void>> futures = new ArrayList<>();
									
			for(int i = 0; i < this.configuration.getMapLoaders()  ; i++)
			{
				futures.add(
					CompletableFuture
					.runAsync(() -> Default.adjacencyMapLoader(adjacencyMapsPathsQueue, List.of(adjacencyMapsQueue1, adjacencyMapsQueue2)), executor)
					.exceptionally((t) -> shutdownExceptional(executor, t))
				);
			}
			
			final List<Path> referenceMaps = Collections.synchronizedList(new ArrayList<>(this.fileCount));
			for(int i = 0; i < this.configuration.getReverseMapCreators(); i++)
			{
				futures.add(
					CompletableFuture
					.runAsync(() ->  referenceMaps.addAll(this.reverseReferenceMapCreator(adjacencyMapsQueue1)), executor)
					.exceptionally((t) -> shutdownExceptional(executor, t))
				);
			}
			
			final List<Path> referenceSets = Collections.synchronizedList(new ArrayList<>(this.fileCount));
			for(int i = 0; i < this.configuration.getSetCreators(); i++)
			{
				futures.add(
					CompletableFuture
					.runAsync(() -> referenceSets.addAll(this.referenceSetCreator(adjacencyMapsQueue2)), executor)
					.exceptionally((t) -> shutdownExceptional(executor, t))
				);
			}
			
											
			final CompletableFuture<Void> createSetsStage = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
				
			try
			{
				createSetsStage.get();
			}
			catch(InterruptedException | ExecutionException e)
			{
				logger.error("prepareReferenceSets failed: ", e);
			}
			finally
			{
				shutdown(executor);
			}
			
			return new DefaultConvertedAdjacencyFiles(referenceSets, referenceMaps);
		}
		
		private static Void shutdownExceptional(final ExecutorService ex, final Throwable t)
		{
			if(ex != null && !ex.isTerminated())
			{
				ex.shutdownNow();
				logger.error("Shutting down exceptionally: ", t);
				throw new RuntimeException("abnormal termination", t);
			}
			
			return null;
		}
		
		private static Void shutdown(final ExecutorService ex)
		{
			if(ex != null && !ex.isTerminated())
			{
				ex.shutdownNow();
			}
			return null;
		}
		
		private List<Path> referenceSetCreator(
			final LinkedBlockingQueue<AdjacencyMap> adjacencyMapsQueue)
		{
			final List<Path> createdFiles = new ArrayList<>(this.fileCount);
			
			while(this.setsCreated.getAndIncrement() < this.fileCount)
			{
				try
				{
					final AdjacencyMap map = adjacencyMapsQueue.take();
					final AdjacencySet rf = new AdjacencySet(map);
					
					rf.store();
					rf.unload();
					
					createdFiles.add(rf.getPath());
					logger.debug("created reference set {}", rf.getPath());
				}
				catch(final InterruptedException e)
				{
					//stop processing
					logger.debug("Reference set creator task stopped after interruption");
					return null;
				}
			}
			
			logger.debug("Reference set creator task finished successfully.");
			return createdFiles;
		}
		
		private List<Path> reverseReferenceMapCreator(final LinkedBlockingQueue<AdjacencyMap> adjacencyMapsQueue)
		{
			final List<Path> createdFiles = new ArrayList<>(this.fileCount);
			
			while(this.reverseMapsCreated.getAndIncrement() < this.fileCount)
			{
				try
				{
					final AdjacencyMap adjacencyMap = adjacencyMapsQueue.take();
					
					final TreeMap<Long, long[]> map = adjacencyMap.getMap();
					
					final Path path = derivePath(adjacencyMap.getPath(), ".brf");
					
	
					final HashMap<Long, Set<Long>> backRefs = new HashMap<>();
					
					map.forEach((k,v) ->
					{
						for(final long r : v)
						{
							backRefs.computeIfAbsent(r, x -> new HashSet<>()).add(k);
						}
					});
					
					final AtomicLong backRefsCount = new AtomicLong();
					backRefs.values().forEach(v -> backRefsCount.addAndGet(v.size()));
																	
					AdjacencyMap.serialize(backRefs, path, backRefs.size(), backRefsCount.get());
	
					createdFiles.add(path);
					
					logger.debug("created reverse reference map {}", path);
				}
				catch(final InterruptedException e)
				{
					//stop processing
					logger.debug("Reverse reference map creator task stopped after interruption.");
					return null;
				}
			}
			
			logger.debug("Reverse reference map creator task finished successfully.");
			return createdFiles;
		}
		
		protected static void adjacencyMapLoader(
			final LinkedBlockingQueue<Path> adjacencyMapsPathsQueue,
			final List<LinkedBlockingQueue<AdjacencyMap>> outputQueues)
		{
			while(!adjacencyMapsPathsQueue.isEmpty())
			{
				try
				{
					final Path path = adjacencyMapsPathsQueue.take();
					final TreeMap<Long, long[]> refMap = AdjacencyMap.deserializeReferenceMap(path);
	
					for(final LinkedBlockingQueue<AdjacencyMap> queue : outputQueues)
					{
						queue.put(new AdjacencyMap(refMap, path));
					}
					
					logger.debug("loaded reference map {}", path);
				}
				catch(final InterruptedException e)
				{
					//stop processing
					logger.debug("Adjacency map loader task stopped after interruption");
					return;
				}
			}
	
			logger.debug("Adjacency map loader task finished successfully.");
		}
	}
}
