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

import org.eclipse.serializer.persistence.types.ObjectIdsProcessor;
import org.eclipse.serializer.persistence.types.ObjectIdsSelector;
import org.eclipse.serializer.persistence.types.PersistenceObjectRegistry;

/**
 * Callback bridge between the storage layer's garbage collector and the persistence layer's
 * {@link PersistenceObjectRegistry}.
 * <p>
 * The storage GC needs to know which object ids are currently held alive on the application side so that it
 * does not erroneously delete the corresponding entities. In embedded mode the persistence layer that owns
 * those live object ids is created later than the storage layer, so this callback is registered with the
 * storage upfront and is then {@link #initializeObjectRegistry(PersistenceObjectRegistry) initialized} as soon
 * as the application-side {@link PersistenceObjectRegistry} becomes available.
 * <p>
 * Until initialization the callback reports an empty live object id set, which is a safe default during the
 * brief window between storage startup and the creation of the first storage connection.
 *
 * @see ObjectIdsSelector
 * @see PersistenceObjectRegistry
 */
public interface EmbeddedStorageObjectRegistryCallback extends ObjectIdsSelector
{
	/**
	 * Binds the passed {@link PersistenceObjectRegistry} as the source of live object ids reported via
	 * {@link #processSelected(ObjectIdsProcessor)}.
	 * <p>
	 * Calling this method again with the same registry instance is a no-op; calling it with a different
	 * registry instance is rejected to avoid silently switching the live id source under a running garbage
	 * collector.
	 *
	 * @param objectRegistry the {@link PersistenceObjectRegistry} whose live object ids shall be exposed.
	 */
	public void initializeObjectRegistry(PersistenceObjectRegistry objectRegistry);



	/**
	 * Pseudo-constructor method to create a new, uninitialized {@link EmbeddedStorageObjectRegistryCallback}
	 * instance.
	 *
	 * @return a new {@link EmbeddedStorageObjectRegistryCallback} instance.
	 */
	public static EmbeddedStorageObjectRegistryCallback New()
	{
		return new EmbeddedStorageObjectRegistryCallback.Default();
	}

	/**
	 * Default implementation that reports an empty live object id set until
	 * {@link #initializeObjectRegistry(PersistenceObjectRegistry)} has been called and afterwards forwards live
	 * id selection to the bound {@link PersistenceObjectRegistry}.
	 */
	public final class Default implements EmbeddedStorageObjectRegistryCallback
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////

		private PersistenceObjectRegistry objectRegistry;



		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		Default()
		{
			super();
		}



		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////

		@Override
		public synchronized void initializeObjectRegistry(final PersistenceObjectRegistry objectRegistry)
		{
			if(this.objectRegistry != null)
			{
				if(this.objectRegistry == objectRegistry)
				{
					return;
				}

				// (29.07.2022 TM)EXCP: proper exception
				throw new RuntimeException("ObjectRegistry already initialized.");
			}

			this.objectRegistry = objectRegistry;
		}

		@Override
		public synchronized boolean processSelected(final ObjectIdsProcessor processor)
		{
			if(this.objectRegistry == null)
			{
				// object registry not yet initialized (i.e. no application-side storage connection yet)
				processor.processObjectIdsByFilter(objectId -> false);
				return true;
			}

			// efficient for embedded mode, but server mode should use #selectLiveObjectIds instead.
			return this.objectRegistry.processLiveObjectIds(processor);
		}

	}

}
