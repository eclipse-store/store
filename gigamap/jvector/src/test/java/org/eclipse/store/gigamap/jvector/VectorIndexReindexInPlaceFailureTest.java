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
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code GigaMap.reindex()} of a vector index that fails after every vector was validated: while the vectors are
 * rewritten in place, or while the graph is rebuilt from them. No entity may go missing: each keeps its new vector
 * or the one it had before, the vector count matches, the graph is rebuilt completely on the next use, and that
 * survives a store and a restart.
 * <p>
 * The restart tests use indices that keep their vector file, so that the restarted index serves the vectors the
 * reindex left rather than vectorizing the stored entities anew: an on-disk index, and a computed in-memory one with
 * an index directory. An embedded in-memory index never keeps its vector file.
 */
class VectorIndexReindexInPlaceFailureTest
{
    private static final int     COUNT     = 200;
    private static final int     POISON    = 100;
    // Moved before the reindex, which rewrites the entities in id order: EARLY before the failing entity, LATE after.
    private static final int     EARLY     = 10;
    private static final int     LATE      = 150;
    // Inside the data range, far from the entity's own position and held by no other entity.
    private static final float[] EARLY_POS = {25.5f, 4.0f, 12.0f, 2.0f};
    private static final float[] LATE_POS  = {60.5f, 4.0f, 29.75f, 2.0f};

    private static final String VECTORIZER_FAILURE = "simulated vectorizer failure for entity " + POISON;
    private static final String GRAPH_FAILURE      = "simulated graph failure";

    // Calls for POISON that still succeed once armed (the reindex validates every entity before it rewrites it);
    // negative while disarmed.
    private static final AtomicInteger poisonPasses = new AtomicInteger(-1);

    static class Doc
    {
        final int        no;
        volatile float[] vector;

        Doc(final int no)
        {
            this.no     = no;
            this.vector = position(no);
        }
    }

    static float[] position(final int no)
    {
        return new float[]{1.0f + no, 1.0f + no % 7, no * 0.5f, 1.0f + no % 3};
    }

    /**
     * Embedded: the reindex reads the vectors from the entities again while it rewrites them, so this one fails in
     * the rewrite once armed with one pass left.
     */
    static class FlakyEmbeddedVectorizer extends Vectorizer<Doc>
    {
        @Override
        public float[] vectorize(final Doc entity)
        {
            if(entity.no == POISON && poisonPasses.get() >= 0 && poisonPasses.getAndDecrement() == 0)
            {
                throw new IllegalStateException(VECTORIZER_FAILURE);
            }
            return entity.vector;
        }

        @Override
        public boolean isEmbedded()
        {
            return true;
        }
    }

    static class ComputedVectorizer extends Vectorizer<Doc>
    {
        @Override
        public float[] vectorize(final Doc entity)
        {
            return entity.vector.clone();
        }
    }

    /**
     * Embedded and, once armed, searching its own index when it vectorizes {@link #POISON} the second time, i.e. in
     * the rewrite of a reindex, e.g. to look up the neighbours of the entity it vectorizes.
     */
    static class SearchingVectorizer extends Vectorizer<Doc>
    {
        final AtomicInteger       poisonCalls = new AtomicInteger();
        volatile VectorIndex<Doc> index;
        volatile boolean          armed;

        void arm()
        {
            this.poisonCalls.set(0);
            this.armed = true;
        }

        @Override
        public float[] vectorize(final Doc entity)
        {
            if(this.armed && entity.no == POISON && this.poisonCalls.incrementAndGet() == 2)
            {
                this.index.search(entity.vector, 5);
            }
            return entity.vector;
        }

        @Override
        public boolean isEmbedded()
        {
            return true;
        }
    }

    @AfterEach
    void disarm()
    {
        poisonPasses.set(-1);
    }

    private static VectorIndexConfiguration inMemory()
    {
        return VectorIndexConfiguration.builder()
            .dimension(4)
            .similarityFunction(VectorSimilarityFunction.EUCLIDEAN)
            .build();
    }

    /**
     * In memory, but keeping the vector file of a computed index in the given directory across a restart.
     */
    private static VectorIndexConfiguration inMemoryKeepingVectors(final Path indexDir)
    {
        return VectorIndexConfiguration.builder()
            .dimension(4)
            .similarityFunction(VectorSimilarityFunction.EUCLIDEAN)
            .indexDirectory(indexDir)
            .build();
    }

