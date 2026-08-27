package org.eclipse.store.gigamap.types;

/*-
 * #%L
 * EclipseStore GigaMap
 * %%
 * Copyright (C) 2023 - 2026 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

/**
 * Resolves a numeric entity id to its actual entity instance within a {@link GigaMap}.
 * <p>
 * {@code EntityResolver} decouples the "find the entity for a given id" step from the query
 * execution machinery. This allows query consumers to plug in custom resolving behavior —
 * for example to collect, transform, or peek at entities as they are resolved — while still
 * relying on the surrounding query infrastructure for id matching.
 * <p>
 * The default resolver simply delegates to {@link GigaMap#get(long)}; wrapping variants add
 * side effects such as invoking an {@link EntryConsumer} for every resolved entity.
 *
 * @param <E> the entity type managed by the parent {@link GigaMap}
 *
 * @see GigaQuery#resolve(EntityResolver)
 */
public interface EntityResolver<E>
{
	/**
	 * Resolves the given entity id to its entity instance.
	 * <p>
	 * Returns {@code null} if no entity exists for the given id. This can happen legitimately
	 * during query execution, e.g. when top-level NOT conditions generate lookups for ids of
	 * already-removed entities.
	 *
	 * @param entityId the entity id to resolve
	 * @return the entity for the given id, or {@code null} if none exists
	 */
	public E get(long entityId);

	/**
	 * Returns the {@link GigaMap} this resolver resolves entities from.
	 *
	 * @return the parent {@link GigaMap}
	 */
	public GigaMap<E> parent();



	/**
	 * Base implementation of {@link EntityResolver} that delegates {@link #get(long)} to
	 * {@link GigaMap#get(long)}. Subclasses can override {@code get} to add side effects.
	 *
	 * @param <E> the entity type managed by the parent {@link GigaMap}
	 */
	abstract class Abstract<E> implements EntityResolver<E>
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////

		final GigaMap<E> parent;



		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		Abstract(final GigaMap<E> parent)
		{
			super();
			this.parent = parent;
		}



		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////

		@Override
		public final GigaMap<E> parent()
		{
			return this.parent;
		}

		@Override
		public E get(final long entityId)
		{
			return this.parent.get(entityId);
		}

	}

	/**
	 * Base implementation for {@link EntityResolver}s that additionally forward every resolved
	 * entity to an {@link EntryConsumer}.
	 *
	 * @param <E> the entity type managed by the parent {@link GigaMap}
	 */
	abstract class AbstractWrapping<E> extends EntityResolver.Abstract<E>
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////

		final EntryConsumer<? super E> consumer;



		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		AbstractWrapping(final GigaMap<E> parent, final EntryConsumer<? super E> consumer)
		{
			super(parent);
			this.consumer = consumer;
		}

	}

	/**
	 * Default wrapping {@link EntityResolver}. For every successfully resolved (non-{@code null})
	 * entity, the given {@link EntryConsumer} is invoked with the entity id and the entity.
	 *
	 * @param <E> the entity type managed by the parent {@link GigaMap}
	 */
	final class Default<E> extends EntityResolver.AbstractWrapping<E>
	{
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		Default(final GigaMap<E> parent, final EntryConsumer<? super E> acceptor)
		{
			super(parent, acceptor);
		}



		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////

		@Override
		public E get(final long entityId)
		{
			final E entity = super.get(entityId);
			if(entity != null)
			{
				// NOT condition arithmetic can cause lookups for entityIds of non-existing (removed) entities.
				this.consumer.accept(entityId, entity);
			}

			return entity;
		}

	}


	/**
	 * Peeking {@link EntityResolver}. For every successfully resolved (non-{@code null}) entity,
	 * the given {@link EntryConsumer} is invoked with the entity id and the entity, typically to
	 * observe or collect results without changing the resolving behavior itself.
	 *
	 * @param <E> the entity type managed by the parent {@link GigaMap}
	 */
	final class Peeking<E> extends EntityResolver.AbstractWrapping<E>
	{
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		Peeking(final GigaMap<E> parent, final EntryConsumer<? super E> collector)
		{
			super(parent, collector);
		}



		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////

		@Override
		public E get(final long entityId)
		{
			final E entity = super.get(entityId);
			if(entity != null)
			{
				// NOT condition arithmetic can cause lookups for entityIds of non-existing (removed) entities.
				this.consumer.accept(entityId, entity);
			}

			return entity;
		}

	}

}
