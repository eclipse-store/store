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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Performance tests for VectorIndex with large datasets (1 million vectors).
 * These tests measure indexing and search performance.
 */
@Disabled("Manual performance test - not for CI")
class VectorIndexPerformanceTest
{
    /**
     * Simple entity with an embedding vector.
     */
    static class Document
    {
        private final String content;
        private final float[] embedding;

        Document(final String content, final float[] embedding)
        {
            this.content = content;
            this.embedding = embedding;
        }

        String content()
        {
            return this.content;
        }

        float[] embedding()
        {
            return this.embedding;
        }
    }

    /**
     * Computed vectorizer for performance tests.
     */
    static class DocumentVectorizer extends Vectorizer<Document>
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
        for (int i = 0; i < dimension; i++)
        {
            vector[i] = random.nextFloat() * 2 - 1; // Range [-1, 1]
            norm += vector[i] * vector[i];
        }
        // Normalize
        norm = (float) Math.sqrt(norm);
        for (int i = 0; i < dimension; i++)
        {
            vector[i] /= norm;
        }
        return vector;
    }

    /**
     * Performance test with 1 million vectors.
     * Measures:
     * - Total indexing time
     * - Indexing throughput (vectors/second)
     * - Average search latency
     * - Search throughput (queries/second)
     */
    @Test
    void testPerformanceWith1MillionVectors()
    {
        final int vectorCount = 1_000_000;
        final int dimension = 128;
        final int searchIterations = 1000;
        final int k = 10; // Number of nearest neighbors to retrieve
        final Random random = new Random(42);

        System.out.println("=== VectorIndex Performance Test ===");
        System.out.println("Vector count: " + vectorCount);
        System.out.println("Dimension: " + dimension);
        System.out.println("Search iterations: " + searchIterations);
        System.out.println("k (nearest neighbors): " + k);
        System.out.println();

        // Create GigaMap and VectorIndex
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
            new DocumentVectorizer()
        );

        // Measure memory before indexing
        System.gc();
        final long memoryBefore = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        // === INDEXING PHASE ===
        System.out.println("Starting indexing (using addAll with batches)...");
        final long indexStartTime = System.currentTimeMillis();

        // Add vectors in batches using addAll
        final int batchSize = 10_000;
        int indexed = 0;

        while (indexed < vectorCount)
        {
            final int currentBatchSize = Math.min(batchSize, vectorCount - indexed);
            final List<Document> batch = new ArrayList<>(currentBatchSize);

            for (int i = 0; i < currentBatchSize; i++)
            {
                batch.add(new Document("doc_" + (indexed + i), randomVector(random, dimension)));
            }

            gigaMap.addAll(batch);
            indexed += currentBatchSize;

            // Progress reporting every 100,000 vectors
            if (indexed % 100_000 == 0)
            {
                final long elapsed = System.currentTimeMillis() - indexStartTime;
                final double throughput = indexed / (elapsed / 1000.0);
                System.out.printf("  Indexed %,d vectors (%.1f vectors/sec)%n", indexed, throughput);
            }
        }

