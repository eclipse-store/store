package org.eclipse.store.storage.embedded.types;

/*-
 * #%L
 * EclipseStore Storage Embedded
 * %%
 * Copyright (C) 2023 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import static org.eclipse.serializer.util.X.notNull;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.eclipse.serializer.afs.types.AFile;
import org.eclipse.serializer.collections.EqHashTable;
import org.eclipse.serializer.collections.XSort;
import org.eclipse.serializer.collections.types.XGettingEnum;
import org.eclipse.serializer.collections.types.XGettingTable;
import org.eclipse.serializer.monitoring.MonitoringManager;
import org.eclipse.serializer.persistence.binary.types.Binary;
import org.eclipse.serializer.persistence.types.Persistence;
import org.eclipse.serializer.persistence.types.PersistenceAcceptor;
import org.eclipse.serializer.persistence.types.PersistenceManager;
import org.eclipse.serializer.persistence.types.PersistenceObjectRegistry;
import org.eclipse.serializer.persistence.types.PersistenceRootReference;
import org.eclipse.serializer.persistence.types.PersistenceRoots;
import org.eclipse.serializer.persistence.types.PersistenceRootsProvider;
import org.eclipse.serializer.persistence.types.PersistenceRootsView;
import org.eclipse.serializer.persistence.types.PersistenceShutdownReleasable;
import org.eclipse.serializer.persistence.types.PersistenceTypeDictionaryExporter;
import org.eclipse.serializer.persistence.types.Storer;
import org.eclipse.serializer.persistence.types.Unpersistable;
import org.eclipse.serializer.reference.LazyReferenceManager;
import org.eclipse.serializer.reference.Swizzling;
import org.eclipse.serializer.reference.UsageMarkable;
import org.eclipse.serializer.typing.KeyValue;
import org.eclipse.serializer.util.logging.Logging;
import org.eclipse.store.storage.exceptions.StorageExceptionConsistency;
import org.eclipse.store.storage.monitoring.ObjectRegistryMonitor;
import org.eclipse.store.storage.monitoring.StorageManagerMonitor;
import org.eclipse.store.storage.types.Database;
import org.eclipse.store.storage.types.StorageAdjacencyDataExporter.AdjacencyFiles;
import org.eclipse.store.storage.types.StorageConfiguration;
import org.eclipse.store.storage.types.StorageConnection;
import org.eclipse.store.storage.types.StorageEntityCacheEvaluator;
import org.eclipse.store.storage.types.StorageEntityTypeExportFileProvider;
import org.eclipse.store.storage.types.StorageEntityTypeExportStatistics;
import org.eclipse.store.storage.types.StorageEntityTypeHandler;
import org.eclipse.store.storage.types.StorageIdAnalysis;
import org.eclipse.store.storage.types.StorageIntegrityCheckResult;
import org.eclipse.store.storage.types.StorageKillable;
import org.eclipse.store.storage.types.StorageLiveFileProvider;
import org.eclipse.store.storage.types.StorageManager;
import org.eclipse.store.storage.types.StorageRawFileStatistics;
import org.eclipse.store.storage.types.StorageSystem;
import org.eclipse.store.storage.types.StorageTypeDictionary;
import org.slf4j.Logger;


/**
 * {@link StorageManager} sub type for usage as an embedded storage solution.
 * <p>
 * "Embedded" is meant in the context that a database is managed in the same process that uses this database,
 * as opposed to the database being managed by a different process that the using process connects to via network
 * communication. That would be a "remote" or "standalone" storage process.
 * <p>
 * Compared to a generic {@link StorageManager}, an {@code EmbeddedStorageManager} additionally takes care of
 * <ul>
 *   <li>resolving and synchronizing the persistent root graph with the application-side root state on
 *       {@link #start()},</li>
 *   <li>controlling the lifecycle of the global {@link org.eclipse.serializer.reference.LazyReferenceManager}
 *       so that lazy references can be resolved transparently while the storage is running,</li>
 *   <li>linking back the persistence and storage layers (object registry, type dictionary, monitoring) so
 *       that a single instance is sufficient to operate the database from application code.</li>
 * </ul>
 * <p>
 * Instances are typically obtained through {@link EmbeddedStorage} or {@link EmbeddedStorageFoundation} rather
 * than constructed directly.
 *
 * @see EmbeddedStorage
 * @see EmbeddedStorageFoundation
 */
