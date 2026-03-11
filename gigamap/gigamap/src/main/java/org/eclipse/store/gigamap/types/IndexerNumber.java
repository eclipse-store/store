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
 *
 * @param <E> the type of entities being indexed
 * @param <K> the numerical type of the key, which must extend {@link Number}
 */
public interface IndexerNumber<E, K extends Number> extends IndexerComparing<E, K>, NumberQueryable<E, K>
{
    // Explicit overrides to resolve "abstract vs default" conflict between
    // NumberQueryable (abstract) and IndexIdentifier (default).

    @Override
    default <S extends E> Condition<S> is(final K key)
    {
        return IndexerComparing.super.is(key);
    }

    @Override
    default <S extends E> Condition<S> not(final K key)
    {
        return IndexerComparing.super.not(key);
    }

    @SuppressWarnings("unchecked")
    @Override
    default <S extends E> Condition<S> in(final K... keys)
    {
        return IndexerComparing.super.in(keys);
    }

    @SuppressWarnings("unchecked")
    @Override
    default <S extends E> Condition<S> notIn(final K... keys)
    {
        return IndexerComparing.super.notIn(keys);
    }


    // Explicit overrides to resolve ambiguous methods between
    // IndexerComparing (abstract) and NumberQueryable (abstract).

    @Override
    <S extends E> Condition<S> lessThan(final K boundExclusive);

    @Override
    <S extends E> Condition<S> lessThanEqual(final K boundInclusive);

    @Override
    <S extends E> Condition<S> greaterThan(final K boundExclusive);

    @Override
    <S extends E> Condition<S> greaterThanEqual(final K boundInclusive);

    @Override
    <S extends E> Condition<S> between(final K startInclusive, final K endInclusive);
}
