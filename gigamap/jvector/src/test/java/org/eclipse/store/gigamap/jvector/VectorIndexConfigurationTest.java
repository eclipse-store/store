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
        assertEquals(30_000L, config.shutdownPersistTimeoutMillis());
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
    void testBuilderShutdownPersistTimeoutMillis()
    {
        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(64)
            .shutdownPersistTimeoutMillis(5_000)
            .build();
        assertEquals(5_000L, config.shutdownPersistTimeoutMillis());

        assertThrows(NumberRangeException.class, () ->
            VectorIndexConfiguration.builder().dimension(64).shutdownPersistTimeoutMillis(0).build()
        );
        assertThrows(NumberRangeException.class, () ->
            VectorIndexConfiguration.builder().dimension(64).shutdownPersistTimeoutMillis(-1).build()
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
     * Enabling PQ must not change any other configured value.
     */
    @Test
    void testOnDiskConfigurationWithCompression(@TempDir final Path tempDir)
    {

        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
                .dimension(128)
                .similarityFunction(VectorSimilarityFunction.COSINE)
                .maxDegree(16)
                .onDisk(true)
                .indexDirectory(tempDir)
                .enablePqCompression(true)
                .pqSubspaces(32)
                .build();

        assertTrue(config.onDisk());
        assertTrue(config.enablePqCompression());
        assertEquals(32, config.pqSubspaces());
        assertEquals(16, config.maxDegree(), "enabling PQ must not override the configured maxDegree");
    }

    /**
     * JVector 4's FusedPQ imposes no constraint on maxDegree - its only precondition is a
     * 256-cluster codebook, and it records the degree in the graph header so the reader sizes the
     * fused block from the file. The builder used to silently rewrite maxDegree to 32 here, a
     * leftover from JVector 3's FusedADC; since nothing else about the flag worked, doubling the
     * graph out-degree was its only observable effect.
     */
    @Test
    void testPqCompressionDoesNotConstrainMaxDegree(@TempDir final Path tempDir)
    {
        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
                .dimension(128)
                .maxDegree(64)
                .onDisk(true)
                .indexDirectory(tempDir)
                .enablePqCompression(true)
                .build();

        assertEquals(64, config.maxDegree(), "PQ must not constrain maxDegree");
    }

    /**
     * The override also used to write back to the builder field, so it leaked into every later
     * build() from the same builder - even one with compression switched off again.
     */
    @Test
    void testBuildDoesNotMutateBuilderMaxDegree(@TempDir final Path tempDir)
    {
        final VectorIndexConfiguration.Builder builder = VectorIndexConfiguration.builder()
                .dimension(128)
                .maxDegree(16)
                .onDisk(true)
                .indexDirectory(tempDir)
                .enablePqCompression(true);

        assertEquals(16, builder.build().maxDegree());

        builder.enablePqCompression(false);

        assertEquals(16, builder.build().maxDegree(),
            "build() must not mutate the builder's maxDegree");
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

    // ==================== Vector Storage / Approximate Scoring Tests ====================

    @Test
    void testStorageAndScoringDefaults()
    {
        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(64)
            .build();

        assertEquals(VectorStorage.INLINE, config.vectorStorage());
        assertEquals(ApproximateScoring.NONE, config.approximateScoring());
        assertEquals(1, config.nvqSubvectors());
        assertFalse(config.enablePqCompression());
    }

    @Test
    void testEnablePqCompressionIsADelegateForApproximateScoring(@TempDir final Path tempDir)
    {
        final VectorIndexConfiguration enabled = VectorIndexConfiguration.builder()
            .dimension(64)
            .onDisk(true)
            .indexDirectory(tempDir)
            .enablePqCompression(true)
            .build();
        assertEquals(ApproximateScoring.FUSED_PQ, enabled.approximateScoring());

        final VectorIndexConfiguration viaEnum = VectorIndexConfiguration.builder()
            .dimension(64)
            .onDisk(true)
            .indexDirectory(tempDir)
            .approximateScoring(ApproximateScoring.FUSED_PQ)
            .build();
        assertTrue(viaEnum.enablePqCompression());
    }

    /**
     * The deprecated setter writes the very same field as the enum setter, so the two are not
     * competing settings needing a precedence rule - whichever was called last simply wins. Pinned
     * in both orders, because a delegate implemented as a second field would pass one and fail the
     * other.
     */
    @Test
    void testDeprecatedAndEnumSettersAreLastWriterWins(@TempDir final Path tempDir)
    {
        final VectorIndexConfiguration deprecatedLast = VectorIndexConfiguration.builder()
            .dimension(64)
            .onDisk(true)
            .indexDirectory(tempDir)
            .approximateScoring(ApproximateScoring.FUSED_PQ)
            .enablePqCompression(false)
            .build();
        assertEquals(ApproximateScoring.NONE, deprecatedLast.approximateScoring());
        assertFalse(deprecatedLast.enablePqCompression());

        final VectorIndexConfiguration enumLast = VectorIndexConfiguration.builder()
            .dimension(64)
            .onDisk(true)
            .indexDirectory(tempDir)
            .enablePqCompression(true)
            .approximateScoring(ApproximateScoring.NONE)
            .build();
        assertEquals(ApproximateScoring.NONE, enumLast.approximateScoring());
        assertFalse(enumLast.enablePqCompression());
    }

    @Test
    void testNvqStorageRequiresOnDisk()
    {
        final VectorIndexConfiguration.Builder builder = VectorIndexConfiguration.builder()
            .dimension(64)
            .vectorStorage(VectorStorage.NVQ);

        final IllegalStateException e = assertThrows(IllegalStateException.class, builder::build);
        assertTrue(e.getMessage().contains("onDisk"), e.getMessage());
    }

    @Test
    void testApproximateScoringRequiresOnDisk()
    {
        final VectorIndexConfiguration.Builder builder = VectorIndexConfiguration.builder()
            .dimension(64)
            .approximateScoring(ApproximateScoring.FUSED_PQ);

        final IllegalStateException e = assertThrows(IllegalStateException.class, builder::build);
        assertTrue(e.getMessage().contains("onDisk"), e.getMessage());
    }

    @Test
    void testBuilderRequiresNonNegativeNvqSubvectors()
    {
        assertThrows(
            IllegalArgumentException.class,
            () -> VectorIndexConfiguration.builder().nvqSubvectors(-1)
        );
    }

    @Test
    void testBuilderRejectsNullStorageAndScoring()
    {
        assertThrows(
            NullPointerException.class,
            () -> VectorIndexConfiguration.builder().vectorStorage(null)
        );
        assertThrows(
            NullPointerException.class,
            () -> VectorIndexConfiguration.builder().approximateScoring(null)
        );
    }

    /**
     * Unlike pqSubspaces there is no divisibility rule - JVector distributes the remainder across
     * the subvectors - but there must be at least one dimension per subvector.
     */
    @Test
    void testNvqSubvectorsMustNotExceedDimension(@TempDir final Path tempDir)
    {
        final VectorIndexConfiguration.Builder builder = VectorIndexConfiguration.builder()
            .dimension(16)
            .onDisk(true)
            .indexDirectory(tempDir)
            .vectorStorage(VectorStorage.NVQ)
            .nvqSubvectors(17);

        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    void testNvqSubvectorsNotDividingDimensionIsAllowed(@TempDir final Path tempDir)
    {
        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(14)
            .onDisk(true)
            .indexDirectory(tempDir)
            .vectorStorage(VectorStorage.NVQ)
            .nvqSubvectors(3)
            .build();

        assertEquals(3, config.nvqSubvectors());
    }

    @Test
    void testNvqSubvectorsZeroMeansAuto()
    {
        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(64)
            .nvqSubvectors(0)
            .build();

        assertEquals(1, config.nvqSubvectors());
    }

    /**
     * A subvector count high enough that NVQ costs more than full precision is a tuning mistake,
     * not an illegal state: the index still works, it just pays quantization recall for nothing.
     * It must warn rather than throw.
     */
    @Test
    void testNvqThatSavesNothingIsAllowedRatherThanRejected(@TempDir final Path tempDir)
    {
        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(64)
            .onDisk(true)
            .indexDirectory(tempDir)
            .vectorStorage(VectorStorage.NVQ)
            .nvqSubvectors(64)
            .build();

        assertEquals(VectorStorage.NVQ, config.vectorStorage());
        assertEquals(64, config.nvqSubvectors());
    }

    @Test
    void testForCompactLargeDatasetIsActuallyTheSmallestOnDisk(@TempDir final Path tempDir)
    {
        final Path indexDir = tempDir.resolve("vectors");
        final VectorIndexConfiguration config = VectorIndexConfiguration.forCompactLargeDataset(768, indexDir);

        assertEquals(768, config.dimension());
        assertEquals(32, config.maxDegree());
        assertEquals(300, config.beamWidth());
        assertTrue(config.onDisk());
        assertEquals(indexDir, config.indexDirectory());
        assertEquals(VectorStorage.NVQ, config.vectorStorage());

        // No fused codes, and that is the point rather than an omission. They are duplicated
        // maxDegree times per node, so at 768/32 they cost 6144 bytes against the 800 the quantized
        // vector occupies - a preset that carried them would be larger than the plain uncompressed
        // one it claims to improve on, which is exactly the kind of claim this module has had to
        // correct before.
        assertEquals(ApproximateScoring.NONE, config.approximateScoring());
        assertFalse(config.enablePqCompression());

        // Not incidental: the quantized format costs about 3x the persist wall time written
        // sequentially and about 1.2x under the parallel writer, so a preset that picks NVQ and
        // leaves this off hands out the cost without the mitigation.
        assertTrue(config.parallelOnDiskWrite(),
            "the compact preset must pair NVQ with the parallel writer");

        assertTrue(config.backgroundPersistence());
        assertTrue(config.backgroundOptimization());

        // Pinned against the node layout: the compact preset must come out below both the
        // large-dataset preset and the plain uncompressed configuration.
        final int compact = bytesPerNode(768, 32, VectorStorage.NVQ, 1);
        final int plain   = bytesPerNode(768, 32, VectorStorage.INLINE, 1);
        final int fused   = plain + 192 * 32;
        assertTrue(compact < plain / 3,
            "the compact preset must be far smaller than plain inline storage: " + compact + " vs " + plain);
        assertTrue(compact < fused / 7,
            "and far smaller than forLargeDataset: " + compact + " vs " + fused);
    }

    /**
     * The existing preset must not have silently changed on-disk format when vectorStorage was
     * introduced: an application that upgrades and keeps calling forLargeDataset gets exactly the
     * graph it got before.
     */
    @Test
    void testForLargeDatasetKeepsInlineStorage(@TempDir final Path tempDir)
    {
        final VectorIndexConfiguration config =
            VectorIndexConfiguration.forLargeDataset(768, tempDir.resolve("vectors"));

        assertEquals(VectorStorage.INLINE, config.vectorStorage());
        assertEquals(ApproximateScoring.FUSED_PQ, config.approximateScoring());
    }

    @Test
    void testForHighPrecisionDisablesBothDimensions()
    {
        final VectorIndexConfiguration config = VectorIndexConfiguration.forHighPrecision(64);

        assertEquals(VectorStorage.INLINE, config.vectorStorage());
        assertEquals(ApproximateScoring.NONE, config.approximateScoring());
    }

    /**
     * The on-disk node layout: node id, the inline feature block, and the neighbour list of
     * {@code maxDegree + 1} ints.
     */
    private static int bytesPerNode(
        final int           dimension    ,
        final int           maxDegree    ,
        final VectorStorage storage      ,
        final int           nvqSubvectors
    )
    {
        final int featureBlock = storage == VectorStorage.NVQ
            ? 4 + dimension + 28 * nvqSubvectors
            : dimension * Float.BYTES;
        return Integer.BYTES + featureBlock + Integer.BYTES * (maxDegree + 1);
    }

    @Test
    void testStorageAndScoringSettersAreFluent()
    {
        final VectorIndexConfiguration.Builder builder = VectorIndexConfiguration.builder();

        assertSame(builder, builder.vectorStorage(VectorStorage.INLINE));
        assertSame(builder, builder.approximateScoring(ApproximateScoring.NONE));
        assertSame(builder, builder.nvqSubvectors(2));
    }

    // ==================== Stable Code Tests ====================

    /**
     * The codes are written into the index metadata file, so they are a file-format contract: an
     * existing constant's code may never change, and a new one must be appended with a fresh code.
     * Pinned literally rather than derived, so that renumbering a constant fails here instead of
     * silently making every previously written index unreadable - or, worse, misread.
     */
    @Test
    void testStableCodesArePinned()
    {
        assertEquals(1, VectorStorage.INLINE.code());
        assertEquals(2, VectorStorage.NVQ.code());

        assertEquals(1, ApproximateScoring.NONE.code());
        assertEquals(2, ApproximateScoring.FUSED_PQ.code());
    }

    @Test
    void testCodesRoundTrip()
    {
        for(final VectorStorage storage : VectorStorage.values())
        {
            assertSame(storage, VectorStorage.fromCode(storage.code()));
        }
        for(final ApproximateScoring scoring : ApproximateScoring.values())
        {
            assertSame(scoring, ApproximateScoring.fromCode(scoring.code()));
        }
    }

    /**
     * An unknown code is what a metadata file written by a newer version carrying a constant this
     * build does not know looks like. It must resolve to null so the caller can treat it as a
     * mismatch and rebuild, rather than throwing or - far worse - mapping it onto a constant that
     * means something else.
     */
    @Test
    void testUnknownCodeResolvesToNull()
    {
        assertNull(VectorStorage.fromCode(0));
        assertNull(VectorStorage.fromCode(99));
        assertNull(ApproximateScoring.fromCode(0));
        assertNull(ApproximateScoring.fromCode(3));  // reserved for PQ_IN_MEMORY
        assertNull(ApproximateScoring.fromCode(99));
    }

    // ==================== Schema Evolution Tests ====================

    /**
     * A {@link VectorIndexConfiguration} round-trips through Eclipse Store as part of the index, and
     * Eclipse Store fills a field added by schema evolution with the zero value. So a configuration
     * persisted by a build that predates these enums loads with both of them null, with only the
     * legacy boolean carrying the setting.
     * <p>
     * The accessors must derive the truth from that boolean rather than reporting the enum
     * defaults. Getting this wrong would silently switch PQ off for every existing on-disk index on
     * upgrade - and, because the metadata records the configured scoring mode, would also invalidate
     * its graph and force a full rebuild.
     * <p>
     * The builder cannot produce this shape, since it derives the boolean from the enum, so the
     * fields are forced by reflection. That is the point: this is the only way the shape occurs, and
     * it occurs on every upgrade.
     */
    @Test
    void testLegacyConfigWithoutModesDerivesFromTheBoolean(@TempDir final Path tempDir) throws Exception
    {
        final VectorIndexConfiguration pqEnabled = VectorIndexConfiguration.builder()
            .dimension(64)
            .onDisk(true)
            .indexDirectory(tempDir)
            .approximateScoring(ApproximateScoring.FUSED_PQ)
            .build();
        zeroFillEvolvedFields(pqEnabled);

        assertEquals(VectorStorage.INLINE, pqEnabled.vectorStorage(),
            "a configuration predating vectorStorage described an INLINE graph");
        assertEquals(ApproximateScoring.FUSED_PQ, pqEnabled.approximateScoring(),
            "PQ must survive the upgrade, derived from the legacy boolean");
        assertTrue(pqEnabled.enablePqCompression());
        assertEquals(1, pqEnabled.nvqSubvectors());

        final VectorIndexConfiguration pqDisabled = VectorIndexConfiguration.builder()
            .dimension(64)
            .build();
        zeroFillEvolvedFields(pqDisabled);

        assertEquals(VectorStorage.INLINE, pqDisabled.vectorStorage());
        assertEquals(ApproximateScoring.NONE, pqDisabled.approximateScoring());
        assertFalse(pqDisabled.enablePqCompression());
    }

    // ==================== Binary Compatibility Tests ====================

    /**
     * The three format accessors are {@code default} methods, so an implementation written before
     * they existed - one that implements only the accessors this interface had in the previous
     * release - still compiles and links. {@link LegacyConfiguration} is exactly such an
     * implementation: it overrides none of the three, and this test would not compile if any of
     * them were abstract.
     * <p>
     * The scoring default is the interesting one. It derives from the deprecated flag rather than
     * returning a constant, so a legacy implementation that enables PQ keeps answering correctly
     * instead of reporting no approximate scoring at all.
     */
    @Test
    void testLegacyImplementationInheritsFormatAccessors()
    {
        final VectorIndexConfiguration pqOff = new LegacyConfiguration(false);

        assertEquals(VectorStorage.INLINE   , pqOff.vectorStorage()     );
        assertEquals(ApproximateScoring.NONE, pqOff.approximateScoring());
        assertEquals(1                      , pqOff.nvqSubvectors()     );

        final VectorIndexConfiguration pqOn = new LegacyConfiguration(true);

        assertEquals(VectorStorage.INLINE       , pqOn.vectorStorage()     );
        assertEquals(ApproximateScoring.FUSED_PQ, pqOn.approximateScoring());
    }

    /**
     * The new {@code Builder} mutators are {@code default} too, for the same linkage reason, but
     * they throw instead of silently ignoring the call: a builder that cannot carry the setting
     * must not report success and then hand back a configuration that says something else.
     */
    @Test
    void testLegacyBuilderRejectsTheNewMutators()
    {
        final VectorIndexConfiguration.Builder legacyBuilder = new LegacyBuilder();

        assertThrows(UnsupportedOperationException.class, () -> legacyBuilder.vectorStorage(VectorStorage.NVQ));
        assertThrows(UnsupportedOperationException.class, () -> legacyBuilder.approximateScoring(ApproximateScoring.NONE));
        assertThrows(UnsupportedOperationException.class, () -> legacyBuilder.nvqSubvectors(1));
    }

    /**
     * A third-party implementation of {@link VectorIndexConfiguration} as it stood before the
     * format accessors were added. The returned values are irrelevant; what matters is that this
     * class declares no {@code vectorStorage}, {@code approximateScoring} or {@code nvqSubvectors}.
     */
    private static final class LegacyConfiguration implements VectorIndexConfiguration
    {
        private final boolean pqCompression;

        LegacyConfiguration(final boolean pqCompression)
        {
            super();
            this.pqCompression = pqCompression;
        }

        @Override
        public int dimension()
        {
            return 64;
        }

        @Override
        public VectorSimilarityFunction similarityFunction()
        {
            return VectorSimilarityFunction.COSINE;
        }

        @Override
        public int maxDegree()
        {
            return 16;
        }

        @Override
        public int beamWidth()
        {
            return 100;
        }

        @Override
        public int minSearchBeamWidth()
        {
            return 50;
        }

        @Override
        public float neighborOverflow()
        {
            return 1.2f;
        }

        @Override
        public float alpha()
        {
            return 1.2f;
        }

        @Override
        public boolean onDisk()
        {
            return true;
        }

        @Override
        public Path indexDirectory()
        {
            return Path.of("legacy");
        }

        @Override
        @SuppressWarnings("deprecation")
        public boolean enablePqCompression()
        {
            return this.pqCompression;
        }

        @Override
        public int pqSubspaces()
        {
            return 0;
        }

        @Override
        public long persistenceIntervalMs()
        {
            return 0L;
        }

        @Override
        public boolean persistOnShutdown()
        {
            return true;
        }

        @Override
        public long shutdownPersistTimeoutMillis()
        {
            return 30_000L;
        }

        @Override
        public int minChangesBetweenPersists()
        {
            return 0;
        }

        @Override
        public long optimizationIntervalMs()
        {
            return 0L;
        }

        @Override
        public int minChangesBetweenOptimizations()
        {
            return 0;
        }

        @Override
        public boolean optimizeOnShutdown()
        {
            return false;
        }

        @Override
        public boolean eventualIndexing()
        {
            return false;
        }

        @Override
        public boolean parallelOnDiskWrite()
        {
            return false;
        }
    }

    /**
     * A third-party {@link VectorIndexConfiguration.Builder} as it stood before the format
     * mutators were added. Every method that existed then throws, since this test exercises only
     * the three inherited defaults.
     */
    private static final class LegacyBuilder implements VectorIndexConfiguration.Builder
    {
        LegacyBuilder()
        {
            super();
        }

        @Override
        public VectorIndexConfiguration.Builder dimension(final int dimension)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public VectorIndexConfiguration.Builder similarityFunction(final VectorSimilarityFunction similarityFunction)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public VectorIndexConfiguration.Builder maxDegree(final int maxDegree)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public VectorIndexConfiguration.Builder beamWidth(final int beamWidth)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public VectorIndexConfiguration.Builder minSearchBeamWidth(final int minSearchBeamWidth)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public VectorIndexConfiguration.Builder neighborOverflow(final float neighborOverflow)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public VectorIndexConfiguration.Builder alpha(final float alpha)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public VectorIndexConfiguration.Builder onDisk(final boolean onDisk)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public VectorIndexConfiguration.Builder indexDirectory(final Path indexDirectory)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        @SuppressWarnings("deprecation")
        public VectorIndexConfiguration.Builder enablePqCompression(final boolean enablePqCompression)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public VectorIndexConfiguration.Builder pqSubspaces(final int pqSubspaces)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public VectorIndexConfiguration.Builder persistenceIntervalMs(final long persistenceIntervalMs)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public VectorIndexConfiguration.Builder persistOnShutdown(final boolean persistOnShutdown)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public VectorIndexConfiguration.Builder shutdownPersistTimeoutMillis(final long shutdownPersistTimeoutMillis)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public VectorIndexConfiguration.Builder minChangesBetweenPersists(final int minChangesBetweenPersists)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public VectorIndexConfiguration.Builder optimizationIntervalMs(final long optimizationIntervalMs)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public VectorIndexConfiguration.Builder minChangesBetweenOptimizations(final int minChangesBetweenOptimizations)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public VectorIndexConfiguration.Builder optimizeOnShutdown(final boolean optimizeOnShutdown)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public VectorIndexConfiguration.Builder eventualIndexing(final boolean eventualIndexing)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public VectorIndexConfiguration.Builder parallelOnDiskWrite(final boolean parallelOnDiskWrite)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public VectorIndexConfiguration build()
        {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * Forces the shape Eclipse Store produces for a configuration stored before the storage and
     * scoring fields existed: both references null and the int zero, with the legacy
     * {@code enablePqCompression} boolean left exactly as it was persisted.
     */
    private static void zeroFillEvolvedFields(final VectorIndexConfiguration config) throws Exception
    {
        setField(config, "vectorStorage"     , null);
        setField(config, "approximateScoring", null);
        setField(config, "nvqSubvectors"     , 0   );
    }

    private static void setField(final Object target, final String name, final Object value) throws Exception
    {
        final java.lang.reflect.Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

}
