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

import org.eclipse.serializer.exceptions.NumberRangeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link VectorIndexConfiguration} builder and factory methods.
 */
class VectorIndexConfigurationTest
{
    // ==================== Default Values Tests ====================

    @Test
    void testBuilderDefaults()
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
        assertFalse(config.onDisk());
        assertNull(config.indexDirectory());
        assertFalse(config.enablePqCompression());
        assertEquals(0, config.pqSubspaces());
        assertFalse(config.backgroundPersistence());
        assertEquals(0L, config.persistenceIntervalMs());
        assertTrue(config.persistOnShutdown());
        assertEquals(100, config.minChangesBetweenPersists());
        assertFalse(config.backgroundOptimization());
        assertEquals(0L, config.optimizationIntervalMs());
        assertEquals(1000, config.minChangesBetweenOptimizations());
        assertFalse(config.optimizeOnShutdown());
        assertFalse(config.parallelOnDiskWrite());
        assertFalse(config.eventualIndexing());
    }

    // ==================== Builder Validation Tests ====================

    @Test
    void testBuilderRequiresPositiveDimension()
    {
        assertThrows(NumberRangeException.class, () ->
            VectorIndexConfiguration.builder().dimension(0).build()
        );
        assertThrows(NumberRangeException.class, () ->
            VectorIndexConfiguration.builder().dimension(-1).build()
        );
    }

    @Test
    void testBuilderRequiresPositiveMaxDegree()
    {
        assertThrows(NumberRangeException.class, () ->
            VectorIndexConfiguration.builder().dimension(64).maxDegree(0).build()
        );
        assertThrows(NumberRangeException.class, () ->
            VectorIndexConfiguration.builder().dimension(64).maxDegree(-1).build()
        );
    }

    @Test
    void testBuilderRequiresPositiveBeamWidth()
    {
        assertThrows(NumberRangeException.class, () ->
            VectorIndexConfiguration.builder().dimension(64).beamWidth(0).build()
        );
        assertThrows(NumberRangeException.class, () ->
            VectorIndexConfiguration.builder().dimension(64).beamWidth(-1).build()
        );
    }

    @Test
    void testBuilderRequiresPositiveNeighborOverflow()
    {
        assertThrows(NumberRangeException.class, () ->
            VectorIndexConfiguration.builder().dimension(64).neighborOverflow(0f).build()
        );
        assertThrows(NumberRangeException.class, () ->
            VectorIndexConfiguration.builder().dimension(64).neighborOverflow(-1f).build()
        );
    }

    @Test
    void testBuilderRequiresPositiveAlpha()
    {
        assertThrows(NumberRangeException.class, () ->
            VectorIndexConfiguration.builder().dimension(64).alpha(0f).build()
        );
        assertThrows(NumberRangeException.class, () ->
            VectorIndexConfiguration.builder().dimension(64).alpha(-1f).build()
        );
    }

    @Test
    void testBuilderRequiresNonNegativePqSubspaces()
    {
        assertThrows(IllegalArgumentException.class, () ->
            VectorIndexConfiguration.builder().dimension(64).pqSubspaces(-1).build()
        );
    }

    @Test
    void testBuilderRequiresNonNegativePersistenceIntervalMs()
    {
        // 0 is valid (means disabled)
        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(64)
            .persistenceIntervalMs(0)
            .build();
        assertEquals(0L, config.persistenceIntervalMs());
        assertFalse(config.backgroundPersistence());

        assertThrows(IllegalArgumentException.class, () ->
            VectorIndexConfiguration.builder().dimension(64).persistenceIntervalMs(-1).build()
        );
    }

    @Test
    void testBuilderRequiresNonNegativeMinChangesBetweenPersists()
    {
        assertThrows(IllegalArgumentException.class, () ->
            VectorIndexConfiguration.builder().dimension(64).minChangesBetweenPersists(-1).build()
        );
    }

    @Test
    void testBuilderRequiresNonNegativeOptimizationIntervalMs()
    {
        // 0 is valid (means disabled)
        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(64)
            .optimizationIntervalMs(0)
            .build();
        assertEquals(0L, config.optimizationIntervalMs());
        assertFalse(config.backgroundOptimization());

        assertThrows(IllegalArgumentException.class, () ->
            VectorIndexConfiguration.builder().dimension(64).optimizationIntervalMs(-1).build()
        );
    }

    @Test
    void testBuilderRequiresNonNegativeMinChangesBetweenOptimizations()
    {
        assertThrows(IllegalArgumentException.class, () ->
            VectorIndexConfiguration.builder().dimension(64).minChangesBetweenOptimizations(-1).build()
        );
    }

    @Test
    void testBuilderRequiresNonNullSimilarityFunction()
    {
        assertThrows(NullPointerException.class, () ->
            VectorIndexConfiguration.builder().dimension(64).similarityFunction(null).build()
        );
    }

    // ==================== On-Disk Configuration Validation Tests ====================

    @Test
    void testOnDiskRequiresIndexDirectory()
    {
        assertThrows(IllegalStateException.class, () ->
            VectorIndexConfiguration.builder()
                .dimension(64)
                .onDisk(true)
                .build()
        );
    }

    @Test
    void testOnDiskWithIndexDirectorySucceeds(@TempDir final Path tempDir)
    {
        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(64)
            .onDisk(true)
            .indexDirectory(tempDir)
            .build();

        assertTrue(config.onDisk());
        assertEquals(tempDir, config.indexDirectory());
    }

    @Test
    void testCompressionRequiresOnDisk()
    {
        assertThrows(IllegalStateException.class, () ->
            VectorIndexConfiguration.builder()
                .dimension(64)
                .enablePqCompression(true)
                .build()
        );
    }

    @Test
    void testBackgroundPersistenceRequiresOnDisk()
    {
        assertThrows(IllegalStateException.class, () ->
            VectorIndexConfiguration.builder()
                .dimension(64)
                .persistenceIntervalMs(30_000)
                .build()
        );
    }

    @Test
    void testPqSubspacesMustDivideDimension(@TempDir final Path tempDir)
    {
        // 64 is not divisible by 17
        assertThrows(IllegalArgumentException.class, () ->
            VectorIndexConfiguration.builder()
                .dimension(64)
                .onDisk(true)
                .indexDirectory(tempDir)
                .enablePqCompression(true)
                .pqSubspaces(17)
                .build()
        );
    }

    @Test
    void testPqSubspacesDividingDimensionSucceeds(@TempDir final Path tempDir)
    {
        // 64 is divisible by 16
        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(64)
            .onDisk(true)
            .indexDirectory(tempDir)
            .enablePqCompression(true)
            .pqSubspaces(16)
            .build();

        assertEquals(16, config.pqSubspaces());
    }

    @Test
    void testPqSubspacesZeroMeansAuto(@TempDir final Path tempDir)
    {
        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(64)
            .onDisk(true)
            .indexDirectory(tempDir)
            .enablePqCompression(true)
            .pqSubspaces(0)
            .build();

        assertEquals(0, config.pqSubspaces());
    }

    // ==================== Parallel On-Disk Write Tests ====================

    @Test
    void testParallelOnDiskWriteDefaultFalse()
    {
        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(64)
            .build();

        assertFalse(config.parallelOnDiskWrite());
    }

    @Test
    void testParallelOnDiskWriteCanBeDisabled(@TempDir final Path tempDir)
    {
        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(64)
            .onDisk(true)
            .indexDirectory(tempDir)
            .parallelOnDiskWrite(false)
            .build();

        assertFalse(config.parallelOnDiskWrite());
    }

    @Test
    void testParallelVsNonParallelShareSameDefaults(@TempDir final Path tempDir)
    {
        final VectorIndexConfiguration parallel = VectorIndexConfiguration.builder()
            .dimension(768)
            .onDisk(true)
            .indexDirectory(tempDir)
            .parallelOnDiskWrite(true)
            .build();

        final VectorIndexConfiguration sequential = VectorIndexConfiguration.builder()
            .dimension(768)
            .onDisk(true)
            .indexDirectory(tempDir)
            .parallelOnDiskWrite(false)
            .build();

        assertTrue(parallel.parallelOnDiskWrite());
        assertFalse(sequential.parallelOnDiskWrite());

        // All other parameters remain identical
        assertEquals(parallel.dimension(), sequential.dimension());
        assertEquals(parallel.similarityFunction(), sequential.similarityFunction());
        assertEquals(parallel.maxDegree(), sequential.maxDegree());
        assertEquals(parallel.beamWidth(), sequential.beamWidth());
        assertEquals(parallel.neighborOverflow(), sequential.neighborOverflow());
        assertEquals(parallel.alpha(), sequential.alpha());
        assertEquals(parallel.onDisk(), sequential.onDisk());
        assertEquals(parallel.indexDirectory(), sequential.indexDirectory());
        assertEquals(parallel.enablePqCompression(), sequential.enablePqCompression());
        assertEquals(parallel.pqSubspaces(), sequential.pqSubspaces());
        assertEquals(parallel.persistenceIntervalMs(), sequential.persistenceIntervalMs());
        assertEquals(parallel.persistOnShutdown(), sequential.persistOnShutdown());
        assertEquals(parallel.minChangesBetweenPersists(), sequential.minChangesBetweenPersists());
        assertEquals(parallel.optimizationIntervalMs(), sequential.optimizationIntervalMs());
        assertEquals(parallel.minChangesBetweenOptimizations(), sequential.minChangesBetweenOptimizations());
        assertEquals(parallel.optimizeOnShutdown(), sequential.optimizeOnShutdown());
    }

    @Test
    void testParallelVsNonParallelWithCompression(@TempDir final Path tempDir)
    {
        final VectorIndexConfiguration parallel = VectorIndexConfiguration.builder()
            .dimension(768)
            .onDisk(true)
            .indexDirectory(tempDir)
            .enablePqCompression(true)
            .pqSubspaces(48)
            .parallelOnDiskWrite(true)
            .build();

        final VectorIndexConfiguration sequential = VectorIndexConfiguration.builder()
            .dimension(768)
            .onDisk(true)
            .indexDirectory(tempDir)
            .enablePqCompression(true)
            .pqSubspaces(48)
            .parallelOnDiskWrite(false)
            .build();

        assertTrue(parallel.parallelOnDiskWrite());
        assertFalse(sequential.parallelOnDiskWrite());

        // Compression settings are identical regardless of parallel mode
        assertEquals(parallel.enablePqCompression(), sequential.enablePqCompression());
        assertEquals(parallel.pqSubspaces(), sequential.pqSubspaces());
        assertEquals(parallel.maxDegree(), sequential.maxDegree());
    }

    @Test
    void testFactoryMethodsDefaultToSequential(@TempDir final Path tempDir)
    {
        final Path indexDir = tempDir.resolve("vectors");

        final VectorIndexConfiguration medium = VectorIndexConfiguration.forMediumDataset(768, indexDir);
        assertFalse(medium.parallelOnDiskWrite());

        final VectorIndexConfiguration large = VectorIndexConfiguration.forLargeDataset(768, indexDir);
        assertFalse(large.parallelOnDiskWrite());

        final VectorIndexConfiguration highPrecision = VectorIndexConfiguration.forHighPrecision(768, indexDir);
        assertFalse(highPrecision.parallelOnDiskWrite());
    }

    @Test
    void testBuilderForLargeDatasetCanDisableParallel(@TempDir final Path tempDir)
    {
        final Path indexDir = tempDir.resolve("vectors");
        final VectorIndexConfiguration config = VectorIndexConfiguration.builderForLargeDataset(768, indexDir)
            .parallelOnDiskWrite(false)
            .enablePqCompression(true)
            .build();

        assertTrue(config.onDisk());
        assertTrue(config.enablePqCompression());
        assertFalse(config.parallelOnDiskWrite());
    }

    // ==================== Similarity Function Tests ====================

    @Test
    void testCosineSimilarityFunction()
    {
        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(64)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .build();

        assertEquals(VectorSimilarityFunction.COSINE, config.similarityFunction());
    }

    @Test
    void testDotProductSimilarityFunction()
    {
        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(64)
            .similarityFunction(VectorSimilarityFunction.DOT_PRODUCT)
            .build();

        assertEquals(VectorSimilarityFunction.DOT_PRODUCT, config.similarityFunction());
    }

    @Test
    void testEuclideanSimilarityFunction()
    {
        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(64)
            .similarityFunction(VectorSimilarityFunction.EUCLIDEAN)
            .build();

        assertEquals(VectorSimilarityFunction.EUCLIDEAN, config.similarityFunction());
    }

    // ==================== Small Dataset Factory Method Tests ====================

    @Test
    void testForSmallDataset()
    {
        final VectorIndexConfiguration config = VectorIndexConfiguration.forSmallDataset(768);

        assertEquals(768, config.dimension());
        assertEquals(VectorSimilarityFunction.COSINE, config.similarityFunction());
        assertEquals(12, config.maxDegree());
        assertEquals(75, config.beamWidth());
        assertFalse(config.onDisk());
    }

    @Test
    void testForSmallDatasetWithSimilarityFunction()
    {
        final VectorIndexConfiguration config = VectorIndexConfiguration.forSmallDataset(
            384,
            VectorSimilarityFunction.DOT_PRODUCT
        );

        assertEquals(384, config.dimension());
        assertEquals(VectorSimilarityFunction.DOT_PRODUCT, config.similarityFunction());
        assertEquals(12, config.maxDegree());
        assertEquals(75, config.beamWidth());
        assertFalse(config.onDisk());
    }

    @Test
    void testForSmallDatasetWithEuclidean()
    {
        final VectorIndexConfiguration config = VectorIndexConfiguration.forSmallDataset(
            256,
            VectorSimilarityFunction.EUCLIDEAN
        );

        assertEquals(256, config.dimension());
        assertEquals(VectorSimilarityFunction.EUCLIDEAN, config.similarityFunction());
    }

    @Test
    void testBuilderForSmallDataset()
    {
        final VectorIndexConfiguration config = VectorIndexConfiguration.builderForSmallDataset(512)
            .similarityFunction(VectorSimilarityFunction.EUCLIDEAN)
            .neighborOverflow(1.5f)
            .build();

        assertEquals(512, config.dimension());
        assertEquals(VectorSimilarityFunction.EUCLIDEAN, config.similarityFunction());
        assertEquals(12, config.maxDegree());
        assertEquals(75, config.beamWidth());
        assertEquals(1.5f, config.neighborOverflow());
        assertFalse(config.onDisk());
    }

    @Test
    void testBuilderForSmallDatasetCanOverrideMaxDegree()
    {
        final VectorIndexConfiguration config = VectorIndexConfiguration.builderForSmallDataset(512)
            .maxDegree(8)
            .build();

        assertEquals(8, config.maxDegree());
    }

    @Test
    void testBuilderForSmallDatasetCanOverrideBeamWidth()
    {
        final VectorIndexConfiguration config = VectorIndexConfiguration.builderForSmallDataset(512)
            .beamWidth(50)
            .build();

        assertEquals(50, config.beamWidth());
    }

    // ==================== Medium Dataset Factory Method Tests ====================

    @Test
    void testForMediumDatasetInMemory()
    {
        final VectorIndexConfiguration config = VectorIndexConfiguration.forMediumDataset(1024);

        assertEquals(1024, config.dimension());
        assertEquals(VectorSimilarityFunction.COSINE, config.similarityFunction());
        assertEquals(24, config.maxDegree());
        assertEquals(150, config.beamWidth());
        assertFalse(config.onDisk());
    }

    @Test
    void testForMediumDatasetOnDisk(@TempDir final Path tempDir)
    {
        final Path indexDir = tempDir.resolve("vectors");
        final VectorIndexConfiguration config = VectorIndexConfiguration.forMediumDataset(1024, indexDir);

        assertEquals(1024, config.dimension());
        assertEquals(VectorSimilarityFunction.COSINE, config.similarityFunction());
        assertEquals(24, config.maxDegree());
        assertEquals(150, config.beamWidth());
        assertTrue(config.onDisk());
        assertEquals(indexDir, config.indexDirectory());
        assertTrue(config.backgroundPersistence());
    }

    @Test
    void testBuilderForMediumDataset()
    {
        final VectorIndexConfiguration config = VectorIndexConfiguration.builderForMediumDataset(768)
            .similarityFunction(VectorSimilarityFunction.DOT_PRODUCT)
            .alpha(1.3f)
            .build();

        assertEquals(768, config.dimension());
        assertEquals(VectorSimilarityFunction.DOT_PRODUCT, config.similarityFunction());
        assertEquals(24, config.maxDegree());
        assertEquals(150, config.beamWidth());
        assertEquals(1.3f, config.alpha());
        assertFalse(config.onDisk());
    }

    @Test
    void testBuilderForMediumDatasetCanEnableOnDisk(@TempDir final Path tempDir)
    {
        final VectorIndexConfiguration config = VectorIndexConfiguration.builderForMediumDataset(768)
            .onDisk(true)
            .indexDirectory(tempDir)
            .build();

        assertTrue(config.onDisk());
        assertEquals(tempDir, config.indexDirectory());
    }

    // ==================== Large Dataset Factory Method Tests ====================

    @Test
    void testForLargeDatasetWithCompression(@TempDir final Path tempDir)
    {
        final Path indexDir = tempDir.resolve("vectors");
        final VectorIndexConfiguration config = VectorIndexConfiguration.forLargeDataset(1536, indexDir);

        assertEquals(1536, config.dimension());
        assertEquals(VectorSimilarityFunction.COSINE, config.similarityFunction());
        assertEquals(32, config.maxDegree());
        assertEquals(300, config.beamWidth());
        assertTrue(config.onDisk());
        assertEquals(indexDir, config.indexDirectory());
        assertTrue(config.enablePqCompression());
        assertTrue(config.backgroundPersistence());
        assertTrue(config.backgroundOptimization());
    }

    @Test
    void testForLargeDatasetWithoutCompression(@TempDir final Path tempDir)
    {
        final Path indexDir = tempDir.resolve("vectors");
        final VectorIndexConfiguration config = VectorIndexConfiguration.forLargeDataset(1536, indexDir, false);

        assertEquals(1536, config.dimension());
        assertEquals(32, config.maxDegree());
        assertEquals(300, config.beamWidth());
        assertTrue(config.onDisk());
        assertEquals(indexDir, config.indexDirectory());
        assertFalse(config.enablePqCompression());
        assertTrue(config.backgroundPersistence());
        assertTrue(config.backgroundOptimization());
    }

    @Test
    void testForLargeDatasetExplicitCompressionEnabled(@TempDir final Path tempDir)
    {
        final Path indexDir = tempDir.resolve("vectors");
        final VectorIndexConfiguration config = VectorIndexConfiguration.forLargeDataset(1536, indexDir, true);

        assertTrue(config.enablePqCompression());
    }

    @Test
    void testBuilderForLargeDataset(@TempDir final Path tempDir)
    {
        final Path indexDir = tempDir.resolve("vectors");
        final VectorIndexConfiguration config = VectorIndexConfiguration.builderForLargeDataset(3072, indexDir)
            .similarityFunction(VectorSimilarityFunction.DOT_PRODUCT)
            .enablePqCompression(false)
            .build();

        assertEquals(3072, config.dimension());
        assertEquals(VectorSimilarityFunction.DOT_PRODUCT, config.similarityFunction());
        assertEquals(32, config.maxDegree());
        assertEquals(300, config.beamWidth());
        assertTrue(config.onDisk());
        assertEquals(indexDir, config.indexDirectory());
        assertFalse(config.enablePqCompression());
        assertTrue(config.backgroundPersistence());
        assertTrue(config.backgroundOptimization());
    }

    @Test
    void testBuilderForLargeDatasetCanDisableBackgroundTasks(@TempDir final Path tempDir)
    {
        final Path indexDir = tempDir.resolve("vectors");
        final VectorIndexConfiguration config = VectorIndexConfiguration.builderForLargeDataset(1536, indexDir)
            .persistenceIntervalMs(0)
            .optimizationIntervalMs(0)
            .build();

        assertFalse(config.backgroundPersistence());
        assertFalse(config.backgroundOptimization());
    }

    // ==================== High Precision Factory Method Tests ====================

    @Test
    void testForHighPrecisionInMemory()
    {
        final VectorIndexConfiguration config = VectorIndexConfiguration.forHighPrecision(768);

        assertEquals(768, config.dimension());
        assertEquals(VectorSimilarityFunction.COSINE, config.similarityFunction());
        assertEquals(56, config.maxDegree());
        assertEquals(450, config.beamWidth());
        assertFalse(config.onDisk());
        assertFalse(config.enablePqCompression());
    }

    @Test
    void testForHighPrecisionOnDisk(@TempDir final Path tempDir)
    {
        final Path indexDir = tempDir.resolve("vectors");
        final VectorIndexConfiguration config = VectorIndexConfiguration.forHighPrecision(768, indexDir);

        assertEquals(768, config.dimension());
        assertEquals(VectorSimilarityFunction.COSINE, config.similarityFunction());
        assertEquals(56, config.maxDegree());
        assertEquals(450, config.beamWidth());
        assertTrue(config.onDisk());
        assertEquals(indexDir, config.indexDirectory());
        assertFalse(config.enablePqCompression());
        assertTrue(config.backgroundPersistence());
    }

    @Test
    void testBuilderForHighPrecision()
    {
        final VectorIndexConfiguration config = VectorIndexConfiguration.builderForHighPrecision(1024)
            .similarityFunction(VectorSimilarityFunction.EUCLIDEAN)
            .build();

        assertEquals(1024, config.dimension());
        assertEquals(VectorSimilarityFunction.EUCLIDEAN, config.similarityFunction());
        assertEquals(56, config.maxDegree());
        assertEquals(450, config.beamWidth());
        assertFalse(config.enablePqCompression());
    }

    @Test
    void testBuilderForHighPrecisionCompressionExplicitlyDisabled()
    {
        // Verify that high precision builder sets enablePqCompression to false
        final VectorIndexConfiguration config = VectorIndexConfiguration.builderForHighPrecision(768)
            .build();

        assertFalse(config.enablePqCompression());
    }

    // ==================== Full Configuration Tests ====================

    @Test
    void testFullOnDiskConfiguration(@TempDir final Path tempDir)
    {
        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(768)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .maxDegree(32)
            .beamWidth(200)
            .neighborOverflow(1.3f)
            .alpha(1.2f)
            .onDisk(true)
            .indexDirectory(tempDir)
            .enablePqCompression(true)
            .pqSubspaces(48)
            .persistenceIntervalMs(15_000)
            .persistOnShutdown(true)
            .minChangesBetweenPersists(50)
            .optimizationIntervalMs(120_000)
            .minChangesBetweenOptimizations(500)
            .optimizeOnShutdown(true)
            .parallelOnDiskWrite(false)
            .build();

        assertEquals(768, config.dimension());
        assertEquals(VectorSimilarityFunction.COSINE, config.similarityFunction());
        assertEquals(32, config.maxDegree());
        assertEquals(200, config.beamWidth());
        assertEquals(1.3f, config.neighborOverflow());
        assertEquals(1.2f, config.alpha());
        assertTrue(config.onDisk());
        assertEquals(tempDir, config.indexDirectory());
        assertTrue(config.enablePqCompression());
        assertEquals(48, config.pqSubspaces());
        assertTrue(config.backgroundPersistence());
        assertEquals(15_000L, config.persistenceIntervalMs());
        assertTrue(config.persistOnShutdown());
        assertEquals(50, config.minChangesBetweenPersists());
        assertTrue(config.backgroundOptimization());
        assertEquals(120_000L, config.optimizationIntervalMs());
        assertEquals(500, config.minChangesBetweenOptimizations());
        assertTrue(config.optimizeOnShutdown());
        assertFalse(config.parallelOnDiskWrite());
    }

    @Test
    void testMinimalInMemoryConfiguration()
    {
        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(3)
            .build();

        assertEquals(3, config.dimension());
        assertFalse(config.onDisk());
        assertNull(config.indexDirectory());
    }

    // ==================== Edge Cases Tests ====================

    @Test
    void testMinimumValidDimension()
    {
        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(1)
            .build();

        assertEquals(1, config.dimension());
    }

    @Test
    void testLargeDimension()
    {
        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(4096)
            .build();

        assertEquals(4096, config.dimension());
    }

    @Test
    void testMinimumValidMaxDegree()
    {
        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(64)
            .maxDegree(1)
            .build();

        assertEquals(1, config.maxDegree());
    }

    @Test
    void testLargeMaxDegree()
    {
        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(64)
            .maxDegree(128)
            .build();

        assertEquals(128, config.maxDegree());
    }

    @Test
    void testMinimumValidBeamWidth()
    {
        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(64)
            .beamWidth(1)
            .build();

        assertEquals(1, config.beamWidth());
    }

    @Test
    void testLargeBeamWidth()
    {
        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(64)
            .beamWidth(1000)
            .build();

        assertEquals(1000, config.beamWidth());
    }

    @Test
    void testZeroMinChangesBetweenPersists()
    {
        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(64)
            .minChangesBetweenPersists(0)
            .build();

        assertEquals(0, config.minChangesBetweenPersists());
    }

    @Test
    void testZeroMinChangesBetweenOptimizations()
    {
        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(64)
            .minChangesBetweenOptimizations(0)
            .build();

        assertEquals(0, config.minChangesBetweenOptimizations());
    }

    // ==================== Common Embedding Dimensions Tests ====================

    @Test
    void testOpenAIAda002Dimension()
    {
        final VectorIndexConfiguration config = VectorIndexConfiguration.forMediumDataset(1536);
        assertEquals(1536, config.dimension());
    }

    @Test
    void testOpenAI3LargeDimension()
    {
        final VectorIndexConfiguration config = VectorIndexConfiguration.forMediumDataset(3072);
        assertEquals(3072, config.dimension());
    }

    @Test
    void testCohereV3Dimension()
    {
        final VectorIndexConfiguration config = VectorIndexConfiguration.forMediumDataset(1024);
        assertEquals(1024, config.dimension());
    }

    @Test
    void testMiniLMDimension()
    {
        final VectorIndexConfiguration config = VectorIndexConfiguration.forSmallDataset(384);
        assertEquals(384, config.dimension());
    }

    @Test
    void testBertBaseDimension()
    {
        final VectorIndexConfiguration config = VectorIndexConfiguration.forMediumDataset(768);
        assertEquals(768, config.dimension());
    }

    // ==================== Builder Method Chaining Tests ====================

    @Test
    void testBuilderMethodChainingReturnsBuilder()
    {
        final VectorIndexConfiguration.Builder builder = VectorIndexConfiguration.builder();

        assertSame(builder, builder.dimension(64));
        assertSame(builder, builder.similarityFunction(VectorSimilarityFunction.COSINE));
        assertSame(builder, builder.maxDegree(16));
        assertSame(builder, builder.beamWidth(100));
        assertSame(builder, builder.neighborOverflow(1.2f));
        assertSame(builder, builder.alpha(1.2f));
        assertSame(builder, builder.onDisk(false));
        assertSame(builder, builder.indexDirectory(null));
        assertSame(builder, builder.enablePqCompression(false));
        assertSame(builder, builder.pqSubspaces(0));
        assertSame(builder, builder.persistenceIntervalMs(30_000));
        assertSame(builder, builder.persistOnShutdown(true));
        assertSame(builder, builder.minChangesBetweenPersists(100));
        assertSame(builder, builder.optimizationIntervalMs(60_000));
        assertSame(builder, builder.minChangesBetweenOptimizations(1000));
        assertSame(builder, builder.optimizeOnShutdown(false));
        assertSame(builder, builder.parallelOnDiskWrite(true));
    }

    // ==================== Factory Methods Comparison Tests ====================

    @Test
    void testSmallVsMediumParameters()
    {
        final VectorIndexConfiguration small = VectorIndexConfiguration.forSmallDataset(768);
        final VectorIndexConfiguration medium = VectorIndexConfiguration.forMediumDataset(768);

        assertTrue(medium.maxDegree() > small.maxDegree(),
            "Medium should have higher maxDegree than small");
        assertTrue(medium.beamWidth() > small.beamWidth(),
            "Medium should have higher beamWidth than small");
    }

    @Test
    void testMediumVsLargeParameters(@TempDir final Path tempDir)
    {
        final VectorIndexConfiguration medium = VectorIndexConfiguration.forMediumDataset(768);
        final VectorIndexConfiguration large = VectorIndexConfiguration.forLargeDataset(768, tempDir, false);

        assertTrue(large.maxDegree() > medium.maxDegree(),
            "Large should have higher maxDegree than medium");
        assertTrue(large.beamWidth() > medium.beamWidth(),
            "Large should have higher beamWidth than medium");
    }

    @Test
    void testLargeVsHighPrecisionParameters(@TempDir final Path tempDir)
    {
        final VectorIndexConfiguration large = VectorIndexConfiguration.forLargeDataset(768, tempDir, false);
        final VectorIndexConfiguration highPrecision = VectorIndexConfiguration.forHighPrecision(768);

        assertTrue(highPrecision.maxDegree() > large.maxDegree(),
            "High precision should have higher maxDegree than large");
        assertTrue(highPrecision.beamWidth() > large.beamWidth(),
            "High precision should have higher beamWidth than large");
    }

    @Test
    void testHighPrecisionDisablesCompression()
    {
        final VectorIndexConfiguration config = VectorIndexConfiguration.forHighPrecision(768);
        assertFalse(config.enablePqCompression(),
            "High precision should have compression disabled");
    }

    @Test
    void testLargeDatasetEnablesCompression(@TempDir final Path tempDir)
    {
        final VectorIndexConfiguration config = VectorIndexConfiguration.forLargeDataset(768, tempDir);
        assertTrue(config.enablePqCompression(),
            "Large dataset should have compression enabled by default");
    }

    // ==================== Eventual Indexing Tests ====================

    @Test
    void testEventualIndexingDefaultFalse()
    {
        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(64)
            .build();

        assertFalse(config.eventualIndexing());
    }

    @Test
    void testEventualIndexingCanBeEnabled()
    {
        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(64)
            .eventualIndexing(true)
            .build();

        assertTrue(config.eventualIndexing());
    }

    @Test
    void testEventualIndexingCanBeDisabledExplicitly()
    {
        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(64)
            .eventualIndexing(false)
            .build();

        assertFalse(config.eventualIndexing());
    }

    @Test
    void testEventualIndexingWithOnDiskConfig(@TempDir final Path tempDir)
    {
        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(64)
            .onDisk(true)
            .indexDirectory(tempDir)
            .eventualIndexing(true)
            .build();

        assertTrue(config.eventualIndexing());
        assertTrue(config.onDisk());
    }

    @Test
    void testFactoryMethodsDefaultEventualIndexingFalse(@TempDir final Path tempDir)
    {
        assertFalse(VectorIndexConfiguration.forSmallDataset(64).eventualIndexing());
        assertFalse(VectorIndexConfiguration.forMediumDataset(64).eventualIndexing());
        assertFalse(VectorIndexConfiguration.forLargeDataset(64, tempDir).eventualIndexing());
        assertFalse(VectorIndexConfiguration.forHighPrecision(64).eventualIndexing());
    }

    /**
     * Test on-disk configuration builder.
     */
    @Test
    void testOnDiskConfigurationBuilder(@TempDir final Path tempDir)
    {

        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
                .dimension(128)
                .similarityFunction(VectorSimilarityFunction.COSINE)
                .onDisk(true)
                .indexDirectory(tempDir)
                .build();

        assertTrue(config.onDisk());
        assertEquals(tempDir, config.indexDirectory());
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

        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
                .dimension(128)
                .similarityFunction(VectorSimilarityFunction.COSINE)
                .maxDegree(16) // Will be overridden to 32 for FusedPQ
                .onDisk(true)
                .indexDirectory(tempDir)
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
        // Try to set maxDegree to 64 with compression enabled
        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
                .dimension(128)
                .maxDegree(64)
                .onDisk(true)
                .indexDirectory(tempDir)
                .enablePqCompression(true)
                .build();

        // Should be overridden to 32
        assertEquals(32, config.maxDegree(), "FusedPQ should enforce maxDegree=32");
    }

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
        assertEquals(0, config.persistenceIntervalMs());
        assertTrue(config.persistOnShutdown());
        assertEquals(100, config.minChangesBetweenPersists());
    }

    /**
     * Test validation: persistenceIntervalMs must be non-negative.
     */
    @Test
    void testPersistenceIntervalMsMustBeNonNegative(@TempDir final Path tempDir)
    {
        // 0 is valid (means disabled)
        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
                .dimension(128)
                .onDisk(true)
                .indexDirectory(tempDir)
                .persistenceIntervalMs(0)
                .build();
        assertEquals(0, config.persistenceIntervalMs());
        assertFalse(config.backgroundPersistence());

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
     * Test validation: optimizationIntervalMs must be non-negative.
     */
    @Test
    void testOptimizationIntervalMsMustBeNonNegative(@TempDir final Path tempDir)
    {
        // 0 is valid (means disabled)
        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
                .dimension(128)
                .onDisk(true)
                .indexDirectory(tempDir)
                .optimizationIntervalMs(0)
                .build();
        assertEquals(0, config.optimizationIntervalMs());
        assertFalse(config.backgroundOptimization());

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
        assertEquals(0, config.optimizationIntervalMs());
        assertEquals(1000, config.minChangesBetweenOptimizations());
        assertFalse(config.optimizeOnShutdown());
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

}
