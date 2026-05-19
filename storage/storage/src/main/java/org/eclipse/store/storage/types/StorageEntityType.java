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

import java.util.function.Predicate;

import org.eclipse.store.storage.exceptions.StorageException;
import org.eclipse.serializer.functional.ThrowingProcedure;
import org.eclipse.serializer.persistence.types.Persistence;
import org.eclipse.serializer.persistence.types.PersistenceObjectIdAcceptor;


/**
 * Per-channel, per-type view over the entities stored in a {@link StorageChannel}.
 * <p>
 * Each channel maintains one {@link StorageEntityType} instance per persistent type it has seen.
 * The instance bundles the type's {@link StorageEntityTypeHandler} (which describes the binary
 * layout) with the live list of entities of that type, providing a way to iterate every entity of
 * a given type that this channel currently holds. This is the primary entry point for custom
 * exporters and analyzers that need to walk a storage type by type.
 *
 * @param <E> the concrete {@link StorageEntity} subtype iterated by this view.
 *
 * @see StorageEntity
 * @see StorageEntityTypeHandler
 */
public interface StorageEntityType<E extends StorageEntity>
{
	/**
	 * Returns the {@link StorageEntityTypeHandler} describing the binary layout of this type.
	 *
	 * @return the {@link StorageEntityTypeHandler} for this type.
	 */
	public StorageEntityTypeHandler typeHandler();

	/**
	 * Returns the number of live entities of this type currently held by the owning channel.
	 *
	 * @return the live entity count.
	 */
	public long entityCount();

	/**
	 * Convenience query: returns {@code true} if {@link #entityCount()} is zero.
	 *
	 * @return {@code true} if this view holds no entities.
	 */
	public default boolean isEmpty()
	{
		return this.entityCount() == 0;
	}

	/**
	 * Calls the passed procedure once for every live entity of this type, in iteration order, and
	 * returns the same procedure instance.
	 * <p>
	 * The procedure may throw a checked exception of type {@code T}; this exception is propagated
	 * out of this method without being wrapped. The iteration is short-circuited when the procedure
	 * throws.
	 *
	 * @param <T>       the type of throwable the procedure may raise.
	 * @param <P>       the procedure type.
	 * @param procedure the procedure to invoke for every entity.
	 *
	 * @return the same {@code procedure} instance, for fluent collection of accumulated state.
	 *
	 * @throws T if the procedure raises a {@code T} during iteration.
	 */
	public <T extends Throwable, P extends ThrowingProcedure<? super E, T>> P iterateEntities(P procedure) throws T;

	/**
	 * Returns whether entities of this type contain any object references that the storage layer has
	 * to track for garbage collection.
	 *
	 * @return {@code true} if this type has at least one reference field.
	 */
	public boolean hasReferences();

	/**
	 * Returns the number of "simple" reference slots in entities of this type, i.e. references
	 * stored directly in the entity's binary form rather than via inlined collections.
	 *
	 * @return the number of simple reference slots per entity of this type.
	 */
	public long simpleReferenceDataCount();

	/**
	 * Calls the passed acceptor once for every reference object id stored in the passed entity.
	 *
	 * @param entity   the entity whose references shall be iterated.
	 * @param iterator the acceptor to receive each reference object id.
	 */
	public void iterateEntityReferenceIds(E entity, PersistenceObjectIdAcceptor iterator);

	/**
	 * Validates every live entity of this type and returns the per-type id analysis (highest object
	 * id and constant id observed).
	 * <p>
	 * Throws a {@link StorageException} if an entity carries an invalid object id or violates the
	 * type's expected binary layout.
	 *
	 * @return a per-type {@link StorageIdAnalysis} reflecting the validated entities.
	 *
	 * @throws StorageException if entity validation fails.
	 */
	public StorageIdAnalysis validateEntities();



	/**
	 * Default {@link StorageEntityType} implementation backed by an internal singly-linked list of
	 * {@link StorageEntity.Default} entries. Used by the entity cache; not intended for direct
	 * construction by application code.
	 */
	public final class Default implements StorageEntityType<StorageEntity.Default>
	{
		/**
		 * Two-stage callback used by {@link Default#removeAll(EntityDeleter)} to delete entities
		 * during iteration: {@link #test(StorageEntity.Default)} decides whether the entity should
		 * be removed; {@link #delete(StorageEntity.Default, StorageEntityType.Default, StorageEntity.Default)}
		 * unlinks the entity from the type's internal list and applies any side-effects the deleter
		 * needs to perform.
		 */
		public interface EntityDeleter extends Predicate<StorageEntity.Default>
		{
			@Override
			public boolean test(StorageEntity.Default entity);

			/**
			 * Removes the passed entity from its type's internal list and applies any deleter
			 * side-effects.
			 *
			 * @param entity         the entity to delete.
			 * @param type           the {@link StorageEntityType.Default} the entity is being removed from.
			 * @param previousInType the entity that immediately precedes {@code entity} in the type's
			 *                       internal list, required because the list is singly-linked.
			 */
			public void delete(
				StorageEntity.Default     entity        ,
				StorageEntityType.Default type          ,
				StorageEntity.Default     previousInType
			);
		}



		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////

