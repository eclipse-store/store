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

import org.eclipse.store.gigamap.exceptions.ConstraintViolationException;
import org.eclipse.serializer.util.X;

import java.util.function.Predicate;

/**
 * Represents a customizable constraint which can validate conditions
 * for an entity within the given context of a {@link GigaMap}. Provides an
 * interface for defining logic to verify whether the constraint is violated.
 *
 * @param <E> the type of entity being validated.
 */
public interface CustomConstraint<E>
{
	/**
	 * Retrieves the name of the custom constraint.
	 *
	 * @return the name of the custom constraint as a String
	 */
	public String name();
	
	/**
	 * Evaluates whether a constraint is violated based on the provided parameters
	 * and optionally creates a corresponding exception if a violation occurs.
	 *
	 * @param gigaMap the GigaMap containing entities against which the check is performed
	 * @param entityId the ID of the entity being evaluated
	 * @param replacedEntity the original entity being replaced in the evaluation
	 * @param entity the new entity to be evaluated for constraint violation
	 * @param createException a flag indicating whether to create and return an exception if a violation is detected
	 * @return a RuntimeException if the constraint is violated and createException is true, otherwise null
	 */
	public RuntimeException isViolated(GigaMap<? extends E> gigaMap, long entityId, E replacedEntity, E entity, boolean createException);
	
	
	/**
	 * Represents a functional interface for defining logic to evaluate whether certain
	 * constraints are violated in the context of a GigaMap and its associated entities.
	 *
	 * @param <E> the type of the entities in the GigaMap
	 */
	@FunctionalInterface
	public interface Logic<E>
	{
		/**
		 * Evaluates whether a specific constraint is violated within the provided GigaMap
		 * context for a certain entity and its replacement, optionally creating an exception
		 * if the constraint is violated.
		 *
		 * @param gigaMap the GigaMap containing the entities to evaluate
		 * @param entityId the unique identifier of the entity being evaluated
		 * @param replacedEntity the entity that is intended to be replaced
		 * @param entity the replacement entity to evaluate
		 * @param createException a flag indicating whether to create and return an exception if the constraint is violated
		 * @return a RuntimeException if the constraint is violated and createException is true, otherwise null
		 */
		/* Note:
		 * This has no inheritance connection to CustomConstraint#isViolated for two reasons:
		 * 1.) extending CustomConstraint$Abstract does not need this type at all.
		 * 2.) an extising CustomConstraint should not be usable as a Logic for another CustomConstraint instance.
		 */
		public RuntimeException isViolated(GigaMap<? extends E> gigaMap, long entityId, E replacedEntity, E entity, boolean createException);
	}
	
	
	/**
	 * AbstractBase serves as a foundational abstract class for implementing
	 * the CustomConstraint interface. It provides a framework for defining
	 * constraints by offering a default implementation for retrieving the
	 * class name as the constraint's name.
	 *
	 * @param <E> the type of the entity that this constraint is intended to validate
	 */
	public abstract class AbstractBase<E> implements CustomConstraint<E>
	{
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////
		
		public AbstractBase()
		{
			super();
		}
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////

		@Override
		public final String name()
		{
			return this.getClass().getSimpleName();
		}
		
	}
	
	
	/**
	 * AbstractSimple is an abstract class that extends {@link AbstractBase} and provides
	 * additional functionality for defining constraints that can be evaluated
	 * directly on individual entities. It serves as a base to streamline the
	 * process of defining custom constraints by implementing a common pattern for
	 * checking and handling constraint violations.
	 *
	 * @param <E> the type of the entity that this constraint is intended to validate
	 */
	public abstract class AbstractSimple<E> extends AbstractBase<E>
	{
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////
		
		public AbstractSimple()
		{
			super();
		}
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////

