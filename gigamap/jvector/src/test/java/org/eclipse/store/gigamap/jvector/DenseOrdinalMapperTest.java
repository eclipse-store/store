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


import io.github.jbellis.jvector.graph.GraphIndexBuilder;
import io.github.jbellis.jvector.graph.OnHeapGraphIndex;
import io.github.jbellis.jvector.graph.RandomAccessVectorValues;
import io.github.jbellis.jvector.graph.disk.AbstractGraphIndexWriter;
import io.github.jbellis.jvector.graph.disk.OnDiskGraphIndex;
import io.github.jbellis.jvector.graph.disk.OnDiskGraphIndexWriter;
import io.github.jbellis.jvector.graph.disk.OnDiskParallelGraphIndexWriter;
import io.github.jbellis.jvector.graph.disk.OrdinalMapper;
import io.github.jbellis.jvector.graph.disk.feature.Feature;
import io.github.jbellis.jvector.graph.disk.feature.FeatureId;
import io.github.jbellis.jvector.graph.disk.feature.InlineVectors;
import io.github.jbellis.jvector.graph.disk.feature.NVQ;
import io.github.jbellis.jvector.quantization.NVQuantization;
import io.github.jbellis.jvector.vector.VectorSimilarityFunction;
import io.github.jbellis.jvector.vector.VectorizationProvider;
import io.github.jbellis.jvector.vector.types.VectorFloat;
import io.github.jbellis.jvector.vector.types.VectorTypeSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Holds {@link DenseOrdinalMapper} to jVector's own {@code sequentialRenumbering} map: the same
 * answers for every ordinal, and byte-identical files from every writer the persist uses.
 */
class DenseOrdinalMapperTest
{
    private static final VectorTypeSupport VECTOR_TYPE_SUPPORT =
        VectorizationProvider.getInstance().getVectorTypeSupport();

    private static final int NODE_COUNT = 300;
    private static final int DIMENSION  = 16;

    // A leading, a middle and the three trailing ordinals, so the graph has a hole at each end.
    // The trailing ones are what tells the highest present ordinal apart from getIdUpperBound() - 1.
    private static final int[] REMOVED = {0, 150, NODE_COUNT - 3, NODE_COUNT - 2, NODE_COUNT - 1};

    @TempDir
    Path directory;

    private RandomAccessVectorValues ravv ;
    private OnHeapGraphIndex         index;

    @BeforeEach
    void setUp()
    {
        final Random               random  = new Random(42L);
        final List<VectorFloat<?>> vectors = new ArrayList<>(NODE_COUNT);
        for(int i = 0; i < NODE_COUNT; i++)
        {
            final float[] vector = new float[DIMENSION];
            for(int d = 0; d < DIMENSION; d++)
            {
                vector[d] = random.nextFloat();
            }
            vectors.add(VECTOR_TYPE_SUPPORT.createFloatVector(vector));
        }
        this.ravv = new ListRandomAccessVectorValues(vectors, DIMENSION);

        final GraphIndexBuilder builder = new GraphIndexBuilder(
            this.ravv,
            VectorSimilarityFunction.EUCLIDEAN,
            16,
            100,
            1.2f,
            1.2f,
            true
        );
        builder.build(this.ravv);
        for(final int ordinal : REMOVED)
        {
            builder.markNodeDeleted(ordinal);
        }
        builder.cleanup();
        this.index = (OnHeapGraphIndex)builder.getGraph();
    }

    @Test
    void mapping_graphWithHoles_matchesSequentialRenumbering()
    {
        final OrdinalMapper reference = new OrdinalMapper.MapMapper(this.sequentialMap());

        final DenseOrdinalMapper mapper = DenseOrdinalMapper.of(this.index);

        assertEquals(reference.maxOrdinal(), mapper.maxOrdinal());
        assertEquals(NODE_COUNT - REMOVED.length - 1, mapper.maxOrdinal(),
            "dense: one local ordinal per present node and none for a hole");
        for(int local = -1; local <= this.index.getIdUpperBound(); local++)
        {
            assertEquals(reference.newToOld(local), mapper.newToOld(local), "newToOld(" + local + ")");
        }
        for(int ordinal = 0; ordinal < this.index.getIdUpperBound(); ordinal++)
        {
            if(this.index.containsNode(ordinal))
            {
                assertEquals(reference.oldToNew(ordinal), mapper.oldToNew(ordinal), "oldToNew(" + ordinal + ")");
            }
        }
    }

    @Test
    void localToGlobal_graphWithHoles_isAscendingPresentOrdinals()
    {
        final int[] localToGlobal = DenseOrdinalMapper.of(this.index).localToGlobal();

        assertEquals(NODE_COUNT - REMOVED.length, localToGlobal.length);
        assertEquals(1, localToGlobal[0], "ordinal 0 is removed, so local 0 is ordinal 1");
        assertEquals(NODE_COUNT - 4, localToGlobal[localToGlobal.length - 1]);
        for(final int removed : REMOVED)
        {
            assertTrue(Arrays.binarySearch(localToGlobal, removed) < 0, "removed ordinal " + removed);
        }
    }

