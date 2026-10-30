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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

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
        final AtomicInteger calls = new AtomicInteger();

        @Override
        public float[] vectorize(final Entity entity)
        {
            this.calls.incrementAndGet();
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

    /** External imports replace entities without updating the temporary vector file or its search graph. */
    @Test
    void externalEntityChangesRecoverLazilyWithoutChangingThePersistedWitness()
    {
        final GigaMap<Entity> map = GigaMap.New();
        try(final VectorIndex<Entity> index = newIndex(map))
        {
            final Entity entity = new Entity(new float[]{1, 0, 0, 0});
            final long id = map.add(entity);
            map.add(new Entity(new float[]{0, 1, 0, 0}));
            index.search(entity.vector, 1);
            final EntityVectorizer vectorizer = (EntityVectorizer)index.vectorizer();
            final long witness = ((VectorIndex.Default<Entity>)index).getStructuralModCount();

            for(final float[] replacement : new float[][]{{0, 0, 1, 0}, {-1, 0, 0, 0}})
            {
                entity.vector = replacement;
                final int calls = vectorizer.calls.get();
                index.invalidateGraph();
                index.invalidateGraph();
                assertEquals(calls, vectorizer.calls.get(), "invalidation must not vectorize entities");
                assertEquals(witness, ((VectorIndex.Default<Entity>)index).getStructuralModCount());

                assertArrayEquals(replacement, index.getVector(id));
                assertEquals(id, index.search(replacement, 1).toList().getFirst().entityId());
                assertEquals(calls + 2, vectorizer.calls.get(), "each entity is recovered once on access");
                assertEquals(witness, ((VectorIndex.Default<Entity>)index).getStructuralModCount(),
                    "recovery must not hide the next imported change");
            }
        }
    }

    @Test
    void invalidationAfterReindexPreservesTheNewWitness()
    {
        final GigaMap<Entity> map = GigaMap.New();
        try(final VectorIndex<Entity> index = newIndex(map))
        {
            final Entity entity = new Entity(new float[]{1, 0, 0, 0});
            final long id = map.add(entity);
            index.search(entity.vector, 1);
            entity.vector = new float[]{0, 1, 0, 0};
            map.reindex();
            assertArrayEquals(entity.vector, index.getVector(id));
            final long witness = ((VectorIndex.Default<Entity>)index).getStructuralModCount();

            entity.vector = new float[]{0, 0, 1, 0};
            index.invalidateGraph();
            assertArrayEquals(entity.vector, index.getVector(id));
            assertEquals(id, index.search(entity.vector, 1).toList().getFirst().entityId());
            assertEquals(witness, ((VectorIndex.Default<Entity>)index).getStructuralModCount(),
                "cache invalidation must not inherit reindex's structural-counter bump");
        }
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
    void onDiskIndexRejectsInvalidation(@TempDir final Path tempDir)
    {
        final Path indexDir = tempDir.resolve("index");
        final Path storageDir = tempDir.resolve("storage");

        // An on-disk index serves its graph from the segments in its index directory, which the
        // invalidation seam does not retire: the call fails closed instead of half-retiring it, from
        // the start and after a reload alike.
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

            assertThrows(IllegalStateException.class, index::invalidateGraph,
                "an on-disk index must reject graph invalidation");
            // Rejected without touching the graph: the index still answers.
            assertFalse(index.search(new float[]{1, 0, 0, 0}, 1).isEmpty());

            index.persistToDisk();
            storage.storeRoot();
        }

        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final GigaMap<Entity> map = storage.root();
            final VectorIndex<Entity> index =
                map.index().get(VectorIndices.Category()).get("vec");

            assertThrows(IllegalStateException.class, index::invalidateGraph,
                "a loaded on-disk index must reject graph invalidation");
            assertFalse(index.search(new float[]{1, 0, 0, 0}, 1).isEmpty());
        }
    }

    /**
     * Concurrent first searches after an invalidation must all see the complete graph. The first access
     * after invalidateGraph() creates the in-memory builder; unsynchronized, two racing searches each
     * created one and the later replaced the rebuilt graph with an empty one that stayed marked as rebuilt.
     */
    @Test
    void concurrentFirstSearchesAfterInvalidationAllSeeTheFullGraph() throws Exception
    {
        final int entities = 200;
        final int searchers = 16;
        for(int round = 0; round < 400; round++)
        {
            final GigaMap<Entity> map = GigaMap.New();
            final VectorIndex<Entity> index = newIndex(map);
            for(int i = 0; i < entities; i++)
            {
                map.add(new Entity(new float[]{1 + i, 1, 0, 0}));
            }
            index.invalidateGraph();

            final CountDownLatch start = new CountDownLatch(1);
            final AtomicInteger incomplete = new AtomicInteger();
            final List<Thread> threads = new ArrayList<>();
            for(int t = 0; t < searchers; t++)
            {
                final Thread thread = new Thread(() ->
                {
                    try
                    {
                        start.await();
                        if(index.search(new float[]{100, 1, 0, 0}, 10).stream().count() != 10)
                        {
                            incomplete.incrementAndGet();
                        }
                    }
                    catch(final InterruptedException interrupted)
                    {
                        Thread.currentThread().interrupt();
                    }
                });
                thread.start();
                threads.add(thread);
            }
            start.countDown();
            for(final Thread thread : threads)
            {
                thread.join();
            }
            assertEquals(0, incomplete.get(), "round " + round + ": a search saw an empty or partial graph");
            assertEquals(10, index.search(new float[]{100, 1, 0, 0}, 10).stream().count());
        }
    }
}
