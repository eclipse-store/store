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

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.eclipse.store.gigamap.jvector.OrdinalStoreIdTable.ABSENT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link OrdinalStoreIdTable}, the computed-mode {@code ordinal -> storeId} table.
 */
class OrdinalStoreIdTableTest
{
    @Test
    void get_emptyTable_returnsAbsent()
    {
        final OrdinalStoreIdTable table = new OrdinalStoreIdTable();
        assertEquals(ABSENT, table.get(0));
        assertEquals(ABSENT, table.get(12_345));
        table.forEach((o, s) -> { throw new AssertionError("no entries expected"); });
    }

    @Test
    void put_storeIdZero_isRepresentable()
    {
        final OrdinalStoreIdTable table = new OrdinalStoreIdTable();
        table.put(7, 0L);
        assertEquals(0L, table.get(7));
        assertEquals(ABSENT, table.get(6));
        assertEquals(ABSENT, table.get(8));
    }

    @Test
    void put_acrossPageBoundaries_growsAndReadsBack()
    {
        final OrdinalStoreIdTable table    = new OrdinalStoreIdTable();
        final int[]               ordinals = {0, 4095, 4096, 4097, 8191, 8192, 1_000_000, Integer.MAX_VALUE};
        for(final int ordinal : ordinals)
        {
            table.put(ordinal, ordinal * 3L);
        }
        for(final int ordinal : ordinals)
        {
            assertEquals(ordinal * 3L, table.get(ordinal), "ordinal " + ordinal);
        }
        assertEquals(ABSENT, table.get(4098));
        assertEquals(ABSENT, table.get(Integer.MAX_VALUE - 1L));
    }

    @Test
    void putAndRemove_sameOrdinal_replacesThenClears()
    {
        final OrdinalStoreIdTable table = new OrdinalStoreIdTable();
        table.put(42, 1L);
        table.put(42, 99L);
        assertEquals(99L, table.get(42));

        table.remove(42);
        assertEquals(ABSENT, table.get(42));

        // Removing what was never mapped, including beyond the allocated range, is a no-op.
        table.remove(43);
        table.remove(10_000_000);
        table.remove(-1);
        assertEquals(ABSENT, table.get(10_000_000));
    }

    @Test
    void get_outOfRangeOrdinals_returnsAbsent()
    {
        final OrdinalStoreIdTable table = new OrdinalStoreIdTable();
        table.put(0, 5L);
        assertEquals(ABSENT, table.get(-1L));
        assertEquals(ABSENT, table.get(Integer.MAX_VALUE + 1L));
        assertEquals(ABSENT, table.get(Long.MAX_VALUE));
    }

    @Test
    void put_invalidArguments_throws()
    {
        final OrdinalStoreIdTable table = new OrdinalStoreIdTable();
        assertThrows(IllegalArgumentException.class, () -> table.put(-1, 0L));
        assertThrows(IllegalArgumentException.class, () -> table.put(0, -1L));
        assertThrows(IllegalArgumentException.class, () -> table.put(0, Long.MAX_VALUE));
    }

    @Test
    void forEach_sparseMappings_visitsInOrdinalOrder()
    {
        final OrdinalStoreIdTable table = new OrdinalStoreIdTable();
        table.put(9000, 2L);
        table.put(3, 0L);
        table.put(4096, 1L);
        table.put(5, 7L);
        table.remove(5);

        final List<long[]> seen = new ArrayList<>();
        table.forEach((ordinal, storeId) -> seen.add(new long[]{ordinal, storeId}));

        assertEquals(3, seen.size());
        assertEquals(3L,    seen.get(0)[0]);
        assertEquals(0L,    seen.get(0)[1]);
        assertEquals(4096L, seen.get(1)[0]);
        assertEquals(1L,    seen.get(1)[1]);
        assertEquals(9000L, seen.get(2)[0]);
        assertEquals(2L,    seen.get(2)[1]);
    }

    /**
     * One writer grows the table while readers poll it lock-free. A reader may see a mapping late,
     * but never a value the writer did not write for that ordinal.
     */
    @Test
    void get_concurrentWithGrowingWriter_neverSeesForeignValues() throws InterruptedException
    {
        final OrdinalStoreIdTable        table   = new OrdinalStoreIdTable();
        final int                        count   = 200_000;
        final AtomicBoolean              done    = new AtomicBoolean();
        final AtomicReference<Throwable> failure = new AtomicReference<>();

        final List<Thread> readers = new ArrayList<>();
        for(int r = 0; r < 4; r++)
        {
            final Thread reader = new Thread(() ->
            {
                try
                {
                    while(!done.get())
                    {
                        for(int ordinal = 0; ordinal < count; ordinal += 97)
                        {
                            final long storeId = table.get(ordinal);
                            if(storeId != ABSENT && storeId != expectedStoreId(ordinal))
                            {
                                throw new AssertionError("ordinal " + ordinal + " -> " + storeId);
                            }
                        }
                    }
                }
                catch(final Throwable t)
                {
                    failure.compareAndSet(null, t);
                }
            });
            reader.start();
            readers.add(reader);
        }

        for(int ordinal = 0; ordinal < count; ordinal++)
        {
            table.put(ordinal, expectedStoreId(ordinal));
        }
        done.set(true);
        for(final Thread reader : readers)
        {
            reader.join();
        }

        assertNull(failure.get());
        for(int ordinal = 0; ordinal < count; ordinal++)
        {
            assertEquals(expectedStoreId(ordinal), table.get(ordinal));
        }
    }

    private static long expectedStoreId(final int ordinal)
    {
        return ordinal * 2L + 1L;
    }

}