		@Override
		public RuntimeException isViolated(
			final GigaMap<? extends E> gigaMap,
			final long                entityId,
			final E             replacedEntity,
			final E                     entity,
			final boolean      createException
		)
		{
			if(this.isViolated(entity))
			{
				return createException
					? new ConstraintViolationException(entityId, replacedEntity, entity)
					: X.BREAK()
				;
			}
			return null;
		}
		
		/**
		 * Evaluates whether the given entity violates a specific constraint.
		 *
		 * @param entity the entity to be validated against the constraint
		 * @return true if the constraint is violated by the provided entity, false otherwise
		 */
		public abstract boolean isViolated(E entity);
		
	}
	
	
	/**
	 * Abstract serves as an intermediate abstract class that extends {@link AbstractBase}
	 * and provides a partial implementation for custom constraint validation.
	 * It delegates the responsibility of enforcing specific constraint checks
	 * to the concrete subclasses by requiring the implementation of the `check` method.
	 *
	 * @param <E> the type of the entity that this constraint is intended to validate
	 */
	public abstract class Abstract<E> extends AbstractBase<E>
	{
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////
		
		public Abstract()
		{
			super();
		}
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////

		@Override
		public RuntimeException isViolated(
			final GigaMap<? extends E> gigaMap,
			final long                entityId,
			final E             replacedEntity,
			final E                     entity,
			final boolean      createException
		)
		{
			this.check(entityId, replacedEntity, entity);

			return null;
		}
		
		/**
		 * Performs a custom validation check on the specified entity based on the given parameters.
		 * This method is abstract and must be implemented by subclasses to enforce specific validation logic.
		 *
		 * @param entityId the unique identifier of the entity being checked
		 * @param replacedEntity the entity which is being replaced, or null if no replacement occurs
		 * @param entity the current entity that is being validated
		 */
		public abstract void check(long entityId, E replacedEntity, E entity);
		
	}
	
	
	/**
	 * The Wrapper class is a concrete implementation of {@link AbstractBase}, designed
	 * to encapsulate additional logic for evaluating constraints. This logic
	 * is provided through the {@link Logic} interface associated with the type
	 * parameter E.
	 *
	 * @param <E> the type of the entity that this constraint is intended to validate
	 */
	public final class Wrapper<E> extends AbstractBase<E>
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////
		
		private final Logic<? super E> logic;
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////
		
		/**
		 * Constructs a Wrapper instance that encapsulates the provided logic for evaluating constraints.
		 *
		 * @param logic the logic implementation used to evaluate constraints on entities,
		 *              represented as a {@link Logic} instance operating on or derived from the type E
		 */
		public Wrapper(final Logic<? super E> logic)
		{
			super();
			this.logic = logic;
		}
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////

		@Override
		public RuntimeException isViolated(
			final GigaMap<? extends E> gigaMap,
			final long                entityId,
			final E             replacedEntity,
			final E                     entity,
			final boolean      createException
		)
		{
			return this.logic.isViolated(gigaMap, entityId, replacedEntity, entity, createException);
		}
		
	}
	
	
	/**
	 * WrapperSimple is a final implementation of the {@link AbstractSimple} class.
	 * It is designed to encapsulate a specific logical condition, defined by a
	 * {@link Predicate}, which determines if a given entity violates a constraint.
	 *
	 * @param <E> the type of the entity that this wrapper is intended to validate
	 */
	public final class WrapperSimple<E> extends AbstractSimple<E>
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////
		
		private final Predicate<? super E> logic;
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////
		
		/**
		 * Constructs a new WrapperSimple with the given predicate logic.
		 * The predicate is used to encapsulate a specific logical condition
		 * for validating entities.
		 *
		 * @param logic the predicate that defines the validation logic for entities of type E
		 */
		public WrapperSimple(final Predicate<? super E> logic)
		{
			super();
			this.logic = logic;
		}
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////

		@Override
		public final boolean isViolated(final E entity)
		{
			return this.logic.test(entity);
		}
		
	}
	
}
