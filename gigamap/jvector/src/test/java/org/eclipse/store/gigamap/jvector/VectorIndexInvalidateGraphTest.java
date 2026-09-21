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
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests {@link VectorIndex#invalidateGraph()}: the transient in-memory graph is
 * retired and lazily rebuilt from the current entity state, so consumers
 * (e.g. replication readers applying externally merged binary state) observe
 * current data after invalidation, with no reference to internal fields.
 */
class VectorIndexInvalidateGraphTest
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

    private static VectorIndex<Entity> newIndex(final GigaMap<Entity> map)
    {
        final VectorIndices<Entity> vectorIndices = map.index().register(VectorIndices.Category());
        vectorIndices.add(
            "vec",
            VectorIndexConfiguration.builder()
                .dimension(4)
                .similarityFunction(VectorSimilarityFunction.COSINE)
                .eventualIndexing(false)
                .build(),
            new EntityVectorizer()
        );
        return vectorIndices.get("vec");
    }

    @Test
    void searchAfterInvalidationRebuildsFromCurrentState()
    {
        final GigaMap<Entity> map = GigaMap.New();
        final VectorIndex<Entity> index = newIndex(map);

        final long idA = map.add(new Entity(new float[]{1, 0, 0, 0}));
        map.add(new Entity(new float[]{0, 1, 0, 0}));

        assertEquals(idA, index.search(new float[]{1, 0, 0, 0}, 1).stream()
            .findFirst().orElseThrow().entityId());

        index.invalidateGraph();

        // The graph was retired; the first access must rebuild it from stored state.
        final var results = index.search(new float[]{1, 0, 0, 0}, 2);
        assertFalse(results.isEmpty(), "search after invalidation should return results");
        assertEquals(idA, results.stream().findFirst().orElseThrow().entityId(),
            "top result after invalidation must still be entity A");
    }

    @Test
    void invalidationAfterMutationSeesLatestEntityState()
    {
        final GigaMap<Entity> map = GigaMap.New();
        final VectorIndex<Entity> index = newIndex(map);

        final long idA = map.add(new Entity(new float[]{1, 0, 0, 0}));
        map.add(new Entity(new float[]{0, 1, 0, 0}));
        final long idC = map.add(new Entity(new float[]{0.99f, 0.01f, 0, 0}));
        index.search(new float[]{1, 0, 0, 0}, 1);

        // Move entity A far away, then invalidate: the rebuilt graph must answer
        // against the updated entity, not the retired graph.
        map.set(idA, new Entity(new float[]{0, 1, 0, 0}));
        index.invalidateGraph();

        assertEquals(idA, index.search(new float[]{0, 1, 0, 0}, 1).stream()
            .findFirst().orElseThrow().entityId(),
            "top result must follow the entity state at invalidation time");
        assertEquals(idC, index.search(new float[]{1, 0, 0, 0}, 1).stream()
                .findFirst().orElseThrow().entityId(),
            "the retired graph's answer must not be replayed after invalidation");
    }

    @Test
    void doubleInvalidationIsSafe()
    {
        final GigaMap<Entity> map = GigaMap.New();
        final VectorIndex<Entity> index = newIndex(map);

        map.add(new Entity(new float[]{1, 0, 0, 0}));
        index.search(new float[]{1, 0, 0, 0}, 1);

        index.invalidateGraph();
        // Invalidating again before any access must not touch a half-torn-down state.
        index.invalidateGraph();

        assertFalse(index.search(new float[]{1, 0, 0, 0}, 1).isEmpty(),
            "search after a repeated invalidation must still work");
    }

    @Test
    void invalidationBeforeAnySearchIsSafe()
    {
        final GigaMap<Entity> map = GigaMap.New();
        final VectorIndex<Entity> index = newIndex(map);
        map.add(new Entity(new float[]{1, 0, 0, 0}));

        // Never searched: the graph may be in its post-registration transient state.
        index.invalidateGraph();

        assertFalse(index.search(new float[]{1, 0, 0, 0}, 1).isEmpty(),
            "search after invalidation without a prior search must still work");
    }

    @Test
    void incrementalOnDiskModeRejectsInvalidation(@TempDir final Path tempDir)
    {
        final Path indexDir = tempDir.resolve("index");
        final Path storageDir = tempDir.resolve("storage");

        // Phase 1: on-disk-configured but not yet incremental (no persist yet);
        // invalidation is legal and must keep answering from stored state.
        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final GigaMap<Entity> map = GigaMap.New();
            storage.setRoot(map);
            map.index().register(VectorIndices.Category()).add(
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
            final VectorIndex<Entity> index =
                map.index().get(VectorIndices.Category()).get("vec");
            map.add(new Entity(new float[]{1, 0, 0, 0}));

            index.invalidateGraph(); // not yet incremental: must work
            assertFalse(index.search(new float[]{1, 0, 0, 0}, 1).isEmpty());

            // persistToDisk() enters incremental on-disk serving mode.
            index.persistToDisk();
            storage.storeRoot();
        }

        // Phase 2: after a disk reload the index serves from incremental mode;
        // invalidation must fail closed instead of half-retiring the graph.
        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final GigaMap<Entity> map = storage.root();
            final VectorIndex<Entity> index =
                map.index().get(VectorIndices.Category()).get("vec");

            assertThrows(IllegalStateException.class, index::invalidateGraph,
                "incremental on-disk mode must reject graph invalidation");
        }
    }
}
