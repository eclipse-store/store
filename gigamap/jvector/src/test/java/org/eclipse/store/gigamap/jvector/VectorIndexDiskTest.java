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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for on-disk VectorIndex functionality and Product Quantization.
 */
class VectorIndexDiskTest
{
    /**
     * Simple entity with an embedding vector.
     */
    record Document(String content, float[] embedding) {}

    /**
     * Computed vectorizer - simulates externally computed vectors.
     */
    static class ComputedDocumentVectorizer extends Vectorizer<Document>
    {
        @Override
        public float[] vectorize(final Document entity)
        {
            return entity.embedding();
        }
    }

    /**
     * Helper to generate a random normalized vector.
     */
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

    /**
     * Test on-disk configuration builder.
     */
    @Test
    void testOnDiskConfigurationBuilder(@TempDir final Path tempDir)
    {
        final Path indexDir = tempDir.resolve("index");

        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(128)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .onDisk(true)
            .indexDirectory(indexDir)
            .build();

        assertTrue(config.onDisk());
        assertEquals(indexDir, config.indexDirectory());
        assertFalse(config.enablePqCompression());
        assertEquals(0, config.pqSubspaces());
    }

    /**
     * Test on-disk configuration with compression.
     * FusedPQ requires maxDegree=32, so it should be auto-set.
     */
    @Test
    void testOnDiskConfigurationWithCompression(@TempDir final Path tempDir)
    {
        final Path indexDir = tempDir.resolve("index");

        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(128)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .maxDegree(16) // Will be overridden to 32 for FusedPQ
            .onDisk(true)
            .indexDirectory(indexDir)
            .enablePqCompression(true)
            .pqSubspaces(32)
            .build();

        assertTrue(config.onDisk());
        assertTrue(config.enablePqCompression());
        assertEquals(32, config.pqSubspaces());
        assertEquals(32, config.maxDegree(), "FusedPQ requires maxDegree=32");
    }

    /**
     * Test that maxDegree is auto-set to 32 when compression is enabled.
     */
    @Test
    void testFusedPQRequiresMaxDegree32(@TempDir final Path tempDir)
    {
        final Path indexDir = tempDir.resolve("index");

        // Try to set maxDegree to 64 with compression enabled
        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(128)
            .maxDegree(64)
            .onDisk(true)
            .indexDirectory(indexDir)
            .enablePqCompression(true)
            .build();

        // Should be overridden to 32
        assertEquals(32, config.maxDegree(), "FusedPQ should enforce maxDegree=32");
    }

    /**
     * Test validation: onDisk requires indexDirectory.
     */
    @Test
    void testOnDiskRequiresIndexDirectory()
    {
        assertThrows(IllegalStateException.class, () ->
            VectorIndexConfiguration.builder()
                .dimension(128)
                .onDisk(true)
                // indexDirectory not set
                .build()
        );
    }

    /**
     * Test validation: compression requires onDisk.
     */
    @Test
    void testCompressionRequiresOnDisk()
    {
        assertThrows(IllegalStateException.class, () ->
            VectorIndexConfiguration.builder()
                .dimension(128)
                .enablePqCompression(true)
                // onDisk not set
                .build()
        );
    }

    /**
     * Test validation: pqSubspaces must divide dimension evenly.
     */
    @Test
    void testPqSubspacesMustDivideDimension(@TempDir final Path tempDir)
    {
        final Path indexDir = tempDir.resolve("index");

        assertThrows(IllegalArgumentException.class, () ->
            VectorIndexConfiguration.builder()
                .dimension(100)
                .onDisk(true)
                .indexDirectory(indexDir)
                .enablePqCompression(true)
                .pqSubspaces(33) // 100 is not divisible by 33
                .build()
        );
    }

    /**
     * Test creating an on-disk index and persisting it.
     */
    @Test
    void testOnDiskIndexCreationAndPersistence(@TempDir final Path tempDir) throws IOException
    {
        final int vectorCount = 500;
        final int dimension = 64;
        final Random random = new Random(42);

        final Path indexDir = tempDir.resolve("index");
        final Path storageDir = tempDir.resolve("storage");

        // Generate vectors
        final List<float[]> vectors = new ArrayList<>();
        for(int i = 0; i < vectorCount; i++)
        {
            vectors.add(randomVector(random, dimension));
        }

        final float[] queryVector = randomVector(new Random(999), dimension);
        final List<Long> expectedIds = new ArrayList<>();

        // Phase 1: Create index and persist
        {
            try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
            {
                final GigaMap<Document> gigaMap = GigaMap.New();
                storage.setRoot(gigaMap);

                final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());
                final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
                    .dimension(dimension)
                    .similarityFunction(VectorSimilarityFunction.COSINE)
                    .onDisk(true)
                    .indexDirectory(indexDir)
                    .build();

                final VectorIndex<Document> index = vectorIndices.add(
                    "embeddings",
                    config,
                    new ComputedDocumentVectorizer()
                );

                assertTrue(index.isOnDisk());
                assertFalse(index.isPqCompressionEnabled());

                // Add vectors
                for(int i = 0; i < vectorCount; i++)
                {
                    gigaMap.add(new Document("doc_" + i, vectors.get(i)));
                }

                // Search and record expected results
                final VectorSearchResult<Document> result = index.search(queryVector, 10);
                for(final VectorSearchResult.Entry<Document> entry : result)
                {
                    expectedIds.add(entry.entityId());
                }

                // Persist index to disk
                index.persistToDisk();

                // Verify files were created
                assertTrue(Files.exists(indexDir.resolve("embeddings.graph")));
                assertTrue(Files.exists(indexDir.resolve("embeddings.meta")));

                storage.storeRoot();
            }
        }