    private static VectorIndexConfiguration onDisk(final Path indexDir)
    {
        return VectorIndexConfiguration.builder()
            .dimension(4)
            .similarityFunction(VectorSimilarityFunction.EUCLIDEAN)
            .onDisk(true)
            .indexDirectory(indexDir)
            .build();
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

    /**
     * Moves {@link #EARLY} and {@link #LATE} without telling the index, so only a reindex picks the change up.
     */
    private static void moveDirectly(final GigaMap<Doc> map)
    {
        map.get(EARLY).vector = EARLY_POS.clone();
        map.get(LATE).vector  = LATE_POS.clone();
    }

    /**
     * {@link #moveDirectly} and stores the moved entities, which {@code map.store()} does not write for a change
     * made to them directly.
     */
    private static void moveDirectlyAndStore(final GigaMap<Doc> map, final EmbeddedStorageManager storage)
    {
        moveDirectly(map);
        storage.storeAll(map.get(EARLY), map.get(LATE));
    }

    private static void assertReindexFails(final String message, final Executable reindex)
    {
        assertEquals(message, assertThrows(IllegalStateException.class, reindex).getMessage());
    }

    private static Set<Long> ids(final VectorIndex<Doc> index)
    {
        final Set<Long> ids = new TreeSet<>();
        index.search(position(0), COUNT * 2).toList().forEach(e -> ids.add(e.entityId()));
        return ids;
    }

    private static long topHit(final VectorIndex<Doc> index, final float[] query)
    {
        return index.search(query, 1).toList().get(0).entityId();
    }

    private static void assertComplete(final VectorIndex<Doc> index, final String when)
    {
        for(int i = 0; i < COUNT; i++)
        {
            assertNotNull(index.getVector(i), when + ": entity " + i + " has no vector");
        }
        assertEquals(COUNT, ((VectorIndex.Default<Doc>)index).getVectorCount(), when + ": wrong vector count");
        assertEquals(COUNT, ids(index).size(), when + ": entities are missing from the graph");
    }

    /**
     * Asserts the state a reindex failing at {@link #POISON} leaves: {@link #EARLY} rewritten, {@link #LATE} not.
     */
    private static void assertRewrittenUpToTheFailure(final VectorIndex<Doc> index, final String when)
    {
        assertArrayEquals(EARLY_POS, index.getVector(EARLY), when + ": rewritten before the failure, the new vector");
        assertArrayEquals(position(LATE), index.getVector(LATE), when + ": behind the failure, the vector from before");
        assertEquals(EARLY, topHit(index, EARLY_POS), when);
    }

    private static void assertBothMoved(final VectorIndex<Doc> index, final String when)
    {
        assertEquals(EARLY, topHit(index, EARLY_POS), when + ": the rewritten vector of " + EARLY + " is not used");
        assertEquals(LATE, topHit(index, LATE_POS), when + ": the rewritten vector of " + LATE + " is not used");
    }

    /**
     * Fails the graph rebuild once, partway through: in one of the threads that insert the nodes, after a third of
     * them were inserted.
     */
    private static void failGraphRebuildPartway(final VectorIndex<Doc> index)
    {
        final AtomicInteger inserted = new AtomicInteger();
        ((VectorIndex.Default<Doc>)index).rebuildWorkerTestHook = () ->
        {
            if(inserted.incrementAndGet() == COUNT / 3)
            {
                throw new IllegalStateException(GRAPH_FAILURE);
            }
        };
    }

    private static void clearGraphFailure(final VectorIndex<Doc> index)
    {
        ((VectorIndex.Default<Doc>)index).rebuildWorkerTestHook = null;
    }

    @Test
    void embeddedVectorizerFailingWhileRewritingKeepsEveryEntity()
    {
        final GigaMap<Doc>     map   = populatedMap();
        final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
            .add("emb", inMemory(), new FlakyEmbeddedVectorizer());
        moveDirectly(map);

        poisonPasses.set(1);
        assertReindexFails(VECTORIZER_FAILURE, map::reindex);

        assertComplete(index, "after the failed reindex");
        assertRewrittenUpToTheFailure(index, "after the failed reindex");

        map.reindex();
        assertComplete(index, "after a reindex that succeeds");
        assertBothMoved(index, "after a reindex that succeeds");
    }

    @Test
    void embeddedVectorizerFailingWhileRewritingOnDiskSurvivesStoreAndRestart(@TempDir final Path tempDir)
    {
        final Path storageDir = tempDir.resolve("storage");
        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final GigaMap<Doc> map = populatedMap();
            storage.setRoot(map);
            storage.storeRoot();
            final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
                .add("emb", onDisk(tempDir.resolve("index")), new FlakyEmbeddedVectorizer());
            map.store();
            index.persistToDisk();

            moveDirectlyAndStore(map, storage);
            poisonPasses.set(1);
            assertReindexFails(VECTORIZER_FAILURE, map::reindex);
            poisonPasses.set(-1);
            assertComplete(index, "after the failed reindex");
            map.store();
        }

        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final GigaMap<Doc>     map   = storage.root();
            final VectorIndex<Doc> index = map.index().get(VectorIndices.class).get("emb");
            assertComplete(index, "after a restart");
            assertRewrittenUpToTheFailure(index, "after a restart");

            // the reindex is completed by calling it again
            map.reindex();
            assertComplete(index, "after a reindex that succeeds");
            assertBothMoved(index, "after a reindex that succeeds");
        }
    }

    @Test
    void graphRebuildFailureKeepsEveryEntity()
    {
        final GigaMap<Doc>     map   = populatedMap();
        final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
            .add("emb", inMemory(), new ComputedVectorizer());
        moveDirectly(map);

        failGraphRebuildPartway(index);
        assertReindexFails(GRAPH_FAILURE, map::reindex);
        clearGraphFailure(index);

        // the vectors were all rewritten before the graph failed; the next search rebuilds it from them
        assertComplete(index, "after the failed graph rebuild");
        assertBothMoved(index, "after the failed graph rebuild");
    }

    @Test
    void graphRebuildFailureSurvivesStoreAndRestartInMemory(@TempDir final Path tempDir)
    {
        this.graphRebuildFailureSurvivesStoreAndRestart(tempDir, inMemoryKeepingVectors(tempDir.resolve("index")));
    }

    @Test
    void graphRebuildFailureSurvivesStoreAndRestartOnDisk(@TempDir final Path tempDir)
    {
        this.graphRebuildFailureSurvivesStoreAndRestart(tempDir, onDisk(tempDir.resolve("index")));
    }

    private void graphRebuildFailureSurvivesStoreAndRestart(
        final Path                     tempDir      ,
        final VectorIndexConfiguration configuration
    )
    {
        final Path storageDir = tempDir.resolve("storage");
        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final GigaMap<Doc> map = populatedMap();
            storage.setRoot(map);
            storage.storeRoot();
            final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
                .add("emb", configuration, new ComputedVectorizer());
            map.store();
            if(configuration.onDisk())
            {
                index.persistToDisk();
            }

            moveDirectlyAndStore(map, storage);
            failGraphRebuildPartway(index);
            assertReindexFails(GRAPH_FAILURE, map::reindex);
            clearGraphFailure(index);
            // stored before anything used the index again, so before its graph was rebuilt
            map.store();
        }

        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final GigaMap<Doc>     map   = storage.root();
            final VectorIndex<Doc> index = map.index().get(VectorIndices.class).get("emb");
            assertComplete(index, "after a restart");
            assertBothMoved(index, "after a restart");
        }
    }

    /**
     * A search from within the vectorizer while the vectors are rewritten would build the graph from a partly
     * rewritten vector file. It is rejected, which fails the reindex, and the graph is rebuilt from the file on the
     * next use.
     */
    @Test
    void searchFromWithinTheVectorizerFailsTheReindex()
    {
        final GigaMap<Doc>        map        = populatedMap();
        final SearchingVectorizer vectorizer = new SearchingVectorizer();
        final VectorIndex<Doc>    index      = map.index().register(VectorIndices.Category())
            .add("emb", inMemory(), vectorizer);
        vectorizer.index = index;
        moveDirectly(map);

        vectorizer.arm();
        final IllegalStateException rejection = assertThrows(IllegalStateException.class, map::reindex);
        vectorizer.armed = false;
        assertTrue(rejection.getMessage().startsWith("Vector index \"emb\" is being reindexed"),
            "not the rejection of the search: " + rejection.getMessage());

        assertComplete(index, "after the rejected search");
        assertRewrittenUpToTheFailure(index, "after the rejected search");

        map.reindex();
        assertComplete(index, "after a reindex that succeeds");
        assertBothMoved(index, "after a reindex that succeeds");
    }
}
