package org.eclipse.store.storage.types;

/*-
 * #%L
 * EclipseStore Storage
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

import java.lang.ref.WeakReference;
import java.time.Duration;

import org.eclipse.serializer.chars.XChars;
import org.eclipse.serializer.persistence.types.BatchStorer;
import org.eclipse.serializer.persistence.types.Persister;
import org.eclipse.serializer.persistence.types.Storer;
import org.eclipse.store.storage.exceptions.StorageExceptionConsistency;
import org.eclipse.store.storage.exceptions.StorageExceptionInitialization;
import org.eclipse.store.storage.exceptions.StorageExceptionNotActive;

/**
 * Top-level handle for a logical database identified by its {@link DatabasePart#databaseName() name}.
 * <p>
 * A {@link Database} entry is registered in a {@link Databases} registry and lives independently of any
 * {@link StorageManager} that may be running for it. The associated {@link StorageManager} can be
 * attached, detached, or replaced over the lifetime of the entry through {@link #setStorage(StorageManager)},
 * and the current attachment is always queryable via {@link #storage()} / {@link #hasStorage()}.
 * <p>
 * Because {@link Database} also implements {@link Persister}, it can be used as a thin facade for
 * storing and retrieving objects: every {@link Persister} call is forwarded to the currently attached
 * {@link StorageManager}, throwing
 * {@link org.eclipse.store.storage.exceptions.StorageExceptionInitialization} or
 * {@link org.eclipse.store.storage.exceptions.StorageExceptionNotActive} if no running storage is
 * attached. This makes a {@link Database} a convenient handle to keep around in application code that
 * needs to outlive individual storage start/shutdown cycles.
 *
 * @see DatabasePart
 * @see Databases
 * @see StorageManager
 */
public interface Database extends DatabasePart, Persister
{
	/**
	 * Returns a human-readable string identifying this database, suitable for inclusion in log and
	 * exception messages. The default implementation combines the system identity hash code with the
	 * {@link #databaseName() database name}.
	 *
	 * @return a textual identifier of this {@link Database} instance.
	 */
	public default String toIdentifyingString()
	{
		return XChars.systemString(this) + " \"" + this.databaseName() + "\"";
	}

	/**
	 * Returns the currently attached {@link StorageManager}, or {@code null} if no storage is attached.
	 * <p>
	 * The returned reference is held weakly: a {@link StorageManager} that has been shut down and is no
	 * longer reachable from application code may be garbage-collected, in which case this method
	 * returns {@code null} again.
	 *
	 * @return the currently attached {@link StorageManager}, or {@code null} if none is attached.
	 */
	public StorageManager storage();

	/**
	 * Attaches the passed {@link StorageManager} to this database, replacing any previous (non-active)
	 * attachment.
	 * <p>
	 * The attachment is rejected if a running storage is currently associated with this database (see
	 * {@link #guaranteeNoActiveStorage()}) or if the passed {@link StorageManager} reports a different
	 * {@link StorageManager#database() database} than this instance.
	 *
	 * @param storage the {@link StorageManager} to associate with this database.
	 *
	 * @return the same {@link StorageManager} that was attached, for fluent use.
	 *
	 * @throws org.eclipse.store.storage.exceptions.StorageExceptionInitialization if a running storage
	 *         is already attached to this database.
	 * @throws org.eclipse.store.storage.exceptions.StorageExceptionConsistency    if the passed
	 *         {@link StorageManager} is associated with a different {@link Database}.
	 */
	public StorageManager setStorage(StorageManager storage);

	/**
	 * Convenience query: returns {@code true} if a {@link StorageManager} is currently attached to
	 * this database (regardless of whether it is running).
	 *
	 * @return {@code true} if {@link #storage()} would return a non-{@code null} value.
	 */
	public default boolean hasStorage()
	{
		return this.storage() != null;
	}

	/**
	 * Asserts that no running {@link StorageManager} is currently attached to this database.
	 * <p>
	 * If the database has an attached storage that {@link StorageManager#isRunning() is running}, this
	 * method throws an exception. A non-running attached storage is tolerated and not reported as a
	 * failure.
	 *
	 * @return this {@link Database} instance, for fluent use.
	 *
	 * @throws org.eclipse.store.storage.exceptions.StorageExceptionInitialization if a running
	 *         {@link StorageManager} is currently attached to this database.
	 */
	public Database guaranteeNoActiveStorage();

	/**
	 * Returns the attached {@link StorageManager}, asserting that it is non-{@code null} and currently
	 * running.
	 *
	 * @return the attached, currently running {@link StorageManager}.
	 *
	 * @throws org.eclipse.store.storage.exceptions.StorageExceptionInitialization if no
	 *         {@link StorageManager} is attached.
	 * @throws org.eclipse.store.storage.exceptions.StorageExceptionNotActive      if a
	 *         {@link StorageManager} is attached but not running.
	 */
	public StorageManager guaranteeActiveStorage();




