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
import org.eclipse.store.gigamap.types.ScoredSearchResult;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import io.github.jbellis.jvector.graph.GraphIndexBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The full graph rebuild runs its inserts in parallel and scores from the vector file instead of the
 * GigaMap. These tests cover both rebuild paths - the deferred first-access rebuild after a
 * load ({@code ensureGraphRebuilt}) and the consolidation of an on-disk index's segments - for computed and embedded
 * vectorizers, with holes in the ordinal space, and that a plain persist rebuilds nothing.
 * <p>
 * Every index is large enough ({@link #COUNT} well above {@link ParallelGraphInsertion#MIN_PARALLEL_NODES}) for the
 * parallel path, and quality is checked as recall against brute-force nearest neighbours rather than against exact
 * result lists, since a concurrently built graph is not deterministic.
 */
class VectorIndexParallelRebuildTest
{
    private static final int    DIM        = 24;
    private static final int    COUNT      = 1_500;
    private static final int    K          = 10;
    private static final int    QUERIES    = 40;
    private static final double MIN_RECALL = 0.9;
    private static final String INDEX_NAME = "embeddings";

    /**
     * Names of the threads that called a vectorizer. Static because the vectorizer is persisted with the index and
     * comes back as a new instance after a restart.
     */
    static final Set<String> VECTORIZING_THREADS = ConcurrentHashMap.newKeySet();

    /**
     * Run on every vectorizer call when set; static for the same reason as {@link #VECTORIZING_THREADS}.
     */
    static volatile Runnable vectorizeHook;

    /**
     * An entity that may or may not carry an embedding.
     */
    record Doc(String content, float[] embedding) {}

    static class ComputedVectorizer extends Vectorizer<Doc>
    {
        @Override
        public float[] vectorize(final Doc entity)
        {
            VECTORIZING_THREADS.add(Thread.currentThread().getName());
            final Runnable hook = vectorizeHook;
            if(hook != null)
            {
                hook.run();
            }
            return entity.embedding();
        }

        @Override
        public boolean allowsNullVectors()
        {
            return true;
        }
    }

    static class EmbeddedVectorizer extends ComputedVectorizer
    {
        @Override
        public boolean isEmbedded()
        {
            return true;
        }
    }

    enum Mode
    {
        COMPUTED,
        EMBEDDED;

        Vectorizer<Doc> vectorizer()
        {
            return this == EMBEDDED ? new EmbeddedVectorizer() : new ComputedVectorizer();
        }
    }

    @BeforeEach
    void clearRecordedThreads()
    {
        VECTORIZING_THREADS.clear();
        vectorizeHook = null;
    }

    // ==================== helpers ====================

    private static float[] randomVector(final Random random)
    {
        final float[] vector = new float[DIM];
        double norm = 0;
        for(int i = 0; i < DIM; i++)
        {
            vector[i] = random.nextFloat() * 2 - 1;
            norm += vector[i] * vector[i];
        }
        final float scale = (float)(1.0 / Math.sqrt(norm));
        for(int i = 0; i < DIM; i++)
        {
            vector[i] *= scale;
        }
        return vector;
    }

    private static VectorIndexConfiguration config(final Path indexDir)
    {
        return config(indexDir, false);
    }

    private static VectorIndexConfiguration config(
        final Path    indexDir         ,
        final boolean eventualIndexing
    )
    {
        final VectorIndexConfiguration.Builder builder = VectorIndexConfiguration.builder()
            .dimension(DIM)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .eventualIndexing(eventualIndexing);
        if(indexDir != null)
        {
            builder.onDisk(true).indexDirectory(indexDir);
        }
        return builder.build();
    }

    /**
     * The root as the typed map: the storage manager is not generic over the root.
     */
    @SuppressWarnings("unchecked")
    private static GigaMap<Doc> rootOf(final EmbeddedStorageManager storage)
    {
        return (GigaMap<Doc>)storage.root();
    }

    private static VectorIndex<Doc> register(
        final GigaMap<Doc> map     ,
        final Mode         mode    ,
        final Path         indexDir
    )
    {
        return map.index().register(VectorIndices.Category()).add(INDEX_NAME, config(indexDir), mode.vectorizer());
    }

    private static VectorIndex<Doc> lookup(final GigaMap<Doc> map)
    {
        return map.index().get(VectorIndices.Category()).get(INDEX_NAME);
    }

    /**
     * Adds {@code count} documents, every seventh without an embedding, and returns the live vectors by entity id.
     */
    private static Map<Long, float[]> addDocs(
        final GigaMap<Doc> map   ,
        final Random       random,
        final int          count ,
        final String       prefix
    )
    {
        final Map<Long, float[]> vectors = new LinkedHashMap<>();
        for(int i = 0; i < count; i++)
        {
            final float[] vector = i % 7 == 3 ? null : randomVector(random);
            final long    id     = map.add(new Doc(prefix + i, vector));
            if(vector != null)
            {
                vectors.put(id, vector);
            }
        }
        return vectors;
    }

    /**
     * Removes every fifth entity, punching deletion holes into the ordinal space, and keeps {@code vectors} in step.
     */
    private static void removeEveryFifth(
        final GigaMap<Doc>       map    ,
        final Map<Long, float[]> vectors,
        final long               fromId ,
        final long               toId
    )
    {
        for(long id = fromId; id <= toId; id++)
        {
            if(id % 5 == 1)
            {
                map.removeById(id);
                vectors.remove(id);
            }
        }
    }

    private static float dot(final float[] a, final float[] b)
    {
        float sum = 0;
        for(int i = 0; i < a.length; i++)
        {
            sum += a[i] * b[i];
        }
        return sum;
    }

    private static Set<Long> bruteForce(final Map<Long, float[]> vectors, final float[] query)
    {
        final Set<Long> ids = new HashSet<>();
        vectors.entrySet().stream()
            .sorted(Comparator.comparingDouble((Map.Entry<Long, float[]> e) -> dot(e.getValue(), query)).reversed())
            .limit(K)
            .forEach(e -> ids.add(e.getKey()));
        return ids;
    }

    /**
     * Average recall@K of the index against brute force, over {@link #QUERIES} random queries. Also asserts that
     * no result names an entity without a live vector.
     */
    private static double recall(final VectorIndex<Doc> index, final Map<Long, float[]> vectors)
    {
        final Random random = new Random(4711);
        double total = 0;
        for(int q = 0; q < QUERIES; q++)
        {
            final float[]   query    = randomVector(random);
            final Set<Long> expected = bruteForce(vectors, query);
            int hits = 0;
            for(final ScoredSearchResult.Entry<Doc> entry : index.search(query, K))
            {
                assertTrue(vectors.containsKey(entry.entityId()),
                    "Result " + entry.entityId() + " is removed or has no embedding");
                if(expected.contains(entry.entityId()))
                {
                    hits++;
                }
            }
            total += (double)hits / K;
        }
        return total / QUERIES;
    }

    private static void assertRecall(final VectorIndex<Doc> index, final Map<Long, float[]> vectors)
    {
        final double recall = recall(index, vectors);
        assertTrue(recall >= MIN_RECALL, "recall@" + K + " was " + recall);
    }

    private static void assertNoRebuildWorkerVectorized()
    {
        // A rebuild worker that vectorized would have resolved an entity through the GigaMap, i.e. through the
        // monitor its caller holds: the deadlock reading the vector file rules out.
        for(final String thread : VECTORIZING_THREADS)
        {
            assertFalse(thread.startsWith("vector-rebuild-"), "Rebuild worker " + thread + " read the GigaMap");
        }
    }

    private static void deleteDirectory(final Path dir)
    {
        if(!Files.exists(dir))
        {
            return;
        }
        try(final Stream<Path> paths = Files.walk(dir))
        {
            paths.sorted(Comparator.reverseOrder()).forEach(path ->
            {
                try
                {
                    Files.delete(path);
                }
                catch(final IOException e)
                {
                    throw new UncheckedIOException(e);
                }
            });
        }
        catch(final IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Populates a new stored GigaMap with {@link #COUNT} documents (null embeddings and deletion holes included)
     * and returns the expected live vectors.
     */
    private static Map<Long, float[]> populateAndStore(
        final Path storageDir,
        final Mode mode      ,
        final Path indexDir
    )
    {
        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(GigaMap.New(), storageDir))
        {
            final GigaMap<Doc>     map     = rootOf(storage);
            final VectorIndex<Doc> index   = register(map, mode, indexDir);
            final Map<Long, float[]> vectors = addDocs(map, new Random(42), COUNT, "doc_");
            removeEveryFifth(map, vectors, 0, map.highestUsedId());
            if(indexDir != null)
            {
                index.persistToDisk();
            }
            storage.storeRoot();
            return vectors;
        }
    }

    // ==================== ensureGraphRebuilt (load path) ====================

    @ParameterizedTest
    @EnumSource(Mode.class)
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void ensureGraphRebuilt_inMemoryRestartWithHoles_matchesBruteForce(
        final Mode mode,
        @TempDir final Path tempDir
    )
    {
        final Path storageDir = tempDir.resolve("storage");
        final Map<Long, float[]> vectors = populateAndStore(storageDir, mode, null);

        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final GigaMap<Doc>     map   = rootOf(storage);
            final VectorIndex<Doc> index = lookup(map);

            // The first search triggers the deferred rebuild.
            assertRecall(index, vectors);
            assertNoRebuildWorkerVectorized();
        }
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void ensureGraphRebuilt_onDiskRestartWithoutIndexFiles_matchesBruteForce(
        final Mode mode,
        @TempDir final Path tempDir
    )
    {
        final Path storageDir = tempDir.resolve("storage");
        final Path indexDir   = tempDir.resolve("index");
        final Map<Long, float[]> vectors = populateAndStore(storageDir, mode, indexDir);

        // Without its files the on-disk index has no segment to load, so it rebuilds from the store on first
        // access, like an in-memory one.
        deleteDirectory(indexDir);

        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final GigaMap<Doc>     map   = rootOf(storage);
            final VectorIndex<Doc> index = lookup(map);

            assertRecall(index, vectors);
            assertNoRebuildWorkerVectorized();

            // The rebuilt graph is also what the next persist writes.
            index.persistToDisk();
            assertRecall(index, vectors);
        }
    }

    // ==================== persist path ====================

    @ParameterizedTest
    @EnumSource(Mode.class)
    @Timeout(value = 180, unit = TimeUnit.SECONDS)
    void persist_afterReloadAndMutations_flushesWithoutARebuild_matchesBruteForce(
        final Mode mode,
        @TempDir final Path tempDir
    )
    {
        final Path storageDir = tempDir.resolve("storage");
        final Path indexDir   = tempDir.resolve("index");
        final Map<Long, float[]> vectors = populateAndStore(storageDir, mode, indexDir);

        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final GigaMap<Doc>     map   = rootOf(storage);
            final VectorIndex<Doc> index = lookup(map);

            // Mutate on top of the loaded segment, then persist, which flushes the in-memory graph as a second
            // segment and rebuilds nothing.
            final long before = map.highestUsedId();
            vectors.putAll(addDocs(map, new Random(43), 400, "added_"));
            removeEveryFifth(map, vectors, before + 1, map.highestUsedId());
            map.removeById(2);
            vectors.remove(2L);

            index.persistToDisk();

            assertRecall(index, vectors);
            assertEquals(2, ((VectorIndex.Default<Doc>)index).sourceSet().segments().size());
            assertNoRebuildWorkerVectorized();
            storage.storeRoot();
        }

        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            assertRecall(lookup(rootOf(storage)), vectors);
        }
    }

    // ==================== live vectors after the rebuild ====================

    @ParameterizedTest
    @EnumSource(Mode.class)
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void ensureGraphRebuilt_thenAddAndUpdate_scoresLiveVectors(
        final Mode mode,
        @TempDir final Path tempDir
    )
    {
        final Path storageDir = tempDir.resolve("storage");
        final Map<Long, float[]> vectors = populateAndStore(storageDir, mode, null);

        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final GigaMap<Doc>     map   = rootOf(storage);
            final VectorIndex<Doc> index = lookup(map);
            assertRecall(index, vectors);

            // Ordinals beyond the rebuilt ones and a changed vector: inserting them scores correctly only if the
            // builder reads the current vectors.
            vectors.putAll(addDocs(map, new Random(44), 300, "late_"));
            final long    updatedId = vectors.keySet().iterator().next();
            final float[] updated   = randomVector(new Random(45));
            map.set(updatedId, new Doc("updated", updated));
            vectors.put(updatedId, updated);

            assertRecall(index, vectors);
            if(mode == Mode.COMPUTED)
            {
                // The computed update replaces the node, so it is linked by its new vector. The embedded vec-to-vec
                // update deliberately leaves the node where it is until the next optimize, so it is not asked here.
                final VectorSearchResult<Doc> top = index.search(updated, 1);
                assertEquals(updatedId, top.iterator().next().entityId());
            }
        }
    }

    // ==================== no deadlock in embedded mode ====================

    /**
     * The rebuild runs under the GigaMap monitor and waits for its workers; in embedded mode the live view would
     * make every worker wait for that same monitor. Runs the load-path rebuild on a separate daemon thread, so a
     * regression fails the test instead of hanging the build, while a second thread queues for the monitor.
     */
    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void ensureGraphRebuilt_embeddedUnderMonitorContention_doesNotDeadlock(@TempDir final Path tempDir)
        throws InterruptedException
    {
        assumeParallel();
        final Path storageDir = tempDir.resolve("storage");
        final Map<Long, float[]> vectors = populateAndStore(storageDir, Mode.EMBEDDED, null);

        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final GigaMap<Doc>             map   = rootOf(storage);
            final VectorIndex.Default<Doc> index = (VectorIndex.Default<Doc>)lookup(map);

            // The first worker holds the rebuild until the contender queues for the monitor the rebuild holds, so
            // the workers provably run while it waits.
            final CountDownLatch workerEntered = new CountDownLatch(1);
            final CountDownLatch release       = new CountDownLatch(1);
            final AtomicBoolean  first         = new AtomicBoolean();
            index.rebuildWorkerTestHook = () ->
            {
                if(first.compareAndSet(false, true))
                {
                    workerEntered.countDown();
                    awaitLatch(release);
                }
            };

            final AtomicReference<Throwable> failure = new AtomicReference<>();
            final AtomicReference<Double>    recall  = new AtomicReference<>();
            final Thread searcher = new Thread(() ->
            {
                try
                {
                    recall.set(recall(index, vectors));
                }
                catch(final Throwable t)
                {
                    failure.set(t);
                }
            }, "rebuild-trigger");
            final Thread contender = new Thread(() ->
            {
                try
                {
                    map.get(0);
                }
                catch(final Throwable t)
                {
                    failure.compareAndSet(null, t);
                }
            }, "monitor-contender");
            searcher.setDaemon(true);
            contender.setDaemon(true);

            try
            {
                searcher.start();
                awaitLatch(workerEntered);
                contender.start();
                awaitBlocked(contender);
            }
            finally
            {
                release.countDown();
                index.rebuildWorkerTestHook = null;
            }
            searcher.join(TimeUnit.SECONDS.toMillis(90));
            contender.join(TimeUnit.SECONDS.toMillis(10));

            assertFalse(searcher.isAlive(), "The rebuild did not finish: deadlock");
            assertFalse(contender.isAlive(), "The monitor was never released");
            assertNull(failure.get(), () -> "Failed: " + failure.get());
            assertTrue(recall.get() >= MIN_RECALL, "recall@" + K + " was " + recall.get());
            assertNoRebuildWorkerVectorized();
        }
    }

    // ==================== sparse ordinal space ====================

    /**
     * A churned map: most ids removed, so the highest ordinal is far above the vector count, which the rebuild
     * collects its ordinals by rather than by the highest id.
     */
    @ParameterizedTest
    @EnumSource(Mode.class)
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void ensureGraphRebuilt_sparseOrdinalSpace_matchesBruteForce(
        final Mode mode,
        @TempDir final Path tempDir
    )
    {
        final Path storageDir = tempDir.resolve("storage");
        final Map<Long, float[]> vectors;
        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(GigaMap.New(), storageDir))
        {
            final GigaMap<Doc> map = rootOf(storage);
            register(map, mode, null);
            vectors = addDocs(map, new Random(46), 8_000, "doc_");
            for(long id = 0; id <= map.highestUsedId(); id++)
            {
                if(id % 10 != 0)
                {
                    map.removeById(id);
                    vectors.remove(id);
                }
            }
            storage.storeRoot();
        }
        assertTrue(vectors.size() >= ParallelGraphInsertion.MIN_PARALLEL_NODES, "and the parallel path");

        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final VectorIndex<Doc> index = lookup(rootOf(storage));
            assertRecall(index, vectors);
            assertNoRebuildWorkerVectorized();
        }
    }

    // ==================== close() racing a rebuild ====================

    private static Thread daemon(
        final String                     name   ,
        final Runnable                   action ,
        final AtomicReference<Throwable> failure
    )
    {
        final Thread thread = new Thread(() ->
        {
            try
            {
                action.run();
            }
            catch(final Throwable t)
            {
                failure.compareAndSet(null, t);
            }
        }, name);
        thread.setDaemon(true);
        return thread;
    }

    private static void awaitLatch(final CountDownLatch latch)
    {
        try
        {
            assertTrue(latch.await(30, TimeUnit.SECONDS), "latch not released");
        }
        catch(final InterruptedException e)
        {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static void awaitBlocked(final Thread thread) throws InterruptedException
    {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while(thread.getState() != Thread.State.BLOCKED)
        {
            assertTrue(thread.isAlive(), thread.getName() + " finished instead of waiting for the monitor");
            assertTrue(System.nanoTime() < deadline, thread.getName() + " never blocked on the monitor");
            Thread.sleep(5);
        }
    }

    private static void assertNoRebuildThreadsLeft() throws InterruptedException
    {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while(true)
        {
            final boolean left = Thread.getAllStackTraces().keySet().stream()
                .anyMatch(t -> t.isAlive() && t.getName().startsWith("vector-rebuild-"));
            if(!left)
            {
                return;
            }
            assertTrue(System.nanoTime() < deadline, "rebuild worker threads outlived the rebuild");
            Thread.sleep(10);
        }
    }

    /**
     * The load-path rebuild holds the monitor but not builderLock, so a close() that took only the write lock could
     * close the builder under the rebuild's workers. close() must wait for the monitor and succeed, the rebuild must
     * notice the close and stop, and the search that triggered it must fail with an IllegalStateException instead of
     * answering from the partial graph. No worker thread may be left behind.
     */
    @ParameterizedTest
    @EnumSource(Mode.class)
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void close_duringRebuildWorkers_waitsForRebuildAndLeavesNoThreads(
        final Mode mode,
        @TempDir final Path tempDir
    ) throws InterruptedException
    {
        assumeParallel();
        final Path storageDir = tempDir.resolve("storage");
        populateAndStore(storageDir, mode, null);

        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final VectorIndex.Default<Doc> index = (VectorIndex.Default<Doc>)lookup(rootOf(storage));

            final CountDownLatch workerEntered = new CountDownLatch(1);
            final CountDownLatch release       = new CountDownLatch(1);
            final AtomicBoolean  first         = new AtomicBoolean();
            index.rebuildWorkerTestHook = () ->
            {
                if(first.compareAndSet(false, true))
                {
                    workerEntered.countDown();
                    awaitLatch(release);
                }
            };

            final AtomicReference<Throwable> searchFailure = new AtomicReference<>();
            final AtomicReference<Throwable> failure       = new AtomicReference<>();
            final Thread rebuilder = daemon("rebuild-trigger",
                () -> index.search(randomVector(new Random(1)), K), searchFailure);
            final Thread closer    = daemon("closer", index::close, failure);

            rebuilder.start();
            awaitLatch(workerEntered);
            closer.start();

            // The close waits for the monitor the rebuild holds (it takes the monitor before the write lock): no
            // teardown yet.
            awaitBlocked(closer);
            assertTrue(rebuilder.isAlive());

            release.countDown();
            rebuilder.join(TimeUnit.SECONDS.toMillis(60));
            closer.join(TimeUnit.SECONDS.toMillis(60));

            assertFalse(rebuilder.isAlive(), "rebuild did not finish");
            assertFalse(closer.isAlive(), "close did not finish");
            assertNull(failure.get(), () -> "close failed: " + failure.get());
            assertClosingFailure(searchFailure.get());
            assertNoRebuildThreadsLeft();
        }
    }

    /**
     * The same race before the workers start, on the rebuilding thread once the stored vectors are collected and
     * prepared: the close must wait, and the rebuild must give up without writing a code or a node.
     */
    @ParameterizedTest
    @EnumSource(Mode.class)
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void close_beforeRebuildWorkersStart_abandonsRebuildWithoutInserting(
        final Mode mode,
        @TempDir final Path tempDir
    ) throws InterruptedException
    {
        assumeParallel();
        final Path storageDir = tempDir.resolve("storage");
        populateAndStore(storageDir, mode, null);

        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final VectorIndex.Default<Doc> index = (VectorIndex.Default<Doc>)lookup(rootOf(storage));

            final CountDownLatch beforeInsert = new CountDownLatch(1);
            final CountDownLatch release        = new CountDownLatch(1);
            final AtomicInteger  workerCalls    = new AtomicInteger();
            index.rebuildBeforeInsertTestHook = () ->
            {
                beforeInsert.countDown();
                awaitLatch(release);
            };
            index.rebuildWorkerTestHook = workerCalls::incrementAndGet;

            final AtomicReference<Throwable> rebuildFailure = new AtomicReference<>();
            final AtomicReference<Throwable> failure        = new AtomicReference<>();
            final Thread rebuilder = daemon("rebuild-trigger", index::ensureIndexInitialized, rebuildFailure);
            final Thread closer    = daemon("closer", index::close, failure);

            rebuilder.start();
            awaitLatch(beforeInsert);
            closer.start();
            awaitBlocked(closer);

            release.countDown();
            rebuilder.join(TimeUnit.SECONDS.toMillis(60));
            closer.join(TimeUnit.SECONDS.toMillis(60));

            assertFalse(rebuilder.isAlive(), "rebuild did not finish");
            assertFalse(closer.isAlive(), "close did not finish");
            assertNull(failure.get(), () -> "close failed: " + failure.get());
            assertClosingFailure(rebuildFailure.get());
            assertEquals(0, workerCalls.get(), "the abandoned rebuild must not start inserting");
            assertNoRebuildThreadsLeft();
        }
    }

    // ==================== background indexing callbacks vs. the load-path rebuild ====================

    /**
     * Populates a stored map like {@link #populateAndStore} but with eventual indexing, so that after a restart the
     * index has a background indexing worker whose callbacks take the read lock instead of the monitor.
     */
    private static Map<Long, float[]> populateAndStoreEventual(
        final Path storageDir,
        final Mode mode
    )
    {
        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(GigaMap.New(), storageDir))
        {
            final GigaMap<Doc> map = rootOf(storage);
            map.index().register(VectorIndices.Category()).add(INDEX_NAME, config(null, true), mode.vectorizer());
            final Map<Long, float[]> vectors = addDocs(map, new Random(42), COUNT, "doc_");
            removeEveryFifth(map, vectors, 0, map.highestUsedId());
            storage.storeRoot();
            return vectors;
        }
    }

    private static void drainIndexing(final VectorIndex.Default<Doc> index)
    {
        assertNotNull(index.backgroundTaskManager, "eventual indexing must run a background manager");
        index.backgroundTaskManager.drainQueue();
    }

    /**
     * Background indexing callbacks take the read lock, not the monitor the load-path rebuild holds. One that ran
     * during the rebuild could insert an ordinal the rebuild is inserting concurrently ("Node already exists") or
     * remove a node the rebuild links to. Invoked while a rebuild worker is blocked, an add and a remove for ordinals
     * the rebuild covers must wait for the rebuild, then apply cleanly.
     */
    @ParameterizedTest
    @EnumSource(Mode.class)
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void backgroundCallbacks_duringRebuild_waitForRebuildThenApply(
        final Mode mode,
        @TempDir final Path tempDir
    ) throws InterruptedException
    {
        assumeParallel();
        final Path storageDir = tempDir.resolve("storage");
        final Map<Long, float[]> vectors = populateAndStoreEventual(storageDir, mode);

        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final VectorIndex.Default<Doc> index = (VectorIndex.Default<Doc>)lookup(rootOf(storage));

            final CountDownLatch workerEntered = new CountDownLatch(1);
            final CountDownLatch release       = new CountDownLatch(1);
            final AtomicBoolean  first         = new AtomicBoolean();
            index.rebuildWorkerTestHook = () ->
            {
                if(first.compareAndSet(false, true))
                {
                    workerEntered.countDown();
                    awaitLatch(release);
                }
            };

            final List<Long> ids       = new ArrayList<>(vectors.keySet());
            final long       addedId   = ids.get(ids.size() / 2);
            final long       removedId = ids.get(ids.size() / 3);

            final AtomicReference<Throwable> failure   = new AtomicReference<>();
            final Thread rebuilder = daemon("rebuild-trigger", index::ensureIndexInitialized, failure);
            final Thread adder     = daemon("callback-add",
                () -> index.applyGraphAdd((int)addedId), failure);
            final Thread remover   = daemon("callback-remove",
                () -> index.applyGraphRemove((int)removedId), failure);

            rebuilder.start();
            awaitLatch(workerEntered);
            adder.start();
            remover.start();

            // Both wait for the monitor the rebuild holds instead of touching the builder under it.
            awaitBlocked(adder);
            awaitBlocked(remover);

            release.countDown();
            for(final Thread thread : List.of(rebuilder, adder, remover))
            {
                thread.join(TimeUnit.SECONDS.toMillis(60));
                assertFalse(thread.isAlive(), thread.getName() + " did not finish");
            }
            assertNull(failure.get(), () -> "failed: " + failure.get());

            vectors.remove(removedId);
            assertRecall(index, vectors);
            assertEquals(addedId, index.search(vectors.get(addedId), 1).iterator().next().entityId());
        }
    }

    /**
     * The end-to-end shape of the same question: GigaMap mutations with eventual indexing issued while the load-path
     * rebuild runs. They need the monitor, so they wait for the rebuild, and the callbacks they queue then apply on
     * top of the complete graph.
     */
    @ParameterizedTest
    @EnumSource(Mode.class)
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void eventualMutations_duringRebuild_applyAfterRebuild(
        final Mode mode,
        @TempDir final Path tempDir
    ) throws InterruptedException
    {
        assumeParallel();
        final Path storageDir = tempDir.resolve("storage");
        final Map<Long, float[]> vectors = populateAndStoreEventual(storageDir, mode);

        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final GigaMap<Doc>             map   = rootOf(storage);
            final VectorIndex.Default<Doc> index = (VectorIndex.Default<Doc>)lookup(map);

            final CountDownLatch workerEntered = new CountDownLatch(1);
            final CountDownLatch release       = new CountDownLatch(1);
            final AtomicBoolean  first         = new AtomicBoolean();
            index.rebuildWorkerTestHook = () ->
            {
                if(first.compareAndSet(false, true))
                {
                    workerEntered.countDown();
                    awaitLatch(release);
                }
            };

            final List<Long> ids       = new ArrayList<>(vectors.keySet());
            final long       updatedId = ids.get(ids.size() / 2);
            final long       removedId = ids.get(ids.size() / 3);
            final float[]    updated   = randomVector(new Random(47));
            final float[]    added     = randomVector(new Random(48));
            final long[]     addedId   = new long[1];

            final AtomicReference<Throwable> failure   = new AtomicReference<>();
            final Thread rebuilder = daemon("rebuild-trigger", index::ensureIndexInitialized, failure);
            final Thread mutator   = daemon("mutator", () ->
            {
                addedId[0] = map.add(new Doc("added", added));
                map.set(updatedId, new Doc("updated", updated));
                map.removeById(removedId);
            }, failure);

            rebuilder.start();
            awaitLatch(workerEntered);
            mutator.start();
            awaitBlocked(mutator);

            release.countDown();
            rebuilder.join(TimeUnit.SECONDS.toMillis(60));
            mutator.join(TimeUnit.SECONDS.toMillis(60));
            assertFalse(rebuilder.isAlive(), "rebuild did not finish");
            assertFalse(mutator.isAlive(), "mutations did not finish");
            assertNull(failure.get(), () -> "failed: " + failure.get());
            drainIndexing(index);

            vectors.put(addedId[0], added);
            vectors.put(updatedId, updated);
            vectors.remove(removedId);
            assertRecall(index, vectors);
            assertEquals(addedId[0], index.search(added, 1).iterator().next().entityId());
            if(mode == Mode.COMPUTED)
            {
                // As above: the embedded vec-to-vec update keeps the node where it is until the next optimize.
                assertEquals(updatedId, index.search(updated, 1).iterator().next().entityId());
            }
        }
    }

    // ==================== review follow-ups ====================

    /**
     * The tests whose point is the parallel path are skipped, not passed vacuously, on a machine where the rebuild
     * runs on the calling thread.
     */
    private static void assumeParallel()
    {
        assumeTrue(ParallelGraphInsertion.parallelism() > 1, "needs more than one core for the parallel path");
    }

    private static void assertClosingFailure(final Throwable failure)
    {
        assertNotNull(failure, "an access whose rebuild a close abandoned must fail, not use the partial graph");
        assertInstanceOf(RebuildAbandonedException.class, failure, () -> "unexpected failure: " + failure);
        assertTrue(failure.getMessage().contains("closing"), failure.getMessage());
    }

    /**
     * A close that fails after it has cut a rebuild short must not leave the index stuck: closeRequested is cleared
     * again, so the next search redoes the rebuild and answers from the complete graph.
     */
    @ParameterizedTest
    @EnumSource(Mode.class)
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void close_failingAfterAbandoningRebuild_laterSearchRebuildsFully(
        final Mode mode,
        @TempDir final Path tempDir
    ) throws InterruptedException
    {
        assumeParallel();
        final Path storageDir = tempDir.resolve("storage");
        final Map<Long, float[]> vectors = populateAndStore(storageDir, mode, null);

        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final VectorIndex.Default<Doc> index = (VectorIndex.Default<Doc>)lookup(rootOf(storage));

            // Held on the 300th insert rather than the first, so the graph is partly filled when the close
            // (which stops the other workers at once) abandons it.
            final CountDownLatch                     workerEntered = new CountDownLatch(1);
            final CountDownLatch                     release       = new CountDownLatch(1);
            final AtomicInteger                      calls         = new AtomicInteger();
            final AtomicReference<GraphIndexBuilder> abandoned     = new AtomicReference<>();
            index.rebuildWorkerTestHook = () ->
            {
                if(calls.incrementAndGet() == 300)
                {
                    abandoned.set(index.currentBuilder());
                    workerEntered.countDown();
                    awaitLatch(release);
                }
            };
            index.closeTeardownTestHook = () ->
            {
                throw new IllegalStateException("injected close failure");
            };

            final AtomicReference<Throwable> searchFailure = new AtomicReference<>();
            final AtomicReference<Throwable> closeFailure  = new AtomicReference<>();
            final Thread searcher = daemon("rebuild-trigger",
                () -> index.search(randomVector(new Random(1)), K), searchFailure);
            final Thread closer   = daemon("closer", index::close, closeFailure);

            searcher.start();
            awaitLatch(workerEntered);
            closer.start();
            awaitBlocked(closer);

            release.countDown();
            searcher.join(TimeUnit.SECONDS.toMillis(60));
            closer.join(TimeUnit.SECONDS.toMillis(60));
            assertFalse(searcher.isAlive(), "rebuild did not finish");
            assertFalse(closer.isAlive(), "close did not finish");
            assertClosingFailure(searchFailure.get());
            assertNotNull(closeFailure.get(), "the injected close failure must surface");
            assertEquals("injected close failure", closeFailure.get().getMessage());

            // The other workers kept inserting while one was held, but the partial graph they built is gone:
            // an empty builder stands in its place, so the retry cannot build on nodes linked against it.
            assertTrue(abandoned.get().getGraph().size(0) > 0, "the fixture must abandon a partly filled graph");
            assertNotSame(abandoned.get(), index.currentBuilder(), "the partial graph must be discarded");
            assertEquals(0, index.inMemoryGraphNodeCount());

            // The close failed, so the index is still open: the next search rebuilds from scratch and is complete.
            index.closeTeardownTestHook = null;
            index.rebuildWorkerTestHook = null;
            assertRecall(index, vectors);
            assertEquals(vectors.size(), index.inMemoryGraphNodeCount());
        }
    }

    /**
     * In-memory PQ codes are transient, so a restored PQ_IN_MEMORY index rebuilds exactly, from the vector file,
     * and the next optimize switches it to compressed scoring again. That switch replays every node into a
     * replacement builder that scores from the codes, through the same parallel loop: concurrent
     * encodeAndSet, code-based scoring and the pre-sized code store. Recall against brute force must hold
     * after both steps.
     */
    @ParameterizedTest
    @EnumSource(Mode.class)
    @Timeout(value = 300, unit = TimeUnit.SECONDS)
    void ensureGraphRebuilt_restoredInMemoryPqIndex_rebuildsAndSwitchesWithRecall(
        final Mode mode,
        @TempDir final Path tempDir
    )
    {
        final Path storageDir = tempDir.resolve("storage");
        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(DIM)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .maxDegree(16)
            .beamWidth(100)
            .approximateScoring(ApproximateScoring.PQ_IN_MEMORY)
            .pqSubspaces(DIM / 4)
            // The switch happens at optimization; the tests call optimize() directly.
            .optimizationIntervalMs(3_600_000)
            .build();

        final Map<Long, float[]> vectors;
        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(GigaMap.New(), storageDir))
        {
            final GigaMap<Doc>     map   = rootOf(storage);
            final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
                .add(INDEX_NAME, config, mode.vectorizer());
            vectors = addDocs(map, new Random(49), COUNT, "doc_");
            removeEveryFifth(map, vectors, 0, map.highestUsedId());
            index.optimize();
            assertTrue(index.isPqCompressionActive(), "the fixture must have switched before the restart");
            storage.storeRoot();
        }

        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final VectorIndex.Default<Doc> index = (VectorIndex.Default<Doc>)lookup(rootOf(storage));

            // Rebuilt exactly, from the vector file: the codes did not survive the restart.
            assertRecall(index, vectors);
            assertFalse(index.isPqCompressionActive());

            // The switch replays every node in parallel against the codes.
            index.optimize();
            assertTrue(index.isPqCompressionActive());
            assertRecall(index, vectors);
            assertNoRebuildWorkerVectorized();
        }
    }

    /**
     * The first scheduled optimization of a restored PQ_IN_MEMORY index runs the load-path rebuild on the background
     * executor. A close arriving meanwhile used to shut the background manager down first, and that shutdown waited
     * for the task doing the rebuild (up to shutdownPersistTimeoutMillis plus the executor grace) before the close
     * could ask the rebuild to stop. Without optimize or persist on shutdown nothing needs that graph, so the close
     * now asks first: the rebuild gives up, the shutdown finishes promptly, and the next load rebuilds normally.
     */
    @ParameterizedTest
    @EnumSource(Mode.class)
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void close_whileBackgroundOptimizationRebuilds_doesNotWaitForTheRebuild(
        final Mode mode,
        @TempDir final Path tempDir
    ) throws InterruptedException
    {
        final Path storageDir = tempDir.resolve("storage");
        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(DIM)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .approximateScoring(ApproximateScoring.PQ_IN_MEMORY)
            .pqSubspaces(DIM / 4)
            // Its first tick after the restart is the unearned optimization a restored PQ index asks for.
            .optimizationIntervalMs(1_000)
            // Long, so that a close waiting for the shutdown work would be unmistakable.
            .shutdownPersistTimeoutMillis(60_000)
            .build();
        assertFalse(config.optimizeOnShutdown());

        final Map<Long, float[]> vectors;
        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(GigaMap.New(), storageDir))
        {
            final GigaMap<Doc> map = rootOf(storage);
            map.index().register(VectorIndices.Category()).add(INDEX_NAME, config, mode.vectorizer());
            vectors = addDocs(map, new Random(50), COUNT, "doc_");
            removeEveryFifth(map, vectors, 0, map.highestUsedId());
            storage.storeRoot();
        }

        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final VectorIndex.Default<Doc> index = (VectorIndex.Default<Doc>)lookup(rootOf(storage));

            // Holds the rebuild until the close has been requested, or for 20 s at most, which is how a
            // close that only asks after the shutdown would show: it would take at least that long.
            final CountDownLatch rebuildStarted = new CountDownLatch(1);
            final long           holdUntil      = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            index.rebuildWorkerTestHook = () ->
            {
                rebuildStarted.countDown();
                while(!index.isCloseRequested() && System.nanoTime() < holdUntil)
                {
                    Thread.onSpinWait();
                }
            };

            awaitLatch(rebuildStarted);

            final AtomicReference<Throwable> closeFailure = new AtomicReference<>();
            final Thread closer = daemon("closer", index::close, closeFailure);
            final long   start  = System.nanoTime();
            closer.start();
            closer.join(TimeUnit.SECONDS.toMillis(60));
            final long tookMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

            assertFalse(closer.isAlive(), "close did not finish");
            assertNull(closeFailure.get(), () -> "close failed: " + closeFailure.get());
            assertTrue(tookMillis < 10_000, "close waited " + tookMillis + " ms for the background rebuild");
            assertNoRebuildThreadsLeft();
        }

        // Nothing of the abandoned rebuild outlived the close: the next load rebuilds the complete graph.
        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            assertRecall(lookup(rootOf(storage)), vectors);
        }
    }

    /**
     * A worker that fails inside the rebuild (here an injected failure part-way through) must not leave its partial
     * graph for the retry either: the failure propagates, the builder is replaced by an empty one, and the next access
     * rebuilds every node from scratch with full recall.
     */
    @ParameterizedTest
    @EnumSource(Mode.class)
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void ensureGraphRebuilt_workerFailure_discardsPartialGraphAndRetriesFromScratch(
        final Mode mode,
        @TempDir final Path tempDir
    )
    {
        final Path storageDir = tempDir.resolve("storage");
        final Map<Long, float[]> vectors = populateAndStore(storageDir, mode, null);

        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final VectorIndex.Default<Doc> index = (VectorIndex.Default<Doc>)lookup(rootOf(storage));

            final AtomicInteger                      calls  = new AtomicInteger();
            final AtomicReference<GraphIndexBuilder> failed = new AtomicReference<>();
            index.rebuildWorkerTestHook = () ->
            {
                if(calls.incrementAndGet() == vectors.size() / 2)
                {
                    failed.set(index.currentBuilder());
                    throw new IllegalStateException("injected worker failure");
                }
            };

            final Throwable failure = assertThrows(RuntimeException.class, index::ensureIndexInitialized);
            assertFalse(failure instanceof RebuildAbandonedException, "a real failure, not a close");
            assertTrue(hasMessageInChain(failure, "injected worker failure"), () -> "unexpected: " + failure);

            assertNotNull(failed.get());
            assertTrue(failed.get().getGraph().size(0) > 0, "the fixture must fail a partly filled graph");
            assertNotSame(failed.get(), index.currentBuilder(), "the partial graph must be discarded");
            assertEquals(0, index.inMemoryGraphNodeCount());

            index.rebuildWorkerTestHook = null;
            assertRecall(index, vectors);
            assertEquals(vectors.size(), index.inMemoryGraphNodeCount());
        }
    }

    private static boolean hasMessageInChain(final Throwable failure, final String message)
    {
        for(Throwable current = failure; current != null; current = current.getCause())
        {
            if(message.equals(current.getMessage()))
            {
                return true;
            }
            if(current.getCause() == current)
            {
                return false;
            }
        }
        return false;
    }

    /**
     * A worker failure in the rebuild of a consolidation fails the persist, but publishes nothing: the index
     * keeps answering from the segment and the live graph it had, and the next persist consolidates again
     * and succeeds.
     */
    @ParameterizedTest
    @EnumSource(Mode.class)
    @Timeout(value = 180, unit = TimeUnit.SECONDS)
    void consolidation_workerFailure_keepsTheSourcesAndRetries(
        final Mode mode,
        @TempDir final Path tempDir
    )
    {
        final Path storageDir = tempDir.resolve("storage");
        final Path indexDir   = tempDir.resolve("index");
        final Map<Long, float[]> vectors = populateAndStore(storageDir, mode, indexDir);

        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final GigaMap<Doc>             map   = rootOf(storage);
            final VectorIndex.Default<Doc> index = (VectorIndex.Default<Doc>)lookup(map);
            final Map<Long, float[]> added = addDocs(map, new Random(51), 100, "added_");
            vectors.putAll(added);

            // As after a failed graph operation, which only a rebuild from the store repairs.
            index.graphOperationFailed(new IllegalStateException("simulated graph operation failure"));
            final AtomicInteger calls = new AtomicInteger();
            index.rebuildWorkerTestHook = () ->
            {
                if(calls.incrementAndGet() == vectors.size() / 2)
                {
                    throw new IllegalStateException("injected worker failure");
                }
            };
            final Throwable failure = assertThrows(RuntimeException.class, index::persistToDisk);
            assertTrue(hasMessageInChain(failure, "injected worker failure"), () -> "unexpected: " + failure);
            index.rebuildWorkerTestHook = null;
            assertEquals(0, index.consolidationCount(), "the failed consolidation must publish nothing");
            assertEquals(1, index.sourceSet().segments().size(), "the loaded segment must still be there");
            assertEquals(added.size(), index.inMemoryGraphNodeCount(), "the live graph must be untouched");
            assertRecall(index, vectors);

            // The next persist consolidates again, into one segment holding everything.
            index.persistToDisk();
            assertEquals(1, index.consolidationCount());
            assertEquals(1, index.sourceSet().segments().size());
            assertEquals(0, index.inMemoryGraphNodeCount());
            assertRecall(index, vectors);
            storage.storeRoot();
        }

        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            assertRecall(lookup(rootOf(storage)), vectors);
        }
    }

    /**
     * Collecting the stored vectors (vectorizing every entity in embedded mode) is the expensive part of a load-path
     * rebuild and runs under the monitor a close waits for. A close requested meanwhile must stop it promptly rather
     * than after the whole store, and the partial collection must not count as a rebuild.
     */
    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void close_duringCollection_stopsCollectingPromptly(@TempDir final Path tempDir) throws InterruptedException
    {
        final Path storageDir = tempDir.resolve("storage");
        populateAndStore(storageDir, Mode.EMBEDDED, null);

        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final VectorIndex.Default<Doc> index = (VectorIndex.Default<Doc>)lookup(rootOf(storage));

            final CountDownLatch collecting  = new CountDownLatch(1);
            final CountDownLatch release     = new CountDownLatch(1);
            final AtomicInteger  vectorized  = new AtomicInteger();
            final AtomicInteger  workerCalls = new AtomicInteger();
            final int            holdAt      = 100;
            vectorizeHook = () ->
            {
                if(vectorized.incrementAndGet() == holdAt)
                {
                    collecting.countDown();
                    awaitLatch(release);
                }
            };
            index.rebuildWorkerTestHook = workerCalls::incrementAndGet;

            final AtomicReference<Throwable> rebuildFailure = new AtomicReference<>();
            final AtomicReference<Throwable> closeFailure   = new AtomicReference<>();
            final Thread rebuilder = daemon("rebuild-trigger", index::ensureIndexInitialized, rebuildFailure);
            final Thread closer    = daemon("closer", index::close, closeFailure);

            rebuilder.start();
            awaitLatch(collecting);
            closer.start();
            awaitBlocked(closer);

            release.countDown();
            rebuilder.join(TimeUnit.SECONDS.toMillis(60));
            closer.join(TimeUnit.SECONDS.toMillis(60));
            vectorizeHook = null;

            assertFalse(rebuilder.isAlive(), "rebuild did not finish");
            assertFalse(closer.isAlive(), "close did not finish");
            assertNull(closeFailure.get(), () -> "close failed: " + closeFailure.get());
            assertClosingFailure(rebuildFailure.get());
            assertTrue(vectorized.get() <= holdAt + 1,
                "collection kept vectorizing after the close: " + vectorized.get() + " vectorizer calls");
            assertEquals(0, workerCalls.get(), "the abandoned rebuild must not start inserting");
        }
    }

    /**
     * Runs a PQ switch on a fresh in-memory index with {@link ParallelGraphInsertion#parallelismLimitForTest} set to
     * {@code parallelismLimit}, checks that the replay has encoded every live ordinal before its first insert, and
     * returns the recall afterwards.
     */
    private static double pqSwitchRecall(
        final Mode mode            ,
        final int  parallelismLimit
    )
    {
        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(DIM)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .maxDegree(16)
            .beamWidth(100)
            .approximateScoring(ApproximateScoring.PQ_IN_MEMORY)
            .pqSubspaces(DIM / 4)
            .optimizationIntervalMs(3_600_000)
            .build();

        ParallelGraphInsertion.parallelismLimitForTest = parallelismLimit;
        final GigaMap<Doc> map = GigaMap.New();
        try(final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
            .add(INDEX_NAME, config, mode.vectorizer()))
        {
            final Map<Long, float[]> vectors = addDocs(map, new Random(54), COUNT, "doc_");
            removeEveryFifth(map, vectors, 0, map.highestUsedId());

            final AtomicInteger hookCalls = new AtomicInteger();
            ((VectorIndex.Default<Doc>)index).rebuildCodesEncodedTestHook = codes ->
            {
                hookCalls.incrementAndGet();
                // Every live ordinal has its code before any node is inserted. A real code being all zero
                // bytes would need centroid 0 in every one of the subspaces, which these vectors never hit.
                for(final long id : vectors.keySet())
                {
                    final int ordinal = (int)id;
                    assertTrue(ordinal < codes.count(), "no code slot for ordinal " + ordinal);
                    final var code = codes.get(ordinal);
                    boolean set = false;
                    for(int i = 0; i < code.length() && !set; i++)
                    {
                        set = code.get(i) != 0;
                    }
                    assertTrue(set, "ordinal " + ordinal + " has no code before the inserts start");
                }
            };

            index.optimize();
            assertTrue(index.isPqCompressionActive());
            assertEquals(1, hookCalls.get(), "the replay must encode in a phase of its own");
            assertEquals(vectors.size(), ((VectorIndex.Default<Doc>)index).inMemoryGraphNodeCount());
            return recall(index, vectors);
        }
        finally
        {
            ParallelGraphInsertion.parallelismLimitForTest = 0;
        }
    }

    /**
     * The PQ switch replay encodes every code in a phase of its own before it inserts any node, so no concurrent
     * insert can score a candidate by an unwritten code; its graph must be as good as the one a sequential
     * encode-then-insert replay builds from the same data.
     */
    @ParameterizedTest
    @EnumSource(Mode.class)
    @Timeout(value = 300, unit = TimeUnit.SECONDS)
    void pqSwitchReplay_encodesAllCodesFirst_matchesSequentialRecall(final Mode mode)
    {
        assumeParallel();
        final double sequential = pqSwitchRecall(mode, 1);
        final double parallel   = pqSwitchRecall(mode, 0);

        assertTrue(sequential >= MIN_RECALL, "sequential replay recall@" + K + " was " + sequential);
        assertTrue(parallel >= MIN_RECALL, "parallel replay recall@" + K + " was " + parallel);
        assertTrue(parallel >= sequential - 0.05,
            "parallel replay recall@" + K + " " + parallel + " fell behind the sequential " + sequential);
    }

    private static VectorIndexConfiguration inMemoryPqConfig()
    {
        return VectorIndexConfiguration.builder()
            .dimension(DIM)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .maxDegree(16)
            .beamWidth(100)
            .approximateScoring(ApproximateScoring.PQ_IN_MEMORY)
            .pqSubspaces(DIM / 4)
            .optimizationIntervalMs(3_600_000)
            .build();
    }

    /**
     * A PQ switch whose replay fails before publishing must close its replacement builder (releasing the per-thread
     * scratch its workers left), keep the index on its old graph, and never close the builder it later publishes.
     */
    @ParameterizedTest
    @EnumSource(Mode.class)
    @Timeout(value = 300, unit = TimeUnit.SECONDS)
    void pqSwitch_failingBeforePublish_closesItsReplacement(final Mode mode)
    {
        final GigaMap<Doc> map = GigaMap.New();
        try(final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
            .add(INDEX_NAME, inMemoryPqConfig(), mode.vectorizer()))
        {
            final VectorIndex.Default<Doc> def     = (VectorIndex.Default<Doc>)index;
            final Map<Long, float[]>       vectors = addDocs(map, new Random(55), COUNT, "doc_");
            removeEveryFifth(map, vectors, 0, map.highestUsedId());
            assertRecall(index, vectors);

            final List<GraphIndexBuilder> closed   = new ArrayList<>();
            final GraphIndexBuilder       original = def.currentBuilder();
            def.unpublishedBuilderClosedTestHook = closed::add;
            def.pqSwitchPublishTestHook = () ->
            {
                throw new IllegalStateException("injected replay failure");
            };

            final Throwable failure = assertThrows(RuntimeException.class, index::optimize);
            assertTrue(hasMessageInChain(failure, "injected replay failure"), () -> "unexpected: " + failure);
            assertEquals(1, closed.size(), "the unpublished replacement must be closed");
            assertNotSame(original, closed.get(0));
            assertSame(original, def.currentBuilder(), "the index keeps its old graph");
            assertFalse(index.isPqCompressionActive());
            assertRecall(index, vectors);

            // A later switch publishes its replacement, which is then the index's builder and stays open.
            def.pqSwitchPublishTestHook = null;
            index.optimize();
            assertTrue(index.isPqCompressionActive());
            assertEquals(1, closed.size(), "a published replacement must not be closed");
            assertNotSame(original, def.currentBuilder());
            assertRecall(index, vectors);
        }
    }

    /**
     * A PQ switch given up because a close was requested between its phases closes its replacement as well, and the
     * close then completes.
     */
    @ParameterizedTest
    @EnumSource(Mode.class)
    @Timeout(value = 300, unit = TimeUnit.SECONDS)
    void pqSwitch_abandonedForClose_closesItsReplacement(final Mode mode) throws InterruptedException
    {
        final GigaMap<Doc> map = GigaMap.New();
        final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
            .add(INDEX_NAME, inMemoryPqConfig(), mode.vectorizer());
        final VectorIndex.Default<Doc> def = (VectorIndex.Default<Doc>)index;
        final Map<Long, float[]> vectors = addDocs(map, new Random(56), COUNT, "doc_");
        removeEveryFifth(map, vectors, 0, map.highestUsedId());
        assertRecall(index, vectors);

        final List<GraphIndexBuilder> closed     = new ArrayList<>();
        final GraphIndexBuilder       original   = def.currentBuilder();
        final CountDownLatch          encoded    = new CountDownLatch(1);
        final long                    holdUntil  = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        def.unpublishedBuilderClosedTestHook = builder ->
        {
            synchronized(closed)
            {
                closed.add(builder);
            }
        };
        def.rebuildCodesEncodedTestHook = codes ->
        {
            encoded.countDown();
            while(!def.isCloseRequested() && System.nanoTime() < holdUntil)
            {
                Thread.onSpinWait();
            }
        };

        final AtomicReference<Throwable> optimizeFailure = new AtomicReference<>();
        final AtomicReference<Throwable> closeFailure    = new AtomicReference<>();
        final Thread optimizer = daemon("optimizer", index::optimize, optimizeFailure);
        final Thread closer    = daemon("closer", index::close, closeFailure);

        optimizer.start();
        awaitLatch(encoded);
        closer.start();
        optimizer.join(TimeUnit.SECONDS.toMillis(60));
        closer.join(TimeUnit.SECONDS.toMillis(60));

        assertFalse(optimizer.isAlive(), "optimize did not finish");
        assertFalse(closer.isAlive(), "close did not finish");
        assertNull(optimizeFailure.get(), () -> "optimize failed: " + optimizeFailure.get());
        assertNull(closeFailure.get(), () -> "close failed: " + closeFailure.get());
        assertFalse(index.isPqCompressionActive(), "the abandoned switch must not have been published");
        synchronized(closed)
        {
            assertEquals(1, closed.size(), "the abandoned replacement must be closed");
            assertNotSame(original, closed.get(0));
        }
        assertNoRebuildThreadsLeft();
    }

}