		        final int                      channelIndex            ;
		        final long                     typeId                  ;
		private final StorageEntityTypeHandler typeHandler             ;
		private final boolean                  hasReferences           ;
		private final long                     simpleReferenceDataCount;
		
		private       long                     entityCount             ;
		StorageEntityType.Default              hashNext                ;
		StorageEntityType.Default              next                    ;
		        final TypeInFile               dummy                    = new TypeInFile(this, null, null);

		StorageEntity.Default head = StorageEntity.Default.createDummy(this.dummy);
		StorageEntity.Default tail = this.head;



		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		Default(final int channelIndex)
		{
			super();
			this.channelIndex             = channelIndex;
			this.typeId                   =           -1;
			this.typeHandler              =         null;
			this.hasReferences            =        false;
			this.simpleReferenceDataCount =            0;
			this.next                     =         this;
		}

		Default(
			final int                              channelIndex,
			final StorageEntityTypeHandler         typeHandler ,
			final StorageEntityType.Default hashNext    ,
			final StorageEntityType.Default next
		)
		{
			super();
			this.channelIndex             = channelIndex                        ;
			this.hasReferences            = typeHandler.hasPersistedReferences();
			this.simpleReferenceDataCount = typeHandler.simpleReferenceCount()  ;
			this.typeId                   = typeHandler.typeId()                ;
			this.typeHandler              = typeHandler                         ;
			this.hashNext                 = hashNext                            ;
			this.next                     = next                                ;
		}



		///////////////////////////////////////////////////////////////////////////
		// declared methods //
		/////////////////////

		final void add(final StorageEntity.Default entry)
		{
			// last item next null strategy to increase adding and iteration performance
//			(entry.typePrev = this.head.typePrev).typeNext = this.head.typePrev = entry;
			this.tail = this.tail.typeNext = entry;
			this.entityCount++;
		}

		final void remove(final StorageEntity.Default entry, final StorageEntity.Default previousInType)
		{
			// tail reference requires special handling logic
			if(entry == this.tail)
			{
				(this.tail = previousInType).typeNext = null;
			}
			else
			{
				previousInType.typeNext = entry.typeNext;
			}

			// decrement entity count (strictly only once per remove as guaranteed by check above)
			this.entityCount--;
		}

		@Override
		public <T extends Throwable, P extends ThrowingProcedure<? super StorageEntity.Default, T>>
		P iterateEntities(final P procedure) throws T
		{
			for(StorageEntity.Default entity = this.head; (entity = entity.typeNext) != null;)
			{
				procedure.accept(entity);
			}
			return procedure;
		}

		public <P extends EntityDeleter> P removeAll(final P deleter)
		{
			for(StorageEntity.Default last, entity = this.head; (entity = (last = entity).typeNext) != null;)
			{
				if(deleter.test(entity))
				{
					deleter.delete(entity, this, last);
					// must back-set entity variable to last in order for last to remain itself in the loop's next step.
					entity = last;
				}
			}
			return deleter;
		}

		@Override
		public final StorageEntityTypeHandler typeHandler()
		{
			return this.typeHandler;
		}

		@Override
		public final long entityCount()
		{
			return this.entityCount;
		}

		@Override
		public final boolean hasReferences()
		{
			return this.hasReferences;
		}

		@Override
		public final long simpleReferenceDataCount()
		{
			return this.simpleReferenceDataCount;
		}

		@Override
		public final void iterateEntityReferenceIds(
			final StorageEntity.Default entity  ,
			final PersistenceObjectIdAcceptor  iterator
		)
		{
			this.typeHandler.iterateReferences(entity.cacheAddress(), iterator);
		}

		@Override
		public StorageIdAnalysis validateEntities()
		{
			final StorageEntityTypeHandler typeHandler = this.typeHandler;

			long maxObjectId = 0, maxConstantId = 0;
			final long maxTypeId = 0;
			for(StorageEntity.Default entity = this.head; (entity = entity.typeNext) != null;)
			{
				final long entityLength   = entity.length;
				final long entityObjectId = entity.objectId();

				typeHandler.validateEntityGuaranteedType(entityLength, entityObjectId);

				final long objectId = entity.objectId();
				if(Persistence.IdType.OID.isInRange(objectId))
				{
					if(objectId >= maxObjectId)
					{
						maxObjectId = objectId;
					}
				}
				else if(Persistence.IdType.CID.isInRange(objectId))
				{
					if(objectId >= maxConstantId)
					{
						maxConstantId = objectId;
					}
				}
				else
				{
					throw new StorageException("Invalid OID: " + objectId);
				}
			}

			return StorageIdAnalysis.New(maxTypeId, maxObjectId, maxConstantId);
		}

		@Override
		public String toString()
		{
			return "Ch#" + this.channelIndex + "_"
				+ (this.typeHandler == null ? "<Dummy Type>"  : this.typeHandler.toString())
			;
		}

	}

}
