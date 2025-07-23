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
		
		public void clearStateChangeMarkers();
	}
	
}
