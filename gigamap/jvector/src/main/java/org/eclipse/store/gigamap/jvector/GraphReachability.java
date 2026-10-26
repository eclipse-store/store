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
import io.github.jbellis.jvector.graph.ImmutableGraphIndex;
import io.github.jbellis.jvector.graph.NodeArray;
import io.github.jbellis.jvector.graph.NodesIterator;
import io.github.jbellis.jvector.graph.OnHeapGraphIndex;
import io.github.jbellis.jvector.util.BitSet;
import io.github.jbellis.jvector.util.DocIdSetIterator;
import io.github.jbellis.jvector.util.FixedBitSet;
import io.github.jbellis.jvector.vector.types.VectorFloat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;
import java.util.stream.IntStream;

/**
 * Reconnects the nodes a parallel insertion or a purge left unreachable in a graph.
 * <p>
 * Concurrent {@code addGraphNode} calls do not reliably see each other: a node whose nearest neighbours are
 * being inserted at the same moment links to farther nodes, and the backlinks into those go in without a
 * diversity check, so they are the first ones a later overflow of the neighbour's list prunes. The node keeps
 * its own edges while nothing points at it any more, and no search ever finds it. Near-duplicate vectors make
 * this frequent. jVector's {@code cleanup()} would not repair it either, as its refinement only covers the
 * upper layers. A purge of deleted nodes followed by an insert, which is how a vector is replaced, can strand
 * a node the same way: both prune neighbour lists for diversity, and the last edge into a node can be the one
 * pruned.
 * <p>
 * So every parallel insertion phase and every purge of the live graph ends with {@link #repair}: a
 * breadth-first walk over layer 0 from the entry node, and a sequential re-insert of every node the walk does
 * not reach. By then every node is complete, so the re-inserted node links to its real neighbours. The walk is
 * one pass over the edges, small against the insertions it follows, and stops as soon as it has reached every
 * node it was asked about.
 * <p>
 * Not thread-safe: the caller has the builder to itself while this runs, no insert, purge or cleanup at the
 * same time. Holds no state.
 */
final class GraphReachability
{
    ///////////////////////////////////////////////////////////////////////////
    // constants //
    //////////////

    private static final Logger LOG = LoggerFactory.getLogger(GraphReachability.class);

    /**
     * How many walk and re-insert rounds {@link #repair} runs at most. One is normally enough; a re-insert can
     * still lose its last backlink to the pruning of a neighbour list, which the next round picks up.
     */
    private static final int MAX_ROUNDS = 3;

    /**
     * How many further insert failures of one re-insert are attached to the first as suppressed exceptions.
     */
    private static final int MAX_SUPPRESSED = 10;


    ///////////////////////////////////////////////////////////////////////////
    // static methods //
    ///////////////////

    /**
     * Makes sure every node of {@code candidates} that is a live node of the graph is reachable on layer 0
     * from the entry node, by re-inserting the unreachable nodes one after another. Once a candidate is found
     * unreachable, every other unreachable live node is re-inserted as well, whether it is a candidate or not.
     * <p>
     * Precondition: no other thread inserts into, purges or cleans up {@code builder} while this runs, and no
     * insert of the batch is still in progress. Postcondition: every re-inserted node holds the vector
     * {@code vectors} returned for it, and a node marked deleted or without a vector was left as it was.
     *
     * @param builder    the builder of the graph to repair
     * @param candidates the ordinals to make sure of, typically the ones just inserted
     * @param count      how many entries of {@code candidates} are valid, from the start
     * @param vectors    the vector of an ordinal, or {@code null} if it has none any more; called on this
     *                   thread only
     * @param name       the name of the index, for the log
     * @return the number of nodes re-inserted
     * @throws NodesLostException if nodes taken out for their re-insert could not be inserted again
     */
    static int repair(
        final GraphIndexBuilder           builder   ,
        final int[]                       candidates,
        final int                         count     ,
        final IntFunction<VectorFloat<?>> vectors   ,
        final String                      name
    )
    {
        final OnHeapGraphIndex graph    = (OnHeapGraphIndex)builder.getGraph();
        int                    repaired = 0;
        for(int round = 0; round < MAX_ROUNDS; round++)
        {
            final FixedBitSet reached = walk(graph, candidates, count);
            if(reached == null)
            {
                if(repaired > 0)
                {
                    LOG.info("Reconnected {} unreachable nodes in the graph of '{}'", repaired, name);
                }
                return repaired;
            }
            final List<Orphan> orphans = orphans(graph, reached, vectors);
            if(orphans.isEmpty())
            {
                // Unreachable candidates all deleted or without a vector: nothing a re-insert could fix.
                return repaired;
            }
            reinsert(builder, graph, orphans, name);
            repaired += orphans.size();
        }
        if(walk(graph, candidates, count) != null)
        {
            LOG.warn("The graph of '{}' still holds unreachable nodes after {} rounds of repair", name, MAX_ROUNDS);
        }
        else
        {
            LOG.info("Reconnected {} unreachable nodes in the graph of '{}'", repaired, name);
        }
        return repaired;
    }

