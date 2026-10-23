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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
@Tag("slow")
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
        this.runStressTest(combo, indexDir, new ComputedDocumentVectorizer());
    }

    private void runStressTestEmbedded(final ConfigCombo combo, final Path indexDir) throws Exception
    {
        this.runStressTest(combo, indexDir, new EmbeddedDocumentVectorizer());
    }

    private void runStressTest(
        final ConfigCombo combo,
        final Path indexDir,
        final Vectorizer<Document> vectorizer
    ) throws Exception
    {
        final int dimension       = 64;
        final int seedCount       = 30;
        final int opsPerThread    = 60;
        final int threadCount     = 4;

        final GigaMap<Document> gigaMap = GigaMap.New();
        final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());

        final VectorIndexConfiguration config = buildConfig(combo, dimension, indexDir);

        try (final VectorIndex<Document> index = vectorIndices.add(
            "embeddings", config, vectorizer
        ))
        {
            // Seed the index with initial entities so updates/removes have targets
            final Random seedRandom = new Random(42);
            for (int i = 0; i < seedCount; i++)
            {
                gigaMap.add(new Document("seed_" + i, randomVector(seedRandom, dimension)));
            }

            // For eventual indexing, drain the seed operations
            if (combo.eventual())
            {
                final VectorIndex.Default<Document> defaultIndex = (VectorIndex.Default<Document>) index;
                defaultIndex.backgroundTaskManager.drainQueue();
            }

            // No explicit training step: persistToDisk() is what trains a codebook, and this
            // harness never calls it directly. The background-persistence combinations can still
            // persist on their own once the workers have added enough vectors, so PQ may or may
            // not become active here - that is deliberately not asserted either way, since this
            // test is about surviving concurrency, not about compression. Compressed search is
            // covered deterministically by the PQ tests in VectorIndexDiskTest.

            // Shared state for coordinating threads
            final AtomicLong nextEntityId = new AtomicLong(seedCount);
            final AtomicBoolean hasError = new AtomicBoolean(false);
            final AtomicInteger completedOps = new AtomicInteger(0);
            final List<Throwable> errors = java.util.Collections.synchronizedList(new ArrayList<>());
            final CountDownLatch startLatch = new CountDownLatch(1);
            final CountDownLatch doneLatch = new CountDownLatch(threadCount);

            final ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            for (int t = 0; t < threadCount; t++)
            {
                final int threadId = t;
                executor.submit(() ->
                {
                    try
                    {
                        // Wait for all threads to be ready
                        startLatch.await();

                        final Random random = new Random(1000 + threadId);

                        for (int op = 0; op < opsPerThread && !hasError.get(); op++)
                        {
                            try
                            {
                                final int action = random.nextInt(100);

                                if (action < 25)
                                {
                                    // 25%: ADD
                                    final float[] vector = randomVector(random, dimension);
                                    synchronized (gigaMap)
                                    {
                                        gigaMap.add(new Document(
                                            "t" + threadId + "_" + op, vector
                                        ));
                                    }
                                } else if (action < 37)
                                {
                                    // 12%: UPDATE (set) — target a seed entity
                                    final long targetId = random.nextInt(seedCount);
                                    final float[] vector = randomVector(random, dimension);
                                    synchronized (gigaMap)
                                    {
                                        // set() refills the slot of a removed id: no exception is expected
                                        gigaMap.set(targetId, new Document(
                                            "updated_" + targetId, vector
                                        ));
                                    }
                                } else if (action < 45)
                                {
                                    // 8%: REMOVE — target a seed entity
                                    final long targetId = random.nextInt(seedCount);
                                    synchronized (gigaMap)
                                    {
                                        // a removed id is a no-op: no exception is expected
                                        gigaMap.removeById(targetId);
                                    }
                                } else if (action < 55)
                                {
                                    // 10%: ADDALL — batch add 2-3 documents
                                    final int batchSize = 2 + random.nextInt(2);
                                    final List<Document> batch = new ArrayList<>();
                                    for (int b = 0; b < batchSize; b++)
                                    {
                                        batch.add(new Document(
                                            "t" + threadId + "_batch_" + op + "_" + b,
                                            randomVector(random, dimension)
                                        ));
                                    }
                                    synchronized (gigaMap)
                                    {
                                        gigaMap.addAll(batch);
                                    }
                                } else
                                {
                                    // 45%: SEARCH
                                    final float[] queryVector = randomVector(random, dimension);
                                    final VectorSearchResult<Document> result = index.search(queryVector, 5);
                                    // Result may be empty if all entities were removed — that's fine
                                    assertNotNull(result);
                                }

                                completedOps.incrementAndGet();
                            } catch (final Throwable e)
                            {
                                errors.add(e);
                                hasError.set(true);
                            }
                        }
                    } catch (final InterruptedException e)
                    {
                        Thread.currentThread().interrupt();
                    } finally
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
            if (!errors.isEmpty())
            {
                final StringBuilder sb = new StringBuilder();
                sb.append("Concurrent stress test failed for: ").append(combo.label());
                sb.append("\n").append(errors.size()).append(" error(s):");
                for (final Throwable err : errors)
                {
                    sb.append("\n  - ").append(err.getClass().getSimpleName())
                        .append(": ").append(err.getMessage());
                }
                fail(sb.toString());
            }

            // Verify the index is still consistent — drain and search
            if (combo.eventual())
            {
                final VectorIndex.Default<Document> defaultIndex = (VectorIndex.Default<Document>) index;
                if (defaultIndex.backgroundTaskManager != null)
                {
                    defaultIndex.backgroundTaskManager.drainQueue();
                }
            }

            final VectorSearchResult<Document> finalResult = index.search(
                randomVector(new Random(999), dimension), 5
            );
            assertNotNull(finalResult);
        }
    }


    // ==================== In-Memory Combinations ====================

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void testConcurrentStress_InMemory_Embedded()
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
                this.runStressTestEmbedded(combo, null);
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
    void testConcurrentStress_OnDisk_NoPQ_Embedded(@TempDir final Path tempDir)
    {
        final List<ConfigCombo> combos = allCombos().stream()
            .filter(c -> c.onDisk() && !c.pqCompression())
            .toList();

        assertFalse(combos.isEmpty(), "Should have on-disk no-PQ combos");

        final List<String> passed = new ArrayList<>();
        int comboIndex = 0;
        for(final ConfigCombo combo : combos)
        {
            final Path indexDir = tempDir.resolve("embedded_combo_" + comboIndex++);
            try
            {
                this.runStressTestEmbedded(combo, indexDir);
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
     * Focused test: heavier load with eventual indexing and embedded vectorizer.
     * Exercises the EntityBackedVectorValues.getVector() → GigaMap.get() path
     * that caused the original ForkJoinPool deadlock.
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void testEventualIndexingHeavyConcurrentLoad_Embedded(@TempDir final Path tempDir)
        throws Exception
    {
        final int dimension = 64;
        final int seedCount = 50;
        final int opsPerThread = 150;
        final int threadCount = 6;
        final Path indexDir = tempDir.resolve("heavy_embedded");

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

        try (final VectorIndex<Document> index = vectorIndices.add(
            "embeddings", config, new EmbeddedDocumentVectorizer()
        ))
        {
            // Seed
            final Random seedRandom = new Random(42);
            for (int i = 0; i < seedCount; i++)
            {
                gigaMap.add(new Document("seed_" + i, randomVector(seedRandom, dimension)));
            }

            final VectorIndex.Default<Document> defaultIndex = (VectorIndex.Default<Document>) index;
            defaultIndex.backgroundTaskManager.drainQueue();

            final AtomicBoolean hasError = new AtomicBoolean(false);
            final List<Throwable> errors = java.util.Collections.synchronizedList(new ArrayList<>());
            final CountDownLatch startLatch = new CountDownLatch(1);
            final CountDownLatch doneLatch = new CountDownLatch(threadCount);

            final ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            for (int t = 0; t < threadCount; t++)
            {
                final int threadId = t;
                executor.submit(() ->
                {
                    try
                    {
                        startLatch.await();
                        final Random random = new Random(2000 + threadId);

                        for (int op = 0; op < opsPerThread && !hasError.get(); op++)
                        {
                            try
                            {
                                final int action = random.nextInt(100);

                                if (action < 20)
                                {
                                    // ADD
                                    synchronized (gigaMap)
                                    {
                                        gigaMap.add(new Document(
                                            "t" + threadId + "_" + op,
                                            randomVector(random, dimension)
                                        ));
                                    }
                                }
                                else if (action < 32)
                                {
                                    // UPDATE
                                    final long targetId = random.nextInt(seedCount);
                                    synchronized (gigaMap)
                                    {
                                        // set() refills the slot of a removed id: no exception is expected
                                        gigaMap.set(targetId, new Document(
                                            "upd_" + targetId,
                                            randomVector(random, dimension)
                                        ));
                                    }
                                } else if (action < 40)
                                {
                                    // REMOVE
                                    final long targetId = random.nextInt(seedCount);
                                    synchronized (gigaMap)
                                    {
                                        // a removed id is a no-op: no exception is expected
                                        gigaMap.removeById(targetId);
                                    }
                                } else if (action < 50)
                                {
                                    // ADDALL
                                    final int batchSize = 2 + random.nextInt(2);
                                    final List<Document> batch = new ArrayList<>();
                                    for (int b = 0; b < batchSize; b++)
                                    {
                                        batch.add(new Document(
                                            "t" + threadId + "_batch_" + op + "_" + b,
                                            randomVector(random, dimension)
                                        ));
                                    }
                                    synchronized (gigaMap)
                                    {
                                        gigaMap.addAll(batch);
                                    }
                                } else
                                {
                                    // SEARCH
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

            assertTrue(doneLatch.await(60, TimeUnit.SECONDS),
                "Heavy concurrent load (embedded) should complete within timeout");

            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

            if (!errors.isEmpty())
            {
                final StringBuilder sb = new StringBuilder("Heavy eventual indexing stress test (embedded) failed:");
                for (final Throwable err : errors)
                {
                    sb.append("\n  - ").append(err.getClass().getSimpleName())
                        .append(": ").append(err.getMessage());
                }
                fail(sb.toString());
            }

            // Drain and verify final state
            defaultIndex.backgroundTaskManager.drainQueue();

            final VectorSearchResult<Document> finalResult = index.search(
                randomVector(new Random(999), dimension), 5
            );
            assertNotNull(finalResult);
        }
    }

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

        try (final VectorIndex<Document> index = vectorIndices.add(
            "embeddings", config, new ComputedDocumentVectorizer()
        ))
        {
            // Seed
            final Random seedRandom = new Random(42);
            for (int i = 0; i < seedCount; i++)
            {
                gigaMap.add(new Document("seed_" + i, randomVector(seedRandom, dimension)));
            }

            final VectorIndex.Default<Document> defaultIndex = (VectorIndex.Default<Document>) index;
            defaultIndex.backgroundTaskManager.drainQueue();

            final AtomicBoolean hasError = new AtomicBoolean(false);
            final List<Throwable> errors = java.util.Collections.synchronizedList(new ArrayList<>());
            final CountDownLatch startLatch = new CountDownLatch(1);
            final CountDownLatch doneLatch = new CountDownLatch(threadCount);

            final ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            for (int t = 0; t < threadCount; t++)
            {
                final int threadId = t;
                executor.submit(() ->
                {
                    try
                    {
                        startLatch.await();
                        final Random random = new Random(2000 + threadId);

                        for (int op = 0; op < opsPerThread && !hasError.get(); op++)
                        {
                            try
                            {
                                final int action = random.nextInt(100);

                                if (action < 25)
                                {
                                    // ADD
                                    synchronized (gigaMap)
                                    {
                                        gigaMap.add(new Document(
                                            "t" + threadId + "_" + op,
                                            randomVector(random, dimension)
                                        ));
                                    }
                                }
                                else if (action < 40)
                                {
                                    // UPDATE
                                    final long targetId = random.nextInt(seedCount);
                                    synchronized (gigaMap)
                                    {
                                        // set() refills the slot of a removed id: no exception is expected
                                        gigaMap.set(targetId, new Document(
                                            "upd_" + targetId,
                                            randomVector(random, dimension)
                                        ));
                                    }
                                } else if (action < 50)
                                {
                                    // REMOVE
                                    final long targetId = random.nextInt(seedCount);
                                    synchronized (gigaMap)
                                    {
                                        // a removed id is a no-op: no exception is expected
                                        gigaMap.removeById(targetId);
                                    }
                                } else
                                {
                                    // SEARCH
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

            assertTrue(doneLatch.await(60, TimeUnit.SECONDS),
                "Heavy concurrent load should complete within timeout");

            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

            if (!errors.isEmpty())
            {
                final StringBuilder sb = new StringBuilder("Heavy eventual indexing stress test failed:");
                for (final Throwable err : errors)
                {
                    sb.append("\n  - ").append(err.getClass().getSimpleName())
                        .append(": ").append(err.getMessage());
                }
                fail(sb.toString());
            }

            // Drain and verify final state
            defaultIndex.backgroundTaskManager.drainQueue();

            final VectorSearchResult<Document> finalResult = index.search(
                randomVector(new Random(999), dimension), 5
            );
            assertNotNull(finalResult);
        }
    }


    // ==================== Incremental Mode Regression ====================

    /**
     * Regression test for concurrent search vs. mutation in incremental on-disk mode.
     * <p>
     * Seeds entities, persists to disk (entering incremental mode), then hammers
     * the index with concurrent searches and updates/removes. This exercises the
     * segment accept bits while mutations release entities in the disk owners. An earlier
     * snapshot-based mask threw
     * {@code ArrayIndexOutOfBoundsException} when the set was modified between
     * its max-scan and mask-fill passes.
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void testIncrementalMode_ConcurrentSearchVsMutation(@TempDir final Path tempDir)
        throws Exception
    {
        final int dimension    = 64;
        final int seedCount    = 30;
        final int opsPerThread = 200;
        final int threadCount  = 4;
        final Path indexDir    = tempDir.resolve("incremental_regression");

        final GigaMap<Document> gigaMap = GigaMap.New();
        final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());

        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(dimension)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .maxDegree(16)
            .beamWidth(100)
            .onDisk(true)
            .indexDirectory(indexDir)
            .build();

        try (final VectorIndex<Document> index = vectorIndices.add(
            "embeddings", config, new ComputedDocumentVectorizer()
        ))
        {
            // Seed entities
            final Random seedRandom = new Random(42);
            for (int i = 0; i < seedCount; i++)
            {
                gigaMap.add(new Document("seed_" + i, randomVector(seedRandom, dimension)));
            }

            // Persist to disk — this enters incremental on-disk mode on reload
            index.persistToDisk();

            // Verify we are in incremental mode
            final VectorIndex.Default<Document> defaultIndex = (VectorIndex.Default<Document>) index;
            // The index should have re-entered incremental mode after persist

            // Shared state
            final AtomicLong nextEntityId = new AtomicLong(seedCount);
            final AtomicBoolean hasError = new AtomicBoolean(false);
            final List<Throwable> errors = java.util.Collections.synchronizedList(new ArrayList<>());
            final CountDownLatch startLatch = new CountDownLatch(1);
            final CountDownLatch doneLatch = new CountDownLatch(threadCount);

            final ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            for (int t = 0; t < threadCount; t++)
            {
                final int threadId = t;
                executor.submit(() ->
                {
                    try
                    {
                        startLatch.await();

                        final Random random = new Random(2000 + threadId);

                        for (int op = 0; op < opsPerThread && !hasError.get(); op++)
                        {
                            try
                            {
                                final int action = random.nextInt(100);

                                if (action < 15)
                                {
                                    // 15%: ADD — grows ordinal space during search
                                    final float[] vector = randomVector(random, dimension);
                                    synchronized (gigaMap)
                                    {
                                        gigaMap.add(new Document(
                                            "t" + threadId + "_" + op, vector
                                        ));
                                    }
                                }
                                else if (action < 35)
                                {
                                    // 20%: UPDATE - releases the disk owner
                                    final long targetId = random.nextInt(seedCount);
                                    final float[] vector = randomVector(random, dimension);
                                    synchronized (gigaMap)
                                    {
                                        // set() refills the slot of a removed id: no exception is expected
                                        gigaMap.set(targetId, new Document(
                                            "updated_" + targetId, vector
                                        ));
                                    }
                                }
                                else if (action < 50)
                                {
                                    // 15%: REMOVE - releases the disk owner
                                    final long targetId = random.nextInt(seedCount);
                                    synchronized (gigaMap)
                                    {
                                        // a removed id is a no-op: no exception is expected
                                        gigaMap.removeById(targetId);
                                    }
                                }
                                else
                                {
                                    // 50%: SEARCH — exercises createDiskAcceptBits()
                                    final float[] queryVector = randomVector(random, dimension);
                                    final VectorSearchResult<Document> result =
                                        index.search(queryVector, 5);
                                    assertNotNull(result);
                                }
                            }
                            catch (final Throwable e)
                            {
                                errors.add(e);
                                hasError.set(true);
                            }
                        }
                    }
                    catch (final InterruptedException e)
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

            assertTrue(doneLatch.await(60, TimeUnit.SECONDS),
                "Threads should complete within timeout");

            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

            // Report errors
            if (!errors.isEmpty())
            {
                final StringBuilder sb = new StringBuilder();
                sb.append("Incremental mode regression test failed");
                sb.append("\n").append(errors.size()).append(" error(s):");
                for (final Throwable err : errors)
                {
                    sb.append("\n  - ").append(err.getClass().getSimpleName())
                        .append(": ").append(err.getMessage());
                }
                fail(sb.toString());
            }

            // Final consistency check
            final VectorSearchResult<Document> finalResult = index.search(
                randomVector(new Random(999), dimension), 5
            );
            assertNotNull(finalResult);
        }
    }


    // ==================== Persist vs. Sustained Writes Regression ====================

    /**
     * Regression test for internal #142: a repeated persist racing a sustained write stream.
     * <p>
     * This is the shape the reported failure had - a bulk load updating the same few vector-carrying
     * entities over and over while background persistence ticks. Each persist defers the in-flight
     * builder ops and then drains them, and the drain used to run without any lock, so it raced the
     * application thread's own drain and died with
     * {@code IllegalStateException: Node N already exists}. Once that happened the op was lost, the
     * drain aborted mid-queue and every later persistence tick failed the same way.
     * <p>
     * The writers concentrate on a handful of ordinals on purpose: consecutive queue entries for the
     * same ordinal are what make two concurrent drains collide on it.
     */
    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void testPersistDuringSustainedWrites(@TempDir final Path tempDir) throws Exception
    {
        final int  dimension     = 64;
        final int  seedCount     = 40;
        final int  hotOrdinals   = 5;
        final int  persistRounds = 12;
        final int  writerThreads = 3;
        final Path indexDir      = tempDir.resolve("persist_vs_writes");

        final GigaMap<Document> gigaMap = GigaMap.New();
        final VectorIndices<Document> vectorIndices = gigaMap.index().register(VectorIndices.Category());

        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(dimension)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .maxDegree(16)
            .beamWidth(100)
            .onDisk(true)
            .indexDirectory(indexDir)
            .build();

        try(final VectorIndex<Document> index = vectorIndices.add(
            "embeddings", config, new ComputedDocumentVectorizer()
        ))
        {
            final Random seedRandom = new Random(42);
            for(int i = 0; i < seedCount; i++)
            {
                gigaMap.add(new Document("seed_" + i, randomVector(seedRandom, dimension)));
            }
            index.persistToDisk(); // enter incremental on-disk mode

            final AtomicBoolean    running = new AtomicBoolean(true);
            final List<Throwable>  errors  = java.util.Collections.synchronizedList(new ArrayList<>());
            final CountDownLatch   done    = new CountDownLatch(writerThreads);
            final ExecutorService  executor = Executors.newFixedThreadPool(writerThreads);

            for(int t = 0; t < writerThreads; t++)
            {
                final int threadId = t;
                executor.submit(() ->
                {
                    try
                    {
                        final Random random = new Random(3000 + threadId);
                        while(running.get())
                        {
                            final long targetId = random.nextInt(hotOrdinals);
                            synchronized(gigaMap)
                            {
                                gigaMap.set(targetId, new Document(
                                    "hot_" + targetId, randomVector(random, dimension)
                                ));
                            }
                        }
                    }
                    catch(final Throwable e)
                    {
                        errors.add(e);
                    }
                    finally
                    {
                        done.countDown();
                    }
                });
            }

            try
            {
                for(int round = 0; round < persistRounds; round++)
                {
                    index.persistToDisk();
                }
            }
            catch(final Throwable e)
            {
                errors.add(e);
            }
            finally
            {
                running.set(false);
            }

            // Shut down before asserting: a writer stuck here must not leak its pool threads into
            // the rest of the suite just because this assertion throws first.
            final boolean writersFinished = done.await(60, TimeUnit.SECONDS);
            executor.shutdownNow();
            assertTrue(writersFinished, "writer threads should finish");
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS), "executor should terminate");

            if(!errors.isEmpty())
            {
                final StringBuilder sb = new StringBuilder("Persist vs. sustained writes failed");
                sb.append("\n").append(errors.size()).append(" error(s):");
                for(final Throwable err : errors)
                {
                    sb.append("\n  - ").append(err.getClass().getSimpleName())
                        .append(": ").append(err.getMessage());
                }
                fail(sb.toString());
            }

            // The index must still answer, and every seed entity must still be reachable exactly once.
            index.optimize();
            final VectorSearchResult<Document> result = index.search(
                randomVector(new Random(999), dimension), 10
            );
            assertNotNull(result);
            assertEquals(
                (long)result.size(),
                result.stream().map(ScoredSearchResult.Entry::entityId).distinct().count(),
                "no entity may be indexed twice"
            );
        }
    }


    // ==================== Flushes and Consolidations under Load ====================

    /**
     * Mixed adds, updates, removes and searches on an on-disk index while explicit persists and
     * background persistence flush its in-memory graph again and again: a small flush threshold makes
     * many segments, and a cap of three makes the background merges combine them, and the persists
     * consolidate them where the merges fall behind, over and over. Afterwards
     * every entity is found by its own vector and no removed one is found at all, and a restart loads
     * what the shutdown persist flushed without rebuilding.
     */
    @Test
    @Timeout(value = 180, unit = TimeUnit.SECONDS)
    void testFlushesAndConsolidationsUnderConcurrentMutation(@TempDir final Path tempDir) throws Exception
    {
        final int  dimension    = 32;
        final int  seedCount    = 300;
        final int  opsPerThread = 600;
        final int  threadCount  = 4;
        final Path storageDir   = tempDir.resolve("storage");
        final Path indexDir     = tempDir.resolve("index");

        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(dimension)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .maxDegree(16)
            .beamWidth(100)
            .onDisk(true)
            .indexDirectory(indexDir)
            .persistenceIntervalMs(50)
            .minChangesBetweenPersists(1)
            .flushThreshold(200)
            .maxSegments(3)
            .build();

        // The expected live vector of every entity, changed together with the map under its monitor.
        final Map<Long, float[]> expected = new HashMap<>();

        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(GigaMap.New(), storageDir))
        {
            final GigaMap<Document>             gigaMap = rootOf(storage);
            final VectorIndex.Default<Document> index   = (VectorIndex.Default<Document>)gigaMap.index()
                .register(VectorIndices.Category())
                .add("embeddings", config, new ComputedDocumentVectorizer());

            final Random seedRandom = new Random(42);
            for(int i = 0; i < seedCount; i++)
            {
                final float[] vector = randomVector(seedRandom, dimension);
                expected.put(gigaMap.add(new Document("seed_" + i, vector)), vector);
            }
            index.persistToDisk();

            final List<Throwable> errors   = Collections.synchronizedList(new ArrayList<>());
            final AtomicBoolean   running  = new AtomicBoolean(true);
            final CountDownLatch  done     = new CountDownLatch(threadCount);
            final ExecutorService executor = Executors.newFixedThreadPool(threadCount + 1);

            for(int t = 0; t < threadCount; t++)
            {
                final int threadId = t;
                executor.submit(() ->
                {
                    try
                    {
                        final Random random = new Random(4000 + threadId);
                        for(int op = 0; op < opsPerThread && errors.isEmpty(); op++)
                        {
                            final int action = random.nextInt(100);
                            if(action < 50)
                            {
                                mutateRandomly(gigaMap, expected, random, dimension, action, threadId, op);
                            }
                            else
                            {
                                final VectorSearchResult<Document> result = index.search(
                                    randomVector(random, dimension), 10
                                );
                                assertEquals(
                                    (long)result.size(),
                                    result.stream().map(ScoredSearchResult.Entry::entityId).distinct().count(),
                                    "no entity may be found twice"
                                );
                            }
                        }
                    }
                    catch(final Throwable e)
                    {
                        errors.add(e);
                    }
                    finally
                    {
                        done.countDown();
                    }
                });
            }
            executor.submit(() ->
            {
                try
                {
                    while(running.get() && errors.isEmpty())
                    {
                        index.persistToDisk();
                        Thread.sleep(20L);
                    }
                }
                catch(final Throwable e)
                {
                    errors.add(e);
                }
            });

            final boolean finished = done.await(150, TimeUnit.SECONDS);
            running.set(false);
            executor.shutdown();
            assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS), "executor should terminate");
            assertTrue(finished, "mutating threads should finish");
            if(!errors.isEmpty())
            {
                final AssertionError failure = new AssertionError(errors.size() + " error(s), the first attached");
                failure.initCause(errors.get(0));
                throw failure;
            }

            assertTrue(index.flushCount() > 3, "the threshold and the persists must have flushed repeatedly");
            assertTrue(index.mergeCount() + index.consolidationCount() > 0,
                "the segments must have been merged or consolidated");
            assertTrue(index.sourceSet().segments().size() <= 3, "the segment cap must hold");
            assertFindsExactlyTheExpected(index, expected);

            storage.storeRoot();
            index.close();
        }

        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final VectorIndex.Default<Document> index = (VectorIndex.Default<Document>)rootOf(storage).index()
                .get(VectorIndices.Category())
                .get("embeddings");
            assertFindsExactlyTheExpected(index, expected);
            assertEquals(0, index.inMemoryGraphNodeCount(), "the restart must load the segments, not rebuild");
            index.close();
        }
    }

    /**
     * One add, update or remove for {@code action} below 50, applied to the map and to
     * {@code expected} together, under the map's monitor.
     */
    private static void mutateRandomly(
        final GigaMap<Document>  gigaMap  ,
        final Map<Long, float[]> expected ,
        final Random             random   ,
        final int                dimension,
        final int                action   ,
        final int                threadId ,
        final int                op
    )
    {
        final float[] vector = randomVector(random, dimension);
        synchronized(gigaMap)
        {
            if(action < 20 || expected.isEmpty())
            {
                expected.put(gigaMap.add(new Document("t" + threadId + "_" + op, vector)), vector);
                return;
            }
            final List<Long> ids    = new ArrayList<>(expected.keySet());
            final long       target = ids.get(random.nextInt(ids.size()));
            if(action < 40)
            {
                gigaMap.set(target, new Document("updated_" + target, vector));
                expected.put(target, vector);
            }
            else
            {
                gigaMap.removeById(target);
                expected.remove(target);
            }
        }
    }

    /**
     * Asserts that nearly every expected entity is the nearest hit for its own, latest vector, and that
     * no hit is an entity that is gone.
     */
    private static void assertFindsExactlyTheExpected(
        final VectorIndex<Document> index   ,
        final Map<Long, float[]>    expected
    )
    {
        int found = 0;
        for(final Map.Entry<Long, float[]> entry : expected.entrySet())
        {
            final VectorSearchResult<Document> result = index.search(entry.getValue(), 3);
            for(final ScoredSearchResult.Entry<Document> hit : result)
            {
                assertTrue(expected.containsKey(hit.entityId()), () -> "removed entity " + hit.entityId() + " found");
            }
            if(result.size() > 0 && result.iterator().next().entityId() == entry.getKey())
            {
                found++;
            }
        }
        final double recall = (double)found / expected.size();
        assertTrue(recall >= 0.98, "self-recall " + recall + " over " + expected.size() + " entities");
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
