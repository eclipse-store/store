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
 * Common query interface for numeric indexers, independent of the underlying storage strategy.
 * <p>
 * Provides equality checks ({@code is}, {@code not}, {@code in}, {@code notIn}) and range queries
 * ({@code lessThan}, {@code lessThanEqual}, {@code greaterThan},
 * {@code greaterThanEqual}, {@code between}) for numeric keys.
 * <p>
 * This interface is implemented by multiple indexer types with different storage strategies:
 * <ul>
 * <li>{@link IndexerNumber} (via {@link IndexerComparing}) &mdash; hash-based, optimized for low-cardinality indices</li>
 * <li>{@link ByteIndexerNumber} &mdash; byte-decomposed composite, with efficient range queries</li>
 * </ul>
 *
 * @param <E> the entity type
 * @param <K> the numeric key type
 *
 * @see IndexerNumber
 * @see ByteIndexerNumber
 */
public interface NumberQueryable<E, K extends Number>
{
	/**
	 * Creates an equality condition for the given key.
	 *
	 * @param <S> the type of entity this condition applies to
	 * @param key the key to compare for equality
	 * @return a new condition representing the equality check
	 */
	public <S extends E> Condition<S> is(K key);

	/**
	 * Creates a negated equality condition for the given key.
	 *
	 * @param <S> the type of entity this condition applies to
	 * @param key the key to compare for inequality
	 * @return a new condition representing the inequality check
	 */
	public <S extends E> Condition<S> not(K key);

	/**
	 * Creates a condition that checks if the key is contained within the specified keys.
	 *
	 * @param <S> the type of entity this condition applies to
	 * @param keys the array of keys to compare to
	 * @return a new condition representing the containment check
	 */
	@SuppressWarnings("unchecked")
	public <S extends E> Condition<S> in(K... keys);

	/**
	 * Creates a condition that checks if the key is not contained within the specified keys.
	 *
	 * @param <S> the type of entity this condition applies to
	 * @param keys the array of keys to compare to
	 * @return a new condition representing the negated containment check
	 */
	@SuppressWarnings("unchecked")
	public <S extends E> Condition<S> notIn(K... keys);

	/**
	 * Creates a condition that checks if the key is strictly less than the given bound.
	 *
	 * @param <S> the type of entity this condition applies to
	 * @param boundExclusive the exclusive upper bound
	 * @return a new condition representing the less-than check
	 */
	public <S extends E> Condition<S> lessThan(K boundExclusive);

	/**
	 * Creates a condition that checks if the key is less than or equal to the given bound.
	 *
	 * @param <S> the type of entity this condition applies to
	 * @param boundInclusive the inclusive upper bound
	 * @return a new condition representing the less-than-or-equal check
	 */
	public <S extends E> Condition<S> lessThanEqual(K boundInclusive);

	/**
	 * Creates a condition that checks if the key is strictly greater than the given bound.
	 *
	 * @param <S> the type of entity this condition applies to
	 * @param boundExclusive the exclusive lower bound
	 * @return a new condition representing the greater-than check
	 */
	public <S extends E> Condition<S> greaterThan(K boundExclusive);

	/**
	 * Creates a condition that checks if the key is greater than or equal to the given bound.
	 *
	 * @param <S> the type of entity this condition applies to
	 * @param boundInclusive the inclusive lower bound
	 * @return a new condition representing the greater-than-or-equal check
	 */
	public <S extends E> Condition<S> greaterThanEqual(K boundInclusive);

	/**
	 * Creates a condition that checks if the key is within the specified range, inclusive.
	 *
	 * @param <S> the type of entity this condition applies to
	 * @param startInclusive the inclusive start of the range
	 * @param endInclusive the inclusive end of the range
	 * @return a new condition representing the between check
	 */
	public <S extends E> Condition<S> between(K startInclusive, K endInclusive);

}
