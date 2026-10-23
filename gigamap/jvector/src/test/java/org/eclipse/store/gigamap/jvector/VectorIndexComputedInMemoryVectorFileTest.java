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
import org.eclipse.store.gigamap.types.IndexLocation;
import org.eclipse.store.gigamap.types.IndexLocations;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An in-memory index with a computed vectorizer keeps its vector file in the index directory when one is
 * configured (see {@link VectorIndex#keepsIndexFiles()}): a restart rebuilds the graph from that file and calls
 * the vectorizer for no entity. Without a directory the file is temporary, as for an embedded index, and every
 * start vectorizes every entity. The alternative, re-vectorizing at every start, is one request per entity for
 * a vectorizer backed by an embedding service, which Eclipse Store never did before the vector file.
 */
class VectorIndexComputedInMemoryVectorFileTest
{
    private static final int    COUNT     = 50;
    private static final int    DIMENSION = 4;
    private static final String LOCATION  = "computed-in-memory-vectors";

    static final AtomicInteger VECTORIZED = new AtomicInteger();

    static class Doc
    {
        final int no;

        Doc(final int no)
        {
            this.no = no;
        }
    }

    static class CountingVectorizer extends Vectorizer<Doc>
    {
        @Override
        public float[] vectorize(final Doc entity)
        {
            VECTORIZED.incrementAndGet();
            return position(entity.no);
        }
    }

    static float[] position(final int no)
    {
        return new float[]{1.0f + no, 1.0f + no % 7, no * 0.5f, 1.0f + no % 3};
    }

    @AfterEach
    void unbind()
    {
        IndexLocations.unbind(LOCATION);
        VECTORIZED.set(0);
    }

    private static VectorIndexConfiguration inMemory(final Path indexDirectory)
    {
        final VectorIndexConfiguration.Builder builder = VectorIndexConfiguration.builder()
            .dimension(DIMENSION)
            .similarityFunction(VectorSimilarityFunction.EUCLIDEAN);
        if(indexDirectory != null)
        {
            builder.indexDirectory(indexDirectory);
        }
        return builder.build();
    }

    @SuppressWarnings("unchecked")
    private static GigaMap<Doc> rootOf(final EmbeddedStorageManager storage)
    {
        return (GigaMap<Doc>)storage.root();
    }

    @SuppressWarnings("unchecked")
    private static VectorIndex<Doc> index(final GigaMap<Doc> map)
    {
        return map.index().get(VectorIndices.class).get("emb");
    }

    private static void assertEveryEntityFound(final VectorIndex<Doc> index)
    {
        // A beam over every node: what this checks is that every entity is indexed with its vector, not recall.
        final int beam = COUNT + 50;
        for(int no = 0; no < COUNT; no++)
        {
            final int expected = no;
            assertTrue(index.search(position(no), 1, beam).toList().stream().anyMatch(e -> e.entity().no == expected),
                "entity " + no + " is not found by its own vector");
        }
    }

    private static void populate(final Path storageDir, final VectorIndexConfiguration configuration)
    {
        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final GigaMap<Doc> map = GigaMap.New();
            storage.setRoot(map);
            map.index().register(VectorIndices.Category()).add("emb", configuration, new CountingVectorizer());
            for(int no = 0; no < COUNT; no++)
            {
                map.add(new Doc(no));
            }
            storage.storeRoot();
            assertEquals(COUNT, VECTORIZED.get(), "one vectorizer call per added entity");
            assertEveryEntityFound(index(map));
        }
    }

    @Test
    void withDirectory_restartRebuildsFromTheVectorFile_callsNoVectorizer(@TempDir final Path tempDir)
    {
        final Path storageDir = tempDir.resolve("storage");
        final Path indexDir   = tempDir.resolve("index");
        populate(storageDir, inMemory(indexDir));
        assertTrue(Files.isRegularFile(indexDir.resolve("emb.vec")), "the vector file lives in the index directory");

        VECTORIZED.set(0);
        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final GigaMap<Doc>     map   = rootOf(storage);
            final VectorIndex<Doc> index = index(map);
            assertTrue(index.keepsIndexFiles());
            assertEquals(indexDir, index.indexDirectory());
            assertEveryEntityFound(index);
            assertEquals(0, VECTORIZED.get(), "the restart must read the vectors from the file, not vectorize");

            // and the file keeps serving mutations made after the restart
            final long added = map.add(new Doc(COUNT));
            assertEquals(1, VECTORIZED.get());
            assertTrue(index.search(position(COUNT), 1).toList().stream().anyMatch(e -> e.entityId() == added));
        }
    }

    @Test
    void withNamedLocation_restartRebuildsFromTheVectorFile_callsNoVectorizer(@TempDir final Path tempDir)
    {
        final Path storageDir = tempDir.resolve("storage");
        final Path indexDir   = tempDir.resolve("index");
        IndexLocations.bind(LOCATION, indexDir);
        populate(storageDir, inMemory(null).withIndexLocation(IndexLocation.Named(LOCATION)));
        assertTrue(Files.isRegularFile(indexDir.resolve("emb.vec")));

        // moved: the name points elsewhere, and the files go with it
        final Path moved = tempDir.resolve("moved");
        try
        {
            Files.createDirectories(moved);
            try(final var files = Files.list(indexDir))
            {
                for(final Path file : files.toList())
                {
                    Files.copy(file, moved.resolve(file.getFileName()));
                }
            }
        }
        catch(final java.io.IOException e)
        {
            throw new java.io.UncheckedIOException(e);
        }
        IndexLocations.bind(LOCATION, moved);

        VECTORIZED.set(0);
        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final VectorIndex<Doc> index = index(rootOf(storage));
            assertEquals(moved, index.indexDirectory());
            assertEveryEntityFound(index);
            assertEquals(0, VECTORIZED.get(), "the moved vector file must be used, not a rebuild from the entities");
        }
    }

    @Test
    void withoutDirectory_restartVectorizesEveryEntity(@TempDir final Path tempDir)
    {
        final Path storageDir = tempDir.resolve("storage");
        populate(storageDir, inMemory(null));

        VECTORIZED.set(0);
        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final GigaMap<Doc>     map   = rootOf(storage);
            final VectorIndex<Doc> index = index(map);
            assertFalse(index.keepsIndexFiles());
            assertEveryEntityFound(index);
            assertEquals(COUNT, VECTORIZED.get(), "a temporary vector file is rebuilt from the entities at every start");

            // and it has no location to change
            assertThrows(IllegalStateException.class, () -> map.index().get(VectorIndices.class)
                .changeIndexLocation("emb", IndexLocation.Named(LOCATION)));
        }
    }
}