    @Test
    void oldToNew_removedOrdinal_throws()
    {
        final DenseOrdinalMapper mapper = DenseOrdinalMapper.of(this.index);

        assertThrows(IllegalStateException.class, () -> mapper.oldToNew(150));
        assertThrows(IllegalStateException.class, () -> mapper.oldToNew(0));
        assertThrows(IllegalStateException.class, () -> mapper.oldToNew(NODE_COUNT - 1));
    }

    @Test
    void constructor_unsortedOrDuplicateOrdinals_throws()
    {
        assertThrows(IllegalArgumentException.class, () -> new DenseOrdinalMapper(new int[]{3, 2}));
        assertThrows(IllegalArgumentException.class, () -> new DenseOrdinalMapper(new int[]{2, 2}));
        assertThrows(IllegalArgumentException.class, () -> new DenseOrdinalMapper(new int[]{-1, 2}));
    }

    @Test
    void maxOrdinal_emptyGraph_isMinusOne()
    {
        final GraphIndexBuilder builder = new GraphIndexBuilder(
            this.ravv,
            VectorSimilarityFunction.EUCLIDEAN,
            16,
            100,
            1.2f,
            1.2f,
            true
        );

        final DenseOrdinalMapper mapper = DenseOrdinalMapper.of((OnHeapGraphIndex)builder.getGraph());

        assertEquals(-1, mapper.maxOrdinal());
    }

    @Test
    void writePlainIndex_graphWithHoles_isByteIdenticalToMapBasedWrite() throws IOException
    {
        final Path expected = this.directory.resolve("expected.graph");
        final Path actual   = this.directory.resolve("actual.graph");

        OnDiskGraphIndex.write(this.index, this.ravv, this.sequentialMap(), expected);
        DiskIndexManager.Default.writePlainIndex(this.index, this.ravv, DenseOrdinalMapper.of(this.index), actual);

        assertFilesIdentical(expected, actual);
    }

    @Test
    void sequentialWriter_nvqGraphWithHoles_isByteIdenticalToMapBasedWrite() throws IOException
    {
        final NVQuantization nvq      = NVQuantization.compute(this.ravv, 2);
        final Path           expected = this.directory.resolve("expected.graph");
        final Path           actual   = this.directory.resolve("actual.graph");

        try(final OnDiskGraphIndexWriter writer = new OnDiskGraphIndexWriter.Builder(this.index, expected)
            .withMap(this.sequentialMap())
            .with(new NVQ(nvq))
            .build())
        {
            writer.write(this.nvqSuppliers(nvq));
        }
        try(final OnDiskGraphIndexWriter writer = new OnDiskGraphIndexWriter.Builder(this.index, actual)
            .withMapper(DenseOrdinalMapper.of(this.index))
            .with(new NVQ(nvq))
            .build())
        {
            writer.write(this.nvqSuppliers(nvq));
        }

        assertFilesIdentical(expected, actual);
    }

    @Test
    void parallelWriter_graphWithHoles_isByteIdenticalToMapBasedWrite() throws IOException
    {
        final Path expected = this.directory.resolve("expected.graph");
        final Path actual   = this.directory.resolve("actual.graph");

        this.writeParallel(expected, new OrdinalMapper.MapMapper(this.sequentialMap()));
        this.writeParallel(actual, DenseOrdinalMapper.of(this.index));

        assertFilesIdentical(expected, actual);
    }

    private void writeParallel(
        final Path          path  ,
        final OrdinalMapper mapper
    ) throws IOException
    {
        final OnDiskParallelGraphIndexWriter.Builder builder = new OnDiskParallelGraphIndexWriter.Builder(this.index, path);
        builder.withParallelDirectBuffers(true);
        builder.withMapper(mapper);
        builder.with(new InlineVectors(DIMENSION));
        try(final OnDiskParallelGraphIndexWriter writer = builder.build())
        {
            writer.write(Feature.singleStateFactory(
                FeatureId.INLINE_VECTORS,
                nodeId -> new InlineVectors.State(this.ravv.getVector(nodeId))
            ));
        }
    }

    private Map<FeatureId, IntFunction<Feature.State>> nvqSuppliers(final NVQuantization nvq)
    {
        final Map<FeatureId, IntFunction<Feature.State>> suppliers = new EnumMap<>(FeatureId.class);
        suppliers.put(FeatureId.NVQ_VECTORS, nodeId -> new NVQ.State(nvq.encode(this.ravv.getVector(nodeId))));
        return suppliers;
    }

    /**
     * jVector's default mapping, which the dense mapper reproduces without a boxed entry per node.
     */
    private Map<Integer, Integer> sequentialMap()
    {
        return AbstractGraphIndexWriter.sequentialRenumbering(this.index);
    }

    private static void assertFilesIdentical(
        final Path expected,
        final Path actual
    ) throws IOException
    {
        assertTrue(Files.size(expected) > 0, "nothing was written");
        assertEquals(-1L, Files.mismatch(expected, actual),
            "files differ (sizes " + Files.size(expected) + " / " + Files.size(actual) + ")");
    }

}
