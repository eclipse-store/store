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

import io.github.jbellis.jvector.graph.RandomAccessVectorValues;
import io.github.jbellis.jvector.graph.similarity.BuildScoreProvider;
import io.github.jbellis.jvector.vector.VectorSimilarityFunction;
import io.github.jbellis.jvector.vector.VectorizationProvider;
import io.github.jbellis.jvector.vector.types.VectorFloat;
import io.github.jbellis.jvector.vector.types.VectorTypeSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ReusableVectorValuesTest
{
    private static final VectorTypeSupport TYPES = VectorizationProvider.getInstance().getVectorTypeSupport();

    @TempDir
    Path directory;

    private VectorFile open()
    {
        final VectorFile file = VectorFile.Default.open(this.directory, "vectors", 2, 256L, 0L, 0L, "test");
        file.recovered();
        file.write(0, new float[]{1f, 0f}, Vectorizer.NO_KEY);
        file.write(1, new float[]{0f, 1f}, Vectorizer.NO_KEY);
        return file;
    }

    private RandomAccessVectorValues values(final MappedVectorValues mapped)
    {
        return new ReusableVectorValues(new NullSafeVectorValues(mapped, 2, TYPES), TYPES);
    }

    @Test
    void reusesTheDestinationWithoutCallingTheAllocatingRead()
    {
        try(final VectorFile file = this.open())
        {
            final MappedVectorValues mapped = new MappedVectorValues(file, TYPES)
            {
                @Override
                public VectorFloat<?> getVector(final int ordinal)
                {
                    fail("present vectors must be copied directly into the reusable destination");
                    return null;
                }
            };
            final RandomAccessVectorValues values = this.values(mapped);
            final VectorFloat<?> first = values.getVector(0);
            assertEquals(1f, first.get(0));
            assertSame(first, values.getVector(1));
            assertEquals(0f, first.get(0));
            assertEquals(1f, first.get(1));
            assertTrue(values.isValueShared());
            assertNotSame(first, values.copy().getVector(0));
            assertArrayEquals(new float[]{1f, 0f}, file.read(0));
        }
    }

    @Test
    void scoringKeepsTheLeftOperandStableWhileReadingTheRight()
    {
        try(final VectorFile file = this.open())
        {
            final var provider = BuildScoreProvider.randomAccessScoreProvider(
                this.values(new MappedVectorValues(file, TYPES)), VectorSimilarityFunction.COSINE);
            final var score = provider.diversityScoreFunctionFor(0);
            assertEquals(0.5f, score.similarityTo(1));
            assertEquals(1f, score.similarityTo(0));
            assertEquals(0.5f, score.similarityTo(1));
            assertTrue(Float.isFinite(score.similarityTo(2)), "holes must still use a finite placeholder");
        }
    }

    @Test
    void buildWorkersHaveIndependentOperands() throws Exception
    {
        try(final VectorFile file = this.open(); final var workers = Executors.newFixedThreadPool(2))
        {
            final var provider = BuildScoreProvider.randomAccessScoreProvider(
                this.values(new MappedVectorValues(file, TYPES)), VectorSimilarityFunction.COSINE);
            final CyclicBarrier ready = new CyclicBarrier(2);
            final var first = workers.submit(() -> {
                for(int i = 0; i < 100; i++)
                {
                    final var score = provider.diversityScoreFunctionFor(0);
                    ready.await(10, TimeUnit.SECONDS);
                    assertEquals(0.5f, score.similarityTo(1));
                    assertEquals(1f, score.similarityTo(0));
                    ready.await(10, TimeUnit.SECONDS);
                }
                return null;
            });
            final var second = workers.submit(() -> {
                for(int i = 0; i < 100; i++)
                {
                    final var score = provider.diversityScoreFunctionFor(1);
                    ready.await(10, TimeUnit.SECONDS);
                    assertEquals(0.5f, score.similarityTo(0));
                    assertEquals(1f, score.similarityTo(1));
                    ready.await(10, TimeUnit.SECONDS);
                }
                return null;
            });
            first.get(30, TimeUnit.SECONDS);
            second.get(30, TimeUnit.SECONDS);
        }
    }

    @Test
    void copiesStillObserveTheSameFrozenGenerationAndRespectDestinationOffsets()
    {
        try(final VectorFile file = this.open())
        {
            final GenerationVectorValues generation = new GenerationVectorValues(file, TYPES);
            final RandomAccessVectorValues copy = this.values(generation).copy();
            generation.freeze(Map.of());
            generation.preserve(0, file.read(0));
            file.write(0, new float[]{0f, 1f}, Vectorizer.NO_KEY);
            final VectorFloat<?> target = TYPES.createFloatVector(new float[]{9f, 9f, 9f, 9f});
            copy.getVectorInto(0, target, 1);
            assertEquals(9f, target.get(0));
            assertEquals(1f, target.get(1));
            assertEquals(0f, target.get(2));
            assertEquals(9f, target.get(3));
            assertEquals(1f, copy.getVector(0).get(0));
        }
    }
}
