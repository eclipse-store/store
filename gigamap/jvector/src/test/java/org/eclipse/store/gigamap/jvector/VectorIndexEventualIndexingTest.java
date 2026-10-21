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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for eventual indexing mode in VectorIndex.
 * <p>
 * Eventual indexing defers HNSW graph mutations to a background thread
 * while keeping vector store updates immediate. This trades immediate
 * search consistency for reduced mutation latency.
 */
class VectorIndexEventualIndexingTest
{
    record Document(String content, float[] embedding) {}

    static class ComputedDocumentVectorizer extends Vectorizer<Document>
    {
        @Override
        public float[] vectorize(final Document entity)
        {
            return entity.embedding();
        }
    }

    static class EmbeddedDocumentVectorizer extends Vectorizer<Document>
    {
        @Override
        public float[] vectorize(final Document entity)
        {
            return entity.embedding();
        }

        @Override
        public boolean isEmbedded()
        {
            return true;
        }
    }

    private static float[] randomVector(final Random random, final int dimension)
    {
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

    // ==================== Basic Add / Search Tests ====================

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testAddAndSearchWithEventualIndexing()
    {
        final GigaMap<Document> gigaMap = GigaMap.New();
        final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());

        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(3)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .eventualIndexing(true)
            .build();

        try (final VectorIndex<Document> index = vectorIndices.add(
            "embeddings", config, new EmbeddedDocumentVectorizer()
        ))
        {
            gigaMap.add(new Document("doc1", new float[]{1.0f, 0.0f, 0.0f}));
            gigaMap.add(new Document("doc2", new float[]{0.0f, 1.0f, 0.0f}));
            gigaMap.add(new Document("doc3", new float[]{0.0f, 0.0f, 1.0f}));

            // Drain queue to ensure all graph operations are applied
            final VectorIndex.Default<Document> defaultIndex = (VectorIndex.Default<Document>) index;
            defaultIndex.backgroundTaskManager.drainQueue();

            // Search should find all 3 documents
            final VectorSearchResult<Document> result = index.search(new float[]{1.0f, 0.0f, 0.0f}, 3);
            assertEquals(3, result.size());

            // The closest match should be doc1
            assertEquals("doc1", result.toList().get(0).entity().content());
        }
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testAddAndSearchWithComputedVectorizer()
    {
        final GigaMap<Document> gigaMap = GigaMap.New();
        final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());

        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(3)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .eventualIndexing(true)
            .build();

        try (final VectorIndex<Document> index = vectorIndices.add(
            "embeddings", config, new ComputedDocumentVectorizer()
        ))
        {
            gigaMap.add(new Document("doc1", new float[]{1.0f, 0.0f, 0.0f}));
            gigaMap.add(new Document("doc2", new float[]{0.0f, 1.0f, 0.0f}));
            gigaMap.add(new Document("doc3", new float[]{0.0f, 0.0f, 1.0f}));

            final VectorIndex.Default<Document> defaultIndex = (VectorIndex.Default<Document>) index;
            defaultIndex.backgroundTaskManager.drainQueue();

            final VectorSearchResult<Document> result = index.search(new float[]{1.0f, 0.0f, 0.0f}, 3);
            assertEquals(3, result.size());
            assertEquals("doc1", result.toList().get(0).entity().content());
        }
    }

    // ==================== Bulk Add Tests ====================

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testBulkAddWithEventualIndexing()
    {
        final int dimension = 64;
        final int vectorCount = 100;
        final Random random = new Random(42);

        final GigaMap<Document> gigaMap = GigaMap.New();
        final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());

        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(dimension)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .eventualIndexing(true)
            .build();

