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

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A {@code reindex()} after a restart, on an on-disk index with eventual indexing. The restart loads the persisted
 * graph and serves it in incremental mode, so the deferred rebuild from the store never runs and its one-shot flag
 * stays unset. {@code reindex()} then replaces the generation and re-adds every entity through the worker. The next
 * access must not run the deferred rebuild: it would insert the same ordinals from the vector store while the worker
 * inserts them from its queue, and jvector rejects the duplicate from an upper layer ("Node N already exists").
 */
class VectorIndexReindexAfterRestartTest
{
    private static final int DIM   = 8;
    private static final int COUNT = 200;

    public static final class Doc
    {
        float[] v;

        Doc(final float[] v)
        {
            this.v = v;
        }
    }

    static final class ComputedVectorizer extends Vectorizer<Doc>
    {
        @Override
        public float[] vectorize(final Doc d)
        {
            return d.v;
        }

        @Override
        public boolean isEmbedded()
        {
            return false;
        }

        @Override
        public boolean allowsNullVectors()
        {
            return true;
        }
    }

    private static float[] vec(final Random r)
    {
        final float[] v = new float[DIM];
        for(int i = 0; i < DIM; i++)
        {
            v[i] = r.nextFloat() * 2 - 1;
        }
        return v;
    }

    private static VectorIndexConfiguration onDiskEventual(final Path indexDir)
    {
        return VectorIndexConfiguration.builder()
            .dimension(DIM)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .eventualIndexing(true)
            .onDisk(true)
            .indexDirectory(indexDir)
            .build();
    }

    @SuppressWarnings("unchecked")
    private static <T> T internalState(final VectorIndex<?> index, final String fieldName)
    {
        try
        {
            final Field field = VectorIndex.Default.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return (T)field.get(index);
        }
        catch(final ReflectiveOperationException e)
        {
            throw new AssertionError("cannot read VectorIndex.Default." + fieldName, e);
        }
    }

    private static void awaitWorker(final VectorIndex<?> index)
    {
        final BackgroundTaskManager manager = internalState(index, "backgroundTaskManager");
        assertNotNull(manager, "precondition: eventual indexing has a background task manager");
        manager.drainQueue();
        manager.drainQueue();
    }

    private static List<Long> missingLiveEntities(final GigaMap<Doc> map, final VectorIndex<Doc> index)
    {
        final List<Long> missing = new ArrayList<>();
        map.iterateIndexed((id, d) ->
        {
            if(d.v != null && index.search(d.v, 3).toList().stream().noneMatch(e -> e.entityId() == id))
            {
                missing.add(id);
            }
        });
        return missing;
    }

    /**
     * The structural statement: after a restart in incremental mode the deferred rebuild is still armed; a
     * {@code reindex()} must disarm it, because the new generation is populated by the re-add.
     */
    @Test
    void reindexAfterARestartDisarmsTheDeferredRebuild(@TempDir final Path tempDir)
    {
        final Random r          = new Random(1);
        final Path   storageDir = tempDir.resolve("storage");
        try(EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final GigaMap<Doc> map = GigaMap.New();
            storage.setRoot(map);
            storage.storeRoot();
            final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
                .add("v", onDiskEventual(tempDir.resolve("index")), new ComputedVectorizer());
            for(int i = 0; i < COUNT; i++)
            {
                map.add(new Doc(vec(r)));
            }
            awaitWorker(index);
            index.persistToDisk();
            map.store();
        }

        try(EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final GigaMap<Doc>     map   = storage.root();
            final VectorIndex<Doc> index = map.index().get(VectorIndices.class).get("v");
            index.search(vec(r), 1); // loads the persisted graph
            assertTrue((boolean)internalState(index, "incrementalMode"), "precondition: the persisted graph was accepted");
            assertFalse((boolean)internalState(index, "graphRebuilt"), "precondition: the deferred rebuild is still armed");

            map.reindex();
            assertTrue((boolean)internalState(index, "graphRebuilt"),
                "reindex() left the deferred rebuild armed against the generation its worker is filling");

            // the accesses that used to trigger the rebuild while the worker was still inserting
            for(int i = 0; i < 50; i++)
            {
                final float[] nv = vec(r);
                map.update(i, d -> d.v = nv);
            }
            awaitWorker(index);
            assertEquals(List.of(), missingLiveEntities(map, index), "entities missing after reindex() and updates");
        }
    }

    /**
     * The scenario of the report: randomized rounds of set/update/null-transition/add, an occasional
     * {@code optimize()}, a {@code reindex()} per round and a restart every few rounds. Seeded; without the fix a
     * routine {@code set()} or {@code update()} threw from the deferred rebuild within the first rounds.
     */
    @Test
    void randomizedRoundsWithReindexAndRestarts(@TempDir final Path tempDir) throws Exception
    {
        final Random r          = new Random(5);
        final Path   storageDir = tempDir.resolve("storage");
        final Path   indexDir   = tempDir.resolve("index");

        EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir);
        try
        {
            GigaMap<Doc> map = GigaMap.New();
            storage.setRoot(map);
            storage.storeRoot();
            VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
                .add("v", onDiskEventual(indexDir), new ComputedVectorizer());
            for(int i = 0; i < COUNT; i++)
            {
                map.add(new Doc(vec(r)));
            }
            for(int round = 0; round < 30; round++)
            {
                for(int i = 0; i < 10; i++)
                {
                    final long id = r.nextInt((int)map.highestUsedId());
                    if(map.get(id) == null)
                    {
                        continue;
                    }
                    switch(r.nextInt(4))
                    {
                        case 0 -> map.set(id, new Doc(vec(r)));
                        case 1 ->
                        {
                            final float[] nv = vec(r);
                            map.update(id, d -> d.v = nv);
                        }
                        case 2 -> map.set(id, new Doc(null));
                        default -> map.add(new Doc(vec(r)));
                    }
                }
                if(r.nextBoolean())
                {
                    index.optimize();
                }
                map.reindex();
                if(r.nextInt(3) == 0)
                {
                    map.store();
                    storage.shutdown();
                    storage = EmbeddedStorage.start(storageDir);
                    map     = storage.root();
                    index   = map.index().get(VectorIndices.class).get("v");
                }
            }
            awaitWorker(index);
            assertEquals(List.of(), missingLiveEntities(map, index), "live entities missing after the rounds");
        }
        finally
        {
            storage.shutdown();
        }
    }
}
