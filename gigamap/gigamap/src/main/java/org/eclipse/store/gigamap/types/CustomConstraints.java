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

import org.eclipse.serializer.collections.EqHashTable;
import org.eclipse.serializer.persistence.binary.types.BinaryTypeHandler;
import org.eclipse.serializer.persistence.types.Storer;
import org.eclipse.serializer.util.X;

/**
 * Represents a category of custom constraints that can be applied to elements of
 * type {@code E}. This interface extends {@link GigaConstraints.Category} and provides
 * mechanisms to add and manage constraints.
 *
 * @param <E> the type of the elements to which the custom constraints are applied
 */
public interface CustomConstraints<E> extends GigaConstraints.Category<E>
{
	/**
	 * Adds a single custom constraint to the current set of constraints.
	 *
	 * @param constraint the constraint to be added, of type CustomConstraint that can be applied to elements
	 *                   of type E or its superclasses
	 * @return the updated CustomConstraints instance with the new constraint added
	 */
	public default CustomConstraints<E> addConstraint(final CustomConstraint<? super E> constraint)
	{
		// to avoid mostly redundant logic and since adding structural elements like constraints is very rare.
		return this.addConstraints(X.List(constraint));
	}
	
	/**
	 * Adds one or more custom constraints to the current set of constraints.
	 *
	 * @param constraints the constraints to be added, each of type CustomConstraint that can be applied
	 *                    to elements of type E or its superclasses
	 * @return the updated CustomConstraints instance with the new constraints added
	 */
	@SuppressWarnings("unchecked")
	public default CustomConstraints<E> addConstraints(final CustomConstraint<? super E>... constraints)
	{
		// to avoid mostly redundant logic and since adding structural elements like constraints is very rare.
		return this.addConstraints(X.List(constraints));
	}
	
	/**
	 * Adds multiple custom constraints to the current set of constraints.
	 *
	 * @param constraints an iterable collection of custom constraints to be added, each of type CustomConstraint
	 *                    that can be applied to elements of type E or its superclasses
	 * @return the updated CustomConstraints instance with the new constraints added
	 */
	public CustomConstraints<E> addConstraints(Iterable<? extends CustomConstraint<? super E>> constraints);
	
		
	
	public final class Default<E> extends AbstractStateChangeFlagged implements CustomConstraints<E>
	{
		///////////////////////////////////////////////////////////////////////////
		// static methods //
		///////////////////
		
		static BinaryTypeHandler<Default<?>> provideTypeHandler()
		{
			return BinaryHandlerCustomConstraintsDefault.New();
		}
		
		static <E> EqHashTable<String, CustomConstraint<? super E>> createConstraintsMap()
		{
			return EqHashTable.New();
		}
		
		public static <E> void addConstraintChecked(
			final EqHashTable<String, CustomConstraint<? super E>> constraints,
			final CustomConstraint<? super E>                      constraint
		)
		{
			if(constraints.add(constraint.name(), constraint))
			{
				return;
			}

			throw new IllegalArgumentException("Duplicate constraint names: " + constraint.name());
		}
		
		
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////
		
		private final GigaMap.Internal<E> parent;
		
		private EqHashTable<String, CustomConstraint<? super E>> elements;
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////
		
		Default(
			final GigaMap.Internal<E>                             parent  ,
			final EqHashTable<String, CustomConstraint<? super E>> elements
		)
		{
			this(parent, elements, true);
		}
		
		Default(
			final GigaMap.Internal<E>                             parent      ,
			final EqHashTable<String, CustomConstraint<? super E>> elements    ,
			final boolean                                          stateChanged
		)
		{
			super(stateChanged);
			this.parent   = parent  ;
			this.elements = elements;
		}
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////
		
		@Override
		public final GigaMap.Internal<E> parentMap()
		{
			return this.parent;
		}
		
		private void validateConstraintToAdd(final String indexName, final CustomConstraint<? super E> constraint)
		{
			if(indexName == null)
			{
				throw new IllegalArgumentException("Constraint name may not be null.");
			}
			
			if(constraint == null)
			{
				throw new IllegalArgumentException("Constraint may not be null.");
			}
			
			// if there are no constraints registered at all, the new one is implicitly valid.
			if(this.elements == null)
			{
				return;
			}
			
			// Index name may not be taken, yet.
			final CustomConstraint<? super E> index = this.elements.get(indexName);
			if(index == null)
			{
				return;
			}
			
			throw new IllegalArgumentException(CustomConstraint.class.getSimpleName() + " already registered for name \"" + index.name() + "\".");
		}
		
		@Override
		public final CustomConstraints<E> addConstraints(final Iterable<? extends CustomConstraint<? super E>> constraints)
		{
			synchronized(this.parentMap())
			{
				for(final CustomConstraint<? super E> constraint : constraints)
				{
					this.validateConstraintToAdd(constraint.name(), constraint);
				}
				
				this.validateForExistingEntities(constraints);
				
				if(this.elements == null)
				{
					this.elements = EqHashTable.New();
				}
				for(final CustomConstraint<? super E> constraint : constraints)
				{
					Default.addConstraintChecked(this.elements, constraint);
				}

				this.markStateChangeInstance();
				this.parent.internalReportConstraintsStateChange();
			}

			return this;
		}
		
		private void validateForExistingEntities(final Iterable<? extends CustomConstraint<? super E>> constraints)
		{
			this.parent.iterateIndexed((final long entityId, final E entity) ->
			{
				for(final CustomConstraint<? super E> constraint : constraints)
				{
					RuntimeException result;
					try
					{
						result = constraint.isViolated(this.parent, entityId, null, entity, true);
					}
					catch(final RuntimeException e)
					{
						// custom logic might throw an exception directly instead of complying with the API.
						result = e;
					}
					if(result != null)
					{
						throw result;
					}
				}
			});
		}

		@Override
		public final boolean isViolated(final long entityId, final E replacedEntity, final E entity)
		{
			synchronized(this.parentMap())
			{
				return this.internalCheckViolation(entityId, replacedEntity, entity, false) != null;
			}
		}

		@Override
		public final void check(final long entityId, final E replacedEntity, final E entity)
		{
			synchronized(this.parentMap())
			{
				final RuntimeException result = this.internalCheckViolation(entityId, replacedEntity, entity, true);
				if(result == null)
				{
					return;
				}
				
				throw result;
			}
		}
		
		protected final EqHashTable<String, CustomConstraint<? super E>> elements()
		{
			return this.elements;
		}
		
		protected final void internalSetElements(final EqHashTable<String, CustomConstraint<? super E>> elements)
		{
			this.elements = elements;
		}
		
		private boolean hasNoElements()
		{
			return this.elements == null || this.elements.isEmpty();
		}
		
		private RuntimeException internalCheckViolation(
			final long    entityId       ,
			final E       replacedEntity ,
			final E       entity         ,
			final boolean createException
		)
		{
			if(this.hasNoElements())
			{
				return null;
			}

			for(final CustomConstraint<? super E> constraint : this.elements.values())
			{
				RuntimeException result;
				try
				{
					result = constraint.isViolated(this.parent, entityId, replacedEntity, entity, createException);
				}
				catch(final RuntimeException e)
				{
					// custom logic might throw an exception directly instead of complying with the API.
					result = e;
				}

				if(result != null)
				{
					return result;
				}
			}

			return null;
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
		protected final void storeChangedChildren(final Storer storer)
		{
			// constraints themselves are not mutable (at least it's not supported)
		}
		
		@Override
		protected final void clearChildrenStateChangeMarkers()
		{
			// constraints themselves are not mutable (at least it's not supported)
		}
		
	}
	
}
