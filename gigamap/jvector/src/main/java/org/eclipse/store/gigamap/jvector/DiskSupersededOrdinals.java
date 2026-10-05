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

import io.github.jbellis.jvector.util.AtomicFixedBitSet;
import io.github.jbellis.jvector.util.Bits;

/**
 * The ordinals of a loaded on-disk graph whose disk-resident version is no longer authoritative,
 * because the entity was removed or updated since the graph was written. Incremental-mode disk
 * search passes {@link #acceptBits()} to exclude them.
 * <p>
 * The on-disk graph is written with an identity ordinal map, so its ordinals are the source entity
 * ids and all of them lie below the graph's {@code getIdUpperBound()}. A fixed-size bit set of that
 * capacity therefore covers every ordinal that can be on disk, and ordinals outside it are ignored:
 * they were never written, so there is no stale disk version to exclude.
 * <p>
 * Concurrency: lock-free. {@link #add(int)} may be called from any number of mutating threads and
 * the background worker while searches read {@link #acceptBits()}. A search that observes
 * {@link #isEmpty()} as {@code false} also observes the bit that made it so. A search that started
 * before an {@code add} may or may not see it, which is the same guarantee a concurrent set gives.
 * <p>
 * There is no clear operation: a persist replaces the whole instance together with the disk graph
 * it describes.
 */
interface DiskSupersededOrdinals
{
    /**
     * Records that the disk-resident version of {@code ordinal} is superseded. Ordinals outside
     * {@code [0, idUpperBound)} are not on disk and are ignored.
     *
     * @param ordinal the graph ordinal (= source entity id)
     */
    public void add(int ordinal);

    /**
     * Returns whether no ordinal has been recorded. Does not scan.
     *
     * @return {@code true} if nothing has been recorded
     */
    public boolean isEmpty();

    /**
     * Returns the accept bits for disk search: {@link Bits#ALL} while nothing is recorded, otherwise
     * a view that rejects every recorded ordinal. The view is created once and reflects later
     * {@link #add(int)} calls, so this method allocates nothing.
     *
     * @return the accept bits for disk search
     */
    public Bits acceptBits();


    /**
     * Creates an empty instance for a disk graph with the given id upper bound.
     *
     * @param idUpperBound the disk graph's {@code getIdUpperBound()}, must not be negative
     * @return a new, empty instance
     * @throws IllegalArgumentException if {@code idUpperBound} is negative
     */
    public static DiskSupersededOrdinals New(final int idUpperBound)
    {
        if(idUpperBound < 0)
        {
            throw new IllegalArgumentException("Negative id upper bound: " + idUpperBound);
        }
        return new Default(idUpperBound);
    }


    /**
     * Default implementation backed by a jvector {@link AtomicFixedBitSet}.
     */
    public static final class Default implements DiskSupersededOrdinals
    {
        ///////////////////////////////////////////////////////////////////////////
        // instance fields //
        ////////////////////

        private final    int               idUpperBound;
        private final    AtomicFixedBitSet superseded  ;
        private final    Bits              acceptBits  ;
        private volatile boolean           nonEmpty    ;



        ///////////////////////////////////////////////////////////////////////////
        // constructors //
        /////////////////

        Default(final int idUpperBound)
        {
            super();
            this.idUpperBound = idUpperBound;
            this.superseded   = new AtomicFixedBitSet(idUpperBound);
            // jvector never passes a negative ordinal, but AtomicFixedBitSet.get would throw on one.
            this.acceptBits   = ordinal -> ordinal < 0 || !this.superseded.get(ordinal);
        }



        ///////////////////////////////////////////////////////////////////////////
        // methods //
        ////////////

        @Override
        public void add(final int ordinal)
        {
            if(ordinal < 0 || ordinal >= this.idUpperBound)
            {
                return;
            }
            // Bit first, flag second: whoever reads the flag as set also sees the bit.
            this.superseded.set(ordinal);
            if(!this.nonEmpty)
            {
                this.nonEmpty = true;
            }
        }

        @Override
        public boolean isEmpty()
        {
            return !this.nonEmpty;
        }

        @Override
        public Bits acceptBits()
        {
            return this.nonEmpty
                ? this.acceptBits
                : Bits.ALL
            ;
        }

    }

}
