package org.eclipse.store.gigamap.types;

/*-
 * #%L
 * EclipseStore GigaMap
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


/**
 * General typing interface for specialized index category types.
 * 
 * @param <E> the entity type
 */
public interface IndexGroup<E> extends GigaMap.Component<E>
{
	/**
	 * Mutable extension of an {@link IndexGroup}.
	 * 
	 * @param <E> the entity type
	 */
	public interface Internal<E> extends IndexGroup<E>
	{
		/**
		 * Adds an entity with its id to this index.
		 * 
		 * @param entityId the entity's id
		 * @param entity the entity to add
		 */
		public void internalAdd(long entityId, E entity);
		
		/**
		 * Adds entities starting with a certain id to this index.
		 * 
		 * @param firstEntityId the first id which will be incremented for remaining entities
		 * @param entities the entities to add
		 */
		public void internalAddAll(long firstEntityId, Iterable<? extends E> entities);
		
		/**
		 * Adds entities starting with a certain id to this index.
		 * 
		 * @param firstEntityId the first id which will be incremented for remaining entities
		 * @param entities the entities to add
		 */
		public void internalAddAll(long firstEntityId, E[] entities);
		
		public void internalPrepareIndicesUpdate(E replacedEntity);
		
		/**
		 * Updates all affected entries in the indices of this group.
		 *
		 * @param entityId the entity's id
		 * @param replacedEntity old entity
		 * @param entity new entity from which the key will be extracted
		 */
		public void internalUpdateIndices(long entityId, E replacedEntity, E entity, CustomConstraints<? super E> customConstraints);

		/**
		 * Variant of {@link #internalUpdateIndices(long, Object, Object, CustomConstraints)} that
		 * additionally states how a failure is to be handled.
		 * <p>
		 * With {@code removeOnFailure == true} the calling context signals that the entity was mutated
		 * in place and will be removed from the map if the update fails, so on a failure this group
		 * should de-index the entity's previous state (using the state prepared via
		 * {@link #internalPrepareIndicesUpdate(Object)}) before propagating the exception. With
		 * {@code false} the calling context guarantees that the entity's previous state stays in place
		 * on failure, so the group must leave its previous entries untouched.
		 * <p>
		 * The default implementation ignores the flag and delegates to the 4-arg variant, which is
		 * correct for groups without such cleanup capability.
		 *
		 * @param entityId the entity's id
		 * @param replacedEntity old entity
		 * @param entity new entity from which the key will be extracted
		 * @param customConstraints the custom constraints to check, may be {@code null}
		 * @param removeOnFailure whether a failed update is followed by the entity's removal
		 */
		public default void internalUpdateIndices(
			final long                         entityId         ,
			final E                            replacedEntity   ,
			final E                            entity           ,
			final CustomConstraints<? super E> customConstraints,
			final boolean                      removeOnFailure
		)
		{
			this.internalUpdateIndices(entityId, replacedEntity, entity, customConstraints);
		}

		public void internalFinishIndicesUpdate();
		
		/**
		 * Removes a entity from this index.
		 * 
		 * @param entityId the entity's id
		 * @param entity the entity to remove
		 */
		public void internalRemove(long entityId, E entity);
		
		/**
		 * Removes all entities from this index.
		 */
		public void internalRemoveAll();

		/**
		 * Lifecycle hook invoked by {@link GigaIndices.Default#register(IndexCategory)} immediately
		 * after this group has been added, while holding the parent-map lock, to let the group
		 * synchronize itself with entities that already exist in the map at registration time
		 * (back-fill). It is <b>not</b> called on deserialization, so a group that back-fills here
		 * will not re-index everything on restart.
		 * <p>
		 * The default is a no-op. Groups that are registered while the map is still empty (e.g. the
		 * bitmap group at build time) or that back-fill their individual indices when those are added
		 * (e.g. the vector group) do not override it.
		 */
		public default void internalOnRegistered()
		{
			// no-op
		}

		/**
		 * Rebuilds this group's index data from scratch, using the current state of all entities in the
		 * given parent map. Invoked by {@link GigaMap#reindex()} to recover from an index that drifted out
		 * of sync - typically because an indexed entity was mutated directly instead of through
		 * {@link GigaMap#update(long, java.util.function.Consumer)} /
		 * {@link GigaMap#apply(long, java.util.function.Function)}.
		 * <p>
		 * A full rebuild (clear + re-add) rather than a per-entity update replay is required because after a
		 * direct mutation the previous index key is no longer available, so only re-indexing from the
		 * current state is correct.
		 * <p>
		 * The default implementation drops all data via {@link #internalRemoveAll()} and re-adds every
		 * entity via {@link #internalAdd(long, Object)}, which is correct for in-memory groups (e.g. bitmap,
		 * vector). Groups with an external commit cost (e.g. Lucene) should override this to batch the
		 * re-add and commit once.
		 *
		 * @param parentMap the map whose entities this group indexes
		 */
		public default void internalReindex(final GigaMap<E> parentMap)
		{
			this.internalRemoveAll();
			parentMap.iterateIndexed((entityId, entity) -> this.internalAdd(entityId, entity));
		}

		public void clearStateChangeMarkers();
	}
	
}
