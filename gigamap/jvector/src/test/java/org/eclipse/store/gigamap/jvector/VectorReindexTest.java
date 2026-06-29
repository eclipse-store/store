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
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Probes the PR #724 claim that {@code GigaMap.reindex()} is "correct for bitmap/vector groups",
 * which the PR does NOT cover with any jvector test. A vector field is mutated DIRECTLY (not via
 * set()/update()/apply()), so the HNSW graph goes stale; reindex() must rebuild it from current state.
 */
class VectorReindexTest
{
    static class Entity
    {
        float[] vector;

        Entity(final float[] vector)
        {
            this.vector = vector;
        }
    }

    static class EntityVectorizer extends Vectorizer<Entity>
    {
        @Override
        public float[] vectorize(final Entity entity)
        {
            return entity.vector;
        }
    }

    private static VectorIndices<Entity> registerIndex(final GigaMap<Entity> map, final Path indexDir)
    {
        final VectorIndices<Entity> vectorIndices = map.index().register(VectorIndices.Category());
        vectorIndices.add(
            "vec",
            VectorIndexConfiguration.builder()
                .dimension(4)
                .similarityFunction(VectorSimilarityFunction.COSINE)
                .onDisk(true)
                .indexDirectory(indexDir)
                .eventualIndexing(false)
                .build(),
            new EntityVectorizer()
        );
        return vectorIndices;
    }

    @Test
    void reindex_recoversStaleVectorIndex(@TempDir final Path tempDir)
    {
        final GigaMap<Entity> map = GigaMap.New();
        final VectorIndices<Entity> vectorIndices = registerIndex(map, tempDir.resolve("index"));

        final Entity entityA = new Entity(new float[]{1, 0, 0, 0});
        final long idA = map.add(entityA);
        map.add(new Entity(new float[]{0, 1, 0, 0}));

        final VectorIndex<Entity> index = vectorIndices.get("vec");

        // sanity: index reflects the original vector
        assertArrayEquals(new float[]{1, 0, 0, 0}, index.getVector(idA), "index should hold original vector");

        // DIRECT mutation - bypasses the indexers, so the HNSW graph stays stale
        entityA.vector = new float[]{0, 0, 0, 1};

        // stale state: index still has the old vector, not the directly mutated one
        assertArrayEquals(new float[]{1, 0, 0, 0}, index.getVector(idA),
            "index is expected to be stale before reindex");

        // rebuild from current entity state
        map.reindex();

        final float[] afterReindex = index.getVector(idA);
        assertNotNull(afterReindex, "entity must still be indexed after reindex");
        assertArrayEquals(new float[]{0, 0, 0, 1}, afterReindex,
            "reindex must rebuild the vector from current entity state. Actual: " + Arrays.toString(afterReindex));

        // and search must now rank idA top for its new vector
        final var hits = index.search(new float[]{0, 0, 0, 1}, 2);
        assertFalse(hits.isEmpty(), "search should return results after reindex");
        assertEquals(idA, hits.stream().findFirst().orElseThrow().entityId(),
            "entity A should be the top result for its updated vector after reindex");
    }

    @Test
    void reindex_recoversStaleVectorIndex_eventualIndexing()
    {
        final GigaMap<Entity> map = GigaMap.New();
        final VectorIndices<Entity> vectorIndices = map.index().register(VectorIndices.Category());
        vectorIndices.add(
            "vec",
            VectorIndexConfiguration.builder()
                .dimension(4)
                .similarityFunction(VectorSimilarityFunction.COSINE)
                .eventualIndexing(true)
                .build(),
            new EntityVectorizer()
        );

        final Entity entityA = new Entity(new float[]{1, 0, 0, 0});
        final long idA = map.add(entityA);
        map.add(new Entity(new float[]{0, 1, 0, 0}));

        final VectorIndex.Default<Entity> index = (VectorIndex.Default<Entity>) vectorIndices.get("vec");
        index.backgroundTaskManager.drainQueue();
        assertArrayEquals(new float[]{1, 0, 0, 0}, index.getVector(idA), "index should hold original vector");

        // DIRECT mutation, then rebuild
        entityA.vector = new float[]{0, 0, 0, 1};
        map.reindex();

        // honor eventual semantics: drain the background queue, then the rebuild must be visible
        index.backgroundTaskManager.drainQueue();

        final float[] afterReindex = index.getVector(idA);
        assertNotNull(afterReindex, "entity must still be indexed after reindex + drain");
        assertArrayEquals(new float[]{0, 0, 0, 1}, afterReindex,
            "reindex must rebuild the vector under eventualIndexing. Actual: " + Arrays.toString(afterReindex));

        final var hits = index.search(new float[]{0, 0, 0, 1}, 2);
        assertFalse(hits.isEmpty(), "search should return results after reindex + drain");
        assertEquals(idA, hits.stream().findFirst().orElseThrow().entityId(),
            "entity A should be the top result for its updated vector after reindex");
    }
}
