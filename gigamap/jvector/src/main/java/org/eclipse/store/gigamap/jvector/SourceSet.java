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


import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The searchable sources of an on-disk index besides its live in-memory graph, as one immutable
 * snapshot: the segments on disk, the in-memory graphs frozen for flushes that have not published
 * their segments yet, and the owner table that decides which source each entity is live in.
 * <p>
 * Published copy-on-write. A freeze, a flush, a merge and a consolidation each build a new set and
 * publish it in place of the old one, and nothing in a published set ever changes. A search
 * {@linkplain #tryAcquire() acquires} the set it finds published and releases it when done, so the set,
 * and through it every source it lists, stays open for the whole search even if a newer set has
 * replaced it meanwhile. The set holds each of its sources (see {@link IndexSource}) from its
 * construction until its last holder releases it.
 * <p>
 * Thread-safe.
 */
final class SourceSet
{
    ///////////////////////////////////////////////////////////////////////////
    // instance fields //
    ////////////////////

    private final List<DiskSegment>    segments;
    private final List<HeapGeneration> frozen  ;
    private final OwnerTable           owners  ;

    // The publisher's hold plus one per search; zero once released for good.
    private final AtomicInteger holds = new AtomicInteger(1);



    ///////////////////////////////////////////////////////////////////////////
    // constructors //
    /////////////////

    /**
     * Creates a set holding the given sources, with one hold, the publisher's.
     *
     * @param segments the segments, ascending by sequence
     * @param frozen   the frozen in-memory graphs, oldest first
     * @param owners   the owner table every source's slot belongs to
     * @throws IllegalStateException if a source is already closed; the sources retained before it are
     *                               released again
     */
    SourceSet(
        final List<DiskSegment>    segments,
        final List<HeapGeneration> frozen  ,
        final OwnerTable           owners
    )
    {
        this.segments = List.copyOf(segments);
        this.frozen   = List.copyOf(frozen)  ;
        this.owners   = Objects.requireNonNull(owners, "owners");

        final List<IndexSource> retained = new ArrayList<>(this.segments.size() + this.frozen.size());
        try
        {
            for(final IndexSource source : this.sources())
            {
                source.retain();
                retained.add(source);
            }
        }
        catch(final RuntimeException e)
        {
            retained.forEach(IndexSource::release);
            throw e;
        }
    }



    ///////////////////////////////////////////////////////////////////////////
    // methods //
    ////////////

    /**
     * @return the segments, ascending by sequence
     */
    List<DiskSegment> segments()
    {
        return this.segments;
    }

    /**
     * @return the frozen in-memory graphs awaiting their flush, oldest first
     */
    List<HeapGeneration> frozen()
    {
        return this.frozen;
    }

    /**
     * @return the owner table of this set's sources
     */
    OwnerTable owners()
    {
        return this.owners;
    }

    /**
     * @return every source, segments first, then the frozen graphs
     */
    List<IndexSource> sources()
    {
        final List<IndexSource> sources = new ArrayList<>(this.segments.size() + this.frozen.size());
        sources.addAll(this.segments);
        sources.addAll(this.frozen);
        return sources;
    }

    /**
     * Creates a set with the same owner table and the given sources.
     *
     * @param segments the segments, ascending by sequence
     * @param frozen   the frozen in-memory graphs, oldest first
     * @return the new set, with the publisher's hold
     */
    SourceSet with(
        final List<DiskSegment>    segments,
        final List<HeapGeneration> frozen
    )
    {
        return new SourceSet(segments, frozen, this.owners);
    }

    /**
     * Adds a hold for a search, unless the set has been released for good, in which case the caller
     * reads the published set again: whatever released this one has published its successor first.
     *
     * @return whether the hold was added
     */
    boolean tryAcquire()
    {
        int current;
        do
        {
            current = this.holds.get();
            if(current <= 0)
            {
                return false;
            }
        }
        while(!this.holds.compareAndSet(current, current + 1));
        return true;
    }

    /**
     * Removes a hold, releasing every source of the set once the last one is gone.
     */
    void release()
    {
        final int remaining = this.holds.decrementAndGet();
        if(remaining == 0)
        {
            // Every source, even if one fails: a source left held would stay open for good.
            RuntimeException failure = null;
            for(final IndexSource source : this.sources())
            {
                try
                {
                    source.release();
                }
                catch(final RuntimeException e)
                {
                    if(failure == null)
                    {
                        failure = e;
                    }
                    else
                    {
                        failure.addSuppressed(e);
                    }
                }
            }
            if(failure != null)
            {
                throw failure;
            }
        }
        else if(remaining < 0)
        {
            throw new IllegalStateException("Source set released more often than held");
        }
    }

}
