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

import org.eclipse.serializer.collections.types.XImmutableEnum;
import org.eclipse.serializer.util.X;

import java.util.function.Consumer;

/**
 * Represents an interface for managing unique constraints on a category of elements.
 * Provides methods to add and access unique constraints utilizing indexing mechanisms.
 * It extends the functionality of GigaConstraints.Category with additional capabilities.
 *
 * @param <E> the type of elements for which the unique constraints are applied
 */
public interface UniqueConstraints<E> extends GigaConstraints.Category<E>
{
	/**
	 * Adds a unique constraint to the current set of constraints using the provided index name and indexer.
	 * This constraint ensures that the indexed values for the specified property are unique across all elements.
	 *
	 * @param indexName the name of the unique index to be created
	 * @param indexer the indexer used to extract the property from elements for indexing
	 * @return the updated instance of {@code UniqueConstraints<E>} with the new unique constraint applied
	 */
	public UniqueConstraints<E> addUniqueConstraint(String indexName, Indexer<? super E, ?> indexer);
	
	/**
	 * Adds a unique constraint to the current set of constraints using the given indexer.
	 * This method ensures that the indexed values for the specified property are unique
	 * across all elements, utilizing the name from the provided indexer.
	 *
	 * @param indexer the indexer used to extract the property from elements for indexing
	 * @return the updated instance of {@code UniqueConstraints<E>} with the new unique constraint applied
	 */
	public default UniqueConstraints<E> addUniqueConstraint(final Indexer<? super E, ?> indexer)
	{
		return this.addUniqueConstraint(indexer.name(), indexer);
	}
	
	/**
	 * Adds multiple unique constraints to the current set of constraints using the provided indexers.
	 * Each unique constraint ensures that the indexed values for the specified properties
	 * are unique across all elements.
	 *
	 * @param indexers an array of indexers used to extract properties from elements for indexing
	 * @return the updated instance of {@code UniqueConstraints<E>} with the new unique constraints applied
	 */
	@SuppressWarnings("unchecked")
	public default UniqueConstraints<E> addUniqueConstraints(final Indexer<? super E, ?>... indexers)
	{
		return this.addUniqueConstraints(X.List(indexers));
	}
	
	/**
	 * Adds multiple unique constraints to the current set of constraints using the provided indexers.
	 * Each unique constraint ensures that the indexed values for the specified properties
	 * are unique across all elements in the context of the implementing category.
	 *
	 * @param indexers an iterable of indexers used to extract properties from elements for indexing
	 * @return the updated instance of {@code UniqueConstraints<E>} with the new unique constraints applied
	 */
	public UniqueConstraints<E> addUniqueConstraints(Iterable<? extends Indexer<? super E, ?>> indexers);

	/**
	 * Removes the unique constraint registered under the given index name, i.e. stops enforcing
	 * uniqueness for that index.
	 * <p>
	 * The underlying index is <b>not</b> removed: it remains registered as a regular, queryable
	 * bitmap index. To remove the index itself, use {@link BitmapIndices#removeIndex(String)}.
	 * <p>
	 * Because the demoted index keeps its name, it cannot be re-promoted directly via
	 * {@link #addUniqueConstraint(String, Indexer)} (that would fail on the already-registered name);
	 * remove the index first with {@link BitmapIndices#removeIndex(String)}, then add the unique
	 * constraint anew (which re-validates the current data).
	 *
	 * @param indexName the name of the unique constraint to remove
	 * @return {@code true} if a unique constraint with that name existed and was removed,
	 *         {@code false} otherwise
	 */
	public boolean removeUniqueConstraint(String indexName);

	/**
	 * Removes the unique constraint registered under the {@link Indexer#name() name} of the given
	 * indexer.
	 * <p>
	 * For details see {@link #removeUniqueConstraint(String)}.
	 *
	 * @param indexer the indexer whose name identifies the unique constraint to remove
	 * @return {@code true} if a unique constraint with that name existed and was removed,
	 *         {@code false} otherwise
	 */
	public default boolean removeUniqueConstraint(final Indexer<? super E, ?> indexer)
	{
		return this.removeUniqueConstraint(indexer.name());
	}

	/**
	 * Applies the provided logic to each unique constraint in the current category.
	 * The unique constraints are represented as instances of {@code XImmutableEnum} containing
	 * {@code GigaIndex} elements, which describe the indexing mechanisms used to enforce uniqueness.
	 *
	 * @param logic a {@code Consumer} that processes each {@code XImmutableEnum} instance
	 *              representing a unique constraint. The consumer takes one parameter, which
	 *              is a {@code XImmutableEnum} encapsulating a {@code GigaIndex} of the element type.
	 */
	public void accessUniqueConstraints(Consumer<? super XImmutableEnum<? extends GigaIndex<E>>> logic);
}