    /**
     * Purges the deleted nodes of {@code builder} with {@code removeDeletedNodes()} and then {@link #repair}s
     * the nodes the deleted ones pointed at on layer 0.
     * <p>
     * The purge gives every node that pointed at a deleted node the deleted node's neighbours as candidates,
     * then prunes the merged list for diversity. A neighbour of the deleted node that lost the edge it was
     * reached by can lose every replacement for it to that pruning as well, near-duplicates most of all, and
     * is then unreachable. Sequential replaces of vectors, each a delete, a purge and an insert, do that.
     * <p>
     * Same precondition as {@link #repair}, and {@code vectors} must return what the graph's own nodes were
     * scored with, so this is for a graph built from the vector file.
     *
     * @param builder the builder of the graph to purge
     * @param vectors the vector of an ordinal, or {@code null} if it has none any more; called on this
     *                thread only
     * @param name    the name of the index, for the log
     * @return the number of nodes re-inserted
     * @throws NodesLostException if nodes taken out for their re-insert could not be inserted again
     */
    static int purgeDeleted(
        final GraphIndexBuilder           builder,
        final IntFunction<VectorFloat<?>> vectors,
        final String                      name
    )
    {
        final OnHeapGraphIndex  graph    = (OnHeapGraphIndex)builder.getGraph();
        final BitSet            deleted  = graph.getDeletedNodes().copy();
        final IntStream.Builder affected = IntStream.builder();
        int node = deleted.nextSetBit(0);
        while(node != DocIdSetIterator.NO_MORE_DOCS)
        {
            for(final NodesIterator neighbours = graph.getNeighborsIterator(0, node); neighbours.hasNext();)
            {
                affected.add(neighbours.nextInt());
            }
            node = deleted.nextSetBit(node + 1);
        }
        builder.removeDeletedNodes();
        final int[] candidates = affected.build().distinct().toArray();
        return repair(builder, candidates, candidates.length, vectors, name);
    }

    /**
     * Walks layer 0 breadth-first from the entry node until it has reached every live candidate.
     *
     * @return {@code null} if every live candidate was reached, otherwise the nodes the complete walk reached
     */
    private static FixedBitSet walk(
        final OnHeapGraphIndex graph     ,
        final int[]            candidates,
        final int              count
    )
    {
        int missing = 0;
        for(int index = 0; index < count; index++)
        {
            if(graph.containsNode(candidates[index]))
            {
                missing++;
            }
        }
        final ImmutableGraphIndex.NodeAtLevel entry = graph.entryNode();
        if(missing == 0 || entry == null)
        {
            return null;
        }

        final int         bound   = graph.getIdUpperBound();
        final FixedBitSet pending = new FixedBitSet(bound);
        for(int index = 0; index < count; index++)
        {
            if(graph.containsNode(candidates[index]))
            {
                pending.set(candidates[index]);
            }
        }
        final FixedBitSet reached = new FixedBitSet(bound);
        final int[]       queue   = new int[graph.size(0)];
        int               head    = 0;
        int               tail    = 0;
        reached.set(entry.node);
        queue[tail++] = entry.node;
        while(head < tail)
        {
            final int node = queue[head++];
            if(pending.getAndClear(node) && --missing == 0)
            {
                return null;
            }
            for(final NodesIterator neighbours = graph.getNeighborsIterator(0, node); neighbours.hasNext();)
            {
                final int neighbour = neighbours.nextInt();
                if(neighbour < bound && !reached.getAndSet(neighbour) && graph.containsNode(neighbour))
                {
                    queue[tail++] = neighbour;
                }
            }
        }
        return reached;
    }

    /**
     * @return the live nodes of layer 0 not in {@code reached} that are not marked deleted and still have a
     *         vector, in ascending order, each with that vector. Read once here and kept: the vector source can
     *         be written by another thread meanwhile, so a second read could find none or a newer one.
     */
    private static List<Orphan> orphans(
        final OnHeapGraphIndex            graph  ,
        final FixedBitSet                 reached,
        final IntFunction<VectorFloat<?>> vectors
    )
    {
        final int[]        unreached = graph.nodeStream(0)
            .filter(node -> !reached.get(node) && !graph.getDeletedNodes().get(node))
            .toArray();
        final List<Orphan> orphans   = new ArrayList<>(unreached.length);
        for(final int node : unreached)
        {
            final VectorFloat<?> vector = vectors.apply(node);
            if(vector != null)
            {
                orphans.add(new Orphan(node, vector));
            }
        }
        return orphans;
    }

