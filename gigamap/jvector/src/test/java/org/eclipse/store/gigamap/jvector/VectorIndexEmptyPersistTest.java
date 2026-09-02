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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Persisting an index that holds no vectors must be a no-op instead of a failure: jvector's header
 * writer dereferences {@code view.entryNode()}, which is null for a graph with zero nodes, so a
 * write attempt used to fail with a NullPointerException: thrown to the caller of
 * {@code persistToDisk()}, and logged as an ERROR on every shutdown of such an index.
 */
class VectorIndexEmptyPersistTest
{
    private static final String INDEX_NAME = "embedding";
    private static final int    DIMENSION  = 8;

    record Document(String content, float[] embedding) {}

    static class DocumentVectorizer extends Vectorizer<Document>
    {
        @Override
        public float[] vectorize(final Document entity)
        {
            return entity.embedding();
        }
    }

    private static VectorIndexConfiguration configuration(
        final Path indexDirectory        ,
        final long optimizationIntervalMs
    )
    {
        return VectorIndexConfiguration.builder()
            .dimension(DIMENSION)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .onDisk(true)
            .indexDirectory(indexDirectory)
            .optimizationIntervalMs(optimizationIntervalMs)
            .build();
    }

    private static VectorIndex<Document> registerIndex(
        final GigaMap<Document> gigaMap       ,
        final Path              indexDirectory,
        final long              optimizationIntervalMs
    )
    {
        return gigaMap.index()
            .register(VectorIndices.Category())
            .add(INDEX_NAME, configuration(indexDirectory, optimizationIntervalMs), new DocumentVectorizer());
    }

    private static Document document(final int seed)
    {
        final float[] embedding = new float[DIMENSION];
        for(int i = 0; i < DIMENSION; i++)
        {
            embedding[i] = (i == seed % DIMENSION) ? 1.0f : 0.0f;
        }
        return new Document("doc_" + seed, embedding);
    }

    private static boolean indexFilesExist(final Path indexDirectory)
    {
        return Files.exists(indexDirectory.resolve(INDEX_NAME + DiskIndexManager.GRAPH_FILE_EXT))
            || Files.exists(indexDirectory.resolve(INDEX_NAME + DiskIndexManager.META_FILE_EXT ));
    }

    private static List<String> backgroundThreadNames()
    {
        return Thread.getAllStackTraces().keySet().stream()
            .map(Thread::getName)
            .filter(name -> name.startsWith("VectorIndex-Background-" + INDEX_NAME))
            .sorted()
            .toList();
    }

    @Test
    void persistToDisk_neverPopulatedIndex_writesNoFiles(@TempDir final Path tempDir)
    {
        final Path indexDirectory = tempDir.resolve("index");

        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(tempDir.resolve("storage")))
        {
            final GigaMap<Document> gigaMap = GigaMap.New();
            storage.setRoot(gigaMap);

            final VectorIndex<Document> index = registerIndex(gigaMap, indexDirectory, 0);
            assertEquals(0, gigaMap.size());

            index.persistToDisk();

            assertFalse(indexFilesExist(indexDirectory), "an empty index must not write index files");
        }
    }

    @Test
    void close_neverPopulatedIndex_persistsOnShutdownWithoutFailing(@TempDir final Path tempDir)
    {
        final Path indexDirectory = tempDir.resolve("index");
        final Path storageDirectory = tempDir.resolve("storage");

        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDirectory))
        {
            final GigaMap<Document> gigaMap = GigaMap.New();
            storage.setRoot(gigaMap);
            registerIndex(gigaMap, indexDirectory, 0);
            storage.storeRoot();
        }

        assertFalse(indexFilesExist(indexDirectory), "an empty index must not write index files on shutdown");

        // The index is still usable after the restart that follows such a shutdown.
        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDirectory))
        {
            final GigaMap<Document> gigaMap = storage.root();
            gigaMap.add(document(1));

            final VectorIndex<Document> index = gigaMap.index().get(VectorIndices.Category()).get(INDEX_NAME);
            assertFalse(index.search(document(1).embedding(), 10).isEmpty());
        }
    }

    @Test
    void persistToDisk_afterAllEntriesRemoved_removesStaleIndexFiles(@TempDir final Path tempDir)
    {
        final Path indexDirectory = tempDir.resolve("index");

        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(tempDir.resolve("storage")))
        {
            final GigaMap<Document> gigaMap = GigaMap.New();
            storage.setRoot(gigaMap);

            final VectorIndex<Document> index = registerIndex(gigaMap, indexDirectory, 0);
            for(int i = 0; i < 10; i++)
            {
                gigaMap.add(document(i));
            }
            storage.storeRoot();

            index.persistToDisk();
            assertTrue(indexFilesExist(indexDirectory), "a populated index must write index files");

            gigaMap.removeAll();
            storage.storeRoot();

            index.persistToDisk();

            assertFalse(
                indexFilesExist(indexDirectory),
                "files describing removed content must not survive the persist"
            );
        }
    }

    @Test
    void close_calledTwice_leavesNoBackgroundThreadBehind(@TempDir final Path tempDir)
    {
        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(tempDir.resolve("storage")))
        {
            final GigaMap<Document> gigaMap = GigaMap.New();
            storage.setRoot(gigaMap);

            // A configured optimization interval is what makes the resurrected manager observable
            // as a live thread; the re-initialization itself happens regardless of the interval.
            final VectorIndex<Document> index = registerIndex(gigaMap, tempDir.resolve("index"), 60_000);

            index.close();
            assertEquals(List.of(), backgroundThreadNames(), "close() must stop the background thread");

            index.close();
            assertEquals(List.of(), backgroundThreadNames(), "close() must not restart the background thread");
        }
    }
}
