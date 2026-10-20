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


import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The owners of an on-disk index's entities together with the slots of its sources: which slots are
 * taken, which source of the index each entity is live in, and which entities have left each source
 * since its deletions were last committed.
 * <p>
 * Every source of an on-disk index - each segment, each in-memory graph frozen for a flush that has
 * not completed, and the live in-memory graph - holds a {@link Lease} on one slot. A frozen graph and
 * the segment its flush writes share the lease, so publishing the segment changes no owner. A slot
 * returns to the pool only once every holder of its lease is closed, which a search still traversing a
 * retired source delays: until then, nothing can make the slot the owner of an entity the retired
 * source also holds.
 * <p>
 * The leave log of a slot records each entity whose owner moved away from it. The deletion set a
 * manifest records for a segment is the one last committed plus the log entries since, and it must
 * describe exactly the instant the manifest's witnesses were sampled, so a flush captures the logs at
 * that instant (see {@link #capture(int)}). An entity never returns to a slot it has left, except to
 * the live graph's, so each log holds an entity at most once and is bounded by its source's size. The
 * live slot logs nothing: its deletions at a freeze are the deleted nodes of the graph, which the
 * flush purges before writing.
 * <p>
 * Thread-safety: owners and logs are written by a single writer, under the parent GigaMap's monitor
 * (or before the table is published), except for the owner moves of a merge, see
 * {@link #move(int, int, int)}; owners are read lock-free. Slots are taken under that monitor
 * too, but released by whichever thread closes the last holder of a lease, so the slot pool has a lock
 * of its own, a leaf lock.
 */
interface OwnerTable
{
    /**
     * Returns the owners, for the accept bits of a search.
     *
     * @return the owners
     */
    public SegmentOwners owners();

    /**
     * Returns the slot owning {@code id}, or {@link SegmentOwners#NONE}.
     *
     * @param id the entity id (= graph ordinal)
     * @return the owning slot or {@link SegmentOwners#NONE}
     */
    public default int owner(final int id)
    {
        return this.owners().owner(id);
    }

    /**
     * Sets the owner of {@code id}, recording in the leave log of the previous owner that the entity
     * has left it, unless that is the live slot or the owner does not change. Under the parent
     * monitor.
     *
     * @param id   the entity id, not negative
     * @param slot the new owner's slot, or {@link SegmentOwners#NONE}
     */
    public void set(
        int id  ,
        int slot
    );

    /**
     * Moves the owner of {@code id} from {@code from} to {@code to} if {@code from} still owns it,
     * without the parent monitor and without logging a leave of {@code from} (see
     * {@link SegmentOwners#move(int, int, int)}). Only for a merge publishing its segment: it removes
     * the source of {@code from} from the published set before any flush can capture that slot's log.
     *
     * @param id   the entity id
     * @param from the merged segment's slot
     * @param to   the slot of the segment it was merged into
     * @return whether the owner moved
     */
    public default boolean move(
        final int id  ,
        final int from,
        final int to
    )
    {
        return this.owners().move(id, from, to);
    }

    /**
     * Returns the slot of the live in-memory graph, whose leaves are not logged.
     *
     * @return the live slot, or {@link SegmentOwners#NONE} if none is set
     */
    public int liveSlot();

    /**
     * Makes {@code slot} the live slot, at a freeze. Under the parent monitor.
     *
     * @param slot the new live slot
     */
    public void liveSlot(int slot);

    /**
     * Takes a free slot, clearing its leave log.
     *
     * @return a lease on the slot, held once, or {@code null} if every slot is taken
     */
    public Lease allocate();

    /**
     * Takes the given slot, for the segments of a load, which are numbered from {@code 0}.
     *
     * @param slot the slot to take
     * @return a lease on the slot, held once
     * @throws IllegalStateException if the slot is taken
     */
    public Lease allocate(int slot);

    /**
     * Returns the number of slots taken, for diagnostics and tests.
     *
     * @return the slots in use
     */
    public int slotsInUse();

    /**
     * Captures the leave log of {@code slot} as it is now. Under the parent monitor, together with the
     * witnesses the captured entries are committed with. Later appends never change what was captured.
     *
     * @param slot the slot
     * @return the entries logged so far
     */
    public LeaveLog capture(int slot);


    /**
     * Creates an empty table: no slot taken, nobody owning anything, no live slot.
     *
     * @return a new table
     */
    public static OwnerTable New()
    {
        return new Default(SegmentOwners.New());
    }


    /**
     * The entities logged as having left a slot, up to some instant: the first {@link #length()}
     * entries of {@link #ids()}.
     *
     * @param ids    the backing array; only the first {@code length} entries belong to the capture
     * @param length the number of entries captured
     */
    public record LeaveLog(
        int[] ids   ,
        int   length
    )
    {
        /**
         * An empty capture.
         */
        public static final LeaveLog EMPTY = new LeaveLog(new int[0], 0);

        /**
         * Returns the entries logged after {@code from}, as a sorted array without duplicates.
         *
         * @param from the number of entries already accounted for
         * @return the newer entries, sorted ascending
         */
        public int[] sortedFrom(final int from)
        {
            if(from >= this.length)
            {
                return new int[0];
            }
            final int[] entries = Arrays.copyOfRange(this.ids, from, this.length);
            Arrays.sort(entries);
            return Arrays.stream(entries).distinct().toArray();
        }
    }


    /**
     * A shared hold on one slot. The slot returns to its table's pool when the last holder releases
     * it.
     */
    public static final class Lease
    {
        private final Default       table;
        private final int           slot ;
        private final AtomicInteger holds;

        Lease(
            final Default table,
            final int     slot
        )
        {
            this.table = table;
            this.slot  = slot ;
            this.holds = new AtomicInteger(1);
        }

        /**
         * @return the slot this lease holds
         */
        public int slot()
        {
            return this.slot;
        }

        /**
         * Adds a holder.
         *
         * @return this lease
         * @throws IllegalStateException if the slot has already been returned
         */
        public Lease retain()
        {
            this.holds.updateAndGet(current ->
            {
                if(current <= 0)
                {
                    throw new IllegalStateException("Slot " + this.slot + " has already been returned");
                }
                return current + 1;
            });
            return this;
        }

        /**
         * Removes a holder, returning the slot to the pool when it was the last one.
         */
        public void release()
        {
            final int remaining = this.holds.decrementAndGet();
            if(remaining == 0)
            {
                this.table.free(this.slot);
            }
            else if(remaining < 0)
            {
                throw new IllegalStateException("Slot " + this.slot + " released more often than held");
            }
        }
    }


    /**
     * Default implementation.
     */
    public static final class Default implements OwnerTable
    {
        ///////////////////////////////////////////////////////////////////////////
        // constants //
        //////////////

        private static final int SLOT_COUNT = SegmentOwners.MAX_SLOT + 1;



        ///////////////////////////////////////////////////////////////////////////
        // instance fields //
        ////////////////////

        private final SegmentOwners owners;

        // Guarded by this: the pool is the only state written by threads that do not hold the parent
        // monitor, namely whichever thread closes the last holder of a lease.
        private final boolean[] taken = new boolean[SLOT_COUNT];
        private       int       inUse;

        // Appended to under the parent monitor only. Reset under this table's lock when a slot is taken
        // or freed, when no source holds the slot, so no append or capture touches it then. A log's
        // array is replaced, never shrunk in place, when it grows, so a capture taken earlier keeps its
        // entries.
        private final int[][] logIds     = new int[SLOT_COUNT][];
        private final int[]   logLengths = new int[SLOT_COUNT];

        private volatile int liveSlot = SegmentOwners.NONE;



        ///////////////////////////////////////////////////////////////////////////
        // constructors //
        /////////////////

        Default(final SegmentOwners owners)
        {
            super();
            this.owners = owners;
        }



        ///////////////////////////////////////////////////////////////////////////
        // methods //
        ////////////

        @Override
        public SegmentOwners owners()
        {
            return this.owners;
        }

        @Override
        public void set(
            final int id  ,
            final int slot
        )
        {
            final int previous = this.owners.set(id, slot);
            if(previous != slot && previous != SegmentOwners.NONE && previous != this.liveSlot)
            {
                this.appendLeave(previous, id);
            }
        }

        private void appendLeave(
            final int slot,
            final int id
        )
        {
            int[]     ids    = this.logIds[slot];
            final int length = this.logLengths[slot];
            if(ids == null)
            {
                ids = new int[16];
                this.logIds[slot] = ids;
            }
            else if(length == ids.length)
            {
                // A fresh array, so an earlier capture keeps reading the one it captured.
                ids = Arrays.copyOf(ids, length * 2);
                this.logIds[slot] = ids;
            }
            ids[length] = id;
            this.logLengths[slot] = length + 1;
        }

        @Override
        public int liveSlot()
        {
            return this.liveSlot;
        }

        @Override
        public void liveSlot(final int slot)
        {
            this.liveSlot = slot;
        }

        @Override
        public synchronized Lease allocate()
        {
            for(int slot = 0; slot < SLOT_COUNT; slot++)
            {
                if(!this.taken[slot])
                {
                    return this.take(slot);
                }
            }
            return null;
        }

        @Override
        public synchronized Lease allocate(final int slot)
        {
            if(slot < 0 || slot >= SLOT_COUNT)
            {
                throw new IllegalArgumentException("Slot out of range 0.." + (SLOT_COUNT - 1) + ": " + slot);
            }
            if(this.taken[slot])
            {
                throw new IllegalStateException("Slot " + slot + " is taken");
            }
            return this.take(slot);
        }

        private Lease take(final int slot)
        {
            this.taken[slot] = true;
            this.inUse++;
            // A reused slot starts with an empty log: what the previous holder logged is about it.
            this.logIds[slot]     = null;
            this.logLengths[slot] = 0;
            return new Lease(this, slot);
        }

        synchronized void free(final int slot)
        {
            if(this.taken[slot])
            {
                this.taken[slot] = false;
                this.inUse--;
                // Nothing reads or appends to the log of a returned slot: no source holds it, so nothing is
                // captured for it, and no entity is owned by it to leave it. A merged segment's log holds
                // every entity moved away from it, which is released here rather than at the slot's reuse.
                this.logIds[slot]     = null;
                this.logLengths[slot] = 0;
            }
        }

        @Override
        public synchronized int slotsInUse()
        {
            return this.inUse;
        }

        @Override
        public LeaveLog capture(final int slot)
        {
            final int[] ids = this.logIds[slot];
            return ids == null
                ? LeaveLog.EMPTY
                : new LeaveLog(ids, this.logLengths[slot])
            ;
        }

    }

}