public interface EmbeddedStorageManager extends StorageManager
{
	/**
	 * Starts the embedded storage and returns this instance.
	 * <p>
	 * On the first invocation, the storage backend is brought up, the persistent root graph is loaded
	 * (or initialized for an empty database) and synchronized with the application-side roots, and the
	 * {@link org.eclipse.serializer.reference.LazyReferenceManager} is enlisted with this manager as one of
	 * its controllers. If startup fails, every part started so far is rolled back before the original error
	 * is re-thrown.
	 *
	 * @return this manager, allowing fluent chaining.
	 */
	@Override
	public EmbeddedStorageManager start();


	/**
	 * Pseudo-constructor method to create a new {@link EmbeddedStorageManager} default instance.
	 * <p>
	 * The returned manager is not started yet. Use {@link #start()} or one of the convenience entry points on
	 * {@link EmbeddedStorage}/{@link EmbeddedStorageFoundation} to start it.
	 *
	 * @param database             the {@link Database} this manager belongs to. Must not be {@code null}.
	 *
	 * @param configuration        the active {@link StorageConfiguration}. Must not be {@code null}.
	 *
	 * @param connectionFoundation the {@link EmbeddedStorageConnectionFoundation} used to create new
	 *        connections to the storage. Must not be {@code null}.
	 *
	 * @param rootsProvider        the {@link PersistenceRootsProvider} that provides the application-defined
	 *        persistent roots. Must not be {@code null}.
	 *
	 * @param monitorManager       the {@link MonitoringManager} that receives this manager's monitoring
	 *        registrations.
	 *
	 * @return a new {@link EmbeddedStorageManager.Default} instance.
	 */
	public static EmbeddedStorageManager.Default New(
		final Database                               database            ,
		final StorageConfiguration                   configuration       ,
		final EmbeddedStorageConnectionFoundation<?> connectionFoundation,
		final PersistenceRootsProvider<?>            rootsProvider       ,
		final MonitoringManager                      monitorManager
	)
	{
		return new EmbeddedStorageManager.Default(
			notNull(database)            ,
			notNull(configuration)       ,
			notNull(connectionFoundation),
			notNull(rootsProvider)       ,
			monitorManager
		);
	}


