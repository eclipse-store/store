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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A computed vectorizer may return one buffer that it overwrites on every call: the index copies each result
 * before the next call. Every path that takes vectors from the vectorizer must keep each entity's own vector,
 * in every mode: add, addAll, update, reindex (which collects all vectors before it writes them), the backfill
 * of an index added to a populated map, and the re-vectorization at a start.
 */
class VectorIndexSharedBufferVectorizerTest
{
    private static final int    COUNT     = 300;
    private static final int    DIMENSION = 8;
    private static final String NAME      = "shared";

    enum Mode
    {
        IN_MEMORY_SYNC    (false, false),
        IN_MEMORY_EVENTUAL(false, true ),
        ON_DISK_SYNC      (true , false),
        ON_DISK_EVENTUAL  (true , true );

        final boolean onDisk;
        final boolean eventual;

        Mode(final boolean onDisk, final boolean eventual)
        {
            this.onDisk   = onDisk;
            this.eventual = eventual;
        }
    }

    static class Doc
    {
        final float[] vector;

        Doc(final float[] vector)
        {
            this.vector = vector;
        }
    }

    /**
     * Computed, and returns the same array from every call, filled with the entity's vector.
     */
    static class SharedBufferVectorizer extends Vectorizer<Doc>
    {
        static final AtomicInteger CALLS = new AtomicInteger();

        private transient float[] buffer;

        @Override
        public float[] vectorize(final Doc entity)
        {
            CALLS.incrementAndGet();
            if(this.buffer == null)
            {
                this.buffer = new float[DIMENSION];
            }
            System.arraycopy(entity.vector, 0, this.buffer, 0, DIMENSION);
            return this.buffer;
        }
    }

    private static List<Doc> docs(final long seed)
    {
        final Random    random = new Random(seed);
        final List<Doc> docs   = new ArrayList<>(COUNT);
        for(int i = 0; i < COUNT; i++)
        {
            final float[] vector = new float[DIMENSION];
            for(int d = 0; d < DIMENSION; d++)
            {
                vector[d] = random.nextFloat() * 2 - 1;
            }
            docs.add(new Doc(vector));
        }
        return docs;
    }

    private static VectorIndexConfiguration configuration(final Mode mode, final Path indexDirectory)
    {
        final VectorIndexConfiguration.Builder builder = VectorIndexConfiguration.builder()
            .dimension(DIMENSION)
            .similarityFunction(VectorSimilarityFunction.EUCLIDEAN)
            .eventualIndexing(mode.eventual);
        if(mode.onDisk)
        {
            builder.onDisk(true).indexDirectory(indexDirectory);
        }
        return builder.build();
    }

    private static VectorIndex<Doc> register(final GigaMap<Doc> map, final Mode mode, final Path indexDirectory)
    {
        return map.index().register(VectorIndices.Category())
            .add(NAME, configuration(mode, indexDirectory), new SharedBufferVectorizer());
    }

    @SuppressWarnings("unchecked")
    private static VectorIndex<Doc> index(final GigaMap<Doc> map)
    {
        return map.index().get(VectorIndices.class).get(NAME);
    }