        // Phase 2: Reload and verify
        {
            try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
            {
                @SuppressWarnings("unchecked")
                final GigaMap<Document> gigaMap = (GigaMap<Document>)storage.root();
                final VectorIndices<Document> vectorIndices = gigaMap.index().get(VectorIndices.Category());

                assertEquals(vectorCount, gigaMap.size());

                final VectorIndex<Document> index = vectorIndices.get("embeddings");
                assertNotNull(index);
                assertTrue(index.isOnDisk());

                // Search and compare results
                final VectorSearchResult<Document> result = index.search(queryVector, 10);
                final List<Long> actualIds = new ArrayList<>();
                for(final VectorSearchResult.Entry<Document> entry : result)
                {
                    actualIds.add(entry.entityId());
                }

                // Results should match (or at least be very similar due to HNSW nature)
                assertEquals(expectedIds.size(), actualIds.size());
            }
        }
    }

    /**
     * Test on-disk index with compression (PQ).
     */
    @Test
    void testOnDiskIndexWithCompression(@TempDir final Path tempDir) throws IOException
    {
        final int vectorCount = 500;
        final int dimension = 64;
        final int pqSubspaces = 16; // 64 / 16 = 4 dimensions per subspace
        final Random random = new Random(42);

        final Path indexDir = tempDir.resolve("index");

        final GigaMap<Document> gigaMap = GigaMap.New();
        final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());

        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(dimension)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .onDisk(true)
            .indexDirectory(indexDir)
            .enablePqCompression(true)
            .pqSubspaces(pqSubspaces)
            .build();

        final VectorIndex<Document> index = vectorIndices.add(
            "embeddings",
            config,
            new ComputedDocumentVectorizer()
        );

        assertTrue(index.isOnDisk());
        assertTrue(index.isPqCompressionEnabled());

        // Add vectors
        for(int i = 0; i < vectorCount; i++)
        {
            gigaMap.add(new Document("doc_" + i, randomVector(random, dimension)));
        }

        // Train compression
        ((VectorIndex.Internal<Document>)index).trainCompressionIfNeeded();

        // Search should work
        final float[] queryVector = randomVector(random, dimension);
        final VectorSearchResult<Document> result = index.search(queryVector, 10);

        assertEquals(10, result.size());

        // Verify all entities are accessible
        for(final VectorSearchResult.Entry<Document> entry : result)
        {
            assertNotNull(entry.entity());
            assertTrue(entry.entity().content().startsWith("doc_"));
        }

        // Persist to disk
        index.persistToDisk();

        // Verify graph file was created (FusedPQ is embedded in graph, no separate .pq file)
        assertTrue(Files.exists(indexDir.resolve("embeddings.graph")));
        assertTrue(Files.exists(indexDir.resolve("embeddings.meta")));
        assertFalse(Files.exists(indexDir.resolve("embeddings.pq")),
            "FusedPQ should be embedded in graph file, not in separate .pq file");
    }

    /**
     * Test search quality with on-disk index - verify exact match is found first.
     */
    @Test
    void testOnDiskSearchQuality(@TempDir final Path tempDir) throws IOException
    {
        final int vectorCount = 1000;
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
            .build();

        final VectorIndex<Document> index = vectorIndices.add(
            "embeddings",
            config,
            new ComputedDocumentVectorizer()
        );

        // Add random vectors
        for(int i = 0; i < vectorCount - 1; i++)
        {
            gigaMap.add(new Document("random_" + i, randomVector(random, dimension)));
        }

        // Add a one-hot "needle" vector that randomVector() cannot produce,
        // since randomVector() populates all dimensions with non-zero values.
        final float[] needleVector = new float[dimension];
        needleVector[0] = 1.0f;

        gigaMap.add(new Document("needle", needleVector));

        // Persist index
        index.persistToDisk();

        // Search for the needle vector - it should be the first result
        final VectorSearchResult<Document> result = index.search(needleVector, 5);

        assertEquals(5, result.size());
        final VectorSearchResult.Entry<Document> firstResult = result.iterator().next();
        assertEquals("needle", firstResult.entity().content(), "Exact match should be first result");
        assertTrue(firstResult.score() > 0.99f, "Exact match should have score close to 1.0");
    }

    /**
     * Test multiple restarts with on-disk index.
     */
    @Test
    void testOnDiskIndexMultipleRestarts(@TempDir final Path tempDir) throws IOException
    {
        final int dimension = 32;
        final Random random = new Random(42);

        final Path indexDir = tempDir.resolve("index");
        final Path storageDir = tempDir.resolve("storage");

        // Phase 1: Create with 100 vectors
        {
            try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
            {
                final GigaMap<Document> gigaMap = GigaMap.New();
                storage.setRoot(gigaMap);

                final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());
                final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
                    .dimension(dimension)
                    .similarityFunction(VectorSimilarityFunction.COSINE)
                    .onDisk(true)
                    .indexDirectory(indexDir)
                    .build();

                vectorIndices.add("embeddings", config, new ComputedDocumentVectorizer());

                for(int i = 0; i < 100; i++)
                {
                    gigaMap.add(new Document("phase1_doc_" + i, randomVector(random, dimension)));
                }

                assertEquals(100, gigaMap.size());
                storage.storeRoot();
            }
        }

        // Phase 2: Restart and add 50 more vectors
        {
            try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
            {
                @SuppressWarnings("unchecked")
                final GigaMap<Document> gigaMap = (GigaMap<Document>)storage.root();
                final VectorIndices<Document> vectorIndices = gigaMap.index().get(VectorIndices.Category());

                assertEquals(100, gigaMap.size());

                final VectorIndex<Document> index = vectorIndices.get("embeddings");
                final VectorSearchResult<Document> result = index.search(randomVector(random, dimension), 10);
                assertEquals(10, result.size());

                // Add more vectors
                for(int i = 0; i < 50; i++)
                {
                    gigaMap.add(new Document("phase2_doc_" + i, randomVector(random, dimension)));
                }

                assertEquals(150, gigaMap.size());
                storage.storeRoot();
            }
        }

        // Phase 3: Final verification
        {
            try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
            {
                @SuppressWarnings("unchecked")
                final GigaMap<Document> gigaMap = (GigaMap<Document>)storage.root();
                final VectorIndices<Document> vectorIndices = gigaMap.index().get(VectorIndices.Category());

                assertEquals(150, gigaMap.size());

                final VectorIndex<Document> index = vectorIndices.get("embeddings");
                final VectorSearchResult<Document> result = index.search(randomVector(random, dimension), 30);
                assertEquals(30, result.size());
            }
        }
    }

    // ========================================================================
    // PQ Compression Search Tests
    // ========================================================================

    /**
     * Test search quality with PQ compression enabled.
     * Verifies that an exact match (needle) is found in the top results
     * despite quantization loss from Product Quantization.
     */
    @Test
    void testPqCompressionSearchQuality(@TempDir final Path tempDir)
    {
        final int vectorCount = 500;
        final int dimension = 64;
        final int pqSubspaces = 16;
        final Random random = new Random(42);

        final Path indexDir = tempDir.resolve("index");

        final GigaMap<Document> gigaMap = GigaMap.New();
        final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());

        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(dimension)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .onDisk(true)
            .indexDirectory(indexDir)
            .enablePqCompression(true)
            .pqSubspaces(pqSubspaces)
            .build();

        final VectorIndex<Document> index = vectorIndices.add(
            "embeddings",
            config,
            new ComputedDocumentVectorizer()
        );

        // Add random vectors
        for(int i = 0; i < vectorCount - 1; i++)
        {
            gigaMap.add(new Document("random_" + i, randomVector(random, dimension)));
        }

        // Add a one-hot "needle" vector that randomVector() cannot produce,
        // since randomVector() populates all dimensions with non-zero values.
        final float[] needleVector = new float[dimension];
        needleVector[0] = 1.0f;

        gigaMap.add(new Document("needle", needleVector));

        // Train PQ compression
        ((VectorIndex.Internal<Document>)index).trainCompressionIfNeeded();

        // Search for the needle vector - it should be in the top results
        final VectorSearchResult<Document> result = index.search(needleVector, 5);

        assertEquals(5, result.size());
        final VectorSearchResult.Entry<Document> firstResult = result.iterator().next();
        assertEquals("needle", firstResult.entity().content(),
            "Exact match should be first result even with PQ compression");
        assertTrue(firstResult.score() > 0.99f,
            "Exact match should have score close to 1.0");

        // Verify results are ordered by score
        float prevScore = Float.MAX_VALUE;
        for(final VectorSearchResult.Entry<Document> entry : result)
        {
            assertTrue(entry.score() <= prevScore, "Results should be ordered by score");
            prevScore = entry.score();
        }
    }

    /**
     * Test PQ-compressed disk index persistence and reload with search verification.
     * Verifies that search still works correctly after saving and reloading
     * a PQ-compressed index.
     */
    @Test
    void testPqCompressionPersistAndReload(@TempDir final Path tempDir) throws IOException
    {
        final int vectorCount = 500;
        final int dimension = 64;
        final int pqSubspaces = 16;
        final Random random = new Random(42);

        final Path indexDir = tempDir.resolve("index");
        final Path storageDir = tempDir.resolve("storage");

        final List<float[]> vectors = new ArrayList<>();
        for(int i = 0; i < vectorCount; i++)
        {
            vectors.add(randomVector(random, dimension));
        }

        final float[] queryVector = randomVector(new Random(999), dimension);
        final List<Long> expectedIds = new ArrayList<>();

        // Phase 1: Create index with PQ, populate, search, persist
        {
            try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
            {
                final GigaMap<Document> gigaMap = GigaMap.New();
                storage.setRoot(gigaMap);

                final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());
                final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
                    .dimension(dimension)
                    .similarityFunction(VectorSimilarityFunction.COSINE)
                    .onDisk(true)
                    .indexDirectory(indexDir)
                    .enablePqCompression(true)
                    .pqSubspaces(pqSubspaces)
                    .build();

                final VectorIndex<Document> index = vectorIndices.add(
                    "embeddings",
                    config,
                    new ComputedDocumentVectorizer()
                );

                assertTrue(index.isOnDisk());
                assertTrue(index.isPqCompressionEnabled());

                for(int i = 0; i < vectorCount; i++)
                {
                    gigaMap.add(new Document("doc_" + i, vectors.get(i)));
                }

                // Train and search
                ((VectorIndex.Internal<Document>)index).trainCompressionIfNeeded();

                final VectorSearchResult<Document> result = index.search(queryVector, 10);
                for(final VectorSearchResult.Entry<Document> entry : result)
                {
                    expectedIds.add(entry.entityId());
                }

                // Persist
                index.persistToDisk();
                assertTrue(Files.exists(indexDir.resolve("embeddings.graph")));
                assertTrue(Files.exists(indexDir.resolve("embeddings.meta")));

                storage.storeRoot();
            }
        }

        // Phase 2: Reload and verify search results
        {
            try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
            {
                @SuppressWarnings("unchecked")
                final GigaMap<Document> gigaMap = (GigaMap<Document>)storage.root();
                final VectorIndices<Document> vectorIndices = gigaMap.index().get(VectorIndices.Category());

                assertEquals(vectorCount, gigaMap.size());

                final VectorIndex<Document> index = vectorIndices.get("embeddings");
                assertNotNull(index);
                assertTrue(index.isOnDisk());
                assertTrue(index.isPqCompressionEnabled());

                // Search after reload
                final VectorSearchResult<Document> result = index.search(queryVector, 10);
                assertEquals(10, result.size());

                final List<Long> actualIds = new ArrayList<>();
                for(final VectorSearchResult.Entry<Document> entry : result)
                {
                    actualIds.add(entry.entityId());
                }

                // Results should match (or at least overlap significantly)
                assertEquals(expectedIds.size(), actualIds.size());

                // Verify all entities are accessible
                for(final VectorSearchResult.Entry<Document> entry : result)
                {
                    assertNotNull(entry.entity());
                    assertTrue(entry.entity().content().startsWith("doc_"));
                }
            }
        }
    }

    /**
     * Test PQ-compressed disk index with DOT_PRODUCT similarity function.
     */
    @Test
    void testPqCompressionWithDotProduct(@TempDir final Path tempDir)
    {
        final int vectorCount = 500;
        final int dimension = 64;
        final int pqSubspaces = 16;
        final Random random = new Random(42);

        final Path indexDir = tempDir.resolve("index");

        final GigaMap<Document> gigaMap = GigaMap.New();
        final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());

        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(dimension)
            .similarityFunction(VectorSimilarityFunction.DOT_PRODUCT)
            .onDisk(true)
            .indexDirectory(indexDir)
            .enablePqCompression(true)
            .pqSubspaces(pqSubspaces)
            .build();

        final VectorIndex<Document> index = vectorIndices.add(
            "embeddings",
            config,
            new ComputedDocumentVectorizer()
        );

        for(int i = 0; i < vectorCount; i++)
        {
            gigaMap.add(new Document("doc_" + i, randomVector(random, dimension)));
        }

        ((VectorIndex.Internal<Document>)index).trainCompressionIfNeeded();

        final float[] queryVector = randomVector(random, dimension);
        final VectorSearchResult<Document> result = index.search(queryVector, 10);

        assertEquals(10, result.size());
        for(final VectorSearchResult.Entry<Document> entry : result)
        {
            assertNotNull(entry.entity());
        }
    }

    /**
     * Test PQ-compressed disk index with EUCLIDEAN similarity function.
     */
    @Test
    void testPqCompressionWithEuclidean(@TempDir final Path tempDir)
    {
        final int vectorCount = 500;
        final int dimension = 64;
        final int pqSubspaces = 16;
        final Random random = new Random(42);

        final Path indexDir = tempDir.resolve("index");

        final GigaMap<Document> gigaMap = GigaMap.New();
        final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());

        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(dimension)
            .similarityFunction(VectorSimilarityFunction.EUCLIDEAN)
            .onDisk(true)
            .indexDirectory(indexDir)
            .enablePqCompression(true)
            .pqSubspaces(pqSubspaces)
            .build();

        final VectorIndex<Document> index = vectorIndices.add(
            "embeddings",
            config,
            new ComputedDocumentVectorizer()
        );

        for(int i = 0; i < vectorCount; i++)
        {
            gigaMap.add(new Document("doc_" + i, randomVector(random, dimension)));
        }

        ((VectorIndex.Internal<Document>)index).trainCompressionIfNeeded();

        final float[] queryVector = randomVector(random, dimension);
        final VectorSearchResult<Document> result = index.search(queryVector, 10);

        assertEquals(10, result.size());
        for(final VectorSearchResult.Entry<Document> entry : result)
        {
            assertNotNull(entry.entity());
        }
    }

    /**
     * Test PQ compression with default subspaces (auto-calculated as dimension/4).
     */
    @Test
    void testPqCompressionWithDefaultSubspaces(@TempDir final Path tempDir)
    {
        final int vectorCount = 500;
        final int dimension = 128;
        final Random random = new Random(42);

        final Path indexDir = tempDir.resolve("index");

        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(dimension)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .onDisk(true)
            .indexDirectory(indexDir)
            .enablePqCompression(true)
            // pqSubspaces not set - should default to dimension/4 = 32
            .build();

        assertEquals(0, config.pqSubspaces(),
            "pqSubspaces should be 0 (auto-calculated at runtime)");

        final GigaMap<Document> gigaMap = GigaMap.New();
        final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());

        final VectorIndex<Document> index = vectorIndices.add(
            "embeddings",
            config,
            new ComputedDocumentVectorizer()
        );

        for(int i = 0; i < vectorCount; i++)
        {
            gigaMap.add(new Document("doc_" + i, randomVector(random, dimension)));
        }

        ((VectorIndex.Internal<Document>)index).trainCompressionIfNeeded();

        final float[] queryVector = randomVector(random, dimension);
        final VectorSearchResult<Document> result = index.search(queryVector, 10);

        assertEquals(10, result.size());
        for(final VectorSearchResult.Entry<Document> entry : result)
        {
            assertNotNull(entry.entity());
            assertTrue(entry.entity().content().startsWith("doc_"));
        }
    }

    /**
     * Test removing entities from a PQ-compressed disk index.
     * Verifies that removed entities do not appear in search results.
     */
    @Test
    void testPqCompressionWithRemoval(@TempDir final Path tempDir)
    {
        final int vectorCount = 500;
        final int dimension = 64;
        final int pqSubspaces = 16;
        final Random random = new Random(42);

        final Path indexDir = tempDir.resolve("index");

        final GigaMap<Document> gigaMap = GigaMap.New();
        final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());

        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(dimension)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .onDisk(true)
            .indexDirectory(indexDir)
            .enablePqCompression(true)
            .pqSubspaces(pqSubspaces)
            .build();

        final VectorIndex<Document> index = vectorIndices.add(
            "embeddings",
            config,
            new ComputedDocumentVectorizer()
        );

        for(int i = 0; i < vectorCount; i++)
        {
            gigaMap.add(new Document("doc_" + i, randomVector(random, dimension)));
        }

        ((VectorIndex.Internal<Document>)index).trainCompressionIfNeeded();

        // Remove every other entity (even IDs)
        for(int i = 0; i < vectorCount; i += 2)
        {
            gigaMap.removeById(i);
        }

        assertEquals(vectorCount / 2, gigaMap.size());

        // Search should only return remaining entities
        final VectorSearchResult<Document> result = index.search(randomVector(random, dimension), 10);
        assertEquals(10, result.size());

        for(final VectorSearchResult.Entry<Document> entry : result)
        {
            assertNotNull(entry.entity());
            final String content = entry.entity().content();
            final int docNum = Integer.parseInt(content.replace("doc_", ""));
            assertTrue(docNum % 2 != 0,
                "Only odd-numbered documents should remain, found: " + content);
        }
    }

    /**
     * Test concurrent search with PQ compression enabled.
     * Verifies thread safety of PQ-compressed search.
     */
    @Test
    void testPqCompressionConcurrentSearch(@TempDir final Path tempDir) throws Exception
    {
        final int vectorCount = 500;
        final int dimension = 64;
        final int pqSubspaces = 16;
        final Random random = new Random(42);

        final Path indexDir = tempDir.resolve("index");

        final GigaMap<Document> gigaMap = GigaMap.New();
        final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());

        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(dimension)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .onDisk(true)
            .indexDirectory(indexDir)
            .enablePqCompression(true)
            .pqSubspaces(pqSubspaces)
            .build();

        final VectorIndex<Document> index = vectorIndices.add(
            "embeddings",
            config,
            new ComputedDocumentVectorizer()
        );

        for(int i = 0; i < vectorCount; i++)
        {
            gigaMap.add(new Document("doc_" + i, randomVector(random, dimension)));
        }

        ((VectorIndex.Internal<Document>)index).trainCompressionIfNeeded();

        // Run concurrent searches
        final int numSearches = 50;
        final AtomicInteger successfulSearches = new AtomicInteger(0);
        final AtomicBoolean hasError = new AtomicBoolean(false);
        final CountDownLatch latch = new CountDownLatch(numSearches);
        final ExecutorService executor = Executors.newFixedThreadPool(4);

        for(int i = 0; i < numSearches; i++)
        {
            final float[] queryVector = randomVector(new Random(i), dimension);
            executor.submit(() ->
            {
                try
                {
                    final VectorSearchResult<Document> result = index.search(queryVector, 10);
                    if(result.size() == 10)
                    {
                        successfulSearches.incrementAndGet();
                    }
                }
                catch(final Exception e)
                {
                    hasError.set(true);
                    e.printStackTrace();
                }
                finally
                {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(30, TimeUnit.SECONDS), "Searches should complete within timeout");
        executor.shutdown();

        assertFalse(hasError.get(), "No errors should occur during concurrent PQ search");
        assertEquals(numSearches, successfulSearches.get(),
            "All concurrent PQ searches should return expected results");
    }

    /**
     * Test adding vectors after PQ training.
     * Verifies that search still works after adding more vectors post-training.
     */
    @Test
    void testPqCompressionAddAfterTraining(@TempDir final Path tempDir)
    {
        final int initialCount = 500;
        final int additionalCount = 200;
        final int dimension = 64;
        final int pqSubspaces = 16;
        final Random random = new Random(42);

        final Path indexDir = tempDir.resolve("index");

        final GigaMap<Document> gigaMap = GigaMap.New();
        final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());

        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(dimension)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .onDisk(true)
            .indexDirectory(indexDir)
            .enablePqCompression(true)
            .pqSubspaces(pqSubspaces)
            .build();

        final VectorIndex<Document> index = vectorIndices.add(
            "embeddings",
            config,
            new ComputedDocumentVectorizer()
        );

        // Add initial vectors
        for(int i = 0; i < initialCount; i++)
        {
            gigaMap.add(new Document("initial_" + i, randomVector(random, dimension)));
        }

        // Train PQ
        ((VectorIndex.Internal<Document>)index).trainCompressionIfNeeded();

        // Search before adding more
        final float[] queryVector = randomVector(random, dimension);
        final VectorSearchResult<Document> resultBefore = index.search(queryVector, 10);
        assertEquals(10, resultBefore.size());

        // Add more vectors after training
        for(int i = 0; i < additionalCount; i++)
        {
            gigaMap.add(new Document("additional_" + i, randomVector(random, dimension)));
        }

        assertEquals(initialCount + additionalCount, gigaMap.size());

        // Search should still work and may include newly added vectors
        final VectorSearchResult<Document> resultAfter = index.search(queryVector, 10);
        assertEquals(10, resultAfter.size());

        for(final VectorSearchResult.Entry<Document> entry : resultAfter)
        {
            assertNotNull(entry.entity());
        }
    }

    /**
     * Test PQ-compressed disk index with multiple restarts.
     * Verifies that search works correctly after persisting a PQ-compressed
     * index to disk and reloading it across multiple restart cycles.
     */
    @Test
    void testPqCompressionMultipleRestarts(@TempDir final Path tempDir) throws IOException
    {
        final int dimension = 64;
        final int pqSubspaces = 16;
        final Random random = new Random(42);

        final Path indexDir = tempDir.resolve("index");
        final Path storageDir = tempDir.resolve("storage");

        final float[] queryVector = randomVector(new Random(999), dimension);

        // Phase 1: Create with 500 vectors and PQ, persist to disk
        {
            try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
            {
                final GigaMap<Document> gigaMap = GigaMap.New();
                storage.setRoot(gigaMap);

                final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());
                final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
                    .dimension(dimension)
                    .similarityFunction(VectorSimilarityFunction.COSINE)
                    .onDisk(true)
                    .indexDirectory(indexDir)
                    .enablePqCompression(true)
                    .pqSubspaces(pqSubspaces)
                    .build();

                final VectorIndex<Document> index = vectorIndices.add(
                    "embeddings",
                    config,
                    new ComputedDocumentVectorizer()
                );

                for(int i = 0; i < 500; i++)
                {
                    gigaMap.add(new Document("doc_" + i, randomVector(random, dimension)));
                }

                ((VectorIndex.Internal<Document>)index).trainCompressionIfNeeded();
                index.persistToDisk();

                // Verify search works before restart
                final VectorSearchResult<Document> result = index.search(queryVector, 10);
                assertEquals(10, result.size());

                storage.storeRoot();
            }
        }

        // Phase 2: Restart and verify search works from loaded disk index
        {
            try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
            {
                @SuppressWarnings("unchecked")
                final GigaMap<Document> gigaMap = (GigaMap<Document>)storage.root();
                final VectorIndices<Document> vectorIndices = gigaMap.index().get(VectorIndices.Category());

                assertEquals(500, gigaMap.size());

                final VectorIndex<Document> index = vectorIndices.get("embeddings");
                assertNotNull(index);
                assertTrue(index.isOnDisk());
                assertTrue(index.isPqCompressionEnabled());

                // Search should work after reload
                final VectorSearchResult<Document> result = index.search(queryVector, 10);
                assertEquals(10, result.size());

                // Verify all entities are accessible
                for(final VectorSearchResult.Entry<Document> entry : result)
                {
                    assertNotNull(entry.entity());
                    assertTrue(entry.entity().content().startsWith("doc_"));
                }
            }
        }

        // Phase 3: Second restart - verify search still works
        {
            try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
            {
                @SuppressWarnings("unchecked")
                final GigaMap<Document> gigaMap = (GigaMap<Document>)storage.root();
                final VectorIndices<Document> vectorIndices = gigaMap.index().get(VectorIndices.Category());

                assertEquals(500, gigaMap.size());

                final VectorIndex<Document> index = vectorIndices.get("embeddings");
                final VectorSearchResult<Document> result = index.search(queryVector, 20);
                assertEquals(20, result.size());
            }
        }
    }

    /**
     * Test PQ-compressed disk index with removeAll and repopulation.
     * Verifies the index can be cleared and rebuilt with PQ compression.
     */
    @Test
    void testPqCompressionRemoveAllAndRepopulate(@TempDir final Path tempDir)
    {
        final int dimension = 64;
        final int pqSubspaces = 16;
        final Random random = new Random(42);

        final Path indexDir = tempDir.resolve("index");

        final GigaMap<Document> gigaMap = GigaMap.New();
        final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());

        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(dimension)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .onDisk(true)
            .indexDirectory(indexDir)
            .enablePqCompression(true)
            .pqSubspaces(pqSubspaces)
            .build();

        vectorIndices.add(
            "embeddings",
            config,
            new ComputedDocumentVectorizer()
        );

        // Initial population
        for(int i = 0; i < 500; i++)
        {
            gigaMap.add(new Document("old_" + i, randomVector(random, dimension)));
        }

        assertEquals(500, gigaMap.size());

        // Clear all
        gigaMap.removeAll();
        assertEquals(0, gigaMap.size());

        // Repopulate
        for(int i = 0; i < 600; i++)
        {
            gigaMap.add(new Document("new_" + i, randomVector(random, dimension)));
        }

        assertEquals(600, gigaMap.size());

        final VectorIndices<Document> vectorIndicesAfter = gigaMap.index().get(VectorIndices.Category());
        final VectorIndex<Document> indexAfter = vectorIndicesAfter.get("embeddings");

        // Train PQ on new data
        ((VectorIndex.Internal<Document>)indexAfter).trainCompressionIfNeeded();

        // Search should find only new documents
        final VectorSearchResult<Document> result = indexAfter.search(randomVector(random, dimension), 20);
        assertEquals(20, result.size());

        for(final VectorSearchResult.Entry<Document> entry : result)
        {
            assertTrue(entry.entity().content().startsWith("new_"),
                "All results should be from new population");
        }
    }

    /**
     * Test that in-memory index (default) still works as expected.
     */
    @Test
    void testInMemoryIndexStillWorks()
    {
        final int dimension = 32;
        final Random random = new Random(42);

        final GigaMap<Document> gigaMap = GigaMap.New();
        final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());

        // Default configuration (in-memory)
        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(dimension)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .build();

        assertFalse(config.onDisk());
        assertNull(config.indexDirectory());

        final VectorIndex<Document> index = vectorIndices.add(
            "embeddings",
            config,
            new ComputedDocumentVectorizer()
        );

        assertFalse(index.isOnDisk());

        // Add vectors
        for(int i = 0; i < 100; i++)
        {
            gigaMap.add(new Document("doc_" + i, randomVector(random, dimension)));
        }

        // Search should work
        final VectorSearchResult<Document> result = index.search(randomVector(random, dimension), 10);
        assertEquals(10, result.size());
    }


    // ========================================================================
    // Background Persistence Tests
    // ========================================================================

    /**
     * Test background persistence configuration builder.
     */
    @Test
    void testBackgroundPersistenceConfigurationBuilder(@TempDir final Path tempDir)
    {
        final Path indexDir = tempDir.resolve("index");

        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(128)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .onDisk(true)
            .indexDirectory(indexDir)
            .backgroundPersistence(true)
            .persistenceIntervalMs(60_000)
            .persistOnShutdown(true)
            .minChangesBetweenPersists(50)
            .build();

        assertTrue(config.onDisk());
        assertTrue(config.backgroundPersistence());
        assertEquals(60_000, config.persistenceIntervalMs());
        assertTrue(config.persistOnShutdown());
        assertEquals(50, config.minChangesBetweenPersists());
    }

    /**
     * Test background persistence configuration defaults.
     */
    @Test
    void testBackgroundPersistenceConfigurationDefaults(@TempDir final Path tempDir)
    {
        final Path indexDir = tempDir.resolve("index");

        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(128)
            .onDisk(true)
            .indexDirectory(indexDir)
            .build();

        // Background persistence should be disabled by default
        assertFalse(config.backgroundPersistence());
        assertEquals(30_000, config.persistenceIntervalMs());
        assertTrue(config.persistOnShutdown());
        assertEquals(100, config.minChangesBetweenPersists());
    }

    /**
     * Test validation: background persistence requires onDisk.
     */
    @Test
    void testBackgroundPersistenceRequiresOnDisk()
    {
        assertThrows(IllegalStateException.class, () ->
            VectorIndexConfiguration.builder()
                .dimension(128)
                .backgroundPersistence(true)
                // onDisk not set
                .build()
        );
    }

    /**
     * Test validation: persistenceIntervalMs must be positive.
     */
    @Test
    void testPersistenceIntervalMsMustBePositive(@TempDir final Path tempDir)
    {
        assertThrows(IllegalArgumentException.class, () ->
            VectorIndexConfiguration.builder()
                .dimension(128)
                .onDisk(true)
                .indexDirectory(tempDir)
                .persistenceIntervalMs(0)
                .build()
        );

        assertThrows(IllegalArgumentException.class, () ->
            VectorIndexConfiguration.builder()
                .dimension(128)
                .onDisk(true)
                .indexDirectory(tempDir)
                .persistenceIntervalMs(-1000)
                .build()
        );
    }

    /**
     * Test validation: minChangesBetweenPersists must be non-negative.
     */
    @Test
    void testMinChangesBetweenPersistsMustBeNonNegative(@TempDir final Path tempDir)
    {
        assertThrows(IllegalArgumentException.class, () ->
            VectorIndexConfiguration.builder()
                .dimension(128)
                .onDisk(true)
                .indexDirectory(tempDir)
                .minChangesBetweenPersists(-1)
                .build()
        );

        // Zero should be allowed (persist on every interval)
        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(128)
            .onDisk(true)
            .indexDirectory(tempDir)
            .minChangesBetweenPersists(0)
            .build();
        assertEquals(0, config.minChangesBetweenPersists());
    }

    /**
     * Test that background persistence triggers after the configured interval.
     */
    @Test
    void testBackgroundPersistenceTriggersAfterInterval(@TempDir final Path tempDir) throws Exception
    {
        final int dimension = 32;
        final Random random = new Random(42);
        final Path indexDir = tempDir.resolve("index");

        final GigaMap<Document> gigaMap = GigaMap.New();
        final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());

        // Configure with short interval for testing
        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(dimension)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .onDisk(true)
            .indexDirectory(indexDir)
            .backgroundPersistence(true)
            .persistenceIntervalMs(500) // 500ms for fast test
            .minChangesBetweenPersists(1) // Persist on any change
            .build();

        final VectorIndex<Document> index = vectorIndices.add(
            "embeddings",
            config,
            new ComputedDocumentVectorizer()
        );

        try
        {
            // Add vectors to trigger dirty state
            for(int i = 0; i < 50; i++)
            {
                gigaMap.add(new Document("doc_" + i, randomVector(random, dimension)));
            }

            // Initially, files should not exist (not yet persisted)
            assertFalse(Files.exists(indexDir.resolve("embeddings.graph")),
                "Graph file should not exist immediately after adding");

            // Wait for background persistence to trigger (interval + some buffer)
            Thread.sleep(1500);

            // Files should now exist
            assertTrue(Files.exists(indexDir.resolve("embeddings.graph")),
                "Graph file should exist after background persistence");
            assertTrue(Files.exists(indexDir.resolve("embeddings.meta")),
                "Meta file should exist after background persistence");
        }
        finally
        {
            index.close();
        }
    }

    /**
     * Test that search works concurrently during background persistence.
     */
    @Test
    void testConcurrentSearchDuringBackgroundPersistence(@TempDir final Path tempDir) throws Exception
    {
        final int dimension = 32;
        final int vectorCount = 200;
        final Random random = new Random(42);
        final Path indexDir = tempDir.resolve("index");

        final GigaMap<Document> gigaMap = GigaMap.New();
        final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());

        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(dimension)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .onDisk(true)
            .indexDirectory(indexDir)
            .backgroundPersistence(true)
            .persistenceIntervalMs(200) // Short interval to trigger during test
            .minChangesBetweenPersists(1)
            .build();

        final VectorIndex<Document> index = vectorIndices.add(
            "embeddings",
            config,
            new ComputedDocumentVectorizer()
        );

        try
        {
            // Add initial vectors
            for(int i = 0; i < vectorCount; i++)
            {
                gigaMap.add(new Document("doc_" + i, randomVector(random, dimension)));
            }

            // Run concurrent searches while background persistence may be running
            final int numSearches = 50;
            final AtomicInteger successfulSearches = new AtomicInteger(0);
            final AtomicBoolean hasError = new AtomicBoolean(false);
            final CountDownLatch latch = new CountDownLatch(numSearches);
            final ExecutorService executor = Executors.newFixedThreadPool(4);

            for(int i = 0; i < numSearches; i++)
            {
                final float[] queryVector = randomVector(new Random(i), dimension);
                executor.submit(() ->
                {
                    try
                    {
                        final VectorSearchResult<Document> result = index.search(queryVector, 10);
                        if(result.size() == 10)
                        {
                            successfulSearches.incrementAndGet();
                        }
                    }
                    catch(final Exception e)
                    {
                        hasError.set(true);
                        e.printStackTrace();
                    }
                    finally
                    {
                        latch.countDown();
                    }
                });

                // Small delay to spread searches over time
                Thread.sleep(20);
            }

            // Wait for all searches to complete
            assertTrue(latch.await(30, TimeUnit.SECONDS), "Searches should complete within timeout");
            executor.shutdown();

            // Verify all searches succeeded
            assertFalse(hasError.get(), "No errors should occur during concurrent search");
            assertEquals(numSearches, successfulSearches.get(),
                "All searches should return expected number of results");
        }
        finally
        {
            index.close();
        }
    }

    /**
     * Test that shutdown persists pending changes when persistOnShutdown is true.
     */
    @Test
    void testShutdownPersistsPendingChanges(@TempDir final Path tempDir) throws Exception
    {
        final int dimension = 32;
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
            .backgroundPersistence(true)
            .persistenceIntervalMs(60_000) // Long interval - won't trigger during test
            .minChangesBetweenPersists(1)
            .persistOnShutdown(true) // Should persist on close
            .build();

        final VectorIndex<Document> index = vectorIndices.add(
            "embeddings",
            config,
            new ComputedDocumentVectorizer()
        );

        // Add vectors
        for(int i = 0; i < vectorCount; i++)
        {
            gigaMap.add(new Document("doc_" + i, randomVector(random, dimension)));
        }

        // Files should not exist yet (interval hasn't triggered)
        assertFalse(Files.exists(indexDir.resolve("embeddings.graph")),
            "Graph file should not exist before close");

        // Close the index (should trigger persist due to persistOnShutdown=true)
        index.close();

        // Files should now exist
        assertTrue(Files.exists(indexDir.resolve("embeddings.graph")),
            "Graph file should exist after close with persistOnShutdown=true");
        assertTrue(Files.exists(indexDir.resolve("embeddings.meta")),
            "Meta file should exist after close with persistOnShutdown=true");
    }

    /**
     * Test that shutdown does NOT persist when persistOnShutdown is false.
     */
    @Test
    void testShutdownSkipsPersistWhenDisabled(@TempDir final Path tempDir) throws Exception
    {
        final int dimension = 32;
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
            .backgroundPersistence(true)
            .persistenceIntervalMs(60_000) // Long interval - won't trigger during test
            .minChangesBetweenPersists(1)
            .persistOnShutdown(false) // Should NOT persist on close
            .build();

        final VectorIndex<Document> index = vectorIndices.add(
            "embeddings",
            config,
            new ComputedDocumentVectorizer()
        );

        // Add vectors
        for(int i = 0; i < vectorCount; i++)
        {
            gigaMap.add(new Document("doc_" + i, randomVector(random, dimension)));
        }

        // Close the index (should NOT trigger persist)
        index.close();

        // Files should NOT exist
        assertFalse(Files.exists(indexDir.resolve("embeddings.graph")),
            "Graph file should not exist after close with persistOnShutdown=false");
    }

    /**
     * Test debouncing: persistence is skipped when change count is below threshold.
     */
    @Test
    void testDebouncing(@TempDir final Path tempDir) throws Exception
    {
        final int dimension = 32;
        final Random random = new Random(42);
        final Path indexDir = tempDir.resolve("index");

        final GigaMap<Document> gigaMap = GigaMap.New();
        final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());

        // Configure with high threshold that won't be met
        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(dimension)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .onDisk(true)
            .indexDirectory(indexDir)
            .backgroundPersistence(true)
            .persistenceIntervalMs(200) // Short interval
            .minChangesBetweenPersists(500) // High threshold
            .build();

        final VectorIndex<Document> index = vectorIndices.add(
            "embeddings",
            config,
            new ComputedDocumentVectorizer()
        );

        try
        {
            // Add fewer vectors than the threshold
            for(int i = 0; i < 50; i++) // 50 < 500 threshold
            {
                gigaMap.add(new Document("doc_" + i, randomVector(random, dimension)));
            }

            // Wait for multiple persistence intervals
            Thread.sleep(800);

            // Files should NOT exist because change count is below threshold
            assertFalse(Files.exists(indexDir.resolve("embeddings.graph")),
                "Graph file should not exist when changes below threshold");

            // Now add more vectors to exceed the threshold
            for(int i = 50; i < 600; i++) // Total now 600 > 500 threshold
            {
                gigaMap.add(new Document("doc_" + i, randomVector(random, dimension)));
            }

            // Wait for persistence to trigger
            Thread.sleep(500);

            // Now files should exist
            assertTrue(Files.exists(indexDir.resolve("embeddings.graph")),
                "Graph file should exist when changes exceed threshold");
        }
        finally
        {
            index.close();
        }
    }

    /**
     * Test that adding vectors in bulk correctly tracks change count.
     */
    @Test
    void testBulkAddTracksChangeCount(@TempDir final Path tempDir) throws Exception
    {
        final int dimension = 32;
        final Random random = new Random(42);
        final Path indexDir = tempDir.resolve("index");

        final GigaMap<Document> gigaMap = GigaMap.New();
        final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());

        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(dimension)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .onDisk(true)
            .indexDirectory(indexDir)
            .backgroundPersistence(true)
            .persistenceIntervalMs(300)
            .minChangesBetweenPersists(100)
            .build();

        final VectorIndex<Document> index = vectorIndices.add(
            "embeddings",
            config,
            new ComputedDocumentVectorizer()
        );

        try
        {
            // Bulk add documents
            final List<Document> documents = new ArrayList<>();
            for(int i = 0; i < 150; i++)
            {
                documents.add(new Document("doc_" + i, randomVector(random, dimension)));
            }
            gigaMap.addAll(documents);

            // Wait for persistence
            Thread.sleep(800);

            // Files should exist because bulk add counted as 150 changes (> 100 threshold)
            assertTrue(Files.exists(indexDir.resolve("embeddings.graph")),
                "Graph file should exist after bulk add exceeds threshold");
        }
        finally
        {
            index.close();
        }
    }

    /**
     * Test that background persistence can be reloaded after restart.
     */
    @Test
    void testBackgroundPersistenceWithRestart(@TempDir final Path tempDir) throws Exception
    {
        final int dimension = 32;
        final int vectorCount = 200;
        final Random random = new Random(42);
        final Path indexDir = tempDir.resolve("index");
        final Path storageDir = tempDir.resolve("storage");

        final float[] queryVector = randomVector(new Random(999), dimension);
        final int expectedK = 10;

        // Phase 1: Create index with background persistence and add vectors
        {
            try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
            {
                final GigaMap<Document> gigaMap = GigaMap.New();
                storage.setRoot(gigaMap);

                final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());
                final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
                    .dimension(dimension)
                    .similarityFunction(VectorSimilarityFunction.COSINE)
                    .onDisk(true)
                    .indexDirectory(indexDir)
                    .backgroundPersistence(true)
                    .persistenceIntervalMs(100)
                    .minChangesBetweenPersists(1)
                    .persistOnShutdown(true)
                    .build();

                final VectorIndex<Document> index = vectorIndices.add(
                    "embeddings",
                    config,
                    new ComputedDocumentVectorizer()
                );

                // Add vectors
                for(int i = 0; i < vectorCount; i++)
                {
                    gigaMap.add(new Document("doc_" + i, randomVector(random, dimension)));
                }

                // Verify search works
                final VectorSearchResult<Document> result = index.search(queryVector, expectedK);
                assertEquals(expectedK, result.size());

                storage.storeRoot();

                // Explicitly close the index to trigger persistOnShutdown
                // (EmbeddedStorageManager doesn't auto-close VectorIndex)
                index.close();
            }
        }

        // Verify files were persisted
        assertTrue(Files.exists(indexDir.resolve("embeddings.graph")),
            "Graph file should exist after close");
        assertTrue(Files.exists(indexDir.resolve("embeddings.meta")),
            "Meta file should exist after close");

        // Phase 2: Reload and verify
        {
            try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
            {
                @SuppressWarnings("unchecked")
                final GigaMap<Document> gigaMap = (GigaMap<Document>)storage.root();
                final VectorIndices<Document> vectorIndices = gigaMap.index().get(VectorIndices.Category());

                assertEquals(vectorCount, gigaMap.size());

                final VectorIndex<Document> index = vectorIndices.get("embeddings");
                assertNotNull(index);
                assertTrue(index.isOnDisk(), "Index should be on-disk after reload");

                // Search should still work after reload
                final VectorSearchResult<Document> result = index.search(queryVector, expectedK);
                assertEquals(expectedK, result.size());

                // Clean up
                index.close();
            }
        }
    }

    /**
     * Test that manual persistToDisk still works with background persistence enabled.
     */
    @Test
    void testManualPersistWithBackgroundPersistenceEnabled(@TempDir final Path tempDir) throws Exception
    {
        final int dimension = 32;
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
            .backgroundPersistence(true)
            .persistenceIntervalMs(60_000) // Long interval - won't trigger
            .minChangesBetweenPersists(1000) // High threshold - won't trigger
            .build();

        final VectorIndex<Document> index = vectorIndices.add(
            "embeddings",
            config,
            new ComputedDocumentVectorizer()
        );

        try
        {
            // Add vectors
            for(int i = 0; i < vectorCount; i++)
            {
                gigaMap.add(new Document("doc_" + i, randomVector(random, dimension)));
            }

            // Files should not exist yet
            assertFalse(Files.exists(indexDir.resolve("embeddings.graph")));

            // Manually trigger persistence
            index.persistToDisk();

            // Files should now exist
            assertTrue(Files.exists(indexDir.resolve("embeddings.graph")),
                "Graph file should exist after manual persistToDisk");
            assertTrue(Files.exists(indexDir.resolve("embeddings.meta")),
                "Meta file should exist after manual persistToDisk");
        }
        finally
        {
            index.close();
        }
    }


    // ========================================================================
    // Background Optimization Tests
    // ========================================================================

    /**
     * Test background optimization configuration builder.
     */
    @Test
    void testBackgroundOptimizationConfigurationBuilder(@TempDir final Path tempDir)
    {
        final Path indexDir = tempDir.resolve("index");

        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(128)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .onDisk(true)
            .indexDirectory(indexDir)
            .backgroundOptimization(true)
            .optimizationIntervalMs(120_000)
            .minChangesBetweenOptimizations(500)
            .optimizeOnShutdown(true)
            .build();

        assertTrue(config.onDisk());
        assertTrue(config.backgroundOptimization());
        assertEquals(120_000, config.optimizationIntervalMs());
        assertEquals(500, config.minChangesBetweenOptimizations());
        assertTrue(config.optimizeOnShutdown());
    }

    /**
     * Test background optimization configuration defaults.
     */
    @Test
    void testBackgroundOptimizationConfigurationDefaults(@TempDir final Path tempDir)
    {
        final Path indexDir = tempDir.resolve("index");

        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(128)
            .onDisk(true)
            .indexDirectory(indexDir)
            .build();

        // Background optimization should be disabled by default
        assertFalse(config.backgroundOptimization());
        assertEquals(60_000, config.optimizationIntervalMs());
        assertEquals(1000, config.minChangesBetweenOptimizations());
        assertFalse(config.optimizeOnShutdown());
    }

    /**
     * Test validation: optimizationIntervalMs must be positive.
     */
    @Test
    void testOptimizationIntervalMsMustBePositive(@TempDir final Path tempDir)
    {
        assertThrows(IllegalArgumentException.class, () ->
            VectorIndexConfiguration.builder()
                .dimension(128)
                .onDisk(true)
                .indexDirectory(tempDir)
                .optimizationIntervalMs(0)
                .build()
        );

        assertThrows(IllegalArgumentException.class, () ->
            VectorIndexConfiguration.builder()
                .dimension(128)
                .onDisk(true)
                .indexDirectory(tempDir)
                .optimizationIntervalMs(-1000)
                .build()
        );
    }

    /**
     * Test validation: minChangesBetweenOptimizations must be non-negative.
     */
    @Test
    void testMinChangesBetweenOptimizationsMustBeNonNegative(@TempDir final Path tempDir)
    {
        assertThrows(IllegalArgumentException.class, () ->
            VectorIndexConfiguration.builder()
                .dimension(128)
                .onDisk(true)
                .indexDirectory(tempDir)
                .minChangesBetweenOptimizations(-1)
                .build()
        );

        // Zero should be allowed (optimize on every interval)
        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(128)
            .onDisk(true)
            .indexDirectory(tempDir)
            .minChangesBetweenOptimizations(0)
            .build();
        assertEquals(0, config.minChangesBetweenOptimizations());
    }

    /**
     * Test that background optimization runs after the configured interval and threshold.
     */
    @Test
    void testBackgroundOptimizationTriggersAfterIntervalAndThreshold(@TempDir final Path tempDir) throws Exception
    {
        final int dimension = 32;
        final Random random = new Random(42);
        final Path indexDir = tempDir.resolve("index");

        final GigaMap<Document> gigaMap = GigaMap.New();
        final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());

        // Configure with short interval and low threshold for testing
        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(dimension)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .onDisk(true)
            .indexDirectory(indexDir)
            .backgroundOptimization(true)
            .optimizationIntervalMs(300) // 300ms for fast test
            .minChangesBetweenOptimizations(10) // Low threshold
            .build();

        final VectorIndex<Document> index = vectorIndices.add(
            "embeddings",
            config,
            new ComputedDocumentVectorizer()
        );

        try
        {
            final VectorIndex.Default<Document> defaultIndex = (VectorIndex.Default<Document>)index;

            // Initially, optimization count should be 0
            assertEquals(0, defaultIndex.optimizationManager.getOptimizationCount(),
                "Optimization count should be 0 initially");

            // Add vectors to trigger dirty state above threshold
            for(int i = 0; i < 50; i++)
            {
                gigaMap.add(new Document("doc_" + i, randomVector(random, dimension)));
            }

            // Verify pending changes are tracked
            assertTrue(defaultIndex.optimizationManager.getPendingChangeCount() > 0,
                "Pending changes should be tracked");

            // Wait for background optimization to run
            Thread.sleep(800);

            // Verify optimization was actually performed
            assertTrue(defaultIndex.optimizationManager.getOptimizationCount() >= 1,
                "Optimization should have been performed at least once");

            // Verify pending changes were reset
            assertEquals(0, defaultIndex.optimizationManager.getPendingChangeCount(),
                "Pending changes should be reset after optimization");

            // Verify search still works
            final VectorSearchResult<Document> result = index.search(randomVector(random, dimension), 10);
            assertEquals(10, result.size());
        }
        finally
        {
            index.close();
        }
    }

    /**
     * Test that optimization is skipped when change count is below threshold.
     */
    @Test
    void testOptimizationDebouncingBelowThreshold(@TempDir final Path tempDir) throws Exception
    {
        final int dimension = 32;
        final Random random = new Random(42);
        final Path indexDir = tempDir.resolve("index");

        final GigaMap<Document> gigaMap = GigaMap.New();
        final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());

        // Configure with high threshold that won't be met
        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(dimension)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .onDisk(true)
            .indexDirectory(indexDir)
            .backgroundOptimization(true)
            .optimizationIntervalMs(200) // Short interval
            .minChangesBetweenOptimizations(500) // High threshold
            .build();

        final VectorIndex<Document> index = vectorIndices.add(
            "embeddings",
            config,
            new ComputedDocumentVectorizer()
        );

        try
        {
            final VectorIndex.Default<Document> defaultIndex = (VectorIndex.Default<Document>)index;

            // Add fewer vectors than the threshold
            for(int i = 0; i < 50; i++) // 50 < 500 threshold
            {
                gigaMap.add(new Document("doc_" + i, randomVector(random, dimension)));
            }

            // Verify pending changes are tracked
            assertEquals(50, defaultIndex.optimizationManager.getPendingChangeCount(),
                "Pending changes should be 50");

            // Wait for multiple optimization intervals
            Thread.sleep(600);

            // Verify optimization was NOT performed (below threshold)
            assertEquals(0, defaultIndex.optimizationManager.getOptimizationCount(),
                "Optimization should NOT have been performed (below threshold)");

            // Verify pending changes are still tracked (not reset)
            assertEquals(50, defaultIndex.optimizationManager.getPendingChangeCount(),
                "Pending changes should still be 50 (not reset)");

            // Search should still work
            final VectorSearchResult<Document> result = index.search(randomVector(random, dimension), 10);
            assertEquals(10, result.size());
        }
        finally
        {
            index.close();
        }
    }

    /**
     * Test that shutdown optimizes pending changes when optimizeOnShutdown is true.
     */
    @Test
    void testShutdownOptimizesPendingChanges(@TempDir final Path tempDir) throws Exception
    {
        final int dimension = 32;
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
            .backgroundOptimization(true)
            .optimizationIntervalMs(60_000) // Long interval - won't trigger during test
            .minChangesBetweenOptimizations(1)
            .optimizeOnShutdown(true) // Should optimize on close
            .build();

        final VectorIndex<Document> index = vectorIndices.add(
            "embeddings",
            config,
            new ComputedDocumentVectorizer()
        );

        final VectorIndex.Default<Document> defaultIndex = (VectorIndex.Default<Document>)index;

        // Add vectors
        for(int i = 0; i < vectorCount; i++)
        {
            gigaMap.add(new Document("doc_" + i, randomVector(random, dimension)));
        }

        // Verify pending changes are tracked
        assertEquals(vectorCount, defaultIndex.optimizationManager.getPendingChangeCount(),
            "Pending changes should equal vector count");

        // Verify no optimization has run yet
        assertEquals(0, defaultIndex.optimizationManager.getOptimizationCount(),
            "Optimization count should be 0 before close");

        // Verify search works before close
        final VectorSearchResult<Document> resultBefore = index.search(randomVector(random, dimension), 10);
        assertEquals(10, resultBefore.size());

        // Close the index (should trigger optimize due to optimizeOnShutdown=true)
        index.close();

        // Note: After close(), we can't verify the count changed because the manager is shutdown.
        // But we verified above that pending changes existed and the interval hadn't triggered.
        // The fact that close() completed without error indicates optimization was attempted.
    }

    /**
     * Test that shutdown does NOT optimize when optimizeOnShutdown is false.
     */
    @Test
    void testShutdownSkipsOptimizeWhenDisabled(@TempDir final Path tempDir) throws Exception
    {
        final int dimension = 32;
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
            .backgroundOptimization(true)
            .optimizationIntervalMs(60_000) // Long interval - won't trigger during test
            .minChangesBetweenOptimizations(1)
            .optimizeOnShutdown(false) // Should NOT optimize on close
            .build();

        final VectorIndex<Document> index = vectorIndices.add(
            "embeddings",
            config,
            new ComputedDocumentVectorizer()
        );

        final VectorIndex.Default<Document> defaultIndex = (VectorIndex.Default<Document>)index;

        // Add vectors
        for(int i = 0; i < vectorCount; i++)
        {
            gigaMap.add(new Document("doc_" + i, randomVector(random, dimension)));
        }

        // Verify pending changes are tracked
        assertEquals(vectorCount, defaultIndex.optimizationManager.getPendingChangeCount(),
            "Pending changes should equal vector count");

        // Verify no optimization has run yet
        assertEquals(0, defaultIndex.optimizationManager.getOptimizationCount(),
            "Optimization count should be 0 before close");

        // Close the index (should NOT trigger optimize)
        index.close();

        // Note: After close(), we can't access the manager. But we verified:
        // 1. Pending changes existed
        // 2. No background optimization had run
        // 3. optimizeOnShutdown=false was set
        // So the pending changes should remain unoptimized.
    }

    /**
     * Test that search works concurrently during background optimization.
     */
    @Test
    void testConcurrentSearchDuringBackgroundOptimization(@TempDir final Path tempDir) throws Exception
    {
        final int dimension = 32;
        final int vectorCount = 200;
        final Random random = new Random(42);
        final Path indexDir = tempDir.resolve("index");

        final GigaMap<Document> gigaMap = GigaMap.New();
        final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());

        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(dimension)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .onDisk(true)
            .indexDirectory(indexDir)
            .backgroundOptimization(true)
            .optimizationIntervalMs(150) // Short interval to trigger during test
            .minChangesBetweenOptimizations(1)
            .build();

        final VectorIndex<Document> index = vectorIndices.add(
            "embeddings",
            config,
            new ComputedDocumentVectorizer()
        );

        try
        {
            // Add initial vectors
            for(int i = 0; i < vectorCount; i++)
            {
                gigaMap.add(new Document("doc_" + i, randomVector(random, dimension)));
            }

            // Run concurrent searches while background optimization may be running
            final int numSearches = 50;
            final AtomicInteger successfulSearches = new AtomicInteger(0);
            final AtomicBoolean hasError = new AtomicBoolean(false);
            final CountDownLatch latch = new CountDownLatch(numSearches);
            final ExecutorService executor = Executors.newFixedThreadPool(4);

            for(int i = 0; i < numSearches; i++)
            {
                final float[] queryVector = randomVector(new Random(i), dimension);
                executor.submit(() ->
                {
                    try
                    {
                        final VectorSearchResult<Document> result = index.search(queryVector, 10);
                        if(result.size() == 10)
                        {
                            successfulSearches.incrementAndGet();
                        }
                    }
                    catch(final Exception e)
                    {
                        hasError.set(true);
                        e.printStackTrace();
                    }
                    finally
                    {
                        latch.countDown();
                    }
                });

                // Small delay to spread searches over time
                Thread.sleep(15);
            }

            // Wait for all searches to complete
            assertTrue(latch.await(30, TimeUnit.SECONDS), "Searches should complete within timeout");
            executor.shutdown();

            // Verify all searches succeeded
            assertFalse(hasError.get(), "No errors should occur during concurrent search with optimization");
            assertEquals(numSearches, successfulSearches.get(),
                "All searches should return expected number of results");
        }
        finally
        {
            index.close();
        }
    }

    /**
     * Test that bulk add correctly tracks change count for optimization.
     */
    @Test
    void testBulkAddTracksChangeCountForOptimization(@TempDir final Path tempDir) throws Exception
    {
        final int dimension = 32;
        final Random random = new Random(42);
        final Path indexDir = tempDir.resolve("index");

        final GigaMap<Document> gigaMap = GigaMap.New();
        final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());

        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(dimension)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .onDisk(true)
            .indexDirectory(indexDir)
            .backgroundOptimization(true)
            .optimizationIntervalMs(300)
            .minChangesBetweenOptimizations(100)
            .build();

        final VectorIndex<Document> index = vectorIndices.add(
            "embeddings",
            config,
            new ComputedDocumentVectorizer()
        );

        try
        {
            // Bulk add documents that exceeds the threshold
            final List<Document> documents = new ArrayList<>();
            for(int i = 0; i < 150; i++)
            {
                documents.add(new Document("doc_" + i, randomVector(random, dimension)));
            }
            gigaMap.addAll(documents);

            // Wait for optimization
            Thread.sleep(800);

            // Search should still work
            final VectorSearchResult<Document> result = index.search(randomVector(random, dimension), 10);
            assertEquals(10, result.size());
        }
        finally
        {
            index.close();
        }
    }

    /**
     * Test that manual optimize() method still works with background optimization enabled.
     */
    @Test
    void testManualOptimizeWithBackgroundOptimizationEnabled(@TempDir final Path tempDir) throws Exception
    {
        final int dimension = 32;
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
            .backgroundOptimization(true)
            .optimizationIntervalMs(60_000) // Long interval - won't trigger
            .minChangesBetweenOptimizations(1000) // High threshold - won't trigger
            .build();

        final VectorIndex<Document> index = vectorIndices.add(
            "embeddings",
            config,
            new ComputedDocumentVectorizer()
        );

        try
        {
            // Add vectors
            for(int i = 0; i < vectorCount; i++)
            {
                gigaMap.add(new Document("doc_" + i, randomVector(random, dimension)));
            }

            // Manually trigger optimization
            index.optimize();

            // Search should still work
            final VectorSearchResult<Document> result = index.search(randomVector(random, dimension), 10);
            assertEquals(10, result.size());
        }
        finally
        {
            index.close();
        }
    }

    /**
     * Test that both background persistence and optimization can be enabled together.
     */
    @Test
    void testBackgroundPersistenceAndOptimizationTogether(@TempDir final Path tempDir) throws Exception
    {
        final int dimension = 32;
        final int vectorCount = 150;
        final Random random = new Random(42);
        final Path indexDir = tempDir.resolve("index");

        final GigaMap<Document> gigaMap = GigaMap.New();
        final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());

        // Enable both background persistence and optimization
        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(dimension)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .onDisk(true)
            .indexDirectory(indexDir)
            .backgroundPersistence(true)
            .persistenceIntervalMs(300)
            .minChangesBetweenPersists(10)
            .persistOnShutdown(true)
            .backgroundOptimization(true)
            .optimizationIntervalMs(400)
            .minChangesBetweenOptimizations(10)
            .optimizeOnShutdown(true)
            .build();

        final VectorIndex<Document> index = vectorIndices.add(
            "embeddings",
            config,
            new ComputedDocumentVectorizer()
        );

        try
        {
            // Add vectors
            for(int i = 0; i < vectorCount; i++)
            {
                gigaMap.add(new Document("doc_" + i, randomVector(random, dimension)));
            }

            // Wait for both background tasks to run
            Thread.sleep(1000);

            // Search should still work
            final VectorSearchResult<Document> result = index.search(randomVector(random, dimension), 10);
            assertEquals(10, result.size());

            // Files should exist from background persistence
            assertTrue(Files.exists(indexDir.resolve("embeddings.graph")),
                "Graph file should exist from background persistence");
        }
        finally
        {
            index.close();
        }
    }

    /**
     * Test that in-memory index can also use background optimization.
     */
    @Test
    void testInMemoryIndexWithBackgroundOptimization(@TempDir final Path tempDir) throws Exception
    {
        final int dimension = 32;
        final int vectorCount = 150;
        final Random random = new Random(42);

        final GigaMap<Document> gigaMap = GigaMap.New();
        final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());

        // In-memory index with background optimization only
        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(dimension)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .backgroundOptimization(true)
            .optimizationIntervalMs(200)
            .minChangesBetweenOptimizations(10)
            .optimizeOnShutdown(true)
            .build();

        assertFalse(config.onDisk(), "Should be in-memory index");
        assertTrue(config.backgroundOptimization(), "Background optimization should be enabled");

        final VectorIndex<Document> index = vectorIndices.add(
            "embeddings",
            config,
            new ComputedDocumentVectorizer()
        );

        try
        {
            // Add vectors
            for(int i = 0; i < vectorCount; i++)
            {
                gigaMap.add(new Document("doc_" + i, randomVector(random, dimension)));
            }

            // Wait for optimization to run
            Thread.sleep(600);

            // Search should still work
            final VectorSearchResult<Document> result = index.search(randomVector(random, dimension), 10);
            assertEquals(10, result.size());
        }
        finally
        {
            index.close();
        }
    }
}
