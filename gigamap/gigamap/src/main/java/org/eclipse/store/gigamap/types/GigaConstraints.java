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

import org.eclipse.serializer.persistence.binary.types.BinaryTypeHandler;
import org.eclipse.serializer.persistence.types.Storer;

/**
 * Defines a set of constraints that can be applied to the elements of a {@link GigaMap}.
 *
 * @param <E> the type of elements to which the constraints apply
 */
public interface GigaConstraints<E> extends GigaMap.Component<E>
{
	/**
	 * Returns the UniqueConstraints associated with this GigaConstraints instance.
	 * This method allows the management and definition of unique constraints for entities.
	 *
	 * @return the UniqueConstraints instance for managing unique constraints.
	 */
	public UniqueConstraints<E> unique();
	
	/**
	 * Provides access to the custom constraints associated with this instance of GigaConstraints.
	 * This method allows tailored or entity-specific constraint handling as needed.
	 *
	 * @return the CustomConstraints instance for managing custom constraints.
	 */
	public CustomConstraints<E> custom();
	
	/**
	 * Validates specific conditions or constraints for an entity. This method performs checks
	 * to ensure that the entity adheres to the required rules, taking into consideration
	 * the current state of the entity and any replacement entity.
	 *
	 * @param entityId the unique identifier of the entity being checked.
	 * @param replacedEntity the entity that is being replaced, if applicable. This could be null
	 *                       if no entity replacement is involved.
	 * @param entity the current entity being validated.
	 */
	public void check(long entityId, E replacedEntity, E entity);
	
	
		
	public final class Default<E> extends AbstractStateChangeFlagged implements GigaConstraints<E>
	{
		static BinaryTypeHandler<Default<?>> provideTypeHandler()
		{
			return BinaryHandlerGigaConstraintsDefault.New();
		}
		
		
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////
		
		private final BitmapIndices<E>             uniqueConstraints;
		private final CustomConstraints.Default<E> customConstraints;
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////
		
		Default(
			final BitmapIndices<E>             uniqueConstraints,
			final CustomConstraints.Default<E> customConstraints
		)
		{
			this(uniqueConstraints, customConstraints, true);
		}
		

		Default(
			final BitmapIndices<E>             uniqueConstraints,
			final CustomConstraints.Default<E> customConstraints,
			final boolean                      stateChanged
		)
		{
			super(stateChanged);
			this.uniqueConstraints = uniqueConstraints;
			this.customConstraints = customConstraints;
		}
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////
		
		@Override
		public final GigaMap<E> parentMap()
		{
			return this.uniqueConstraints.parentMap();
		}
		
		@Override
		public final UniqueConstraints<E> unique()
		{
			return this.uniqueConstraints;
		}
		
		@Override
		public final CustomConstraints<E> custom()
		{
			return this.customConstraints;
		}
		
		@Override
		public final void check(final long entityId, final E replacedEntity, final E entity)
		{
			this.uniqueConstraints.check(entityId, replacedEntity, entity);
			this.customConstraints.check(entityId, replacedEntity, entity);
		}
		
		protected final void reportChildStateChange()
		{
			this.markStateChangeChildren();
		}
		
		@Override
		protected void clearChildrenStateChangeMarkers()
		{
			// note: uniqueConstraints is the bitmapIndices instance, which is managed via indices.
			
			this.customConstraints.clearStateChangeMarkers();
		}
		
		@Override
		protected final void storeChildren(final Storer storer)
		{
			// this is just a local, partial lock that does NOT protect the whole giga map storing process. See GigaMap#store.
			synchronized(this.parentMap())
			{
				super.storeChildren(storer);
			}
		}
						
		@Override
		protected void storeChangedChildren(final Storer storer)
		{
			// note: uniqueConstraints is the bitmapIndices instance, which is managed via indices.
			
			if(this.customConstraints.isChangedAndNotNew())
			{
				// must take a detour over the TypeHandler because of interface typing
				storer.store(this.customConstraints);
			}
		}
		
	}
	
	
	/**
	 * Represents a generic interface for implementing checks or validations on entities.
	 * This interface provides a method to validate certain conditions or constraints
	 * associated with entities during creation, updating, or replacement operations.
	 *
	 * @param <E> the type of entity to be checked.
	 */
	public interface Check<E>
	{
		/**
		 * Validates or performs a check on an entity with a specific identifier, considering both
		 * the replaced entity and the new entity.
		 *
		 * @param entityId the unique identifier of the entity being checked
		 * @param replacedEntity the entity which is being replaced, can be null if not applicable
		 * @param entity the new entity to be checked, must not be null
		 */
		public void check(long entityId, E replacedEntity, E entity);
	}
	
	
	/**
	 * Represents a category of constraints that can be applied to entities.
	 * A category defines a specific type of validation or condition that entities must adhere to
	 * during creation, updating, or replacement operations.
	 * <p>
	 * It extends both the Check interface,
	 * which allows for validation, and the GigaMap.Component interface, providing a link to the
	 * parent GigaMap.
	 *
	 * @param <E> the type of entities this category handles
	 */
	public interface Category<E> extends Check<E>, GigaMap.Component<E>
	{
		/**
		 * Determines whether a constraint is violated for a given entity operation.
		 * This validation checks whether replacing or modifying the specified entity
		 * with the provided new entity would breach any defined rules or conditions
		 * within the category.
		 *
		 * @param entityId the unique identifier of the entity being assessed
		 * @param replacedEntity the existing entity that is being replaced or modified
		 * @param entity the new entity that replaces or interacts with the existing entity
		 * @return true if the operation violates the constraint, false otherwise
		 */
		public boolean isViolated(long entityId, E replacedEntity, E entity);
	}
	
}
