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

import java.util.function.Supplier;

import org.eclipse.serializer.exceptions.MissingFoundationPartException;
import org.eclipse.serializer.persistence.binary.types.Binary;
import org.eclipse.serializer.persistence.binary.types.BinaryLoader;
import org.eclipse.serializer.persistence.binary.types.BinaryPersistenceFoundation;
import org.eclipse.serializer.persistence.binary.types.BinaryStorer;
import org.eclipse.serializer.persistence.types.PersistenceLiveStorerRegistry;
import org.eclipse.serializer.persistence.types.PersistenceManager;
import org.eclipse.serializer.persistence.types.PersistenceStorer;
import org.eclipse.serializer.reference.Reference;
import org.eclipse.store.storage.types.StorageConnection;
import org.eclipse.store.storage.types.StorageRequestAcceptor;
import org.eclipse.store.storage.types.StorageSystem;
import org.eclipse.store.storage.types.StorageWriteController;

/**
 * Foundation for the persistence-side parts of an embedded storage setup.
 * <p>
 * Where {@link EmbeddedStorageFoundation} assembles the storage layer (channels, files, threads, GC),
 * this foundation assembles everything that lives on the persistence layer: the
 * {@link PersistenceManager}, its type handler manager and type
 * dictionary, the binary {@link org.eclipse.serializer.persistence.types.PersistenceSource}/
 * {@link org.eclipse.serializer.persistence.types.PersistenceTarget} pair connecting it to the storage,
 * and the live storer/object-registry plumbing required for garbage collection and roots handling.
 * <p>
 * The resulting {@link StorageConnection} created via {@link #createStorageConnection()} ties both layers
 * together so that the calling application can simply load and store object graphs.
 * <p>
 * Like {@link EmbeddedStorageFoundation}, this type follows the foundation pattern: every {@code set~} method
 * is a plain setter returning {@literal this} for chaining, and every {@code get~} method either returns the
 * currently set value or lazily creates a default one on first access.
 *
 * @param <F> the "self-type" of the concrete {@link EmbeddedStorageConnectionFoundation} implementation.
 *
 * @see EmbeddedStorageFoundation
 * @see BinaryPersistenceFoundation
 */
