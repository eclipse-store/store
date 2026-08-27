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

import java.util.function.Consumer;

/**
 * Executes a bitmap-based query by traversing the underlying {@link BitmapResult}s and feeding
 * every matching, non-{@code null} entity to a {@link Consumer}.
 * <p>
 * Unlike {@link BitmapIterator}, which cooperates with {@link java.util.Iterator} semantics and
 * stops at every hit to return the element, {@code BitmapIteration} performs a one-shot
 * traversal: {@link #handleEntityId(long)} always returns {@code false} so that the iteration
 * loop keeps advancing until all matching ids have been processed.
 * <p>
 * The matcher uses an {@link EntityResolver} to resolve entity ids to entity instances. Entities
 * that resolve to {@code null} (e.g. because they were removed) are silently skipped.
 *
 * @param <E> the entity type of the parent {@link GigaMap}
 *
 * @see BitmapIterator
 * @see EntityResolver
 */
public final class BitmapIteration<E> extends AbstractBitmapIterating<E>
{
	///////////////////////////////////////////////////////////////////////////
	// instance fields //
	////////////////////

	private final EntityResolver<E>   resolver;
	private final Consumer<? super E> consumer;



	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////

	BitmapIteration(
		final EntityIdMatcher     idMatcher,
		final EntityResolver<E>   resolver ,
		final long                idStart  ,
		final long                idBound  ,
		final BitmapResult[]      results  ,
		final Consumer<? super E> consumer
	)
	{
		// currentBitposition value is irrelevant
		super(idMatcher, idStart, idBound, results, -2);
		this.resolver = resolver;
		this.consumer = consumer;
	}



	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////

	@Override
	protected final int getCurrentBitPosition()
	{
		return -1;
	}

	@Override
	protected final void setCurrentBitPosition(final int currentBitPosition)
	{
		// no-op to leave currentBitPosition as a dummyValue
	}

	@Override
	protected boolean handleEntityId(final long entityId)
	{
		// Null check filters out null entries when using a top-level not condition. No measurable performance impact.
		final E entity = this.resolver.get(entityId);
		if(entity != null)
		{
			this.consumer.accept(entity);
		}

		return false;
	}

}
