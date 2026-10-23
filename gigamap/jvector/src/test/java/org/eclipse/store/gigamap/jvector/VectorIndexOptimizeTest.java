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

import org.eclipse.serializer.exceptions.IORuntimeException;
import org.eclipse.store.gigamap.types.GigaMap;
import io.github.jbellis.jvector.graph.NodesIterator;
import io.github.jbellis.jvector.graph.OnHeapGraphIndex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that {@link VectorIndex#optimize()} leaves the live in-memory graph mutable.
 * <p>
 * Optimization used to run jVector's {@code GraphIndexBuilder.cleanup()} on the builder that keeps
 * receiving every later insert. {@code cleanup()} is a finishing step meant for a graph about to
 * be written: it ends with {@code OnHeapGraphIndex.setAllMutationsCompleted()}, a one-way flag that
 * nothing ever resets. From then on {@code getView()} hands out a {@code FrozenView} instead of a
 * {@code ConcurrentGraphIndexView}, so every search - and every insert's own neighbor search - loses
 * the snapshot isolation that keeps nodes whose insertion is still in progress off the search path,
 * while the index keeps mutating the graph. An in-memory index is never replaced, so one optimize
 * froze its graph for the rest of the session; an incremental on-disk index stayed frozen until the
 * next persist swapped the builder.
 * <p>
 * Searching sequentially does not expose the difference, because a frozen view shows every node.
 * The assertions therefore check the graph state itself, and additionally that the maintenance an
 * optimization is for still happens: deleted nodes are purged, overflowed neighbor lists are trimmed,
 * and nodes inserted afterwards are found.
 * <p>
 * Persist does still run {@code cleanup()}, because the graph it cleans is replaced right after the
 * write. When the write fails or the reload rejects the written file, nothing replaced it: the frozen
 * builder stayed live and received the deferred mutations and every later one. The persist tests
 * cover both failure paths.
 */
class VectorIndexOptimizeTest
{
    static final int DIM           = 32;
    static final int PQ_DIM        = 64;
    static final int INITIAL_COUNT = 300;
    static final int ADDED_COUNT   = 100;
    static final int MAX_DEGREE    = 16;

    record Doc(String content, float[] embedding) {}

    static class ComputedDocVectorizer extends Vectorizer<Doc>
    {
        @Override
        public float[] vectorize(final Doc entity)
        {
            return entity.embedding();
        }
    }

    static class EmbeddedDocVectorizer extends Vectorizer<Doc>
    {
        @Override
        public float[] vectorize(final Doc entity)
        {
            return entity.embedding();
        }

        @Override
        public boolean isEmbedded()
        {
            return true;
        }
    }

    /**
     * Deterministic pseudo-random unit vector.
     */
    static float[] vec(
        final int seed,
        final int dimension
    )
    {
        // Scramble the seed: java.util.Random's first outputs for nearby seeds are strongly
        // correlated, which would make "nearest neighbor" assertions depend on luck.
        final Random random = new Random(seed * 6364136223846793005L + 1442695040888963407L);
        final float[] vector = new float[dimension];
        float norm = 0;
        for(int i = 0; i < dimension; i++)
        {
            vector[i] = random.nextFloat() * 2 - 1;
            norm += vector[i] * vector[i];
        }
        norm = (float)Math.sqrt(norm);
        for(int i = 0; i < dimension; i++)
        {
            vector[i] /= norm;
        }
        return vector;
    }

    private static VectorIndexConfiguration.Builder baseConfig(final int dimension)
    {
        return VectorIndexConfiguration.builder()
            .dimension(dimension)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .maxDegree(MAX_DEGREE)
            .beamWidth(100);
    }

    /**
     * Reads the live in-memory graph of a {@link VectorIndex.Default}. The frozen flag is jVector-internal
     * state that sequential search cannot observe, so the regression is asserted on it directly.
     */
    private static OnHeapGraphIndex liveGraph(final VectorIndex<?> index)
    {
        return internalState(index, "index");
    }

    private static int segmentCount(final VectorIndex<?> index)
    {
        return ((VectorIndex.Default<?>)index).sourceSet().segments().size();
    }

    @SuppressWarnings("unchecked")
    private static <T> T internalState(
        final VectorIndex<?> index,
        final String         fieldName
    )
    {
        try
        {
            final Field field = VectorIndex.Default.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return (T)field.get(index);
        }
        catch(final ReflectiveOperationException e)
        {
            throw new AssertionError("cannot read VectorIndex.Default." + fieldName, e);
        }
    }

    private static void deleteRecursively(final Path path)
    {
        if(!Files.exists(path))
        {
            return;
        }
        try(final Stream<Path> paths = Files.walk(path))
        {
            for(final Path each : paths.sorted(Comparator.reverseOrder()).toList())
            {
                Files.delete(each);
            }
        }
        catch(final IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }

    private static void assertFound(
        final VectorIndex<Doc> index,
        final String           content,
        final float[]          vector
    )
    {
        final VectorSearchResult<Doc> result = index.search(vector, 1);
        assertFalse(result.isEmpty(), "no result for " + content);
        assertEquals(content, result.toList().get(0).entity().content(), content + " must be found");
    }

    private static void assertLiveGraphMutable(
        final VectorIndex<?> index,
        final String         when
    )
    {
        final OnHeapGraphIndex graph = liveGraph(index);
        assertFalse(graph.allMutationsCompleted(),
            "the live graph must not be marked as having all mutations completed " + when);
        assertInstanceOf(OnHeapGraphIndex.ConcurrentGraphIndexView.class, graph.getView(),
            "searches on the live graph must get a snapshot-isolated view " + when);
    }

    private static void addAndAssertEachFound(
        final GigaMap<Doc>     map,
        final VectorIndex<Doc> index,
        final int              firstSeed,
        final int              dimension
    )
    {
        for(int seed = firstSeed; seed < firstSeed + ADDED_COUNT; seed++)
        {
            final String content = "added-" + seed;
            map.add(new Doc(content, vec(seed, dimension)));
            assertFound(index, content, vec(seed, dimension));
        }
    }

    private static void awaitAnotherOptimization(
        final AtomicInteger              optimizations,
        final AtomicReference<Throwable> failure
    )
        throws InterruptedException
    {
        final int  seen     = optimizations.get();
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while(optimizations.get() == seen && failure.get() == null)
        {
            assertTrue(System.nanoTime() < deadline, "the optimizer must keep optimizing");
            Thread.sleep(1);
        }
    }

    private static int maxLevelZeroDegree(final OnHeapGraphIndex graph)
    {
        int max = 0;
        for(final NodesIterator nodes = graph.getNodes(0); nodes.hasNext();)
        {
            max = Math.max(max, graph.getNeighborsIterator(0, nodes.nextInt()).size());
        }
        return max;
    }


    // ==================== the live graph stays mutable ====================

    /**
     * In-memory index: the builder is never replaced, so a frozen graph would stay frozen for the
     * rest of the session.
     */
    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void optimize_inMemoryIndex_liveGraphStaysMutable()
    {
        final GigaMap<Doc> map = GigaMap.New();
        try(final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
            .add("embeddings", baseConfig(DIM).build(), new ComputedDocVectorizer()))
        {
            for(int i = 0; i < INITIAL_COUNT; i++)
            {
                map.add(new Doc("d" + i, vec(i, DIM)));
            }
            assertLiveGraphMutable(index, "before optimize()");

            index.optimize();
            assertLiveGraphMutable(index, "after optimize()");

            addAndAssertEachFound(map, index, 10_000, DIM);
            assertLiveGraphMutable(index, "after inserting past optimize()");

            // A second pass must not freeze it either.
            index.optimize();
            assertLiveGraphMutable(index, "after a second optimize()");
        }
    }

    /**
     * Embedded vectorizer: the maintenance workers resolve vectors through the GigaMap, so this also
     * covers the lock posture (no deadlock against the parentMap monitor).
     */
    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void optimize_embeddedVectorizer_liveGraphStaysMutable()
    {
        final GigaMap<Doc> map = GigaMap.New();
        try(final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
            .add("embeddings", baseConfig(DIM).build(), new EmbeddedDocVectorizer()))
        {
            for(int i = 0; i < INITIAL_COUNT; i++)
            {
                map.add(new Doc("d" + i, vec(i, DIM)));
            }
            for(int i = 0; i < INITIAL_COUNT; i += 10)
            {
                map.removeById(i);
            }

            index.optimize();
            assertLiveGraphMutable(index, "after optimize()");

            addAndAssertEachFound(map, index, 10_000, DIM);
        }
    }

    /**
     * In-memory index: optimize maintains the live graph under the locks that mutations take, scoring
     * from a snapshot so its workers need neither. A mutation issued meanwhile waits for it and is then
     * applied to the graph directly, with nothing deferred to replay, so every entity is found by its
     * latest vector and no removed one is found.
     *
     * @param embedded whether to use an embedded vectorizer instead of a computed one
     */
    @ParameterizedTest(name = "embedded={0}")
    @ValueSource(booleans = {false, true})
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void optimize_inMemory_concurrentMutationsAreAppliedDirectly(final boolean embedded) throws InterruptedException
    {
        final GigaMap<Doc> map = GigaMap.New();
        final Vectorizer<Doc> vectorizer = embedded ? new EmbeddedDocVectorizer() : new ComputedDocVectorizer();
        try(final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
            .add("embeddings", baseConfig(DIM).build(), vectorizer))
        {
            final Map<Long, Integer> seeds = new HashMap<>();
            for(int i = 0; i < INITIAL_COUNT; i++)
            {
                seeds.put(map.add(new Doc("d" + i, vec(i, DIM))), i);
            }

            final AtomicBoolean              running       = new AtomicBoolean(true);
            final AtomicInteger              optimizations = new AtomicInteger();
            final AtomicReference<Throwable> failure       = new AtomicReference<>();
            final Thread                     optimizer     = new Thread(() ->
            {
                try
                {
                    while(running.get())
                    {
                        index.optimize();
                        optimizations.incrementAndGet();
                        // A pause, so that the mutations do not each queue behind back-to-back passes.
                        Thread.sleep(2);
                    }
                }
                catch(final Throwable t)
                {
                    failure.set(t);
                }
            }, "optimizer");
            optimizer.setDaemon(true);
            optimizer.start();

            try
            {
                final Random random = new Random(3);
                for(int op = 0; op < 300; op++)
                {
                    if(op % 50 == 0)
                    {
                        // Interleaves the mutations with optimizations that start while they go on.
                        awaitAnotherOptimization(optimizations, failure);
                    }
                    final int  seed   = 20_000 + op;
                    final long target = seeds.keySet().stream()
                        .skip(random.nextInt(seeds.size()))
                        .findFirst()
                        .orElseThrow();
                    switch(op % 3)
                    {
                        case 0 -> seeds.put(map.add(new Doc("a" + seed, vec(seed, DIM))), seed);
                        case 1 ->
                        {
                            map.set(target, new Doc("u" + seed, vec(seed, DIM)));
                            seeds.put(target, seed);
                        }
                        default ->
                        {
                            map.removeById(target);
                            seeds.remove(target);
                        }
                    }
                }
            }
            finally
            {
                running.set(false);
                optimizer.join(TimeUnit.SECONDS.toMillis(60));
            }
            assertFalse(optimizer.isAlive(), "the optimizer did not finish");
            assertNull(failure.get(), () -> "optimize failed: " + failure.get());

            // A beam over every node: the graph's shape depends on how the threads interleaved, and what this
            // checks is that each entity's node carries its latest vector, not the recall of that shape.
            final int beam = seeds.size() + 50;
            for(final Map.Entry<Long, Integer> entry : seeds.entrySet())
            {
                final VectorSearchResult<Doc> result = index.search(vec(entry.getValue(), DIM), 1, beam);
                assertEquals(entry.getKey(), result.toList().get(0).entityId(),
                    "entity " + entry.getKey() + " must be found by its latest vector");
            }
            final VectorSearchResult<Doc> all = index.search(vec(0, DIM), seeds.size() + 50);
            assertEquals(seeds.size(), all.size(), "every entity, and no removed one, must be found");
            all.toList().forEach(hit -> assertTrue(seeds.containsKey(hit.entityId()), "removed entity found"));
            assertLiveGraphMutable(index, "after optimizing concurrently with mutations");
        }
    }

    /**
     * On-disk index: optimize leaves the graphs alone, since every flush cleans up the graph it writes,
     * and the live graph that carries the mutations since the last persist stays mutable across it and
     * across the next persist, which freezes it and installs a fresh one.
     */
    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void optimize_onDiskIndex_leavesTheLiveGraphMutable(@TempDir final Path indexDir)
    {
        final GigaMap<Doc> map = GigaMap.New();
        try(final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
            .add("embeddings", baseConfig(DIM).onDisk(true).indexDirectory(indexDir).build(),
                new ComputedDocVectorizer()))
        {
            for(int i = 0; i < INITIAL_COUNT; i++)
            {
                map.add(new Doc("d" + i, vec(i, DIM)));
            }
            index.persistToDisk(); // the first segment

            for(int i = INITIAL_COUNT; i < INITIAL_COUNT + ADDED_COUNT; i++)
            {
                map.add(new Doc("d" + i, vec(i, DIM)));
            }

            index.optimize();
            assertLiveGraphMutable(index, "after optimize()");

            addAndAssertEachFound(map, index, 10_000, DIM);

            // Persist still writes, and leaves a fresh, mutable live graph.
            index.persistToDisk();
            assertEquals(2, segmentCount(index));
            assertLiveGraphMutable(index, "after the persist following optimize()");
            addAndAssertEachFound(map, index, 20_000, DIM);
        }
    }

    /**
     * In-memory PQ: the switch publishes a replacement builder that the same optimization then
     * maintains, and that replacement is the live builder from then on.
     */
    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void optimize_inMemoryPqSwitch_replacementGraphStaysMutable()
    {
        final VectorIndexConfiguration config = baseConfig(PQ_DIM)
            .approximateScoring(ApproximateScoring.PQ_IN_MEMORY)
            .pqSubspaces(PQ_DIM / 4)
            // The switch happens at optimization, so there has to be one; long, because the test
            // calls optimize() directly.
            .optimizationIntervalMs(3_600_000)
            .build();

        final GigaMap<Doc> map = GigaMap.New();
        try(final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
            .add("embeddings", config, new ComputedDocVectorizer()))
        {
            for(int i = 0; i < INITIAL_COUNT; i++)
            {
                map.add(new Doc("d" + i, vec(i, PQ_DIM)));
            }

            index.optimize();
            assertTrue(index.isPqCompressionActive(), "precondition: optimize() performs the PQ switch");
            assertLiveGraphMutable(index, "after the optimize() that switched to PQ");

            addAndAssertEachFound(map, index, 10_000, PQ_DIM);
        }
    }


    // ==================== the maintenance still happens ====================

    /**
     * Not freezing the graph must not mean skipping the work: deleted nodes are still purged.
     */
    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void optimize_removedEntities_purgedFromLiveGraph()
    {
        final GigaMap<Doc> map = GigaMap.New();
        try(final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
            .add("embeddings", baseConfig(DIM).build(), new ComputedDocVectorizer()))
        {
            final long[] ids = new long[INITIAL_COUNT];
            for(int i = 0; i < INITIAL_COUNT; i++)
            {
                ids[i] = map.add(new Doc("d" + i, vec(i, DIM)));
            }
            for(int i = 0; i < INITIAL_COUNT; i += 3)
            {
                map.removeById(ids[i]);
            }
            final OnHeapGraphIndex graph = liveGraph(index);
            assertTrue(graph.getDeletedNodes().cardinality() > 0, "precondition: removals mark nodes deleted");

            index.optimize();

            assertEquals(0, graph.getDeletedNodes().cardinality(), "optimize() must purge deleted nodes");
            for(int i = 0; i < INITIAL_COUNT; i += 3)
            {
                assertFalse(graph.containsNode((int)ids[i]), "removed node " + ids[i] + " is still in the graph");
            }
            assertLiveGraphMutable(index, "after optimize()");
        }
    }

    /**
     * Not freezing the graph must not mean skipping the work: neighbor lists that overflowed during
     * construction are still trimmed to the configured maximum degree.
     */
    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void optimize_overflowedNeighborLists_trimmedToMaxDegree()
    {
        // A generous overflow so construction is certain to leave lists above the maximum degree.
        final VectorIndexConfiguration config = baseConfig(DIM).neighborOverflow(2.0f).build();

        final GigaMap<Doc> map = GigaMap.New();
        try(final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
            .add("embeddings", config, new ComputedDocVectorizer()))
        {
            for(int i = 0; i < INITIAL_COUNT; i++)
            {
                map.add(new Doc("d" + i, vec(i, DIM)));
            }
            final OnHeapGraphIndex graph = liveGraph(index);
            assertTrue(maxLevelZeroDegree(graph) > MAX_DEGREE,
                "precondition: construction left neighbor lists above the maximum degree");

            index.optimize();

            assertTrue(maxLevelZeroDegree(graph) <= MAX_DEGREE,
                "optimize() must trim neighbor lists to the maximum degree");
            assertLiveGraphMutable(index, "after optimize()");
        }
    }


    // ==================== a failed flush keeps the frozen graph ====================

    /**
     * The write of a flush fails after {@code cleanup()} froze the graph: the index directory is
     * replaced by a regular file once the graph is frozen, so creating it fails. The persist must fail,
     * the frozen graph stay searchable, the live graph mutable, and a later persist must write the
     * frozen graph after all.
     */
    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void flush_writeFails_frozenGraphStaysSearchableAndIsWrittenLater(@TempDir final Path tempDir) throws IOException
    {
        final Path indexDir = tempDir.resolve("index");
        final GigaMap<Doc> map = GigaMap.New();
        try(final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
            .add("embeddings", baseConfig(DIM).onDisk(true).indexDirectory(indexDir).build(),
                new ComputedDocVectorizer()))
        {
            for(int i = 0; i < INITIAL_COUNT; i++)
            {
                map.add(new Doc("d" + i, vec(i, DIM)));
            }
            index.persistToDisk();
            assertEquals(1, segmentCount(index), "precondition: the first persist writes a segment");

            map.add(new Doc("dirty", vec(5_000, DIM)));

            final VectorIndex.Default<Doc> def = (VectorIndex.Default<Doc>)index;
            FlushHooks.onceAt(def, VectorIndex.Default.FlushStage.FROZEN, () ->
            {
                deleteRecursively(indexDir);
                try
                {
                    Files.createFile(indexDir);
                }
                catch(final IOException e)
                {
                    throw new UncheckedIOException(e);
                }
                // Into the fresh live graph, while the flush is in flight.
                map.add(new Doc("in-window", vec(6_000, DIM)));
            });

            final IORuntimeException failure = assertThrows(IORuntimeException.class, index::persistToDisk,
                "the failed write must fail the persist");
            assertInstanceOf(IOException.class, failure.getCause());

            assertEquals(1, def.sourceSet().frozen().size(), "the frozen graph must stay a source");
            assertLiveGraphMutable(index, "after a persist whose write failed");
            assertFound(index, "dirty", vec(5_000, DIM));
            assertFound(index, "in-window", vec(6_000, DIM));
            assertFound(index, "d7", vec(7, DIM));
            addAndAssertEachFound(map, index, 10_000, DIM);

            Files.delete(indexDir);
            index.persistToDisk();
            assertTrue(def.sourceSet().frozen().isEmpty(), "a later persist must write the frozen graph");
            assertEquals(3, segmentCount(index), "the retried frozen graph and the live graph frozen with it");
            assertLiveGraphMutable(index, "after the persist that succeeded");
            assertFound(index, "dirty", vec(5_000, DIM));
            assertFound(index, "in-window", vec(6_000, DIM));
            addAndAssertEachFound(map, index, 20_000, DIM);
        }
    }

    /**
     * The commit of a flush fails before the new manifest replaces the old one. Nothing is committed:
     * the frozen graph stays searchable and the previous manifest stays what is on disk, and a later
     * persist commits everything.
     */
    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void flush_commitFailsBeforeTheMove_commitsNothing(@TempDir final Path tempDir)
    {
        final Path indexDir = tempDir.resolve("index");
        final GigaMap<Doc> map = GigaMap.New();
        try(final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
            .add("embeddings", baseConfig(DIM).onDisk(true).indexDirectory(indexDir).build(),
                new ComputedDocVectorizer()))
        {
            for(int i = 0; i < INITIAL_COUNT; i++)
            {
                map.add(new Doc("d" + i, vec(i, DIM)));
            }
            index.persistToDisk();
            map.add(new Doc("dirty", vec(5_000, DIM)));

            final VectorIndex.Default<Doc> def = (VectorIndex.Default<Doc>)index;
            def.commitStageTestHook = stage ->
            {
                if(stage == DiskIndexManager.CommitStage.MANIFEST_TEMP_WRITTEN)
                {
                    def.commitStageTestHook = null;
                    throw new IllegalStateException("injected commit failure");
                }
            };
            assertThrows(IllegalStateException.class, index::persistToDisk);

            assertEquals(1, segmentCount(index), "nothing may be published");
            assertEquals(1, def.sourceSet().frozen().size(), "the frozen graph must stay a source");
            assertEquals(1, SegmentLayout.graphFiles(indexDir, "embeddings").size(),
                "the uncommitted segment's files must be gone again");
            assertFound(index, "dirty", vec(5_000, DIM));
            assertLiveGraphMutable(index, "after a persist whose commit failed");

            index.persistToDisk();
            assertEquals(2, segmentCount(index));
            assertTrue(def.sourceSet().frozen().isEmpty());
            assertFound(index, "dirty", vec(5_000, DIM));
            addAndAssertEachFound(map, index, 20_000, DIM);
        }
    }
}