        try (final VectorIndex<Document> index = vectorIndices.add(
            "embeddings", config, new ComputedDocumentVectorizer()
        ))
        {
            for (int i = 0; i < vectorCount; i++)
            {
                gigaMap.add(new Document("doc_" + i, randomVector(random, dimension)));
            }

            final VectorIndex.Default<Document> defaultIndex = (VectorIndex.Default<Document>) index;
            defaultIndex.backgroundTaskManager.drainQueue();

            final VectorSearchResult<Document> result = index.search(
                randomVector(new Random(99), dimension), 10
            );
            assertEquals(10, result.size());
        }
    }

    // ==================== Update Tests ====================

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testUpdateWithEventualIndexing()
    {
        final GigaMap<Document> gigaMap = GigaMap.New();
        final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());

        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(3)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .eventualIndexing(true)
            .build();

        try (final VectorIndex<Document> index = vectorIndices.add(
            "embeddings", config, new EmbeddedDocumentVectorizer()
        ))
        {
            final Document doc1 = new Document("doc1", new float[]{1.0f, 0.0f, 0.0f});
            final Document doc2 = new Document("doc2", new float[]{0.0f, 1.0f, 0.0f});
            gigaMap.add(doc1);
            gigaMap.add(doc2);

            final VectorIndex.Default<Document> defaultIndex = (VectorIndex.Default<Document>) index;
            defaultIndex.backgroundTaskManager.drainQueue();

            // Update doc1's vector to be close to doc2
            final Document updatedDoc1 = new Document("doc1_updated", new float[]{0.1f, 0.9f, 0.0f});
            gigaMap.set(0L, updatedDoc1);

            defaultIndex.backgroundTaskManager.drainQueue();

            // Search for doc2-like vector: updated doc1 should now be close
            final VectorSearchResult<Document> result = index.search(new float[]{0.0f, 1.0f, 0.0f}, 2);
            assertEquals(2, result.size());
        }
    }

    // ==================== Remove Tests ====================

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testRemoveWithEventualIndexing()
    {
        final GigaMap<Document> gigaMap = GigaMap.New();
        final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());

        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(3)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .eventualIndexing(true)
            .build();

        try (final VectorIndex<Document> index = vectorIndices.add(
            "embeddings", config, new EmbeddedDocumentVectorizer()
        ))
        {
            gigaMap.add(new Document("doc1", new float[]{1.0f, 0.0f, 0.0f}));
            gigaMap.add(new Document("doc2", new float[]{0.0f, 1.0f, 0.0f}));
            gigaMap.add(new Document("doc3", new float[]{0.0f, 0.0f, 1.0f}));

            final VectorIndex.Default<Document> defaultIndex = (VectorIndex.Default<Document>) index;
            defaultIndex.backgroundTaskManager.drainQueue();

            // Remove doc1
            gigaMap.removeById(0L);

            defaultIndex.backgroundTaskManager.drainQueue();

            // Search should only return 2 documents
            final VectorSearchResult<Document> result = index.search(new float[]{1.0f, 0.0f, 0.0f}, 3);
            assertEquals(2, result.size());
        }
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testRemoveMultipleWithEventualIndexing()
    {
        final int dimension = 64;
        final int vectorCount = 50;
        final Random random = new Random(42);

        final GigaMap<Document> gigaMap = GigaMap.New();
        final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());

        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(dimension)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .eventualIndexing(true)
            .build();

        try (final VectorIndex<Document> index = vectorIndices.add(
            "embeddings", config, new ComputedDocumentVectorizer()
        ))
        {
            for (int i = 0; i < vectorCount; i++)
            {
                gigaMap.add(new Document("doc_" + i, randomVector(random, dimension)));
            }

            final VectorIndex.Default<Document> defaultIndex = (VectorIndex.Default<Document>) index;
            defaultIndex.backgroundTaskManager.drainQueue();

            // Remove first 10 entities
            for (int i = 0; i < 10; i++)
            {
                gigaMap.removeById(i);
            }

            defaultIndex.backgroundTaskManager.drainQueue();

            // Should have 40 remaining
            final VectorSearchResult<Document> result = index.search(
                randomVector(new Random(99), dimension), 50
            );
            assertEquals(40, result.size());
        }
    }

    // ==================== RemoveAll Tests ====================

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testRemoveAllDiscardsQueueAndResets()
    {
        final GigaMap<Document> gigaMap = GigaMap.New();
        final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());

        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(3)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .eventualIndexing(true)
            .build();

        try (final VectorIndex<Document> index = vectorIndices.add(
            "embeddings", config, new EmbeddedDocumentVectorizer()
        ))
        {
            gigaMap.add(new Document("doc1", new float[]{1.0f, 0.0f, 0.0f}));
            gigaMap.add(new Document("doc2", new float[]{0.0f, 1.0f, 0.0f}));

            final VectorIndex.Default<Document> defaultIndex = (VectorIndex.Default<Document>) index;
            defaultIndex.backgroundTaskManager.drainQueue();

            // RemoveAll — this discards pending operations and shuts down manager
            gigaMap.removeAll();

            // Index should be empty
            final VectorSearchResult<Document> result = index.search(new float[]{1.0f, 0.0f, 0.0f}, 10);
            assertEquals(0, result.size());

            // Add new data after removeAll — indexing manager is recreated by initializeIndex
            gigaMap.add(new Document("new_doc", new float[]{1.0f, 0.0f, 0.0f}));

            // Drain the new indexing manager
            defaultIndex.backgroundTaskManager.drainQueue();

            final VectorSearchResult<Document> result2 = index.search(new float[]{1.0f, 0.0f, 0.0f}, 10);
            assertEquals(1, result2.size());
        }
    }

    // ==================== Optimize Drains Queue Tests ====================

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testOptimizeDrainsQueueBeforeCleanup()
    {
        final int dimension = 64;
        final Random random = new Random(42);

        final GigaMap<Document> gigaMap = GigaMap.New();
        final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());

        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(dimension)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .eventualIndexing(true)
            .build();

        try (final VectorIndex<Document> index = vectorIndices.add(
            "embeddings", config, new ComputedDocumentVectorizer()
        ))
        {
            for (int i = 0; i < 50; i++)
            {
                gigaMap.add(new Document("doc_" + i, randomVector(random, dimension)));
            }

            // Optimize should drain the queue first, then run cleanup
            index.optimize();

            // After optimize, all nodes should be searchable
            final VectorSearchResult<Document> result = index.search(
                randomVector(new Random(99), dimension), 10
            );
            assertEquals(10, result.size());
        }
    }

    // ==================== PersistToDisk Writes Queued Operations Tests ====================

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testPersistToDiskWritesQueuedOperations(@TempDir final Path tempDir)
    {
        final int dimension = 64;
        final Random random = new Random(42);
        final Path indexDir = tempDir.resolve("index");

        final GigaMap<Document> gigaMap = GigaMap.New();
        final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());

        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(dimension)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .onDisk(true)
            .indexDirectory(indexDir)
            .eventualIndexing(true)
            .build();

        try (final VectorIndex<Document> index = vectorIndices.add(
            "embeddings", config, new ComputedDocumentVectorizer()
        ))
        {
            for (int i = 0; i < 50; i++)
            {
                gigaMap.add(new Document("doc_" + i, randomVector(random, dimension)));
            }

            // The operations still queued at the freeze move into the flush, which applies them
            index.persistToDisk();

            // Verify files were created
            assertTrue(SegmentLayout.indexExists(indexDir, "embeddings"));
            assertEquals(1, SegmentLayout.graphFiles(indexDir, "embeddings").size());

            // Search should work after persist
            final VectorSearchResult<Document> result = index.search(
                randomVector(new Random(99), dimension), 10
            );
            assertEquals(10, result.size());
        }
    }

    /**
     * The operations queued when a persist freezes the in-memory graph move into its flush, so the
     * segment holds them, and a restart, finding the manifest current, loads it without a rebuild.
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void testQueuedOperationsMovedIntoAFlushSurviveARestart(@TempDir final Path tempDir) throws InterruptedException
    {
        final int    dimension = 32;
        final Random random    = new Random(42);
        final Path   indexDir  = tempDir.resolve("index");
        final Path   storeDir  = tempDir.resolve("store");
        final float[] queued   = randomVector(random, dimension);
        final long    queuedId;

        try (final EmbeddedStorageManager storage = EmbeddedStorage.start(GigaMap.New(), storeDir))
        {
            final GigaMap<Document> gigaMap = rootOf(storage);
            final VectorIndex.Default<Document> index = (VectorIndex.Default<Document>)gigaMap.index()
                .register(VectorIndices.Category())
                .add("embeddings", onDiskEventualConfig(dimension, indexDir), new ComputedDocumentVectorizer());
            for (int i = 0; i < 50; i++)
            {
                gigaMap.add(new Document("doc_" + i, randomVector(random, dimension)));
            }
            index.backgroundTaskManager.drainQueue();

            // Hold the worker inside its next operation, so the one queued behind it is still queued
            // when the persist freezes; the persist waits for the held one, which owns the indexing lock.
            final CountDownLatch applying = new CountDownLatch(1);
            final CountDownLatch release  = new CountDownLatch(1);
            index.backgroundTaskManager.applyTestHook = () ->
            {
                index.backgroundTaskManager.applyTestHook = null;
                applying.countDown();
                awaitQuietly(release);
            };
            gigaMap.add(new Document("held", randomVector(random, dimension)));
            assertTrue(applying.await(10, TimeUnit.SECONDS));
            queuedId = gigaMap.add(new Document("queued", queued));

            final AtomicReference<Throwable> failure   = new AtomicReference<>();
            final Thread                     persister = new Thread(() ->
            {
                try
                {
                    index.persistToDisk();
                }
                catch (final Throwable t)
                {
                    failure.set(t);
                }
            }, "persister");
            persister.start();
            final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            while (persister.getState() != Thread.State.WAITING)
            {
                assertTrue(System.nanoTime() < deadline, "the persist must wait for the indexing lock");
                Thread.onSpinWait();
            }
            release.countDown();
            persister.join(TimeUnit.SECONDS.toMillis(30));
            assertFalse(persister.isAlive(), "the persist did not finish");
            assertNull(failure.get(), () -> "the persist failed: " + failure.get());

            assertEquals(0, index.inMemoryGraphNodeCount(), "the queued operation must be in the flush");
            assertEquals(queuedId, index.search(queued, 1).iterator().next().entityId());
            storage.storeRoot();
            index.close();
        }

        try (final EmbeddedStorageManager storage = EmbeddedStorage.start(storeDir))
        {
            final VectorIndex.Default<Document> index = (VectorIndex.Default<Document>)rootOf(storage).index()
                .get(VectorIndices.Category())
                .get("embeddings");
            assertEquals(queuedId, index.search(queued, 1).iterator().next().entityId());
            assertEquals(0, index.inMemoryGraphNodeCount(), "the restart must load the segment, not rebuild");
            index.close();
        }
    }

    /**
     * After a failed graph operation the graph lacks a change the store has, which only a rebuild
     * from the store repairs. The shutdown persist never rebuilds, so it commits nothing: the manifest
     * stays as it was, the restart finds it stale, rebuilds, and finds everything.
     */
    @Test
    @Tag("slow")
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void testShutdownAfterAFailedGraphOperationCommitsNothing(@TempDir final Path tempDir) throws IOException
    {
        final int    dimension = 32;
        final Random random    = new Random(43);
        final Path   indexDir  = tempDir.resolve("index");
        final Path   storeDir  = tempDir.resolve("store");
        final Path   manifest  = SegmentLayout.manifest(indexDir, "embeddings");
        final float[] late     = randomVector(random, dimension);
        final byte[]   manifestBytes;
        final FileTime manifestTime;

        try (final EmbeddedStorageManager storage = EmbeddedStorage.start(GigaMap.New(), storeDir))
        {
            final GigaMap<Document> gigaMap = rootOf(storage);
            final VectorIndex.Default<Document> index = (VectorIndex.Default<Document>)gigaMap.index()
                .register(VectorIndices.Category())
                .add("embeddings", onDiskEventualConfig(dimension, indexDir), new ComputedDocumentVectorizer());
            for (int i = 0; i < 50; i++)
            {
                gigaMap.add(new Document("doc_" + i, randomVector(random, dimension)));
            }
            index.persistToDisk();
            manifestBytes = Files.readAllBytes(manifest);
            manifestTime  = Files.getLastModifiedTime(manifest);

            gigaMap.add(new Document("late", late));
            index.graphOperationFailed(new IllegalStateException("simulated graph operation failure"));
            storage.storeRoot();
            index.close();
        }

        assertEquals(manifestTime, Files.getLastModifiedTime(manifest), "the shutdown must commit nothing");
        assertArrayEquals(manifestBytes, Files.readAllBytes(manifest), "the shutdown must commit nothing");

        try (final EmbeddedStorageManager storage = EmbeddedStorage.start(storeDir))
        {
            final VectorIndex.Default<Document> index = (VectorIndex.Default<Document>)rootOf(storage).index()
                .get(VectorIndices.Category())
                .get("embeddings");
            assertEquals("late", index.search(late, 1).iterator().next().entity().content());
            assertEquals(51, index.inMemoryGraphNodeCount(), "the stale manifest must make the restart rebuild");
            index.close();
        }
    }

    /**
     * A batch is checked against the flush threshold when it is enqueued, before the worker has
     * applied it, so the check sees none of it. The worker checks again once it has applied the batch,
     * which is what requests the flush here: the scheduled persist does not come within the test.
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void testBatchReachingTheThresholdInTheWorkerRequestsAFlush(@TempDir final Path tempDir)
        throws InterruptedException
    {
        final int    dimension = 32;
        final Random random    = new Random(44);
        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(dimension)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .onDisk(true)
            .indexDirectory(tempDir.resolve("index"))
            .eventualIndexing(true)
            .persistenceIntervalMs(600_000L)
            .flushThreshold(20)
            .build();

        final GigaMap<Document> gigaMap = GigaMap.New();
        try (final VectorIndex<Document> index = gigaMap.index().register(VectorIndices.Category())
            .add("embeddings", config, new ComputedDocumentVectorizer()))
        {
            final VectorIndex.Default<Document> def       = (VectorIndex.Default<Document>)index;
            final List<Document>                documents = new ArrayList<>();
            for (int i = 0; i < 25; i++)
            {
                documents.add(new Document("doc_" + i, randomVector(random, dimension)));
            }
            gigaMap.addAll(documents);

            final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            while (def.flushCount() == 0)
            {
                assertTrue(System.nanoTime() < deadline, "the applied batch must have requested a flush");
                Thread.sleep(20);
            }
            assertEquals(1, def.sourceSet().segments().size());
        }
    }

    /**
     * removeAll() discards the queued operations without waiting for the worker, which may have polled
     * one already. Applied after the clearing, that operation would put an entity that is gone into the
     * new graph, under an id a new entity may take, so it must be dropped.
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void testOperationPolledBeforeRemoveAllDoesNotReachTheNewGraph() throws InterruptedException
    {
        final GigaMap<Document> gigaMap = GigaMap.New();
        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(3)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .eventualIndexing(true)
            .build();
        try (final VectorIndex<Document> index = gigaMap.index().register(VectorIndices.Category())
            .add("embeddings", config, new ComputedDocumentVectorizer()))
        {
            final VectorIndex.Default<Document> def      = (VectorIndex.Default<Document>)index;
            final CountDownLatch                applying = new CountDownLatch(1);
            final CountDownLatch                release  = new CountDownLatch(1);
            final AtomicReference<Thread>       worker   = new AtomicReference<>();
            def.backgroundTaskManager.applyTestHook = () ->
            {
                worker.set(Thread.currentThread());
                applying.countDown();
                awaitQuietly(release);
            };
            gigaMap.add(new Document("before", new float[]{1.0f, 0.0f, 0.0f}));
            assertTrue(applying.await(10, TimeUnit.SECONDS));

            // The worker holds the polled operation, not the builder lock, so the clearing goes ahead.
            gigaMap.removeAll();
            release.countDown();
            worker.get().join(TimeUnit.SECONDS.toMillis(30));
            assertFalse(worker.get().isAlive(), "the old worker must finish once released");

            assertEquals(0, def.inMemoryGraphNodeCount(), "the stale operation must not reach the new graph");
            gigaMap.add(new Document("after", new float[]{0.0f, 1.0f, 0.0f}));
            def.backgroundTaskManager.drainQueue();
            final VectorSearchResult<Document> result = index.search(new float[]{1.0f, 0.0f, 0.0f}, 10);
            assertEquals(1, result.size());
            assertEquals("after", result.toList().get(0).entity().content());
        }
    }

    private static VectorIndexConfiguration onDiskEventualConfig(
        final int  dimension,
        final Path indexDir
    )
    {
        return VectorIndexConfiguration.builder()
            .dimension(dimension)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .onDisk(true)
            .indexDirectory(indexDir)
            .eventualIndexing(true)
            .persistOnShutdown(true)
            .build();
    }

    private static void awaitQuietly(final CountDownLatch latch)
    {
        try
        {
            latch.await(30, TimeUnit.SECONDS);
        }
        catch (final InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
    }

    // ==================== Close Drains Queue Tests ====================

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testCloseDrainsPendingOperations()
    {
        final GigaMap<Document> gigaMap = GigaMap.New();
        final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());

        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(3)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .eventualIndexing(true)
            .build();

        final VectorIndex<Document> index = vectorIndices.add(
            "embeddings", config, new EmbeddedDocumentVectorizer()
        );

        gigaMap.add(new Document("doc1", new float[]{1.0f, 0.0f, 0.0f}));
        gigaMap.add(new Document("doc2", new float[]{0.0f, 1.0f, 0.0f}));

        // Close should drain pending operations without error
        index.close();

        // No assertion needed — if close() deadlocks or throws, the @Timeout will catch it
    }

    // ==================== Pending Count Tests ====================

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testPendingCountTracksQueuedOperations()
    {
        final GigaMap<Document> gigaMap = GigaMap.New();
        final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());

        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(3)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .eventualIndexing(true)
            .build();

        try (final VectorIndex<Document> index = vectorIndices.add(
            "embeddings", config, new EmbeddedDocumentVectorizer()
        ))
        {
            final VectorIndex.Default<Document> defaultIndex = (VectorIndex.Default<Document>) index;

            // Initially empty
            assertEquals(0, defaultIndex.backgroundTaskManager.getPendingIndexingCount());

            // After drain, count should be 0
            gigaMap.add(new Document("doc1", new float[]{1.0f, 0.0f, 0.0f}));
            gigaMap.add(new Document("doc2", new float[]{0.0f, 1.0f, 0.0f}));

            defaultIndex.backgroundTaskManager.drainQueue();

            assertEquals(0, defaultIndex.backgroundTaskManager.getPendingIndexingCount());
        }
    }

    // ==================== Large Data Set Tests ====================

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void testLargeDataSetWithEventualIndexing()
    {
        final int dimension = 128;
        final int vectorCount = 500;
        final Random random = new Random(42);

        final GigaMap<Document> gigaMap = GigaMap.New();
        final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());

        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(dimension)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .maxDegree(16)
            .beamWidth(100)
            .eventualIndexing(true)
            .build();

        try (final VectorIndex<Document> index = vectorIndices.add(
            "embeddings", config, new ComputedDocumentVectorizer()
        ))
        {
            // Add random vectors
            for (int i = 0; i < vectorCount; i++)
            {
                gigaMap.add(new Document("doc_" + i, randomVector(random, dimension)));
            }

            final VectorIndex.Default<Document> defaultIndex = (VectorIndex.Default<Document>) index;
            defaultIndex.backgroundTaskManager.drainQueue();

            // Search should return correct number of results
            final VectorSearchResult<Document> result = index.search(
                randomVector(new Random(99), dimension), 10
            );
            assertEquals(10, result.size());

            // All results should have valid scores
            for (final ScoredSearchResult.Entry<Document> entry : result)
            {
                assertTrue(entry.score() > 0, "Score should be positive");
                assertNotNull(entry.entity());
            }
        }
    }

    // ==================== On-Disk with Eventual Indexing ====================

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void testOnDiskWithEventualIndexing(@TempDir final Path tempDir)
    {
        final int dimension = 64;
        final int vectorCount = 100;
        final Random random = new Random(42);
        final Path indexDir = tempDir.resolve("index");

        final GigaMap<Document> gigaMap = GigaMap.New();
        final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());

        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(dimension)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .onDisk(true)
            .indexDirectory(indexDir)
            .eventualIndexing(true)
            .build();

        try (final VectorIndex<Document> index = vectorIndices.add(
            "embeddings", config, new ComputedDocumentVectorizer()
        ))
        {
            for (int i = 0; i < vectorCount; i++)
            {
                gigaMap.add(new Document("doc_" + i, randomVector(random, dimension)));
            }

            // Persist triggers drain
            index.persistToDisk();

            assertTrue(SegmentLayout.indexExists(indexDir, "embeddings"));

            final VectorSearchResult<Document> result = index.search(
                randomVector(new Random(99), dimension), 10
            );
            assertEquals(10, result.size());
        }
    }

    // ==================== Background Persistence + Eventual Indexing ====================

    @Test
    @Tag("slow")
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testBackgroundPersistenceWithEventualIndexing(@TempDir final Path tempDir) throws Exception
    {
        final int dimension = 64;
        final Random random = new Random(42);
        final Path indexDir = tempDir.resolve("index");

        final GigaMap<Document> gigaMap = GigaMap.New();
        final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());

        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(dimension)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .onDisk(true)
            .indexDirectory(indexDir)
            .persistenceIntervalMs(500)
            .minChangesBetweenPersists(1)
            // Below the threshold a background persist leaves the in-memory graph alone.
            .flushThreshold(1)
            .eventualIndexing(true)
            .build();

        try (final VectorIndex<Document> index = vectorIndices.add(
            "embeddings", config, new ComputedDocumentVectorizer()
        ))
        {
            for (int i = 0; i < 20; i++)
            {
                gigaMap.add(new Document("doc_" + i, randomVector(random, dimension)));
            }

            // Wait for background persistence (which should drain first)
            Thread.sleep(1500);

            assertTrue(SegmentLayout.indexExists(indexDir, "embeddings"));
        }
    }

    // ==================== Disabled by Default Tests ====================

    @Test
    void testEventualIndexingDisabledByDefault()
    {
        final GigaMap<Document> gigaMap = GigaMap.New();
        final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());

        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(3)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .build();

        try(final VectorIndex<Document> index = vectorIndices.add(
            "embeddings", config, new EmbeddedDocumentVectorizer()
        ))
        {
            final VectorIndex.Default<Document> defaultIndex = (VectorIndex.Default<Document>) index;

            // Background task manager should be null when no background features are enabled
            assertNull(defaultIndex.backgroundTaskManager);

            // Synchronous indexing should still work
            gigaMap.add(new Document("doc1", new float[]{1.0f, 0.0f, 0.0f}));

            final VectorSearchResult<Document> result = index.search(new float[]{1.0f, 0.0f, 0.0f}, 1);
            assertEquals(1, result.size());
        }
    }

    // ==================== Combined Operations Tests ====================

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testAddUpdateRemoveSequenceWithEventualIndexing()
    {
        final GigaMap<Document> gigaMap = GigaMap.New();
        final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());

        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(3)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .eventualIndexing(true)
            .build();

        try (final VectorIndex<Document> index = vectorIndices.add(
            "embeddings", config, new EmbeddedDocumentVectorizer()
        ))
        {
            final VectorIndex.Default<Document> defaultIndex = (VectorIndex.Default<Document>) index;

            // Add 3 documents
            gigaMap.add(new Document("doc1", new float[]{1.0f, 0.0f, 0.0f}));
            gigaMap.add(new Document("doc2", new float[]{0.0f, 1.0f, 0.0f}));
            gigaMap.add(new Document("doc3", new float[]{0.0f, 0.0f, 1.0f}));

            defaultIndex.backgroundTaskManager.drainQueue();

            // Update doc2
            gigaMap.set(1L, new Document("doc2_updated", new float[]{0.9f, 0.1f, 0.0f}));

            defaultIndex.backgroundTaskManager.drainQueue();

            // Remove doc3
            gigaMap.removeById(2L);

            defaultIndex.backgroundTaskManager.drainQueue();

            // Search: should find 2 documents
            final VectorSearchResult<Document> result = index.search(new float[]{1.0f, 0.0f, 0.0f}, 3);
            assertEquals(2, result.size());

            // doc1 should be closest to [1,0,0], followed by updated doc2 [0.9,0.1,0]
            assertEquals("doc1", result.toList().get(0).entity().content());
            assertEquals("doc2_updated", result.toList().get(1).entity().content());
        }
    }

    // ==================== Background Optimization + Eventual Indexing ====================

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testBackgroundOptimizationWithEventualIndexing() throws Exception
    {
        final int dimension = 64;
        final Random random = new Random(42);

        final GigaMap<Document> gigaMap = GigaMap.New();
        final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());

        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(dimension)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .optimizationIntervalMs(300)
            .minChangesBetweenOptimizations(10)
            .eventualIndexing(true)
            .build();

        try (final VectorIndex<Document> index = vectorIndices.add(
            "embeddings", config, new ComputedDocumentVectorizer()
        ))
        {
            for (int i = 0; i < 50; i++)
            {
                gigaMap.add(new Document("doc_" + i, randomVector(random, dimension)));
            }

            final VectorIndex.Default<Document> defaultIndex = (VectorIndex.Default<Document>) index;
            defaultIndex.backgroundTaskManager.drainQueue();

            // Wait for background optimization to run
            Thread.sleep(800);

            // Optimization should have run at least once
            assertTrue(defaultIndex.backgroundTaskManager.getOptimizationCount() >= 1);

            // Search should still work
            final VectorSearchResult<Document> result = index.search(
                randomVector(new Random(99), dimension), 10
            );
            assertEquals(10, result.size());
        }
    }

    /**
     * The root as the typed map: the storage manager is not generic over the root.
     */
    @SuppressWarnings("unchecked")
    private static GigaMap<Document> rootOf(final EmbeddedStorageManager storage)
    {
        return (GigaMap<Document>)storage.root();
    }
}
