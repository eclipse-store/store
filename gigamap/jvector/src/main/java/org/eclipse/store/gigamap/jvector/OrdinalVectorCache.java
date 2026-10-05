package org.eclipse.store.gigamap.jvector;

/*-
 * #%L
 * EclipseStore GigaMap JVector
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

import io.github.jbellis.jvector.vector.types.VectorFloat;

import java.util.function.IntFunction;

/**
 * Per-query cache from a graph ordinal to its resolved vector, used by the caching vector values
 * ({@link GigaMapBackedVectorValues.Caching}, {@link EntityBackedVectorValues.Caching}).
 * <p>
 * A search scores up to several thousand distinct nodes, and a fresh cache is built for every
 * query. A boxed {@code Map<Integer, VectorFloat<?>>} pays an {@code Integer} and a map node per
 * visited node and resizes several times per query. This table is an open-addressing
 * {@code int} to {@code Object} map with linear probing instead: a query allocates its two arrays,
 * plus a few more if it outgrows the initial capacity.
 * <p>
 * A lookup that resolves to {@code null} (deleted entity, or no embedding) is cached as well,
 * so such an ordinal is resolved only once per query no matter how often the traversal visits it.
 * <p>
 * Not thread-safe. An instance belongs to a single query and is used by one thread at a time;
 * {@link VectorIndex} creates one per search, and the searcher it is handed to is thread-confined.
 */
final class OrdinalVectorCache
{
    ///////////////////////////////////////////////////////////////////////////
    // constants //
    //////////////

    private static final int MINIMUM_CAPACITY_BITS = 6;
    private static final int MAXIMUM_CAPACITY_BITS = 30;

    // 2^32 / golden ratio. Graph ordinals are entity ids, so they arrive in dense runs; multiplying
    // spreads such a run over the whole table, where masking the low bits would pack it into one
    // contiguous block and make linear probing degrade.
    private static final int FIBONACCI_MULTIPLIER = 0x9E3779B9;

    // Marks an ordinal whose lookup returned null. A null slot means "not cached yet".
    private static final Object MISSING = new Object();



    ///////////////////////////////////////////////////////////////////////////
    // instance fields //
    ////////////////////

    private int[]    keys    ;
    private Object[] values  ;
    private int      shift   ;
    private int      size    ;
    private int      resizeAt;



    ///////////////////////////////////////////////////////////////////////////
    // constructors //
    /////////////////

    /**
     * Creates a cache sized to hold {@code expectedSize} ordinals without growing.
     *
     * @param expectedSize the expected number of distinct ordinals; non-positive values yield the
     *                     minimum capacity
     */
    OrdinalVectorCache(final int expectedSize)
    {
        this.allocate(capacityBitsFor(expectedSize));
    }



    ///////////////////////////////////////////////////////////////////////////
    // methods //
    ////////////

    /**
     * Returns the vector cached for {@code ordinal}, resolving and caching it through
     * {@code loader} on the first request. A {@code null} result is cached too.
     *
     * @param ordinal the graph ordinal
     * @param loader  resolves an ordinal that is not cached yet; may return {@code null}
     * @return the vector for {@code ordinal}, or {@code null} if it has none
     */
    VectorFloat<?> computeIfAbsent(
        final int                         ordinal,
        final IntFunction<VectorFloat<?>> loader
    )
    {
        final int[]    keys   = this.keys  ;
        final Object[] values = this.values;
        final int      mask   = keys.length - 1;

        int index = this.indexOf(ordinal);
        Object value;
        while((value = values[index]) != null)
        {
            if(keys[index] == ordinal)
            {
                return value == MISSING
                    ? null
                    : (VectorFloat<?>)value
                ;
            }
            index = (index + 1) & mask;
        }

        final VectorFloat<?> loaded = loader.apply(ordinal);
        keys  [index] = ordinal;
        values[index] = loaded == null ? MISSING : loaded;
        if(++this.size > this.resizeAt)
        {
            this.grow();
        }
        return loaded;
    }

    /**
     * Returns the number of cached ordinals, including those cached as missing.
     */
    int size()
    {
        return this.size;
    }

    private int indexOf(final int ordinal)
    {
        return ordinal * FIBONACCI_MULTIPLIER >>> this.shift;
    }

    private void allocate(final int capacityBits)
    {
        final int capacity = 1 << capacityBits;
        this.keys     = new int   [capacity];
        this.values   = new Object[capacity];
        this.shift    = Integer.SIZE - capacityBits;
        // Load factor 0.5 keeps linear-probing runs short.
        this.resizeAt = capacity >>> 1;
    }

    private void grow()
    {
        final int capacityBits = Integer.SIZE - this.shift;
        if(capacityBits >= MAXIMUM_CAPACITY_BITS)
        {
            // Unreachable for a search, which scores far fewer nodes. Keep filling instead of
            // failing; the table stays correct as long as a free slot remains.
            this.resizeAt = Integer.MAX_VALUE;
            return;
        }

        final int[]    oldKeys   = this.keys  ;
        final Object[] oldValues = this.values;
        this.allocate(capacityBits + 1);

        final int[]    keys   = this.keys  ;
        final Object[] values = this.values;
        final int      mask   = keys.length - 1;
        for(int i = 0; i < oldValues.length; i++)
        {
            final Object value = oldValues[i];
            if(value == null)
            {
                continue;
            }
            int index = this.indexOf(oldKeys[i]);
            while(values[index] != null)
            {
                index = (index + 1) & mask;
            }
            keys  [index] = oldKeys[i];
            values[index] = value;
        }
    }

    private static int capacityBitsFor(final int expectedSize)
    {
        // Twice the expected size, so the expected entries fit below the 0.5 load factor.
        final long wanted = Math.max(1L, 2L * expectedSize);
        final int  bits   = Long.SIZE - Long.numberOfLeadingZeros(wanted - 1L);
        return Math.min(MAXIMUM_CAPACITY_BITS, Math.max(MINIMUM_CAPACITY_BITS, bits));
    }

}
