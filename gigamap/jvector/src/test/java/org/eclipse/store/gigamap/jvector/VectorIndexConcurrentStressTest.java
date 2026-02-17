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
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Concurrent stress tests for VectorIndex thread-safety.
 * <p>
 * Multiple threads perform random add, update, remove, and search operations
 * concurrently. Each test configuration exercises a different combination of:
 * <ul>
 *   <li>On-disk vs. in-memory</li>
 *   <li>PQ compression</li>
 *   <li>Eventual indexing</li>
 *   <li>Parallel on-disk write</li>
 *   <li>Background optimization</li>
 *   <li>Background persistence</li>
 * </ul>
 * <p>
 * The primary assertion is that no exceptions are thrown and no deadlocks occur
 * (enforced by {@link Timeout}).
 */
class VectorIndexConcurrentStressTest
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


    // ==================== Configuration Combinations ====================

    /**
     * Describes one configuration combination.
     */
    private record ConfigCombo(
        String   label,
        boolean  onDisk,
        boolean  pqCompression,
        boolean  eventual,
        boolean  parallel,
        boolean  backgroundOptimization,
        boolean  backgroundPersistence
    ) {}

    /**
     * Generates all valid configuration combinations.
     * <p>
     * Constraints:
     * <ul>
     *   <li>PQ compression requires onDisk</li>
     *   <li>Background persistence requires onDisk</li>
     *   <li>parallel only meaningful when onDisk</li>
     * </ul>
     */
    private static List<ConfigCombo> allCombos()
    {
        final List<ConfigCombo> combos = new ArrayList<>();

        // In-memory combos: onDisk=false → pq=false, persistence=false, parallel irrelevant
        for(final boolean eventual : new boolean[]{false, true})
        {
            for(final boolean optimization : new boolean[]{false, true})
            {
                combos.add(new ConfigCombo(
                    "mem|eventual=" + eventual + "|opt=" + optimization,
                    false, false, eventual, false, optimization, false
                ));
            }
        }

        // On-disk combos
        for(final boolean pq : new boolean[]{false, true})
        {
            for(final boolean eventual : new boolean[]{false, true})
            {
                for(final boolean parallel : new boolean[]{false, true})
                {
                    for(final boolean optimization : new boolean[]{false, true})
                    {
                        for(final boolean persistence : new boolean[]{false, true})
                        {
                            combos.add(new ConfigCombo(
                                "disk|pq=" + pq
                                    + "|eventual=" + eventual
                                    + "|parallel=" + parallel
                                    + "|opt=" + optimization
                                    + "|persist=" + persistence,
                                true, pq, eventual, parallel, optimization, persistence
                            ));
                        }
                    }
                }
            }
        }

        return combos;
    }

    /**
     * Builds a VectorIndexConfiguration from a combo.
     */
    private static VectorIndexConfiguration buildConfig(
        final ConfigCombo combo,
        final int         dimension,
        final Path        indexDir
    )
    {
        final VectorIndexConfiguration.Builder builder = VectorIndexConfiguration.builder()
            .dimension(dimension)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .maxDegree(combo.pqCompression() ? 32 : 16)
            .beamWidth(100)
            .eventualIndexing(combo.eventual());

        if(combo.onDisk())
        {
            builder
                .onDisk(true)
                .indexDirectory(indexDir)
                .parallelOnDiskWrite(combo.parallel());

            if(combo.pqCompression())
            {
                builder
                    .enablePqCompression(true)
                    .pqSubspaces(dimension / 4);
            }

            if(combo.backgroundPersistence())
            {
                builder
                    .persistenceIntervalMs(200)
                    .minChangesBetweenPersists(1);
            }
        }

        if(combo.backgroundOptimization())
        {
            builder
                .optimizationIntervalMs(200)
                .minChangesBetweenOptimizations(5);
        }

        return builder.build();
    }


    // ==================== Stress Test Core ====================

    /**
     * Runs a concurrent stress test for a single configuration.
     * <p>
     * 4 threads perform random add/update/remove/search operations concurrently.
     * A pool of pre-seeded entities ensures ordinals exist for update/remove.
     *
     * @param combo    the configuration combination
     * @param indexDir directory for on-disk index (may be null for in-memory)
     */
    private void runStressTest(final ConfigCombo combo, final Path indexDir) throws Exception
    {
        final int dimension       = 64;
        final int seedCount       = 30;
        final int opsPerThread    = 60;
        final int threadCount     = 4;

        final GigaMap<Document> gigaMap = GigaMap.New();
        final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());

        final VectorIndexConfiguration config = buildConfig(combo, dimension, indexDir);

        final VectorIndex<Document> index = vectorIndices.add(
            "embeddings", config, new ComputedDocumentVectorizer()
        );

        try
        {
            // Seed the index with initial entities so updates/removes have targets
            final Random seedRandom = new Random(42);
            for(int i = 0; i < seedCount; i++)
            {
                gigaMap.add(new Document("seed_" + i, randomVector(seedRandom, dimension)));
            }

            // For eventual indexing, drain the seed operations
            if(combo.eventual())
            {
                final VectorIndex.Default<Document> defaultIndex = (VectorIndex.Default<Document>)index;
                defaultIndex.indexingManager.drainQueue();
            }

            // If PQ compression, train before concurrent access
            if(combo.pqCompression())
            {
                ((VectorIndex.Internal<Document>)index).trainCompressionIfNeeded();
            }

            // Shared state for coordinating threads
            final AtomicLong nextEntityId = new AtomicLong(seedCount);
            final AtomicBoolean hasError = new AtomicBoolean(false);
            final AtomicInteger completedOps = new AtomicInteger(0);
            final List<Throwable> errors = java.util.Collections.synchronizedList(new ArrayList<>());
            final CountDownLatch startLatch = new CountDownLatch(1);
            final CountDownLatch doneLatch = new CountDownLatch(threadCount);

            final ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            for(int t = 0; t < threadCount; t++)
            {
                final int threadId = t;
                executor.submit(() ->
                {
                    try
                    {
                        // Wait for all threads to be ready
                        startLatch.await();

                        final Random random = new Random(1000 + threadId);

                        for(int op = 0; op < opsPerThread && !hasError.get(); op++)
                        {
                            try
                            {
                                final int action = random.nextInt(100);

                                if(action < 30)
                                {
                                    // 30%: ADD
                                    final float[] vector = randomVector(random, dimension);
                                    synchronized(gigaMap)
                                    {
                                        gigaMap.add(new Document(
                                            "t" + threadId + "_" + op, vector
                                        ));
                                    }
                                }
                                else if(action < 45)
                                {
                                    // 15%: UPDATE (set) — target a seed entity
                                    final long targetId = random.nextInt(seedCount);
                                    final float[] vector = randomVector(random, dimension);
                                    synchronized(gigaMap)
                                    {
                                        try
                                        {
                                            gigaMap.set(targetId, new Document(
                                                "updated_" + targetId, vector
                                            ));
                                        }
                                        catch(final Exception e)
                                        {
                                            // Entity may have been removed by another thread — acceptable
                                        }
                                    }
                                }
                                else if(action < 55)
                                {
                                    // 10%: REMOVE — target a seed entity
                                    final long targetId = random.nextInt(seedCount);
                                    synchronized(gigaMap)
                                    {
                                        try
                                        {
                                            gigaMap.removeById(targetId);
                                        }
                                        catch(final Exception e)
                                        {
                                            // Entity may already be removed — acceptable
                                        }
                                    }
                                }
                                else
                                {
                                    // 45%: SEARCH
                                    final float[] queryVector = randomVector(random, dimension);
                                    final VectorSearchResult<Document> result = index.search(queryVector, 5);
                                    // Result may be empty if all entities were removed — that's fine
                                    assertNotNull(result);
                                }

                                completedOps.incrementAndGet();
                            }
                            catch(final Exception e)
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

            // Release all threads simultaneously
            startLatch.countDown();

            // Wait for completion
            assertTrue(doneLatch.await(60, TimeUnit.SECONDS),
                "Threads should complete within timeout for: " + combo.label());

            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

            // Report errors
            if(!errors.isEmpty())
            {
                final StringBuilder sb = new StringBuilder();
                sb.append("Concurrent stress test failed for: ").append(combo.label());
                sb.append("\n").append(errors.size()).append(" error(s):");
                for(final Throwable err : errors)
                {
                    sb.append("\n  - ").append(err.getClass().getSimpleName())
                      .append(": ").append(err.getMessage());
                }
                fail(sb.toString());
            }

            // Verify the index is still consistent — drain and search
            if(combo.eventual())
            {
                final VectorIndex.Default<Document> defaultIndex = (VectorIndex.Default<Document>)index;
                if(defaultIndex.indexingManager != null)
                {
                    defaultIndex.indexingManager.drainQueue();
                }
            }

            final VectorSearchResult<Document> finalResult = index.search(
                randomVector(new Random(999), dimension), 5
            );
            assertNotNull(finalResult);
        }
        finally
        {
            index.close();
        }
    }


    // ==================== In-Memory Combinations ====================

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void testConcurrentStress_InMemory()
    {
        final List<ConfigCombo> combos = allCombos().stream()
            .filter(c -> !c.onDisk())
            .toList();

        assertFalse(combos.isEmpty(), "Should have in-memory combos");

        final List<String> passed = new ArrayList<>();
        for(final ConfigCombo combo : combos)
        {
            try
            {
                this.runStressTest(combo, null);
                passed.add(combo.label());
            }
            catch(final Exception e)
            {
                fail("Failed for combo: " + combo.label() + " — " + e.getMessage(), e);
            }
        }

        assertEquals(combos.size(), passed.size(),
            "All in-memory combos should pass");
    }


    // ==================== On-Disk without PQ Combinations ====================

    @Test
    @Timeout(value = 180, unit = TimeUnit.SECONDS)
    void testConcurrentStress_OnDisk_NoPQ(@TempDir final Path tempDir)
    {
        final List<ConfigCombo> combos = allCombos().stream()
            .filter(c -> c.onDisk() && !c.pqCompression())
            .toList();

        assertFalse(combos.isEmpty(), "Should have on-disk no-PQ combos");

        final List<String> passed = new ArrayList<>();
        int comboIndex = 0;
        for(final ConfigCombo combo : combos)
        {
            final Path indexDir = tempDir.resolve("combo_" + comboIndex++);
            try
            {
                this.runStressTest(combo, indexDir);
                passed.add(combo.label());
            }
            catch(final Exception e)
            {
                fail("Failed for combo: " + combo.label() + " — " + e.getMessage(), e);
            }
        }

        assertEquals(combos.size(), passed.size(),
            "All on-disk no-PQ combos should pass");
    }


    // ==================== On-Disk with PQ Combinations ====================

    @Test
    @Timeout(value = 180, unit = TimeUnit.SECONDS)
    void testConcurrentStress_OnDisk_WithPQ(@TempDir final Path tempDir)
    {
        final List<ConfigCombo> combos = allCombos().stream()
            .filter(c -> c.onDisk() && c.pqCompression())
            .toList();

        assertFalse(combos.isEmpty(), "Should have on-disk PQ combos");

        final List<String> passed = new ArrayList<>();
        int comboIndex = 0;
        for(final ConfigCombo combo : combos)
        {
            final Path indexDir = tempDir.resolve("pq_combo_" + comboIndex++);
            try
            {
                this.runStressTest(combo, indexDir);
                passed.add(combo.label());
            }
            catch(final Exception e)
            {
                fail("Failed for combo: " + combo.label() + " — " + e.getMessage(), e);
            }
        }

        assertEquals(combos.size(), passed.size(),
            "All on-disk PQ combos should pass");
    }


    // ==================== Focused Eventual Indexing Stress ====================

    /**
     * Focused test: heavier load with eventual indexing enabled.
     * More operations per thread to stress the background queue.
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void testEventualIndexingHeavyConcurrentLoad(@TempDir final Path tempDir)
        throws Exception
    {
        final int dimension = 64;
        final int seedCount = 50;
        final int opsPerThread = 150;
        final int threadCount = 6;
        final Path indexDir = tempDir.resolve("heavy");

        final GigaMap<Document> gigaMap = GigaMap.New();
        final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());

        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(dimension)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .maxDegree(16)
            .beamWidth(100)
            .onDisk(true)
            .indexDirectory(indexDir)
            .eventualIndexing(true)
            .optimizationIntervalMs(300)
            .minChangesBetweenOptimizations(10)
            .persistenceIntervalMs(500)
            .minChangesBetweenPersists(5)
            .build();

        final VectorIndex<Document> index = vectorIndices.add(
            "embeddings", config, new ComputedDocumentVectorizer()
        );

        try
        {
            // Seed
            final Random seedRandom = new Random(42);
            for(int i = 0; i < seedCount; i++)
            {
                gigaMap.add(new Document("seed_" + i, randomVector(seedRandom, dimension)));
            }

            final VectorIndex.Default<Document> defaultIndex = (VectorIndex.Default<Document>)index;
            defaultIndex.indexingManager.drainQueue();

            final AtomicBoolean hasError = new AtomicBoolean(false);
            final List<Throwable> errors = java.util.Collections.synchronizedList(new ArrayList<>());
            final CountDownLatch startLatch = new CountDownLatch(1);
            final CountDownLatch doneLatch = new CountDownLatch(threadCount);

            final ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            for(int t = 0; t < threadCount; t++)
            {
                final int threadId = t;
                executor.submit(() ->
                {
                    try
                    {
                        startLatch.await();
                        final Random random = new Random(2000 + threadId);

                        for(int op = 0; op < opsPerThread && !hasError.get(); op++)
                        {
                            try
                            {
                                final int action = random.nextInt(100);

                                if(action < 25)
                                {
                                    // ADD
                                    synchronized(gigaMap)
                                    {
                                        gigaMap.add(new Document(
                                            "t" + threadId + "_" + op,
                                            randomVector(random, dimension)
                                        ));
                                    }
                                }
                                else if(action < 40)
                                {
                                    // UPDATE
                                    final long targetId = random.nextInt(seedCount);
                                    synchronized(gigaMap)
                                    {
                                        try
                                        {
                                            gigaMap.set(targetId, new Document(
                                                "upd_" + targetId,
                                                randomVector(random, dimension)
                                            ));
                                        }
                                        catch(final Exception ignored) {}
                                    }
                                }
                                else if(action < 50)
                                {
                                    // REMOVE
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
                                else
                                {
                                    // SEARCH
                                    final VectorSearchResult<Document> result = index.search(
                                        randomVector(random, dimension), 5
                                    );
                                    assertNotNull(result);
                                }
                            }
                            catch(final Exception e)
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

            assertTrue(doneLatch.await(60, TimeUnit.SECONDS),
                "Heavy concurrent load should complete within timeout");

            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

            if(!errors.isEmpty())
            {
                final StringBuilder sb = new StringBuilder("Heavy eventual indexing stress test failed:");
                for(final Throwable err : errors)
                {
                    sb.append("\n  - ").append(err.getClass().getSimpleName())
                      .append(": ").append(err.getMessage());
                }
                fail(sb.toString());
            }

            // Drain and verify final state
            defaultIndex.indexingManager.drainQueue();

            final VectorSearchResult<Document> finalResult = index.search(
                randomVector(new Random(999), dimension), 5
            );
            assertNotNull(finalResult);
        }
        finally
        {
            index.close();
        }
    }
}
