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

import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code GigaMap.reindex()} of vector indices: a throwing vectorizer must leave every index complete, an on-disk
 * index must be rebuilt from the current entities rather than reloaded from its persisted graph, and a persist must
 * not write a graph that misses a node its {@code .meta} witnesses count, also when the deferred op behind that node
 * failed.
 * <p>
 * The persist tests build their interleaving deterministically (a search held inside scoring keeps the read lock) on
 * daemon threads joined with a timeout, so a regression fails instead of hanging the build. They run last: a hung
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
     * Embedded, throws for entity {@link #POISON} while {@link #failPoison} is set.
     */
    static class FlakyEmbeddedVectorizer extends EmbeddedVectorizer
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

    /**
     * Whether the index answers from a persisted segment: a reindex or a failed consolidation must not drop or
     * re-adopt one, and a persist must write one.
     */
    private static boolean isServingFromSegments(final VectorIndex<?> index)
    {
        final SourceSet set = ((VectorIndex.Default<?>)index).sourceSet();
        return set != null && !set.segments().isEmpty();
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
     * The same with an embedded failing index, whose reindex validates the vectors first and reads them from the
     * entities again while rewriting them, instead of keeping a copy of every vector.
     */
    @Test
    void failingEmbeddedVectorizerLeavesItsIndexAndRebuildsTheSibling()
    {
        final GigaMap<Doc> map = populatedMap(COUNT);
        final VectorIndices<Doc> indices = map.index().register(VectorIndices.Category());
        final VectorIndex<Doc> flaky   = indices.add("flaky", inMemory(), new FlakyEmbeddedVectorizer());
        final VectorIndex<Doc> healthy = indices.add("healthy", inMemory(), new ComputedVectorizer());

        map.get(MOVED).vector = NEW_POS.clone();
        failPoison = true;
        assertThrows(IllegalStateException.class, map::reindex);
        failPoison = false;

        assertEquals(COUNT, ids(flaky, COUNT).size(), "the failing index lost entities");
        assertEquals(COUNT, ids(healthy, COUNT).size(), "the sibling index lost entities");
        assertEquals(MOVED, topHit(healthy, NEW_POS), "the sibling index was not rebuilt from the current state");

        // and a reindex that succeeds afterwards rebuilds the embedded index from the entities
        map.reindex();
        assertEquals(COUNT, ids(flaky, COUNT).size());
        assertEquals(MOVED, topHit(flaky, NEW_POS));
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
            assertTrue(isServingFromSegments(index), "precondition: the persisted graph is in use");

            map.get(MOVED).vector = NEW_POS.clone();
            map.reindex();

            assertFalse(isServingFromSegments(index), "reindex() reloaded the persisted disk graph");
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
            assertTrue(isServingFromSegments(index), "precondition: the persisted graph was loaded");

            map.get(MOVED).vector = NEW_POS.clone();
            map.reindex();

            assertFalse(isServingFromSegments(index), "reindex() reloaded the persisted disk graph");
            assertEquals(MOVED, topHit(index, NEW_POS));
            assertEquals(COUNT, ids(index, COUNT).size());
        }
    }

    /**
     * Embedded vectorizer that pauses one designated thread on its first call, so that thread can be held inside a
     * search's scoring (holding the read lock), and that throws on another designated thread while failures are
     * left.
     */
    static class PausableVectorizer extends EmbeddedVectorizer
    {
        // transient: the vectorizer is stored with the index, these are not persistable
        transient volatile Thread      pauseThread;
        transient final    CountDownLatch paused = new CountDownLatch(1);
        transient final    CountDownLatch resume = new CountDownLatch(1);
        transient volatile Thread      failThread;
        transient volatile int         failuresLeft;
        transient volatile boolean     failWithError;
        transient volatile int         skipCallsBeforeFailing; // calls of failThread that pass before the failures

        @Override
        public float[] vectorize(final Doc entity)
        {
            final Thread current = Thread.currentThread();
            if(current == this.pauseThread)
            {
                this.pauseThread = null;
                this.paused.countDown();
                try
                {
                    this.resume.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);
                }
                catch(final InterruptedException e)
                {
                    current.interrupt();
                }
            }
            if(current == this.failThread && this.failuresLeft > 0)
            {
                if(this.skipCallsBeforeFailing > 0)
                {
                    this.skipCallsBeforeFailing--;
                    return super.vectorize(entity);
                }
                this.failuresLeft--;
                if(this.failWithError)
                {
                    throw new Error("simulated vectorizer error on " + current.getName());
                }
                throw new IllegalStateException("simulated vectorizer failure on " + current.getName());
            }
            return super.vectorize(entity);
        }
    }


    private static boolean foundExactly(final VectorIndex<Doc> index, final Doc doc, final long id)
    {
        return index.search(doc.vector, 3).toList().stream().anyMatch(e -> e.entityId() == id && e.score() > 0.99f);
    }

    // The tests of the deferral protocol (a sync mutation deferred while a persist waited for the write lock, its
    // failed op forcing a rebuild before the persist captured the graph) went with that protocol: a persist now
    // freezes the live graph and writes it as a segment with no lock held, mutations insert into the live graph
    // at once, and a failed graph operation makes the next persist consolidate (VectorIndexPersistWindowConcurrencyTest,
    // VectorIndexEventualIndexingTest, VectorIndexParallelRebuildTest#consolidation_workerFailure_keepsTheSourcesAndRetries).
    // What stays here is the property those tests shared with the two below: a persist whose rebuild fails publishes
    // nothing and leaves the index serving complete results.

    /**
     * A persist that has to consolidate (a graph operation failed since the last persist, which only a rebuild
     * from the store repairs; the index holds a segment and entities added since) fails while the new graph is
     * built: a rebuild worker throws. (The rebuild reads the vectors from the vector file and scores from them,
     * so the vectorizer cannot fail it.) The consolidation must publish nothing: the new, partial graph is
     * discarded, the index keeps serving complete results from the segment plus the live graph, and the next
     * persist consolidates and succeeds.
     */
    @Test
    @Order(Integer.MAX_VALUE)
    void failedConsolidationRebuildKeepsTheSources(@TempDir final Path tempDir) throws Exception
    {
        assertFailedConsolidationPublishesNothing(tempDir, "consolidate");
    }

    private static final int ADDED = 5;

    private static void assertFailedConsolidationPublishesNothing(
        final Path   tempDir      ,
        final String failingMethod
    )
        throws Exception
    {
        final Path  storageDir = tempDir.resolve("storage");
        final Doc[] added      = new Doc[ADDED];
        final long  firstAddedId;
        try(EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final GigaMap<Doc> map = populatedMap(COUNT);
            storage.setRoot(map);
            storage.storeRoot();
            final PausableVectorizer vectorizer = new PausableVectorizer();
            final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
                .add("emb", onDisk(tempDir.resolve("index"), ApproximateScoring.NONE), vectorizer);
            map.store();
            index.persistToDisk();
            assertTrue(isServingFromSegments(index), "precondition: the first persist wrote a segment");

            firstAddedId = map.add(added[0] = new Doc(COUNT));
            for(int i = 1; i < ADDED; i++)
            {
                map.add(added[i] = new Doc(COUNT + i));
            }
            map.store();

            // the next persist has to consolidate, and fails doing so
            final VectorIndex.Default<Doc> def = (VectorIndex.Default<Doc>)index;
            def.graphOperationFailed(new IllegalStateException("simulated graph operation failure"));
            final AtomicReference<Throwable> persistFailure = new AtomicReference<>();
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
            def.rebuildWorkerTestHook = () ->
            {
                throw new IllegalStateException("simulated worker failure on persister");
            };
            persister.start();
            persister.join(TIMEOUT_MS);
            def.rebuildWorkerTestHook = null;
            assertFalse(persister.isAlive(), "the persist did not finish");
            final Throwable failure = persistFailure.get();
            assertNotNull(failure, "the persist succeeded although the rebuild failed");
            assertTrue(thrownFrom(failure, failingMethod), "the persist did not fail in " + failingMethod + ": " + failure);

            // every source as it was: the segment and the live graph, nothing published
            assertEquals(0, def.consolidationCount(), "the failed consolidation published a generation");
            assertTrue(isServingFromSegments(index), "the failed consolidation dropped the segment");
            assertEquals(ADDED, def.inMemoryGraphNodeCount(), "the failed consolidation changed the live graph");
            assertEquals(COUNT + ADDED, ids(index, COUNT + ADDED).size(),
                "searches after the failed persist do not see every entity");
            assertTrue(foundExactly(index, added[0], firstAddedId), "an entity added since the last persist is missing");
            assertTrue(foundExactly(index, map.get(MOVED), MOVED), "an entity of the segment is missing");

            // the next persist, with a working vectorizer, consolidates and writes the complete graph
            assertNull(persistWithinTimeout(index), "the next persist failed");
            assertEquals(1, def.consolidationCount(), "the next persist did not consolidate");
            assertEquals(COUNT + ADDED, ids(index, COUNT + ADDED).size(), "the next persist lost entities");
            map.store();
        }

        try(EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final GigaMap<Doc>     map   = storage.root();
            final VectorIndex<Doc> index = map.index().get(VectorIndices.class).get("emb");
            index.search(added[0].vector, 1); // initializes the index from disk
            assertTrue(isServingFromSegments(index), "precondition: the written segment was accepted after the restart");
            assertEquals(COUNT + ADDED, ids(index, COUNT + ADDED).size(), "entities are missing after a restart");
            assertTrue(foundExactly(index, added[0], firstAddedId));
        }
    }

    private static boolean thrownFrom(final Throwable failure, final String methodName)
    {
        for(final StackTraceElement frame : failure.getStackTrace())
        {
            if(frame.getMethodName().equals(methodName))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Persists on a daemon thread joined with a timeout: a persist that failed before may have left a lock held,
     * which must fail the test rather than hang the build.
     *
     * @return what the persist threw, or {@code null}
     */
    private static Throwable persistWithinTimeout(final VectorIndex<Doc> index) throws InterruptedException
    {
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final Thread persister = daemon("persister", () ->
        {
            try
            {
                index.persistToDisk();
            }
            catch(final Throwable t)
            {
                failure.set(t);
            }
        });
        persister.start();
        persister.join(TIMEOUT_MS);
        assertFalse(persister.isAlive(), "the persist did not finish");
        return failure.get();
    }

    private static Thread daemon(final String name, final Runnable action)
    {
        final Thread thread = new Thread(action, name);
        thread.setDaemon(true);
        return thread;
    }
}
