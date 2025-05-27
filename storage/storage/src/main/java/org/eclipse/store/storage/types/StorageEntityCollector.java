package org.eclipse.store.storage.types;

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

import org.eclipse.serializer.functional._longProcedure;
import org.eclipse.serializer.persistence.binary.types.ChunksBuffer;
import org.eclipse.serializer.util.logging.Logging;
import org.eclipse.store.storage.exceptions.StorageExceptionConsistency;
import org.eclipse.store.storage.types.StorageEntityCache.Default;
import org.slf4j.Logger;

public interface StorageEntityCollector extends _longProcedure
{

	/**
	 * Responsible to create the StorageEntityCollector used by the storage
	 * on standard load operations.
	 */
	public interface Creator
	{
		public static Creator Default()
		{
			return new EntityCollectorCreatorByOid();
		}
		
		public static Creator Unchecked()
		{
			return new EntityCollectorCreatorByOidUnchecked();
		}
		
		StorageEntityCollector create(StorageEntityCache.Default entityCache, ChunksBuffer dataCollector);
			
		public class EntityCollectorCreatorByOid implements Creator
		{
			@Override
			public StorageEntityCollector create(Default entityCache, ChunksBuffer dataCollector)
			{
				return new EntityCollectorByOid(entityCache, dataCollector);
			}
		}
		
		public class EntityCollectorCreatorByOidUnchecked implements Creator
		{
			@Override
			public StorageEntityCollector create(Default entityCache, ChunksBuffer dataCollector)
			{
				return new EntityCollectorByOidUnchecked(entityCache, dataCollector);
			}
		}
		
	}
	
	/**
	 * Default StorageEntityCollector implementation that will fail
	 * with a StorageExceptionConsistency exception if the storage
	 * does not contain a persisted object with the given id.
	 */
	class EntityCollectorByOid implements StorageEntityCollector
	{
		// (01.06.2013 TM)TODO: clean up / consolidate all internal implementations
				
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////
	
		private final StorageEntityCache.Default entityCache  ;
		private final ChunksBuffer               dataCollector;
	
	
	
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////
	
		public EntityCollectorByOid(
			final StorageEntityCache.Default entityCache  ,
			final ChunksBuffer               dataCollector
		)
		{
			super();
			this.entityCache   = entityCache  ;
			this.dataCollector = dataCollector;
		}
	
	
	
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////
	
		@Override
		public final void accept(final long objectId)
		{
			final StorageEntity.Default entry;
			if((entry = this.entityCache.getEntry(objectId)) == null)
			{
				/* (14.01.2015 TM)NOTE: this actually is an error, as every oid request comes
				 * from a referencing entity from inside the same database. So if any load request lookup
				 * yields null, it is an inconsistency that has to be expressed rather sooner than later.
				 *
				 * If some kind of querying request (look if an arbitrary oid yields an entity) is needed,
				 * it has to be a dedicated kind of request, not this one.
				 * This one does recursive graph loading (consistency required), not arbitrary querying
				 * with optional results.
				 */
				
				throw new StorageExceptionConsistency("No entity found for objectId " + objectId);
			}
			entry.copyCachedData(this.dataCollector);
			this.entityCache.checkForCacheClear(entry, System.currentTimeMillis());
		}
	
	}

	/**
	 * Special StorageEntityCollector implementation that will NOT fail
	 * with a StorageExceptionConsistency exception if the storage
	 * does not contain a persisted object with the given id.
	 * Instead, the missing objects id will only be logged.
	 * <br><br><b>
	 * Use this StorageEntityCollector with extreme caution as it may
	 * result in more unrecognized persistence errors and missing runtime objects
	 * when used wrong!
	 */
	class EntityCollectorByOidUnchecked implements StorageEntityCollector
	{
		private final static Logger logger = Logging.getLogger(StorageEntityCollector.class);
		
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////
	
		private final StorageEntityCache.Default entityCache  ;
		private final ChunksBuffer               dataCollector;
	
	
	
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////
	
		public EntityCollectorByOidUnchecked(
			final StorageEntityCache.Default entityCache  ,
			final ChunksBuffer               dataCollector
		)
		{
			super();
			this.entityCache   = entityCache  ;
			this.dataCollector = dataCollector;
		}
	
	
	
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////
	
		@Override
		public final void accept(final long objectId)
		{
			final StorageEntity.Default entry;
			if((entry = this.entityCache.getEntry(objectId)) == null)
			{
				logger.warn("No entity found for ObjectID {}, continuing without throwing an exception!", objectId);
				return;
			}
			entry.copyCachedData(this.dataCollector);
			this.entityCache.checkForCacheClear(entry, System.currentTimeMillis());
		}
	
	}

	class EntityCollectorByTid implements StorageEntityCollector
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////
	
		private final StorageEntityCache.Default entityCache  ;
		private final ChunksBuffer               dataCollector;
	
	
	
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////
	
		public EntityCollectorByTid(
			final StorageEntityCache.Default entityCache  ,
			final ChunksBuffer               dataCollector
		)
		{
			super();
			this.entityCache   = entityCache  ;
			this.dataCollector = dataCollector;
		}
	
	
	
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////
	
		@Override
		public final void accept(final long tid)
		{
			final StorageEntityType.Default type;
			if((type = this.entityCache.getType(tid)) == null)
			{
				// it can very well be that a channel does not have a certain type at all. That is no error
				return;
			}
	
			// all the type's entities are iterated and their data is collected
			for(StorageEntity.Default entity = type.head; (entity = entity.typeNext) != null;)
			{
				entity.copyCachedData(this.dataCollector);
				this.entityCache.checkForCacheClear(entity, System.currentTimeMillis());
			}
		}
	
	}

}