public interface EmbeddedStorageConnectionFoundation<F extends EmbeddedStorageConnectionFoundation<?>>
extends BinaryPersistenceFoundation<F>
{
	/**
	 * Returns the {@link Supplier} currently set as a callback for lazily obtaining a {@link StorageSystem}.
	 * <p>
	 * Used during foundation assembly to break a circular dependency: the embedded storage foundation
	 * registers a supplier that, when first called, creates the {@link StorageSystem}. Returns {@code null}
	 * if no supplier has been set.
	 *
	 * @return the currently set {@link StorageSystem} supplier, or {@code null} if none has been set.
	 */
	// intentionally no "get" prefix since this is a pure pseudo-property getter and not an action.
	public Supplier<? extends StorageSystem> storageSystemSupplier();

	/**
	 * Returns the {@link StorageSystem} associated with this foundation, lazily creating it via the configured
	 * {@link #storageSystemSupplier() storage system supplier} on first access.
	 *
	 * @return the {@link StorageSystem} to be used.
	 *
	 * @throws MissingFoundationPartException if no instance is set and none can be created via a supplier.
	 */
	public StorageSystem getStorageSystem();

	/**
	 * Returns the currently set {@link StorageWriteController} without creating a default if none is set.
	 *
	 * @return the currently set {@link StorageWriteController}, or {@code null} if none has been set.
	 *
	 * @see #getWriteController()
	 */
	public StorageWriteController writeController();

	/**
	 * Returns the {@link StorageWriteController} to be used, lazily creating a default one (derived from the
	 * storage's file system) if none has been set.
	 *
	 * @return the {@link StorageWriteController} to be used.
	 */
	public StorageWriteController getWriteController();

	/**
	 * Returns the {@link EmbeddedStorageObjectRegistryCallback} bridging the storage's GC to the persistence
	 * layer's object registry, lazily creating a default one if none has been set.
	 *
	 * @return the {@link EmbeddedStorageObjectRegistryCallback} to be used.
	 */
	public EmbeddedStorageObjectRegistryCallback getObjectRegistryCallback();

	/**
	 * Returns the {@link Reference} that holds the {@link PersistenceLiveStorerRegistry} of the currently
	 * active storage connection. The reference is shared with the storage layer (for GC sweep coordination)
	 * and updated whenever a new persistence manager is created.
	 *
	 * @return the shared {@link PersistenceLiveStorerRegistry} reference.
	 */
	public Reference<PersistenceLiveStorerRegistry> getLiveStorerRegistryReference();

	/**
	 * Returns the {@link PersistenceLiveStorerRegistry} used to track currently active storers, lazily
	 * creating a default one if none has been set.
	 *
	 * @return the {@link PersistenceLiveStorerRegistry} to be used.
	 */
	public PersistenceLiveStorerRegistry getLiveStorerRegistry();

	/**
	 * Sets the {@link StorageSystem} to be used and returns {@literal this} for chaining.
	 * Setting the storage system explicitly bypasses the lazy creation through
	 * {@link #storageSystemSupplier()}.
	 *
	 * @param storageSystem the {@link StorageSystem} instance to be used.
	 *
	 * @return {@literal this} to allow method chaining.
	 */
	public F setStorageSystem(StorageSystem storageSystem);

	/**
	 * Sets the {@link Supplier} used to lazily create the {@link StorageSystem} when it is first requested.
	 *
	 * @param storageSystemSupplier the supplier to be used.
	 *
	 * @return {@literal this} to allow method chaining.
	 */
	public F setStorageSystemSupplier(Supplier<? extends StorageSystem> storageSystemSupplier);

	/**
	 * Sets the {@link StorageWriteController} that gates write requests from the persistence layer.
	 *
	 * @param writeController the {@link StorageWriteController} to be used.
	 *
	 * @return {@literal this} to allow method chaining.
	 */
	public F setWriteController(StorageWriteController writeController);

	/**
	 * Sets the {@link EmbeddedStorageObjectRegistryCallback} that bridges the storage GC to the persistence
	 * object registry.
	 *
	 * @param objectRegistryCallback the {@link EmbeddedStorageObjectRegistryCallback} to be used.
	 *
	 * @return {@literal this} to allow method chaining.
	 */
	public F setObjectRegistryCallback(EmbeddedStorageObjectRegistryCallback objectRegistryCallback);

	/**
	 * Sets the shared {@link Reference} holding the active {@link PersistenceLiveStorerRegistry}. This
	 * reference is shared with the storage layer so that both sides observe the same registry instance.
	 *
	 * @param storerRegistryReference the reference to be used.
	 *
	 * @return {@literal this} to allow method chaining.
	 */
	public F setLiveStorerRegistryReference(Reference<PersistenceLiveStorerRegistry> storerRegistryReference);

	/**
	 * Sets the {@link PersistenceLiveStorerRegistry} used to track currently active storers.
	 *
	 * @param liveLiveStorerRegistry the registry to be used.
	 *
	 * @return {@literal this} to allow method chaining.
	 */
	public F setLiveStorerRegistry(PersistenceLiveStorerRegistry liveLiveStorerRegistry);

	/**
	 * Creates a new {@link StorageConnection} that ties a freshly built persistence manager to the storage's
	 * request acceptor.
	 * <p>
	 * Each call returns a separate connection. Most embedded usages share a single connection internally; an
	 * application typically does not need to create more than one. Note that using more than one connection
	 * in parallel can introduce GC consistency issues as documented on the implementation.
	 *
	 * @return a new {@link StorageConnection} instance.
	 */
	public StorageConnection createStorageConnection();



	/**
	 * Pseudo-constructor method to create a new {@link EmbeddedStorageConnectionFoundation} instance with the
	 * default implementation.
	 *
	 * @return a new {@link EmbeddedStorageConnectionFoundation} instance.
	 */
	public static EmbeddedStorageConnectionFoundation<?> New()
	{
		return new EmbeddedStorageConnectionFoundation.Default<>();
	}

	public class Default<F extends EmbeddedStorageConnectionFoundation.Default<?>>
	extends BinaryPersistenceFoundation.Default<F>
	implements EmbeddedStorageConnectionFoundation<F>
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////

		private StorageSystem                            storageSystem          ;
		private Supplier<? extends StorageSystem>        storageSystemSupplier  ;
		private StorageWriteController                   writeController        ;
		private transient StorageRequestAcceptor         storageRequestAcceptor ;
		private EmbeddedStorageObjectRegistryCallback    objectRegistryCallback ;
		private Reference<PersistenceLiveStorerRegistry> storerRegistryReference;
		private PersistenceLiveStorerRegistry            liveLiveStorerRegistry ;
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		protected Default()
		{
			super();
		}

		

		///////////////////////////////////////////////////////////////////////////
		// getters //
		////////////

		@Override
		public Supplier<? extends StorageSystem> storageSystemSupplier()
		{
			return this.storageSystemSupplier;
		}
		
		@Override
		public StorageWriteController writeController()
		{
			return this.writeController;
		}
		
		@Override
		public StorageWriteController getWriteController()
		{
			if(this.writeController == null)
			{
				this.writeController = this.dispatch(this.ensureWriteController());
			}
			return this.writeController;
		}
		
		@Override
		public StorageSystem getStorageSystem()
		{
			if(this.storageSystem == null)
			{
				this.storageSystem = this.dispatch(this.ensureStorageSystem());
			}
			return this.storageSystem;
		}

		@Override
		public final EmbeddedStorageObjectRegistryCallback getObjectRegistryCallback()
		{
			if(this.objectRegistryCallback == null)
			{
				this.objectRegistryCallback = this.dispatch(this.ensureObjectRegistryCallback());
			}
			return this.objectRegistryCallback;
		}

		@Override
		public final Reference<PersistenceLiveStorerRegistry> getLiveStorerRegistryReference()
		{
			if(this.storerRegistryReference == null)
			{
				this.storerRegistryReference = this.dispatch(this.ensureLiveStorerRegistryReference());
			}
			return this.storerRegistryReference;
		}

		@Override
		public final PersistenceLiveStorerRegistry getLiveStorerRegistry()
		{
			if(this.liveLiveStorerRegistry == null)
			{
				this.liveLiveStorerRegistry = this.dispatch(this.ensureLiveStorerRegistry());
			}
			return this.liveLiveStorerRegistry;
		}
		

		///////////////////////////////////////////////////////////////////////////
		// setters //
		////////////

		@Override
		public F setStorageSystem(
			final StorageSystem storageSystem
		)
		{
			this.storageSystem = storageSystem;
			return this.$();
		}
		
		@Override
		public F setStorageSystemSupplier(final Supplier<? extends StorageSystem> storageSystemSupplier)
		{
			this.storageSystemSupplier = storageSystemSupplier;
			return this.$();
		}
		
		@Override
		public F setWriteController(final StorageWriteController writeController)
		{
			this.writeController = writeController;
			
			return this.$();
		}
		
		@Override
		public F setObjectRegistryCallback(final EmbeddedStorageObjectRegistryCallback objectRegistryCallback)
		{
			this.objectRegistryCallback = objectRegistryCallback;
			return this.$();
		}

		@Override
		public final F setLiveStorerRegistryReference(final Reference<PersistenceLiveStorerRegistry> storerRegistryReference)
		{
			this.storerRegistryReference = storerRegistryReference;
			return this.$();
		}

		@Override
		public final F setLiveStorerRegistry(final PersistenceLiveStorerRegistry liveLiveStorerRegistry)
		{
			this.liveLiveStorerRegistry = liveLiveStorerRegistry;
			return this.$();
		}
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////
		
		protected final void internalSetStorageSystem(final StorageSystem storageSystem)
		{
			this.storageSystem = storageSystem;
		}

		protected StorageSystem ensureStorageSystem()
		{
			if(this.storageSystemSupplier != null)
			{
				return notNull(this.storageSystemSupplier.get());
			}
			
			throw new MissingFoundationPartException(StorageSystem.class);
		}
		
		protected EmbeddedStorageObjectRegistryCallback ensureObjectRegistryCallback()
		{
			// initially empty, gets initialized upon storage connection creation
			return EmbeddedStorageObjectRegistryCallback.New();
		}

		protected Reference<PersistenceLiveStorerRegistry> ensureLiveStorerRegistryReference()
		{
			throw new MissingFoundationPartException(Reference.class, "to " + PersistenceLiveStorerRegistry.class.getSimpleName());
		}
		
		protected StorageWriteController ensureWriteController()
		{
			return StorageWriteController.Wrap(
				this.getStorageSystem().fileSystem()
			);
		}

		@Override
		protected BinaryLoader.Creator ensureBuilderCreator()
		{
			return new BinaryLoader.CreatorChannelHashing(
				this.getStorageSystem().operationController().channelCountProvider(),
				this.isByteOrderMismatch()
			);
		}

		@Override
		protected BinaryStorer.Creator ensureStorerCreator()
		{
			return BinaryStorer.Creator(
				this.getStorageSystem().channelCountProvider(),
				this.isByteOrderMismatch(),
				// capture is only worthwhile when the storage-side validation is enabled; single config knob.
				this.getStorageSystem().configuration().referenceValidationPolicy().isValidating()
			);
		}

		@Override
		protected EmbeddedStorageBinarySource ensurePersistenceSource()
		{
			// pass a supplier — Source resolves the current acceptor on every call so it
			// survives shutdown/start cycles without being rebuilt or explicitly rebound.
			return new EmbeddedStorageBinarySource.Default(this::internalGetStorageRequestAcceptor);
		}

		@Override
		protected EmbeddedStorageBinaryTarget ensurePersistenceTarget()
		{
			return EmbeddedStorageBinaryTarget.New(
				this::internalGetStorageRequestAcceptor,
				this.getWriteController()
			);
		}

		protected StorageRequestAcceptor internalGetStorageRequestAcceptor()
		{
			if(this.storageRequestAcceptor == null)
			{
				this.storageRequestAcceptor = this.storageSystem.createRequestAcceptor();
			}
			return this.storageRequestAcceptor;
		}
		
		protected PersistenceLiveStorerRegistry ensureLiveStorerRegistry()
		{
			// embedded storage must create a functional storer registry for use with the storage layer (GC sweep).
			return PersistenceLiveStorerRegistry.New();
		}

		@Override
		public PersistenceManager<Binary> createPersistenceManager()
		{
			final PersistenceLiveStorerRegistry storerRegistry = this.getLiveStorerRegistry();
			final PersistenceStorer.CreationObserver observer = this.getStorerCreationObserver();
			if(observer == null)
			{
				// registry can simply be set as the (sole) observer
				this.setStorerCreationObserver(storerRegistry);
			}
			else
			{
				// conserve existing observer
				this.setStorerCreationObserver(
					PersistenceStorer.CreationObserver.Chain(observer, storerRegistry)
				);
			}
			this.getLiveStorerRegistryReference().set(storerRegistry);

			final PersistenceManager<Binary> pm = super.createPersistenceManager();

			// reference explicitly the PM's object registry, just to be safe
			this.getObjectRegistryCallback().initializeObjectRegistry(pm.objectRegistry());
			// note: using more than 1 connection might cause consistency problems for the Storage GC using the callback

			return pm;
		}

		@Override
		public synchronized StorageConnection createStorageConnection()
		{
			// reset for new connection, gets set via method called in super method
			this.storageRequestAcceptor = null;

			/*
			 * even though super.create() always gets called prior to reading the connectionRequestAcceptor
			 * and in the process calling internalGetStorageRequestAcceptor() and createRequestAcceptor(),
			 * sometimes it happens that despite the internalGetStorageRequestAcceptor() and despite being
			 * single-threaded and even synchronized (= no code rearrangement), the field reference
			 * is still null when read as the second constructor argument.
			 * It is not clear why this happens under those conditions.
			 * As a workaround, the initializing getter has to be called once beforehand.
			 */
			this.internalGetStorageRequestAcceptor();

			// using this. instead of super. is important here!
			final PersistenceManager<Binary> pm = this.createPersistenceManager();

			// persistence manager is "connected" to the storage's request acceptor (= the storage threads)
			return StorageConnection.New(pm, this.storageRequestAcceptor);
		}

	}

}