	public final class Default
	extends UsageMarkable.Default
	implements EmbeddedStorageManager, Unpersistable, LazyReferenceManager.Controller
	{
		private final static Logger logger = Logging.getLogger(EmbeddedStorageManager.class);
		
		
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////

		private final Database                               database            ;
		private final StorageConfiguration                   configuration       ;
		private final StorageSystem                          storageSystem       ;
		private final EmbeddedStorageConnectionFoundation<?> connectionFoundation;
		private final PersistenceRootsProvider<?>            rootsProvider       ;
				
		private StorageConnection singletonConnection;

		private final MonitoringManager monitorManager;



		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		Default(
			final Database                               database            ,
			final StorageConfiguration                   configuration       ,
			final EmbeddedStorageConnectionFoundation<?> connectionFoundation,
			final PersistenceRootsProvider<?>            rootsProvider       ,
			final MonitoringManager                      monitorManager
		)
		{
			super();
			this.database             = database                               ;
			this.configuration        = configuration                          ;
			this.storageSystem        = connectionFoundation.getStorageSystem(); // to ensure consistency
			this.connectionFoundation = connectionFoundation                   ;
			this.rootsProvider        = rootsProvider                          ;
			this.monitorManager       = monitorManager                         ;
		}


		private synchronized StorageConnection singletonConnection()
		{
			if(this.singletonConnection == null)
			{
				this.singletonConnection = this.createConnection();
			}
			return this.singletonConnection;
		}



		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////
		
		@Override
		public final Database database()
		{
			return this.database;
		}

		@SuppressWarnings("unchecked")
		@Override
		public final <R> R root()
		{
			return (R)this.rootReference().get();
		}
		
		@Override
		public final <R> R setRoot(final R newRoot)
		{
			this.rootReference().setRoot(newRoot);
			
			return newRoot;
		}
		
		final PersistenceRootReference rootReference()
		{
			return this.rootsProvider.provideRoots().rootReference();
		}
		
		@Override
		public long storeRoot()
		{
			final PersistenceRootReference rootReference = this.rootReference();
			final Object                   root            = rootReference.get();
			
			// this construction is necessary to use the locking mechanism of the called storing methods
			if(root == null)
			{
				this.store(rootReference);
				return Swizzling.nullId();
			}
			
			return this.storeAll(rootReference, root)[1];
		}
		
		@Override
		public final PersistenceRootsView viewRoots()
		{
			return this.rootsProvider.provideRoots();
		}

		@Override
		public PersistenceManager<Binary> persistenceManager()
		{
			return this.singletonConnection().persistenceManager();
		}

		@Override
		public final Storer createStorer()
		{
			return this.singletonConnection().createStorer();
		}
		
		@Override
		public boolean mayRun()
		{
			return this.isRunning();
		}
				
		private void ensureActiveLazyReferenceManager(
			final LazyReferenceManager lazyReferenceManager         ,
			final boolean              lazyReferenceManagerIsRunning
		)
		{
			lazyReferenceManager.addController(this);
			if(lazyReferenceManagerIsRunning)
			{
				return;
			}
			
			lazyReferenceManager.start();
		}
		
		private void rollbackLazyReferenceManager(
			final LazyReferenceManager lazyReferenceManager
		)
		{
			lazyReferenceManager.removeController(this);
		}

		@Override
		public final synchronized EmbeddedStorageManager.Default start()
		{
			logger.info("Starting embedded storage manager");
			
			final LazyReferenceManager lazyReferenceManager = LazyReferenceManager.get();
			final boolean lazyReferenceManagerIsRunning = lazyReferenceManager.isRunning();
			
			this.storageSystem.start();
			
			try
			{
				this.ensureRequiredTypeHandlers();
				this.initialize();
				
				logger.info("Embedded storage manager initialized");
				
				// this depends on completed initialization
				this.ensureActiveLazyReferenceManager(lazyReferenceManager, lazyReferenceManagerIsRunning);
			}
			catch(final Throwable t)
			{
				try
				{
					this.rollbackLazyReferenceManager(lazyReferenceManager);
					this.monitorManager.shutdown();
					
					if(this.storageSystem instanceof StorageKillable)
					{
						((StorageKillable)this.storageSystem).killStorage(t);
					}
					else
					{
						this.storageSystem.shutdown();
					}
				}
				catch(final Throwable t1)
				{
					t1.addSuppressed(t);
					throw t1;
				}
				throw t;
			}
			
			this.monitorManager.registerMonitor(new StorageManagerMonitor(this));
			
			return this;
		}
		
		static final boolean isEqualRootEntry(final KeyValue<String, Object> e1, final KeyValue<String, Object> e2)
		{
			if(!e1.key().equals(e2.key()))
			{
				return false;
			}
			
			// Enum special case: enum holder arrays are not identical, only their content must be.
			if(Persistence.isEnumRootIdentifier(e1.key()))
			{
				return Arrays.equals((Object[])e1.value(), (Object[])e2.value());
			}
			
			// All non-Enum cases must have identical root instances.
			return e1.value() == e2.value();
		}
		
		private static EqHashTable<String, Object> normalize(final XGettingTable<String, Object> entries)
		{
			final EqHashTable<String, Object> preparedEntries = EqHashTable.New(entries);
			
			// order of enum entries can differ (sorted type dictionary vs. encountering order). Normalize here.
			preparedEntries.keys().sort(XSort::compare);
			
			return preparedEntries;
		}
		
				
		private void synchronizeRoots(final PersistenceRoots loadedRoots)
		{
			final EqHashTable<String, Object> loadedEntries  = normalize(loadedRoots.entries());
			final EqHashTable<String, Object> definedEntries = normalize(this.rootsProvider.provideRoots().entries());

			final boolean match = loadedEntries.equalsContent(definedEntries, EmbeddedStorageManager.Default::isEqualRootEntry);
			if(!match)
			{
				// change detected. Entries of loadedRoots must be updated/replaced
				loadedRoots.updateEntries(definedEntries);
			}

			/*
			 * If the loaded roots had to change in any way to match the runtime state of the application,
			 * it means that it has to be stored to update the persistent state to the current (changed) one.
			 * The loaded roots instance is the one that has to be stored to maintain the associated ObjectId,
			 * hence the entry synchronization instead of just storing the defined roots instance right away.
			 */
			
			// must update the roots provider with the loadedRoots instance for the same reason
			this.rootsProvider.updateRuntimeRoots(loadedRoots);
		}
		
		private void ensureRequiredTypeHandlers()
		{
			// make sure a functional type handler is present for every occurring type id or throw an exception.
			final StorageIdAnalysis  idAnalysis       = this.storageSystem.initializationIdAnalysis();
			final XGettingEnum<Long> occurringTypeIds = idAnalysis.occurringTypeIds();
			this.connectionFoundation.getTypeHandlerManager().ensureTypeHandlersByTypeIds(occurringTypeIds);
		}
		
		private PersistenceRoots loadExistingRoots(final StorageConnection initConnection)
		{
			return initConnection.persistenceManager().createLoader().loadRoots();
		}
		
		private PersistenceRoots validateEmptyDatabaseAndReturnDefinedRoots(final StorageConnection initConnection)
		{
			// no loaded roots is only valid if there is no data yet at all (no database files / content)
			final StorageRawFileStatistics statistics = initConnection.createStorageStatistics();
			if(statistics.liveDataLength() != 0)
			{
				throw new StorageExceptionConsistency("No roots found for existing data.");
			}

			return this.rootsProvider.provideRoots();
		}

		private void initialize()
		{
			try
			{
				final StorageConnection initConnection = this.createConnection();
				
				this.monitorManager.registerMonitor(new ObjectRegistryMonitor(initConnection.persistenceManager().objectRegistry()));

				PersistenceRoots loadedRoots = this.loadExistingRoots(initConnection);
				if(loadedRoots == null)
				{
					// gets stored below, no matter the changed state (which is initially false)
					loadedRoots = this.validateEmptyDatabaseAndReturnDefinedRoots(initConnection);
				}
				else
				{
					this.synchronizeRoots(loadedRoots);
					if(!loadedRoots.hasChanged())
					{
						//  abort before storing because there is no need to.
						return;
					}
				}

				if(this.connectionFoundation.writeController().isWritable())
				{
					logger.debug("Storing required root objects and constants");
					// any other case than a perfectly synchronous loaded roots instance needs to store
					initConnection.store(loadedRoots);
				}
				else
				{
					logger.warn("Updated root objects and constants can't be stored because storage is opened read only!");
				}

			}
			catch(final Exception e)
			{
				logger.error("Exception occurred while initializing embedded storage manager", e);
				
				throw e;
			}
		}
		
		@Override
		public final synchronized boolean shutdown()
		{
			// Release shutdown-aware instances (e.g. GigaMaps closing their vector index groups) while
			// the object registry is still populated and the storage system is still up.
			this.releaseShutdownParticipants();

			LazyReferenceManager.get().removeController(this);
			final boolean result = this.storageSystem.shutdown();
			if(!result)
			{
				// shutdown was interrupted; storage may still be partially running, so don't
				// invalidate cached connection/registry state on top of an indeterminate storage.
				return false;
			}
			// drop cached connection so a subsequent start() rebuilds it against the new task broker
			this.singletonConnection = null;
			final PersistenceObjectRegistry registry = this.connectionFoundation.getObjectRegistry();
			registry.truncateAll();
			Persistence.registerJavaConstants(registry);
			return true;
		}

		/**
		 * Invokes {@link PersistenceShutdownReleasable#releaseOnShutdown()} on every registered instance
		 * that opts into shutdown participation. Enumerates the object registry, which holds only
		 * already-materialized instances, so no {@code Lazy} subgraph is force-loaded. Best-effort: a
		 * failing participant is logged and skipped so it cannot abort shutdown of the rest or of storage.
		 * <p>
		 * Participants are collected during the registry iteration and released afterwards, outside of it:
		 * {@code releaseOnShutdown()} may block on application locks and await background-task termination,
		 * which must not happen while the object registry is being iterated.
		 */
		private void releaseShutdownParticipants()
		{
			final List<PersistenceShutdownReleasable> participants = new ArrayList<>();
			try
			{
				final PersistenceAcceptor collector = (objectId, instance) ->
				{
					if(instance instanceof PersistenceShutdownReleasable)
					{
						participants.add((PersistenceShutdownReleasable)instance);
					}
				};
				this.connectionFoundation.getObjectRegistry().iterateEntries(collector);
			}
			catch(final Throwable t)
			{
				logger.error("Error while collecting storage shutdown participants", t);
			}

			for(final PersistenceShutdownReleasable participant : participants)
			{
				try
				{
					participant.releaseOnShutdown();
				}
				catch(final Throwable t)
				{
					logger.error("Error releasing shutdown participant of type {}",
						participant.getClass().getName(), t);
				}
			}
		}

		@Override
		public final boolean isAcceptingTasks()
		{
			return this.storageSystem.isAcceptingTasks();
		}

		@Override
		public final boolean isRunning()
		{
			return this.storageSystem.isRunning();
		}
		
		@Override
		public final boolean isActive()
		{
			return this.storageSystem.isActive();
		}

		@Override
		public final boolean isStartingUp()
		{
			return this.storageSystem.isStartingUp();
		}

		@Override
		public final boolean isShuttingDown()
		{
			return this.storageSystem.isShuttingDown();
		}

		@Override
		public final void checkAcceptingTasks()
		{
			this.storageSystem.checkAcceptingTasks();
		}

		@Override
		public final StorageConfiguration configuration()
		{
			return this.configuration;
		}

		@Override
		public final StorageTypeDictionary typeDictionary()
		{
			return this.storageSystem.typeDictionary();
		}

		@Override
		public final StorageConnection createConnection()
		{
			return this.connectionFoundation.createStorageConnection();
		}
		
		@Override
		public final long initializationTime()
		{
			return this.storageSystem.initializationTime();
		}
		
		@Override
		public final long operationModeTime()
		{
			return this.storageSystem.operationModeTime();
		}

		@Override
		public final void issueFullGarbageCollection()
		{
			this.singletonConnection().issueFullGarbageCollection();
		}

		@Override
		public final boolean issueGarbageCollection(final long nanoTimeBudget)
		{
			return this.singletonConnection().issueGarbageCollection(nanoTimeBudget);
		}

		@Override
		public final void issueFullFileCheck()
		{
			this.singletonConnection().issueFullFileCheck();
		}

		@Override
		public final boolean issueFileCheck(final long nanoTimeBudget)
		{
			return this.singletonConnection().issueFileCheck(nanoTimeBudget);
		}

		@Override
		public final StorageIntegrityCheckResult issueIntegrityCheck(final long nanoTimeBudget)
		{
			return this.singletonConnection().issueIntegrityCheck(nanoTimeBudget);
		}

		@Override
		public final void issueFullCacheCheck()
		{
			this.singletonConnection().issueFullCacheCheck();
		}

		@Override
		public final void issueFullCacheCheck(final StorageEntityCacheEvaluator entityEvaluator)
		{
			this.singletonConnection().issueFullCacheCheck(entityEvaluator);
		}

		@Override
		public final boolean issueCacheCheck(final long nanoTimeBudget)
		{
			return this.singletonConnection().issueCacheCheck(nanoTimeBudget);
		}

		@Override
		public final boolean issueCacheCheck(
			final long                        nanoTimeBudget,
			final StorageEntityCacheEvaluator entityEvaluator
		)
		{
			return this.singletonConnection().issueCacheCheck(nanoTimeBudget, entityEvaluator);
		}

		@Override
		public final void issueFullBackup(
			final StorageLiveFileProvider           targetFileProvider    ,
			final PersistenceTypeDictionaryExporter typeDictionaryExporter
		)
		{
			this.singletonConnection().issueFullBackup(targetFileProvider, typeDictionaryExporter);
		}
		
		@Override
		public void issueTransactionsLogCleanup()
		{
			this.singletonConnection().issueTransactionsLogCleanup();
		}
		
		@Override
		public List<AdjacencyFiles> exportAdjacencyData(
			final Path workingDir)
		{
			return this.singletonConnection().exportAdjacencyData(workingDir);
		}
		
		@Override
		public final StorageRawFileStatistics createStorageStatistics()
		{
			return this.singletonConnection().createStorageStatistics();
		}

		@Override
		public final void exportChannels(
			final StorageLiveFileProvider fileProvider             ,
			final boolean             performGarbageCollection
		)
		{
			this.singletonConnection().exportChannels(fileProvider, performGarbageCollection);
		}

		@Override
		public final StorageEntityTypeExportStatistics exportTypes(
			final StorageEntityTypeExportFileProvider         exportFileProvider,
			final Predicate<? super StorageEntityTypeHandler> isExportType
		)
		{
			return this.singletonConnection().exportTypes(exportFileProvider, isExportType);
		}

		@Override
		public final void importFiles(final XGettingEnum<AFile> importFiles)
		{
			this.singletonConnection().importFiles(importFiles);
		}
		
		@Override
		public void importData(final XGettingEnum<ByteBuffer> importData)
		{
			this.singletonConnection().importData(importData);
		}

	}

}
