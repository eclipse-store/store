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


import io.github.jbellis.jvector.disk.ReaderSupplier;
import io.github.jbellis.jvector.graph.GraphSearcher;
import io.github.jbellis.jvector.graph.SearchResult;
import io.github.jbellis.jvector.graph.disk.OnDiskGraphIndex;
import io.github.jbellis.jvector.quantization.PQVectors;
import io.github.jbellis.jvector.util.Bits;
import io.github.jbellis.jvector.util.ExplicitThreadLocal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

/**
 * One loaded, immutable segment of an on-disk {@link VectorIndex}: a jVector graph with dense local
 * ordinals, the array translating them to global ordinals (= source entity ids), and optionally the
 * segment's PQ codes.
 * <p>
 * The translation happens in exactly three places, all of them here or fed from here:
 * <ul>
 *   <li>{@link #acceptBits(SegmentOwners)} - a local ordinal is live only if the segment owns its
 *       entity;</li>
 *   <li>{@link #toGlobal(SearchResult)} - search results leave the segment with global ordinals,
 *       so merging and converting them never sees a local one;</li>
 *   <li>{@link #localToGlobal()} feeds {@link SegmentVectorValues}, through which the exact reranker
 *       reads the live vectors.</li>
 * </ul>
 * <p>
 * Besides what is on disk, a segment carries the deletions last committed for it: the deleted local
 * ordinals the current manifest records, and how much of its slot's leave log those already cover
 * (see {@link OwnerTable}). The next flush adds the rest of the log to them. Both change only when a
 * flush commits or a merge publishes, under the index's flush permit, which also covers every reader but
 * the merge policy, which only estimates sizes from them.
 * <p>
 * Thread-safe for searching: the graph and the arrays are read-only, and each thread searches with
 * its own {@link GraphSearcher} from {@link #searcher()}. A segment is closed when the last holder
 * releases it (see {@link IndexSource}), so a search holding a set that lists it never sees it closed.
 */
interface DiskSegment extends IndexSource, Closeable
{
    /**
     * @return the number in this segment's file names
     */
    public long sequence();

    /**
     * @return the format this segment was written for, as the manifest records it
     */
    public GraphFormat format();

    /**
     * Returns whether this segment's recorded format or codebook does not match the index any more,
     * so it can only be searched exactly and should be rewritten. Such a segment contributes no
     * quantizer to the index.
     *
     * @return whether the segment is flagged for a merge
     */
    public boolean flaggedForMerge();

    /**
     * @return the node count, i.e. the local ordinals are {@code 0..nodeCount()-1}
     */
    public int nodeCount();

    /**
     * Returns the local-to-global array. Not a copy: the caller must not modify it.
     *
     * @return the global ordinal of each local ordinal, strictly ascending
     */
    public int[] localToGlobal();

    /**
     * @return the segment's graph
     */
    public OnDiskGraphIndex graph();

    /**
     * Returns the segment's PQ codes by local ordinal, or {@code null} if it has none.
     *
     * @return the codes, or {@code null}
     */
    public PQVectors pqVectors();

    /**
     * Returns the deleted local ordinals the current manifest records for this segment. Not a copy:
     * the caller must not modify it.
     *
     * @return the committed deletions, strictly ascending
     */
    public int[] committedDeletedLocals();

    /**
     * Returns how many entries of this segment's leave log the committed deletions cover.
     *
     * @return the committed log length
     */
    public int committedLogLength();

    /**
     * Records the deletions a flush has just committed for this segment. Under the flush permit.
     *
     * @param deletedLocals the deleted local ordinals now in the manifest, strictly ascending
     * @param logLength     how many leave log entries they cover
     */
    public void committed(
        int[] deletedLocals,
        int   logLength
    );

    /**
     * Returns the calling thread's searcher over this segment's graph.
     *
     * @return the searcher
     */
    public GraphSearcher searcher();

    /**
     * Returns the accept bits for searching this segment: a local ordinal is accepted while
     * {@code owners} names this segment as the owner of its entity. Reflects later changes of the
     * owners, so this allocates nothing per search beyond the view itself.
     *
     * @param owners the owners of the index
     * @return the accept bits, by local ordinal
     */
    public Bits acceptBits(SegmentOwners owners);

    /**
     * Translates the local ordinals of a search result over this segment into global ones.
     *
     * @param result a result of searching this segment
     * @return the same result with global ordinals
     */
    public SearchResult toGlobal(SearchResult result);

