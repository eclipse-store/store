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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Opt-in heap check of the vector file: during an ingest the heap must not grow by a vector per node. Before the
 * vector file every vector lived on the heap, in the computed-mode vector store and in the eventual-indexing
 * queue; now it lives in the mapping, outside the heap.
 * <p>
 * Run like the ingest benchmark, with {@code -Declipsestore.benchmark=true -Dtest=VectorIndexHeapHistogramTest}. It
 * prints the class histogram's {@code float[]} bytes and the process's resident and mapped memory, since mapped
 * pages count against a container's memory even though they are not heap.
 */
@Tag("benchmark")
@EnabledIfSystemProperty(named = "eclipsestore.benchmark", matches = "true")
class VectorIndexHeapHistogramTest
{
    private static final int     DIMENSION = Integer.getInteger("benchmark.dimension", 768);
    private static final int     NODES     = Integer.getInteger("benchmark.nodes"    , 20_000);
    private static final Pattern FLOATS    = Pattern.compile(
        "^\\s*\\d+:\\s+(\\d+)\\s+(\\d+)\\s+\\[F\\b", Pattern.MULTILINE
    );

    static final class Doc
    {
        final float[] vector;

        Doc(final float[] vector)
        {
            this.vector = vector;
        }
    }

    /**
     * Computed mode, so the entity's own array is not what is measured: the vectorizer hands out a fresh one, which
     * the index must not keep.
     */
    static final class CopyingVectorizer extends Vectorizer<Doc>
    {
        @Override
        public float[] vectorize(final Doc entity)
        {
            return entity.vector.clone();
        }
    }

    @Test
    void ingest_heapHistogram_noFloatArrayGrowthPerNode() throws Exception
    {
        final GigaMap<Doc> map   = GigaMap.New();
        final Random       random = new Random(42);
        final VectorIndex<Doc> index = map.index().register(VectorIndices.Category()).add(
            "embeddings",
            VectorIndexConfiguration.builder().dimension(DIMENSION).build(),
            new CopyingVectorizer()
        );
        try
        {
            ingest(map, random, NODES / 2);
            final long half = floatArrayBytes();
            ingest(map, random, NODES / 2);
            final long full = floatArrayBytes();

            // The entities themselves keep their arrays, one vector per node: that is the client's data.
            final long entityBytes = (long)(NODES / 2) * (16L + 4L * DIMENSION);
            final long indexGrowth = full - half - entityBytes;
            final long perNode     = indexGrowth / (NODES / 2);
            System.out.printf(
                "float[] bytes: %,d at %,d nodes, %,d at %,d nodes; index growth %,d bytes per node"
                    + " (a vector is %,d)%n",
                half, NODES / 2, full, NODES, perNode, 4L * DIMENSION
            );
            System.out.println(memoryStatus());

            assertTrue(perNode < DIMENSION,
                "the index keeps " + perNode + " bytes of float[] per node, a quarter vector or more");
        }
        finally
        {
            index.close();
        }
    }

    private static void ingest(
        final GigaMap<Doc> map   ,
        final Random       random,
        final int          count
    )
    {
        for(int i = 0; i < count; i++)
        {
            final float[] vector = new float[DIMENSION];
            for(int d = 0; d < DIMENSION; d++)
            {
                vector[d] = random.nextFloat() - 0.5f;
            }
            map.add(new Doc(vector));
        }
    }

    /**
     * The live bytes of {@code float[]}, from a class histogram, which runs a full collection first.
     */
    private static long floatArrayBytes() throws Exception
    {
        final MBeanServer server    = ManagementFactory.getPlatformMBeanServer();
        final String      histogram = (String)server.invoke(
            new ObjectName("com.sun.management:type=DiagnosticCommand"),
            "gcClassHistogram",
            new Object[]{new String[0]},
            new String[]{String[].class.getName()}
        );
        final Matcher matcher = FLOATS.matcher(histogram);
        return matcher.find() ? Long.parseLong(matcher.group(2)) : 0L;
    }

    private static String memoryStatus()
    {
        try
        {
            return Files.readAllLines(Path.of("/proc/self/status")).stream()
                .filter(line -> line.startsWith("VmRSS") || line.startsWith("RssFile") || line.startsWith("RssAnon"))
                .reduce((a, b) -> a + ", " + b)
                .orElse("no memory status")
                .replaceAll("\\s+", " ");
        }
        catch(final IOException e)
        {
            return "no memory status: " + e;
        }
    }
}
