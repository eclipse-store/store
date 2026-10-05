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

import io.github.jbellis.jvector.util.Bits;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link DiskSupersededOrdinals}, the incremental-mode disk deletion mask.
 */
class DiskSupersededOrdinalsTest
{
    @Test
    void acceptBits_newInstance_isEmptyAndAcceptsAll()
    {
        final DiskSupersededOrdinals superseded = DiskSupersededOrdinals.New(100);

        assertTrue(superseded.isEmpty());
        assertSame(Bits.ALL, superseded.acceptBits());
    }

    @Test
    void add_ordinalInRange_rejectsOnlyThatOrdinal()
    {
        final DiskSupersededOrdinals superseded = DiskSupersededOrdinals.New(200);

        superseded.add(64);
        superseded.add(199);

        final Bits acceptBits = superseded.acceptBits();
        assertFalse(superseded.isEmpty());
        assertFalse(acceptBits.get(64));
        assertFalse(acceptBits.get(199));
        assertTrue(acceptBits.get(0));
        assertTrue(acceptBits.get(63));
        assertTrue(acceptBits.get(65));
        assertTrue(acceptBits.get(198));
    }

    @Test
    void add_ordinalOutsideDiskGraph_isIgnored()
    {
        final DiskSupersededOrdinals superseded = DiskSupersededOrdinals.New(100);

        superseded.add(-1);
        superseded.add(100);
        superseded.add(Integer.MAX_VALUE);

        assertTrue(superseded.isEmpty());
        assertSame(Bits.ALL, superseded.acceptBits());
    }

    @Test
    void acceptBits_ordinalOutsideDiskGraph_acceptsWithoutThrowing()
    {
        final DiskSupersededOrdinals superseded = DiskSupersededOrdinals.New(100);
        superseded.add(5);

        final Bits acceptBits = superseded.acceptBits();

        assertTrue(acceptBits.get(-1));
        assertTrue(acceptBits.get(100));
        assertTrue(acceptBits.get(200));
        assertTrue(acceptBits.get(Integer.MAX_VALUE));
    }

    @Test
    void acceptBits_nonEmpty_returnsSameInstanceThatSeesLaterAdds()
    {
        final DiskSupersededOrdinals superseded = DiskSupersededOrdinals.New(100);
        superseded.add(1);

        final Bits first = superseded.acceptBits();
        superseded.add(2);
        final Bits second = superseded.acceptBits();

        assertSame(first, second);
        assertFalse(first.get(2));
    }

    @Test
    void add_emptyDiskGraph_isIgnored()
    {
        final DiskSupersededOrdinals superseded = DiskSupersededOrdinals.New(0);

        superseded.add(0);

        assertTrue(superseded.isEmpty());
        assertSame(Bits.ALL, superseded.acceptBits());
    }

    @Test
    void new_negativeIdUpperBound_throws()
    {
        assertThrows(IllegalArgumentException.class, () -> DiskSupersededOrdinals.New(-1));
    }

    @Test
    void add_concurrentFromManyThreads_allOrdinalsRejected() throws Exception
    {
        final int                    threadCount  = 8;
        final int                    idUpperBound = 100_000;
        final DiskSupersededOrdinals superseded   = DiskSupersededOrdinals.New(idUpperBound);
        final CountDownLatch         start        = new CountDownLatch(1);
        final ExecutorService        executor     = Executors.newFixedThreadPool(threadCount);
        try
        {
            final List<Future<?>> futures = new ArrayList<>();
            for(int t = 0; t < threadCount; t++)
            {
                final int offset = t;
                futures.add(executor.submit(() ->
                {
                    start.await();
                    // Threads interleave within the same 64-bit words, so lost CAS updates would show.
                    for(int ordinal = offset; ordinal < idUpperBound; ordinal += threadCount)
                    {
                        superseded.add(ordinal);
                    }
                    return null;
                }));
            }

            start.countDown();
            for(final Future<?> future : futures)
            {
                future.get();
            }
        }
        finally
        {
            executor.shutdownNow();
        }

        final Bits acceptBits = superseded.acceptBits();
        for(int ordinal = 0; ordinal < idUpperBound; ordinal++)
        {
            assertFalse(acceptBits.get(ordinal), "ordinal " + ordinal);
        }
    }
}
