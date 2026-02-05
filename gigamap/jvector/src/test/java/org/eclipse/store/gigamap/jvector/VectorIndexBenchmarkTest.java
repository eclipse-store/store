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

import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Benchmark test for VectorIndex using standard vector database evaluation methodology.
 * <p>
 * This test measures search recall against ground truth data, following the methodology
 * used by industry-standard benchmarks like ANN-Benchmarks (https://ann-benchmarks.com/).
 * <p>
 * Two test modes are supported:
 * <ul>
 *   <li><b>Synthetic data</b> - Generates random vectors and computes exact k-NN as ground truth</li>
 *   <li><b>Real SIFT data</b> - Uses the SIFT-128 dataset if available in src/test/resources/sift/</li>
 * </ul>
 *
 * <h2>Recall Metric</h2>
 * Recall@k measures the fraction of true k nearest neighbors found by the approximate search:
 * <pre>
 * recall@k = |retrieved ∩ ground_truth| / k
 * </pre>
 *
 * <h2>Expected Results</h2>
 * With default HNSW parameters (maxDegree=16, beamWidth=100):
 * <ul>
 *   <li>Recall@10: &gt; 95%</li>
 *   <li>Recall@100: &gt; 90%</li>
 * </ul>
 *
 * <h2>SIFT Dataset</h2>
 * To run benchmarks with real SIFT data, download from:
 * <code>ftp://ftp.irisa.fr/local/texmex/corpus/siftsmall.tar.gz</code>
 * and extract to <code>src/test/resources/sift/</code>:
 * <ul>
 *   <li>siftsmall_base.fvecs - 10,000 base vectors (128 dimensions)</li>
 *   <li>siftsmall_query.fvecs - 100 query vectors</li>
 *   <li>siftsmall_groundtruth.ivecs - Ground truth (100 nearest neighbors per query)</li>
 * </ul>
 */
@Disabled("Manual benchmark test - not for CI")
class VectorIndexBenchmarkTest
{
    private static final int SYNTHETIC_VECTOR_COUNT = 10_000;
    private static final int SYNTHETIC_QUERY_COUNT = 100;
    private static final int DIMENSION = 128;
    private static final long SEED = 42L;

    // SIFT data file paths
    private static final Path SIFT_BASE_PATH = Path.of("src/test/resources/sift/siftsmall_base.fvecs");
    private static final Path SIFT_QUERY_PATH = Path.of("src/test/resources/sift/siftsmall_query.fvecs");
    private static final Path SIFT_GROUNDTRUTH_PATH = Path.of("src/test/resources/sift/siftsmall_groundtruth.ivecs");

    /**
     * Simple entity with an embedding vector.
     */
    static class VectorEntity
    {
        private final int id;
        private final float[] vector;

        VectorEntity(final int id, final float[] vector)
        {
            this.id = id;
            this.vector = vector;
        }

        int id()
        {
            return this.id;
        }

        float[] vector()
        {
            return this.vector;
        }
    }

    /**
     * Embedded vectorizer for benchmark entities.
     */
    static class VectorEntityVectorizer extends Vectorizer<VectorEntity>
    {
        @Override
        public float[] vectorize(final VectorEntity entity)
        {
            return entity.vector();
        }

        @Override
        public boolean isEmbedded()
        {
            return true;
        }
    }

    // ==================== Synthetic Data Benchmarks ====================

    /**
     * Benchmark test with clustered synthetic data - more representative of real embeddings.
     * <p>
     * This test generates data with cluster structure, which is more similar to real-world
     * vector data (e.g., embeddings from neural networks have semantic structure).
     * HNSW performs much better on structured data than uniformly random data.
     */
    @Test
    void testRecallWithClusteredData()
    {
        System.out.println("=== Clustered Data Recall Benchmark ===");

        final int numClusters = 100;
        final int vectorsPerCluster = 100;
        final int totalVectors = numClusters * vectorsPerCluster;
        final int queryCount = 100;
        final float clusterSpread = 0.1f; // How spread out vectors are within a cluster

        System.out.println("Clusters: " + numClusters);
        System.out.println("Vectors per cluster: " + vectorsPerCluster);
        System.out.println("Total vectors: " + totalVectors);
        System.out.println("Query count: " + queryCount);
        System.out.println("Dimension: " + DIMENSION);
        System.out.println();

        final Random random = new Random(SEED);

        // Generate cluster centers
        System.out.println("Generating cluster centers...");
        final List<float[]> clusterCenters = new ArrayList<>(numClusters);
        for (int i = 0; i < numClusters; i++)
        {
            clusterCenters.add(randomVector(random, DIMENSION));
        }

        // Generate vectors around cluster centers
        System.out.println("Generating clustered vectors...");
        final List<float[]> baseVectors = new ArrayList<>(totalVectors);
        for (int c = 0; c < numClusters; c++)
        {
            final float[] center = clusterCenters.get(c);
            for (int i = 0; i < vectorsPerCluster; i++)
            {
                final float[] vec = new float[DIMENSION];
                for (int d = 0; d < DIMENSION; d++)
                {
                    vec[d] = center[d] + (random.nextFloat() * 2 - 1) * clusterSpread;
                }
                baseVectors.add(vec);
            }
        }

        // Generate query vectors (random points from clusters)
        System.out.println("Generating query vectors...");
        final List<float[]> queryVectors = new ArrayList<>(queryCount);
        for (int i = 0; i < queryCount; i++)
        {
            final int clusterIdx = random.nextInt(numClusters);
            final float[] center = clusterCenters.get(clusterIdx);
            final float[] query = new float[DIMENSION];
            for (int d = 0; d < DIMENSION; d++)
            {
                query[d] = center[d] + (random.nextFloat() * 2 - 1) * clusterSpread;
            }
            queryVectors.add(query);
        }

        // Compute ground truth
        final int[] kValues = {1, 10, 50, 100};
        final int maxK = Arrays.stream(kValues).max().orElse(100);

        System.out.println("Computing ground truth...");
        final long gtStartTime = System.currentTimeMillis();
        final List<int[]> groundTruth = computeGroundTruth(baseVectors, queryVectors, maxK);
        final long gtTime = System.currentTimeMillis() - gtStartTime;
        System.out.printf("Ground truth computation: %,d ms%n", gtTime);
        System.out.println();

        // Build VectorIndex
        System.out.println("Building VectorIndex...");
        final GigaMap<VectorEntity> gigaMap = GigaMap.New();
        final VectorIndices<VectorEntity> vectorIndices = gigaMap.index().register(VectorIndices.Category());

        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(DIMENSION)
            .similarityFunction(VectorSimilarityFunction.EUCLIDEAN)
            .maxDegree(16)
            .beamWidth(100)
            .build();

        final VectorIndex<VectorEntity> index = vectorIndices.add(
            "clustered_benchmark",
            config,
            new VectorEntityVectorizer()
        );

        final long indexStartTime = System.currentTimeMillis();
        final List<VectorEntity> entities = new ArrayList<>(totalVectors);
        for (int i = 0; i < totalVectors; i++)
        {
            entities.add(new VectorEntity(i, baseVectors.get(i)));
        }
        gigaMap.addAll(entities);
        final long indexTime = System.currentTimeMillis() - indexStartTime;
        System.out.printf("Indexing time: %,d ms (%.1f vectors/sec)%n",
            indexTime, totalVectors / (indexTime / 1000.0));
        System.out.println();

        // Measure recall for different k values
        System.out.println("=== Recall Results (Clustered Data) ===");
        System.out.println("k\tRecall@k");
        System.out.println("---\t--------");

        double recall10 = 0;
        for (final int k : kValues)
        {
            final double recall = measureRecall(index, queryVectors, groundTruth, k);
            System.out.printf("%d\t%.4f (%.1f%%)%n", k, recall, recall * 100);
            if (k == 10)
            {
                recall10 = recall;
            }
        }

        System.out.println();

        // For clustered data, we expect much higher recall
        System.out.printf("Recall@10: %.1f%% (expected: >= 90%% for clustered data)%n", recall10 * 100);
        assertTrue(recall10 >= 0.85,
            "Recall@10 on clustered data should be >= 85%, got " + (recall10 * 100) + "%");

        System.out.println();
        System.out.println("=== Clustered Data Benchmark Complete ===");
    }

    /**
     * Benchmark test with uniformly random synthetic data.
     * <p>
     * <b>Note:</b> Uniformly random vectors in high dimensions suffer from the "curse of
     * dimensionality" - all pairwise distances concentrate around the same value, making
     * it difficult for any ANN algorithm to distinguish true nearest neighbors. This test
     * serves as a baseline measurement; use {@link #testRecallWithClusteredData()} or
     * {@link #testRecallWithSiftData()} for meaningful recall benchmarks.
     * <p>
     * This test:
     * <ol>
     *   <li>Generates N random 128-dimensional vectors</li>
     *   <li>Generates Q query vectors</li>
     *   <li>Computes brute-force k-NN as ground truth</li>
     *   <li>Builds VectorIndex and searches</li>
     *   <li>Reports recall@k (no assertion - this is a measurement)</li>
     * </ol>
     */
    @Test
    void testRecallWithSyntheticData()
    {
        System.out.println("=== Synthetic Data Recall Benchmark ===");
        System.out.println("Base vectors: " + SYNTHETIC_VECTOR_COUNT);
        System.out.println("Query vectors: " + SYNTHETIC_QUERY_COUNT);
        System.out.println("Dimension: " + DIMENSION);
        System.out.println();

        final Random random = new Random(SEED);

        // Generate base vectors (unnormalized for better Euclidean distance spread)
        System.out.println("Generating base vectors...");
        final List<float[]> baseVectors = new ArrayList<>(SYNTHETIC_VECTOR_COUNT);
        for (int i = 0; i < SYNTHETIC_VECTOR_COUNT; i++)
        {
            baseVectors.add(randomVectorUnnormalized(random, DIMENSION));
        }

        // Generate query vectors
        System.out.println("Generating query vectors...");
        final List<float[]> queryVectors = new ArrayList<>(SYNTHETIC_QUERY_COUNT);
        for (int i = 0; i < SYNTHETIC_QUERY_COUNT; i++)
        {
            queryVectors.add(randomVectorUnnormalized(random, DIMENSION));
        }

        // Compute ground truth using brute-force k-NN
        final int[] kValues = {1, 10, 50, 100};
        final int maxK = Arrays.stream(kValues).max().orElse(100);

        System.out.println("Computing ground truth (brute-force k-NN for k=" + maxK + ")...");
        final long gtStartTime = System.currentTimeMillis();
        final List<int[]> groundTruth = computeGroundTruth(baseVectors, queryVectors, maxK);
        final long gtTime = System.currentTimeMillis() - gtStartTime;
        System.out.printf("Ground truth computation: %,d ms%n", gtTime);
        System.out.println();

        // Build VectorIndex
        System.out.println("Building VectorIndex...");
        final GigaMap<VectorEntity> gigaMap = GigaMap.New();
        final VectorIndices<VectorEntity> vectorIndices = gigaMap.index().register(VectorIndices.Category());

        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(DIMENSION)
            .similarityFunction(VectorSimilarityFunction.EUCLIDEAN)
            .maxDegree(16)
            .beamWidth(100)
            .build();

        final VectorIndex<VectorEntity> index = vectorIndices.add(
            "benchmark",
            config,
            new VectorEntityVectorizer()
        );

        final long indexStartTime = System.currentTimeMillis();
        final List<VectorEntity> entities = new ArrayList<>(SYNTHETIC_VECTOR_COUNT);
        for (int i = 0; i < SYNTHETIC_VECTOR_COUNT; i++)
        {
            entities.add(new VectorEntity(i, baseVectors.get(i)));
        }
        gigaMap.addAll(entities);
        final long indexTime = System.currentTimeMillis() - indexStartTime;
        System.out.printf("Indexing time: %,d ms (%.1f vectors/sec)%n",
            indexTime, SYNTHETIC_VECTOR_COUNT / (indexTime / 1000.0));
        System.out.println();

        // Measure recall for different k values
        System.out.println("=== Recall Results ===");
        System.out.println("k\tRecall@k");
        System.out.println("---\t--------");

        double recall10 = 0;
        for (final int k : kValues)
        {
            final double recall = measureRecall(index, queryVectors, groundTruth, k);
            System.out.printf("%d\t%.4f (%.1f%%)%n", k, recall, recall * 100);
            if (k == 10)
            {
                recall10 = recall;
            }
        }

        System.out.println();

        // Print diagnostic info for first query
        printDiagnosticInfo(index, queryVectors.get(0), groundTruth.get(0), baseVectors, 10);

        // Note about synthetic data recall
        System.out.println();
        System.out.println("NOTE: Uniformly random vectors in high dimensions suffer from the");
        System.out.println("'curse of dimensionality' - all pairwise distances concentrate around");
        System.out.println("the same value, making it hard to distinguish true nearest neighbors.");
        System.out.println("For meaningful recall benchmarks, use real data like SIFT.");
        System.out.printf("%nRecall@10: %.1f%%%n", recall10 * 100);

        // No strict assertion for synthetic data - this is a benchmark measurement
        // Real-world structured data (SIFT, embeddings) achieves much higher recall

        System.out.println();
        System.out.println("=== Synthetic Data Benchmark Complete ===");
    }

    /**
     * Prints diagnostic information comparing search results with ground truth.
     */
    private void printDiagnosticInfo(
        final VectorIndex<VectorEntity> index,
        final float[] query,
        final int[] truth,
        final List<float[]> baseVectors,
        final int k
    )
    {
        System.out.println();
        System.out.println("=== Diagnostic Info (Query 0) ===");

        // Search
        final VectorSearchResult<VectorEntity> result = index.search(query, k);

        // Print ground truth
        System.out.println("Ground truth (brute-force k-NN):");
        for (int i = 0; i < Math.min(k, truth.length); i++)
        {
            final int idx = truth[i];
            final double dist = euclideanDistance(query, baseVectors.get(idx));
            System.out.printf("  [%d] id=%d, distance=%.6f%n", i, idx, dist);
        }

        // Print search results
        System.out.println("VectorIndex search results:");
        int rank = 0;
        for (final VectorSearchResult.Entry<VectorEntity> entry : result)
        {
            final int id = entry.entity().id();
            final double dist = euclideanDistance(query, baseVectors.get(id));
            final boolean inTruth = contains(truth, id, k);
            System.out.printf("  [%d] id=%d, score=%.6f, distance=%.6f %s%n",
                rank++, id, entry.score(), dist, inTruth ? "(IN TRUTH)" : "");
        }
    }

    private static boolean contains(final int[] arr, final int value, final int limit)
    {
        for (int i = 0; i < Math.min(limit, arr.length); i++)
        {
            if (arr[i] == value)
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Benchmark test with real SIFT data (if available).
     * <p>
     * SIFT (Scale-Invariant Feature Transform) is the standard benchmark dataset
     * for vector similarity search. This test uses siftsmall (10K vectors).
     * <p>
     * To run this test, download the data from:
     * <code>ftp://ftp.irisa.fr/local/texmex/corpus/siftsmall.tar.gz</code>
     */
    @Test
    void testRecallWithSiftData()
    {
        // Check if SIFT data files exist
        if (!Files.exists(SIFT_BASE_PATH) ||
            !Files.exists(SIFT_QUERY_PATH) ||
            !Files.exists(SIFT_GROUNDTRUTH_PATH))
        {
            System.out.println("=== SIFT Data Benchmark SKIPPED ===");
            System.out.println("SIFT data files not found. To run this benchmark:");
            System.out.println("1. Download: ftp://ftp.irisa.fr/local/texmex/corpus/siftsmall.tar.gz");
            System.out.println("2. Extract to: src/test/resources/sift/");
            System.out.println("   - siftsmall_base.fvecs");
            System.out.println("   - siftsmall_query.fvecs");
            System.out.println("   - siftsmall_groundtruth.ivecs");
            return;
        }

        System.out.println("=== SIFT Data Recall Benchmark ===");

        try
        {
            // Load SIFT data
            System.out.println("Loading SIFT base vectors...");
            final List<float[]> baseVectors = readFvecs(SIFT_BASE_PATH);
            System.out.println("Loaded " + baseVectors.size() + " base vectors");

            System.out.println("Loading SIFT query vectors...");
            final List<float[]> queryVectors = readFvecs(SIFT_QUERY_PATH);
            System.out.println("Loaded " + queryVectors.size() + " query vectors");

            System.out.println("Loading SIFT ground truth...");
            final List<int[]> groundTruth = readIvecs(SIFT_GROUNDTRUTH_PATH);
            System.out.println("Loaded " + groundTruth.size() + " ground truth entries");
            System.out.println();

            // Validate dimensions
            final int dimension = baseVectors.get(0).length;
            System.out.println("Vector dimension: " + dimension);

            // Build VectorIndex
            System.out.println("Building VectorIndex...");
            final GigaMap<VectorEntity> gigaMap = GigaMap.New();
            final VectorIndices<VectorEntity> vectorIndices = gigaMap.index().register(VectorIndices.Category());

            final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
                .dimension(dimension)
                .similarityFunction(VectorSimilarityFunction.EUCLIDEAN)
                .maxDegree(16)
                .beamWidth(100)
                .build();

            final VectorIndex<VectorEntity> index = vectorIndices.add(
                "sift_benchmark",
                config,
                new VectorEntityVectorizer()
            );

            final long indexStartTime = System.currentTimeMillis();
            final List<VectorEntity> entities = new ArrayList<>(baseVectors.size());
            for (int i = 0; i < baseVectors.size(); i++)
            {
                entities.add(new VectorEntity(i, baseVectors.get(i)));
            }
            gigaMap.addAll(entities);
            final long indexTime = System.currentTimeMillis() - indexStartTime;
            System.out.printf("Indexing time: %,d ms (%.1f vectors/sec)%n",
                indexTime, baseVectors.size() / (indexTime / 1000.0));
            System.out.println();

            // Measure recall for different k values
            final int[] kValues = {1, 10, 50, 100};

            System.out.println("=== SIFT Recall Results ===");
            System.out.println("k\tRecall@k");
            System.out.println("---\t--------");

            for (final int k : kValues)
            {
                final double recall = measureRecall(index, queryVectors, groundTruth, k);
                System.out.printf("%d\t%.4f (%.1f%%)%n", k, recall, recall * 100);
            }

            System.out.println();
            System.out.println("=== SIFT Data Benchmark Complete ===");
        }
        catch (final IOException e)
        {
            fail("Failed to load SIFT data: " + e.getMessage());
        }
    }

    /**
     * Performance benchmark measuring QPS (queries per second) and latency.
     */
    @Test
    void testSearchPerformance()
    {
        System.out.println("=== Search Performance Benchmark ===");

        final int vectorCount = SYNTHETIC_VECTOR_COUNT;
        final int warmupQueries = 100;
        final int benchmarkQueries = 1000;
        final int k = 10;

        final Random random = new Random(SEED);

        // Generate and index vectors
        System.out.println("Generating and indexing " + vectorCount + " vectors...");
        final GigaMap<VectorEntity> gigaMap = GigaMap.New();
        final VectorIndices<VectorEntity> vectorIndices = gigaMap.index().register(VectorIndices.Category());

        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(DIMENSION)
            .similarityFunction(VectorSimilarityFunction.EUCLIDEAN)
            .maxDegree(16)
            .beamWidth(100)
            .build();

        final VectorIndex<VectorEntity> index = vectorIndices.add(
            "perf_benchmark",
            config,
            new VectorEntityVectorizer()
        );

        final List<VectorEntity> entities = new ArrayList<>(vectorCount);
        for (int i = 0; i < vectorCount; i++)
        {
            entities.add(new VectorEntity(i, randomVector(random, DIMENSION)));
        }
        gigaMap.addAll(entities);
        System.out.println("Indexing complete.");
        System.out.println();

        // Generate query vectors
        final List<float[]> queryVectors = new ArrayList<>(benchmarkQueries);
        for (int i = 0; i < benchmarkQueries; i++)
        {
            queryVectors.add(randomVector(random, DIMENSION));
        }

        // Warmup
        System.out.println("Warming up (" + warmupQueries + " queries)...");
        for (int i = 0; i < warmupQueries; i++)
        {
            index.search(queryVectors.get(i % queryVectors.size()), k);
        }

        // Benchmark
        System.out.println("Running benchmark (" + benchmarkQueries + " queries)...");
        final long[] latencies = new long[benchmarkQueries];

        final long startTime = System.nanoTime();
        for (int i = 0; i < benchmarkQueries; i++)
        {
            final long queryStart = System.nanoTime();
            index.search(queryVectors.get(i), k);
            latencies[i] = System.nanoTime() - queryStart;
        }
        final long totalTime = System.nanoTime() - startTime;

        // Calculate statistics
        Arrays.sort(latencies);
        final double totalTimeMs = totalTime / 1_000_000.0;
        final double qps = benchmarkQueries / (totalTimeMs / 1000.0);
        final double avgLatencyMs = totalTimeMs / benchmarkQueries;
        final double p50LatencyMs = latencies[benchmarkQueries / 2] / 1_000_000.0;
        final double p90LatencyMs = latencies[(int) (benchmarkQueries * 0.90)] / 1_000_000.0;
        final double p99LatencyMs = latencies[(int) (benchmarkQueries * 0.99)] / 1_000_000.0;

        System.out.println();
        System.out.println("=== Performance Results ===");
        System.out.printf("Vector count: %,d%n", vectorCount);
        System.out.printf("Query count: %,d%n", benchmarkQueries);
        System.out.printf("k (neighbors): %d%n", k);
        System.out.println();
        System.out.printf("Total time: %.2f ms%n", totalTimeMs);
        System.out.printf("QPS (queries/second): %.1f%n", qps);
        System.out.println();
        System.out.printf("Average latency: %.3f ms%n", avgLatencyMs);
        System.out.printf("p50 latency: %.3f ms%n", p50LatencyMs);
        System.out.printf("p90 latency: %.3f ms%n", p90LatencyMs);
        System.out.printf("p99 latency: %.3f ms%n", p99LatencyMs);
        System.out.println();

        // Assert reasonable performance
        assertTrue(avgLatencyMs < 10.0, "Average latency should be < 10ms for " + vectorCount + " vectors");
        assertTrue(qps > 100, "QPS should be > 100 for " + vectorCount + " vectors");

        System.out.println("=== Performance Benchmark Complete ===");
    }

    // ==================== Helper Methods ====================

    /**
     * Generates a random normalized vector.
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
     * Generates a random unnormalized vector.
     * Better for Euclidean distance benchmarks as it provides more distance spread.
     */
    private static float[] randomVectorUnnormalized(final Random random, final int dimension)
    {
        final float[] vector = new float[dimension];
        for (int i = 0; i < dimension; i++)
        {
            vector[i] = random.nextFloat() * 2 - 1; // Range [-1, 1]
        }
        return vector;
    }

    /**
     * Computes brute-force k-NN ground truth using Euclidean distance.
     *
     * @param baseVectors  the base vectors to search in
     * @param queryVectors the query vectors
     * @param k            the number of nearest neighbors
     * @return list of ground truth arrays (one per query, containing k nearest neighbor indices)
     */
    private static List<int[]> computeGroundTruth(
        final List<float[]> baseVectors,
        final List<float[]> queryVectors,
        final int k
    )
    {
        final List<int[]> groundTruth = new ArrayList<>(queryVectors.size());

        for (final float[] query : queryVectors)
        {
            // Compute distances to all base vectors
            final double[] distances = new double[baseVectors.size()];
            for (int i = 0; i < baseVectors.size(); i++)
            {
                distances[i] = euclideanDistance(query, baseVectors.get(i));
            }

            // Find k smallest distances
            final int[] indices = new int[baseVectors.size()];
            for (int i = 0; i < indices.length; i++)
            {
                indices[i] = i;
            }

            // Partial sort to get k smallest
            for (int i = 0; i < k; i++)
            {
                int minIdx = i;
                for (int j = i + 1; j < indices.length; j++)
                {
                    if (distances[indices[j]] < distances[indices[minIdx]])
                    {
                        minIdx = j;
                    }
                }
                // Swap
                final int tmp = indices[i];
                indices[i] = indices[minIdx];
                indices[minIdx] = tmp;
            }

            // Copy first k indices
            groundTruth.add(Arrays.copyOf(indices, k));
        }

        return groundTruth;
    }

    /**
     * Computes Euclidean distance between two vectors.
     */
    private static double euclideanDistance(final float[] a, final float[] b)
    {
        double sum = 0;
        for (int i = 0; i < a.length; i++)
        {
            final double diff = a[i] - b[i];
            sum += diff * diff;
        }
        return Math.sqrt(sum);
    }

    /**
     * Measures recall@k for the given index against ground truth.
     *
     * @param index        the vector index to test
     * @param queryVectors the query vectors
     * @param groundTruth  the ground truth (k nearest neighbor indices per query)
     * @param k            the number of neighbors to retrieve
     * @return the average recall@k across all queries
     */
    private static double measureRecall(
        final VectorIndex<VectorEntity> index,
        final List<float[]> queryVectors,
        final List<int[]> groundTruth,
        final int k
    )
    {
        double totalRecall = 0;

        for (int q = 0; q < queryVectors.size(); q++)
        {
            final float[] query = queryVectors.get(q);
            final int[] truth = groundTruth.get(q);

            // Search the index
            final VectorSearchResult<VectorEntity> result = index.search(query, k);

            // Extract retrieved IDs
            final Set<Integer> retrieved = new HashSet<>();
            for (final VectorSearchResult.Entry<VectorEntity> entry : result)
            {
                retrieved.add(entry.entity().id());
            }

            // Compute recall for this query
            final Set<Integer> truthSet = new HashSet<>();
            for (int i = 0; i < k && i < truth.length; i++)
            {
                truthSet.add(truth[i]);
            }

            final double recall = computeRecall(retrieved, truthSet);
            totalRecall += recall;
        }

        return totalRecall / queryVectors.size();
    }

    /**
     * Computes recall: fraction of true nearest neighbors found.
     *
     * @param retrieved   IDs returned by VectorIndex
     * @param groundTruth true k nearest neighbor IDs
     * @return recall value between 0 and 1
     */
    private static double computeRecall(final Set<Integer> retrieved, final Set<Integer> groundTruth)
    {
        if (groundTruth.isEmpty())
        {
            return 1.0;
        }

        int intersection = 0;
        for (final Integer id : retrieved)
        {
            if (groundTruth.contains(id))
            {
                intersection++;
            }
        }
        return (double) intersection / groundTruth.size();
    }

    // ==================== FVECS/IVECS File Format ====================

    /**
     * Reads vectors from a .fvecs file (SIFT format).
     * <p>
     * Format: Each vector is stored as [dimension (4 bytes, little-endian int)] + [float values]
     */
    private static List<float[]> readFvecs(final Path path) throws IOException
    {
        final List<float[]> vectors = new ArrayList<>();

        try (final DataInputStream dis = new DataInputStream(new FileInputStream(path.toFile())))
        {
            while (dis.available() > 0)
            {
                vectors.add(readFvec(dis));
            }
        }

        return vectors;
    }

    /**
     * Reads a single vector from a .fvecs file.
     */
    private static float[] readFvec(final DataInputStream dis) throws IOException
    {
        final int dim = Integer.reverseBytes(dis.readInt()); // Little-endian
        final float[] vec = new float[dim];
        for (int i = 0; i < dim; i++)
        {
            vec[i] = Float.intBitsToFloat(Integer.reverseBytes(dis.readInt()));
        }
        return vec;
    }

    /**
     * Reads integer vectors from a .ivecs file (ground truth format).
     * <p>
     * Format: Each vector is stored as [count (4 bytes, little-endian int)] + [int values]
     */
    private static List<int[]> readIvecs(final Path path) throws IOException
    {
        final List<int[]> vectors = new ArrayList<>();

        try (final DataInputStream dis = new DataInputStream(new FileInputStream(path.toFile())))
        {
            while (dis.available() > 0)
            {
                vectors.add(readIvec(dis));
            }
        }

        return vectors;
    }

    /**
     * Reads a single integer vector from a .ivecs file.
     */
    private static int[] readIvec(final DataInputStream dis) throws IOException
    {
        final int count = Integer.reverseBytes(dis.readInt()); // Little-endian
        final int[] vec = new int[count];
        for (int i = 0; i < count; i++)
        {
            vec[i] = Integer.reverseBytes(dis.readInt());
        }
        return vec;
    }
}