    /**
     * Returns the global ordinals of the given local ordinals.
     *
     * @param locals local ordinals, strictly ascending
     * @return their global ordinals, strictly ascending
     */
    public default int[] toGlobal(final int[] locals)
    {
        final int[] localToGlobal = this.localToGlobal();
        final int[] global        = new int[locals.length];
        for(int i = 0; i < locals.length; i++)
        {
            global[i] = localToGlobal[locals[i]];
        }
        return global;
    }

    /**
     * Returns the local ordinals of those of the given global ordinals this segment holds.
     *
     * @param globals global ordinals, strictly ascending
     * @return the local ordinals of the ones present, strictly ascending
     */
    public default int[] toLocal(final int[] globals)
    {
        final int[] localToGlobal = this.localToGlobal();
        final int[] local         = new int[globals.length];
        int         count         = 0;
        int         from          = 0;
        for(final int global : globals)
        {
            final int position = Arrays.binarySearch(localToGlobal, from, localToGlobal.length, global);
            if(position >= 0)
            {
                local[count++] = position;
                from = position + 1;
            }
            else
            {
                from = -position - 1;
            }
        }
        return Arrays.copyOf(local, count);
    }

    /**
     * Closes the searchers, the graph and its file, and returns the slot lease, unless the segment is
     * held (see {@link IndexSource#close()}). I/O failures closing the searchers, the graph and the reader
     * are logged, not thrown; returning the lease throws an {@link IllegalStateException} if it was
     * released more often than held.
     */
    @Override
    public void close();


    /**
     * Creates a segment over files that are already open.
     *
     * @param sequence        the number in the segment's file names
     * @param lease           the slot lease; the segment becomes one of its holders
     * @param format          the recorded format
     * @param flaggedForMerge whether the segment no longer matches the index
     * @param localToGlobal   the local-to-global array, strictly ascending; not copied
     * @param graph           the loaded graph, whose size must equal the array's length
     * @param readerSupplier  the supplier the graph reads through, closed with the segment
     * @param pqVectors       the segment's PQ codes by local ordinal, or {@code null}
     * @param deletedLocals   the deletions the manifest records for it, strictly ascending; not copied
     * @param logLength       how many entries of the slot's leave log those cover
     * @param onClosed        run once the segment is closed, or {@code null}
     * @return the segment
     */
    public static DiskSegment New(
        final long              sequence       ,
        final OwnerTable.Lease  lease          ,
        final GraphFormat       format         ,
        final boolean           flaggedForMerge,
        final int[]             localToGlobal  ,
        final OnDiskGraphIndex  graph          ,
        final ReaderSupplier    readerSupplier ,
        final PQVectors         pqVectors      ,
        final int[]             deletedLocals  ,
        final int               logLength      ,
        final Runnable          onClosed
    )
    {
        return new Default(
            sequence, lease, format, flaggedForMerge, localToGlobal, graph, readerSupplier, pqVectors,
            deletedLocals, logLength, onClosed
        );
    }


    /**
     * Default implementation.
     */
    public static final class Default implements DiskSegment
    {
        ///////////////////////////////////////////////////////////////////////////
        // constants //
        //////////////

        private static final Logger LOG = LoggerFactory.getLogger(DiskSegment.class);



        ///////////////////////////////////////////////////////////////////////////
        // instance fields //
        ////////////////////

        private final long                               sequence       ;
        private final OwnerTable.Lease                   lease          ;
        private final GraphFormat                        format         ;
        private final boolean                            flaggedForMerge;
        private final int[]                              localToGlobal  ;
        private final OnDiskGraphIndex                   graph          ;
        private final ReaderSupplier                     readerSupplier ;
        private final PQVectors                          pqVectors      ;
        private final ExplicitThreadLocal<GraphSearcher> searchers      ;
        private final Runnable                           onClosed       ;
        private final IndexSource.Holds                  holds          ;

        // Written under the flush permit, see the interface. Volatile: the merge policy reads them without
        // it, as an estimate of the live nodes.
        private volatile int[] committedDeletedLocals;
        private volatile int   committedLogLength    ;



        ///////////////////////////////////////////////////////////////////////////
        // constructors //
        /////////////////