	/**
	 * Pseudo-constructor method to create a new {@link Database} instance with the passed name and no
	 * attached storage.
	 * <p>
	 * The returned entry can be registered in a {@link Databases} registry; it acquires a
	 * {@link StorageManager} only once {@link #setStorage(StorageManager)} is called (typically by an
	 * embedded-storage foundation during startup).
	 *
	 * @param databaseName the identifying name of the new database.
	 *
	 * @return a new, storage-less {@link Database} instance.
	 */
	public static Database New(final String databaseName)
	{
		return new Database.Default(
			databaseName,
			new WeakReference<>(null)
		);
	}

	/**
	 * Default implementation of {@link Database}. Holds the attached {@link StorageManager} via a
	 * {@link WeakReference} so that storage instances which are shut down and no longer reachable from
	 * application code may be reclaimed even if their {@link Database} entry is still registered.
	 * Mutating operations are synchronized to make concurrent attach/detach calls safe.
	 */
	public final class Default implements Database
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////
		
		private final String name;
		
		private WeakReference<StorageManager> storageReference;
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////
		
		Default(final String identifier, final WeakReference<StorageManager> storageReference)
		{
			super();
			this.name = identifier;
			this.storageReference = storageReference;
		}
		
		

		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////

		@Override
		public final String databaseName()
		{
			return this.name;
		}

		@Override
		public final synchronized StorageManager storage()
		{
			return this.storageReference.get();
		}
		
		@Override
		public final synchronized Database guaranteeNoActiveStorage()
		{
			final StorageManager existingStorage = this.storage();
			if(existingStorage != null && existingStorage.isRunning())
			{
				throw new StorageExceptionInitialization(
					"Active storage for " + this.toIdentifyingString() + " already exists."
				);
			}
			
			return this;
		}
		
		@Override
		public final synchronized StorageManager guaranteeActiveStorage()
		{
			final StorageManager existingStorage = this.storage();
			if(existingStorage == null)
			{
				throw new StorageExceptionInitialization(
					"No storage for " + this.toIdentifyingString() + " exists."
				);
			}
			
			if(!existingStorage.isRunning())
			{
				throw new StorageExceptionNotActive(
					"Storage for " + this.toIdentifyingString() + " is not active."
				);
			}
			
			return existingStorage;
		}
		
		@Override
		public final synchronized StorageManager setStorage(final StorageManager storage)
		{
			this.guaranteeNoActiveStorage();
			
			final Database associatedDatabase = storage.database();
			if(associatedDatabase != this)
			{
				throw new StorageExceptionConsistency(
					"Inconsistent database association: the passed " + StorageManager.class.getSimpleName()
					+ " belongs to " + associatedDatabase.toIdentifyingString()
					+ ", which is incompatible to this: " + this.toIdentifyingString() + "."
				);
			}
			
			// other storage instance can be set validly/consistently.
			this.storageReference = new WeakReference<>(storage);
			
			return storage;
		}
		
		@Override
		public final Object getObject(final long objectId)
		{
			final StorageManager storage = this.guaranteeActiveStorage();

			return storage.getObject(objectId);
		}
		
		@Override
		public final long store(final Object instance)
		{
			final StorageManager storage = this.guaranteeActiveStorage();

			return storage.store(instance);
		}
		
		@Override
		public final long[] storeAll(final Object... instances)
		{
			final StorageManager storage = this.guaranteeActiveStorage();

			return storage.storeAll(instances);
		}
		
		@Override
		public final void storeAll(final Iterable<?> instances)
		{
			final StorageManager storage = this.guaranteeActiveStorage();

			storage.storeAll(instances);
		}
		
		@Override
		public final Storer createLazyStorer()
		{
			final StorageManager storage = this.guaranteeActiveStorage();

			return storage.createLazyStorer();
		}
		
		@Override
		public final Storer createStorer()
		{
			final StorageManager storage = this.guaranteeActiveStorage();

			return storage.createStorer();
		}

		@Override
		public final Storer createEagerStorer()
		{
			final StorageManager storage = this.guaranteeActiveStorage();

			return storage.createEagerStorer();
		}

		@Override
		public final BatchStorer createBatchStorer(
			final BatchStorer.Controller controller   ,
			final Duration               checkInterval
		)
		{
			final StorageManager storage = this.guaranteeActiveStorage();

			return storage.createBatchStorer(controller, checkInterval);
		}

	}

}
