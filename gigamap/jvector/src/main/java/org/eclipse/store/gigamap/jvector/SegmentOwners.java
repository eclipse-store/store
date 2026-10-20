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
 * Which source of an on-disk index holds the live copy of each entity: one byte per entity id,
 * holding the slot of the owning source or {@link #NONE}.
 * <p>
 * An on-disk index consists of several immutable segments, in-memory graphs frozen for a flush that
 * has not completed yet, and the live in-memory graph, and an entity can have a copy in more than
 * one of them. Each source accepts a node during search only if the owner of its entity is the
 * source's slot, so each entity is live in exactly one source. Every mutation of an entity
 * {@linkplain #set(int, int) sets} its owner, to the live graph's slot when the entity has a vector
 * and to {@link #NONE} when it has not, which hides every other copy at once. The owners are not
 * persisted: each segment keeps a sparse deletion set on disk, and the owners are derived from those
 * when the segments are loaded.
 * <p>
 * Ids are the graph ordinals, i.e. the source entity ids, so the array is indexed by id and paged:
 * a page is allocated only when an id in its range is given an owner, and a missing page reads as
 * {@link #NONE}. Ids in ranges no source holds therefore cost one {@code null} directory slot per
 * {@code 65536} ids.
 * <p>
 * Concurrency: lock-free reads, one writer of arbitrary owners. The writes happen while the owners are
 * derived at load, before they are published, and afterwards only under the parent GigaMap's monitor,
 * which every mutation of the index holds. Besides, a merge publishing its segment
 * {@linkplain #move(int, int, int) moves} owners from a merged segment's slot to its own without the
 * monitor, by compare-and-set on the slot, so it neither waits for the monitor nor overwrites a
 * mutation: whichever comes second sees the other's write. Readers see the directory through a volatile
 * field and pages and slots with acquire semantics; the writers publish them with release semantics.
 */
interface SegmentOwners
{
    /**
     * The owner of an entity that no segment holds a live copy of, either because no segment ever
     * held it or because it was mutated since.
     */
    public static final int NONE = 0xFF;

    /**
     * The highest slot a segment can have; slots are {@code 0..MAX_SLOT}.
     */
    public static final int MAX_SLOT = NONE - 1;

    /**
     * Returns the slot of the segment that owns {@code id}, or {@link #NONE}.
     *
     * @param id the entity id (= graph ordinal); negative ids are owned by nobody
     * @return the owning slot or {@link #NONE}
     */
    public int owner(int id);

    /**
     * Makes the source with {@code slot} the owner of {@code id}. Single writer, see the class
     * documentation.
     *
     * @param id   the entity id, not negative
     * @param slot the source slot, {@code 0..MAX_SLOT}
     * @throws IllegalArgumentException if either argument is out of range
     */
    public default void assign(
        final int id  ,
        final int slot
    )
    {
        if(slot == NONE)
        {
            throw new IllegalArgumentException("Source slot out of range 0.." + MAX_SLOT + ": " + slot);
        }
        this.set(id, slot);
    }

    /**
     * Sets the owner of {@code id} and returns the previous one. Single writer, see the class
     * documentation.
     *
     * @param id   the entity id, not negative
     * @param slot the source slot, {@code 0..MAX_SLOT}, or {@link #NONE} to make nobody the owner
     * @return the previous owner, a slot or {@link #NONE}
     * @throws IllegalArgumentException if either argument is out of range
     */
    public int set(
        int id  ,
        int slot
    );

    /**
     * Makes {@code to} the owner of {@code id} if {@code from} still is, atomically with respect to
     * {@link #set(int, int)}. Allocates nothing: an id owned by a slot has its page already.
     *
     * @param id   the entity id
     * @param from the expected owner, a slot
     * @param to   the new owner, a slot
     * @return whether the owner was {@code from} and is {@code to} now
     */
    public boolean move(
        int id  ,
        int from,
        int to
    );


    /**
     * Creates owners in which nobody owns anything.
     *
     * @return new, empty owners
     */
    public static SegmentOwners New()
    {
        return new Default();
    }


    /**
     * Default implementation, a paged {@code byte} array.
     */
    public static final class Default implements SegmentOwners
    {
        ///////////////////////////////////////////////////////////////////////////
        // constants //
        //////////////

        private static final int PAGE_SHIFT = 16;
        private static final int PAGE_SIZE  = 1 << PAGE_SHIFT;
        private static final int PAGE_MASK  = PAGE_SIZE - 1;

        // Enough pages to address every non-negative int id.
        private static final int MAX_PAGE_COUNT = (Integer.MAX_VALUE >>> PAGE_SHIFT) + 1;

        private static final byte NONE_BYTE = (byte)NONE;

        private static final VarHandle PAGES = MethodHandles.arrayElementVarHandle(byte[][].class);
        private static final VarHandle SLOTS = MethodHandles.arrayElementVarHandle(byte[].class);



        ///////////////////////////////////////////////////////////////////////////
        // instance fields //
        ////////////////////

        private volatile byte[][] pages = new byte[0][];



        ///////////////////////////////////////////////////////////////////////////
        // constructors //
        /////////////////

        Default()
        {
            super();
        }



        ///////////////////////////////////////////////////////////////////////////
        // methods //
        ////////////

        @Override
        public int owner(final int id)
        {
            final byte[] page = this.pageOf(id);
            return page == null
                ? NONE
                : (byte)SLOTS.getAcquire(page, id & PAGE_MASK) & 0xFF
            ;
        }

        @Override
        public int set(
            final int id  ,
            final int slot
        )
        {
            if(id < 0)
            {
                throw new IllegalArgumentException("Negative id: " + id);
            }
            if(slot < 0 || slot > NONE)
            {
                throw new IllegalArgumentException("Source slot out of range 0.." + NONE + ": " + slot);
            }
            if(slot == NONE)
            {
                // Never allocates: an id without a page is owned by nobody already.
                final byte[] page = this.pageOf(id);
                return page == null
                    ? NONE
                    : (byte)SLOTS.getAndSetRelease(page, id & PAGE_MASK, NONE_BYTE) & 0xFF
                ;
            }
            return (byte)SLOTS.getAndSetRelease(this.pageForWrite(id >>> PAGE_SHIFT), id & PAGE_MASK, (byte)slot)
                & 0xFF;
        }

        @Override
        public boolean move(
            final int id  ,
            final int from,
            final int to
        )
        {
            if(from < 0 || from >= NONE || to < 0 || to >= NONE)
            {
                throw new IllegalArgumentException("Source slots out of range 0.." + MAX_SLOT + ": " + from + ", " + to);
            }
            final byte[] page = this.pageOf(id);
            return page != null && SLOTS.compareAndSet(page, id & PAGE_MASK, (byte)from, (byte)to);
        }

        private byte[] pageOf(final int id)
        {
            if(id < 0)
            {
                return null;
            }
            final byte[][] pages     = this.pages;
            final int      pageIndex = id >>> PAGE_SHIFT;
            return pageIndex < pages.length
                ? (byte[])PAGES.getAcquire(pages, pageIndex)
                : null
            ;
        }

        private byte[] pageForWrite(final int pageIndex)
        {
            byte[][] pages = this.pages;
            if(pageIndex >= pages.length)
            {
                final int newLength = (int)Math.min(
                    MAX_PAGE_COUNT,
                    Math.max(pageIndex + 1L, pages.length * 2L)
                );
                // Publish the grown directory before any page or slot it carries is touched below.
                this.pages = pages = Arrays.copyOf(pages, newLength);
            }
            byte[] page = (byte[])PAGES.getAcquire(pages, pageIndex);
            if(page == null)
            {
                page = new byte[PAGE_SIZE];
                // Filled before it is published, so no reader can see a zero, which is slot 0.
                Arrays.fill(page, NONE_BYTE);
                PAGES.setRelease(pages, pageIndex, page);
            }
            return page;
        }

    }

}
