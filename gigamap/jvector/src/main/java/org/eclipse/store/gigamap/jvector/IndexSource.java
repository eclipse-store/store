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


import java.util.concurrent.atomic.AtomicInteger;

/**
 * One searchable part of an on-disk index other than its live in-memory graph: a {@link DiskSegment}
 * or a {@link HeapGeneration} frozen for a flush.
 * <p>
 * A source is shared by every {@link SourceSet} that lists it and by a flush writing it, each of which
 * {@linkplain #retain() retains} it, and it is closed when the last of them {@linkplain #release()
 * releases} it. That is what lets a flush publish a new set while a search is still traversing a
 * source the new set no longer lists: the search's set keeps the source open until the search ends.
 * A source that was never retained is closed by its creator directly.
 */
interface IndexSource
{
    /**
     * Returns the slot this source is known by in the owner table of its index. A node of the source
     * is live only while its entity's owner is this slot.
     *
     * @return the slot
     */
    public int slot();

    /**
     * Adds a holder.
     *
     * @return this source
     * @throws IllegalStateException if the source is already closed
     */
    public IndexSource retain();

    /**
     * Removes a holder, closing the source when it was the last one.
     */
    public void release();

    /**
     * Closes the source and returns its slot lease. Called by {@link #release()} for the last holder,
     * or directly by a creator for a source that was never retained. Never throws; failures are
     * logged.
     */
    public void close();


    /**
     * The holder count of a source, starting at zero, and the transition to closed when it drops back
     * to zero after having been held.
     */
    public static final class Holds
    {
        // Positive while held; CLOSED once closed, after which nothing can retain it again.
        private static final int CLOSED = -1;

        private final AtomicInteger count = new AtomicInteger();

        /**
         * Adds a holder.
         *
         * @param what names the source in the exception
         * @throws IllegalStateException if the source is already closed
         */
        public void retain(final Object what)
        {
            this.count.updateAndGet(current ->
            {
                if(current == CLOSED)
                {
                    throw new IllegalStateException(what + " is already closed");
                }
                return current + 1;
            });
        }

        /**
         * Removes a holder.
         *
         * @param what names the source in the exception
         * @return whether this was the last holder, in which case the caller closes the source
         * @throws IllegalStateException if the source is not held
         */
        public boolean release(final Object what)
        {
            final int remaining = this.count.updateAndGet(current ->
            {
                if(current <= 0)
                {
                    throw new IllegalStateException(what + " released more often than retained");
                }
                return current == 1 ? CLOSED : current - 1;
            });
            return remaining == CLOSED;
        }

        /**
         * Marks the source closed if nothing holds it, for a creator closing a source directly.
         *
         * @return whether the caller is the one to close it: {@code false} if it is held or closed
         */
        public boolean closeUnheld()
        {
            return this.count.compareAndSet(0, CLOSED);
        }

        /**
         * @return whether the source is closed
         */
        public boolean isClosed()
        {
            return this.count.get() == CLOSED;
        }
    }

}
