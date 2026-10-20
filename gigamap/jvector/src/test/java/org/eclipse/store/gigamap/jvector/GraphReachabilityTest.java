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
import io.github.jbellis.jvector.graph.ListRandomAccessVectorValues;
import io.github.jbellis.jvector.graph.NodeArray;
import io.github.jbellis.jvector.graph.NodesIterator;
import io.github.jbellis.jvector.graph.OnHeapGraphIndex;
import io.github.jbellis.jvector.util.FixedBitSet;
import io.github.jbellis.jvector.vector.VectorSimilarityFunction;
import io.github.jbellis.jvector.vector.VectorizationProvider;
import io.github.jbellis.jvector.vector.types.VectorFloat;
import io.github.jbellis.jvector.vector.types.VectorTypeSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link GraphReachability#repair}: an orphan, a node nothing points at, is re-inserted and reachable from
 * the entry node afterwards; a graph without one is left as it is, and so is an orphan marked deleted.
 */
class GraphReachabilityTest
{
    private static final VectorTypeSupport VECTOR_TYPE_SUPPORT =
        VectorizationProvider.getInstance().getVectorTypeSupport();

    private static final int NODE_COUNT = 300;
    private static final int DIMENSION  = 16 ;

    private List<VectorFloat<?>> vectors;
    private GraphIndexBuilder    builder;
    private OnHeapGraphIndex     graph  ;
    private int[]                all    ;

    @BeforeEach
    void setUp()
    {
        final Random random = new Random(42L);
        this.vectors = new ArrayList<>(NODE_COUNT);
        for(int i = 0; i < NODE_COUNT; i++)
        {
            final float[] vector = new float[DIMENSION];
            for(int d = 0; d < DIMENSION; d++)
            {
                vector[d] = random.nextFloat();
            }
            this.vectors.add(VECTOR_TYPE_SUPPORT.createFloatVector(vector));
        }
        final ListRandomAccessVectorValues ravv = new ListRandomAccessVectorValues(this.vectors, DIMENSION);
        this.builder = new GraphIndexBuilder(ravv, VectorSimilarityFunction.EUCLIDEAN, 16, 100, 1.2f, 1.2f, true);
        // Sequential inserts: every node is reachable before a test cuts one off.
        for(int i = 0; i < NODE_COUNT; i++)
        {
            this.builder.addGraphNode(i, this.vectors.get(i));
        }
        this.graph = (OnHeapGraphIndex)this.builder.getGraph();
        this.all   = IntStream.range(0, NODE_COUNT).toArray();
    }

    @Test
    void repair_everyNodeReachable_changesNothing()
    {
        final int repaired = GraphReachability.repair(this.builder, this.all, NODE_COUNT, this.vectors::get, "test");

        assertEquals(0, repaired);
        assertEquals(NODE_COUNT, this.reachable().cardinality());
    }

    @Test
    void repair_orphanedNode_reinsertsItReachable()
    {
        final int orphan = this.someNodeButTheEntry();
        this.cutOff(orphan);
        assertFalse(this.reachable().get(orphan), "precondition: the node is unreachable");

        final int repaired = GraphReachability.repair(this.builder, this.all, NODE_COUNT, this.vectors::get, "test");

        assertEquals(1, repaired);
        assertTrue(this.reachable().get(orphan), "the orphan must be reachable after the repair");
        assertEquals(NODE_COUNT, this.reachable().cardinality());
    }

    @Test
    void repair_orphanedNodeOutsideCandidates_reinsertsItWithTheUnreachableCandidate()
    {
        final int[] orphans = this.twoNodesButTheEntry();
        this.cutOff(orphans[0]);
        this.cutOff(orphans[1]);

        // Only the first is asked about; the complete walk finds the second as well.
        final int repaired = GraphReachability.repair(
            this.builder, new int[] {orphans[0]}, 1, this.vectors::get, "test"
        );

        assertEquals(2, repaired);
        assertEquals(NODE_COUNT, this.reachable().cardinality());
    }

    @Test
    void repair_orphanMarkedDeleted_leavesItAlone()
    {
        final int orphan = this.someNodeButTheEntry();
        this.cutOff(orphan);
        this.builder.markNodeDeleted(orphan);

        final int repaired = GraphReachability.repair(this.builder, this.all, NODE_COUNT, this.vectors::get, "test");

        assertEquals(0, repaired);
        assertTrue(this.graph.containsNode(orphan));
        assertFalse(this.reachable().get(orphan));
    }

    private int someNodeButTheEntry()
    {
        return this.twoNodesButTheEntry()[0];
    }

    private int[] twoNodesButTheEntry()
    {
        final int entry = this.graph.entryNode().node;
        return IntStream.range(0, NODE_COUNT).filter(node -> node != entry).limit(2).toArray();
    }

    /**
     * Strips every edge pointing at {@code node}, on every layer, the state a pruned backlink leaves behind.
     */
    private void cutOff(final int node)
    {
        final FixedBitSet target = new FixedBitSet(this.graph.getIdUpperBound());
        target.set(node);
        for(int level = 0; level <= this.graph.getMaxLevel(); level++)
        {
            final int   layer     = level;
            final int[] referrers = this.graph.nodeStream(layer).filter(other -> other != node).toArray();
            for(final int other : referrers)
            {
                this.graph.replaceDeletedNeighbors(layer, other, target, new NodeArray(1));
            }
        }
    }

    private FixedBitSet reachable()
    {
        final FixedBitSet         reached = new FixedBitSet(this.graph.getIdUpperBound());
        final ArrayDeque<Integer> queue   = new ArrayDeque<>();
        final int                 entry   = this.graph.entryNode().node;
        reached.set(entry);
        queue.add(entry);
        while(!queue.isEmpty())
        {
            final int node = queue.poll();
            for(final NodesIterator neighbours = this.graph.getNeighborsIterator(0, node); neighbours.hasNext();)
            {
                final int neighbour = neighbours.nextInt();
                if(!reached.getAndSet(neighbour))
                {
                    queue.add(neighbour);
                }
            }
        }
        return reached;
    }

}
