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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Registering a vector index on a populated {@link GigaMap} is all-or-nothing (internal#143): the index is
 * back-filled before it is registered, so a vectorizer that throws for one entity leaves the group exactly as
 * it was - nothing registered, nothing persisted, no background thread - and a retry creates the index anew.
 * <p>
 * The failure is transient, like an embedding service timing out: the vectorizer throws for one entity only
 * while {@link #FAIL} is set.
 */
class VectorIndexRegistrationFailureTest
{
    private static final int    COUNT            = 10;
    private static final int    POISON           = 5;
    private static final String BG_THREAD_PREFIX = "VectorIndex-Background-";

    static final AtomicBoolean FAIL = new AtomicBoolean();

    static final class Doc
    {
        final int     no;
        final float[] embedding;

        Doc(final int no)
        {
            this.no        = no;
            this.embedding = new float[]{1.0f + no, 1.0f, (float)no * no};
        }
    }

    static final class FlakyVectorizer extends Vectorizer<Doc>
    {
        private final boolean embedded;

        /** embedded mode: the vector is read from the entity on demand */
        FlakyVectorizer()
        {
            this(true);
        }

        /** @param embedded {@code false} for computed mode, where the index stores the vectors itself */
        FlakyVectorizer(final boolean embedded)
        {
            this.embedded = embedded;
        }

        @Override
        public float[] vectorize(final Doc entity)
        {
            if(FAIL.get() && entity.no == POISON)
            {
                throw new IllegalStateException("simulated vectorizer failure for entity " + entity.no);
            }
            return entity.embedding;
        }

        @Override
        public boolean isEmbedded()
        {
            return this.embedded;
        }
    }

    private static VectorIndexConfiguration.Builder configBuilder()
    {
        return VectorIndexConfiguration.builder()
            .dimension(3)
            .similarityFunction(VectorSimilarityFunction.EUCLIDEAN);
    }

    private static GigaMap<Doc> populatedMap()
    {
        final GigaMap<Doc> map = GigaMap.New();
        for(int i = 0; i < COUNT; i++)
        {
            map.add(new Doc(i));
        }
        return map;
    }

    private static int hits(final VectorIndex<Doc> index)
    {
        return index.search(new float[]{1.0f, 1.0f, 0.0f}, COUNT * 2).size();
    }

    private static long backgroundThreads(final String indexName)
    {
        return Thread.getAllStackTraces().keySet().stream()
            .filter(t -> t.getName().equals(BG_THREAD_PREFIX + indexName))
            .count();
    }

    @AfterEach
    void reset()
    {
        FAIL.set(false);
    }

    @Test
    void failedAddRegistersNothing()
    {
        final GigaMap<Doc>       map = populatedMap();
        final VectorIndices<Doc> vi  = map.index().register(VectorIndices.Category());

        FAIL.set(true);
        assertThrows(IllegalStateException.class, () -> vi.add("emb", configBuilder().build(), new FlakyVectorizer()));

        assertNull(vi.get("emb"));
    }

    @Test
    void ensureAfterAFailedAddCreatesTheIndexAnew()
    {
        final GigaMap<Doc>       map = populatedMap();
        final VectorIndices<Doc> vi  = map.index().register(VectorIndices.Category());

        FAIL.set(true);
        assertThrows(IllegalStateException.class, () -> vi.ensure("emb", configBuilder().build(), new FlakyVectorizer()));
        FAIL.set(false);

        final VectorIndex<Doc> index = vi.ensure("emb", configBuilder().build(), new FlakyVectorizer());
        assertEquals(COUNT, hits(index));
    }

    @Test
    void failedAddOfAnInMemoryIndexPersistsNothing(@TempDir final Path dir)
    {
        assertNoIndexAfterFailedAddAndRestart(dir.resolve("storage"), configBuilder().build());
    }

    @Test
    void failedAddOfAnOnDiskIndexPersistsNothing(@TempDir final Path dir)
    {
        assertNoIndexAfterFailedAddAndRestart(
            dir.resolve("storage"),
            configBuilder().onDisk(true).indexDirectory(dir.resolve("index")).build()
        );
    }

    /**
     * Computed mode: the vectors the back-fill stored before the failure live in the discarded index's own
     * vector store, which must not be persisted either.
     */
    @Test
    void failedAddOfAComputedIndexPersistsNothing(@TempDir final Path dir)
    {
        assertNoIndexAfterFailedAddAndRestart(dir.resolve("storage"), configBuilder().build(), false);
    }

    private static void assertNoIndexAfterFailedAddAndRestart(
        final Path                     storageDir,
        final VectorIndexConfiguration config
    )
    {
        assertNoIndexAfterFailedAddAndRestart(storageDir, config, true);
    }

    /**
     * Fails an add() on a populated, stored map, then does unrelated work that stores the map, restarts, and
     * asserts that no index of that name came back.
     */
    private static void assertNoIndexAfterFailedAddAndRestart(
        final Path                     storageDir,
        final VectorIndexConfiguration config,
        final boolean                  embedded
    )
    {
        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final GigaMap<Doc> map = populatedMap();
            storage.setRoot(map);
            storage.storeRoot();
            final VectorIndices<Doc> vi = map.index().register(VectorIndices.Category());
            map.store();

            FAIL.set(true);
            assertThrows(IllegalStateException.class, () -> vi.add("emb", config, new FlakyVectorizer(embedded)));
            FAIL.set(false);

            map.add(new Doc(COUNT));
            map.store();
        }

        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final GigaMap<Doc> map = storage.root();
            assertEquals(COUNT + 1, map.size());
            assertNull(map.index().get(VectorIndices.class).get("emb"));
        }
    }

    @Test
    void failedAddWithEventualIndexingRegistersNothing()
    {
        final GigaMap<Doc>       map = populatedMap();
        final VectorIndices<Doc> vi  = map.index().register(VectorIndices.Category());

        FAIL.set(true);
        assertThrows(
            IllegalStateException.class,
            () -> vi.add("emb", configBuilder().eventualIndexing(true).build(), new FlakyVectorizer())
        );

        assertNull(vi.get("emb"));
    }

    @Test
    void addWithEventualIndexingOnAPopulatedMapIndexesEveryEntity()
    {
        final GigaMap<Doc>       map = populatedMap();
        final VectorIndices<Doc> vi  = map.index().register(VectorIndices.Category());
        try(final VectorIndex<Doc> index = vi.add("emb", configBuilder().eventualIndexing(true).build(), new FlakyVectorizer()))
        {
            index.optimize(); // drains the indexing queue
            assertEquals(COUNT, hits(index));
        }
    }

    /**
     * Computed mode with eventual indexing: the back-filled vectors reach the index's vector store only after
     * registration, and the worker builds the graph from there.
     */
    @Test
    void addWithEventualIndexingOfAComputedIndexOnAPopulatedMapIndexesEveryEntity()
    {
        final GigaMap<Doc>       map = populatedMap();
        final VectorIndices<Doc> vi  = map.index().register(VectorIndices.Category());
        try(final VectorIndex<Doc> index = vi.add("emb", configBuilder().eventualIndexing(true).build(), new FlakyVectorizer(false)))
        {
            index.optimize(); // drains the indexing queue
            assertEquals(COUNT, hits(index));
        }
    }

    /**
     * The background manager of a new index starts only once its back-fill succeeded, so a failed add leaves
     * no background thread behind.
     */
    @Test
    void failedAddStartsNoBackgroundThread()
    {
        final GigaMap<Doc>       map  = populatedMap();
        final VectorIndices<Doc> vi   = map.index().register(VectorIndices.Category());
        final String             name = "emb-no-thread";

        FAIL.set(true);
        assertThrows(
            IllegalStateException.class,
            () -> vi.add(name, configBuilder().eventualIndexing(true).build(), new FlakyVectorizer())
        );

        assertEquals(0, backgroundThreads(name));
    }

    /**
     * The back-fill runs before the background manager exists, so activation must hand its changes to the
     * manager: otherwise the change threshold of background persistence is never reached and the freshly
     * built on-disk graph is not persisted until the next change.
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void backfillOfAnOnDiskIndexIsPersistedInTheBackground(@TempDir final Path dir) throws InterruptedException
    {
        final Path               indexDir = dir.resolve("index");
        final GigaMap<Doc>       map      = populatedMap();
        final VectorIndices<Doc> vi       = map.index().register(VectorIndices.Category());
        final VectorIndexConfiguration config = configBuilder()
            .onDisk(true)
            .indexDirectory(indexDir)
            .persistenceIntervalMs(50L)
            .minChangesBetweenPersists(COUNT)
            .build();

        try(final VectorIndex<Doc> index = vi.add("emb", config, new FlakyVectorizer()))
        {
            assertNotNull(index);
            final Path graph = indexDir.resolve("emb.graph");
            final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            while(!Files.exists(graph) && System.nanoTime() < deadline)
            {
                Thread.sleep(50L);
            }
            assertTrue(Files.exists(graph), "background persistence did not write the back-filled graph");
        }
    }
}