    /**
     * Removes the orphans from the graph and inserts them again, one after another.
     * <p>
     * {@code removeNode} drops a node's own lists but not the edges other nodes hold to it, and an insert that
     * follows such a stale edge fails. Nothing reached points at an orphan on layer 0, but an unreached node
     * that stays (a deleted one), or any node on an upper layer, may. So those edges are stripped first, in one
     * pass over every layer, before the orphans go.
     * <p>
     * Every orphan is out of the graph once the removals are done, so an insert that fails must not end the
     * loop: the orphans after it would stay out of the graph, unfindable and unreported. The remaining ones are
     * inserted, and the failure is thrown afterwards as a {@link NodesLostException} that counts the nodes the
     * graph no longer holds.
     *
     * @throws NodesLostException if an orphan could not be inserted again
     */
    private static void reinsert(
        final GraphIndexBuilder builder,
        final OnHeapGraphIndex  graph  ,
        final List<Orphan>      orphans,
        final String            name
    )
    {
        final FixedBitSet removed = new FixedBitSet(graph.getIdUpperBound());
        for(final Orphan orphan : orphans)
        {
            removed.set(orphan.node());
        }
        final NodeArray none = new NodeArray(1);
        for(int level = 0; level <= graph.getMaxLevel(); level++)
        {
            final int   layer     = level;
            final int[] referrers = graph.nodeStream(layer)
                .filter(node -> !removed.get(node) && pointsAtAny(graph, layer, node, removed))
                .toArray();
            for(final int node : referrers)
            {
                graph.replaceDeletedNeighbors(layer, node, removed, none);
            }
        }
        for(final Orphan orphan : orphans)
        {
            graph.removeNode(orphan.node());
        }
        RuntimeException failure = null;
        int              lost    = 0;
        for(final Orphan orphan : orphans)
        {
            try
            {
                builder.addGraphNode(orphan.node(), orphan.vector());
            }
            catch(final RuntimeException e)
            {
                if(failure == null)
                {
                    failure = e;
                }
                else if(failure.getSuppressed().length < MAX_SUPPRESSED)
                {
                    // Bounded: a systematic failure (a closed vector file) fails every orphan alike.
                    failure.addSuppressed(e);
                }
                lost++;
            }
        }
        if(failure != null)
        {
            throw new NodesLostException(name, lost, orphans.size(), failure);
        }
    }

    private static boolean pointsAtAny(
        final OnHeapGraphIndex graph,
        final int              level,
        final int              node ,
        final FixedBitSet      nodes
    )
    {
        for(final NodesIterator neighbours = graph.getNeighborsIterator(level, node); neighbours.hasNext();)
        {
            final int neighbour = neighbours.nextInt();
            if(neighbour < nodes.length() && nodes.get(neighbour))
            {
                return true;
            }
        }
        return false;
    }


    ///////////////////////////////////////////////////////////////////////////
    // nested types //
    /////////////////

    /**
     * An unreachable node and the vector it is re-inserted with.
     */
    private record Orphan(int node, VectorFloat<?> vector) {}

    /**
     * Thrown by {@link #repair} and {@link #purgeDeleted} when unreachable nodes were taken out of the graph for
     * their re-insert and some of them could not be inserted again. Those nodes are missing from the graph or left
     * incomplete (searches skip them, later inserts take them as present), so no search finds their entities until
     * the graph is rebuilt; the caller has to make sure it is.
     */
    static final class NodesLostException extends IllegalStateException
    {
        private static final long serialVersionUID = 1L;

        private final int lost;

        NodesLostException(
            final String           name    ,
            final int              lost    ,
            final int              orphans ,
            final RuntimeException cause
        )
        {
            super(lost + " of " + orphans + " unreachable nodes of the graph of '" + name
                + "' could not be inserted again; no search finds them until the graph is rebuilt", cause);
            this.lost = lost;
        }

        /**
         * @return how many nodes the graph lost
         */
        int lost()
        {
            return this.lost;
        }
    }


    ///////////////////////////////////////////////////////////////////////////
    // constructors //
    /////////////////

    private GraphReachability()
    {
        throw new UnsupportedOperationException();
    }

}
