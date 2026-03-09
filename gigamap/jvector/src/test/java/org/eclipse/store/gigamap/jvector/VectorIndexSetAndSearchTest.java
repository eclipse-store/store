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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that {@code GigaMap.set()} correctly updates the HNSW graph
 * so that subsequent searches find the updated vector.
 */
class VectorIndexSetAndSearchTest
{
    @TempDir
    Path tempDir;

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

        @Override
        public boolean isEmbedded()
        {
            return false;
        }
    }

    @Test
    void setAfterSearchUpdatesHNSWGraph()
    {
        final GigaMap<Entity> map = GigaMap.New();
        final VectorIndices<Entity> vectorIndices = map.index().register(VectorIndices.Category());
        vectorIndices.add(
            "vec",
            VectorIndexConfiguration.builder()
                .dimension(4)
                .similarityFunction(VectorSimilarityFunction.COSINE)
                .onDisk(true)
                .indexDirectory(this.tempDir.resolve("index"))
                .eventualIndexing(false)
                .build(),
            new EntityVectorizer()
        );

        final long idA = map.add(new Entity(new float[]{1, 0, 0, 0}));
        final long idB = map.add(new Entity(new float[]{0, 1, 0, 0}));

        final VectorIndex<Entity> index = vectorIndices.get("vec");
        final var results = index.search(new float[]{1, 0, 0, 0}, 5);
        assertFalse(results.isEmpty(), "initial search should return results");

        map.set(idA, new Entity(new float[]{0.5f, 0.5f, 0, 0}));

        final float[] updatedVector = index.getVector(idA);
        assertNotNull(updatedVector, "getVector should return updated vector");
        assertArrayEquals(new float[]{0.5f, 0.5f, 0, 0}, updatedVector, "vector should be updated");

        final var searchResults = index.search(new float[]{0.5f, 0.5f, 0, 0}, 2);
        assertFalse(searchResults.isEmpty(), "search should return results");

        final long topResultId = searchResults.stream().findFirst().orElseThrow().entityId();
        assertEquals(idA, topResultId,
            "Entity A should be the top result for its own updated vector");
    }

    @Test
    void setWithoutPriorSearchUpdatesHNSWGraph()
    {
        final GigaMap<Entity> map = GigaMap.New();
        final VectorIndices<Entity> vectorIndices = map.index().register(VectorIndices.Category());
        vectorIndices.add(
            "vec",
            VectorIndexConfiguration.builder()
                .dimension(4)
                .similarityFunction(VectorSimilarityFunction.COSINE)
                .onDisk(true)
                .indexDirectory(this.tempDir.resolve("index"))
                .eventualIndexing(false)
                .build(),
            new EntityVectorizer()
        );

        final long idA = map.add(new Entity(new float[]{1, 0, 0, 0}));
        final long idB = map.add(new Entity(new float[]{0, 1, 0, 0}));

        map.set(idA, new Entity(new float[]{0.5f, 0.5f, 0, 0}));

        final VectorIndex<Entity> index = vectorIndices.get("vec");
        final var searchResults = index.search(new float[]{0.5f, 0.5f, 0, 0}, 2);
        assertFalse(searchResults.isEmpty(), "search should return results");

        final long topResultId = searchResults.stream().findFirst().orElseThrow().entityId();
        assertEquals(idA, topResultId,
            "Entity A should be the top result for its own updated vector");
    }
}
