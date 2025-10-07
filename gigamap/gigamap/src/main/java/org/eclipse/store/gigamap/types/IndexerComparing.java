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

import java.util.Comparator;

/**
 * An interface providing indexing capabilities for entities combined with a {@link java.util.Comparator}.
 * <p>
 * By default, {@code null} values are treated as unknown, meaning conditions always evaluate to {@code false}.
 * <p>
 * This interface extends {@link Indexer} and adds methods to create conditions based on
 * comparisons (such as greater than, less than, and ranges).
 *
 * @param <E> the type of entities being indexed
 * @param <K> the type of the key
 */
public interface IndexerComparing<E, K> extends Indexer<E, K>
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
     * @param endInclusive   the inclusive upper bound of the range
     * @return a new condition that enforces the specified range constraint
     */
    public <S extends E> Condition<S> between(final K startInclusive, final K endInclusive);


    /**
     * A generic abstract class that extends the functionality of {@link Indexer.Abstract} and implements
     * the {@link IndexerComparing} interface. This class provides methods for creating conditions
     * based on comparisons of entity keys of type K.
     * <p>
     * By default {@link Comparator#naturalOrder()} is used for comparisons, see {@link #comparator()}.
     *
     * @param <E> the type of entities indexed by this class
     * @param <K> the type of the key used for comparison
     */
    public static abstract class Abstract<E, K> extends Indexer.Abstract<E, K> implements IndexerComparing<E, K>
    {
        protected Abstract()
        {
            super();
        }

        /**
         * Provides a comparator for elements of type {@code K}.
         * By default, the comparator returned is the natural order comparator.
         *
         * @return a comparator for elements of type {@code K}
         */
        @SuppressWarnings("unchecked")
        protected Comparator<K> comparator()
        {
            return (Comparator<K>) Comparator.naturalOrder();
        }

        /**
         * Creates a condition which checks if the key is less than a specified value.
         *
         * @param boundExclusive the exclusive upper bound that the key must be less than
         * @return a new condition that enforces the specified constraint
         */
        @Override
        public <S extends E> Condition<S> lessThan(final K boundExclusive)
        {
            final Comparator<K> comparator = this.comparator();
            return this.is(key ->
            {
                if (key == null || boundExclusive == null)
                {
                    return false; // null is always treated as unknown
                }
                return comparator.compare(key, boundExclusive) < 0;
            });
        }

        /**
         * Creates a condition which checks if the key is less than or equal to a specified value.
         *
         * @param boundInclusive the inclusive upper bound that the key must be less than or equal to
         * @return a new condition that enforces the specified constraint
         */
        @Override
        public <S extends E> Condition<S> lessThanEqual(final K boundInclusive)
        {
            final Comparator<K> comparator = this.comparator();
            return this.is(key ->
            {
                if (key == null || boundInclusive == null)
                {
                    return false; // null is always treated as unknown
                }
                return comparator.compare(key, boundInclusive) <= 0;
            });
        }

        /**
         * Creates a condition which checks if the key is greater than a specified value.
         *
         * @param boundExclusive the exclusive lower bound that the key must be greater than
         * @return a new condition that enforces the specified constraint
         */
        @Override
        public <S extends E> Condition<S> greaterThan(final K boundExclusive)
        {
            final Comparator<K> comparator = this.comparator();
            return this.is(key ->
            {
                if (key == null || boundExclusive == null)
                {
                    return false; // null is always treated as unknown
                }
                return comparator.compare(key, boundExclusive) > 0;
            });
        }

        /**
         * Creates a condition which checks if the key is greater than or equal to a specified value.
         *
         * @param boundInclusive the inclusive lower bound that the key must be greater than or equal to
         * @return a new condition that enforces the specified constraint
         */
        @Override
        public <S extends E> Condition<S> greaterThanEqual(final K boundInclusive)
        {
            final Comparator<K> comparator = this.comparator();
            return this.is(key ->
            {
                if (key == null || boundInclusive == null)
                {
                    return false; // null is always treated as unknown
                }
                return comparator.compare(key, boundInclusive) >= 0;
            });
        }

        /**
         * Creates a condition which checks if the key is within the specified inclusive range.
         *
         * @param startInclusive the inclusive lower bound of the range
         * @param endInclusive   the inclusive upper bound of the range
         * @return a new condition that enforces the specified range constraint
         */
        @Override
        public <S extends E> Condition<S> between(final K startInclusive, final K endInclusive)
        {
            final Comparator<K> comparator = this.comparator();
            return this.is(key ->
            {
                if (key == null || startInclusive == null || endInclusive == null)
                {
                    return false; // null is always treated as unknown
                }
                return comparator.compare(key, startInclusive) >= 0
                    && comparator.compare(key, endInclusive) <= 0;
            });
        }

    }

}
