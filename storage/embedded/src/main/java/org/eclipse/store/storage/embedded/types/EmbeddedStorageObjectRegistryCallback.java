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
import org.eclipse.serializer.persistence.types.Persistence;
import org.eclipse.serializer.persistence.types.PersistenceObjectIdAcceptor;
import org.eclipse.serializer.persistence.types.PersistenceObjectRegistry;
import org.eclipse.store.storage.types.LiveObjectIdsHandler;

public interface EmbeddedStorageObjectRegistryCallback extends LiveObjectIdsHandler
{
	public void initializeObjectRegistry(PersistenceObjectRegistry objectRegistry);



	public static EmbeddedStorageObjectRegistryCallback New()
	{
		return new EmbeddedStorageObjectRegistryCallback.Default();
	}

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

		@Override
		public synchronized void iterateLiveObjectIds(final PersistenceObjectIdAcceptor acceptor)
		{
			if(this.objectRegistry == null)
			{
				return;
			}

			this.objectRegistry.iterateEntries((objectId, instance) ->
			{
				// only emit live data OIDs:
				// - skip cleared WeakReferences (instance == null)
				// - skip TypeIds and ConstantIds: they are intentionally unresolvable in the persistent form
				//   and feeding them into the mark queue would cause the zombie handler to see them as zombies.
				if(instance != null && Persistence.IdType.OID.isInRange(objectId))
				{
					acceptor.acceptObjectId(objectId);
				}
			});
		}

	}

}
