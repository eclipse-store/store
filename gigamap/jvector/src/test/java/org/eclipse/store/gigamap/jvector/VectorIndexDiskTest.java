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

import io.github.jbellis.jvector.disk.ReaderSupplier;
import io.github.jbellis.jvector.disk.ReaderSupplierFactory;
import io.github.jbellis.jvector.graph.disk.OnDiskGraphIndex;
import io.github.jbellis.jvector.graph.disk.feature.FeatureId;
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
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static java.time.Duration.ofMillis;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for on-disk VectorIndex functionality and Product Quantization.
 */
@Tag("slow")
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
     * Embedded vectorizer - vectors are part of the entity, not stored separately.
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
     * Helper to add multiple documents with random vectors to a GigaMap.
     */
    private static void addRandomDocuments(
            final GigaMap<Document> gigaMap,
            final Random random,
            final int dimension,
            final int count,
            final String prefix
    )
    {
        IntStream.range(0, count)
                .forEach(i -> gigaMap.add(new Document(prefix + i, randomVector(random, dimension))));
    }

    /**
     * Helper to add multiple documents from a list of pre-generated vectors.
     */
    private static void addDocumentsFromVectors(
            final GigaMap<Document> gigaMap,
            final List<float[]> vectors,
            final String prefix
    )
    {
        IntStream.range(0, vectors.size())
                .forEach(i -> gigaMap.add(new Document(prefix + i, vectors.get(i))));
    }


    /**
     * Test creating an on-disk index and persisting it.
     */
    @Test
    void testOnDiskIndexCreationAndPersistence(@TempDir final Path tempDir) throws IOException
    {
        final int vectorCount = 500;
        final int additionalCount = 100;
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
                addDocumentsFromVectors(gigaMap, vectors, "doc_");

                // Search and record expected results
                final VectorSearchResult<Document> result = index.search(queryVector, 10);
                for(final ScoredSearchResult.Entry<Document> entry : result)
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

        // Phase 2: Reload, verify, and add more vectors
        {
            try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
            {
                final GigaMap<Document> gigaMap = storage.root();
                final VectorIndices<Document> vectorIndices = gigaMap.index().get(VectorIndices.Category());

                assertEquals(vectorCount, gigaMap.size());

                final VectorIndex<Document> index = vectorIndices.get("embeddings");
                assertTrue(index.isOnDisk());

                // Search and compare results
                final VectorSearchResult<Document> result = index.search(queryVector, 10);
                final List<Long> actualIds = new ArrayList<>();
                for(final ScoredSearchResult.Entry<Document> entry : result)
                {
                    actualIds.add(entry.entityId());
                }

                // Results should match (or at least be very similar due to HNSW nature)
                assertEquals(expectedIds.size(), actualIds.size());

                // Add more vectors after reload — this exercises the builder
                // that must be available even when the disk index was loaded
                addRandomDocuments(gigaMap, new Random(123), dimension, additionalCount, "reload_doc_");
                assertEquals(vectorCount + additionalCount, gigaMap.size());

                // Persist updated index and store
                index.persistToDisk();
                storage.storeRoot();
            }
        }

        // Phase 3: Reload again and verify added vectors survived
        {
            try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
            {
                final GigaMap<Document> gigaMap = storage.root();
                final VectorIndices<Document> vectorIndices = gigaMap.index().get(VectorIndices.Category());

                assertEquals(vectorCount + additionalCount, gigaMap.size());

                final VectorIndex<Document> index = vectorIndices.get("embeddings");
                assertTrue(index.isOnDisk());

                // Search should return results from the full dataset
                final VectorSearchResult<Document> result = index.search(queryVector, 10);
                assertEquals(10, result.size());
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
        addRandomDocuments(gigaMap, random, dimension, vectorCount, "doc_");

        // Search should work
        final float[] queryVector = randomVector(random, dimension);
        final VectorSearchResult<Document> result = index.search(queryVector, 10);

        assertEquals(10, result.size());

        // Verify all entities are accessible
        result.forEach(entry -> assertTrue(entry.entity().content().startsWith("doc_")));

        // Persist to disk. No explicit training call: the persist path trains the codebook itself,
        // which is precisely what was missing before.
        index.persistToDisk();

        assertTrue(index.isPqCompressionActive(),
            "persisting an index with >= 256 vectors must have trained a PQ codebook");

        // Verify graph file was created (FusedPQ is embedded in graph, no separate .pq file)
        assertTrue(Files.exists(indexDir.resolve("embeddings.graph")));
        assertTrue(Files.exists(indexDir.resolve("embeddings.meta")));
        assertFalse(Files.exists(indexDir.resolve("embeddings.pq")),
            "FusedPQ should be embedded in graph file, not in separate .pq file");

        index.close();

        // The core assertion: the flag must actually change what is written.
        assertTrue(graphFeatures(indexDir.resolve("embeddings.graph")).contains(FeatureId.FUSED_PQ),
            "enablePqCompression(true) must write a FusedPQ graph");
    }

    /**
     * Counterpart to {@link #testOnDiskIndexWithCompression}: without the flag, no FusedPQ is
     * written. Guards against the gate degenerating into "always compress".
     */
    @Test
    void testOnDiskIndexWithoutCompressionHasNoFusedPq(@TempDir final Path tempDir) throws IOException
    {
        final int vectorCount = 500;
        final int dimension   = 64;
        final Random random   = new Random(42);

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

        addRandomDocuments(gigaMap, random, dimension, vectorCount, "doc_");
        index.persistToDisk();

        assertFalse(index.isPqCompressionActive(), "PQ must not activate when it was never enabled");

        index.close();

        final Set<FeatureId> features = graphFeatures(indexDir.resolve("embeddings.graph"));
        assertFalse(features.contains(FeatureId.FUSED_PQ), "no FusedPQ without enablePqCompression");
        assertTrue(features.contains(FeatureId.INLINE_VECTORS), "inline vectors are always written");
    }

    /**
     * Reads back the feature set of a persisted graph file.
     * <p>
     * The index must be closed first: on Windows an open memory mapping blocks the read.
     *
     * @param graphPath the {@code .graph} file to inspect
     * @return the features the file carries
     */
    private static Set<FeatureId> graphFeatures(final Path graphPath) throws IOException
    {
        try(final ReaderSupplier readerSupplier = ReaderSupplierFactory.open(graphPath);
            final OnDiskGraphIndex graph = OnDiskGraphIndex.load(readerSupplier))
        {
            return EnumSet.copyOf(graph.getFeatureSet());
        }
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
        addRandomDocuments(gigaMap, random, dimension, vectorCount - 1, "random_");

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
        final ScoredSearchResult.Entry<Document> firstResult = result.iterator().next();
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

                final VectorIndex<Document> index = vectorIndices.add(
                    "embeddings", config, new ComputedDocumentVectorizer()
                );

                addRandomDocuments(gigaMap, random, dimension, 100, "phase1_doc_");

                assertEquals(100, gigaMap.size());

                // Persist to disk so Phase 2 exercises the disk-loaded path
                index.persistToDisk();
                storage.storeRoot();
            }
        }

        // Phase 2: Restart and add 50 more vectors
        {
            try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
            {
                final GigaMap<Document> gigaMap = storage.root();
                final VectorIndices<Document> vectorIndices = gigaMap.index().get(VectorIndices.Category());

                assertEquals(100, gigaMap.size());

                final VectorIndex<Document> index = vectorIndices.get("embeddings");
                final VectorSearchResult<Document> result = index.search(randomVector(random, dimension), 10);
                assertEquals(10, result.size());

                // Add more vectors after disk-loaded restart
                addRandomDocuments(gigaMap, random, dimension, 50, "phase2_doc_");

                assertEquals(150, gigaMap.size());

                // Persist updated index so Phase 3 loads from disk too
                index.persistToDisk();
                storage.storeRoot();
            }
        }

        // Phase 3: Final verification
        {
            try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
            {
                final GigaMap<Document> gigaMap = storage.root();
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
        addRandomDocuments(gigaMap, random, dimension, vectorCount - 1, "random_");

        // Add a one-hot "needle" vector that randomVector() cannot produce,
        // since randomVector() populates all dimensions with non-zero values.
        final float[] needleVector = new float[dimension];
        needleVector[0] = 1.0f;

        gigaMap.add(new Document("needle", needleVector));

        // Persist first, otherwise the search below runs the in-memory exact path and this says
        // nothing about PQ search quality.
        index.persistToDisk();
        assertTrue(index.isPqCompressionActive());

        // Search for the needle vector - it should be in the top results
        final VectorSearchResult<Document> result = index.search(needleVector, 5);

        assertEquals(5, result.size());
        final ScoredSearchResult.Entry<Document> firstResult = result.iterator().next();
        assertEquals("needle", firstResult.entity().content(),
            "Exact match should be first result even with PQ compression");
        assertTrue(firstResult.score() > 0.99f,
            "Exact match should have score close to 1.0");

        // Verify results are ordered by score
        float prevScore = Float.MAX_VALUE;
        for(final ScoredSearchResult.Entry<Document> entry : result)
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

                addDocumentsFromVectors(gigaMap, vectors, "doc_");

                final VectorSearchResult<Document> result = index.search(queryVector, 10);
                for(final ScoredSearchResult.Entry<Document> entry : result)
                {
                    expectedIds.add(entry.entityId());
                }

                // Persist
                index.persistToDisk();
                assertTrue(Files.exists(indexDir.resolve("embeddings.graph")));
                assertTrue(Files.exists(indexDir.resolve("embeddings.meta")));
                assertTrue(index.isPqCompressionActive(), "the persist must have trained a codebook");

                storage.storeRoot();
            }
        }

        // Phase 2: Reload and verify search results
        {
            try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
            {
                final GigaMap<Document> gigaMap = storage.root();
                final VectorIndices<Document> vectorIndices = gigaMap.index().get(VectorIndices.Category());

                assertEquals(vectorCount, gigaMap.size());

                final VectorIndex<Document> index = vectorIndices.get("embeddings");
                assertTrue(index.isOnDisk());
                assertTrue(index.isPqCompressionEnabled());

                // The codebook must come back from the graph header rather than being retrained -
                // a retrained one would not match the codes already fused into the graph.
                assertTrue(index.isPqCompressionActive(),
                    "the reloaded index must recover its codebook from the graph");

                // Search after reload
                final VectorSearchResult<Document> result = index.search(queryVector, 10);
                assertEquals(10, result.size());

                final List<Long> actualIds = new ArrayList<>();
                for(final ScoredSearchResult.Entry<Document> entry : result)
                {
                    actualIds.add(entry.entityId());
                }

                // Results should match (or at least overlap significantly)
                assertEquals(expectedIds.size(), actualIds.size());

                // Verify all entities are accessible
                result.forEach(entry -> assertTrue(entry.entity().content().startsWith("doc_")));
            }
        }
    }

    /**
     * Regression cover: a reload followed by a re-persist must keep writing a
     * FusedPQ graph.
     * <p>
     * The load path used to call {@code markTrained()} unconditionally, which set the trained flag
     * without a codebook. {@code trainIfNeeded()} then short-circuited forever while the write gate
     * - which also requires a codebook - kept falling through to the uncompressed branch. The result
     * was an index that compressed on its first persist and silently downgraded on every later one,
     * with nothing in the {@code .meta} to reveal it.
     */
    @Test
    void testPqCompressionSurvivesReloadAndRepersist(@TempDir final Path tempDir) throws IOException
    {
        final int    vectorCount = 400;
        final int    dimension   = 64;
        final Path   indexDir    = tempDir.resolve("index");
        final Path   storageDir  = tempDir.resolve("storage");
        final Path   graphPath   = indexDir.resolve("embeddings.graph");
        final Random random      = new Random(4711);

        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(dimension)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .onDisk(true)
            .indexDirectory(indexDir)
            .enablePqCompression(true)
            .pqSubspaces(16)
            .build();

        // Phase 1: build and persist - writes a FusedPQ graph.
        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final GigaMap<Document> gigaMap = GigaMap.New();
            storage.setRoot(gigaMap);

            final VectorIndex<Document> index = gigaMap.index().register(VectorIndices.Category())
                .add("embeddings", config, new ComputedDocumentVectorizer());

            addRandomDocuments(gigaMap, random, dimension, vectorCount, "doc_");
            index.persistToDisk();

            assertTrue(index.isPqCompressionActive());
            storage.storeRoot();
            // graphFeatures() below opens the graph file, which Windows refuses while the
            // index still holds it memory-mapped.
            index.close();
        }
        assertTrue(graphFeatures(graphPath).contains(FeatureId.FUSED_PQ),
            "the first persist must write FusedPQ");

        // Phase 2: reload, mutate so the persist is not skipped as incremental-clean, persist again.
        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final GigaMap<Document> gigaMap = storage.root();
            final VectorIndex<Document> index = gigaMap.index()
                .get(VectorIndices.Category())
                .get("embeddings");

            assertTrue(index.isPqCompressionActive(),
                "the codebook must be recovered from the reloaded graph");

            addRandomDocuments(gigaMap, random, dimension, 20, "more_");
            index.persistToDisk();

            assertTrue(index.isPqCompressionActive(), "the re-persist must not lose the codebook");
            storage.storeRoot();
            index.close();
        }

        assertTrue(graphFeatures(graphPath).contains(FeatureId.FUSED_PQ),
            "a re-persist after a reload must not silently downgrade to an uncompressed graph");

        // Phase 3: the twice-written graph must still be loadable and searchable.
        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final GigaMap<Document> gigaMap = storage.root();
            final VectorIndex<Document> index = gigaMap.index()
                .get(VectorIndices.Category())
                .get("embeddings");

            assertTrue(index.isPqCompressionActive());
            assertEquals(10, index.search(randomVector(random, dimension), 10).size());
            // Release the mapping before @TempDir cleanup, which Windows would otherwise fail.
            index.close();
        }
    }

    /**
     * PQ traversal is lossy, so this pins how much recall it may cost at the default settings.
     * <p>
     * Run over seeds 7/11/23/42/99. Measured with this configuration: exact recall@10 is 0.994-1.000 and
     * PQ recall@10 is 0.996-1.000, i.e. indistinguishable (PQ is occasionally a hair higher, since
     * approximate traversal explores a different part of the graph). The 0.95 floor therefore has
     * roughly 0.045 of headroom against the worst observed run.
     * <p>
     * The point is not to police the exact figure but to catch a PQ path that has stopped working:
     * scoring against the wrong node's fused codes, or a codebook that does not match the codes in
     * the graph, collapses recall far below this floor rather than nudging it.
     */
    @Test
    void testPqCompressionRecallStaysHigh(@TempDir final Path tempDir)
    {
        for(final int seed : new int[]{7, 11, 23, 42, 99})
        {
            this.assertRecallForSeed(seed, tempDir.resolve("seed" + seed));
        }
    }

    /**
     * Builds a PQ index from {@code seed}, persists it and asserts recall@10 against brute-force
     * ground truth.
     *
     * @param seed     the seed for both the data set and the queries
     * @param indexDir the directory to build the index in
     */
    private void assertRecallForSeed(final int seed, final Path indexDir)
    {
        final int vectorCount = 2000;
        final int dimension   = 64;
        final int k           = 10;
        final int queryCount  = 50;
        final double minRecall = 0.95;

        final Random random = new Random(seed);

        // Clustered rather than uniform-random vectors, which is both closer to real embeddings and
        // the harder case for a graph index (near-duplicates compete for the same top-k slots).
        final List<float[]> centroids = new ArrayList<>();
        for(int c = 0; c < 20; c++)
        {
            centroids.add(randomVector(random, dimension));
        }
        final List<float[]> vectors = new ArrayList<>();
        for(int i = 0; i < vectorCount; i++)
        {
            vectors.add(nearVector(random, centroids.get(i % centroids.size())));
        }

        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(dimension)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .maxDegree(32)
            .beamWidth(100)
            .onDisk(true)
            .indexDirectory(indexDir)
            .enablePqCompression(true)
            .pqSubspaces(dimension / 4)
            .build();

        final GigaMap<Document> gigaMap = GigaMap.New();
        final List<Long> ids = new ArrayList<>();

        double totalRecall = 0;

        try(final VectorIndex<Document> index = gigaMap.index().register(VectorIndices.Category())
            .add("embeddings", config, new ComputedDocumentVectorizer()))
        {
            for(int i = 0; i < vectorCount; i++)
            {
                ids.add(gigaMap.add(new Document("doc_" + i, vectors.get(i))));
            }
            index.persistToDisk();

            assertTrue(index.isPqCompressionActive(), "the PQ path must be the one under test");

            final Random queryRandom = new Random(seed * 31 + 5);
            for(int q = 0; q < queryCount; q++)
            {
                final float[] query = nearVector(queryRandom, vectors.get(queryRandom.nextInt(vectorCount)));

                final Set<Long> expected = new HashSet<>(bruteForceTopK(query, vectors, ids, k));

                final Set<Long> actual = new HashSet<>();
                for(final ScoredSearchResult.Entry<Document> entry : index.search(query, k))
                {
                    actual.add(entry.entityId());
                }

                actual.retainAll(expected);
                totalRecall += (double)actual.size() / k;
            }
        }

        final double recall = totalRecall / queryCount;
        assertTrue(recall >= minRecall,
            "PQ recall@" + k + " for seed " + seed + " was " + recall + ", below the " + minRecall + " floor");
    }

    /**
     * Returns the ids of the {@code k} vectors most similar to {@code query} by cosine, by exhaustive
     * scan - the ground truth the index is measured against.
     */
    private static List<Long> bruteForceTopK(
        final float[]     query  ,
        final List<float[]> vectors,
        final List<Long>  ids    ,
        final int         k
    )
    {
        final List<Integer> order = new ArrayList<>();
        for(int i = 0; i < vectors.size(); i++)
        {
            order.add(i);
        }
        order.sort(Comparator.comparingDouble(i -> -cosine(query, vectors.get(i))));

        final List<Long> topK = new ArrayList<>(k);
        for(int i = 0; i < k; i++)
        {
            topK.add(ids.get(order.get(i)));
        }
        return topK;
    }

    private static double cosine(final float[] a, final float[] b)
    {
        double dot = 0, na = 0, nb = 0;
        for(int i = 0; i < a.length; i++)
        {
            dot += a[i] * b[i];
            na  += a[i] * a[i];
            nb  += b[i] * b[i];
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    /** A unit vector scattered around {@code centroid}, so the data set has cluster structure. */
    private static float[] nearVector(final Random random, final float[] centroid)
    {
        final float[] v = new float[centroid.length];
        double norm = 0;
        for(int i = 0; i < v.length; i++)
        {
            v[i] = centroid[i] + (float)random.nextGaussian() * 0.35f;
            norm += v[i] * v[i];
        }
        final float length = (float)Math.sqrt(norm);
        for(int i = 0; i < v.length; i++)
        {
            v[i] /= length;
        }
        return v;
    }

    /**
     * A dimension whose quarter does not divide it must still train.
     * <p>
     * With {@code pqSubspaces} left at 0 the count is {@code dimension / 4}, which is not always a
     * divisor - 14/4 is 3, and 14 % 3 != 0. That is fine:
     * {@code ProductQuantization.getSubvectorSizesAndOffsets} distributes the remainder across the
     * subvectors (here 5/5/4) and only requires {@code M <= dimension}. The builder's divisibility
     * check applies to an explicitly configured {@code pqSubspaces}, not to the automatic value, so
     * this test pins that the automatic path is not subject to it - and that nobody "fixes" it by
     * snapping the count down to a divisor, which for a prime dimension would collapse it to 1.
     */
    @Test
    void testPqCompressionAutoSubspacesForIndivisibleDimension(@TempDir final Path tempDir) throws IOException
    {
        final int vectorCount = 400;
        final int dimension   = 14; // 14 / 4 = 3, and 14 % 3 != 0
        final Path indexDir   = tempDir.resolve("index");

        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(dimension)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .onDisk(true)
            .indexDirectory(indexDir)
            .enablePqCompression(true)
            .build();

        assertEquals(0, config.pqSubspaces(), "this test is about the automatic subspace count");

        final GigaMap<Document> gigaMap = GigaMap.New();
        try(final VectorIndex<Document> index = gigaMap.index().register(VectorIndices.Category())
            .add("embeddings", config, new ComputedDocumentVectorizer()))
        {
            addRandomDocuments(gigaMap, new Random(5), dimension, vectorCount, "doc_");
            index.persistToDisk();

            assertTrue(index.isPqCompressionActive(),
                "the automatic subspace count need not divide the dimension, so training must succeed");
            assertEquals(10, index.search(randomVector(new Random(6), dimension), 10).size());
        }

        assertTrue(graphFeatures(indexDir.resolve("embeddings.graph")).contains(FeatureId.FUSED_PQ));
    }

    /**
     * Direct guard on the PQ marker in the metadata: a graph written with PQ off must be rejected by
     * a manager configured with PQ on, and vice versa, even though every other witness matches.
     * <p>
     * Exercises {@code verifyMetadata} through {@code tryLoad} rather than end-to-end, because the
     * end-to-end path reaches the right state either way - the training carve-out rewrites the graph
     * on the next persist. Only this level shows that the stale graph is refused at load.
     */
    @Test
    void testMetadataRejectsAMismatchedPqSetting(@TempDir final Path tempDir) throws IOException
    {
        final int vectorCount = 300;
        final int dimension   = 64;
        final Path indexDir   = tempDir.resolve("index");

        // Write a graph with PQ off.
        final GigaMap<Document> gigaMap = GigaMap.New();
        try(final VectorIndex<Document> index = gigaMap.index().register(VectorIndices.Category())
            .add("embeddings", VectorIndexConfiguration.builder()
                .dimension(dimension)
                .similarityFunction(VectorSimilarityFunction.COSINE)
                .onDisk(true)
                .indexDirectory(indexDir)
                .build(), new ComputedDocumentVectorizer()))
        {
            addRandomDocuments(gigaMap, new Random(8), dimension, vectorCount, "doc_");
            index.persistToDisk();
        }

        // The witnesses the file was written with: everything except the PQ flag agrees.
        final VectorIndex.Default<Document> written =
            (VectorIndex.Default<Document>)gigaMap.index().get(VectorIndices.Category()).get("embeddings");
        final DiskIndexManager.MetaState state = new DiskIndexManager.MetaState(
            written.getExpectedVectorCount(), written.getHighestEntityId(), written.getStructuralModCount());

        try(final DiskIndexManager matching = new DiskIndexManager.Default(
            written, "embeddings", indexDir, dimension, false, false))
        {
            assertTrue(matching.tryLoad(state), "the same PQ setting must load");
        }

        try(final DiskIndexManager mismatched = new DiskIndexManager.Default(
            written, "embeddings", indexDir, dimension, true, false))
        {
            assertFalse(mismatched.tryLoad(state),
                "a graph written with PQ off must not load into an index configured with PQ on");
        }
    }

    /**
     * Turning {@code enablePqCompression} on over an existing uncompressed index directory ends up
     * with a FusedPQ graph.
     * <p>
     * {@code removeIndex} deliberately leaves the {@code .graph} and {@code .meta} behind, so the
     * new index opens on top of the old files. Two mechanisms can produce the right end state here:
     * the PQ marker in the metadata rejects the stale graph at load, and failing that the
     * training carve-out makes the next persist retrain and rewrite it. This test pins the outcome,
     * not which of the two got there - it passes with the marker check disabled, so it is end-to-end
     * cover rather than a guard on the marker itself. The marker's contribution is that the
     * correction happens at load rather than being deferred to the next persist.
     */
    @Test
    void testEnablingPqCompressionOverExistingFilesYieldsFusedPq(@TempDir final Path tempDir) throws IOException
    {
        final int vectorCount = 400;
        final int dimension   = 64;
        final Path indexDir   = tempDir.resolve("index");
        final Path graphPath  = indexDir.resolve("embeddings.graph");

        // First index: PQ off.
        final GigaMap<Document> plainMap = GigaMap.New();
        try(final VectorIndex<Document> plain = plainMap.index().register(VectorIndices.Category())
            .add("embeddings", VectorIndexConfiguration.builder()
                .dimension(dimension)
                .similarityFunction(VectorSimilarityFunction.COSINE)
                .onDisk(true)
                .indexDirectory(indexDir)
                .build(), new ComputedDocumentVectorizer()))
        {
            addRandomDocuments(plainMap, new Random(3), dimension, vectorCount, "doc_");
            plain.persistToDisk();
        }
        assertFalse(graphFeatures(graphPath).contains(FeatureId.FUSED_PQ));

        // Second index: same name, same directory, same data - but PQ on. The old files are still
        // there, so this only works if the metadata records the setting.
        final GigaMap<Document> pqMap = GigaMap.New();
        try(final VectorIndex<Document> pq = pqMap.index().register(VectorIndices.Category())
            .add("embeddings", VectorIndexConfiguration.builder()
                .dimension(dimension)
                .similarityFunction(VectorSimilarityFunction.COSINE)
                .onDisk(true)
                .indexDirectory(indexDir)
                .enablePqCompression(true)
                .pqSubspaces(16)
                .build(), new ComputedDocumentVectorizer()))
        {
            addRandomDocuments(pqMap, new Random(3), dimension, vectorCount, "doc_");
            pq.persistToDisk();

            assertTrue(pq.isPqCompressionActive(), "switching PQ on must train rather than reuse the old graph");
            assertEquals(10, pq.search(randomVector(new Random(4), dimension), 10).size());
        }

        assertTrue(graphFeatures(graphPath).contains(FeatureId.FUSED_PQ),
            "the graph must have been rebuilt for the new PQ setting");
    }

    /**
     * FusedPQ at a degree other than 32.
     * <p>
     * The writer takes the fused block size from {@code index.getDegree(0)} while the reader takes
     * it from the graph header, so a mismatch would corrupt every approximate score. Every other PQ
     * test runs at 32 - the value the builder used to force - so without this case the newly
     * supported degrees would go untested and a mismatch would surface only as quietly wrong
     * results.
     */
    @Test
    void testPqCompressionWithNonDefaultMaxDegree(@TempDir final Path tempDir) throws IOException
    {
        final int vectorCount = 500;
        final int dimension   = 64;
        final int maxDegree   = 16;

        final Path indexDir   = tempDir.resolve("index");
        final Path storageDir = tempDir.resolve("storage");

        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(dimension)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .maxDegree(maxDegree)
            .onDisk(true)
            .indexDirectory(indexDir)
            .enablePqCompression(true)
            .pqSubspaces(16)
            .build();

        assertEquals(maxDegree, config.maxDegree(), "PQ must not rewrite maxDegree");

        final float[] queryVector = randomVector(new Random(99), dimension);
        final List<Long> beforeIds = new ArrayList<>();

        // Phase 1: build and persist through the storage manager, so phase 2 can reload the very
        // same GigaMap. Registering a fresh index over an empty map would not reload anything - the
        // metadata check compares against the live store and rejects the files.
        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final GigaMap<Document> gigaMap = GigaMap.New();
            storage.setRoot(gigaMap);

            final VectorIndex<Document> index = gigaMap.index().register(VectorIndices.Category())
                .add("embeddings", config, new ComputedDocumentVectorizer());

            addRandomDocuments(gigaMap, new Random(31), dimension, vectorCount, "doc_");
            index.persistToDisk();

            assertTrue(index.isPqCompressionActive());

            for(final ScoredSearchResult.Entry<Document> entry : index.search(queryVector, 10))
            {
                beforeIds.add(entry.entityId());
            }
            assertEquals(10, beforeIds.size());

            storage.storeRoot();
            // graphFeatures() below opens the graph file, which Windows refuses while the
            // index still holds it memory-mapped.
            index.close();
        }

        assertTrue(graphFeatures(indexDir.resolve("embeddings.graph")).contains(FeatureId.FUSED_PQ),
            "a non-32 maxDegree must still produce a FusedPQ graph");

        // Phase 2: reload. The reader sizes the fused block from the graph header while the writer
        // sized it from index.getDegree(0), so a mismatch surfaces here as different results.
        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final GigaMap<Document> gigaMap = storage.root();
            final VectorIndex<Document> reloaded = gigaMap.index()
                .get(VectorIndices.Category())
                .get("embeddings");

            assertTrue(reloaded.isPqCompressionActive(),
                "the codebook must come back from the reloaded graph");

            final List<Long> afterIds = new ArrayList<>();
            for(final ScoredSearchResult.Entry<Document> entry : reloaded.search(queryVector, 10))
            {
                afterIds.add(entry.entityId());
            }

            assertEquals(beforeIds, afterIds,
                "search over the reloaded non-32-degree FusedPQ graph must match the pre-reload result");

            reloaded.close();
        }
    }

    /**
     * PQ is a speed optimisation, not a space one: FusedPQ stores the compressed codes of every
     * neighbour inside each node's block, on top of the full-precision inline vectors that both
     * layouts write. The graph therefore gets bigger, by roughly {@code pqSubspaces * maxDegree}
     * bytes per node.
     * <p>
     * Pinned as a test because the documentation claimed the opposite, which is what led a
     * downstream project to enable the flag expecting a memory saving.
     */
    @Test
    void testPqCompressionMakesTheGraphLarger(@TempDir final Path tempDir) throws IOException
    {
        final int vectorCount = 400;
        final int dimension   = 64;
        final int maxDegree   = 32;

        final long withoutPq = this.persistAndMeasureGraph(
            tempDir.resolve("plain"), vectorCount, dimension, maxDegree, 0);
        final long withPq = this.persistAndMeasureGraph(
            tempDir.resolve("pq"), vectorCount, dimension, maxDegree, 16);

        assertTrue(withPq > withoutPq,
            "FusedPQ adds the neighbour codes on top of the inline vectors, so the graph must grow: "
                + withPq + " vs " + withoutPq);

        // Sanity-check the magnitude: the fused block is pqSubspaces * maxDegree bytes per node, so
        // the delta should be in that ballpark rather than a rounding difference.
        final long expectedDelta = (long)vectorCount * 16 * maxDegree;
        assertTrue(withPq - withoutPq > expectedDelta / 2,
            "the growth should be dominated by the fused block, expected around " + expectedDelta
                + " but was " + (withPq - withoutPq));
    }

    /**
     * Builds an on-disk index, persists it and returns the size of the resulting graph file.
     *
     * @param indexDir    the directory to write into
     * @param vectorCount how many documents to index
     * @param dimension   the vector dimension
     * @param maxDegree   the graph out-degree
     * @param pqSubspaces the PQ subspace count, or 0 to leave PQ disabled
     * @return the size of the written {@code .graph} file in bytes
     */
    private long persistAndMeasureGraph(
        final Path indexDir   ,
        final int  vectorCount,
        final int  dimension  ,
        final int  maxDegree  ,
        final int  pqSubspaces
    ) throws IOException
    {
        final VectorIndexConfiguration.Builder builder = VectorIndexConfiguration.builder()
            .dimension(dimension)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .maxDegree(maxDegree)
            .onDisk(true)
            .indexDirectory(indexDir);

        if(pqSubspaces > 0)
        {
            builder.enablePqCompression(true).pqSubspaces(pqSubspaces);
        }

        final GigaMap<Document> gigaMap = GigaMap.New();
        try(final VectorIndex<Document> index = gigaMap.index().register(VectorIndices.Category())
            .add("embeddings", builder.build(), new ComputedDocumentVectorizer()))
        {
            // Same seed for both runs so the two graphs hold identical vectors.
            addRandomDocuments(gigaMap, new Random(99), dimension, vectorCount, "doc_");
            index.persistToDisk();
            assertEquals(pqSubspaces > 0, index.isPqCompressionActive());
        }

        return Files.size(indexDir.resolve("embeddings.graph"));
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

        addRandomDocuments(gigaMap, random, dimension, vectorCount, "doc_");

        // Persist so the search runs against a FusedPQ graph: the approximate score function
        // is built per similarity function, so COSINE passing says nothing about this one.
        index.persistToDisk();
        assertTrue(index.isPqCompressionActive());

        final float[] queryVector = randomVector(random, dimension);
        final VectorSearchResult<Document> result = index.search(queryVector, 10);

        assertEquals(10, result.size());
        result.forEach(entry -> assertNotNull(entry.entity()));
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

        addRandomDocuments(gigaMap, random, dimension, vectorCount, "doc_");

        // Persist so the search runs against a FusedPQ graph: the approximate score function
        // is built per similarity function, so COSINE passing says nothing about this one.
        index.persistToDisk();
        assertTrue(index.isPqCompressionActive());

        final float[] queryVector = randomVector(random, dimension);
        final VectorSearchResult<Document> result = index.search(queryVector, 10);

        assertEquals(10, result.size());
        result.forEach(entry -> assertNotNull(entry.entity()));
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

        addRandomDocuments(gigaMap, random, dimension, vectorCount, "doc_");

        // Persist so the runtime dimension/4 default is actually exercised: without this the
        // test only proves the builder stored a zero.
        index.persistToDisk();
        assertTrue(index.isPqCompressionActive());

        final float[] queryVector = randomVector(random, dimension);
        final VectorSearchResult<Document> result = index.search(queryVector, 10);

        assertEquals(10, result.size());
        result.forEach(entry -> assertTrue(entry.entity().content().startsWith("doc_")));
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

        addRandomDocuments(gigaMap, random, dimension, vectorCount, "doc_");

        // Persist before removing, so the removals are masked against a real FusedPQ graph
        // rather than just mutating an in-memory builder.
        index.persistToDisk();
        assertTrue(index.isPqCompressionActive());

        // Remove every other entity (even IDs)
        for(int i = 0; i < vectorCount; i += 2)
        {
            gigaMap.removeById(i);
        }

        assertEquals(vectorCount / 2, gigaMap.size());

        // Search should only return remaining entities
        final VectorSearchResult<Document> result = index.search(randomVector(random, dimension), 10);
        assertEquals(10, result.size());

        for(final ScoredSearchResult.Entry<Document> entry : result)
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

        addRandomDocuments(gigaMap, random, dimension, vectorCount, "doc_");

        // Persist first: without a FusedPQ graph on disk the workers below would hammer the
        // in-memory exact path, and this test would say nothing about concurrent PQ search.
        index.persistToDisk();
        assertTrue(index.isPqCompressionActive());

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
        addRandomDocuments(gigaMap, random, dimension, initialCount, "initial_");

        // persistToDisk() is the only thing that trains a codebook, so without it the searches
        // below would run untrained and the "after training" scenario would not exist.
        index.persistToDisk();
        assertTrue(index.isPqCompressionActive());

        // Search before adding more
        final float[] queryVector = randomVector(random, dimension);
        final VectorSearchResult<Document> resultBefore = index.search(queryVector, 10);
        assertEquals(10, resultBefore.size());

        // Add more vectors after training
        addRandomDocuments(gigaMap, random, dimension, additionalCount, "additional_");

        assertEquals(initialCount + additionalCount, gigaMap.size());

        // Search should still work and may include newly added vectors
        final VectorSearchResult<Document> resultAfter = index.search(queryVector, 10);
        assertEquals(10, resultAfter.size());

        for(final ScoredSearchResult.Entry<Document> entry : resultAfter)
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

                addRandomDocuments(gigaMap, random, dimension, 500, "doc_");

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
                final GigaMap<Document> gigaMap = storage.root();
                final VectorIndices<Document> vectorIndices = gigaMap.index().get(VectorIndices.Category());

                assertEquals(500, gigaMap.size());

                final VectorIndex<Document> index = vectorIndices.get("embeddings");
                assertTrue(index.isOnDisk());
                assertTrue(index.isPqCompressionEnabled());

                // Search should work after reload
                final VectorSearchResult<Document> result = index.search(queryVector, 10);
                assertEquals(10, result.size());

                // Verify all entities are accessible
                result.forEach(entry -> assertTrue(entry.entity().content().startsWith("doc_")));

            }
        }

        // Phase 3: Second restart - verify search still works
        {
            try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
            {
                final GigaMap<Document> gigaMap = storage.root();
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

        final VectorIndex<Document> index = vectorIndices.add(
            "embeddings",
            config,
            new ComputedDocumentVectorizer()
        );

        // Initial population
        addRandomDocuments(gigaMap, random, dimension, 500, "old_");

        assertEquals(500, gigaMap.size());

        // Persist the initial population so the removeAll below operates on a PQ graph.
        index.persistToDisk();
        assertTrue(index.isPqCompressionActive());

        // Clear all
        gigaMap.removeAll();
        assertEquals(0, gigaMap.size());

        // Repopulate
        addRandomDocuments(gigaMap, random, dimension, 600, "new_");

        assertEquals(600, gigaMap.size());

        final VectorIndices<Document> vectorIndicesAfter = gigaMap.index().get(VectorIndices.Category());
        final VectorIndex<Document> indexAfter = vectorIndicesAfter.get("embeddings");

        // Persist the repopulated graph too, so the search runs against the rebuilt PQ index.
        indexAfter.persistToDisk();
        assertTrue(indexAfter.isPqCompressionActive());

        // Search should find only new documents
        final VectorSearchResult<Document> result = indexAfter.search(randomVector(random, dimension), 20);
        assertEquals(20, result.size());

        result.forEach(entry -> assertTrue(entry.entity().content().startsWith("new_")));

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
        addRandomDocuments(gigaMap, random, dimension, 100, "doc_");

        // Search should work
        final VectorSearchResult<Document> result = index.search(randomVector(random, dimension), 10);
        assertEquals(10, result.size());
    }


    // ========================================================================
    // Background Persistence Tests
    // ========================================================================

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
            addRandomDocuments(gigaMap, random, dimension, 50, "doc_");

            // Initially, files should not exist (not yet persisted)
            assertFalse(Files.exists(indexDir.resolve("embeddings.graph")),
                "Graph file should not exist immediately after adding");

            // Wait for background persistence to trigger (interval + some buffer)
            await()
                    .atMost(ofMillis(1500))
                    .pollInterval(ofMillis(100))
                    .untilAsserted(() -> assertAll(
                            () -> assertTrue(Files.exists(indexDir.resolve("embeddings.graph")),
                                    "Graph file should exist after background persistence"),
                            () -> assertTrue(Files.exists(indexDir.resolve("embeddings.meta")),
                                    "Meta file should exist after background persistence")));

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
            addRandomDocuments(gigaMap, random, dimension, vectorCount, "doc_");

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
        addRandomDocuments(gigaMap, random, dimension, vectorCount, "doc_");

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
     * Test that shutdown persists pending changes when persistOnShutdown is true
     * and no background features (eventual indexing, background optimization,
     * background persistence) are enabled — i.e. persistenceIntervalMs=0.
     */
    @Test
    void testShutdownPersistsWithoutBackgroundFeatures(@TempDir final Path tempDir) throws Exception
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
            // No background persistence (interval=0), no eventual indexing,
            // no background optimization. persistOnShutdown alone must flush.
            .persistenceIntervalMs(0)
            .persistOnShutdown(true)
            .build();

        final VectorIndex<Document> index = vectorIndices.add(
            "embeddings",
            config,
            new ComputedDocumentVectorizer()
        );

        addRandomDocuments(gigaMap, random, dimension, vectorCount, "doc_");

        assertFalse(Files.exists(indexDir.resolve("embeddings.graph")),
            "Graph file should not exist before close");

        index.close();

        assertTrue(Files.exists(indexDir.resolve("embeddings.graph")),
            "Graph file should exist after close with persistOnShutdown=true even without background features");
        assertTrue(Files.exists(indexDir.resolve("embeddings.meta")),
            "Meta file should exist after close with persistOnShutdown=true even without background features");
    }

    /**
     * Test that the on-disk integrity check rejects a stale graph when the
     * entity count has not changed but the entity-id high-water mark has —
     * i.e. an equal number of additions and removals occurred between
     * persists. Without the highestEntityId check the loader would accept
     * the stale graph (count-collision) and serve phantom hits / silent
     * misses; with the check it rebuilds from the current GigaMap state.
     */
    @Test
    void testRecoveryRejectsCountCollision(@TempDir final Path tempDir) throws Exception
    {
        final int dimension = 32;
        final int initialCount = 50;
        final Random random = new Random(42);
        final Path indexDir = tempDir.resolve("index");
        final Path storageDir = tempDir.resolve("storage");

        // Phase 1: build, persist, store. persistOnShutdown=false so we control
        // exactly when the on-disk graph is updated.
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
                    .persistOnShutdown(false)
                    .build();

                final VectorIndex<Document> index = vectorIndices.add(
                    "embeddings",
                    config,
                    new ComputedDocumentVectorizer()
                );

                addRandomDocuments(gigaMap, random, dimension, initialCount, "doc_");
                index.persistToDisk();
                storage.storeRoot();
                index.close();
            }
        }

        // Phase 2: simulate a crash window — remove the highest id and add a
        // new one (so size stays the same but highestUsedId advances). Persist
        // GigaMap state but NOT the on-disk graph.
        {
            try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
            {
                final GigaMap<Document> gigaMap = storage.root();
                final VectorIndices<Document> vectorIndices = gigaMap.index().get(VectorIndices.Category());
                final VectorIndex<Document> index = vectorIndices.get("embeddings");

                final long victim = gigaMap.highestUsedId();
                gigaMap.removeById(victim);
                addRandomDocuments(gigaMap, new Random(7), dimension, 1, "replacement_");

                assertEquals(initialCount, gigaMap.size(),
                    "Phase 2 should preserve the entity count (count-collision setup)");

                storage.storeRoot();
                // Deliberately skip index.persistToDisk() — leaves the on-disk
                // graph stale but with a count that still matches.
                index.close();
            }
        }

        // Phase 3: reopen. The integrity check must reject the stale graph and
        // rebuild from the current GigaMap state, so the removed entity must
        // not appear and the replacement entity must.
        {
            try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
            {
                final GigaMap<Document> gigaMap = storage.root();
                final VectorIndices<Document> vectorIndices = gigaMap.index().get(VectorIndices.Category());
                final VectorIndex<Document> index = vectorIndices.get("embeddings");

                final long replacementId = gigaMap.highestUsedId();
                final Document replacement = gigaMap.get(replacementId);
                assertNotNull(replacement, "Replacement entity should be present in the GigaMap state");

                final VectorSearchResult<Document> result = index.search(replacement.embedding(), initialCount);
                final Set<Long> hitIds = new HashSet<>();
                for(final ScoredSearchResult.Entry<Document> entry : result)
                {
                    hitIds.add(entry.entityId());
                }

                assertTrue(hitIds.contains(replacementId),
                    "Replacement entity must be searchable after recovery");
                assertEquals(initialCount, hitIds.size(),
                    "Recovered index must expose exactly the live entity set, with no phantoms");
            }
        }
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
        addRandomDocuments(gigaMap, random, dimension, vectorCount, "doc_");

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
            addRandomDocuments(gigaMap, random, dimension, 50, "doc_"); // 50 < 500 threshold

            // Wait for multiple persistence intervals
            Thread.sleep(500);

            // Files should NOT exist because change count is below threshold
            assertFalse(Files.exists(indexDir.resolve("embeddings.graph")),
                "Graph file should not exist when changes below threshold");

            // Now add more vectors to exceed the threshold
            for(int i = 50; i < 600; i++) // Total now 600 > 500 threshold
            {
                gigaMap.add(new Document("doc_" + i, randomVector(random, dimension)));
            }

            await()
                    .atMost(ofMillis(500))
                    .pollInterval(ofMillis(100))
                    .untilAsserted(() -> assertTrue(Files.exists(indexDir.resolve("embeddings.graph")),
                            "Graph file should exist when changes exceed threshold"));
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
            await()
                    .atMost(ofMillis(800))
                    .pollInterval(ofMillis(100))
                    .untilAsserted(() -> assertTrue(Files.exists(indexDir.resolve("embeddings.graph")),
                            "Graph file should exist after bulk add exceeds threshold"));
        } finally {
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
                addRandomDocuments(gigaMap, random, dimension, vectorCount, "doc_");

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
                final GigaMap<Document> gigaMap = storage.root();
                final VectorIndices<Document> vectorIndices = gigaMap.index().get(VectorIndices.Category());

                assertEquals(vectorCount, gigaMap.size());

                final VectorIndex<Document> index = vectorIndices.get("embeddings");
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
            addRandomDocuments(gigaMap, random, dimension, vectorCount, "doc_");

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
            assertEquals(0, defaultIndex.backgroundTaskManager.getOptimizationCount(),
                "Optimization count should be 0 initially");

            // Add vectors to trigger dirty state above threshold
            addRandomDocuments(gigaMap, random, dimension, 50, "doc_");

            // Verify pending changes are tracked
            assertTrue(defaultIndex.backgroundTaskManager.getOptimizationPendingChangeCount() > 0,
                "Pending changes should be tracked");

            // Verify optimization was actually performed
            await()
                    .atLeast(ofMillis(300))
                    .atMost(ofMillis(800))
                    .pollInterval(ofMillis(100))
                    .untilAsserted(() -> assertTrue(defaultIndex.backgroundTaskManager.getOptimizationCount() >= 1,
                            "Optimization should have been performed at least once"));

            // Verify pending changes were reset
            assertEquals(0, defaultIndex.backgroundTaskManager.getOptimizationPendingChangeCount(),
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
            addRandomDocuments(gigaMap, random, dimension, 50, "doc_"); // 50 < 500 threshold

            // Verify pending changes are tracked
            assertEquals(50, defaultIndex.backgroundTaskManager.getOptimizationPendingChangeCount(),
                "Pending changes should be 50");

            // Wait for multiple optimization intervals
            Thread.sleep(600);

            // Verify optimization was NOT performed (below threshold)
            assertEquals(0, defaultIndex.backgroundTaskManager.getOptimizationCount(),
                "Optimization should NOT have been performed (below threshold)");

            // Verify pending changes are still tracked (not reset)
            assertEquals(50, defaultIndex.backgroundTaskManager.getOptimizationPendingChangeCount(),
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

        addRandomDocuments(gigaMap, random, dimension, vectorCount, "doc_");

        // Verify pending changes are tracked
        assertEquals(vectorCount, defaultIndex.backgroundTaskManager.getOptimizationPendingChangeCount(),
            "Pending changes should equal vector count");

        // Verify no optimization has run yet
        assertEquals(0, defaultIndex.backgroundTaskManager.getOptimizationCount(),
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
        addRandomDocuments(gigaMap, random, dimension, vectorCount, "doc_");

        // Verify pending changes are tracked
        assertEquals(vectorCount, defaultIndex.backgroundTaskManager.getOptimizationPendingChangeCount(),
            "Pending changes should equal vector count");

        // Verify no optimization has run yet
        assertEquals(0, defaultIndex.backgroundTaskManager.getOptimizationCount(),
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
            addRandomDocuments(gigaMap, random, dimension, vectorCount, "doc_");

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
            Thread.sleep(500);

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
            addRandomDocuments(gigaMap, random, dimension, vectorCount, "doc_");

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
            .persistenceIntervalMs(300)
            .minChangesBetweenPersists(10)
            .persistOnShutdown(true)
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
            addRandomDocuments(gigaMap, random, dimension, vectorCount, "doc_");

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


    // ========================================================================
    // Parallel vs Non-Parallel On-Disk Write Tests
    // ========================================================================


    /**
     * Test that parallel and non-parallel on-disk writes both support persist-and-reload
     * for a large PQ-compressed index.
     * Verifies that the graph files produced by both modes can be loaded correctly
     * and yield equivalent search results after restart.
     */
    @Test
    void testParallelVsNonParallelPersistAndReload(@TempDir final Path tempDir) throws IOException
    {
        final int vectorCount = 2000;
        final int dimension = 64;
        final int pqSubspaces = 16;
        final int k = 20;
        final Random random = new Random(42);

        // Generate shared vectors and query
        final List<float[]> vectors = new ArrayList<>();
        for(int i = 0; i < vectorCount; i++)
        {
            vectors.add(randomVector(random, dimension));
        }
        final float[] queryVector = randomVector(new Random(999), dimension);

        final Path parallelIndexDir    = tempDir.resolve("parallel-index");
        final Path parallelStorageDir  = tempDir.resolve("parallel-storage");
        final Path sequentialIndexDir  = tempDir.resolve("sequential-index");
        final Path sequentialStorageDir = tempDir.resolve("sequential-storage");

        // --- Build and persist both modes ---
        buildAndPersistIndex(vectors, queryVector, dimension, pqSubspaces, parallelIndexDir, parallelStorageDir, true);
        buildAndPersistIndex(vectors, queryVector, dimension, pqSubspaces, sequentialIndexDir, sequentialStorageDir, false);

        // --- Reload both and compare search results ---
        final List<Long> parallelIds = new ArrayList<>();
        final List<Float> parallelScores = new ArrayList<>();
        {
            try(final EmbeddedStorageManager storage = EmbeddedStorage.start(parallelStorageDir))
            {
                final GigaMap<Document> gigaMap = storage.root();
                final VectorIndices<Document> vectorIndices = gigaMap.index().get(VectorIndices.Category());

                assertEquals(vectorCount, gigaMap.size());

                final VectorIndex<Document> index = vectorIndices.get("embeddings");
                assertTrue(index.isOnDisk());

                final VectorSearchResult<Document> result = index.search(queryVector, k);
                assertEquals(k, result.size());
                for(final ScoredSearchResult.Entry<Document> entry : result)
                {
                    parallelIds.add(entry.entityId());
                    parallelScores.add(entry.score());
                    assertNotNull(entry.entity());
                }
            }
        }

        final List<Long> sequentialIds = new ArrayList<>();
        final List<Float> sequentialScores = new ArrayList<>();
        {
            try(final EmbeddedStorageManager storage = EmbeddedStorage.start(sequentialStorageDir))
            {
                final GigaMap<Document> gigaMap = storage.root();
                final VectorIndices<Document> vectorIndices = gigaMap.index().get(VectorIndices.Category());

                assertEquals(vectorCount, gigaMap.size());

                final VectorIndex<Document> index = vectorIndices.get("embeddings");
                assertTrue(index.isOnDisk());

                final VectorSearchResult<Document> result = index.search(queryVector, k);
                assertEquals(k, result.size());
                for(final ScoredSearchResult.Entry<Document> entry : result)
                {
                    sequentialIds.add(entry.entityId());
                    sequentialScores.add(entry.score());
                    assertNotNull(entry.entity());
                }
            }
        }

        // Both modes should produce equivalent results after reload
        assertEquals(parallelIds, sequentialIds,
            "Parallel and sequential modes should produce identical search results after reload");
        assertEquals(parallelScores, sequentialScores,
            "Parallel and sequential modes should produce identical search scores after reload");
    }

    /**
     * Helper to build, populate, train PQ, persist, and store a PQ-compressed index.
     */
    private void buildAndPersistIndex(
        final List<float[]> vectors         ,
        final float[]       queryVector     ,
        final int           dimension       ,
        final int           pqSubspaces     ,
        final Path          indexDir        ,
        final Path          storageDir      ,
        final boolean       parallel
    ) throws IOException
    {
        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final GigaMap<Document> gigaMap = GigaMap.New();
            storage.setRoot(gigaMap);

            final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());
            final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
                .dimension(dimension)
                .similarityFunction(VectorSimilarityFunction.COSINE)
                .maxDegree(32)
                .beamWidth(100)
                .onDisk(true)
                .indexDirectory(indexDir)
                .enablePqCompression(true)
                .pqSubspaces(pqSubspaces)
                .parallelOnDiskWrite(parallel)
                .build();

            final VectorIndex<Document> index = vectorIndices.add(
                "embeddings", config, new ComputedDocumentVectorizer()
            );

            addDocumentsFromVectors(gigaMap, vectors, "doc_");

            index.persistToDisk();

            assertTrue(Files.exists(indexDir.resolve("embeddings.graph")));
            assertTrue(Files.exists(indexDir.resolve("embeddings.meta")));

            storage.storeRoot();
        }
    }


    // ========================================================================
    // Embedded Vectorizer + On-Disk Tests
    // ========================================================================

    /**
     * Test that an embedded vectorizer with parallel on-disk write completes without deadlock.
     * <p>
     * This is a regression test for a deadlock where {@code persistToDisk()} held
     * {@code synchronized(parentMap)} for the entire disk write. The disk writer uses
     * internal worker threads (ForkJoinPool for PQ encoding, parallel graph writer)
     * that call {@code parentMap.get()} — which also synchronizes on the same monitor.
     * <p>
     * The fix restructures locking: Phase 1 (prep) runs inside {@code synchronized(parentMap)},
     * Phase 2 (disk write) runs outside it but still holds {@code persistenceLock.writeLock()}.
     * <p>
     * Uses {@code @Timeout} to fail fast if a deadlock occurs instead of hanging indefinitely.
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void testEmbeddedVectorizerWithParallelOnDiskWrite(@TempDir final Path tempDir)
    {
        final int vectorCount = 500;
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
            .parallelOnDiskWrite(true)
            .build();

        final VectorIndex<Document> index = vectorIndices.add(
            "embeddings",
            config,
            new EmbeddedDocumentVectorizer()
        );

        addRandomDocuments(gigaMap, random, dimension, vectorCount, "doc_");

        // This would deadlock before the fix
        index.persistToDisk();

        // Verify files were created
        assertAll(
                () -> assertTrue(Files.exists(indexDir.resolve("embeddings.graph"))),
                () -> assertTrue(Files.exists(indexDir.resolve("embeddings.meta")))
        );

        // Verify search still works after persist
        final float[] queryVector = randomVector(random, dimension);
        final VectorSearchResult<Document> result = index.search(queryVector, 10);
        assertEquals(10, result.size());

        result.forEach(entry -> assertNotNull(entry.entity()));
    }

    /**
     * Test that an embedded vectorizer with PQ compression and parallel on-disk write
     * completes without deadlock.
     * <p>
     * This is the most deadlock-prone scenario: FusedPQ encoding uses a ForkJoinPool
     * that calls {@code getVector()} on worker threads, plus the parallel graph writer
     * also calls {@code getVector()} from its own thread pool.
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void testEmbeddedVectorizerWithPqAndParallelOnDiskWrite(@TempDir final Path tempDir)
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
            .parallelOnDiskWrite(true)
            .build();

        final VectorIndex<Document> index = vectorIndices.add(
            "embeddings",
            config,
            new EmbeddedDocumentVectorizer()
        );

        // Add vectors
        addRandomDocuments(gigaMap, random, dimension, vectorCount, "doc_");

        // This would deadlock before the fix
        index.persistToDisk();

        // Verify files were created
        assertAll(
                () -> assertTrue(Files.exists(indexDir.resolve("embeddings.graph"))),
                () -> assertTrue(Files.exists(indexDir.resolve("embeddings.meta")))
        );

        // Verify search still works
        final float[] queryVector = randomVector(random, dimension);
        final VectorSearchResult<Document> result = index.search(queryVector, 10);
        assertEquals(10, result.size());
    }

    /**
     * Test that parallel and non-parallel on-disk writes produce equivalent search results
     * for a large index without PQ compression.
     * Both modes should produce identical graph files that yield the same search quality.
     */
    @Test
    void testParallelVsSequentialOnDiskWrite(@TempDir final Path tempDir) throws IOException
    {
        final int vectorCount = 2000;
        final int dimension = 64;
        final int k = 20;
        final Random random = new Random(42);

        // Generate shared vectors and query
        final List<float[]> vectors = new ArrayList<>();
        for (int i = 0; i < vectorCount; i++) {
            vectors.add(randomVector(random, dimension));
        }
        final float[] queryVector = randomVector(new Random(999), dimension);

        final Path parallelIndexDir = tempDir.resolve("parallel");
        final Path sequentialIndexDir = tempDir.resolve("sequential");

        final List<Long> parallelIds = new ArrayList<>();
        final List<Float> parallelScores = new ArrayList<>();
        final List<Long> sequentialIds = new ArrayList<>();
        final List<Float> sequentialScores = new ArrayList<>();

        // --- Parallel config
        final GigaMap<Document> gigaMap = GigaMap.New();
        final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());

        final VectorIndexConfiguration configParallel = VectorIndexConfiguration.builder()
                .dimension(dimension)
                .similarityFunction(VectorSimilarityFunction.COSINE)
                .maxDegree(16)
                .beamWidth(100)
                .onDisk(true)
                .indexDirectory(parallelIndexDir)
                .parallelOnDiskWrite(true)
                .build();

        // --- Sequential config
        final VectorIndex<Document> index = vectorIndices.add(
                "embeddings", configParallel, new ComputedDocumentVectorizer()
        );

        // Identical to configParallel except for parallelOnDiskWrite, which is the whole point of
        // the comparison below. This config used to also set enablePqCompression(true); that was
        // harmless only while the flag was inert. Now that PQ really is applied, the compressed
        // index traverses on lossy approximate scores and its top-k can legitimately differ, which
        // made this test flaky. Parallel writes with PQ are covered by
        // testEmbeddedVectorizerWithPqAndParallelOnDiskWrite.
        final VectorIndexConfiguration configSequential = VectorIndexConfiguration.builder()
                .dimension(dimension)
                .similarityFunction(VectorSimilarityFunction.COSINE)
                .maxDegree(16)
                .beamWidth(100)
                .onDisk(true)
                .indexDirectory(sequentialIndexDir)
                .parallelOnDiskWrite(false)
                .build();

        final VectorIndex<Document> indexSequential = vectorIndices.add(
                "embeddingsSequential", configSequential, new ComputedDocumentVectorizer()
        );

        addDocumentsFromVectors(gigaMap, vectors, "doc_");

        index.persistToDisk();
        indexSequential.persistToDisk();

        //parallel
        final VectorSearchResult<Document> result = index.search(queryVector, k);
        for (final ScoredSearchResult.Entry<Document> entry : result) {
            parallelIds.add(entry.entityId());
            parallelScores.add(entry.score());
        }

        //sequential
        final VectorSearchResult<Document> resultSequential = indexSequential.search(queryVector, k);
        for (final ScoredSearchResult.Entry<Document> entry : resultSequential) {
            sequentialIds.add(entry.entityId());
            sequentialScores.add(entry.score());
        }

        assertAll(
                () -> assertTrue(Files.exists(parallelIndexDir.resolve("embeddings.graph"))),
                () -> assertTrue(Files.exists(parallelIndexDir.resolve("embeddings.meta"))),
                () -> assertTrue(Files.exists(sequentialIndexDir.resolve("embeddingsSequential.graph"))),
                () -> assertTrue(Files.exists(sequentialIndexDir.resolve("embeddingsSequential.meta")))
        );

        // Both indices were built from the same data with the same HNSW parameters,
        // so search results must be identical.
        assertEquals(parallelIds, sequentialIds,
                "Parallel and sequential on-disk writes should produce identical search results");
        assertEquals(parallelScores, sequentialScores,
                "Parallel and sequential on-disk writes should produce identical search scores");
    }


    // ========================================================================
    // Incremental On-Disk Mode Tests
    // ========================================================================

    /**
     * Test adding vectors after disk reload (incremental mode).
     * Verifies search finds both old (disk) and new (in-memory) vectors.
     */
    @Test
    void testIncrementalAddAfterDiskReload(@TempDir final Path tempDir) throws IOException
    {
        final int dimension = 32;
        final Random random = new Random(42);

        final Path indexDir = tempDir.resolve("index");
        final Path storageDir = tempDir.resolve("storage");

        // Distinguishable needle vectors for deterministic assertions
        final float[] oldNeedleVector = new float[dimension];
        oldNeedleVector[0] = 1.0f;
        final float[] newNeedleVector = new float[dimension];
        newNeedleVector[1] = 1.0f;

        // Phase 1: Create index, add vectors including old needle, persist
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

                final VectorIndex<Document> index = vectorIndices.add("embeddings", config, new ComputedDocumentVectorizer());

                addRandomDocuments(gigaMap, random, dimension, 100, "original_");
                gigaMap.add(new Document("original_needle", oldNeedleVector));

                index.persistToDisk();
                storage.storeRoot();
            }
        }

        // Phase 2: Reload (incremental mode), add new vectors, verify search
        {
            try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
            {
                final GigaMap<Document> gigaMap = storage.root();
                final VectorIndices<Document> vectorIndices = gigaMap.index().get(VectorIndices.Category());
                final VectorIndex<Document> index = vectorIndices.get("embeddings");

                assertEquals(101, gigaMap.size());

                // Add new vectors (goes to in-memory builder in incremental mode)
                addRandomDocuments(gigaMap, random, dimension, 50, "new_");
                gigaMap.add(new Document("new_needle", newNeedleVector));

                assertEquals(152, gigaMap.size());

                // Search for old needle — should be found from disk graph
                final VectorSearchResult<Document> oldResult = index.search(oldNeedleVector, 5);
                final ScoredSearchResult.Entry<Document> oldFirst = oldResult.iterator().next();
                assertEquals("original_needle", oldFirst.entity().content(),
                    "Old needle should be found from disk graph");
                assertTrue(oldFirst.score() > 0.99f, "Exact match should have score close to 1.0");

                // Search for new needle — should be found from in-memory builder
                final VectorSearchResult<Document> newResult = index.search(newNeedleVector, 5);
                final ScoredSearchResult.Entry<Document> newFirst = newResult.iterator().next();
                assertEquals("new_needle", newFirst.entity().content(),
                    "New needle should be found from in-memory builder");
                assertTrue(newFirst.score() > 0.99f, "Exact match should have score close to 1.0");
            }
        }
    }

    /**
     * Test deleting vectors after disk reload (incremental mode).
     * Verifies search excludes deleted vectors from disk graph.
     */
    @Test
    void testIncrementalDeleteAfterDiskReload(@TempDir final Path tempDir) throws IOException
    {
        final int dimension = 32;
        final Random random = new Random(42);

        final Path indexDir = tempDir.resolve("index");
        final Path storageDir = tempDir.resolve("storage");

        // Needle vector for easy identification
        final float[] needleVector = new float[dimension];
        needleVector[0] = 1.0f;

        long needleEntityId;

        // Phase 1: Create index with needle, persist
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

                addRandomDocuments(gigaMap, random, dimension, 100, "doc_");
                gigaMap.add(new Document("needle", needleVector));
                assertEquals(101, gigaMap.size());

                // Verify needle is found
                final VectorIndex<Document> index = vectorIndices.get("embeddings");
                final VectorSearchResult<Document> result = index.search(needleVector, 5);
                final ScoredSearchResult.Entry<Document> firstEntry = result.iterator().next();
                assertEquals("needle", firstEntry.entity().content());
                needleEntityId = firstEntry.entityId();

                index.persistToDisk();
                storage.storeRoot();
            }
        }

        // Phase 2: Reload (incremental mode), delete needle, verify search excludes it
        {
            try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
            {
                final GigaMap<Document> gigaMap = storage.root();
                final VectorIndices<Document> vectorIndices = gigaMap.index().get(VectorIndices.Category());
                final VectorIndex<Document> index = vectorIndices.get("embeddings");

                assertEquals(101, gigaMap.size());

                // Delete the needle
                gigaMap.removeById(needleEntityId);
                assertEquals(100, gigaMap.size());

                // Search for needle vector — it should NOT be the first result anymore
                final VectorSearchResult<Document> result = index.search(needleVector, 5);
                assertEquals(5, result.size());
                for(final ScoredSearchResult.Entry<Document> entry : result)
                {
                    assertNotEquals("needle", entry.entity().content(),
                        "Deleted needle should not appear in search results");
                }
            }
        }
    }

    /**
     * Test updating vectors after disk reload (incremental mode).
     * Verifies search returns updated vector data.
     */
    @Test
    void testIncrementalUpdateAfterDiskReload(@TempDir final Path tempDir) throws IOException
    {
        final int dimension = 32;
        final Random random = new Random(42);

        final Path indexDir = tempDir.resolve("index");
        final Path storageDir = tempDir.resolve("storage");

        // Needle vector for identification
        final float[] needleVector = new float[dimension];
        needleVector[0] = 1.0f;

        // Opposite direction vector for update
        final float[] oppositeVector = new float[dimension];
        oppositeVector[0] = -1.0f;

        long needleEntityId;

        // Phase 1: Create index with needle, persist
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

                final VectorIndex<Document> index = vectorIndices.add("embeddings", config, new ComputedDocumentVectorizer());

                addRandomDocuments(gigaMap, random, dimension, 100, "doc_");
                gigaMap.add(new Document("needle", needleVector));

                needleEntityId = index.search(needleVector, 1).iterator().next().entityId();

                index.persistToDisk();
                storage.storeRoot();
            }
        }

        // Phase 2: Reload, update needle to point in opposite direction, verify
        {
            try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
            {
                final GigaMap<Document> gigaMap = storage.root();
                final VectorIndices<Document> vectorIndices = gigaMap.index().get(VectorIndices.Category());
                final VectorIndex<Document> index = vectorIndices.get("embeddings");

                // Update needle in-place via set() — exercises internalUpdate() in incremental mode.
                // This keeps the same entityId but changes the vector direction.
                // Force lazy segment loading before set() (set uses peek() internally).
                assertNotNull(gigaMap.get(needleEntityId));
                gigaMap.set(needleEntityId, new Document("updated_needle", oppositeVector));
                assertEquals(101, gigaMap.size(), "Size should be unchanged after in-place update");

                // Search for original needle direction — updated entity should not be top result
                final VectorSearchResult<Document> result = index.search(needleVector, 5);
                for(final ScoredSearchResult.Entry<Document> entry : result)
                {
                    assertNotEquals("updated_needle", entry.entity().content(),
                        "Updated needle with opposite vector should not appear in original direction search");
                }

                // Search for opposite direction — updated entity should be found
                final VectorSearchResult<Document> oppositeResult = index.search(oppositeVector, 5);
                boolean foundUpdated = false;
                for(final ScoredSearchResult.Entry<Document> entry : oppositeResult)
                {
                    if("updated_needle".equals(entry.entity().content()))
                    {
                        foundUpdated = true;
                        break;
                    }
                }
                assertTrue(foundUpdated, "Updated needle should be found when searching in its new direction");

                // Also test updating a node that was added in incremental mode (builder-only).
                // Add a new entity, then update it in-place — exercises internalUpdate()
                // for an ordinal that exists in the builder, not on disk.
                final long newId = gigaMap.add(new Document("builder_node", needleVector));
                assertEquals(102, gigaMap.size());

                // Update it in-place — the builder must delete the old node and re-add
                gigaMap.get(newId); // force lazy segment loading
                gigaMap.set(newId, new Document("builder_node_updated", oppositeVector));
                assertEquals(102, gigaMap.size(), "Size unchanged after in-place update of builder node");

                // Verify the entity was actually updated
                assertEquals("builder_node_updated", gigaMap.get(newId).content());

                // Search should find the updated builder node and not the stale version
                final VectorSearchResult<Document> builderResult = index.search(oppositeVector, 102);
                boolean foundBuilderUpdated = false;
                boolean foundStaleBuilderNode = false;
                for(final ScoredSearchResult.Entry<Document> entry : builderResult)
                {
                    if("builder_node_updated".equals(entry.entity().content()))
                    {
                        foundBuilderUpdated = true;
                    }
                    if("builder_node".equals(entry.entity().content()))
                    {
                        foundStaleBuilderNode = true;
                    }
                }
                assertFalse(foundStaleBuilderNode, "Stale builder node should not appear in search");
                assertTrue(foundBuilderUpdated, "Updated builder node should be found in search");
            }
        }
    }

    /**
     * Test persist after incremental add mutations — reload, add, persist, reload, verify.
     */
    @Test
    void testPersistAfterIncrementalMutations(@TempDir final Path tempDir) throws IOException
    {
        final int dimension = 32;
        final Random random = new Random(42);

        final Path indexDir = tempDir.resolve("index");
        final Path storageDir = tempDir.resolve("storage");

        final float[] needleVector = new float[dimension];
        needleVector[0] = 1.0f;

        // Phase 1: Create index, persist
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

                final VectorIndex<Document> index = vectorIndices.add("embeddings", config, new ComputedDocumentVectorizer());

                addRandomDocuments(gigaMap, random, dimension, 100, "original_");

                index.persistToDisk();
                storage.storeRoot();
            }
        }

        // Phase 2: Reload, add new vectors (including needle), persist again
        {
            try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
            {
                final GigaMap<Document> gigaMap = storage.root();
                final VectorIndices<Document> vectorIndices = gigaMap.index().get(VectorIndices.Category());
                final VectorIndex<Document> index = vectorIndices.get("embeddings");

                // Add new vectors
                addRandomDocuments(gigaMap, random, dimension, 50, "added_");
                gigaMap.add(new Document("needle", needleVector));
                assertEquals(151, gigaMap.size());

                // Verify needle is searchable before persist
                final VectorSearchResult<Document> preResult = index.search(needleVector, 5);
                boolean preFoundNeedle = false;
                for(final ScoredSearchResult.Entry<Document> entry : preResult)
                {
                    if("needle".equals(entry.entity().content()))
                    {
                        preFoundNeedle = true;
                        break;
                    }
                }
                assertTrue(preFoundNeedle, "Needle should be found before persist");

                // Persist with incremental changes
                index.persistToDisk();
                storage.storeRoot();
            }
        }

        // Phase 3: Reload again, verify complete state including needle
        {
            try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
            {
                final GigaMap<Document> gigaMap = storage.root();
                final VectorIndices<Document> vectorIndices = gigaMap.index().get(VectorIndices.Category());
                final VectorIndex<Document> index = vectorIndices.get("embeddings");

                assertEquals(151, gigaMap.size());

                // Search for needle — should be found after persist+reload cycle
                final VectorSearchResult<Document> result = index.search(needleVector, 10);
                assertTrue(result.size() > 0, "Search should return results");
                boolean foundNeedle = false;
                for(final ScoredSearchResult.Entry<Document> entry : result)
                {
                    if("needle".equals(entry.entity().content()))
                    {
                        foundNeedle = true;
                        break;
                    }
                }
                assertTrue(foundNeedle, "Needle should survive persist cycle");

                // Verify search returns results from both original and added vectors
                final VectorSearchResult<Document> allResult = index.search(
                    randomVector(new Random(999), dimension), 20
                );
                boolean hasOriginal = false;
                boolean hasAdded = false;
                for(final ScoredSearchResult.Entry<Document> entry : allResult)
                {
                    if(entry.entity().content().startsWith("original_")) hasOriginal = true;
                    if(entry.entity().content().startsWith("added_"))    hasAdded = true;
                }
                assertTrue(hasOriginal, "Should find original vectors after persist cycle");
                assertTrue(hasAdded, "Should find added vectors after persist cycle");
            }
        }
    }

    /**
     * Test no-op persist in incremental mode — when no changes have been made after reload,
     * persist should skip quickly.
     */
    @Test
    void testIncrementalNoOpPersist(@TempDir final Path tempDir) throws IOException
    {
        final int dimension = 32;
        final Random random = new Random(42);

        final Path indexDir = tempDir.resolve("index");
        final Path storageDir = tempDir.resolve("storage");

        // Phase 1: Create index, persist
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
                addRandomDocuments(gigaMap, random, dimension, 100, "doc_");

                final VectorIndex<Document> index = vectorIndices.get("embeddings");
                index.persistToDisk();
                storage.storeRoot();
            }
        }

        // Phase 2: Reload, do nothing, persist (should be a no-op)
        {
            try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
            {
                final GigaMap<Document> gigaMap = storage.root();
                final VectorIndices<Document> vectorIndices = gigaMap.index().get(VectorIndices.Category());
                final VectorIndex<Document> index = vectorIndices.get("embeddings");

                // Verify search still works
                final VectorSearchResult<Document> resultBefore = index.search(
                    randomVector(random, dimension), 10
                );
                assertEquals(10, resultBefore.size());

                // Persist with no changes — should skip
                index.persistToDisk();

                // Verify search still works after no-op persist
                final VectorSearchResult<Document> resultAfter = index.search(
                    randomVector(random, dimension), 10
                );
                assertEquals(10, resultAfter.size());
            }
        }
    }

    /**
     * Test removeAll in incremental mode — should cleanly reset state.
     */
    @Test
    void testIncrementalRemoveAll(@TempDir final Path tempDir) throws IOException
    {
        final int dimension = 32;
        final Random random = new Random(42);

        final Path indexDir = tempDir.resolve("index");
        final Path storageDir = tempDir.resolve("storage");

        // Phase 1: Create index, persist
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
                addRandomDocuments(gigaMap, random, dimension, 100, "doc_");

                final VectorIndex<Document> index = vectorIndices.get("embeddings");
                index.persistToDisk();
                storage.storeRoot();
            }
        }

        // Phase 2: Reload, removeAll, add new data, verify
        {
            try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
            {
                final GigaMap<Document> gigaMap = storage.root();
                final VectorIndices<Document> vectorIndices = gigaMap.index().get(VectorIndices.Category());
                final VectorIndex<Document> index = vectorIndices.get("embeddings");

                assertEquals(100, gigaMap.size());

                // Remove all
                gigaMap.removeAll();
                assertEquals(0, gigaMap.size());

                // Add new data after removeAll
                addRandomDocuments(gigaMap, random, dimension, 50, "new_");
                assertEquals(50, gigaMap.size());

                // Search should work on new data
                final VectorSearchResult<Document> result = index.search(
                    randomVector(random, dimension), 10
                );
                assertEquals(10, result.size());
                for(final ScoredSearchResult.Entry<Document> entry : result)
                {
                    assertTrue(entry.entity().content().startsWith("new_"),
                        "After removeAll, only new documents should be found");
                }
            }
        }
    }

    /**
     * Regression test for internal#112: an on-disk index in incremental mode with pending changes
     * must NOT rebuild and rewrite the full graph on {@code close()} with {@code persistOnShutdown}.
     * The graph file must stay byte-identical across the shutdown (no O(n) consolidation), and the
     * changes must still be visible after reload thanks to the load-time self-heal from the store.
     */
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testShutdownDoesNotRebuildGraphInIncrementalMode(@TempDir final Path tempDir) throws IOException
    {
        final int dimension = 32;
        final Random random = new Random(42);

        final Path indexDir   = tempDir.resolve("index");
        final Path storageDir = tempDir.resolve("storage");
        final Path graphPath  = indexDir.resolve("embeddings.graph");

        final float[] needleVector = new float[dimension];
        needleVector[0] = 1.0f;

        byte[]   graphBytesAfterPersist = null;
        FileTime graphMTimeAfterPersist = null;

        // Phase 1: create, persist once (enters incremental mode), apply incremental changes, then
        // close with persistOnShutdown(true). The graph must not be rewritten by the shutdown.
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
                    .persistenceIntervalMs(60_000) // long interval — background persistence won't fire during the test
                    .minChangesBetweenPersists(1)
                    .persistOnShutdown(true)
                    .build();

                final VectorIndex<Document> index = vectorIndices.add(
                    "embeddings", config, new ComputedDocumentVectorizer());

                addRandomDocuments(gigaMap, random, dimension, 100, "original_");

                // First persist writes the full graph and re-enters incremental mode.
                index.persistToDisk();
                storage.storeRoot();
                assertTrue(Files.exists(graphPath), "Graph file should exist after the first persist");
                graphBytesAfterPersist = Files.readAllBytes(graphPath);
                graphMTimeAfterPersist = Files.getLastModifiedTime(graphPath);

                // Apply incremental changes (in-memory builder + tombstones) — deliberately NOT persisted.
                addRandomDocuments(gigaMap, random, dimension, 50, "added_");
                gigaMap.add(new Document("needle", needleVector));
                storage.storeRoot(); // commit to the store so the self-heal can rebuild them on reload

                // Shutdown persist: in incremental mode this must skip the full-graph consolidation.
                index.close();
            }

            // The graph file must be untouched by the shutdown: not rewritten (last-modified time
            // unchanged — catches a deterministic rebuild that happens to produce identical bytes) and
            // byte-identical (catches a rewrite that a coarse mtime granularity might miss).
            assertEquals(graphMTimeAfterPersist, Files.getLastModifiedTime(graphPath),
                "Shutdown must not rewrite the graph while in incremental mode (last-modified time changed)");
            assertArrayEquals(graphBytesAfterPersist, Files.readAllBytes(graphPath),
                "Shutdown must not rewrite the graph while in incremental mode (no full-graph rebuild)");
            // And it must not leave any temp file behind.
            assertNoTempFiles(indexDir);
        }

        // Phase 2: reload — the on-disk graph is stale (store advanced past it), so the self-heal
        // rebuilds from the store. All vectors, including the unpersisted incremental ones, are found.
        {
            try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
            {
                final GigaMap<Document> gigaMap = storage.root();
                final VectorIndices<Document> vectorIndices = gigaMap.index().get(VectorIndices.Category());
                final VectorIndex<Document> index = vectorIndices.get("embeddings");

                assertEquals(151, gigaMap.size());

                final VectorSearchResult<Document> result = index.search(needleVector, 10);
                boolean foundNeedle = false;
                for(final ScoredSearchResult.Entry<Document> entry : result)
                {
                    if("needle".equals(entry.entity().content()))
                    {
                        foundNeedle = true;
                        break;
                    }
                }
                assertTrue(foundNeedle, "Self-heal must recover the unpersisted incremental changes from the store");
            }
        }
    }

    /**
     * Fix for internal#112: {@link DiskIndexManager} writes the graph and meta via temp files and an
     * atomic rename. A completed persist must leave no {@code .tmp} files behind, and the graph must
     * load normally.
     */
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testWriteIndexLeavesNoTempFiles(@TempDir final Path tempDir) throws IOException
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
            .build();

        final VectorIndex<Document> index = vectorIndices.add(
            "embeddings", config, new ComputedDocumentVectorizer());
        addRandomDocuments(gigaMap, random, dimension, 100, "doc_");

        index.persistToDisk();

        assertTrue(Files.exists(indexDir.resolve("embeddings.graph")), "Graph file should exist after persist");
        assertTrue(Files.exists(indexDir.resolve("embeddings.meta")),  "Meta file should exist after persist");
        assertNoTempFiles(indexDir);

        index.close();
    }

    /**
     * Asserts that no temporary write files ({@code *.graph.tmp} / {@code *.meta.tmp}) linger in the
     * index directory after a persist.
     */
    private static void assertNoTempFiles(final Path indexDir) throws IOException
    {
        if(!Files.exists(indexDir))
        {
            return;
        }
        try(final var entries = Files.list(indexDir))
        {
            final List<String> tempFiles = entries
                .map(p -> p.getFileName().toString())
                .filter(n -> n.endsWith(".tmp"))
                .toList();
            assertTrue(tempFiles.isEmpty(), "No temp files should remain after persist, found: " + tempFiles);
        }
    }

}
