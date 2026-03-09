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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that set(), removeById(), and addAll() operations can be freely interleaved
 * on a GigaMap with a VectorIndex without requiring an intermediate store() call.
 * <p>
 * Before the fix, deferred HNSW builder operations from set()/removeById() could
 * interleave with addAll() batch operations, causing graph corruption or deadlocks.
 * The fix drains deferred builder ops at the start of each sync-mode mutation,
 * ensuring operations are properly sequenced.
 */
class VectorIndexInterleavingTest
{
    static final class Document
    {
        private final String  content;
        private final float[] embedding;

        Document(final String content, final float[] embedding)
        {
            this.content   = content;
            this.embedding = embedding;
        }

        public String content()
        {
            return this.content;
        }

        public float[] embedding()
        {
            return this.embedding;
        }

        @Override
        public boolean equals(final Object obj)
        {
            if(obj == this)
                return true;
            if(obj == null || obj.getClass() != this.getClass())
                return false;
            final var that = (Document)obj;
            return Objects.equals(this.content, that.content) &&
                Arrays.equals(this.embedding, that.embedding);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(this.content, Arrays.hashCode(this.embedding));
        }

        @Override
        public String toString()
        {
            return "Document[content=" + this.content + "]";
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

    static class ComputedDocumentVectorizer extends Vectorizer<Document>
    {
        @Override
        public float[] vectorize(final Document entity)
        {
            return entity.embedding();
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

    private static GigaMap<Document> createGigaMapWithVectorIndex(
        final Vectorizer<Document> vectorizer,
        final int                  dimension
    )
    {
        final GigaMap<Document> gigaMap = GigaMap.New();
        final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());
        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(dimension)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .maxDegree(16)
            .beamWidth(100)
            .build();
        vectorIndices.add("embeddings", config, vectorizer);
        return gigaMap;
    }

    private static VectorIndex<Document> getVectorIndex(final GigaMap<Document> gigaMap)
    {
        return gigaMap.index().get(VectorIndices.Category()).get("embeddings");
    }


    // ==================== set() then addAll() ====================

    /**
     * Core scenario: set() followed by addAll() without intermediate store().
     * Before the fix, deferred builder ops from set() could corrupt the HNSW graph
     * when addAll() added new nodes.
     */
    @Test
    void testSetThenAddAllWithoutStore()
    {
        final GigaMap<Document> gigaMap = createGigaMapWithVectorIndex(new EmbeddedDocumentVectorizer(), 3);
        final VectorIndex<Document> index = getVectorIndex(gigaMap);

        // Add initial documents
        gigaMap.add(new Document("doc0", new float[]{1.0f, 0.0f, 0.0f}));
        gigaMap.add(new Document("doc1", new float[]{0.0f, 1.0f, 0.0f}));

        // set() — this triggers internalUpdate which may defer builder ops
        gigaMap.set(0, new Document("doc0-updated", new float[]{0.5f, 0.5f, 0.0f}));

        // addAll() immediately after set() — no store() in between
        gigaMap.addAll(List.of(
            new Document("doc2", new float[]{0.0f, 0.0f, 1.0f}),
            new Document("doc3", new float[]{0.1f, 0.1f, 0.8f})
        ));

        assertEquals(4, gigaMap.size());

        // Verify search returns correct results — graph should be consistent
        final VectorSearchResult<Document> result = index.search(new float[]{0.0f, 0.0f, 1.0f}, 2);
        assertNotNull(result);
        assertFalse(result.isEmpty());

        // doc2 should be the closest match
        final Document firstMatch = result.toList().get(0).entity();
        assertEquals("doc2", firstMatch.content());
    }

    /**
     * Multiple set() calls followed by addAll() — accumulates multiple deferred ops.
     */
    @Test
    void testMultipleSetsThenAddAll()
    {
        final GigaMap<Document> gigaMap = createGigaMapWithVectorIndex(new EmbeddedDocumentVectorizer(), 3);
        final VectorIndex<Document> index = getVectorIndex(gigaMap);

        // Add initial documents
        gigaMap.add(new Document("doc0", new float[]{1.0f, 0.0f, 0.0f}));
        gigaMap.add(new Document("doc1", new float[]{0.0f, 1.0f, 0.0f}));
        gigaMap.add(new Document("doc2", new float[]{0.0f, 0.0f, 1.0f}));

        // Multiple set() calls
        gigaMap.set(0, new Document("doc0-v2", new float[]{0.9f, 0.1f, 0.0f}));
        gigaMap.set(1, new Document("doc1-v2", new float[]{0.1f, 0.9f, 0.0f}));
        gigaMap.set(2, new Document("doc2-v2", new float[]{0.0f, 0.1f, 0.9f}));

        // addAll() without store()
        gigaMap.addAll(List.of(
            new Document("doc3", new float[]{0.7f, 0.7f, 0.0f}),
            new Document("doc4", new float[]{0.0f, 0.7f, 0.7f})
        ));

        assertEquals(5, gigaMap.size());

        // Search should work correctly
        final VectorSearchResult<Document> result = index.search(new float[]{1.0f, 0.0f, 0.0f}, 5);
        assertNotNull(result);
        assertEquals(5, result.size());
    }


    // ==================== removeById() then addAll() ====================

    /**
     * removeById() followed by addAll() without intermediate store().
     */
    @Test
    void testRemoveByIdThenAddAllWithoutStore()
    {
        final GigaMap<Document> gigaMap = createGigaMapWithVectorIndex(new EmbeddedDocumentVectorizer(), 3);
        final VectorIndex<Document> index = getVectorIndex(gigaMap);

        // Add initial documents
        gigaMap.add(new Document("doc0", new float[]{1.0f, 0.0f, 0.0f}));
        gigaMap.add(new Document("doc1", new float[]{0.0f, 1.0f, 0.0f}));
        gigaMap.add(new Document("doc2", new float[]{0.0f, 0.0f, 1.0f}));

        // Remove
        gigaMap.removeById(1);

        // addAll() without store()
        gigaMap.addAll(List.of(
            new Document("doc3", new float[]{0.5f, 0.5f, 0.0f}),
            new Document("doc4", new float[]{0.0f, 0.5f, 0.5f})
        ));

        assertEquals(4, gigaMap.size());

        // Search should not find the removed document
        final VectorSearchResult<Document> result = index.search(new float[]{0.0f, 1.0f, 0.0f}, 4);
        assertNotNull(result);
        for(final VectorSearchResult.Entry<Document> entry : result)
        {
            assertNotEquals("doc1", entry.entity().content(), "Removed document should not appear in results");
        }
    }

    /**
     * Multiple removeById() calls followed by addAll().
     */
    @Test
    void testMultipleRemovesThenAddAll()
    {
        final GigaMap<Document> gigaMap = createGigaMapWithVectorIndex(new EmbeddedDocumentVectorizer(), 3);
        final VectorIndex<Document> index = getVectorIndex(gigaMap);

        // Add initial documents — all vectors must be non-zero for cosine similarity
        gigaMap.add(new Document("doc0", new float[]{1.0f, 0.0f, 0.0f}));
        gigaMap.add(new Document("doc1", new float[]{0.0f, 1.0f, 0.0f}));
        gigaMap.add(new Document("doc2", new float[]{0.0f, 0.0f, 1.0f}));
        gigaMap.add(new Document("doc3", new float[]{0.7f, 0.7f, 0.0f}));
        gigaMap.add(new Document("doc4", new float[]{0.0f, 0.7f, 0.7f}));

        // Remove multiple
        gigaMap.removeById(0);
        gigaMap.removeById(2);

        // addAll() without store()
        gigaMap.addAll(List.of(
            new Document("doc5", new float[]{0.5f, 0.5f, 0.0f}),
            new Document("doc6", new float[]{0.0f, 0.5f, 0.5f})
        ));

        assertEquals(5, gigaMap.size());

        final VectorSearchResult<Document> result = index.search(new float[]{1.0f, 0.0f, 0.0f}, 5);
        assertNotNull(result);
    }


    // ==================== addAll() then set() ====================

    /**
     * addAll() followed by set() without intermediate store().
     * Tests the reverse interleaving direction.
     */
    @Test
    void testAddAllThenSetWithoutStore()
    {
        final GigaMap<Document> gigaMap = createGigaMapWithVectorIndex(new EmbeddedDocumentVectorizer(), 3);
        final VectorIndex<Document> index = getVectorIndex(gigaMap);

        // Add initial document
        gigaMap.add(new Document("doc0", new float[]{1.0f, 0.0f, 0.0f}));

        // addAll()
        gigaMap.addAll(List.of(
            new Document("doc1", new float[]{0.0f, 1.0f, 0.0f}),
            new Document("doc2", new float[]{0.0f, 0.0f, 1.0f})
        ));

        // set() immediately after addAll() — no store() in between
        gigaMap.set(0, new Document("doc0-updated", new float[]{0.5f, 0.5f, 0.0f}));

        assertEquals(3, gigaMap.size());

        // Search should reflect the updated vector
        final VectorSearchResult<Document> result = index.search(new float[]{0.5f, 0.5f, 0.0f}, 1);
        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals("doc0-updated", result.toList().get(0).entity().content());
    }

    /**
     * addAll() followed by removeById() without intermediate store().
     */
    @Test
    void testAddAllThenRemoveByIdWithoutStore()
    {
        final GigaMap<Document> gigaMap = createGigaMapWithVectorIndex(new EmbeddedDocumentVectorizer(), 3);
        final VectorIndex<Document> index = getVectorIndex(gigaMap);

        // Add initial document
        gigaMap.add(new Document("doc0", new float[]{1.0f, 0.0f, 0.0f}));

        // addAll()
        gigaMap.addAll(List.of(
            new Document("doc1", new float[]{0.0f, 1.0f, 0.0f}),
            new Document("doc2", new float[]{0.0f, 0.0f, 1.0f})
        ));

        // removeById() immediately after addAll() — no store() in between
        gigaMap.removeById(0);

        assertEquals(2, gigaMap.size());

        final VectorSearchResult<Document> result = index.search(new float[]{1.0f, 0.0f, 0.0f}, 2);
        assertNotNull(result);
        for(final VectorSearchResult.Entry<Document> entry : result)
        {
            assertNotEquals("doc0", entry.entity().content(), "Removed document should not appear in results");
        }
    }


    // ==================== Mixed interleaving ====================

    /**
     * Complex interleaving: set, addAll, remove, addAll, set — all without store().
     */
    @Test
    void testComplexInterleavingWithoutStore()
    {
        final GigaMap<Document> gigaMap = createGigaMapWithVectorIndex(new EmbeddedDocumentVectorizer(), 3);
        final VectorIndex<Document> index = getVectorIndex(gigaMap);

        // Seed data
        gigaMap.add(new Document("doc0", new float[]{1.0f, 0.0f, 0.0f}));
        gigaMap.add(new Document("doc1", new float[]{0.0f, 1.0f, 0.0f}));

        // set
        gigaMap.set(0, new Document("doc0-v2", new float[]{0.8f, 0.2f, 0.0f}));

        // addAll
        gigaMap.addAll(List.of(
            new Document("doc2", new float[]{0.0f, 0.0f, 1.0f}),
            new Document("doc3", new float[]{0.3f, 0.3f, 0.4f})
        ));

        // removeById
        gigaMap.removeById(1);

        // addAll again
        gigaMap.addAll(List.of(
            new Document("doc4", new float[]{0.5f, 0.0f, 0.5f})
        ));

        // set again
        gigaMap.set(2, new Document("doc2-v2", new float[]{0.1f, 0.1f, 0.8f}));

        assertEquals(4, gigaMap.size());

        // Verify search consistency
        final VectorSearchResult<Document> result = index.search(new float[]{0.0f, 0.0f, 1.0f}, 4);
        assertNotNull(result);
        assertEquals(4, result.size());

        // doc1 was removed — should not appear
        for(final VectorSearchResult.Entry<Document> entry : result)
        {
            assertNotEquals("doc1", entry.entity().content());
        }
    }

    /**
     * Repeated cycles of set+addAll without any store() calls.
     */
    @Test
    void testRepeatedSetAddAllCycles()
    {
        final GigaMap<Document> gigaMap = createGigaMapWithVectorIndex(new EmbeddedDocumentVectorizer(), 3);
        final VectorIndex<Document> index = getVectorIndex(gigaMap);
        final Random random = new Random(42);

        // Seed
        for(int i = 0; i < 5; i++)
        {
            gigaMap.add(new Document("seed" + i, randomVector(random, 3)));
        }

        // 10 cycles of set + addAll
        for(int cycle = 0; cycle < 10; cycle++)
        {
            // Update an existing entity
            final long entityId = cycle % 5;
            gigaMap.set(entityId, new Document("updated-cycle" + cycle, randomVector(random, 3)));

            // Add a batch of new entities
            final List<Document> batch = new ArrayList<>();
            for(int j = 0; j < 3; j++)
            {
                batch.add(new Document("batch-c" + cycle + "-" + j, randomVector(random, 3)));
            }
            gigaMap.addAll(batch);
        }

        assertEquals(5 + 10 * 3, gigaMap.size()); // 5 seed + 30 added

        // Search should work
        final VectorSearchResult<Document> result = index.search(randomVector(random, 3), 10);
        assertNotNull(result);
        assertEquals(10, result.size());
    }


    // ==================== Computed (non-embedded) vectorizer ====================

    /**
     * Same interleaving test but with a computed (non-embedded) vectorizer
     * which stores vectors separately in a VectorStore.
     */
    @Test
    void testSetThenAddAllComputedVectorizer()
    {
        final GigaMap<Document> gigaMap = createGigaMapWithVectorIndex(new ComputedDocumentVectorizer(), 3);
        final VectorIndex<Document> index = getVectorIndex(gigaMap);

        gigaMap.add(new Document("doc0", new float[]{1.0f, 0.0f, 0.0f}));
        gigaMap.add(new Document("doc1", new float[]{0.0f, 1.0f, 0.0f}));

        gigaMap.set(0, new Document("doc0-updated", new float[]{0.5f, 0.5f, 0.0f}));

        gigaMap.addAll(List.of(
            new Document("doc2", new float[]{0.0f, 0.0f, 1.0f}),
            new Document("doc3", new float[]{0.1f, 0.1f, 0.8f})
        ));

        assertEquals(4, gigaMap.size());

        final VectorSearchResult<Document> result = index.search(new float[]{0.0f, 0.0f, 1.0f}, 2);
        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals("doc2", result.toList().get(0).entity().content());
    }

    /**
     * Complex interleaving with computed vectorizer.
     */
    @Test
    void testComplexInterleavingComputedVectorizer()
    {
        final GigaMap<Document> gigaMap = createGigaMapWithVectorIndex(new ComputedDocumentVectorizer(), 3);
        final VectorIndex<Document> index = getVectorIndex(gigaMap);

        gigaMap.add(new Document("doc0", new float[]{1.0f, 0.0f, 0.0f}));
        gigaMap.add(new Document("doc1", new float[]{0.0f, 1.0f, 0.0f}));

        // Interleave: set, addAll, remove, set, addAll
        gigaMap.set(0, new Document("doc0-v2", new float[]{0.9f, 0.1f, 0.0f}));
        gigaMap.addAll(List.of(new Document("doc2", new float[]{0.0f, 0.0f, 1.0f})));
        gigaMap.removeById(1);
        gigaMap.set(0, new Document("doc0-v3", new float[]{0.8f, 0.2f, 0.0f}));
        gigaMap.addAll(List.of(new Document("doc3", new float[]{0.1f, 0.9f, 0.0f})));

        assertEquals(3, gigaMap.size());

        final VectorSearchResult<Document> result = index.search(new float[]{0.0f, 1.0f, 0.0f}, 3);
        assertNotNull(result);
        assertEquals(3, result.size());
    }


    // ==================== On-disk mode ====================

    /**
     * Interleaving with on-disk vector index.
     */
    @Test
    void testSetThenAddAllOnDisk(@TempDir final Path tempDir)
    {
        final GigaMap<Document> gigaMap = GigaMap.New();
        final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());
        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(3)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .maxDegree(16)
            .beamWidth(100)
            .onDisk(true)
            .indexDirectory(tempDir)
            .build();
        final VectorIndex<Document> index = vectorIndices.add(
            "embeddings", config, new EmbeddedDocumentVectorizer()
        );

        gigaMap.add(new Document("doc0", new float[]{1.0f, 0.0f, 0.0f}));
        gigaMap.add(new Document("doc1", new float[]{0.0f, 1.0f, 0.0f}));

        // set then addAll without store
        gigaMap.set(0, new Document("doc0-updated", new float[]{0.5f, 0.5f, 0.0f}));
        gigaMap.addAll(List.of(
            new Document("doc2", new float[]{0.0f, 0.0f, 1.0f}),
            new Document("doc3", new float[]{0.1f, 0.1f, 0.8f})
        ));

        assertEquals(4, gigaMap.size());

        final VectorSearchResult<Document> result = index.search(new float[]{0.0f, 0.0f, 1.0f}, 2);
        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals("doc2", result.toList().get(0).entity().content());
    }


    // ==================== Concurrent interleaving stress test ====================

    /**
     * Multiple threads concurrently interleave set(), removeById(), and addAll()
     * without any store() calls. This validates that no deadlock or graph corruption
     * occurs under concurrent access.
     */
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testConcurrentInterleavingWithoutStore()
    {
        final int dimension    = 16;
        final int seedCount    = 20;
        final int threadCount  = 4;
        final int opsPerThread = 50;

        final GigaMap<Document> gigaMap = createGigaMapWithVectorIndex(new ComputedDocumentVectorizer(), dimension);
        final VectorIndex<Document> index = getVectorIndex(gigaMap);

        // Seed
        final Random seedRandom = new Random(42);
        for(int i = 0; i < seedCount; i++)
        {
            gigaMap.add(new Document("seed_" + i, randomVector(seedRandom, dimension)));
        }

        final AtomicBoolean hasError = new AtomicBoolean(false);
        final List<Throwable> errors = java.util.Collections.synchronizedList(new ArrayList<>());
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch doneLatch = new CountDownLatch(threadCount);
        final AtomicInteger completedOps = new AtomicInteger(0);

        final ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for(int t = 0; t < threadCount; t++)
        {
            final int threadId = t;
            executor.submit(() ->
            {
                try
                {
                    startLatch.await();
                    final Random random = new Random(1000 + threadId);

                    for(int op = 0; op < opsPerThread && !hasError.get(); op++)
                    {
                        try
                        {
                            final int action = random.nextInt(100);

                            if(action < 25)
                            {
                                // 25%: set() on a seed entity
                                final long targetId = random.nextInt(seedCount);
                                synchronized(gigaMap)
                                {
                                    try
                                    {
                                        gigaMap.set(targetId, new Document(
                                            "set_t" + threadId + "_" + op,
                                            randomVector(random, dimension)
                                        ));
                                    }
                                    catch(final Exception ignored)
                                    {
                                        // Entity may have been removed
                                    }
                                }
                            }
                            else if(action < 40)
                            {
                                // 15%: removeById()
                                final long targetId = random.nextInt(seedCount);
                                synchronized(gigaMap)
                                {
                                    try
                                    {
                                        gigaMap.removeById(targetId);
                                    }
                                    catch(final Exception ignored)
                                    {
                                        // Already removed
                                    }
                                }
                            }
                            else if(action < 60)
                            {
                                // 20%: addAll() — interleaved with set/remove
                                final List<Document> batch = new ArrayList<>();
                                for(int j = 0; j < 3; j++)
                                {
                                    batch.add(new Document(
                                        "addAll_t" + threadId + "_" + op + "_" + j,
                                        randomVector(random, dimension)
                                    ));
                                }
                                synchronized(gigaMap)
                                {
                                    gigaMap.addAll(batch);
                                }
                            }
                            else
                            {
                                // 40%: search
                                final VectorSearchResult<Document> result = index.search(
                                    randomVector(random, dimension), 5
                                );
                                assertNotNull(result);
                            }

                            completedOps.incrementAndGet();
                        }
                        catch(final Throwable e)
                        {
                            errors.add(e);
                            hasError.set(true);
                        }
                    }
                }
                catch(final InterruptedException e)
                {
                    Thread.currentThread().interrupt();
                }
                finally
                {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();

        try
        {
            assertTrue(doneLatch.await(25, TimeUnit.SECONDS), "Threads should complete within timeout");
        }
        catch(final InterruptedException e)
        {
            Thread.currentThread().interrupt();
            fail("Test was interrupted");
        }
        finally
        {
            executor.shutdownNow();
        }

        if(!errors.isEmpty())
        {
            final Throwable first = errors.get(0);
            fail("Concurrent interleaving failed with " + errors.size() + " error(s): " + first.getMessage(), first);
        }

        assertTrue(completedOps.get() > 0, "Some operations should have completed");
    }


    // ==================== Concurrent set+addAll interleaving stress ====================

    /**
     * Targeted stress test with embedded vectorizer: one thread does set() in a loop,
     * another does addAll() in a loop. Exercises the EntityBackedVectorValues.getVector()
     * → GigaMap.get() path that caused the original ForkJoinPool deadlock.
     */
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testConcurrentSetAndAddAllInterleavingEmbedded()
    {
        final int dimension = 8;
        final int iterations = 100;

        final GigaMap<Document> gigaMap = createGigaMapWithVectorIndex(new EmbeddedDocumentVectorizer(), dimension);
        final VectorIndex<Document> index = getVectorIndex(gigaMap);

        // Seed entities for set() to target
        final Random seedRandom = new Random(42);
        for(int i = 0; i < 10; i++)
        {
            gigaMap.add(new Document("seed_" + i, randomVector(seedRandom, dimension)));
        }

        final AtomicBoolean hasError = new AtomicBoolean(false);
        final List<Throwable> errors = java.util.Collections.synchronizedList(new ArrayList<>());
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch doneLatch = new CountDownLatch(2);

        // Thread 1: set() loop
        final Thread setThread = new Thread(() ->
        {
            try
            {
                startLatch.await();
                final Random random = new Random(100);
                for(int i = 0; i < iterations && !hasError.get(); i++)
                {
                    final long targetId = random.nextInt(10);
                    synchronized(gigaMap)
                    {
                        try
                        {
                            gigaMap.set(targetId, new Document(
                                "set_" + i, randomVector(random, dimension)
                            ));
                        }
                        catch(final Exception ignored)
                        {
                        }
                    }
                }
            }
            catch(final Throwable e)
            {
                errors.add(e);
                hasError.set(true);
            }
            finally
            {
                doneLatch.countDown();
            }
        });

        // Thread 2: addAll() loop
        final Thread addAllThread = new Thread(() ->
        {
            try
            {
                startLatch.await();
                final Random random = new Random(200);
                for(int i = 0; i < iterations && !hasError.get(); i++)
                {
                    final List<Document> batch = List.of(
                        new Document("batch_" + i + "_0", randomVector(random, dimension)),
                        new Document("batch_" + i + "_1", randomVector(random, dimension))
                    );
                    synchronized(gigaMap)
                    {
                        gigaMap.addAll(batch);
                    }
                }
            }
            catch(final Throwable e)
            {
                errors.add(e);
                hasError.set(true);
            }
            finally
            {
                doneLatch.countDown();
            }
        });

        setThread.start();
        addAllThread.start();
        startLatch.countDown();

        try
        {
            assertTrue(doneLatch.await(25, TimeUnit.SECONDS), "Threads should complete within timeout");
        }
        catch(final InterruptedException e)
        {
            Thread.currentThread().interrupt();
            fail("Test was interrupted");
        }

        if(!errors.isEmpty())
        {
            fail("Concurrent set+addAll (embedded) failed: " + errors.get(0).getMessage(), errors.get(0));
        }

        final VectorSearchResult<Document> result = index.search(randomVector(new Random(999), dimension), 5);
        assertNotNull(result);
    }

    /**
     * Targeted stress test: one thread does set() in a loop, another does addAll() in a loop,
     * concurrently. This is the specific pattern that caused the original deadlock.
     */
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testConcurrentSetAndAddAllInterleaving()
    {
        final int dimension = 8;
        final int iterations = 100;

        final GigaMap<Document> gigaMap = createGigaMapWithVectorIndex(new ComputedDocumentVectorizer(), dimension);
        final VectorIndex<Document> index = getVectorIndex(gigaMap);

        // Seed entities for set() to target
        final Random seedRandom = new Random(42);
        for(int i = 0; i < 10; i++)
        {
            gigaMap.add(new Document("seed_" + i, randomVector(seedRandom, dimension)));
        }

        final AtomicBoolean hasError = new AtomicBoolean(false);
        final List<Throwable> errors = java.util.Collections.synchronizedList(new ArrayList<>());
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch doneLatch = new CountDownLatch(2);

        // Thread 1: set() loop
        final Thread setThread = new Thread(() ->
        {
            try
            {
                startLatch.await();
                final Random random = new Random(100);
                for(int i = 0; i < iterations && !hasError.get(); i++)
                {
                    final long targetId = random.nextInt(10);
                    synchronized(gigaMap)
                    {
                        try
                        {
                            gigaMap.set(targetId, new Document(
                                "set_" + i, randomVector(random, dimension)
                            ));
                        }
                        catch(final Exception ignored)
                        {
                            // Entity may have been removed
                        }
                    }
                }
            }
            catch(final Throwable e)
            {
                errors.add(e);
                hasError.set(true);
            }
            finally
            {
                doneLatch.countDown();
            }
        });

        // Thread 2: addAll() loop
        final Thread addAllThread = new Thread(() ->
        {
            try
            {
                startLatch.await();
                final Random random = new Random(200);
                for(int i = 0; i < iterations && !hasError.get(); i++)
                {
                    final List<Document> batch = List.of(
                        new Document("batch_" + i + "_0", randomVector(random, dimension)),
                        new Document("batch_" + i + "_1", randomVector(random, dimension))
                    );
                    synchronized(gigaMap)
                    {
                        gigaMap.addAll(batch);
                    }
                }
            }
            catch(final Throwable e)
            {
                errors.add(e);
                hasError.set(true);
            }
            finally
            {
                doneLatch.countDown();
            }
        });

        setThread.start();
        addAllThread.start();
        startLatch.countDown();

        try
        {
            assertTrue(doneLatch.await(25, TimeUnit.SECONDS), "Threads should complete within timeout");
        }
        catch(final InterruptedException e)
        {
            Thread.currentThread().interrupt();
            fail("Test was interrupted");
        }

        if(!errors.isEmpty())
        {
            fail("Concurrent set+addAll failed: " + errors.get(0).getMessage(), errors.get(0));
        }

        // Verify search still works after all the interleaving
        final VectorSearchResult<Document> result = index.search(randomVector(new Random(999), dimension), 5);
        assertNotNull(result);
    }


    // ==================== Concurrent remove+addAll interleaving stress ====================

    /**
     * Targeted stress test with embedded vectorizer: one thread does removeById(),
     * another does addAll().
     */
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testConcurrentRemoveAndAddAllInterleavingEmbedded()
    {
        final int dimension = 8;
        final int iterations = 80;

        final GigaMap<Document> gigaMap = createGigaMapWithVectorIndex(new EmbeddedDocumentVectorizer(), dimension);
        final VectorIndex<Document> index = getVectorIndex(gigaMap);

        // Seed enough entities for remove targets
        final Random seedRandom = new Random(42);
        for(int i = 0; i < 50; i++)
        {
            gigaMap.add(new Document("seed_" + i, randomVector(seedRandom, dimension)));
        }

        final AtomicBoolean hasError = new AtomicBoolean(false);
        final List<Throwable> errors = java.util.Collections.synchronizedList(new ArrayList<>());
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch doneLatch = new CountDownLatch(2);

        // Thread 1: removeById() loop
        final Thread removeThread = new Thread(() ->
        {
            try
            {
                startLatch.await();
                final Random random = new Random(100);
                for(int i = 0; i < iterations && !hasError.get(); i++)
                {
                    final long targetId = random.nextInt(50);
                    synchronized(gigaMap)
                    {
                        try
                        {
                            gigaMap.removeById(targetId);
                        }
                        catch(final Exception ignored) {}
                    }
                }
            }
            catch(final Throwable e)
            {
                errors.add(e);
                hasError.set(true);
            }
            finally
            {
                doneLatch.countDown();
            }
        });

        // Thread 2: addAll() loop
        final Thread addAllThread = new Thread(() ->
        {
            try
            {
                startLatch.await();
                final Random random = new Random(200);
                for(int i = 0; i < iterations && !hasError.get(); i++)
                {
                    final List<Document> batch = List.of(
                        new Document("batch_" + i, randomVector(random, dimension))
                    );
                    synchronized(gigaMap)
                    {
                        gigaMap.addAll(batch);
                    }
                }
            }
            catch(final Throwable e)
            {
                errors.add(e);
                hasError.set(true);
            }
            finally
            {
                doneLatch.countDown();
            }
        });

        removeThread.start();
        addAllThread.start();
        startLatch.countDown();

        try
        {
            assertTrue(doneLatch.await(25, TimeUnit.SECONDS), "Threads should complete within timeout");
        }
        catch(final InterruptedException e)
        {
            Thread.currentThread().interrupt();
            fail("Test was interrupted");
        }

        if(!errors.isEmpty())
        {
            fail("Concurrent remove+addAll (embedded) failed: " + errors.get(0).getMessage(), errors.get(0));
        }

        final VectorSearchResult<Document> result = index.search(randomVector(new Random(999), dimension), 5);
        assertNotNull(result);
    }

    /**
     * Targeted stress test: one thread does removeById() in a loop, another does addAll().
     */
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testConcurrentRemoveAndAddAllInterleaving()
    {
        final int dimension = 8;
        final int iterations = 80;

        final GigaMap<Document> gigaMap = createGigaMapWithVectorIndex(new ComputedDocumentVectorizer(), dimension);
        final VectorIndex<Document> index = getVectorIndex(gigaMap);

        // Seed enough entities for remove targets
        final Random seedRandom = new Random(42);
        for(int i = 0; i < 50; i++)
        {
            gigaMap.add(new Document("seed_" + i, randomVector(seedRandom, dimension)));
        }

        final AtomicBoolean hasError = new AtomicBoolean(false);
        final List<Throwable> errors = java.util.Collections.synchronizedList(new ArrayList<>());
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch doneLatch = new CountDownLatch(2);

        // Thread 1: removeById() loop
        final Thread removeThread = new Thread(() ->
        {
            try
            {
                startLatch.await();
                final Random random = new Random(100);
                for(int i = 0; i < iterations && !hasError.get(); i++)
                {
                    final long targetId = random.nextInt(50);
                    synchronized(gigaMap)
                    {
                        try
                        {
                            gigaMap.removeById(targetId);
                        }
                        catch(final Exception ignored) {}
                    }
                }
            }
            catch(final Throwable e)
            {
                errors.add(e);
                hasError.set(true);
            }
            finally
            {
                doneLatch.countDown();
            }
        });

        // Thread 2: addAll() loop
        final Thread addAllThread = new Thread(() ->
        {
            try
            {
                startLatch.await();
                final Random random = new Random(200);
                for(int i = 0; i < iterations && !hasError.get(); i++)
                {
                    final List<Document> batch = List.of(
                        new Document("batch_" + i, randomVector(random, dimension))
                    );
                    synchronized(gigaMap)
                    {
                        gigaMap.addAll(batch);
                    }
                }
            }
            catch(final Throwable e)
            {
                errors.add(e);
                hasError.set(true);
            }
            finally
            {
                doneLatch.countDown();
            }
        });

        removeThread.start();
        addAllThread.start();
        startLatch.countDown();

        try
        {
            assertTrue(doneLatch.await(25, TimeUnit.SECONDS), "Threads should complete within timeout");
        }
        catch(final InterruptedException e)
        {
            Thread.currentThread().interrupt();
            fail("Test was interrupted");
        }

        if(!errors.isEmpty())
        {
            fail("Concurrent remove+addAll failed: " + errors.get(0).getMessage(), errors.get(0));
        }

        // Verify search works
        final VectorSearchResult<Document> result = index.search(randomVector(new Random(999), dimension), 5);
        assertNotNull(result);
    }


    // ==================== Concurrent search during mutations (embedded) ====================

    /**
     * Concurrent search while set/addAll/remove happen with embedded vectorizer.
     * Exercises the EntityBackedVectorValues.getVector() → GigaMap.get() path
     * under concurrent search load — the exact code path that caused the original deadlock.
     */
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testConcurrentSearchDuringMutationsEmbedded()
    {
        final int dimension    = 16;
        final int seedCount    = 30;
        final int threadCount  = 4;
        final int opsPerThread = 80;

        final GigaMap<Document> gigaMap = createGigaMapWithVectorIndex(new EmbeddedDocumentVectorizer(), dimension);
        final VectorIndex<Document> index = getVectorIndex(gigaMap);

        // Seed
        final Random seedRandom = new Random(42);
        for(int i = 0; i < seedCount; i++)
        {
            gigaMap.add(new Document("seed_" + i, randomVector(seedRandom, dimension)));
        }

        final AtomicBoolean hasError = new AtomicBoolean(false);
        final List<Throwable> errors = java.util.Collections.synchronizedList(new ArrayList<>());
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch doneLatch = new CountDownLatch(threadCount);

        final ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        // 2 mutation threads + 2 search threads
        for(int t = 0; t < threadCount; t++)
        {
            final int threadId = t;
            final boolean isMutator = t < 2;
            executor.submit(() ->
            {
                try
                {
                    startLatch.await();
                    final Random random = new Random(3000 + threadId);

                    for(int op = 0; op < opsPerThread && !hasError.get(); op++)
                    {
                        try
                        {
                            if(isMutator)
                            {
                                final int action = random.nextInt(100);
                                if(action < 30)
                                {
                                    // set
                                    final long targetId = random.nextInt(seedCount);
                                    synchronized(gigaMap)
                                    {
                                        try
                                        {
                                            gigaMap.set(targetId, new Document(
                                                "set_t" + threadId + "_" + op,
                                                randomVector(random, dimension)
                                            ));
                                        }
                                        catch(final Exception ignored) {}
                                    }
                                }
                                else if(action < 50)
                                {
                                    // removeById
                                    final long targetId = random.nextInt(seedCount);
                                    synchronized(gigaMap)
                                    {
                                        try
                                        {
                                            gigaMap.removeById(targetId);
                                        }
                                        catch(final Exception ignored) {}
                                    }
                                }
                                else if(action < 75)
                                {
                                    // addAll
                                    final List<Document> batch = List.of(
                                        new Document("batch_t" + threadId + "_" + op,
                                            randomVector(random, dimension))
                                    );
                                    synchronized(gigaMap)
                                    {
                                        gigaMap.addAll(batch);
                                    }
                                }
                                else
                                {
                                    // single add
                                    synchronized(gigaMap)
                                    {
                                        gigaMap.add(new Document(
                                            "add_t" + threadId + "_" + op,
                                            randomVector(random, dimension)
                                        ));
                                    }
                                }
                            }
                            else
                            {
                                // Search thread — no synchronization needed, exercises
                                // concurrent read on the HNSW graph and GigaMap.get()
                                final VectorSearchResult<Document> result = index.search(
                                    randomVector(random, dimension), 5
                                );
                                assertNotNull(result);
                            }
                        }
                        catch(final Throwable e)
                        {
                            errors.add(e);
                            hasError.set(true);
                        }
                    }
                }
                catch(final InterruptedException e)
                {
                    Thread.currentThread().interrupt();
                }
                finally
                {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();

        try
        {
            assertTrue(doneLatch.await(25, TimeUnit.SECONDS), "Threads should complete within timeout");
        }
        catch(final InterruptedException e)
        {
            Thread.currentThread().interrupt();
            fail("Test was interrupted");
        }
        finally
        {
            executor.shutdownNow();
        }

        if(!errors.isEmpty())
        {
            fail("Concurrent search during mutations (embedded) failed with "
                + errors.size() + " error(s): " + errors.get(0).getMessage(), errors.get(0));
        }
    }


    // ==================== Single add() interleaving ====================

    /**
     * set() followed by single add() without store().
     * Single add() uses the same executeOrDeferBuilderOp path as addAll().
     */
    @Test
    void testSetThenSingleAddWithoutStore()
    {
        final GigaMap<Document> gigaMap = createGigaMapWithVectorIndex(new EmbeddedDocumentVectorizer(), 3);
        final VectorIndex<Document> index = getVectorIndex(gigaMap);

        gigaMap.add(new Document("doc0", new float[]{1.0f, 0.0f, 0.0f}));
        gigaMap.add(new Document("doc1", new float[]{0.0f, 1.0f, 0.0f}));

        // set then single add without store
        gigaMap.set(0, new Document("doc0-updated", new float[]{0.5f, 0.5f, 0.0f}));
        gigaMap.add(new Document("doc2", new float[]{0.0f, 0.0f, 1.0f}));

        assertEquals(3, gigaMap.size());

        final VectorSearchResult<Document> result = index.search(new float[]{0.0f, 0.0f, 1.0f}, 1);
        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals("doc2", result.toList().get(0).entity().content());
    }

    /**
     * removeById() followed by single add() without store().
     */
    @Test
    void testRemoveByIdThenSingleAddWithoutStore()
    {
        final GigaMap<Document> gigaMap = createGigaMapWithVectorIndex(new EmbeddedDocumentVectorizer(), 3);
        final VectorIndex<Document> index = getVectorIndex(gigaMap);

        gigaMap.add(new Document("doc0", new float[]{1.0f, 0.0f, 0.0f}));
        gigaMap.add(new Document("doc1", new float[]{0.0f, 1.0f, 0.0f}));

        gigaMap.removeById(0);
        gigaMap.add(new Document("doc2", new float[]{0.0f, 0.0f, 1.0f}));

        assertEquals(2, gigaMap.size());

        final VectorSearchResult<Document> result = index.search(new float[]{1.0f, 0.0f, 0.0f}, 2);
        assertNotNull(result);
        for(final VectorSearchResult.Entry<Document> entry : result)
        {
            assertNotEquals("doc0", entry.entity().content(), "Removed document should not appear");
        }
    }

    /**
     * Alternating single add() and set() calls without any store().
     */
    @Test
    void testAlternatingSingleAddAndSet()
    {
        final GigaMap<Document> gigaMap = createGigaMapWithVectorIndex(new EmbeddedDocumentVectorizer(), 3);
        final VectorIndex<Document> index = getVectorIndex(gigaMap);
        final Random random = new Random(42);

        // Seed
        gigaMap.add(new Document("seed0", randomVector(random, 3)));

        for(int i = 0; i < 20; i++)
        {
            // Alternate: add, set, add, set, ...
            if(i % 2 == 0)
            {
                gigaMap.add(new Document("add_" + i, randomVector(random, 3)));
            }
            else
            {
                gigaMap.set(0, new Document("set_" + i, randomVector(random, 3)));
            }
        }

        // 1 seed + 10 adds = 11 total
        assertEquals(11, gigaMap.size());

        final VectorSearchResult<Document> result = index.search(randomVector(random, 3), 5);
        assertNotNull(result);
        assertEquals(5, result.size());
    }


    // ==================== Verification: store() between operations still works ====================

    /**
     * Ensures backward compatibility: store() between set() and addAll() still works fine.
     * Uses EmbeddedStorage since store() requires a persistence context.
     */
    @Test
    void testSetStoreAddAllStillWorks(@TempDir final Path tempDir)
    {
        try(final var storage = org.eclipse.store.storage.embedded.types.EmbeddedStorage.start(tempDir))
        {
            final GigaMap<Document> gigaMap = GigaMap.New();
            storage.setRoot(gigaMap);

            final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());
            final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
                .dimension(3)
                .similarityFunction(VectorSimilarityFunction.COSINE)
                .build();
            final VectorIndex<Document> index = vectorIndices.add(
                "embeddings", config, new EmbeddedDocumentVectorizer()
            );

            gigaMap.add(new Document("doc0", new float[]{1.0f, 0.0f, 0.0f}));
            gigaMap.add(new Document("doc1", new float[]{0.0f, 1.0f, 0.0f}));

            gigaMap.set(0, new Document("doc0-updated", new float[]{0.5f, 0.5f, 0.0f}));
            storage.storeRoot(); // explicit store between operations
            gigaMap.addAll(List.of(
                new Document("doc2", new float[]{0.0f, 0.0f, 1.0f})
            ));

            assertEquals(3, gigaMap.size());

            final VectorSearchResult<Document> result = index.search(new float[]{0.0f, 0.0f, 1.0f}, 1);
            assertNotNull(result);
            assertFalse(result.isEmpty());
            assertEquals("doc2", result.toList().get(0).entity().content());
        }
    }


    // ==================== Large batch interleaving ====================

    /**
     * Larger-scale interleaving test to exercise the HNSW graph more thoroughly.
     */
    @Test
    void testLargeScaleInterleaving()
    {
        final int dimension = 32;
        final Random random = new Random(42);

        final GigaMap<Document> gigaMap = createGigaMapWithVectorIndex(new EmbeddedDocumentVectorizer(), dimension);
        final VectorIndex<Document> index = getVectorIndex(gigaMap);

        // Seed 50 entities
        for(int i = 0; i < 50; i++)
        {
            gigaMap.add(new Document("seed_" + i, randomVector(random, dimension)));
        }

        // Interleave: 20 rounds of set + addAll(10) + removeById
        for(int round = 0; round < 20; round++)
        {
            // Update some entities
            for(int j = 0; j < 3; j++)
            {
                final long entityId = random.nextInt(50);
                try
                {
                    gigaMap.set(entityId, new Document("set_r" + round + "_" + j, randomVector(random, dimension)));
                }
                catch(final Exception ignored)
                {
                    // May have been removed
                }
            }

            // Batch add
            final List<Document> batch = new ArrayList<>();
            for(int j = 0; j < 10; j++)
            {
                batch.add(new Document("batch_r" + round + "_" + j, randomVector(random, dimension)));
            }
            gigaMap.addAll(batch);

            // Remove some
            for(int j = 0; j < 2; j++)
            {
                final long entityId = random.nextInt(50);
                try
                {
                    gigaMap.removeById(entityId);
                }
                catch(final Exception ignored) {}
            }
        }

        // Search should work on the resulting graph
        final VectorSearchResult<Document> result = index.search(randomVector(random, dimension), 10);
        assertNotNull(result);
        assertTrue(result.size() > 0, "Should have searchable entities");
    }
}
