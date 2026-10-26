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


import io.github.jbellis.jvector.graph.GraphIndexBuilder;
import io.github.jbellis.jvector.graph.GraphSearcher;
import io.github.jbellis.jvector.graph.OnHeapGraphIndex;
import io.github.jbellis.jvector.util.ExplicitThreadLocal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/**
 * An in-memory graph of an on-disk index, frozen by a persist and searchable until the flush that
 * writes it has published its segment.
 * <p>
 * A persist freezes the live in-memory graph and puts a fresh one in its place under the index's
 * locks, which takes only as long as swapping a few references. Everything expensive happens to the
 * frozen graph afterwards, with no lock held: the eventual-indexing operations still queued when it
 * was frozen are applied to it (they are counted in the witnesses the flush commits, see
 * {@link #movedOps()}), then {@code GraphIndexBuilder.cleanup()} and the write. Searches keep
 * traversing it throughout. Nothing else inserts into it or marks a node of it deleted: an entity that
 * changes after the freeze moves to the live graph, and the owner table hides the frozen copy.
 * <p>
 * The segment the flush writes keeps this generation's slot lease, so its nodes stay live under the
 * same owners. A flush that fails leaves the generation searchable, and the next one writes it again.
 * <p>
 * Thread-safety: the graph supports concurrent search alongside the flush's work on it, and only the
 * one flush in flight at a time touches the builder.
 */
interface HeapGeneration extends IndexSource
{
    /**
     * Returns the slot lease, which the segment the flush writes from this graph takes over, so its
     * nodes stay live under the same owners.
     *
     * @return the slot lease
     */
    public OwnerTable.Lease lease();

    /**
     * @return the frozen graph's builder
     */
    public GraphIndexBuilder builder();

    /**
     * @return the frozen graph
     */
    public OnHeapGraphIndex graph();

    /**
     * Returns the vectors the graph was built from, frozen with it: the vector file, with the freeze-time
     * vectors of the moved operations and of every node a mutation has changed since the freeze in front of it
     * (see {@link GenerationVectorValues}). The flush cleans the graph up and writes it with these, so a
     * mutation after the freeze cannot change what the segment holds of an entity the witnesses sampled at the
     * freeze count.
     *
     * @return the generation's vectors, frozen
     */
    public GenerationVectorValues vectorValues();

    /**
     * Returns the eventual-indexing operations that were queued when the graph was frozen. They are
     * counted in the witnesses sampled at the freeze, so the flush applies them to this graph before
     * writing it; a flush that is retried finds them applied already.
     *
     * @return the operations, oldest first
     */
    public List<BackgroundTaskManager.IndexingOperation> movedOps();

    /**
     * Returns whether {@link #movedOps()} have been applied, so a retried flush does not apply them
     * twice.
     *
     * @return whether the moved operations are applied
     */
    public boolean movedOpsApplied();

    /**
     * Records that {@link #movedOps()} have been applied.
     */
    public void markMovedOpsApplied();

    /**
     * Returns the calling thread's searcher over the frozen graph.
     *
     * @return the searcher
     */
    public GraphSearcher searcher();

    /**
     * @return the number of nodes in the graph's base layer
     */
    public default int nodeCount()
    {
        return this.graph().size(0);
    }

    @Override
    public HeapGeneration retain();

    /**
     * @return whether the generation is closed, its last hold released
     */
    public boolean isClosed();


    /**
     * Freezes a live in-memory graph. Takes over the builder, the searchers and the slot lease; the
     * caller installs fresh ones for the live graph.
     *
     * @param lease        the slot lease the graph was live under
     * @param builder      the builder
     * @param vectorValues the vectors the builder scores from, already frozen, see {@link #vectorValues()}
     * @param searchers    the searchers over the graph, or {@code null} to create them on demand
     * @param movedOps     the eventual-indexing operations queued at the freeze, oldest first
     * @param name         the index name, for logging
     * @return the frozen generation, not yet retained
     */
    public static HeapGeneration Freeze(
        final OwnerTable.Lease                              lease       ,
        final GraphIndexBuilder                             builder     ,
        final GenerationVectorValues                        vectorValues,
        final ExplicitThreadLocal<GraphSearcher>            searchers   ,
        final List<BackgroundTaskManager.IndexingOperation> movedOps    ,
        final String                                        name
    )
    {
        return new Default(lease, builder, vectorValues, searchers, movedOps, name);
    }


