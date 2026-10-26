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
import io.github.jbellis.jvector.graph.GraphIndexBuilder;
import io.github.jbellis.jvector.graph.NodeArray;
import io.github.jbellis.jvector.graph.NodesIterator;
import io.github.jbellis.jvector.graph.OnHeapGraphIndex;
import io.github.jbellis.jvector.graph.disk.OnDiskGraphIndex;
import io.github.jbellis.jvector.util.FixedBitSet;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Every node of a graph stays reachable: a walk over layer 0 from the entry node, which a search with a large
 * enough {@code k} explores, reaches every node. With near-duplicate vectors, the parallel
 * inserts of a batch or a rebuild left nodes nothing pointed at, and so did replacing vectors one by one, see
 * {@link GraphReachability}.
 * <p>
 * The reachability is checked on the graph rather than through a search on purpose: jVector's search drops a
 * candidate on an upper layer whose score exactly ties the best one so far, which near-duplicates do, so a
 * search can miss a node that is perfectly reachable. That is a defect of the search, not of the graph.
 */
class VectorIndexReachabilityTest
{
    record Document(int position, float[] embedding) {}

    static class ComputedDocumentVectorizer extends Vectorizer<Document>
    {
        @Override
        public float[] vectorize(final Document entity)
        {
            return entity.embedding();
        }
    }

    private static final int    DIMENSION    = 32          ;
    private static final int    NODES        = 600         ;
    private static final int    LOADS        = 20          ;
    // Fewer for the updates: each replaces every vector one by one, and the first loads already fail without
    // the repair.
    private static final int    UPDATE_LOADS = 5           ;
    private static final double STEP         = 0.0002      ;
    private static final String INDEX_NAME   = "embeddings";

    @TempDir
    Path directory;

    @Test
    @Timeout(value = 300, unit = TimeUnit.SECONDS)
    void addAll_nearDuplicateBatch_everyNodeReachable()
    {
        assumeTrue(ParallelGraphInsertion.parallelism() >= 2, "needs at least two cores");
        for(int load = 0; load < LOADS; load++)
        {
            final GigaMap<Document> gigaMap = GigaMap.New();
            try(final VectorIndex<Document> index = register(gigaMap, false))
            {
                gigaMap.addAll(documents(load));
                assertEveryNodeReachable(load, index);
            }
        }
    }

    @Test
    @Timeout(value = 300, unit = TimeUnit.SECONDS)
    void addAll_nearDuplicateBatchEventualIndexing_everyNodeReachable()
    {
        assumeTrue(ParallelGraphInsertion.parallelism() >= 2, "needs at least two cores");
        for(int load = 0; load < LOADS; load++)
        {
            final GigaMap<Document> gigaMap = GigaMap.New();
            try(final VectorIndex<Document> index = register(gigaMap, true))
            {
                gigaMap.addAll(documents(load));
                ((VectorIndex.Default<Document>)index).backgroundTaskManager.drainQueue();
                assertEveryNodeReachable(load, index);
            }
        }
    }

