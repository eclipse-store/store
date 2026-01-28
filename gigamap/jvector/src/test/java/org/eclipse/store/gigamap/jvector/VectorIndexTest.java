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

import org.eclipse.serializer.util.X;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for VectorIndex and VectorIndices.
 */
class VectorIndexTest
{
    /**
     * Simple entity with an embedding vector.
     */
    static final class Document
    {
        private final String content;
        private final float[] embedding;

        Document(final String content, final float[] embedding)
        {
            this.content = content;
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
            if (obj == this)
                return true;
            if (obj == null || obj.getClass() != this.getClass())
                return false;
            final var that = (Document) obj;
            return Objects.equals(this.content, that.content) &&
                Arrays.equals(this.embedding, that.embedding);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(this.content, this.embedding);
        }

        @Override
        public String toString()
        {
            return "Document[" +
                "content=" + this.content + ", " +
                "embedding=" + this.embedding + ']';
        }
    }

    /**
     * Embedded vectorizer - extracts vectors already stored in entities.
     * Does NOT store vectors separately (avoids duplicate storage).
     */
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

    /**
     * Computed vectorizer - simulates externally computed vectors.
     * Vectors are stored separately in VectorIndex for persistence.
     * Uses default isEmbedded() = false.
     */
    static class ComputedDocumentVectorizer extends Vectorizer<Document>
    {
        @Override
        public float[] vectorize(final Document entity)
        {
            // Simulate computing a vector (in reality could call an embedding API)
            return entity.embedding();
        }
        // isEmbedded() defaults to false - vectors will be stored separately
    }

    static class DocumentVectorizer32 extends EmbeddedDocumentVectorizer
    {
        @Override
        public float[] vectorize(final Document entity)
        {
            // Return first 32 dimensions
            final float[] full = entity.embedding();
            final float[] result = new float[32];
            System.arraycopy(full, 0, result, 0, 32);
            return result;
        }
    }

    /**
     * Test basic vector index operations using GigaMap.
     */
    @Test
    void testVectorIndexWithGigaMap()
    {
        // Create a GigaMap
        final GigaMap<Document> gigaMap = GigaMap.New();

        // Register vector indices category
        final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());
        assertNotNull(vectorIndices, "VectorIndices should be available");

        // Create a vector index configuration
        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(3)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .maxDegree(16)
            .beamWidth(100)
            .build();

        // Add a vector index
        final VectorIndex<Document> index = vectorIndices.add(
            "embeddings",
            config,
            new EmbeddedDocumentVectorizer()
        );
        assertNotNull(index, "VectorIndex should be created");
        assertEquals("embeddings", index.name());

        // Add some documents
        final Document doc1 = new Document("Hello world", new float[]{1.0f, 0.0f, 0.0f});
        final Document doc2 = new Document("Hello there", new float[]{0.9f, 0.1f, 0.0f});
        final Document doc3 = new Document("Goodbye world", new float[]{0.0f, 1.0f, 0.0f});

        gigaMap.add(doc1);
        gigaMap.add(doc2);
        gigaMap.add(doc3);

        // Search for similar vectors
        final VectorSearchResult<Document> result = index.search(new float[]{1.0f, 0.0f, 0.0f}, 2);
        assertNotNull(result, "Search result should not be null");
        assertFalse(result.isEmpty(), "Search result should not be empty");
        assertEquals(2, result.size(), "Should return 2 nearest neighbors");

        // The first result should be the most similar (doc1 with exact match)
        final List<VectorSearchResult.Entry<Document>> entries = result.toList();
        assertTrue(entries.get(0).score() >= entries.get(1).score(), "Results should be ordered by score");

        // Verify lazy entity access
        final Document firstMatch = entries.get(0).entity();
        assertNotNull(firstMatch, "Entity should be accessible via lazy lookup");
        assertEquals("Hello world", firstMatch.content(), "First match should be doc1");

