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

import io.github.jbellis.jvector.vector.VectorizationProvider;
import io.github.jbellis.jvector.vector.types.VectorFloat;
import io.github.jbellis.jvector.vector.types.VectorTypeSupport;
import org.junit.jupiter.api.Test;

import java.util.function.IntFunction;
import java.util.function.IntPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class OrdinalVectorCacheTest
{
    private static final VectorTypeSupport VECTOR_TYPE_SUPPORT =
        VectorizationProvider.getInstance().getVectorTypeSupport();

    @Test
    void computeIfAbsent_repeatedOrdinal_loadsOnceAndReturnsSameInstance()
    {
        final OrdinalVectorCache cache  = new OrdinalVectorCache(16);
        final CountingLoader     loader = new CountingLoader(ordinal -> true);

        final VectorFloat<?> first  = cache.computeIfAbsent(7, loader);
        final VectorFloat<?> second = cache.computeIfAbsent(7, loader);

        assertNotNull(first);
        assertSame(first, second);
        assertEquals(1, loader.calls);
        assertEquals(1, cache.size());
    }

    @Test
    void computeIfAbsent_loaderReturnsNull_cachesTheMiss()
    {
        final OrdinalVectorCache cache  = new OrdinalVectorCache(16);
        final CountingLoader     loader = new CountingLoader(ordinal -> false);

        assertNull(cache.computeIfAbsent(3, loader));
        assertNull(cache.computeIfAbsent(3, loader));
        assertNull(cache.computeIfAbsent(3, loader));

        assertEquals(1, loader.calls);
        assertEquals(1, cache.size());
    }

    @Test
    void computeIfAbsent_extremeOrdinals_areKeptApart()
    {
        final OrdinalVectorCache cache    = new OrdinalVectorCache(4);
        final CountingLoader     loader   = new CountingLoader(ordinal -> true);
        final int[]              ordinals = {0, 1, 64, 1 << 26, Integer.MAX_VALUE};

        final VectorFloat<?>[] loaded = new VectorFloat<?>[ordinals.length];
        for(int i = 0; i < ordinals.length; i++)
        {
            loaded[i] = cache.computeIfAbsent(ordinals[i], loader);
        }

        for(int i = 0; i < ordinals.length; i++)
        {
            final VectorFloat<?> cached = cache.computeIfAbsent(ordinals[i], loader);
            assertSame(loaded[i], cached);
            assertEquals((float)ordinals[i], cached.get(0));
        }
        assertEquals(ordinals.length, loader.calls);
        assertEquals(ordinals.length, cache.size());
    }

    @Test
    void computeIfAbsent_beyondInitialCapacity_growsAndKeepsEveryEntry()
    {
        final int                count  = 10_000;
        final OrdinalVectorCache cache  = new OrdinalVectorCache(1);
        // Every third ordinal has no vector, so cached misses must survive the rehash too.
        final CountingLoader     loader = new CountingLoader(ordinal -> ordinal % 3 != 0);

        final VectorFloat<?>[] loaded = new VectorFloat<?>[2 * count];
        for(int i = 0; i < count; i++)
        {
            // A dense run, as graph ordinals are entity ids, and a strided one.
            loaded[i]         = cache.computeIfAbsent(i, loader);
            loaded[count + i] = cache.computeIfAbsent(count + i * 4096, loader);
        }

        for(int i = 0; i < count; i++)
        {
            assertSame(loaded[i], cache.computeIfAbsent(i, loader));
            assertSame(loaded[count + i], cache.computeIfAbsent(count + i * 4096, loader));
        }
        assertEquals(2 * count, loader.calls);
        assertEquals(2 * count, cache.size());
    }

    @Test
    void constructor_hugeExpectedSize_capsInitialCapacityAndGrowsOnDemand()
    {
        final CountingLoader loader = new CountingLoader(ordinal -> true);

        // The expected size is the caller's search beam width, which may be any positive int.
        final OrdinalVectorCache cache = new OrdinalVectorCache(Integer.MAX_VALUE);
        final int initialCapacity = cache.capacity();
        // One entry past the 0.5 load factor triggers exactly one growth step.
        final int count = initialCapacity / 2 + 1;
        for(int i = 0; i < count; i++)
        {
            cache.computeIfAbsent(i, loader);
        }

        assertEquals(1 << 14, initialCapacity);
        assertEquals(2 * initialCapacity, cache.capacity());
        assertEquals(count, cache.size());
    }

    @Test
    void constructor_nonPositiveExpectedSize_yieldsUsableCache()
    {
        final CountingLoader loader = new CountingLoader(ordinal -> true);

        for(final int expectedSize : new int[]{0, -1, Integer.MIN_VALUE})
        {
            final OrdinalVectorCache cache = new OrdinalVectorCache(expectedSize);

            assertNotNull(cache.computeIfAbsent(42, loader));
            assertEquals(1, cache.size());
        }
    }

    /**
     * Loads a one-component vector holding the ordinal, or {@code null} where {@code hasVector}
     * says the ordinal has none, and counts its calls.
     */
    private static final class CountingLoader implements IntFunction<VectorFloat<?>>
    {
        private final IntPredicate hasVector;
        private       int          calls    ;

        CountingLoader(final IntPredicate hasVector)
        {
            this.hasVector = hasVector;
        }

        @Override
        public VectorFloat<?> apply(final int ordinal)
        {
            this.calls++;
            return this.hasVector.test(ordinal)
                ? VECTOR_TYPE_SUPPORT.createFloatVector(new float[]{ordinal})
                : null
            ;
        }
    }

}
