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
 * An interface providing indexing capabilities for entities using numerical keys.
 * This interface extends {@link Indexer} and adds methods to create conditions based on
 * comparisons of numerical key values (such as greater than, less than, and ranges).
 *
 * @param <E> the type of entities being indexed
 * @param <K> the numerical type of the key, which must extend {@link Number}
 */
public interface IndexerNumber<E, K extends Number> extends Indexer<E, K>
{
	/**
	 * Creates a condition which checks if the key is less than a specified value.
	 *
	 * @param boundExclusive the exclusive upper bound that the key must be less than
	 * @return a new condition that enforces the specified constraint
	 */
	public <S extends E> Condition<S> lessThan(final K boundExclusive);
	
	/**
	 * Creates a condition which checks if the key is less than or equal to a specified value.
	 *
	 * @param boundInclusive the inclusive upper bound that the key must be less than or equal to
	 * @return a new condition that enforces the specified constraint
	 */
	public <S extends E> Condition<S> lessThanEqual(final K boundInclusive);
	
	/**
	 * Creates a condition which checks if the key is greater than a specified value.
	 *
	 * @param boundExclusive the exclusive lower bound that the key must be greater than
	 * @return a new condition that enforces the specified constraint
	 */
	public <S extends E> Condition<S> greaterThan(final K boundExclusive);
	
	/**
	 * Creates a condition which checks if the key is greater than or equal to a specified value.
	 *
	 * @param boundInclusive the inclusive lower bound that the key must be greater than or equal to
	 * @return a new condition that enforces the specified constraint
	 */
	public <S extends E> Condition<S> greaterThanEqual(final K boundInclusive);
	
	/**
	 * Creates a condition which checks if the key is within the specified inclusive range.
	 *
	 * @param startInclusive the inclusive lower bound of the range
	 * @param endInclusive the inclusive upper bound of the range
	 * @return a new condition that enforces the specified range constraint
	 */
	public <S extends E> Condition<S> between(final K startInclusive, final K endInclusive);
	
}