//        System.out.print("Optimizing index... ");
//        index.optimize();
//        System.out.println("done");

        final long indexEndTime = System.currentTimeMillis();
        final long indexingTime = indexEndTime - indexStartTime;

        // Measure memory after indexing
        System.gc();
        final long memoryAfter = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        final long memoryUsed = memoryAfter - memoryBefore;

        System.out.println();
        System.out.println("=== Indexing Results ===");
        System.out.printf("Total indexing time: %,d ms (%.2f seconds)%n", indexingTime, indexingTime / 1000.0);
        System.out.printf("Indexing throughput: %.1f vectors/second%n", vectorCount / (indexingTime / 1000.0));
        System.out.printf("Memory used (approx): %.2f MB%n", memoryUsed / (1024.0 * 1024.0));
        System.out.println();

        // === SEARCH PHASE ===
        System.out.println("Starting search benchmark...");

        // Warmup searches
        System.out.println("  Warming up (100 searches)...");
        for (int i = 0; i < 100; i++)
        {
            final float[] queryVector = randomVector(random, dimension);
            index.search(queryVector, k);
        }

        // Timed searches
        final long searchStartTime = System.currentTimeMillis();

        for (int i = 0; i < searchIterations; i++)
        {
            final float[] queryVector = randomVector(random, dimension);
            final VectorSearchResult<Document> result = index.search(queryVector, k);

            // Verify result count
            if (result.size() != k)
            {
                System.err.printf("Warning: Expected %d results, got %d%n", k, result.size());
            }
        }

        final long searchEndTime = System.currentTimeMillis();
        final long searchTime = searchEndTime - searchStartTime;
        final double avgSearchLatency = (double) searchTime / searchIterations;
        final double searchThroughput = searchIterations / (searchTime / 1000.0);

        System.out.println();
        System.out.println("=== Search Results ===");
        System.out.printf("Total search time: %,d ms%n", searchTime);
        System.out.printf("Search iterations: %,d%n", searchIterations);
        System.out.printf("Average search latency: %.3f ms%n", avgSearchLatency);
        System.out.printf("Search throughput: %.1f queries/second%n", searchThroughput);
        System.out.println();

        // === VERIFY SEARCH QUALITY ===
        System.out.println("Verifying search quality...");

        // Add a known vector and search for it
        final float[] knownVector = randomVector(new Random(999), dimension);
        gigaMap.add(new Document("known_vector", knownVector));

        final VectorSearchResult<Document> qualityResult = index.search(knownVector, 1);
        final VectorSearchResult.Entry<Document> topResult = qualityResult.iterator().next();

        System.out.printf("Known vector search - Top result: %s (score: %.4f)%n",
            topResult.entity().content(), topResult.score());

        if (!"known_vector".equals(topResult.entity().content()))
        {
            System.out.println("Note: Exact match not found as top result (HNSW is approximate)");
        } else
        {
            System.out.println("Exact match found as top result!");
        }

        System.out.println();
        System.out.println("=== Performance Test Complete ===");
    }

    /**
     * Performance test comparing different HNSW configurations.
     */
    @Test
    void testPerformanceWithDifferentConfigurations()
    {
        final int vectorCount = 100_000; // Smaller for faster iteration
        final int dimension = 128;
        final int searchIterations = 500;
        final int k = 10;
        final Random random = new Random(42);

        System.out.println("=== Configuration Comparison Test ===");
        System.out.println("Vector count: " + vectorCount);
        System.out.println();

        // Test different configurations
        final int[][] configs = {
            {16, 100},   // maxDegree=16, beamWidth=100 (default)
            {32, 100},   // maxDegree=32, beamWidth=100
            {16, 200},   // maxDegree=16, beamWidth=200
            {32, 200},   // maxDegree=32, beamWidth=200
        };

        // Pre-generate vectors for consistency
        final float[][] vectors = new float[vectorCount][];
        for (int i = 0; i < vectorCount; i++)
        {
            vectors[i] = randomVector(random, dimension);
        }

        // Pre-generate query vectors
        final float[][] queryVectors = new float[searchIterations][];
        for (int i = 0; i < searchIterations; i++)
        {
            queryVectors[i] = randomVector(random, dimension);
        }

        for (final int[] cfg : configs)
        {
            final int maxDegree = cfg[0];
            final int beamWidth = cfg[1];

            System.out.printf("--- Config: maxDegree=%d, beamWidth=%d ---%n", maxDegree, beamWidth);

            final GigaMap<Document> gigaMap = GigaMap.New();
            final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());

            final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
                .dimension(dimension)
                .similarityFunction(VectorSimilarityFunction.COSINE)
                .maxDegree(maxDegree)
                .beamWidth(beamWidth)
                .build();

            final VectorIndex<Document> index = vectorIndices.add(
                "embeddings",
                config,
                new DocumentVectorizer()
            );

            // Index using addAll with batches
            final long indexStart = System.currentTimeMillis();
            final int batchSize = 10_000;
            for (int i = 0; i < vectorCount; i += batchSize)
            {
                final int currentBatchSize = Math.min(batchSize, vectorCount - i);
                final List<Document> batch = new ArrayList<>(currentBatchSize);
                for (int j = 0; j < currentBatchSize; j++)
                {
                    batch.add(new Document("doc_" + (i + j), vectors[i + j]));
                }
                gigaMap.addAll(batch);
            }

//            System.out.print("Optimizing index... ");
//            index.optimize();
//            System.out.println("done");

            final long indexTime = System.currentTimeMillis() - indexStart;

            // Warmup
            for (int i = 0; i < 50; i++)
            {
                index.search(queryVectors[i], k);
            }

            // Search
            final long searchStart = System.currentTimeMillis();
            for (int i = 0; i < searchIterations; i++)
            {
                index.search(queryVectors[i], k);
            }
            final long searchTime = System.currentTimeMillis() - searchStart;

            System.out.printf("  Index time: %,d ms (%.1f vec/sec)%n",
                indexTime, vectorCount / (indexTime / 1000.0));
            System.out.printf("  Search time: %,d ms (%.2f ms/query, %.1f qps)%n",
                searchTime, (double) searchTime / searchIterations,
                searchIterations / (searchTime / 1000.0));
            System.out.println();
        }

        System.out.println("=== Configuration Comparison Complete ===");
    }
}