    /**
     * Default implementation.
     */
    public static final class Default implements HeapGeneration
    {
        ///////////////////////////////////////////////////////////////////////////
        // constants //
        //////////////

        private static final Logger LOG = LoggerFactory.getLogger(HeapGeneration.class);



        ///////////////////////////////////////////////////////////////////////////
        // instance fields //
        ////////////////////

        private final OwnerTable.Lease                              lease          ;
        private final GraphIndexBuilder                             builder        ;
        private final OnHeapGraphIndex                              graph          ;
        private final GenerationVectorValues                        vectorValues   ;
        private final ExplicitThreadLocal<GraphSearcher>            searchers      ;
        private final List<BackgroundTaskManager.IndexingOperation> movedOps       ;
        private final String                                        name           ;
        private final IndexSource.Holds                             holds          ;
        private volatile boolean                                    movedOpsApplied;



        ///////////////////////////////////////////////////////////////////////////
        // constructors //
        /////////////////

        Default(
            final OwnerTable.Lease                              lease       ,
            final GraphIndexBuilder                             builder     ,
            final GenerationVectorValues                        vectorValues,
            final ExplicitThreadLocal<GraphSearcher>            searchers   ,
            final List<BackgroundTaskManager.IndexingOperation> movedOps    ,
            final String                                        name
        )
        {
            this.lease           = Objects.requireNonNull(lease, "lease")    ;
            this.builder         = Objects.requireNonNull(builder, "builder");
            this.graph           = (OnHeapGraphIndex)builder.getGraph()      ;
            this.vectorValues    = Objects.requireNonNull(vectorValues, "vectorValues");
            this.searchers       = searchers != null
                ? searchers
                : ExplicitThreadLocal.withInitial(() -> new GraphSearcher(this.graph))
            ;
            this.movedOps        = List.copyOf(movedOps)                      ;
            this.name            = name                                       ;
            this.holds           = new IndexSource.Holds()                    ;
            this.movedOpsApplied = this.movedOps.isEmpty()                    ;
        }



        ///////////////////////////////////////////////////////////////////////////
        // methods //
        ////////////

        @Override
        public int slot()
        {
            return this.lease.slot();
        }

        @Override
        public OwnerTable.Lease lease()
        {
            return this.lease;
        }

        @Override
        public GraphIndexBuilder builder()
        {
            return this.builder;
        }

        @Override
        public OnHeapGraphIndex graph()
        {
            return this.graph;
        }

        @Override
        public GenerationVectorValues vectorValues()
        {
            return this.vectorValues;
        }

        @Override
        public List<BackgroundTaskManager.IndexingOperation> movedOps()
        {
            return this.movedOps;
        }

        @Override
        public boolean movedOpsApplied()
        {
            return this.movedOpsApplied;
        }

        @Override
        public void markMovedOpsApplied()
        {
            this.movedOpsApplied = true;
        }

        @Override
        public GraphSearcher searcher()
        {
            return this.searchers.get();
        }

        @Override
        public HeapGeneration retain()
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
        public boolean isClosed()
        {
            return this.holds.isClosed();
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
                LOG.warn("Error closing searchers of a frozen graph of '{}': {}", this.name, e.getMessage());
            }
            try
            {
                this.builder.close();
            }
            catch(final Exception e)
            {
                LOG.warn("Error closing a frozen graph builder of '{}': {}", this.name, e.getMessage());
            }
            try
            {
                this.graph.close();
            }
            catch(final Exception e)
            {
                LOG.warn("Error closing a frozen graph of '{}': {}", this.name, e.getMessage());
            }
            this.lease.release();
        }

        @Override
        public String toString()
        {
            return "Frozen graph of '" + this.name + "' in slot " + this.lease.slot();
        }

    }

}
