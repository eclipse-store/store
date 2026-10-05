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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;

/**
 * Dense mapping from a graph ordinal (= source entity id) to the vector store's internal entity id,
 * used by computed-mode {@link VectorIndex}es on the hot vector lookup path.
 * <p>
 * Graph ordinals are the GigaMap entity ids cast to {@code int}, so the key space is dense and
 * bounded by {@code highestUsedId() + 1}. A paged {@code long[]} indexed by ordinal therefore
 * replaces a boxed hash map: no hashing, no boxing, about 8 bytes per slot. Pages are allocated
 * on first write, so ordinal ranges that never carry a vector cost only a {@code null} directory
 * slot.
 * <p>
 * Each slot holds {@code storeId + 1}, so {@code 0} means "no vector" while store id {@code 0}
 * stays representable; {@link #get(long)} returns {@link #ABSENT} for an empty slot.
 * <p>
 * Concurrency: single writer, lock-free readers. All mutations ({@link #put(int, long)},
 * {@link #remove(int)}) must be serialized externally; in {@link VectorIndex} that is the parent
 * GigaMap monitor every mutation already holds. Readers take no lock: they read the page directory
 * through a volatile field and pages and slots with acquire semantics, the writer publishes new
 * pages and slot values with release semantics and a grown directory with a volatile write.
 * A grown directory shares its pages with the old one, so a reader still holding the old directory
 * observes later writes to existing pages as well.
 */
final class OrdinalStoreIdTable
{
    ///////////////////////////////////////////////////////////////////////////
    // constants //
    //////////////

    /**
     * Returned by {@link #get(long)} for an ordinal that has no stored vector.
     */
    static final long ABSENT = -1L;

    private static final int PAGE_SHIFT = 12;
    private static final int PAGE_SIZE  = 1 << PAGE_SHIFT;
    private static final int PAGE_MASK  = PAGE_SIZE - 1;

    // Enough pages to address every non-negative int ordinal.
    private static final int MAX_PAGE_COUNT = (Integer.MAX_VALUE >>> PAGE_SHIFT) + 1;

    private static final VarHandle PAGES = MethodHandles.arrayElementVarHandle(long[][].class);
    private static final VarHandle SLOTS = MethodHandles.arrayElementVarHandle(long[].class);



    ///////////////////////////////////////////////////////////////////////////
    // instance fields //
    ////////////////////

    private volatile long[][] pages = new long[0][];



    ///////////////////////////////////////////////////////////////////////////
    // methods //
    ////////////

    /**
     * Returns the store id mapped to {@code ordinal}, or {@link #ABSENT} if there is none.
     * Ordinals outside the {@code int} range are never mapped and yield {@link #ABSENT}.
     */
    long get(final long ordinal)
    {
        if(ordinal < 0 || ordinal > Integer.MAX_VALUE)
        {
            return ABSENT;
        }
        final long[][] pages     = this.pages;
        final int      pageIndex = (int)ordinal >>> PAGE_SHIFT;
        if(pageIndex >= pages.length)
        {
            return ABSENT;
        }
        final long[] page = (long[])PAGES.getAcquire(pages, pageIndex);
        if(page == null)
        {
            return ABSENT;
        }
        // An empty slot holds 0, which decodes to ABSENT.
        return (long)SLOTS.getAcquire(page, (int)ordinal & PAGE_MASK) - 1L;
    }

    /**
     * Maps {@code ordinal} to {@code storeId}, replacing any previous mapping. Writer only.
     */
    void put(
        final int  ordinal,
        final long storeId
    )
    {
        if(ordinal < 0)
        {
            throw new IllegalArgumentException("Negative ordinal: " + ordinal);
        }
        if(storeId < 0 || storeId == Long.MAX_VALUE)
        {
            throw new IllegalArgumentException("Store id out of range: " + storeId);
        }
        SLOTS.setRelease(this.pageForWrite(ordinal >>> PAGE_SHIFT), ordinal & PAGE_MASK, storeId + 1L);
    }

    /**
     * Removes the mapping of {@code ordinal}, if any. Writer only.
     */
    void remove(final int ordinal)
    {
        if(ordinal < 0)
        {
            return;
        }
        final long[][] pages     = this.pages;
        final int      pageIndex = ordinal >>> PAGE_SHIFT;
        if(pageIndex >= pages.length || pages[pageIndex] == null)
        {
            return;
        }
        SLOTS.setRelease(pages[pageIndex], ordinal & PAGE_MASK, 0L);
    }

    /**
     * Calls {@code consumer} for every mapping, in ascending ordinal order.
     */
    void forEach(final EntryConsumer consumer)
    {
        final long[][] pages = this.pages;
        for(int p = 0; p < pages.length; p++)
        {
            final long[] page = (long[])PAGES.getAcquire(pages, p);
            if(page == null)
            {
                continue;
            }
            final long base = (long)p << PAGE_SHIFT;
            for(int s = 0; s < PAGE_SIZE; s++)
            {
                final long value = (long)SLOTS.getAcquire(page, s);
                if(value != 0L)
                {
                    consumer.accept(base + s, value - 1L);
                }
            }
        }
    }

    private long[] pageForWrite(final int pageIndex)
    {
        long[][] pages = this.pages;
        if(pageIndex >= pages.length)
        {
            final int newLength = (int)Math.min(
                MAX_PAGE_COUNT,
                Math.max(pageIndex + 1L, pages.length * 2L)
            );
            // Publish the grown directory before any page or slot it carries is touched below.
            this.pages = pages = Arrays.copyOf(pages, newLength);
        }
        long[] page = pages[pageIndex];
        if(page == null)
        {
            PAGES.setRelease(pages, pageIndex, page = new long[PAGE_SIZE]);
        }
        return page;
    }



    ///////////////////////////////////////////////////////////////////////////
    // member types //
    /////////////////

    @FunctionalInterface
    interface EntryConsumer
    {
        void accept(
            long ordinal,
            long storeId
        );
    }

}
