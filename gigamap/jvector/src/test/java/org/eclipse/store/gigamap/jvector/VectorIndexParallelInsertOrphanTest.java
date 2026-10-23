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

import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Nodes inserted in parallel into an empty graph must all stay reachable. jVector sets the entry node only when an
 * insert completes, so every insert that starts before that searches an empty graph and links only to the inserts
 * in progress at its start; one that finds none gets no edge from any node a search reaches, and the live graph,
 * which never runs jVector's cleanup, would keep that orphan for good. Two mechanisms prevent it: the graph is
 * seeded with one node inserted on the calling thread ({@code VectorIndex.Default#seedIfEmpty}), and every parallel
 * insertion ends with a reachability repair, which also reconnects the nodes that concurrent inserts leave
 * unreachable in a graph that is not empty. This checks their result: no orphan survives a parallel build. Each
 * mechanism alone keeps it green, so it does not tell which one did the work.
 * <p>
 * Reachability is checked with a wide beam, so a node the narrow top-1 search merely passes by does not count: a
 * search for a node's own vector with a beam as wide as the graph finds every node a path from the entry reaches.
 */
class VectorIndexParallelInsertOrphanTest
{
    /** above ParallelGraphInsertion.MIN_PARALLEL_NODES, so the inserts run on the workers */
    private static final int COUNT  = 300;
    private static final int ROUNDS = 40;

    static class Doc
    {
        final int     no;
        final float[] vector;

        Doc(final int no)
        {
            this.no     = no;
            this.vector = position(no);
        }
    }

    static float[] position(final int no)
    {
        return new float[]{1.0f + no, 1.0f + no % 7, no * 0.5f, 1.0f + no % 3};
    }

    static class EmbeddedVectorizer extends Vectorizer<Doc>
    {
        @Override
        public float[] vectorize(final Doc entity)
        {
            return entity.vector;
        }

        @Override
        public boolean isEmbedded()
        {
            return true;
        }
    }

    private static VectorIndexConfiguration inMemory()
    {
        return VectorIndexConfiguration.builder()
            .dimension(4)
            .similarityFunction(VectorSimilarityFunction.EUCLIDEAN)
            .build();
    }

    private static List<Doc> docs()
    {
        final List<Doc> docs = new ArrayList<>(COUNT);
        for(int no = 0; no < COUNT; no++)
        {
            docs.add(new Doc(no));
        }
        return docs;
    }

    /** the layer-0 edges of a node and the nodes that have an edge to it, for a failure message */
    static String describe(final VectorIndex<Doc> index, final int node)
    {
        try
        {
            final java.lang.reflect.Field field = VectorIndex.Default.class.getDeclaredField("builder");
            field.setAccessible(true);
            final io.github.jbellis.jvector.graph.OnHeapGraphIndex graph =
                (io.github.jbellis.jvector.graph.OnHeapGraphIndex)((io.github.jbellis.jvector.graph.GraphIndexBuilder)field.get(index)).getGraph();
            final StringBuilder out = new StringBuilder(node + ": out=[");
            final var outgoing = graph.getNeighborsIterator(0, node);
            while(outgoing.hasNext())
            {
                out.append(outgoing.nextInt()).append(' ');
            }
            out.append("] in=[");
            final var nodes = graph.nodeStream(0).iterator();
            while(nodes.hasNext())
            {
                final int other = nodes.nextInt();
                final var edges = graph.getNeighborsIterator(0, other);
                while(edges.hasNext())
                {
                    if(edges.nextInt() == node)
                    {
                        out.append(other).append(' ');
                        break;
                    }
                }
            }
            return out.append("] entry=").append(graph.entryNode()).toString();
        }
        catch(final ReflectiveOperationException e)
        {
            return node + ": " + e;
        }
    }

    private static List<Integer> unreachable(final VectorIndex<Doc> index)
    {
        final List<Integer> unreachable = new ArrayList<>();
        for(int no = 0; no < COUNT; no++)
        {
            final int expected = no;
            if(index.search(position(no), 10, COUNT).toList().stream().noneMatch(e -> e.entity().no == expected))
            {
                unreachable.add(no);
            }
        }
        return unreachable;
    }

    @BeforeEach
    void requireTheParallelPath()
    {
        // Below two physical cores the insertion runs sequentially, which cannot leave such an orphan.
        assumeTrue(ParallelGraphInsertion.parallelism() >= 2, "needs at least two cores for the parallel insertion");
    }

    /** a batch added at once goes through the parallel batch insertion into the empty live graph */
    @Test
    @Timeout(value = 300, unit = TimeUnit.SECONDS)
    void batchIntoAnEmptyGraph_leavesNoOrphan()
    {
        for(int round = 0; round < ROUNDS; round++)
        {
            final GigaMap<Doc>     map   = GigaMap.New();
            final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
                .add("emb", inMemory(), new EmbeddedVectorizer());
            try
            {
                map.addAll(docs());
                final int           r       = round;
                final List<Integer> orphans = unreachable(index);
                assertEquals(List.of(), orphans, () -> "round " + r + ": nodes no search reaches; first: "
                    + describe(index, orphans.get(0)) + "; predecessor: "
                    + describe(index, Math.max(0, orphans.get(0) - 1)));
            }
            finally
            {
                index.close();
            }
        }
    }

    /** the first access after a load rebuilds the graph from the vector file in parallel */
    @Test
    @Timeout(value = 300, unit = TimeUnit.SECONDS)
    void rebuildAfterLoad_leavesNoOrphan(@TempDir final Path tempDir)
    {
        final Path storageDir = tempDir.resolve("storage");
        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final GigaMap<Doc> map = GigaMap.New();
            storage.setRoot(map);
            map.index().register(VectorIndices.Category()).add("emb", inMemory(), new EmbeddedVectorizer());
            map.addAll(docs());
            storage.storeRoot();
        }
        for(int round = 0; round < ROUNDS / 2; round++)
        {
            try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
            {
                @SuppressWarnings("unchecked")
                final GigaMap<Doc> map = (GigaMap<Doc>)storage.root();
                @SuppressWarnings("unchecked")
                final VectorIndex<Doc> index = map.index().get(VectorIndices.class).get("emb");
                // the first search runs the parallel rebuild
                assertEquals(List.of(), unreachable(index), "restart " + round + ": nodes no search reaches");
            }
        }
    }
}