    /**
     * Every entity holds its own vector in the index and is found by it. The search beam covers every node: what
     * is checked is the vector each entity is indexed with, not recall.
     */
    private static void assertEveryEntityKeepsItsVector(final GigaMap<Doc> map, final VectorIndex<Doc> index)
    {
        if(index.configuration().eventualIndexing())
        {
            ((VectorIndex.Default<Doc>)index).backgroundTaskManager.drainQueue();
        }
        assertEquals(COUNT, map.size());
        int wrong = 0;
        int lost  = 0;
        for(long id = 0; id < COUNT; id++)
        {
            final float[] expected = map.get(id).vector;
            final float[] actual   = index.getVector(id);
            if(actual == null || !Arrays.equals(expected, actual))
            {
                wrong++;
                continue;
            }
            final long entityId = id;
            if(index.search(expected, 1, COUNT + 50).toList().stream().noneMatch(e -> e.entityId() == entityId))
            {
                lost++;
            }
        }
        assertEquals(0, wrong, wrong + " of " + COUNT + " entities are indexed with another entity's vector");
        assertEquals(0, lost, lost + " of " + COUNT + " entities are not found by their own vector");
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void add_eachEntityKeepsItsOwnVector(final Mode mode, @TempDir final Path tempDir)
    {
        final GigaMap<Doc> map = GigaMap.New();
        try(final VectorIndex<Doc> index = register(map, mode, tempDir))
        {
            docs(1).forEach(map::add);
            assertEveryEntityKeepsItsVector(map, index);
        }
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void addAll_eachEntityKeepsItsOwnVector(final Mode mode, @TempDir final Path tempDir)
    {
        final GigaMap<Doc> map = GigaMap.New();
        try(final VectorIndex<Doc> index = register(map, mode, tempDir))
        {
            map.addAll(docs(2));
            assertEveryEntityKeepsItsVector(map, index);
        }
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void set_eachEntityKeepsItsNewVector(final Mode mode, @TempDir final Path tempDir)
    {
        final GigaMap<Doc> map = GigaMap.New();
        try(final VectorIndex<Doc> index = register(map, mode, tempDir))
        {
            map.addAll(docs(3));
            final List<Doc> replacements = docs(4);
            for(int id = 0; id < COUNT; id++)
            {
                map.set(id, replacements.get(id));
            }
            assertEveryEntityKeepsItsVector(map, index);
        }
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void reindex_eachEntityKeepsItsOwnVector(final Mode mode, @TempDir final Path tempDir)
    {
        final GigaMap<Doc> map = GigaMap.New();
        try(final VectorIndex<Doc> index = register(map, mode, tempDir))
        {
            map.addAll(docs(5));
            assertEveryEntityKeepsItsVector(map, index);

            // A computed reindex vectorizes every entity before it drops anything, and writes the vectors after.
            map.reindex();
            assertEveryEntityKeepsItsVector(map, index);

            if(mode.onDisk)
            {
                // and the segment written from the rebuilt index carries the same vectors
                index.persistToDisk();
                assertEveryEntityKeepsItsVector(map, index);
            }
        }
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void backfill_eachEntityKeepsItsOwnVector(final Mode mode, @TempDir final Path tempDir)
    {
        final GigaMap<Doc> map = GigaMap.New();
        map.addAll(docs(6));
        try(final VectorIndex<Doc> index = register(map, mode, tempDir))
        {
            assertEveryEntityKeepsItsVector(map, index);
        }
    }

    @Test
    void restart_revectorizesEachEntityWithItsOwnVector(@TempDir final Path tempDir)
    {
        // In memory without a directory: the vector file is temporary, so a start vectorizes every entity.
        final Path storageDir = tempDir.resolve("storage");
        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final GigaMap<Doc> map = GigaMap.New();
            storage.setRoot(map);
            register(map, Mode.IN_MEMORY_SYNC, null);
            map.addAll(docs(7));
            storage.storeRoot();
        }

        SharedBufferVectorizer.CALLS.set(0);
        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            @SuppressWarnings("unchecked")
            final GigaMap<Doc>     map   = (GigaMap<Doc>)storage.root();
            final VectorIndex<Doc> index = index(map);
            assertEveryEntityKeepsItsVector(map, index);
            assertTrue(SharedBufferVectorizer.CALLS.get() >= COUNT,
                "the start must re-vectorize every entity, else this test checks nothing");
        }
    }

    @Test
    void restart_firstSearchByEntity_scoresTheQueryEntitysVector(@TempDir final Path tempDir)
    {
        // The search vectorizes the query entity, then its first access recovers the vectors, which calls the
        // same vectorizer on the same thread for every entity: a per-thread buffer does not help here.
        final Path storageDir = tempDir.resolve("storage");
        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final GigaMap<Doc> map = GigaMap.New();
            storage.setRoot(map);
            register(map, Mode.IN_MEMORY_SYNC, null);
            map.addAll(docs(9));
            storage.storeRoot();
        }

        SharedBufferVectorizer.CALLS.set(0);
        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            @SuppressWarnings("unchecked")
            final GigaMap<Doc>     map   = (GigaMap<Doc>)storage.root();
            final VectorIndex<Doc> index = index(map);
            final long             query = 0;
            assertEquals(query, index.search(map.get(query), 1).toList().get(0).entityId(),
                "the search must score the query entity's vector, not the last one the recovery vectorized");
            assertTrue(SharedBufferVectorizer.CALLS.get() > 1,
                "the search must have run the recovery, else this test checks nothing");
        }
    }

    @Test
    void vectorizerResult_isNotHeldByTheIndex(@TempDir final Path tempDir)
    {
        // The other half of a shared buffer: a caller changing the returned array afterwards changes nothing. The
        // vector file holds its own copy, so this guards that copy, not the one at the vectorizer boundary.
        final float[]      returned = new float[DIMENSION];
        final GigaMap<Doc> map      = GigaMap.New();
        try(final VectorIndex<Doc> index = map.index().register(VectorIndices.Category()).add(
            NAME, configuration(Mode.IN_MEMORY_SYNC, tempDir), new Vectorizer<Doc>()
            {
                @Override
                public float[] vectorize(final Doc entity)
                {
                    System.arraycopy(entity.vector, 0, returned, 0, DIMENSION);
                    return returned;
                }
            }
        ))
        {
            final Doc doc = docs(8).get(0);
            map.add(doc);
            Arrays.fill(returned, 0.5f);
            assertArrayEquals(doc.vector, index.getVector(0));
        }
    }
}