        Default(
            final long              sequence       ,
            final OwnerTable.Lease  lease          ,
            final GraphFormat       format         ,
            final boolean           flaggedForMerge,
            final int[]             localToGlobal  ,
            final OnDiskGraphIndex  graph          ,
            final ReaderSupplier    readerSupplier ,
            final PQVectors         pqVectors      ,
            final int[]             deletedLocals  ,
            final int               logLength      ,
            final Runnable          onClosed
        )
        {
            if(graph.size(0) != localToGlobal.length)
            {
                throw new IllegalArgumentException("Segment " + sequence + " graph holds " + graph.size(0)
                    + " nodes, its ordinal map " + localToGlobal.length);
            }
            this.sequence               = sequence                                  ;
            this.lease                  = Objects.requireNonNull(lease, "lease")    ;
            this.format                 = Objects.requireNonNull(format, "format")  ;
            this.flaggedForMerge        = flaggedForMerge                           ;
            this.localToGlobal          = localToGlobal                             ;
            this.graph                  = graph                                     ;
            this.readerSupplier         = readerSupplier                            ;
            this.pqVectors              = pqVectors                                 ;
            this.committedDeletedLocals = Objects.requireNonNull(deletedLocals, "deletedLocals");
            this.committedLogLength     = logLength                                 ;
            this.onClosed               = onClosed                                  ;
            this.holds                  = new IndexSource.Holds()                   ;
            this.searchers              = ExplicitThreadLocal.withInitial(() -> new GraphSearcher(graph));
        }



        ///////////////////////////////////////////////////////////////////////////
        // methods //
        ////////////

        @Override
        public long sequence()
        {
            return this.sequence;
        }

        @Override
        public int slot()
        {
            return this.lease.slot();
        }

        @Override
        public GraphFormat format()
        {
            return this.format;
        }

        @Override
        public boolean flaggedForMerge()
        {
            return this.flaggedForMerge;
        }

        @Override
        public int nodeCount()
        {
            return this.localToGlobal.length;
        }

        @Override
        public int[] localToGlobal()
        {
            return this.localToGlobal;
        }

        @Override
        public OnDiskGraphIndex graph()
        {
            return this.graph;
        }

        @Override
        public PQVectors pqVectors()
        {
            return this.pqVectors;
        }

        @Override
        public int[] committedDeletedLocals()
        {
            return this.committedDeletedLocals;
        }

        @Override
        public int committedLogLength()
        {
            return this.committedLogLength;
        }

        @Override
        public void committed(
            final int[] deletedLocals,
            final int   logLength
        )
        {
            this.committedDeletedLocals = Objects.requireNonNull(deletedLocals, "deletedLocals");
            this.committedLogLength     = logLength;
        }

        @Override
        public GraphSearcher searcher()
        {
            return this.searchers.get();
        }

        @Override
        public Bits acceptBits(final SegmentOwners owners)
        {
            final int[] localToGlobal = this.localToGlobal;
            final int   slot          = this.slot();
            return local -> local >= 0
                && local < localToGlobal.length
                && owners.owner(localToGlobal[local]) == slot
            ;
        }

        @Override
        public SearchResult toGlobal(final SearchResult result)
        {
            final SearchResult.NodeScore[] local  = result.getNodes();
            final SearchResult.NodeScore[] global = new SearchResult.NodeScore[local.length];
            for(int i = 0; i < local.length; i++)
            {
                global[i] = new SearchResult.NodeScore(this.localToGlobal[local[i].node], local[i].score);
            }
            return new SearchResult(
                global,
                result.getVisitedCount(),
                result.getExpandedCount(),
                result.getExpandedCountBaseLayer(),
                result.getRerankedCount(),
                result.getWorstApproximateScoreInTopK()
            );
        }

        @Override
        public DiskSegment retain()
        {
            this.holds.retain(this);
            return this;
        }

        @Override
        public void release()
        {
            if(this.holds.release(this))
            {
                this.closeResources();
            }
        }

        @Override
        public void close()
        {
            if(this.holds.closeUnheld())
            {
                this.closeResources();
            }
        }

        private void closeResources()
        {
            try
            {
                this.searchers.close();
            }
            catch(final Exception e)
            {
                LOG.warn("Error closing searchers of segment {}: {}", this.sequence, e.getMessage());
            }
            try
            {
                this.graph.close();
            }
            catch(final IOException e)
            {
                LOG.warn("Error closing graph of segment {}: {}", this.sequence, e.getMessage());
            }
            if(this.readerSupplier != null)
            {
                try
                {
                    this.readerSupplier.close();
                }
                catch(final IOException e)
                {
                    LOG.warn("Error closing reader of segment {}: {}", this.sequence, e.getMessage());
                }
            }
            this.lease.release();
            if(this.onClosed != null)
            {
                this.onClosed.run();
            }
        }

        @Override
        public String toString()
        {
            return "Segment " + this.sequence;
        }

    }

}