    @Test
    @Timeout(value = 300, unit = TimeUnit.SECONDS)
    void ensureGraphRebuilt_nearDuplicatesAddedOneByOne_everyNodeReachable()
    {
        assumeTrue(ParallelGraphInsertion.parallelism() >= 2, "needs at least two cores");
        for(int load = 0; load < LOADS; load++)
        {
            final Path storageDirectory = this.directory.resolve("load-" + load);
            final List<Document> documents = documents(load);
            try(final EmbeddedStorageManager storage =
                EmbeddedStorage.start(GigaMap.New(), storageDirectory))
            {
                final GigaMap<Document> gigaMap = rootOf(storage);
                register(gigaMap, false);
                // One by one: sequential inserts, so whatever is unreachable afterwards comes from the rebuild.
                documents.forEach(gigaMap::add);
                storage.storeRoot();
            }
            try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDirectory))
            {
                final VectorIndex<Document> index =
                    rootOf(storage).index().get(VectorIndices.Category()).get(INDEX_NAME);
                // The first search triggers the deferred rebuild, whose inserts run in parallel.
                index.search(documents.get(0).embedding(), 1);
                assertEveryNodeReachable(load, index);
            }
        }
    }

    @Test
    @Tag("slow")
    @Timeout(value = 300, unit = TimeUnit.SECONDS)
    void set_nearDuplicatesReplacedOneByOne_everyNodeReachable()
    {
        for(int load = 0; load < UPDATE_LOADS; load++)
        {
            final GigaMap<Document> gigaMap = GigaMap.New();
            try(final VectorIndex<Document> index = register(gigaMap, false))
            {
                final List<Document> documents = documents(load);
                documents.forEach(gigaMap::add);
                for(final Document document : documents)
                {
                    gigaMap.set(document.position(), new Document(
                        document.position(), vector(load + LOADS, document.position())
                    ));
                }
                assertEveryNodeReachable(load, index);
            }
        }
    }

    @Test
    @Tag("slow")
    @Timeout(value = 300, unit = TimeUnit.SECONDS)
    void persistToDisk_replacesMovedIntoFrozenGeneration_segmentEveryNodeReachable()
        throws InterruptedException, IOException
    {
        // The worker applies the replace of node 0 before the freeze, so no moved one re-inserts it.
        this.assertFrozenGenerationRepaired("replaced", (gigaMap, load) ->
        {
            for(int position = 0; position < NODES; position++)
            {
                gigaMap.set(position, new Document(position, vector(load + LOADS, position)));
            }
        });
    }

    @Test
    @Timeout(value = 300, unit = TimeUnit.SECONDS)
    void persistToDisk_removesMovedIntoFrozenGeneration_segmentEveryNodeReachable()
        throws InterruptedException, IOException
    {
        // Removes only: the moved operations just mark nodes deleted, and node 0 is none of them.
        this.assertFrozenGenerationRepaired("removed", (gigaMap, load) ->
        {
            for(int position = 1; position < NODES; position += 3)
            {
                gigaMap.removeById(position);
            }
        });
    }

    /**
     * Fills an eventual-indexing on-disk index with near-duplicates, holds its worker inside the first operation
     * {@code mutation} queues, so that the persist's freeze moves the others into the frozen generation, and
     * strands node 0 of the frozen graph before the first moved operation is applied. The flush must reconnect
     * it after the moved operations, or it writes the segment with node 0 unreachable.
     */
    private void assertFrozenGenerationRepaired(
        final String                                 name    ,
        final BiConsumer<GigaMap<Document>, Integer> mutation
    ) throws InterruptedException, IOException
    {
        final AtomicInteger stranded = new AtomicInteger();
        for(int load = 0; load < UPDATE_LOADS; load++)
        {
            final Path              indexDirectory = this.directory.resolve(name + "-" + load);
            final GigaMap<Document> gigaMap        = GigaMap.New();
            try(final VectorIndex<Document> index = gigaMap.index().register(VectorIndices.Category()).add(
                INDEX_NAME,
                VectorIndexConfiguration.builder()
                    .dimension(DIMENSION)
                    .similarityFunction(VectorSimilarityFunction.COSINE)
                    .onDisk(true)
                    .indexDirectory(indexDirectory)
                    .eventualIndexing(true)
                    .build(),
                new ComputedDocumentVectorizer()
            ))
            {
                final VectorIndex.Default<Document> def = (VectorIndex.Default<Document>)index;
                gigaMap.addAll(documents(load));
                def.backgroundTaskManager.drainQueue();

                final CountDownLatch applying = new CountDownLatch(1);
                final CountDownLatch release  = new CountDownLatch(1);
                def.backgroundTaskManager.applyTestHook = () ->
                {
                    def.backgroundTaskManager.applyTestHook = null;
                    applying.countDown();
                    awaitQuietly(release);
                };
                mutation.accept(gigaMap, load);
                awaitQuietly(applying);
                def.movedOpTestHook = () ->
                {
                    def.movedOpTestHook = null;
                    for(final HeapGeneration frozen : def.sourceSet().frozen())
                    {
                        if(frozen.graph().entryNode().node != 0)
                        {
                            cutOff(frozen.graph(), 0);
                            stranded.incrementAndGet();
                        }
                    }
                };
                final AtomicReference<Throwable> failure = new AtomicReference<>();
                final Thread persist = new Thread(() ->
                {
                    try
                    {
                        index.persistToDisk();
                    }
                    catch(final Throwable t)
                    {
                        failure.set(t);
                    }
                }, "persist");
                persist.start();
                final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
                while(persist.getState() != Thread.State.WAITING)
                {
                    assertTrue(System.nanoTime() < deadline, "the persist must wait for the indexing lock");
                    Thread.onSpinWait();
                }
                release.countDown();
                persist.join(TimeUnit.SECONDS.toMillis(120));
                assertFalse(persist.isAlive(), "load " + load + ": the persist did not finish");
                assertNull(failure.get(), "load " + load + ": the persist failed");

                final List<DiskSegment> segments = def.sourceSet().segments();
                assertFalse(segments.isEmpty(), "load " + load + ": the persist published no segment");
                for(final DiskSegment segment : segments)
                {
                    assertSegmentEveryNodeReachable(load, segment);
                }
            }
        }
        assertTrue(stranded.get() > 0, "no load stranded a node, so none tested the repair");
    }

    /**
     * Strips every edge pointing at {@code node}, on every layer, the state a pruned backlink leaves behind.
     */
    private static void cutOff(
        final OnHeapGraphIndex graph,
        final int              node
    )
    {
        final FixedBitSet target = new FixedBitSet(graph.getIdUpperBound());
        target.set(node);
        for(int level = 0; level <= graph.getMaxLevel(); level++)
        {
            final int   layer     = level;
            final int[] referrers = graph.nodeStream(layer).filter(other -> other != node).toArray();
            for(final int other : referrers)
            {
                graph.replaceDeletedNeighbors(layer, other, target, new NodeArray(1));
            }
        }
    }

    private static void assertSegmentEveryNodeReachable(
        final int         load   ,
        final DiskSegment segment
    ) throws IOException
    {
        try(final OnDiskGraphIndex.View view = segment.graph().getView())
        {
            final FixedBitSet         reached = new FixedBitSet(segment.nodeCount());
            final ArrayDeque<Integer> queue   = new ArrayDeque<>();
            final int                 entry   = view.entryNode().node;
            reached.set(entry);
            queue.add(entry);
            while(!queue.isEmpty())
            {
                final int node = queue.poll();
                for(final NodesIterator neighbours = view.getNeighborsIterator(0, node); neighbours.hasNext();)
                {
                    final int neighbour = neighbours.nextInt();
                    if(!reached.getAndSet(neighbour))
                    {
                        queue.add(neighbour);
                    }
                }
            }
            assertEquals(segment.nodeCount(), reached.cardinality(),
                "load " + load + ": the segment holds nodes not reachable from its entry node");
        }
    }

    private static void awaitQuietly(final CountDownLatch latch)
    {
        try
        {
            assertTrue(latch.await(30, TimeUnit.SECONDS), "timed out");
        }
        catch(final InterruptedException e)
        {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    private static VectorIndex<Document> register(
        final GigaMap<Document> gigaMap ,
        final boolean           eventual
    )
    {
        return gigaMap.index().register(VectorIndices.Category()).add(
            INDEX_NAME,
            VectorIndexConfiguration.builder()
                .dimension(DIMENSION)
                .similarityFunction(VectorSimilarityFunction.COSINE)
                .eventualIndexing(eventual)
                .build(),
            new ComputedDocumentVectorizer()
        );
    }

    private static List<Document> documents(final int load)
    {
        final List<Document> documents = new ArrayList<>(NODES);
        for(int i = 0; i < NODES; i++)
        {
            documents.add(new Document(i, vector(load, i)));
        }
        return documents;
    }

    private static void assertEveryNodeReachable(
        final int                   load ,
        final VectorIndex<Document> index
    )
    {
        final FixedBitSet reached = reachableFromEntry(graphOf(index));
        for(int position = 0; position < NODES; position++)
        {
            assertTrue(reached.get(position),
                "load " + load + ": node " + position + " is not reachable from the entry node");
        }
    }

    /**
     * The live graph of {@code index}, read from its private field, as no other seam exposes it.
     */
    private static OnHeapGraphIndex graphOf(final VectorIndex<Document> index)
    {
        try
        {
            final Field field = VectorIndex.Default.class.getDeclaredField("builder");
            field.setAccessible(true);
            return (OnHeapGraphIndex)((GraphIndexBuilder)field.get(index)).getGraph();
        }
        catch(final ReflectiveOperationException e)
        {
            throw new IllegalStateException("the live graph of the index is not accessible", e);
        }
    }

    private static FixedBitSet reachableFromEntry(final OnHeapGraphIndex graph)
    {
        final FixedBitSet         reached = new FixedBitSet(graph.getIdUpperBound());
        final ArrayDeque<Integer> queue   = new ArrayDeque<>();
        final int                 entry   = graph.entryNode().node;
        reached.set(entry);
        queue.add(entry);
        while(!queue.isEmpty())
        {
            final int node = queue.poll();
            for(final NodesIterator neighbours = graph.getNeighborsIterator(0, node); neighbours.hasNext();)
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

    // near-duplicates: consecutive vectors are almost on top of each other, like the same text embedded twice
    private static float[] vector(
        final int load ,
        final int index
    )
    {
        final float[] vector = new float[DIMENSION];
        final double  t      = (load * 100_000 + index) * STEP;
        final Random  random = new Random(load * 100_000L + index);
        double        norm   = 0;
        for(int j = 0; j < DIMENSION; j++)
        {
            vector[j] = (float)(Math.cos(t * (j + 1)) + 1e-4 * random.nextGaussian());
            norm += vector[j] * (double)vector[j];
        }
        final float inverse = (float)(1 / Math.sqrt(norm));
        for(int j = 0; j < DIMENSION; j++)
        {
            vector[j] *= inverse;
        }
        return vector;
    }


    @SuppressWarnings("unchecked")
    private static GigaMap<Document> rootOf(final EmbeddedStorageManager storage)
    {
        return (GigaMap<Document>)storage.root();
    }
}
