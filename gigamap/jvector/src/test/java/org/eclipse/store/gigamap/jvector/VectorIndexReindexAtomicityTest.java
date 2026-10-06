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
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code GigaMap.reindex()} of vector indices: a throwing vectorizer must leave every index complete, an on-disk
 * index must be rebuilt from the current entities rather than reloaded from its persisted graph, and a persist must
 * not write a graph that misses a node its {@code .meta} witnesses count.
 * <p>
 * The persist test builds its interleaving deterministically (a search held inside scoring keeps the read lock) on
 * daemon threads joined with a timeout, so a regression fails instead of hanging the build. It runs last: a hung
 * persist leaves graph-cleanup tasks blocked on {@code ForkJoinPool.commonPool}.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class VectorIndexReindexAtomicityTest
{
    private static final int     COUNT      = 50;
    private static final int     PQ_COUNT   = 300; // PQ training needs at least 256 vectors
    private static final int     MOVED      = 3;
    private static final int     POISON     = 5;
    // inside the data range but held by no other entity: an outlier query may not reach its node in a top-k search
    private static final float[] NEW_POS    = {25.5f, 4.0f, 12.0f, 2.0f};
    private static final long    TIMEOUT_MS = 10_000;

    private static volatile boolean failPoison;

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

    static class EmbeddedVectorizer extends Vectorizer<Doc>
    {
        @Override
        public float[] vectorize(final Doc entity)
        {
            return entity.vector;
        }

        @Override
        public boolean isEmbedded()
        {
            return true;
        }
    }

    /**
     * Computed mode: the index stores a copy of each vector and scores from it, so an index that was not rebuilt
     * keeps answering with the old vector of a directly mutated entity.
     */
    static class ComputedVectorizer extends Vectorizer<Doc>
    {
        @Override
        public float[] vectorize(final Doc entity)
        {
            return entity.vector.clone();
        }
    }

    /**
     * Throws for entity {@link #POISON} while {@link #failPoison} is set.
     */
    static class FlakyVectorizer extends ComputedVectorizer
    {
        @Override
        public float[] vectorize(final Doc entity)
        {
            if(failPoison && entity.no == POISON)
            {
                throw new IllegalStateException("simulated vectorizer failure for entity " + entity.no);
            }
            return super.vectorize(entity);
        }
    }

    @AfterEach
    void reset()
    {
        failPoison = false;
    }

    private static VectorIndexConfiguration inMemory()
    {
        return VectorIndexConfiguration.builder()
            .dimension(4)
            .similarityFunction(VectorSimilarityFunction.EUCLIDEAN)
            .build();
    }

    private static VectorIndexConfiguration onDisk(final Path indexDir, final ApproximateScoring scoring)
    {
        final VectorIndexConfiguration.Builder builder = VectorIndexConfiguration.builder()
            .dimension(4)
            .similarityFunction(VectorSimilarityFunction.EUCLIDEAN)
            .onDisk(true)
            .indexDirectory(indexDir)
            .approximateScoring(scoring);
        if(scoring != ApproximateScoring.NONE)
        {
            builder.pqSubspaces(2);
        }
        return builder.build();
    }

    private static GigaMap<Doc> populatedMap(final int count)
    {
        final GigaMap<Doc> map = GigaMap.New();
        for(int i = 0; i < count; i++)
        {
            map.add(new Doc(i));
        }
        return map;
    }

    private static Set<Long> ids(final VectorIndex<Doc> index, final int count)
    {
        final Set<Long> ids = new TreeSet<>();
        index.search(position(0), count * 2).toList().forEach(e -> ids.add(e.entityId()));
        return ids;
    }

    private static long topHit(final VectorIndex<Doc> index, final float[] query)
    {
        return index.search(query, 1).toList().get(0).entityId();
    }

    /**
     * Score of {@link #MOVED} for a query at its old position, or -1 if it is not among the top hits.
     */
    private static float scoreOfMovedAtOldPosition(final VectorIndex<Doc> index)
    {
        return index.search(position(MOVED), 5).toList().stream()
            .filter(e -> e.entityId() == MOVED)
            .map(e -> e.score())
            .findFirst()
            .orElse(-1.0f);
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

    private static boolean isIncrementalMode(final VectorIndex<?> index)
    {
        return internalState(index, "incrementalMode");
    }

    /**
     * A vectorizer throwing for one entity leaves its index as it was, and does not keep the sibling index from
     * being rebuilt. Before, the group default dropped every index first and re-added per entity, so the failure
     * truncated both.
     * <p>
     * The failing index is registered first, so it is also rebuilt first: the sibling is only rebuilt if the
     * failure does not end the rebuild of the group.
     */
    @Test
    void failingVectorizerLeavesItsIndexAndRebuildsTheSibling()
    {
        final GigaMap<Doc> map = populatedMap(COUNT);
        final VectorIndices<Doc> indices = map.index().register(VectorIndices.Category());
        final VectorIndex<Doc> flaky   = indices.add("flaky", inMemory(), new FlakyVectorizer());
        final VectorIndex<Doc> healthy = indices.add("healthy", inMemory(), new ComputedVectorizer());

        map.get(MOVED).vector = NEW_POS.clone();
        failPoison = true;
        assertThrows(IllegalStateException.class, map::reindex);
        failPoison = false;

        assertEquals(COUNT, ids(flaky, COUNT).size(), "the failing index lost entities");
        // computed mode keeps the stored vectors, so an unchanged index still holds the moved entity's old one
        assertEquals(MOVED, topHit(flaky, position(MOVED)), "the failing index was changed");
        assertEquals(COUNT, ids(healthy, COUNT).size(), "the sibling index lost entities");
        assertEquals(MOVED, topHit(healthy, NEW_POS), "the sibling index was not rebuilt from the current state");
    }

    /**
     * Computed mode: the vectors are stored with the map, so a truncation would survive store() and a restart.
     */
    @Test
    void failedComputedReindexSurvivesStoreAndRestart(@TempDir final Path tempDir)
    {
        final Path storageDir = tempDir.resolve("storage");
        try(EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final GigaMap<Doc> map = populatedMap(COUNT);
            storage.setRoot(map);
            storage.storeRoot();
            map.index().register(VectorIndices.Category()).add("emb", inMemory(), new FlakyVectorizer());
            map.store();

            failPoison = true;
            assertThrows(IllegalStateException.class, map::reindex);
            failPoison = false;
            map.store();
        }

        try(EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final GigaMap<Doc>     map   = storage.root();
            final VectorIndex<Doc> index = map.index().get(VectorIndices.class).get("emb");
            assertEquals(COUNT, ids(index, COUNT).size(), "the failed reindex left a truncated, stored index");
        }
    }

    /**
     * An on-disk index must not reload the graph it persisted before the reindex. With FusedPQ the reloaded disk
     * graph scores from its own stored codes, so the moved entity was still reported at its old position.
     */
    @Test
    void onDiskReindexDoesNotReadoptThePersistedGraph(@TempDir final Path tempDir)
    {
        final GigaMap<Doc> map = populatedMap(PQ_COUNT);
        final VectorIndices<Doc> indices = map.index().register(VectorIndices.Category());
        try(final VectorIndex<Doc> index = indices.add(
            "emb", onDisk(tempDir.resolve("index"), ApproximateScoring.FUSED_PQ), new EmbeddedVectorizer()))
        {
            index.persistToDisk();
            assertTrue(isIncrementalMode(index), "precondition: the persisted graph is in use");

            map.get(MOVED).vector = NEW_POS.clone();
            map.reindex();

            assertFalse(isIncrementalMode(index), "reindex() reloaded the persisted disk graph");
            final float staleScore = scoreOfMovedAtOldPosition(index);
            assertTrue(staleScore < 0.5f, "entity " + MOVED + " is still found at its old position (score "
                + staleScore + "): the persisted disk graph was re-adopted");
            // top 3 rather than top 1: the PQ codebook is trained without a fixed seed
            assertTrue(index.search(NEW_POS, 3).toList().stream().anyMatch(e -> e.entityId() == MOVED),
                "entity " + MOVED + " is not found at its new position");
        }
    }

    /**
     * The same after a restart, where the graph was loaded from disk on first access.
     */
    @Test
    void onDiskReindexAfterRestartDoesNotReadoptThePersistedGraph(@TempDir final Path tempDir)
    {
        final Path storageDir = tempDir.resolve("storage");
        final Path indexDir   = tempDir.resolve("index");
        try(EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final GigaMap<Doc> map = populatedMap(COUNT);
            storage.setRoot(map);
            storage.storeRoot();
            final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
                .add("emb", onDisk(indexDir, ApproximateScoring.NONE), new EmbeddedVectorizer());
            map.store();
            index.persistToDisk();
        }

        try(EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final GigaMap<Doc>     map   = storage.root();
            final VectorIndex<Doc> index = map.index().get(VectorIndices.class).get("emb");
            index.search(NEW_POS, 1); // initializes the index from disk
            assertTrue(isIncrementalMode(index), "precondition: the persisted graph was loaded");

            map.get(MOVED).vector = NEW_POS.clone();
            map.reindex();

            assertFalse(isIncrementalMode(index), "reindex() reloaded the persisted disk graph");
            assertEquals(MOVED, topHit(index, NEW_POS));
            assertEquals(COUNT, ids(index, COUNT).size());
        }
    }

    /**
     * Embedded vectorizer that pauses one designated thread on its first call, so that thread can be held inside a
     * search's scoring (holding the read lock).
     */
    static class PausableVectorizer extends EmbeddedVectorizer
    {
        // transient: the vectorizer is stored with the index, these are not persistable
        transient volatile Thread      pauseThread;
        transient final    CountDownLatch paused = new CountDownLatch(1);
        transient final    CountDownLatch resume = new CountDownLatch(1);

        @Override
        public float[] vectorize(final Doc entity)
        {
            if(Thread.currentThread() == this.pauseThread)
            {
                this.pauseThread = null;
                this.paused.countDown();
                try
                {
                    this.resume.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);
                }
                catch(final InterruptedException e)
                {
                    Thread.currentThread().interrupt();
                }
            }
            return super.vectorize(entity);
        }
    }

    /**
     * A persist sets {@code cleanupInProgress} before it waits for the write lock. An add() in that window defers
     * its graph op but counts at once in the {@code .meta} witnesses, so the persist must apply the op before it
     * captures the graph: otherwise the written files claim to be current, a restart accepts them, and the entity
     * is missing.
     */
    @Test
    @Order(Integer.MAX_VALUE)
    void addDeferredWhileAPersistWaitsIsPersisted(@TempDir final Path tempDir) throws Exception
    {
        final Path storageDir = tempDir.resolve("storage");
        final Doc  late       = new Doc(COUNT + 7);
        final long lateId;
        try(EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final GigaMap<Doc> map = populatedMap(COUNT);
            storage.setRoot(map);
            storage.storeRoot();
            final PausableVectorizer vectorizer = new PausableVectorizer();
            final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
                .add("emb", onDisk(tempDir.resolve("index"), ApproximateScoring.NONE), vectorizer);
            map.store();

            final AtomicReference<Throwable> persistFailure = new AtomicReference<>();
            final Thread searcher  = daemon("searcher", () -> index.search(position(10), 5));
            final Thread persister = daemon("persister", () ->
            {
                try
                {
                    index.persistToDisk();
                }
                catch(final Throwable t)
                {
                    persistFailure.set(t);
                }
            });
            try
            {
                // a search holds the read lock, paused inside scoring
                vectorizer.pauseThread = searcher;
                searcher.start();
                assertTrue(vectorizer.paused.await(TIMEOUT_MS, TimeUnit.MILLISECONDS), "search did not reach scoring");

                // the persist sets cleanupInProgress and queues on the write lock
                persister.start();
                awaitQueued(internalState(index, "builderLock"), persister);

                // a synchronous add in that window: its graph op is deferred
                lateId = map.add(late);
                final Queue<?> deferred = internalState(index, "deferredBuilderOps");
                assertEquals(1, deferred.size(), "precondition: the add's graph op was deferred");
            }
            finally
            {
                // also when the test failed: a held search would block the storage shutdown
                vectorizer.resume.countDown();
            }
            searcher.join(TIMEOUT_MS);
            persister.join(TIMEOUT_MS);
            assertFalse(persister.isAlive(), "the persist did not finish");
            assertNull(persistFailure.get(), "the persist failed");
            map.store();
        }

        try(EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final GigaMap<Doc>     map   = storage.root();
            final VectorIndex<Doc> index = map.index().get(VectorIndices.class).get("emb");
            index.search(late.vector, 1); // initializes the index from disk
            assertTrue(isIncrementalMode(index), "precondition: the written files were accepted after the restart");
            assertTrue(
                index.search(late.vector, 3).toList().stream().anyMatch(e -> e.entityId() == lateId && e.score() > 0.99f),
                "the entity added while the persist waited for the write lock is missing after a restart");
        }
    }

    private static Thread daemon(final String name, final Runnable action)
    {
        final Thread thread = new Thread(action, name);
        thread.setDaemon(true);
        return thread;
    }

    private static void awaitQueued(final ReentrantReadWriteLock lock, final Thread thread) throws InterruptedException
    {
        final long until = System.currentTimeMillis() + TIMEOUT_MS;
        while(!lock.hasQueuedThread(thread) && System.currentTimeMillis() < until)
        {
            Thread.sleep(5);
        }
        assertTrue(lock.hasQueuedThread(thread), thread.getName() + " did not queue on the write lock");
    }
}
