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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A vector holding {@code NaN} or an infinity is rejected rather than indexed.
 * <p>
 * The cost of letting one through is not proportionate to how easy it is to produce. A quantizer
 * fits its parameters to the data, so one non-finite component reaches the quantizer and from there
 * every vector the graph stores; traversal then scores {@code NaN} against everything and the index
 * returns nothing at all. It does not recover when the entity is removed, because the quantizer is
 * adopted from the loaded graph rather than retrained. And {@code NaN} is ordinary: normalising a
 * zero vector produces it.
 */
@Tag("slow")
class VectorIndexNonFiniteVectorTest
{
    static final int DIM = 64;

    static final class Doc
    {
        final String  content;
        final float[] embedding;

        Doc(final String content, final float[] embedding)
        {
            this.content   = content;
            this.embedding = embedding;
        }
    }

    static final class Vz extends Vectorizer<Doc>
    {
        @Override
        public float[] vectorize(final Doc entity)
        {
            return entity.embedding;
        }
    }

    private static float[] unit(final Random random)
    {
        final float[] v = new float[DIM];
        double norm = 0;
        for(int i = 0; i < DIM; i++)
        {
            v[i] = (float)random.nextGaussian();
            norm += v[i] * v[i];
        }
        final float inv = (float)(1.0 / Math.sqrt(norm));
        for(int i = 0; i < DIM; i++)
        {
            v[i] *= inv;
        }
        return v;
    }

    private static float[] filled(final float value)
    {
        final float[] v = new float[DIM];
        Arrays.fill(v, value);
        return v;
    }

    private static VectorIndexConfiguration nvqOnDisk(final Path indexDir)
    {
        return VectorIndexConfiguration.builder()
            .dimension(DIM)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .maxDegree(16)
            .beamWidth(100)
            .onDisk(true)
            .indexDirectory(indexDir)
            .vectorStorage(VectorStorage.NVQ)
            .approximateScoring(ApproximateScoring.NONE)
            .build();
    }

    private static int hits(final VectorIndex<Doc> index, final float[] query)
    {
        int n = 0;
        for(final ScoredSearchResult.Entry<Doc> entry : index.search(query, 10))
        {
            n++;
        }
        return n;
    }

    /**
     * The configuration that was measured going permanently blank, driven end to end.
     * <p>
     * {@code NVQ} with {@link ApproximateScoring#NONE} traverses on the graph's own quantized
     * vectors, so it is the combination where a poisoned quantizer has nothing else to fall back
     * on. Before the fix this went from ten hits to zero at the persist that trains the quantizer,
     * and stayed at zero after the entity was removed and the index persisted again.
     *
     * @param dir the temporary index directory
     */
    @Test
    @Timeout(value = 300, unit = TimeUnit.SECONDS)
    void oneNonFiniteEmbeddingCannotBlankAQuantizedIndex(@TempDir final Path dir)
    {
        final Random        random = new Random(7);
        final List<float[]> good   = new ArrayList<>();
        for(int i = 0; i < 300; i++)
        {
            good.add(unit(random));
        }

        final GigaMap<Doc> map = GigaMap.New();
        try(final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
            .add("embeddings", nvqOnDisk(dir.resolve("index")), new Vz()))
        {
            for(int i = 0; i < good.size(); i++)
            {
                map.add(new Doc("d" + i, good.get(i)));
            }
            assertEquals(10, hits(index, good.get(0)), "the index must work before the bad vector");

            assertThrows(IllegalStateException.class,
                () -> map.add(new Doc("nan", filled(Float.NaN))),
                "a NaN embedding must be refused at the door");

            // The point of refusing it: the index is still an index afterwards.
            index.persistToDisk();
            assertEquals(10, hits(index, good.get(0)),
                "the index returns nothing after a persist, so a non-finite vector reached the"
                    + " quantizer and every stored vector with it");
        }
    }

    /**
     * Both non-finite kinds, and both entry points that accept an embedding.
     */
    @Test
    @Timeout(value = 300, unit = TimeUnit.SECONDS)
    void everyNonFiniteEmbeddingIsRejected()
    {
        final GigaMap<Doc> map = GigaMap.New();
        try(final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
            .add("embeddings", VectorIndexConfiguration.builder()
                .dimension(DIM)
                .similarityFunction(VectorSimilarityFunction.COSINE)
                .build(), new Vz()))
        {
            for(final float bad : new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY})
            {
                assertThrows(IllegalStateException.class,
                    () -> map.add(new Doc("bad", filled(bad))),
                    "add must reject " + bad);

                assertThrows(IllegalStateException.class,
                    () -> map.addAll(new Doc("bad", filled(bad)), new Doc("worse", filled(bad))),
                    "addAll must reject " + bad);
            }

            // One non-finite component among good ones is enough, which is the realistic shape -
            // a single division by a zero norm, not a whole vector of them.
            final float[] mostlyGood = unit(new Random(11));
            mostlyGood[DIM / 2] = Float.NaN;
            assertThrows(IllegalStateException.class,
                () -> map.add(new Doc("one-bad-component", mostlyGood)),
                "a single non-finite component must be enough to reject the vector");

            assertTrue(map.isEmpty(), "nothing must have been indexed");
        }
    }

    /**
     * A non-finite query cannot poison anything, but it cannot answer anything either.
     */
    @Test
    @Timeout(value = 300, unit = TimeUnit.SECONDS)
    void aNonFiniteQueryIsRejectedRatherThanReturningNothing()
    {
        final Random random = new Random(3);

        final GigaMap<Doc> map = GigaMap.New();
        try(final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
            .add("embeddings", VectorIndexConfiguration.builder()
                .dimension(DIM)
                .similarityFunction(VectorSimilarityFunction.COSINE)
                .build(), new Vz()))
        {
            for(int i = 0; i < 50; i++)
            {
                map.add(new Doc("d" + i, unit(random)));
            }

            assertThrows(IllegalStateException.class,
                () -> index.search(filled(Float.NaN), 10),
                "a NaN query returns an arbitrary result or none, silently; it must say so instead");
        }
    }
}