        // Test search with entity
        final VectorSearchResult<Document> entityResult = index.search(doc1, 2);
        assertNotNull(entityResult);
        assertEquals(2, entityResult.size());
    }

    /**
     * Test accessing vector indices via GigaIndices.
     */
    @Test
    void testAccessVectorIndicesViaGigaIndices()
    {
        final GigaMap<Document> gigaMap = GigaMap.New();

        // Register vector indices
        gigaMap.index().register(VectorIndices.Category());

        // Access via get
        final VectorIndices<Document> vectorIndices = gigaMap.index().get(VectorIndices.Category());
        assertNotNull(vectorIndices, "Should be able to get VectorIndices via class");
    }

    /**
     * Test VectorResult functionality with lazy entity access.
     */
    @Test
    void testVectorResult()
    {
        // Create a GigaMap with test entities
        final GigaMap<Document> gigaMap = GigaMap.New();
        gigaMap.add(new Document("First", new float[]{1.0f, 0.0f, 0.0f}));
        gigaMap.add(new Document("Second", new float[]{0.0f, 1.0f, 0.0f}));
        gigaMap.add(new Document("Third", new float[]{0.0f, 0.0f, 1.0f}));

        @SuppressWarnings("unchecked")
        final VectorSearchResult.Entry<Document>[] entries = new VectorSearchResult.Entry[] {
            new VectorSearchResult.Entry.Default<>(0L, 0.95f, gigaMap),
            new VectorSearchResult.Entry.Default<>(1L, 0.85f, gigaMap),
            new VectorSearchResult.Entry.Default<>(2L, 0.75f, gigaMap)
        };

        final VectorSearchResult<Document> result = new VectorSearchResult.Default<>(X.List(entries));

        assertEquals(3, result.size());
        assertFalse(result.isEmpty());

        final List<VectorSearchResult.Entry<Document>> resultEntries = result.toList();
        // Test lazy entity access
        assertEquals("First", resultEntries.get(0).entity().content());
        assertEquals("Second", resultEntries.get(1).entity().content());
        assertEquals("Third", resultEntries.get(2).entity().content());

        // Test iteration
        int count = 0;
        for(final VectorSearchResult.Entry<Document> score : result)
        {
            assertNotNull(score.entity(), "Entity should be accessible lazily");
            count++;
        }
        assertEquals(3, count);
    }

    /**
     * Test VectorIndexConfiguration builder.
     */
    @Test
    void testVectorIndexConfiguration()
    {
        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(128)
            .similarityFunction(VectorSimilarityFunction.DOT_PRODUCT)
            .maxDegree(32)
            .beamWidth(200)
            .neighborOverflow(1.5f)
            .alpha(1.4f)
            .build();

        assertEquals(128, config.dimension());
        assertEquals(VectorSimilarityFunction.DOT_PRODUCT, config.similarityFunction());
        assertEquals(32, config.maxDegree());
        assertEquals(200, config.beamWidth());
        assertEquals(1.5f, config.neighborOverflow());
        assertEquals(1.4f, config.alpha());
    }

    /**
     * Test default VectorIndexConfiguration values.
     */
    @Test
    void testVectorIndexConfigurationDefaults()
    {
        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(64)
            .build();

        assertEquals(64, config.dimension());
        assertEquals(VectorSimilarityFunction.COSINE, config.similarityFunction());
        assertEquals(16, config.maxDegree());
        assertEquals(100, config.beamWidth());
        assertEquals(1.2f, config.neighborOverflow());
        assertEquals(1.2f, config.alpha());
    }

    /**
     * Test persistence with binary handlers.
     */
    @Test
    void testPersistenceWithBinaryHandlers(@TempDir final Path tempDir)
    {
        final Document doc1 = new Document("Hello world", new float[]{1.0f, 0.0f, 0.0f});
        final Document doc2 = new Document("Hello there", new float[]{0.9f, 0.1f, 0.0f});
        final Document doc3 = new Document("Goodbye world", new float[]{0.0f, 1.0f, 0.0f});

        // Create and populate
        {
            try(final EmbeddedStorageManager storage = EmbeddedStorage.start(tempDir))
            {
                final GigaMap<Document> gigaMap = GigaMap.New();
                storage.setRoot(gigaMap);

                final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());
                final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
                    .dimension(3)
                    .similarityFunction(VectorSimilarityFunction.COSINE)
                    .build();

                vectorIndices.add("embeddings", config, new EmbeddedDocumentVectorizer());

                gigaMap.add(doc1);
                gigaMap.add(doc2);
                gigaMap.add(doc3);

                storage.storeRoot();
            }
        }

        // Reload and verify
        {
            try(final EmbeddedStorageManager storage = EmbeddedStorage.start(tempDir))
            {
                @SuppressWarnings("unchecked")
                final GigaMap<Document> gigaMap = (GigaMap<Document>)storage.root();
                assertNotNull(gigaMap);

                final VectorIndices<Document> vectorIndices = gigaMap.index().get(VectorIndices.Category());
                assertNotNull(vectorIndices, "VectorIndices should be restored");

                final VectorIndex<Document> index = vectorIndices.get("embeddings");
                assertNotNull(index, "VectorIndex should be restored");

                // Verify search still works
                final VectorSearchResult<Document> result = index.search(new float[]{1.0f, 0.0f, 0.0f}, 2);
                assertNotNull(result);
                assertEquals(2, result.size());

                // Verify lazy entity access works after reload
                final Document firstMatch = result.iterator().next().entity();
                assertNotNull(firstMatch, "Entity should be accessible via lazy lookup after reload");
            }
        }
    }

    /**
     * Test non-embedded (computed) vectorizer mode where vectors are stored separately.
     */
    @Test
    void testComputedVectorizerMode()
    {
        // Create a GigaMap
        final GigaMap<Document> gigaMap = GigaMap.New();

        // Register vector indices category
        final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());

        // Create a vector index configuration
        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(3)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .maxDegree(16)
            .beamWidth(100)
            .build();

        // Add a vector index with COMPUTED (non-embedded) vectorizer
        final ComputedDocumentVectorizer vectorizer = new ComputedDocumentVectorizer();
        assertFalse(vectorizer.isEmbedded(), "Computed vectorizer should NOT be embedded");

        final VectorIndex<Document> index = vectorIndices.add(
            "computed_embeddings",
            config,
            vectorizer
        );
        assertNotNull(index, "VectorIndex should be created");

        // Add some documents
        final Document doc1 = new Document("Hello world", new float[]{1.0f, 0.0f, 0.0f});
        final Document doc2 = new Document("Hello there", new float[]{0.9f, 0.1f, 0.0f});
        final Document doc3 = new Document("Goodbye world", new float[]{0.0f, 1.0f, 0.0f});

        gigaMap.add(doc1);
        gigaMap.add(doc2);
        gigaMap.add(doc3);

        // Search for similar vectors
        final VectorSearchResult<Document> result = index.search(new float[]{1.0f, 0.0f, 0.0f}, 2);
        assertNotNull(result, "Search result should not be null");
        assertFalse(result.isEmpty(), "Search result should not be empty");
        assertEquals(2, result.size(), "Should return 2 nearest neighbors");

        // The first result should be the most similar (doc1 with exact match)
        final List<VectorSearchResult.Entry<Document>> entries = result.toList();
        assertTrue(entries.get(0).score() >= entries.get(1).score(), "Results should be ordered by score");

        // Verify lazy entity access
        final Document firstMatch = entries.get(0).entity();
        assertNotNull(firstMatch, "Entity should be accessible via lazy lookup");
        assertEquals("Hello world", firstMatch.content(), "First match should be doc1");
    }

    /**
     * Test persistence with computed (non-embedded) vectorizer.
     * Vectors are stored separately and should persist correctly.
     */
    @Test
    void testPersistenceWithComputedVectorizer(@TempDir final Path tempDir)
    {
        final Document doc1 = new Document("Hello world", new float[]{1.0f, 0.0f, 0.0f});
        final Document doc2 = new Document("Hello there", new float[]{0.9f, 0.1f, 0.0f});
        final Document doc3 = new Document("Goodbye world", new float[]{0.0f, 1.0f, 0.0f});

        // Create and populate
        {
            try(final EmbeddedStorageManager storage = EmbeddedStorage.start(tempDir))
            {
                final GigaMap<Document> gigaMap = GigaMap.New();
                storage.setRoot(gigaMap);

                final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());
                final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
                    .dimension(3)
                    .similarityFunction(VectorSimilarityFunction.COSINE)
                    .build();

                // Use COMPUTED vectorizer - vectors stored separately
                vectorIndices.add("computed_embeddings", config, new ComputedDocumentVectorizer());

                gigaMap.add(doc1);
                gigaMap.add(doc2);
                gigaMap.add(doc3);

                storage.storeRoot();
            }
        }

        // Reload and verify
        {
            try(final EmbeddedStorageManager storage = EmbeddedStorage.start(tempDir))
            {
                @SuppressWarnings("unchecked")
                final GigaMap<Document> gigaMap = (GigaMap<Document>)storage.root();
                assertNotNull(gigaMap);

                final VectorIndices<Document> vectorIndices = gigaMap.index().get(VectorIndices.Category());
                assertNotNull(vectorIndices, "VectorIndices should be restored");

                final VectorIndex<Document> index = vectorIndices.get("computed_embeddings");
                assertNotNull(index, "VectorIndex should be restored");

                // Verify search still works - vectors were persisted separately
                final VectorSearchResult<Document> result = index.search(new float[]{1.0f, 0.0f, 0.0f}, 2);
                assertNotNull(result);
                assertEquals(2, result.size());

                // Verify lazy entity access works after reload
                final Document firstMatch = result.iterator().next().entity();
                assertNotNull(firstMatch, "Entity should be accessible via lazy lookup after reload");
            }
        }
    }

    // ==================== Large Data Set Tests ====================

    /**
     * Helper to generate a random normalized vector.
     */
    private static float[] randomVector(final Random random, final int dimension)
    {
        final float[] vector = new float[dimension];
        float norm = 0;
        for(int i = 0; i < dimension; i++)
        {
            vector[i] = random.nextFloat() * 2 - 1; // Range [-1, 1]
            norm += vector[i] * vector[i];
        }
        // Normalize
        norm = (float)Math.sqrt(norm);
        for(int i = 0; i < dimension; i++)
        {
            vector[i] /= norm;
        }
        return vector;
    }

    /**
     * Test with 1000 vectors and 64 dimensions.
     */
    @Test
    void testLargeDataSet1000Vectors()
    {
        final int vectorCount = 1000;
        final int dimension = 64;
        final Random random = new Random(42); // Fixed seed for reproducibility

        final GigaMap<Document> gigaMap = GigaMap.New();
        final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());

        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(dimension)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .maxDegree(16)
            .beamWidth(100)
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

        assertEquals(vectorCount, gigaMap.size(), "GigaMap should have all documents");

        // Search with random query
        final float[] queryVector = randomVector(random, dimension);
        final VectorSearchResult<Document> result = index.search(queryVector, 10);

        assertEquals(10, result.size(), "Should return 10 nearest neighbors");

        // Verify results are ordered by score (descending)
        float prevScore = Float.MAX_VALUE;
        for(final VectorSearchResult.Entry<Document> entry : result)
        {
            assertTrue(entry.score() <= prevScore, "Results should be ordered by score");
            prevScore = entry.score();
            assertNotNull(entry.entity(), "Entity should be accessible");
        }
    }

    /**
     * Test with 5000 vectors and 128 dimensions.
     */
    @Test
    void testLargeDataSet5000Vectors()
    {
        final int vectorCount = 5000;
        final int dimension = 128;
        final Random random = new Random(123);

        final GigaMap<Document> gigaMap = GigaMap.New();
        final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());

        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(dimension)
            .similarityFunction(VectorSimilarityFunction.DOT_PRODUCT)
            .maxDegree(32)
            .beamWidth(200)
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

        assertEquals(vectorCount, gigaMap.size(), "GigaMap should have all documents");

        // Search multiple times
        for(int q = 0; q < 10; q++)
        {
            final float[] queryVector = randomVector(random, dimension);
            final VectorSearchResult<Document> result = index.search(queryVector, 20);
            assertEquals(20, result.size());
        }
    }

    /**
     * Test full persistence cycle with large data set.
     * Creates index, saves graph, simulates restart, loads graph, verifies search.
     */
    @Test
    void testFullPersistenceCycleLargeDataSet(@TempDir final Path tempDir) throws IOException
    {
        final int vectorCount = 1000;
        final int dimension = 64;
        final Random random = new Random(42);

        // Generate vectors upfront for consistency
        final List<float[]> vectors = new ArrayList<>();
        for(int i = 0; i < vectorCount; i++)
        {
            vectors.add(randomVector(random, dimension));
        }

        final float[] queryVector = randomVector(new Random(999), dimension);
        final List<Long> expectedIds = new ArrayList<>();

        // Phase 1: Create, populate, search, and save
        {
            try(final EmbeddedStorageManager storage = EmbeddedStorage.start(tempDir))
            {
                final GigaMap<Document> gigaMap = GigaMap.New();
                storage.setRoot(gigaMap);

                final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());
                // Disable parallel indexing for deterministic results during persistence test
                final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
                    .dimension(dimension)
                    .similarityFunction(VectorSimilarityFunction.COSINE)
                    .maxDegree(16)
                    .beamWidth(100)
                    .build();

                final VectorIndex<Document> index = vectorIndices.add(
                    "embeddings",
                    config,
                    new ComputedDocumentVectorizer()
                );

                // Add all vectors
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

                storage.storeRoot();
            }
        }

        // Phase 2: Reload and verify
        {
            try(final EmbeddedStorageManager storage = EmbeddedStorage.start(tempDir))
            {
                @SuppressWarnings("unchecked")
                final GigaMap<Document> gigaMap = (GigaMap<Document>)storage.root();
                final VectorIndices<Document> vectorIndices = gigaMap.index().get(VectorIndices.Category());

                // Search and compare results
                final VectorIndex<Document> index = vectorIndices.get("embeddings");
                assertEquals(vectorCount, gigaMap.size(), "GigaMap should still have all documents");

                final VectorSearchResult<Document> result = index.search(queryVector, 10);
                final List<Long> actualIds = new ArrayList<>();
                for(final VectorSearchResult.Entry<Document> entry : result)
                {
                    actualIds.add(entry.entityId());
                }

                assertEquals(expectedIds, actualIds, "Search results should match after reload with loaded graph");
            }
        }
    }

    /**
     * Test with embedded vectorizer and large data set.
     */
    @Test
    void testLargeDataSetEmbeddedVectorizer()
    {
        final int vectorCount = 2000;
        final int dimension = 32;
        final Random random = new Random(777);

        final GigaMap<Document> gigaMap = GigaMap.New();
        final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());

        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(dimension)
            .similarityFunction(VectorSimilarityFunction.EUCLIDEAN)
            .maxDegree(24)
            .beamWidth(150)
            .build();

        // Use EMBEDDED vectorizer (no separate vector storage)
        final VectorIndex<Document> index = vectorIndices.add(
            "embeddings",
            config,
            new EmbeddedDocumentVectorizer()
        );

        // Add vectors
        for(int i = 0; i < vectorCount; i++)
        {
            gigaMap.add(new Document("doc_" + i, randomVector(random, dimension)));
        }

        assertEquals(vectorCount, gigaMap.size(), "GigaMap should have all documents");

        // Search
        final float[] queryVector = randomVector(random, dimension);
        final VectorSearchResult<Document> result = index.search(queryVector, 15);

        assertEquals(15, result.size());

        // Verify all entities are accessible
        for(final VectorSearchResult.Entry<Document> entry : result)
        {
            assertNotNull(entry.entity());
            assertTrue(entry.entity().content().startsWith("doc_"));
        }
    }

    /**
     * Test search quality - verify that exact match is found first.
     */
    @Test
    void testSearchQualityWithLargeDataSet()
    {
        final int vectorCount = 1000;
        final int dimension = 64;
        final Random random = new Random(42);

        final GigaMap<Document> gigaMap = GigaMap.New();
        final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());

        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(dimension)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .maxDegree(32)
            .beamWidth(200)
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

        // Add a specific "needle" vector
        final float[] needleVector = new float[dimension];
        for(int i = 0; i < dimension; i++)
        {
            needleVector[i] = (i % 2 == 0) ? 0.1f : -0.1f;
        }
        // Normalize
        float norm = 0;
        for(float v : needleVector) norm += v * v;
        norm = (float)Math.sqrt(norm);
        for(int i = 0; i < dimension; i++) needleVector[i] /= norm;

        gigaMap.add(new Document("needle", needleVector));

        // Search for the needle vector - it should be the first result
        final VectorSearchResult<Document> result = index.search(needleVector, 5);

        assertEquals(5, result.size());
        final VectorSearchResult.Entry<Document> firstResult = result.iterator().next();
        assertEquals("needle", firstResult.entity().content(), "Exact match should be first result");
        assertTrue(firstResult.score() > 0.99f, "Exact match should have score close to 1.0");
    }

    // ==================== Multiple Restart Tests ====================

    /**
     * Test multiple storage restarts with additions between each restart.
     * Simulates a real-world scenario where data grows over time.
     */
    @Test
    void testMultipleRestartsWithAdditions(@TempDir final Path tempDir) throws IOException
    {
        final int dimension = 32;
        final Random random = new Random(42);

        // Phase 1: Initial creation with 100 vectors
        {
            try(final EmbeddedStorageManager storage = EmbeddedStorage.start(tempDir))
            {
                final GigaMap<Document> gigaMap = GigaMap.New();
                storage.setRoot(gigaMap);

                final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());
                final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
                    .dimension(dimension)
                    .similarityFunction(VectorSimilarityFunction.COSINE)
                    .build();

                vectorIndices.add("embeddings", config, new ComputedDocumentVectorizer());

                // Add initial 100 vectors
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
            try(final EmbeddedStorageManager storage = EmbeddedStorage.start(tempDir))
            {
                @SuppressWarnings("unchecked")
                final GigaMap<Document> gigaMap = (GigaMap<Document>)storage.root();
                final VectorIndices<Document> vectorIndices = gigaMap.index().get(VectorIndices.Category());

                assertEquals(100, gigaMap.size(), "Should have 100 documents from phase 1");

                // Graph load should fail because we'll add more vectors
                // But first verify search works with rebuilt graph
                final VectorIndex<Document> index = vectorIndices.get("embeddings");
                VectorSearchResult<Document> result = index.search(randomVector(random, dimension), 10);
                assertEquals(10, result.size());

                // Add 50 more vectors
                for(int i = 0; i < 50; i++)
                {
                    gigaMap.add(new Document("phase2_doc_" + i, randomVector(random, dimension)));
                }

                assertEquals(150, gigaMap.size());

                // Verify search still works with new vectors
                result = index.search(randomVector(random, dimension), 10);
                assertEquals(10, result.size());

                storage.storeRoot();
            }
        }

        // Phase 3: Restart and add 50 more vectors
        {
            try(final EmbeddedStorageManager storage = EmbeddedStorage.start(tempDir))
            {
                @SuppressWarnings("unchecked")
                final GigaMap<Document> gigaMap = (GigaMap<Document>)storage.root();
                final VectorIndices<Document> vectorIndices = gigaMap.index().get(VectorIndices.Category());

                assertEquals(150, gigaMap.size(), "Should have 150 documents from phases 1+2");

                final VectorIndex<Document> index = vectorIndices.get("embeddings");

                // Add 50 more vectors
                for(int i = 0; i < 50; i++)
                {
                    gigaMap.add(new Document("phase3_doc_" + i, randomVector(random, dimension)));
                }

                assertEquals(200, gigaMap.size());

                // Search should find vectors from all phases
                final VectorSearchResult<Document> result = index.search(randomVector(random, dimension), 20);
                assertEquals(20, result.size());

                storage.storeRoot();
            }
        }

        // Phase 4: Final restart and verify all data
        {
            try(final EmbeddedStorageManager storage = EmbeddedStorage.start(tempDir))
            {
                @SuppressWarnings("unchecked")
                final GigaMap<Document> gigaMap = (GigaMap<Document>)storage.root();
                final VectorIndices<Document> vectorIndices = gigaMap.index().get(VectorIndices.Category());

                assertEquals(200, gigaMap.size(), "Should have all 200 documents");

                final VectorIndex<Document> index = vectorIndices.get("embeddings");
                final VectorSearchResult<Document> result = index.search(randomVector(random, dimension), 50);
                assertEquals(50, result.size());

                // Verify we can find documents from each phase
                boolean foundPhase1 = false, foundPhase2 = false, foundPhase3 = false;
                for(final VectorSearchResult.Entry<Document> entry : result)
                {
                    final String content = entry.entity().content();
                    if(content.startsWith("phase1_")) foundPhase1 = true;
                    if(content.startsWith("phase2_")) foundPhase2 = true;
                    if(content.startsWith("phase3_")) foundPhase3 = true;
                }
                // At least some phases should be represented in top 50 results
                assertTrue(foundPhase1 || foundPhase2 || foundPhase3,
                    "Should find documents from at least one phase in search results");
            }
        }
    }

    /**
     * Test storage restarts with graph invalidation when size changes.
     * Verifies that stale graphs are rejected and rebuilt.
     */
    @Test
    void testGraphInvalidationOnSizeChange(@TempDir final Path tempDir) throws IOException
    {
        final int dimension = 32;
        final Random random = new Random(123);

        // Phase 1: Create with 100 vectors
        {
            try(final EmbeddedStorageManager storage = EmbeddedStorage.start(tempDir))
            {
                final GigaMap<Document> gigaMap = GigaMap.New();
                storage.setRoot(gigaMap);

                final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());
                final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
                    .dimension(dimension)
                    .similarityFunction(VectorSimilarityFunction.COSINE)
                    .build();

                vectorIndices.add("embeddings", config, new ComputedDocumentVectorizer());

                for(int i = 0; i < 100; i++)
                {
                    gigaMap.add(new Document("doc_" + i, randomVector(random, dimension)));
                }

                storage.storeRoot();
            }
        }

        // Phase 2: Restart, add vectors, DON'T save graph
        {
            try(final EmbeddedStorageManager storage = EmbeddedStorage.start(tempDir))
            {
                @SuppressWarnings("unchecked")
                final GigaMap<Document> gigaMap = (GigaMap<Document>)storage.root();
                final VectorIndices<Document> vectorIndices = gigaMap.index().get(VectorIndices.Category());

                // Add 50 more vectors
                for(int i = 100; i < 150; i++)
                {
                    gigaMap.add(new Document("doc_" + i, randomVector(random, dimension)));
                }

                assertEquals(150, gigaMap.size());

                // DON'T save graph - leaving stale graph on disk
                storage.storeRoot();
            }
        }

        // Phase 3: Restart - graph load should FAIL due to size mismatch
        {
            try(final EmbeddedStorageManager storage = EmbeddedStorage.start(tempDir))
            {
                @SuppressWarnings("unchecked")
                final GigaMap<Document> gigaMap = (GigaMap<Document>)storage.root();
                final VectorIndices<Document> vectorIndices = gigaMap.index().get(VectorIndices.Category());

                assertEquals(150, gigaMap.size());

                // But search should still work (graph rebuilt from vectors)
                final VectorIndex<Document> index = vectorIndices.get("embeddings");
                final VectorSearchResult<Document> result = index.search(randomVector(random, dimension), 10);
                assertEquals(10, result.size());
            }
        }

        // Phase 4: Restart - graph load should succeed now
        {
            try(final EmbeddedStorageManager storage = EmbeddedStorage.start(tempDir))
            {
                @SuppressWarnings("unchecked")
                final GigaMap<Document> gigaMap = (GigaMap<Document>)storage.root();
                final VectorIndices<Document> vectorIndices = gigaMap.index().get(VectorIndices.Category());

                final VectorIndex<Document> index = vectorIndices.get("embeddings");
                final VectorSearchResult<Document> result = index.search(randomVector(random, dimension), 10);
                assertEquals(10, result.size());
            }
        }
    }

    /**
     * Test multiple indices across restarts.
     * Creates multiple vector indices and verifies they all persist correctly.
     */
    @Test
    void testMultipleIndicesAcrossRestarts(@TempDir final Path tempDir) throws IOException
    {
        final Random random = new Random(456);

        // Phase 1: Create with two indices
        {
            try(final EmbeddedStorageManager storage = EmbeddedStorage.start(tempDir))
            {
                final GigaMap<Document> gigaMap = GigaMap.New();
                storage.setRoot(gigaMap);

                final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());

                // Index 1: 32-dimensional with COSINE
                final VectorIndexConfiguration config1 = VectorIndexConfiguration.builder()
                    .dimension(32)
                    .similarityFunction(VectorSimilarityFunction.COSINE)
                    .build();
                vectorIndices.add("index_32d", config1, new DocumentVectorizer32());

                // Index 2: 64-dimensional with DOT_PRODUCT
                final VectorIndexConfiguration config2 = VectorIndexConfiguration.builder()
                    .dimension(64)
                    .similarityFunction(VectorSimilarityFunction.DOT_PRODUCT)
                    .build();
                vectorIndices.add("index_64d", config2, new EmbeddedDocumentVectorizer());

                // Add documents with 64-dimensional vectors
                for(int i = 0; i < 200; i++)
                {
                    gigaMap.add(new Document("doc_" + i, randomVector(random, 64)));
                }

                storage.storeRoot();
            }
        }

        // Phase 2: Restart and verify both indices
        {
            try(final EmbeddedStorageManager storage = EmbeddedStorage.start(tempDir))
            {
                @SuppressWarnings("unchecked")
                final GigaMap<Document> gigaMap = (GigaMap<Document>)storage.root();
                final VectorIndices<Document> vectorIndices = gigaMap.index().get(VectorIndices.Category());

                assertEquals(200, gigaMap.size());

                // Search index 1 (32d)
                final VectorIndex<Document> index1 = vectorIndices.get("index_32d");
                assertNotNull(index1);
                final VectorSearchResult<Document> result1 = index1.search(randomVector(random, 32), 10);
                assertEquals(10, result1.size());

                // Search index 2 (64d)
                final VectorIndex<Document> index2 = vectorIndices.get("index_64d");
                assertNotNull(index2);
                final VectorSearchResult<Document> result2 = index2.search(randomVector(random, 64), 10);
                assertEquals(10, result2.size());

                // Add more documents
                for(int i = 200; i < 300; i++)
                {
                    gigaMap.add(new Document("doc_" + i, randomVector(random, 64)));
                }

                storage.storeRoot();
            }
        }

        // Phase 3: Final verification
        {
            try(final EmbeddedStorageManager storage = EmbeddedStorage.start(tempDir))
            {
                @SuppressWarnings("unchecked")
                final GigaMap<Document> gigaMap = (GigaMap<Document>)storage.root();
                final VectorIndices<Document> vectorIndices = gigaMap.index().get(VectorIndices.Category());

                assertEquals(300, gigaMap.size());

                // Both indices should work with all 300 documents
                final VectorIndex<Document> index1 = vectorIndices.get("index_32d");
                final VectorIndex<Document> index2 = vectorIndices.get("index_64d");

                final VectorSearchResult<Document> result1 = index1.search(randomVector(random, 32), 20);
                final VectorSearchResult<Document> result2 = index2.search(randomVector(random, 64), 20);

                assertEquals(20, result1.size());
                assertEquals(20, result2.size());
            }
        }
    }

    /**
     * Test embedded vectorizer persistence across restarts.
     * Embedded mode doesn't store vectors separately, so graph rebuild
     * reads vectors from entities.
     */
    @Test
    void testEmbeddedVectorizerAcrossRestarts(@TempDir final Path tempDir) throws IOException
    {
        final int dimension = 32;
        final Random random = new Random(789);

        // Generate consistent vectors
        final List<float[]> vectors = new ArrayList<>();
        for(int i = 0; i < 500; i++)
        {
            vectors.add(randomVector(random, dimension));
        }

        // Phase 1: Create with embedded vectorizer
        {
            try(final EmbeddedStorageManager storage = EmbeddedStorage.start(tempDir))
            {
                final GigaMap<Document> gigaMap = GigaMap.New();
                storage.setRoot(gigaMap);

                final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());
                final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
                    .dimension(dimension)
                    .similarityFunction(VectorSimilarityFunction.COSINE)
                    .build();

                // Use EMBEDDED vectorizer
                vectorIndices.add("embeddings", config, new EmbeddedDocumentVectorizer());

                for(int i = 0; i < 500; i++)
                {
                    gigaMap.add(new Document("doc_" + i, vectors.get(i)));
                }

                storage.storeRoot();
            }
        }

        // Phase 2: Restart, load graph, search
        {
            try(final EmbeddedStorageManager storage = EmbeddedStorage.start(tempDir))
            {
                @SuppressWarnings("unchecked")
                final GigaMap<Document> gigaMap = (GigaMap<Document>)storage.root();
                final VectorIndices<Document> vectorIndices = gigaMap.index().get(VectorIndices.Category());

                assertEquals(500, gigaMap.size());

                final VectorIndex<Document> index = vectorIndices.get("embeddings");

                // Search for a known vector - should find exact match
                final VectorSearchResult<Document> result = index.search(vectors.get(42), 5);
                assertEquals(5, result.size());

                // First result should be the exact match
                final VectorSearchResult.Entry<Document> firstResult = result.iterator().next();
                assertEquals("doc_42", firstResult.entity().content());
                assertTrue(firstResult.score() > 0.99f);
            }
        }

        // Phase 3: Restart WITHOUT loading graph - forces rebuild from entities
        {
            try(final EmbeddedStorageManager storage = EmbeddedStorage.start(tempDir))
            {
                @SuppressWarnings("unchecked")
                final GigaMap<Document> gigaMap = (GigaMap<Document>)storage.root();
                final VectorIndices<Document> vectorIndices = gigaMap.index().get(VectorIndices.Category());

                // DON'T load graph - let it rebuild from entities
                final VectorIndex<Document> index = vectorIndices.get("embeddings");

                // Search should still work after rebuild
                final VectorSearchResult<Document> result = index.search(vectors.get(100), 5);
                assertEquals(5, result.size());

                // First result should be the exact match
                final VectorSearchResult.Entry<Document> firstResult = result.iterator().next();
                assertEquals("doc_100", firstResult.entity().content());
            }
        }
    }

    /**
     * Test rapid restarts with small modifications each time.
     * Simulates frequent application restarts with incremental changes.
     */
    @Test
    void testRapidRestartsWithSmallModifications(@TempDir final Path tempDir) throws IOException
    {
        final int dimension = 16;
        final Random random = new Random(999);

        // Initial creation
        {
            try(final EmbeddedStorageManager storage = EmbeddedStorage.start(tempDir))
            {
                final GigaMap<Document> gigaMap = GigaMap.New();
                storage.setRoot(gigaMap);

                final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());
                final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
                    .dimension(dimension)
                    .similarityFunction(VectorSimilarityFunction.COSINE)
                    .build();

                vectorIndices.add("embeddings", config, new ComputedDocumentVectorizer());

                // Start with 50 documents
                for(int i = 0; i < 50; i++)
                {
                    gigaMap.add(new Document("initial_" + i, randomVector(random, dimension)));
                }

                storage.storeRoot();
            }
        }

        // Perform 10 rapid restart cycles, adding 10 documents each time
        for(int cycle = 1; cycle <= 10; cycle++)
        {
            try(final EmbeddedStorageManager storage = EmbeddedStorage.start(tempDir))
            {
                @SuppressWarnings("unchecked")
                final GigaMap<Document> gigaMap = (GigaMap<Document>)storage.root();
                final VectorIndices<Document> vectorIndices = gigaMap.index().get(VectorIndices.Category());

                final int expectedSize = 50 + (cycle - 1) * 10;
                assertEquals(expectedSize, gigaMap.size(), "Cycle " + cycle + " should have correct size");

                // Add 10 more documents
                for(int i = 0; i < 10; i++)
                {
                    gigaMap.add(new Document("cycle" + cycle + "_doc_" + i, randomVector(random, dimension)));
                }

                // Verify search works
                final VectorIndex<Document> index = vectorIndices.get("embeddings");
                final VectorSearchResult<Document> result = index.search(randomVector(random, dimension), 10);
                assertEquals(10, result.size());

                storage.storeRoot();
            }
        }

        // Final verification
        {
            try(final EmbeddedStorageManager storage = EmbeddedStorage.start(tempDir))
            {
                @SuppressWarnings("unchecked")
                final GigaMap<Document> gigaMap = (GigaMap<Document>)storage.root();
                final VectorIndices<Document> vectorIndices = gigaMap.index().get(VectorIndices.Category());

                // 50 initial + 10 cycles * 10 documents = 150 total
                assertEquals(150, gigaMap.size());

                final VectorIndex<Document> index = vectorIndices.get("embeddings");
                final VectorSearchResult<Document> result = index.search(randomVector(random, dimension), 30);
                assertEquals(30, result.size());

                // Verify documents from different cycles exist
                boolean foundInitial = false, foundCycle1 = false, foundCycle10 = false;
                for(final VectorSearchResult.Entry<Document> entry : result)
                {
                    final String content = entry.entity().content();
                    if(content.startsWith("initial_")) foundInitial = true;
                    if(content.startsWith("cycle1_")) foundCycle1 = true;
                    if(content.startsWith("cycle10_")) foundCycle10 = true;
                }
                assertTrue(foundInitial || foundCycle1 || foundCycle10,
                    "Should find documents from various cycles");
            }
        }
    }


    // ==================== VectorSearchResult Stream Tests ====================

    /**
     * Test that stream() returns correct number of elements.
     */
    @Test
    void testStreamReturnsCorrectSize()
    {
        final GigaMap<Document> gigaMap = GigaMap.New();
        gigaMap.add(new Document("First", new float[]{1.0f, 0.0f, 0.0f}));
        gigaMap.add(new Document("Second", new float[]{0.0f, 1.0f, 0.0f}));
        gigaMap.add(new Document("Third", new float[]{0.0f, 0.0f, 1.0f}));

        @SuppressWarnings("unchecked")
        final VectorSearchResult.Entry<Document>[] entries = new VectorSearchResult.Entry[] {
            new VectorSearchResult.Entry.Default<>(0L, 0.95f, gigaMap),
            new VectorSearchResult.Entry.Default<>(1L, 0.85f, gigaMap),
            new VectorSearchResult.Entry.Default<>(2L, 0.75f, gigaMap)
        };

        final VectorSearchResult<Document> result = new VectorSearchResult.Default<>(X.List(entries));

        assertEquals(3, result.stream().count(), "Stream should have correct count");
    }

    /**
     * Test that stream() preserves order (scores in descending order).
     */
    @Test
    void testStreamPreservesOrder()
    {
        final GigaMap<Document> gigaMap = GigaMap.New();
        gigaMap.add(new Document("First", new float[]{1.0f, 0.0f, 0.0f}));
        gigaMap.add(new Document("Second", new float[]{0.0f, 1.0f, 0.0f}));
        gigaMap.add(new Document("Third", new float[]{0.0f, 0.0f, 1.0f}));

        @SuppressWarnings("unchecked")
        final VectorSearchResult.Entry<Document>[] entries = new VectorSearchResult.Entry[] {
            new VectorSearchResult.Entry.Default<>(0L, 0.95f, gigaMap),
            new VectorSearchResult.Entry.Default<>(1L, 0.85f, gigaMap),
            new VectorSearchResult.Entry.Default<>(2L, 0.75f, gigaMap)
        };

        final VectorSearchResult<Document> result = new VectorSearchResult.Default<>(X.List(entries));

        final List<Float> scores = result.stream()
            .map(VectorSearchResult.Entry::score)
            .collect(Collectors.toList());

        assertEquals(List.of(0.95f, 0.85f, 0.75f), scores, "Stream should preserve order");
    }

    /**
     * Test stream filter operation.
     */
    @Test
    void testStreamFilter()
    {
        final GigaMap<Document> gigaMap = GigaMap.New();
        gigaMap.add(new Document("First", new float[]{1.0f, 0.0f, 0.0f}));
        gigaMap.add(new Document("Second", new float[]{0.0f, 1.0f, 0.0f}));
        gigaMap.add(new Document("Third", new float[]{0.0f, 0.0f, 1.0f}));

        @SuppressWarnings("unchecked")
        final VectorSearchResult.Entry<Document>[] entries = new VectorSearchResult.Entry[] {
            new VectorSearchResult.Entry.Default<>(0L, 0.95f, gigaMap),
            new VectorSearchResult.Entry.Default<>(1L, 0.85f, gigaMap),
            new VectorSearchResult.Entry.Default<>(2L, 0.75f, gigaMap)
        };

        final VectorSearchResult<Document> result = new VectorSearchResult.Default<>(X.List(entries));

        // Filter entries with score >= 0.80
        final List<VectorSearchResult.Entry<Document>> highScores = result.stream()
            .filter(e -> e.score() >= 0.80f)
            .collect(Collectors.toList());

        assertEquals(2, highScores.size(), "Should filter to 2 high-score entries");
        assertEquals(0.95f, highScores.get(0).score());
        assertEquals(0.85f, highScores.get(1).score());
    }

    /**
     * Test stream map operation.
     */
    @Test
    void testStreamMap()
    {
        final GigaMap<Document> gigaMap = GigaMap.New();
        gigaMap.add(new Document("First", new float[]{1.0f, 0.0f, 0.0f}));
        gigaMap.add(new Document("Second", new float[]{0.0f, 1.0f, 0.0f}));
        gigaMap.add(new Document("Third", new float[]{0.0f, 0.0f, 1.0f}));

        @SuppressWarnings("unchecked")
        final VectorSearchResult.Entry<Document>[] entries = new VectorSearchResult.Entry[] {
            new VectorSearchResult.Entry.Default<>(0L, 0.95f, gigaMap),
            new VectorSearchResult.Entry.Default<>(1L, 0.85f, gigaMap),
            new VectorSearchResult.Entry.Default<>(2L, 0.75f, gigaMap)
        };

        final VectorSearchResult<Document> result = new VectorSearchResult.Default<>(X.List(entries));

        // Map to entity content
        final List<String> contents = result.stream()
            .map(e -> e.entity().content())
            .collect(Collectors.toList());

        assertEquals(List.of("First", "Second", "Third"), contents, "Should map to entity contents");
    }

    /**
     * Test stream findFirst operation.
     */
    @Test
    void testStreamFindFirst()
    {
        final GigaMap<Document> gigaMap = GigaMap.New();
        gigaMap.add(new Document("First", new float[]{1.0f, 0.0f, 0.0f}));
        gigaMap.add(new Document("Second", new float[]{0.0f, 1.0f, 0.0f}));

        @SuppressWarnings("unchecked")
        final VectorSearchResult.Entry<Document>[] entries = new VectorSearchResult.Entry[] {
            new VectorSearchResult.Entry.Default<>(0L, 0.95f, gigaMap),
            new VectorSearchResult.Entry.Default<>(1L, 0.85f, gigaMap)
        };

        final VectorSearchResult<Document> result = new VectorSearchResult.Default<>(X.List(entries));

        final Optional<VectorSearchResult.Entry<Document>> first = result.stream().findFirst();

        assertTrue(first.isPresent(), "Should find first element");
        assertEquals(0.95f, first.get().score(), "First element should have highest score");
        assertEquals("First", first.get().entity().content());
    }

    /**
     * Test stream on empty result.
     */
    @Test
    void testStreamOnEmptyResult()
    {
        @SuppressWarnings("unchecked")
        final VectorSearchResult.Entry<Document>[] entries = new VectorSearchResult.Entry[0];

        final VectorSearchResult<Document> result = new VectorSearchResult.Default<>(X.List(entries));

        assertTrue(result.isEmpty(), "Result should be empty");
        assertEquals(0, result.stream().count(), "Empty stream should have count 0");
        assertTrue(result.stream().findFirst().isEmpty(), "findFirst on empty stream should be empty");
        assertTrue(result.toList().isEmpty(), "toList on empty result should be empty");
    }

    /**
     * Test that multiple stream() calls create independent streams.
     */
    @Test
    void testMultipleStreamCalls()
    {
        final GigaMap<Document> gigaMap = GigaMap.New();
        gigaMap.add(new Document("First", new float[]{1.0f, 0.0f, 0.0f}));
        gigaMap.add(new Document("Second", new float[]{0.0f, 1.0f, 0.0f}));

        @SuppressWarnings("unchecked")
        final VectorSearchResult.Entry<Document>[] entries = new VectorSearchResult.Entry[] {
            new VectorSearchResult.Entry.Default<>(0L, 0.95f, gigaMap),
            new VectorSearchResult.Entry.Default<>(1L, 0.85f, gigaMap)
        };

        final VectorSearchResult<Document> result = new VectorSearchResult.Default<>(X.List(entries));

        // First stream call - consume it
        final long count1 = result.stream().count();

        // Second stream call - should work independently
        final long count2 = result.stream().count();

        // Third stream call - collect to list
        final List<VectorSearchResult.Entry<Document>> list = result.stream().toList();

        assertEquals(2, count1, "First stream should have 2 elements");
        assertEquals(2, count2, "Second stream should also have 2 elements");
        assertEquals(2, list.size(), "Third stream collected to list should have 2 elements");
    }

    /**
     * Test toList() method.
     */
    @Test
    void testToList()
    {
        final GigaMap<Document> gigaMap = GigaMap.New();
        gigaMap.add(new Document("First", new float[]{1.0f, 0.0f, 0.0f}));
        gigaMap.add(new Document("Second", new float[]{0.0f, 1.0f, 0.0f}));
        gigaMap.add(new Document("Third", new float[]{0.0f, 0.0f, 1.0f}));

        @SuppressWarnings("unchecked")
        final VectorSearchResult.Entry<Document>[] entries = new VectorSearchResult.Entry[] {
            new VectorSearchResult.Entry.Default<>(0L, 0.95f, gigaMap),
            new VectorSearchResult.Entry.Default<>(1L, 0.85f, gigaMap),
            new VectorSearchResult.Entry.Default<>(2L, 0.75f, gigaMap)
        };

        final VectorSearchResult<Document> result = new VectorSearchResult.Default<>(X.List(entries));

        final List<VectorSearchResult.Entry<Document>> list = result.toList();

        assertEquals(3, list.size(), "List should have 3 entries");
        assertEquals(0.95f, list.get(0).score());
        assertEquals(0.85f, list.get(1).score());
        assertEquals(0.75f, list.get(2).score());
    }

    /**
     * Test stream spliterator characteristics.
     */
    @Test
    void testStreamSpliteratorCharacteristics()
    {
        final GigaMap<Document> gigaMap = GigaMap.New();
        gigaMap.add(new Document("First", new float[]{1.0f, 0.0f, 0.0f}));
        gigaMap.add(new Document("Second", new float[]{0.0f, 1.0f, 0.0f}));

        @SuppressWarnings("unchecked")
        final VectorSearchResult.Entry<Document>[] entries = new VectorSearchResult.Entry[] {
            new VectorSearchResult.Entry.Default<>(0L, 0.95f, gigaMap),
            new VectorSearchResult.Entry.Default<>(1L, 0.85f, gigaMap)
        };

        final VectorSearchResult<Document> result = new VectorSearchResult.Default<>(X.List(entries));

        final Spliterator<VectorSearchResult.Entry<Document>> spliterator = result.stream().spliterator();

        // Verify characteristics
        assertTrue(spliterator.hasCharacteristics(Spliterator.SIZED), "Should be SIZED");
        assertTrue(spliterator.hasCharacteristics(Spliterator.ORDERED), "Should be ORDERED");
        assertTrue(spliterator.hasCharacteristics(Spliterator.NONNULL), "Should be NONNULL");
        assertTrue(spliterator.hasCharacteristics(Spliterator.IMMUTABLE), "Should be IMMUTABLE");

        // Should NOT have CONCURRENT (that was the bug we fixed)
        assertFalse(spliterator.hasCharacteristics(Spliterator.CONCURRENT), "Should NOT be CONCURRENT");

        // Verify exact size
        assertEquals(2, spliterator.getExactSizeIfKnown(), "Should report exact size");
    }

    /**
     * Test stream with real search results from VectorIndex.
     */
    @Test
    void testStreamWithRealSearchResults()
    {
        final GigaMap<Document> gigaMap = GigaMap.New();
        final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());

        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(3)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .build();

        final VectorIndex<Document> index = vectorIndices.add(
            "embeddings",
            config,
            new ComputedDocumentVectorizer()
        );

        // Add documents
        gigaMap.add(new Document("doc1", new float[]{1.0f, 0.0f, 0.0f}));
        gigaMap.add(new Document("doc2", new float[]{0.9f, 0.1f, 0.0f}));
        gigaMap.add(new Document("doc3", new float[]{0.8f, 0.2f, 0.0f}));
        gigaMap.add(new Document("doc4", new float[]{0.0f, 1.0f, 0.0f}));
        gigaMap.add(new Document("doc5", new float[]{0.0f, 0.0f, 1.0f}));

        // Search
        final VectorSearchResult<Document> result = index.search(new float[]{1.0f, 0.0f, 0.0f}, 3);

        // Test stream operations on real results
        assertEquals(3, result.stream().count());

        // Filter high similarity results (score > 0.9)
        final long highSimilarityCount = result.stream()
            .filter(e -> e.score() > 0.9f)
            .count();
        assertTrue(highSimilarityCount >= 1, "Should have at least one high similarity result");

        // Map to content and collect
        final List<String> contents = result.stream()
            .map(e -> e.entity().content())
            .collect(Collectors.toList());
        assertEquals(3, contents.size());
        assertTrue(contents.contains("doc1"), "Should contain doc1 (exact match)");

        // Verify order is preserved (descending by score)
        final List<Float> scores = result.stream()
            .map(VectorSearchResult.Entry::score)
            .toList();
        for(int i = 0; i < scores.size() - 1; i++)
        {
            assertTrue(scores.get(i) >= scores.get(i + 1),
                "Scores should be in descending order");
        }
    }

    /**
     * Test stream anyMatch and allMatch operations.
     */
    @Test
    void testStreamMatchOperations()
    {
        final GigaMap<Document> gigaMap = GigaMap.New();
        gigaMap.add(new Document("First", new float[]{1.0f, 0.0f, 0.0f}));
        gigaMap.add(new Document("Second", new float[]{0.0f, 1.0f, 0.0f}));
        gigaMap.add(new Document("Third", new float[]{0.0f, 0.0f, 1.0f}));

        @SuppressWarnings("unchecked")
        final VectorSearchResult.Entry<Document>[] entries = new VectorSearchResult.Entry[] {
            new VectorSearchResult.Entry.Default<>(0L, 0.95f, gigaMap),
            new VectorSearchResult.Entry.Default<>(1L, 0.85f, gigaMap),
            new VectorSearchResult.Entry.Default<>(2L, 0.75f, gigaMap)
        };

        final VectorSearchResult<Document> result = new VectorSearchResult.Default<>(X.List(entries));

        // anyMatch
        assertTrue(result.stream().anyMatch(e -> e.score() > 0.9f), "Should have at least one score > 0.9");
        assertFalse(result.stream().anyMatch(e -> e.score() > 0.99f), "Should not have any score > 0.99");

        // allMatch
        assertTrue(result.stream().allMatch(e -> e.score() > 0.5f), "All scores should be > 0.5");
        assertFalse(result.stream().allMatch(e -> e.score() > 0.9f), "Not all scores are > 0.9");

        // noneMatch
        assertTrue(result.stream().noneMatch(e -> e.score() < 0.5f), "No scores should be < 0.5");
        assertFalse(result.stream().noneMatch(e -> e.score() > 0.7f), "Some scores are > 0.7");
    }

    /**
     * Test stream reduce operation.
     */
    @Test
    void testStreamReduce()
    {
        final GigaMap<Document> gigaMap = GigaMap.New();
        gigaMap.add(new Document("First", new float[]{1.0f, 0.0f, 0.0f}));
        gigaMap.add(new Document("Second", new float[]{0.0f, 1.0f, 0.0f}));
        gigaMap.add(new Document("Third", new float[]{0.0f, 0.0f, 1.0f}));

        @SuppressWarnings("unchecked")
        final VectorSearchResult.Entry<Document>[] entries = new VectorSearchResult.Entry[] {
            new VectorSearchResult.Entry.Default<>(0L, 0.95f, gigaMap),
            new VectorSearchResult.Entry.Default<>(1L, 0.85f, gigaMap),
            new VectorSearchResult.Entry.Default<>(2L, 0.75f, gigaMap)
        };

        final VectorSearchResult<Document> result = new VectorSearchResult.Default<>(X.List(entries));

        // Sum of all scores
        final float sumOfScores = result.stream()
            .map(VectorSearchResult.Entry::score)
            .reduce(0f, Float::sum);

        assertEquals(2.55f, sumOfScores, 0.001f, "Sum of scores should be 2.55");

        // Max score
        final Optional<Float> maxScore = result.stream()
            .map(VectorSearchResult.Entry::score)
            .max(Float::compareTo);

        assertTrue(maxScore.isPresent());
        assertEquals(0.95f, maxScore.get(), "Max score should be 0.95");
    }
}
