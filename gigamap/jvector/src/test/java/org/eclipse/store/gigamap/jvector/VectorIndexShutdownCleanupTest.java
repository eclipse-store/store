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
import org.eclipse.store.gigamap.types.ScoredSearchResult;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The persist of a closing index cleans its frozen graph up without jVector's refinement, which took most of the
 * close of a large unflushed graph, while every other persist still refines. The segment the close writes must be
 * complete all the same: removed entities purged, every remaining one found after a restart.
 */
class VectorIndexShutdownCleanupTest
{
    private static final int DIMENSION = 32;
    private static final int COUNT     = 600;
    private static final int REMOVED   = 60;

    record Doc(String content, float[] embedding) {}

    static final class DocVectorizer extends Vectorizer<Doc>
    {
        @Override
        public float[] vectorize(final Doc entity)
        {
            return entity.embedding();
        }
    }

    @TempDir
    Path tempDir;

    private VectorIndex<Doc> register(final GigaMap<Doc> gigaMap)
    {
        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(DIMENSION)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .onDisk(true)
            .indexDirectory(this.tempDir.resolve("index"))
            .persistenceIntervalMs(0)
            .persistOnShutdown(true)
            .build();
        return gigaMap.index().register(VectorIndices.Category()).add("embeddings", config, new DocVectorizer());
    }

    private static float[] vector(
        final Random random,
        final int    dimension
    )
    {
        final float[] vector = new float[dimension];
        for(int i = 0; i < dimension; i++)
        {
            vector[i] = random.nextFloat() * 2 - 1;
        }
        return vector;
    }

    private static void addDocs(final GigaMap<Doc> gigaMap)
    {
        final Random random = new Random(42);
        for(int i = 0; i < COUNT; i++)
        {
            gigaMap.add(new Doc("doc_" + i, vector(random, DIMENSION)));
        }
    }

    @Test
    void close_persistOnShutdown_cleansUpWithoutRefinement()
    {
        final GigaMap<Doc>     gigaMap = GigaMap.New();
        final VectorIndex<Doc> index   = this.register(gigaMap);
        addDocs(gigaMap);
        final List<Boolean> refined = new CopyOnWriteArrayList<>();
        ((VectorIndex.Default<Doc>)index).cleanupTestHook = refined::add;

        index.close();

        assertEquals(List.of(false), refined);
        assertFalse(SegmentLayout.graphFiles(this.tempDir.resolve("index"), "embeddings").isEmpty());
    }

    @Test
    void persistToDisk_explicitPersist_cleansUpWithRefinement()
    {
        final GigaMap<Doc>     gigaMap = GigaMap.New();
        final VectorIndex<Doc> index   = this.register(gigaMap);
        addDocs(gigaMap);
        final List<Boolean> refined = new CopyOnWriteArrayList<>();
        ((VectorIndex.Default<Doc>)index).cleanupTestHook = refined::add;

        index.persistToDisk();

        assertEquals(List.of(true), refined);
        ((VectorIndex.Default<Doc>)index).cleanupTestHook = null;
        index.close();
    }

    @Test
    void close_withRemovedEntities_writesASegmentThatFindsEveryRemainingEntityAfterARestart()
    {
        final Path storageDir = this.tempDir.resolve("storage");
        final EmbeddedStorageManager written = EmbeddedStorage.start(GigaMap.New(), storageDir);
        final GigaMap<Doc>           map     = rootOf(written);
        final VectorIndex<Doc>       closing = this.register(map);
        addDocs(map);
        for(long id = 0; id < REMOVED; id++)
        {
            map.removeById(id);
        }
        written.storeRoot();
        final List<Boolean> refined = new CopyOnWriteArrayList<>();
        ((VectorIndex.Default<Doc>)closing).cleanupTestHook = refined::add;

        // the storage's shutdown closes the index, whose shutdown persist writes the unflushed graph.
        written.shutdown();

        assertEquals(List.of(false), refined);

        try(EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final GigaMap<Doc>             gigaMap = rootOf(storage);
            final VectorIndex.Default<Doc> index   =
                (VectorIndex.Default<Doc>)gigaMap.index().get(VectorIndices.Category()).get("embeddings");
            assertEquals(COUNT - REMOVED, gigaMap.size());

            gigaMap.iterateIndexed((id, doc) ->
            {
                final ScoredSearchResult.Entry<Doc> first = index.search(doc.embedding(), 1).iterator().next();
                assertEquals(id, first.entityId(), () -> doc.content() + " is its own nearest neighbour");
            });
            index.search(vector(new Random(7), DIMENSION), 20).forEach(entry ->
                assertFalse(entry.entityId() < REMOVED, () -> "removed entity " + entry.entityId())
            );
            assertEquals(1, index.sourceSet().segments().size(), "the index was loaded from the written segment");
        }
    }

    @SuppressWarnings("unchecked")
    private static GigaMap<Doc> rootOf(final EmbeddedStorageManager storage)
    {
        return (GigaMap<Doc>)storage.root();
    }
}
