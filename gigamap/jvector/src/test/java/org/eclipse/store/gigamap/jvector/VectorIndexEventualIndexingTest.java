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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.TimeUnit;

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

        final VectorIndex<Document> index = vectorIndices.add(
            "embeddings", config, new EmbeddedDocumentVectorizer()
        );

        try
        {
            gigaMap.add(new Document("doc1", new float[]{1.0f, 0.0f, 0.0f}));
            gigaMap.add(new Document("doc2", new float[]{0.0f, 1.0f, 0.0f}));
            gigaMap.add(new Document("doc3", new float[]{0.0f, 0.0f, 1.0f}));

            // Drain queue to ensure all graph operations are applied
            final VectorIndex.Default<Document> defaultIndex = (VectorIndex.Default<Document>)index;
            defaultIndex.indexingManager.drainQueue();

            // Search should find all 3 documents
            final VectorSearchResult<Document> result = index.search(new float[]{1.0f, 0.0f, 0.0f}, 3);
            assertEquals(3, result.size());

            // The closest match should be doc1
            assertEquals("doc1", result.toList().get(0).entity().content());
        }
        finally
        {
            index.close();
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

        final VectorIndex<Document> index = vectorIndices.add(
            "embeddings", config, new ComputedDocumentVectorizer()
        );

        try
        {
            gigaMap.add(new Document("doc1", new float[]{1.0f, 0.0f, 0.0f}));
            gigaMap.add(new Document("doc2", new float[]{0.0f, 1.0f, 0.0f}));
            gigaMap.add(new Document("doc3", new float[]{0.0f, 0.0f, 1.0f}));

            final VectorIndex.Default<Document> defaultIndex = (VectorIndex.Default<Document>)index;
            defaultIndex.indexingManager.drainQueue();

            final VectorSearchResult<Document> result = index.search(new float[]{1.0f, 0.0f, 0.0f}, 3);
            assertEquals(3, result.size());
            assertEquals("doc1", result.toList().get(0).entity().content());
        }
        finally
        {
            index.close();
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

        final VectorIndex<Document> index = vectorIndices.add(
            "embeddings", config, new ComputedDocumentVectorizer()
        );

        try
        {
            for(int i = 0; i < vectorCount; i++)
            {
                gigaMap.add(new Document("doc_" + i, randomVector(random, dimension)));
            }

            final VectorIndex.Default<Document> defaultIndex = (VectorIndex.Default<Document>)index;
            defaultIndex.indexingManager.drainQueue();

            final VectorSearchResult<Document> result = index.search(
                randomVector(new Random(99), dimension), 10
            );
            assertEquals(10, result.size());
        }
        finally
        {
            index.close();
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

        final VectorIndex<Document> index = vectorIndices.add(
            "embeddings", config, new EmbeddedDocumentVectorizer()
        );

        try
        {
            final Document doc1 = new Document("doc1", new float[]{1.0f, 0.0f, 0.0f});
            final Document doc2 = new Document("doc2", new float[]{0.0f, 1.0f, 0.0f});
            gigaMap.add(doc1);
            gigaMap.add(doc2);

            final VectorIndex.Default<Document> defaultIndex = (VectorIndex.Default<Document>)index;
            defaultIndex.indexingManager.drainQueue();

            // Update doc1's vector to be close to doc2
            final Document updatedDoc1 = new Document("doc1_updated", new float[]{0.1f, 0.9f, 0.0f});
            gigaMap.set(0L, updatedDoc1);

            defaultIndex.indexingManager.drainQueue();

            // Search for doc2-like vector: updated doc1 should now be close
            final VectorSearchResult<Document> result = index.search(new float[]{0.0f, 1.0f, 0.0f}, 2);
            assertEquals(2, result.size());
        }
        finally
        {
            index.close();
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

        final VectorIndex<Document> index = vectorIndices.add(
            "embeddings", config, new EmbeddedDocumentVectorizer()
        );

        try
        {
            gigaMap.add(new Document("doc1", new float[]{1.0f, 0.0f, 0.0f}));
            gigaMap.add(new Document("doc2", new float[]{0.0f, 1.0f, 0.0f}));
            gigaMap.add(new Document("doc3", new float[]{0.0f, 0.0f, 1.0f}));

            final VectorIndex.Default<Document> defaultIndex = (VectorIndex.Default<Document>)index;
            defaultIndex.indexingManager.drainQueue();

            // Remove doc1
            gigaMap.removeById(0L);

            defaultIndex.indexingManager.drainQueue();

            // Search should only return 2 documents
            final VectorSearchResult<Document> result = index.search(new float[]{1.0f, 0.0f, 0.0f}, 3);
            assertEquals(2, result.size());
        }
        finally
        {
            index.close();
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

        final VectorIndex<Document> index = vectorIndices.add(
            "embeddings", config, new ComputedDocumentVectorizer()
        );

        try
        {
            for(int i = 0; i < vectorCount; i++)
            {
                gigaMap.add(new Document("doc_" + i, randomVector(random, dimension)));
            }

            final VectorIndex.Default<Document> defaultIndex = (VectorIndex.Default<Document>)index;
            defaultIndex.indexingManager.drainQueue();

            // Remove first 10 entities
            for(int i = 0; i < 10; i++)
            {
                gigaMap.removeById(i);
            }

            defaultIndex.indexingManager.drainQueue();

            // Should have 40 remaining
            final VectorSearchResult<Document> result = index.search(
                randomVector(new Random(99), dimension), 50
            );
            assertEquals(40, result.size());
        }
        finally
        {
            index.close();
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

        final VectorIndex<Document> index = vectorIndices.add(
            "embeddings", config, new EmbeddedDocumentVectorizer()
        );

        try
        {
            gigaMap.add(new Document("doc1", new float[]{1.0f, 0.0f, 0.0f}));
            gigaMap.add(new Document("doc2", new float[]{0.0f, 1.0f, 0.0f}));

            final VectorIndex.Default<Document> defaultIndex = (VectorIndex.Default<Document>)index;
            defaultIndex.indexingManager.drainQueue();

            // RemoveAll — this discards pending operations and shuts down manager
            gigaMap.removeAll();

            // Index should be empty
            final VectorSearchResult<Document> result = index.search(new float[]{1.0f, 0.0f, 0.0f}, 10);
            assertEquals(0, result.size());

            // Add new data after removeAll — indexing manager is recreated by initializeIndex
            gigaMap.add(new Document("new_doc", new float[]{1.0f, 0.0f, 0.0f}));

            // Drain the new indexing manager
            defaultIndex.indexingManager.drainQueue();

            final VectorSearchResult<Document> result2 = index.search(new float[]{1.0f, 0.0f, 0.0f}, 10);
            assertEquals(1, result2.size());
        }
        finally
        {
            index.close();
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

        final VectorIndex<Document> index = vectorIndices.add(
            "embeddings", config, new ComputedDocumentVectorizer()
        );

        try
        {
            for(int i = 0; i < 50; i++)
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
        finally
        {
            index.close();
        }
    }

    // ==================== PersistToDisk Drains Queue Tests ====================

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testPersistToDiskDrainsQueueBeforeWrite(@TempDir final Path tempDir)
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

        final VectorIndex<Document> index = vectorIndices.add(
            "embeddings", config, new ComputedDocumentVectorizer()
        );

        try
        {
            for(int i = 0; i < 50; i++)
            {
                gigaMap.add(new Document("doc_" + i, randomVector(random, dimension)));
            }

            // PersistToDisk should drain the queue first
            index.persistToDisk();

            // Verify files were created
            assertTrue(Files.exists(indexDir.resolve("embeddings.graph")));
            assertTrue(Files.exists(indexDir.resolve("embeddings.meta")));

            // Search should work after persist
            final VectorSearchResult<Document> result = index.search(
                randomVector(new Random(99), dimension), 10
            );
            assertEquals(10, result.size());
        }
        finally
        {
            index.close();
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

        final VectorIndex<Document> index = vectorIndices.add(
            "embeddings", config, new EmbeddedDocumentVectorizer()
        );

        try
        {
            final VectorIndex.Default<Document> defaultIndex = (VectorIndex.Default<Document>)index;

            // Initially empty
            assertEquals(0, defaultIndex.indexingManager.getPendingCount());

            // After drain, count should be 0
            gigaMap.add(new Document("doc1", new float[]{1.0f, 0.0f, 0.0f}));
            gigaMap.add(new Document("doc2", new float[]{0.0f, 1.0f, 0.0f}));

            defaultIndex.indexingManager.drainQueue();

            assertEquals(0, defaultIndex.indexingManager.getPendingCount());
        }
        finally
        {
            index.close();
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

        final VectorIndex<Document> index = vectorIndices.add(
            "embeddings", config, new ComputedDocumentVectorizer()
        );

        try
        {
            // Add random vectors
            for(int i = 0; i < vectorCount; i++)
            {
                gigaMap.add(new Document("doc_" + i, randomVector(random, dimension)));
            }

            final VectorIndex.Default<Document> defaultIndex = (VectorIndex.Default<Document>)index;
            defaultIndex.indexingManager.drainQueue();

            // Search should return correct number of results
            final VectorSearchResult<Document> result = index.search(
                randomVector(new Random(99), dimension), 10
            );
            assertEquals(10, result.size());

            // All results should have valid scores
            for(final VectorSearchResult.Entry<Document> entry : result)
            {
                assertTrue(entry.score() > 0, "Score should be positive");
                assertNotNull(entry.entity());
            }
        }
        finally
        {
            index.close();
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

        final VectorIndex<Document> index = vectorIndices.add(
            "embeddings", config, new ComputedDocumentVectorizer()
        );

        try
        {
            for(int i = 0; i < vectorCount; i++)
            {
                gigaMap.add(new Document("doc_" + i, randomVector(random, dimension)));
            }

            // Persist triggers drain
            index.persistToDisk();

            assertTrue(Files.exists(indexDir.resolve("embeddings.graph")));

            final VectorSearchResult<Document> result = index.search(
                randomVector(new Random(99), dimension), 10
            );
            assertEquals(10, result.size());
        }
        finally
        {
            index.close();
        }
    }

    // ==================== Background Persistence + Eventual Indexing ====================

    @Test
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
            .eventualIndexing(true)
            .build();

        final VectorIndex<Document> index = vectorIndices.add(
            "embeddings", config, new ComputedDocumentVectorizer()
        );

        try
        {
            for(int i = 0; i < 20; i++)
            {
                gigaMap.add(new Document("doc_" + i, randomVector(random, dimension)));
            }

            // Wait for background persistence (which should drain first)
            Thread.sleep(1500);

            assertTrue(Files.exists(indexDir.resolve("embeddings.graph")));
        }
        finally
        {
            index.close();
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

        final VectorIndex<Document> index = vectorIndices.add(
            "embeddings", config, new EmbeddedDocumentVectorizer()
        );

        final VectorIndex.Default<Document> defaultIndex = (VectorIndex.Default<Document>)index;

        // Indexing manager should be null when eventualIndexing is false
        assertNull(defaultIndex.indexingManager);

        // Synchronous indexing should still work
        gigaMap.add(new Document("doc1", new float[]{1.0f, 0.0f, 0.0f}));

        final VectorSearchResult<Document> result = index.search(new float[]{1.0f, 0.0f, 0.0f}, 1);
        assertEquals(1, result.size());
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

        final VectorIndex<Document> index = vectorIndices.add(
            "embeddings", config, new EmbeddedDocumentVectorizer()
        );

        try
        {
            final VectorIndex.Default<Document> defaultIndex = (VectorIndex.Default<Document>)index;

            // Add 3 documents
            gigaMap.add(new Document("doc1", new float[]{1.0f, 0.0f, 0.0f}));
            gigaMap.add(new Document("doc2", new float[]{0.0f, 1.0f, 0.0f}));
            gigaMap.add(new Document("doc3", new float[]{0.0f, 0.0f, 1.0f}));

            defaultIndex.indexingManager.drainQueue();

            // Update doc2
            gigaMap.set(1L, new Document("doc2_updated", new float[]{0.9f, 0.1f, 0.0f}));

            defaultIndex.indexingManager.drainQueue();

            // Remove doc3
            gigaMap.removeById(2L);

            defaultIndex.indexingManager.drainQueue();

            // Search: should find 2 documents
            final VectorSearchResult<Document> result = index.search(new float[]{1.0f, 0.0f, 0.0f}, 3);
            assertEquals(2, result.size());

            // doc1 should be closest to [1,0,0], followed by updated doc2 [0.9,0.1,0]
            assertEquals("doc1", result.toList().get(0).entity().content());
            assertEquals("doc2_updated", result.toList().get(1).entity().content());
        }
        finally
        {
            index.close();
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

        final VectorIndex<Document> index = vectorIndices.add(
            "embeddings", config, new ComputedDocumentVectorizer()
        );

        try
        {
            for(int i = 0; i < 50; i++)
            {
                gigaMap.add(new Document("doc_" + i, randomVector(random, dimension)));
            }

            final VectorIndex.Default<Document> defaultIndex = (VectorIndex.Default<Document>)index;
            defaultIndex.indexingManager.drainQueue();

            // Wait for background optimization to run
            Thread.sleep(800);

            // Optimization should have run at least once
            assertTrue(defaultIndex.optimizationManager.getOptimizationCount() >= 1);

            // Search should still work
            final VectorSearchResult<Document> result = index.search(
                randomVector(new Random(99), dimension), 10
            );
            assertEquals(10, result.size());
        }
        finally
        {
            index.close();
        }
    }
}
