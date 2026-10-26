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
import org.eclipse.store.gigamap.jvector.VectorIndex.Default.FlushStage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import io.github.jbellis.jvector.graph.OnHeapGraphIndex;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the flush window: the stretch after a persist has frozen the in-memory graph, while the
 * flush cleans it up, writes it as a segment and commits, with no lock held. Searches and mutations
 * must go on throughout, and what they do must end up exactly in the committed state.
 * <p>
 * Covered here:
 * <ul>
 *   <li>a search and every kind of mutation complete while a flush is held at each of its stages, and
 *       their effects are visible at once and after the flush;</li>
 *   <li>an eventual-indexing operation that is queued, or being applied, when the persist freezes
 *       ends up in the segment the flush writes;</li>
 *   <li>a remove or vector update landing in the window is hidden in the segment written from the
 *       frozen graph, with eventual as well as synchronous indexing;</li>
 *   <li>embedded updates in the window leave the entity live in the live graph;</li>
 *   <li>a crash after the commit, with the store committed exactly at the freeze, loads exactly the
 *       frozen state, deletions included;</li>
 *   <li>an update whose computed vector did not change performs no graph work;</li>
 *   <li>a close or a removeAll during a held flush: the close waits for it and the flush commits
 *       nothing, the removeAll does not wait and the flush commits nothing either;</li>
 *   <li>a mutation that reaches the flush threshold freezes on its own thread, so a monitor held
 *       without a break cannot keep a flush from starting.</li>
 * </ul>
 */
@Tag("slow")
class VectorIndexPersistWindowConcurrencyTest
{
    static final int DIM = 64;

    record Doc(String content, float[] embedding) {}

    /** Computed (non-embedded) vectorizer: the branch the reported stack trace came from. */
    static class ComputedDocVectorizer extends Vectorizer<Doc>
    {
        @Override
        public float[] vectorize(final Doc entity)
        {
            return entity.embedding();
        }
    }

    /** Embedded vectorizer: vectors are read live from the entity, no separate vector store. */
    static class EmbeddedDocVectorizer extends ComputedDocVectorizer
    {
        @Override
        public boolean isEmbedded()
        {
            return true;
        }
    }

    /** Embedded vectorizer that permits entities without an embedding. */
    static class NullableEmbeddedDocVectorizer extends EmbeddedDocVectorizer
    {
        @Override
        public boolean allowsNullVectors()
        {
            return true;
        }
    }

    /**
     * Deterministic pseudo-random unit vector. Two calls with the same seed produce equal but not
     * identical arrays, so an equality-based fast path is actually exercised.
     */
    static float[] vec(final int seed)
    {
        // Scramble the seed: java.util.Random's first outputs for nearby seeds are strongly
        // correlated, which would make "nearest neighbor" assertions depend on luck.
        final Random random = new Random(seed * 6364136223846793005L + 1442695040888963407L);
        final float[] vector = new float[DIM];
        float norm = 0;
        for(int i = 0; i < DIM; i++)
        {
            vector[i] = random.nextFloat() * 2 - 1;
            norm += vector[i] * vector[i];
        }
        norm = (float)Math.sqrt(norm);
        for(int i = 0; i < DIM; i++)
        {
            vector[i] /= norm;
        }
        return vector;
    }

    private static VectorIndexConfiguration diskConfig(final Path indexDirectory)
    {
        return VectorIndexConfiguration.builder()
            .dimension(DIM)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .onDisk(true)
            .indexDirectory(indexDirectory)
            .build();
    }

    private static VectorIndexConfiguration diskConfig(
        final Path    indexDirectory,
        final boolean eventualIndexing
    )
    {
        return VectorIndexConfiguration.builder()
            .dimension(DIM)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .onDisk(true)
            .indexDirectory(indexDirectory)
            .eventualIndexing(eventualIndexing)
            .build();
    }

    private static long top(
        final VectorIndex<Doc> index ,
        final float[]          vector
    )
    {
        return index.search(vector, 1).stream().findFirst().orElseThrow().entityId();
    }

    private static List<Long> all(
        final VectorIndex<Doc> index ,
        final float[]          vector,
        final int              k
    )
    {
        return index.search(vector, k).stream().map(ScoredSearchResult.Entry::entityId).toList();
    }

    private static int ownerOf(
        final VectorIndex.Default<Doc> index,
        final long                     id
    )
    {
        return index.sourceSet().owners().owner((int)id);
    }

    private static void await(final CountDownLatch latch)
    {
        try
        {
            assertTrue(latch.await(30, TimeUnit.SECONDS), "timed out waiting for the other thread");
        }
        catch(final InterruptedException e)
        {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    private static Thread daemon(
        final String                     name   ,
        final Runnable                   action ,
        final AtomicReference<Throwable> failure
    )
    {
        final Thread thread = new Thread(() ->
        {
            try
            {
                action.run();
            }
            catch(final Throwable t)
            {
                failure.set(t);
            }
        }, name);
        thread.setDaemon(true);
        return thread;
    }


    // ==================== 1. Searches and mutations go on while a flush is held ====================

    /**
     * The flush holds no lock from the freeze on, so while it is held at a stage - before the moved
     * operations, after the cleanup, after the segment is written - a search must answer from the frozen
     * graph, and an add, an update of a frozen entity and a remove of another must all complete at once
     * with their effects visible. Once released, the flush commits the frozen state, and the changes made
     * meanwhile stay where they are: in the live graph, hiding the frozen copies. A further persist
     * writes them, and the files then load against the live witnesses without a rebuild.
     *
     * @param stage    where the flush is held
     * @param indexDir the index directory
     */
    @ParameterizedTest(name = "held at {0}")
    @EnumSource(value = FlushStage.class, names = {"FROZEN", "CLEANED", "SEGMENT_WRITTEN"})
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void searchAndMutationsCompleteWhileAFlushIsHeld(
        final FlushStage    stage   ,
        @TempDir final Path indexDir
    ) throws InterruptedException
    {
        final GigaMap<Doc> map = GigaMap.New();
        try(final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
            .add("embeddings", diskConfig(indexDir), new ComputedDocVectorizer()))
        {
            final VectorIndex.Default<Doc> def   = (VectorIndex.Default<Doc>)index;
            final int                      count = 30;
            final long[]                   ids   = new long[count];
            for(int i = 0; i < count; i++)
            {
                ids[i] = map.add(new Doc("d" + i, vec(i)));
            }

            final CountDownLatch held    = new CountDownLatch(1);
            final CountDownLatch release = new CountDownLatch(1);
            FlushHooks.onceAt(def, stage, () ->
            {
                held.countDown();
                await(release);
            });
            final AtomicReference<Throwable> failure = new AtomicReference<>();
            final Thread persister = daemon("persister", index::persistToDisk, failure);
            persister.start();
            await(held);

            // The frozen graph answers, and every mutation completes although the flush is in flight.
            assertEquals(ids[7], top(index, vec(7)), "the frozen graph must answer searches");
            final float[] updated = vec(7_000);
            final long    added   = map.add(new Doc("added", vec(8_000)));
            map.set(ids[3], new Doc("d3", updated));
            map.removeById(ids[4]);

            assertEquals(added, top(index, vec(8_000)), "an add during the flush must be searchable at once");
            assertEquals(ids[3], top(index, updated), "an update during the flush must be searchable at once");
            assertFalse(all(index, vec(4), count + 1).contains(ids[4]), "a removed entity must be hidden at once");

            release.countDown();
            persister.join(TimeUnit.SECONDS.toMillis(30));
            assertFalse(persister.isAlive(), "the persist did not finish");
            assertNull(failure.get(), () -> "the persist failed: " + failure.get());

            assertEquals(1, def.sourceSet().segments().size(), "the flush must have published its segment");
            assertTrue(def.sourceSet().frozen().isEmpty());
            assertEquals(def.liveSlot(), ownerOf(def, ids[3]), "the updated entity stays live in the live graph");
            assertEquals(SegmentOwners.NONE, ownerOf(def, ids[4]));
            final List<Long> afterFlush = all(index, vec(4), count + 1);
            assertFalse(afterFlush.contains(ids[4]), "the removed entity must stay hidden: " + afterFlush);
            assertEquals(count, afterFlush.size(), "every live entity exactly once: " + afterFlush);
            assertEquals(ids[3], top(index, updated));
            assertEquals(added, top(index, vec(8_000)));

            // The next persist writes the changes; the files then match the live store exactly.
            index.persistToDisk();
            assertEquals(2, def.sourceSet().segments().size());
            try(final SegmentLoad load = SegmentLoad.of(
                def, "embeddings", indexDir, DIM, GraphFormat.of(def.configuration()), false))
            {
                assertTrue(load.tryLoad(new DiskIndexManager.MetaState(
                    def.getExpectedVectorCount(), def.getHighestEntityId(), def.getStructuralModCount())),
                    "the committed files must load against the live witnesses, without a rebuild");
                final SegmentOwners owners = load.owners();
                assertEquals(SegmentOwners.NONE, owners.owner((int)ids[4]));
                assertEquals(1, owners.owner((int)ids[3]), "the updated entity is live in the newer segment");
                assertEquals(0, owners.owner((int)ids[5]), "an untouched entity stays in the first segment");
            }
        }
    }


    // ==================== 2. Eventual operations in flight at the freeze ====================

    /**
     * With eventual indexing, an operation can be queued when the persist freezes, or be the one the
     * worker is applying. The worker applies each operation under the indexing lock, which the freeze
     * takes as well, so the persist waits for an operation being applied, and moves the queued ones into
     * the frozen graph, whose flush applies them. Either way every operation counted in the witnesses is
     * in the committed segment.
     *
     * @param indexDir the index directory
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void eventualOperationsInFlightAtTheFreezeLandInTheSegment(@TempDir final Path indexDir)
        throws InterruptedException
    {
        final GigaMap<Doc> map = GigaMap.New();
        try(final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
            .add("embeddings", diskConfig(indexDir, true), new ComputedDocVectorizer()))
        {
            final VectorIndex.Default<Doc> def   = (VectorIndex.Default<Doc>)index;
            final int                      count = 10;
            for(int i = 0; i < count; i++)
            {
                map.add(new Doc("d" + i, vec(i)));
            }
            def.backgroundTaskManager.drainQueue();

            final CountDownLatch applying = new CountDownLatch(1);
            final CountDownLatch release  = new CountDownLatch(1);
            def.backgroundTaskManager.applyTestHook = () ->
            {
                def.backgroundTaskManager.applyTestHook = null;
                applying.countDown();
                await(release);
            };
            final long first = map.add(new Doc("first", vec(9_001)));
            await(applying);
            // Queued behind the operation the worker is holding.
            final long second = map.add(new Doc("second", vec(9_002)));

            final AtomicReference<Throwable> failure = new AtomicReference<>();
            final Thread persister = daemon("persister", index::persistToDisk, failure);
            persister.start();
            // The persist cannot freeze while the worker holds the indexing lock.
            Thread.sleep(200);
            assertTrue(persister.isAlive(), "the persist must wait for the operation being applied");

            release.countDown();
            persister.join(TimeUnit.SECONDS.toMillis(30));
            assertFalse(persister.isAlive(), "the persist did not finish");
            assertNull(failure.get(), () -> "the persist failed: " + failure.get());
            def.backgroundTaskManager.drainQueue();

            assertEquals(1, def.sourceSet().segments().size());
            final int segment = def.sourceSet().segments().get(0).slot();
            assertEquals(segment, ownerOf(def, first), "the operation applied at the freeze must be in the segment");
            assertEquals(segment, ownerOf(def, second), "the queued operation must be in the segment");
            assertEquals(first, top(index, vec(9_001)));
            assertEquals(second, top(index, vec(9_002)));
            assertEquals(0, def.inMemoryGraphNodeCount(), "nothing may be left for the live graph");
        }
    }


    // ==================== 3. Window mutations are hidden in the written segment ====================

    /**
     * A remove or vector update that lands while the flush writes - after the freeze captured the graph
     * and the deletions - must be hidden in the segment written from the frozen graph, which carries the
     * entity as it was at the freeze. With eventual indexing the owner is set when the mutation is
     * queued; synchronous indexing sets it after the graph change. Both must leave the removed entity
     * owned by nobody and the updated one by the live graph.
     *
     * @param embedded         whether to use an embedded vectorizer instead of a computed one
     * @param eventualIndexing whether graph mutations are applied by the background worker
     * @param indexDir         the index directory
     */
    @ParameterizedTest(name = "embedded={0}, eventualIndexing={1}")
    @CsvSource({"false, true", "true, true", "false, false", "true, false"})
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void mutationDuringAFlushIsHiddenInTheWrittenSegment(
        final boolean embedded,
        final boolean eventualIndexing,
        @TempDir final Path indexDir
    )
    {
        final GigaMap<Doc> map = GigaMap.New();
        final Vectorizer<Doc> vectorizer = embedded ? new EmbeddedDocVectorizer() : new ComputedDocVectorizer();
        try(final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
            .add("embeddings", diskConfig(indexDir, eventualIndexing), vectorizer))
        {
            final int    count   = 20;
            final int    removed = 3;
            final int    updated = 5;
            final long[] ids     = new long[count];
            for(int i = 0; i < count; i++)
            {
                ids[i] = map.add(new Doc("d" + i, vec(i)));
            }

            final VectorIndex.Default<Doc> def = (VectorIndex.Default<Doc>)index;
            final float[] newVector = vec(5_000);
            FlushHooks.onceAt(def, FlushStage.CLEANED, () ->
            {
                map.removeById(ids[removed]);
                map.set(ids[updated], new Doc("d" + updated, newVector));
            });

            index.persistToDisk();
            if(eventualIndexing)
            {
                def.backgroundTaskManager.drainQueue();
            }

            assertEquals(1, def.sourceSet().segments().size());
            assertEquals(SegmentOwners.NONE, ownerOf(def, ids[removed]),
                "the segment copy of an entity removed during the flush must be hidden");
            assertEquals(def.liveSlot(), ownerOf(def, ids[updated]),
                "an entity updated during the flush must be live in the live graph only");

            final List<Long> found = all(index, vec(removed), count);
            assertFalse(found.contains(ids[removed]), "a removed entity must not be returned by search: " + found);
            assertEquals(count - 1, found.size(), "every remaining entity must be returned exactly once: " + found);
            assertEquals(ids[updated], top(index, newVector), "the updated entity must be found by its new vector");
            for(int i = 0; i < count; i++)
            {
                if(i != removed && i != updated)
                {
                    assertEquals(ids[i], top(index, vec(i)), "entity " + i + " must survive the flush");
                }
            }
        }
    }


    // ==================== 4. Embedded updates during a flush ====================

    /**
     * Three embedded updates of the same entity during a flush - {@code vec->vec}, {@code vec->null},
     * {@code null->vec} - leave it live in the live graph, by its final vector, and hidden in the
     * segment written from the frozen graph. The first re-adds the node to the live graph (it is only in
     * the frozen one there), the second marks it deleted, and the last has to resurrect it.
     *
     * @param indexDir the index directory
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    @SuppressWarnings("unchecked")
    void embeddedUpdatesDuringAFlushEndLiveInTheLiveGraph(@TempDir final Path indexDir)
    {
        final GigaMap<Doc> map = GigaMap.New();
        try(final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
            .add("embeddings", diskConfig(indexDir, false), new NullableEmbeddedDocVectorizer()))
        {
            final int    count  = 20;
            final int    target = 5;
            final long[] ids    = new long[count];
            for(int i = 0; i < count; i++)
            {
                ids[i] = map.add(new Doc("d" + i, vec(i)));
            }

            final VectorIndex.Default<Doc> def = (VectorIndex.Default<Doc>)index;
            final float[] finalVector = vec(5_002);
            FlushHooks.onceAt(def, FlushStage.FROZEN, () ->
            {
                map.set(ids[target], new Doc("d" + target, vec(5_001)));
                map.set(ids[target], new Doc("d" + target, null));
                map.set(ids[target], new Doc("d" + target, finalVector));
            });

            index.persistToDisk();

            assertEquals(def.liveSlot(), ownerOf(def, ids[target]), "the segment copy must be hidden");
            final OnHeapGraphIndex graph = (OnHeapGraphIndex)def.currentBuilder().getGraph();
            final int ordinal = (int)ids[target];
            assertTrue(graph.containsNode(ordinal), "the live graph must hold the updated node");
            assertFalse(graph.getDeletedNodes().get(ordinal), "the updated node must not stay marked deleted");
            assertEquals(ids[target], top(index, finalVector), "the entity must be found by its final vector");
        }
    }


    // ==================== 5. A crash after the commit loads exactly the frozen state ====================

    /**
     * The manifest of a flush must describe exactly the instant of the freeze: its witnesses, and with
     * them the deletion set of every segment. If the store is committed exactly then and the process dies
     * after the flush committed, the restart accepts the manifest, so a deletion recorded after the
     * freeze would hide an entity the store still has, and a deletion before it missing from the set
     * would resurrect a removed one.
     *
     * @param dir the test directory
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void crashAfterACommitWithTheStoreAtTheFreeze_loadsExactlyTheFrozenState(@TempDir final Path dir)
    {
        final Path storageDir = dir.resolve("storage");
        final Path indexDir   = dir.resolve("index");
        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(DIM)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .onDisk(true)
            .indexDirectory(indexDir)
            .persistOnShutdown(false) // the "crash": close() writes nothing
            .build();

        final long[] ids = new long[10];
        final long   addedBeforeTheFreeze;
        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(GigaMap.New(), storageDir))
        {
            final GigaMap<Doc>     map   = rootOf(storage);
            final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
                .add("embeddings", config, new ComputedDocVectorizer());
            for(int i = 0; i < ids.length; i++)
            {
                ids[i] = map.add(new Doc("d" + i, vec(i)));
            }
            index.persistToDisk();

            // Before the freeze: in the witnesses and in the first segment's deletions.
            map.removeById(ids[1]);
            addedBeforeTheFreeze = map.add(new Doc("added", vec(1_000)));

            FlushHooks.onceAt((VectorIndex.Default<Doc>)index, FlushStage.FROZEN, () ->
            {
                storage.storeRoot();       // the store, exactly at the freeze
                map.removeById(ids[2]);    // after the freeze, and never stored
            });
            index.persistToDisk();
            index.close();
        }

        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final GigaMap<Doc>             map   = rootOf(storage);
            final VectorIndex.Default<Doc> index = (VectorIndex.Default<Doc>)map.index()
                .get(VectorIndices.Category()).get("embeddings");

            assertEquals(2, index.sourceSet().segments().size(), "the committed manifest must be accepted");
            assertEquals(0, index.inMemoryGraphNodeCount(), "nothing may be rebuilt");
            final List<Long> found = all(index, vec(2), ids.length + 1);
            assertTrue(found.contains(ids[2]), "an entity removed after the freeze is in the store: " + found);
            assertFalse(found.contains(ids[1]), "an entity removed before the freeze must stay removed: " + found);
            assertEquals(addedBeforeTheFreeze, top(index, vec(1_000)));
            assertEquals(ids.length, found.size(), "every stored entity exactly once: " + found);
            index.close();
        }
    }


    // ==================== 6. Unchanged computed vector ====================

    /**
     * In computed mode the stored vector IS the indexed value, so an update that leaves it untouched
     * has nothing to do. Without the equality check every {@code update()} paid
     * {@code markNodeDeleted + removeDeletedNodes + addGraphNode} - two ForkJoinPool round trips per
     * level - on every update during a bulk load.
     * {@code structuralModCount} is the observable witness: it is bumped by exactly the
     * {@code markContentChanged()} that the fast path must skip.
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    @SuppressWarnings("unchecked")
    void updateWithUnchangedComputedVectorDoesNoGraphWork(@TempDir final Path indexDir)
    {
        final GigaMap<Doc> map = GigaMap.New();
        try(final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
            .add("embeddings", diskConfig(indexDir), new ComputedDocVectorizer()))
        {
            final long a = map.add(new Doc("a", vec(0)));
            final long b = map.add(new Doc("b", vec(1)));

            final VectorIndex.Default<Doc> def = (VectorIndex.Default<Doc>)index;
            final long modCountAfterAdds = def.structuralModCount;

            // Same vector content (a fresh array), unrelated field changed.
            map.set(a, new Doc("a-renamed", vec(0)));
            assertEquals(modCountAfterAdds, def.structuralModCount,
                "an update that does not change the vector must not count as a structural change");
            assertEquals(a, index.search(vec(0), 1).stream().findFirst().orElseThrow().entityId(),
                "the entity must stay searchable by its unchanged vector");

            // A real vector change must still be recorded and applied.
            map.set(a, new Doc("a-renamed", vec(2)));
            assertTrue(def.structuralModCount > modCountAfterAdds,
                "an update that changes the vector must count as a structural change");
            assertEquals(a, index.search(vec(2), 1).stream().findFirst().orElseThrow().entityId(),
                "the entity must be searchable by its new vector");
            assertEquals(b, index.search(vec(1), 1).stream().findFirst().orElseThrow().entityId(),
                "the untouched entity must be unaffected");
        }
    }


    // ==================== 7. Close and removeAll during a flush ====================

    /**
     * A close waits for a background flush in progress, which it asks to give up: the flush commits
     * nothing and deletes the files it wrote, and its thread does not outlive the close. Without a
     * persist on shutdown, nothing is written in its place.
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void closeDuringABackgroundFlush_waitsAndTheFlushCommitsNothing(@TempDir final Path indexDir)
        throws InterruptedException
    {
        final GigaMap<Doc> map = GigaMap.New();
        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(DIM)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .onDisk(true)
            .indexDirectory(indexDir)
            .persistenceIntervalMs(600_000L) // only the flush the threshold requests runs
            .flushThreshold(10)
            .persistOnShutdown(false)
            .build();
        final VectorIndex.Default<Doc> index = (VectorIndex.Default<Doc>)map.index()
            .register(VectorIndices.Category())
            .add("embeddings", config, new ComputedDocVectorizer());

        final CountDownLatch holding = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final AtomicReference<String> flushThread = new AtomicReference<>();
        FlushHooks.onceAt(index, FlushStage.SEGMENT_WRITTEN, () ->
        {
            flushThread.set(Thread.currentThread().getName());
            holding.countDown();
            await(release);
        });
        final Thread closer;
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        try
        {
            for(int i = 0; i < 20; i++)
            {
                map.add(new Doc("d" + i, vec(i)));
            }
            await(holding);
            assertEquals("VectorIndex-Background-embeddings-flush", flushThread.get());
            assertFalse(SegmentLayout.graphFiles(indexDir, "embeddings").isEmpty(), "the held flush wrote its files");

            closer = daemon("closer", index::close, failure);
            closer.start();
            Thread.sleep(200);
            assertTrue(closer.isAlive(), "the close must wait for the flush in progress");
        }
        finally
        {
            release.countDown();
        }
        closer.join(TimeUnit.SECONDS.toMillis(30));
        assertFalse(closer.isAlive(), "the close did not finish");
        assertNull(failure.get(), () -> "the close failed: " + failure.get());

        assertFalse(SegmentLayout.indexExists(indexDir, "embeddings"), "the abandoned flush must commit nothing");
        assertTrue(SegmentLayout.graphFiles(indexDir, "embeddings").isEmpty(),
            "the abandoned flush must delete the files it wrote");
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while(Thread.getAllStackTraces().keySet().stream().anyMatch(t -> t.getName().equals(flushThread.get())))
        {
            assertTrue(System.nanoTime() < deadline, "the flush thread must not outlive the close");
            Thread.sleep(20);
        }
    }

    /**
     * With a persist on shutdown, a close that comes while a background flush writes must not lose what
     * that flush froze: the persist handed it to the flush thread without taking the counted changes,
     * so the shutdown persist runs, waits for the flush and finds it committed. With eventual indexing,
     * so the freeze is the background task thread's, which counts the changes.
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void closeDuringABackgroundFlush_withPersistOnShutdown_commitsIt(@TempDir final Path indexDir)
        throws InterruptedException
    {
        final GigaMap<Doc> map = GigaMap.New();
        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(DIM)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .onDisk(true)
            .indexDirectory(indexDir)
            .eventualIndexing(true)
            .persistenceIntervalMs(600_000L) // only the flush the threshold requests runs
            .flushThreshold(10)
            .persistOnShutdown(true)
            .build();
        final VectorIndex.Default<Doc> index = (VectorIndex.Default<Doc>)map.index()
            .register(VectorIndices.Category())
            .add("embeddings", config, new ComputedDocVectorizer());

        final CountDownLatch holding = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        FlushHooks.onceAt(index, FlushStage.SEGMENT_WRITTEN, () ->
        {
            holding.countDown();
            await(release);
        });
        final List<Doc> documents = new ArrayList<>();
        for(int i = 0; i < 20; i++)
        {
            documents.add(new Doc("d" + i, vec(i)));
        }
        final Thread                     closer;
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        try
        {
            map.addAll(documents);
            await(holding);
            closer = daemon("closer", index::close, failure);
            closer.start();
            Thread.sleep(200);
            assertTrue(closer.isAlive(), "the close must wait for the flush in progress");
        }
        finally
        {
            release.countDown();
        }
        closer.join(TimeUnit.SECONDS.toMillis(30));
        assertFalse(closer.isAlive(), "the close did not finish");
        assertNull(failure.get(), () -> "the close failed: " + failure.get());

        assertTrue(SegmentLayout.indexExists(indexDir, "embeddings"), "the frozen graph must be committed");
        assertEquals(1, SegmentLayout.graphFiles(indexDir, "embeddings").size());
    }

    /**
     * A removeAll during a flush does not wait for it: it retires every source at once, and the flush,
     * finding them retired when it is about to commit, commits nothing and deletes only the files it
     * wrote itself. The index goes on empty, and its next persist writes what was added since.
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void removeAllDuringAFlush_doesNotWaitAndTheFlushCommitsNothing(@TempDir final Path indexDir)
        throws InterruptedException
    {
        final GigaMap<Doc> map = GigaMap.New();
        try(final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
            .add("embeddings", diskConfig(indexDir), new ComputedDocVectorizer()))
        {
            final VectorIndex.Default<Doc> def = (VectorIndex.Default<Doc>)index;
            for(int i = 0; i < 20; i++)
            {
                map.add(new Doc("d" + i, vec(i)));
            }

            final CountDownLatch holding = new CountDownLatch(1);
            final CountDownLatch release = new CountDownLatch(1);
            FlushHooks.onceAt(def, FlushStage.SEGMENT_WRITTEN, () ->
            {
                holding.countDown();
                await(release);
            });
            final AtomicReference<Throwable> failure   = new AtomicReference<>();
            final Thread                     persister = daemon("persister", index::persistToDisk, failure);
            persister.start();
            try
            {
                await(holding);
                map.removeAll();
                assertTrue(persister.isAlive(), "removeAll must not wait for the flush");
                assertEquals(0, index.search(vec(1), 10).size());
            }
            finally
            {
                release.countDown();
            }
            persister.join(TimeUnit.SECONDS.toMillis(30));
            assertFalse(persister.isAlive(), "the persist did not finish");
            assertNull(failure.get(), () -> "an abandoned flush is no failure of the persist: " + failure.get());
            assertFalse(SegmentLayout.indexExists(indexDir, "embeddings"), "the abandoned flush must commit nothing");
            assertTrue(SegmentLayout.graphFiles(indexDir, "embeddings").isEmpty(),
                "the abandoned flush must delete the files it wrote");

            final long added = map.add(new Doc("after", vec(100)));
            index.persistToDisk();
            assertEquals(1, SegmentLayout.graphFiles(indexDir, "embeddings").size());
            assertEquals(added, top(index, vec(100)));
        }
    }


    // ==================== 8. A mutation reaching the threshold freezes on its own thread ====================

    /**
     * An ingest that holds the parent monitor without a break would keep the background task thread from
     * ever freezing, so the mutation that reaches the flush threshold freezes itself, holding the monitor
     * already, and hands the write to the flush thread. Asserted while the monitor is still held, so no
     * other thread can have done the freeze.
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void mutationReachingTheThreshold_freezesOnItsOwnThread(@TempDir final Path indexDir) throws InterruptedException
    {
        final GigaMap<Doc> map = GigaMap.New();
        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(DIM)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .onDisk(true)
            .indexDirectory(indexDir)
            .persistenceIntervalMs(600_000L) // no scheduled persist within the test
            .flushThreshold(50)
            .build();
        try(final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
            .add("embeddings", config, new ComputedDocVectorizer()))
        {
            final VectorIndex.Default<Doc> def = (VectorIndex.Default<Doc>)index;
            final CountDownLatch release = new CountDownLatch(1);
            FlushHooks.onceAt(def, FlushStage.FROZEN, () -> await(release));

            synchronized(map)
            {
                for(int i = 0; i < 60; i++)
                {
                    map.add(new Doc("d" + i, vec(i)));
                }
                assertEquals(1, def.sourceSet().frozen().size(), "the mutation at the threshold must have frozen");
                assertTrue(def.inMemoryGraphNodeCount() < 50, "the live graph must have started over");
            }
            release.countDown();

            final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            while(def.flushCount() == 0)
            {
                assertTrue(System.nanoTime() < deadline, "the frozen graph must be flushed");
                Thread.sleep(20);
            }
            assertEquals(1, def.sourceSet().segments().size());
            assertEquals(map.size(), all(index, vec(1), 100).size());
        }
    }

    /**
     * Without background persistence, reaching the flush threshold writes nothing, even with a
     * background task manager running for background optimization: only explicit and shutdown persists
     * write then.
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void thresholdWithoutBackgroundPersistence_writesNothing(@TempDir final Path indexDir) throws InterruptedException
    {
        final GigaMap<Doc> map = GigaMap.New();
        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(DIM)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .onDisk(true)
            .indexDirectory(indexDir)
            .optimizationIntervalMs(600_000L) // a background task manager, for optimization only
            .flushThreshold(10)
            .build();
        try(final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
            .add("embeddings", config, new ComputedDocVectorizer()))
        {
            final VectorIndex.Default<Doc> def = (VectorIndex.Default<Doc>)index;
            assertTrue(def.backgroundTaskManager != null, "precondition: a background task manager runs");
            for(int i = 0; i < 30; i++)
            {
                map.add(new Doc("d" + i, vec(i)));
            }
            Thread.sleep(300);

            assertEquals(0, def.flushCount(), "nothing may be flushed without background persistence");
            assertTrue(def.sourceSet().frozen().isEmpty());
            assertTrue(SegmentLayout.graphFiles(indexDir, "embeddings").isEmpty());
        }
    }

    /**
     * A background persist that retries the write of a frozen graph an earlier flush failed to write
     * commits the witnesses as they are now, so it must write the live graph too: the mutations that
     * reached it since are counted in those witnesses. Otherwise a crash with the store committed at
     * them restarts from a manifest that is accepted but lacks those entities.
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void backgroundRetryAfterAFailedFlush_alsoWritesTheLiveGraph(@TempDir final Path dir) throws InterruptedException
    {
        final Path storageDir = dir.resolve("storage");
        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(DIM)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .onDisk(true)
            .indexDirectory(dir.resolve("index"))
            .persistenceIntervalMs(600_000L) // background persistence, run by the test itself
            .persistOnShutdown(false)       // the close stands for a crash
            .build();
        final long late;

        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(GigaMap.New(), storageDir))
        {
            final GigaMap<Doc>             map   = rootOf(storage);
            final VectorIndex.Default<Doc> index = (VectorIndex.Default<Doc>)map.index()
                .register(VectorIndices.Category())
                .add("embeddings", config, new ComputedDocVectorizer());
            for(int i = 0; i < 20; i++)
            {
                map.add(new Doc("d" + i, vec(i)));
            }
            FlushHooks.onceAt(index, FlushStage.CLEANED, () ->
            {
                throw new IllegalStateException("simulated write failure");
            });
            try
            {
                index.persistToDisk();
            }
            catch(final RuntimeException expected)
            {
                // the frozen graph stays for the next persist
            }
            assertEquals(1, index.sourceSet().frozen().size());

            late = map.add(new Doc("late", vec(7_777)));
            index.doPersistToDisk(false);
            final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            while(index.flushCount() == 0)
            {
                assertTrue(System.nanoTime() < deadline, "the background retry must flush");
                Thread.sleep(20);
            }
            storage.storeRoot();
            index.close();
        }

        try(final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final VectorIndex.Default<Doc> index = (VectorIndex.Default<Doc>)rootOf(storage).index()
                .get(VectorIndices.Category())
                .get("embeddings");
            assertEquals(late, top(index, vec(7_777)), "the entity added after the failed flush must survive");
            assertEquals(0, index.inMemoryGraphNodeCount(), "the manifest matches the store, so it loads");
            index.close();
        }
    }

    /**
     * A flush requested while another one holds the flush permit is declined. Once that flush is done,
     * it asks again if a flush is due, rather than leaving the live graph above the threshold until the
     * scheduled persist, which does not come within the test.
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void thresholdReachedDuringAFlush_flushesAgainOnceItIsDone(@TempDir final Path indexDir)
        throws InterruptedException
    {
        final GigaMap<Doc> map = GigaMap.New();
        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(DIM)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .onDisk(true)
            .indexDirectory(indexDir)
            .persistenceIntervalMs(600_000L) // only the flushes the threshold requests run
            .flushThreshold(10)
            .build();
        try(final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
            .add("embeddings", config, new ComputedDocVectorizer()))
        {
            final VectorIndex.Default<Doc> def     = (VectorIndex.Default<Doc>)index;
            final CountDownLatch           holding = new CountDownLatch(1);
            final CountDownLatch           release = new CountDownLatch(1);
            FlushHooks.onceAt(def, FlushStage.SEGMENT_WRITTEN, () ->
            {
                holding.countDown();
                await(release);
            });
            for(int i = 0; i < 10; i++)
            {
                map.add(new Doc("first" + i, vec(i)));
            }
            await(holding);

            // The threshold again, while the first flush holds the permit.
            for(int i = 0; i < 12; i++)
            {
                map.add(new Doc("second" + i, vec(100 + i)));
            }
            Thread.sleep(200);
            release.countDown();

            final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            while(def.flushCount() < 2)
            {
                assertTrue(System.nanoTime() < deadline, "the threshold reached during the flush must flush again");
                Thread.sleep(20);
            }
            assertTrue(def.inMemoryGraphNodeCount() < 10);
            assertEquals(map.size(), all(index, vec(1), 100).size());
        }
    }

    /**
     * A flush handed to the flush thread retires the changes it froze once it commits them. Before, the
     * background persist gave the count back because the write was still running, and nothing retired it
     * afterwards, so every later persist and the shutdown persist ran for changes already committed.
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void backgroundFlush_retiresTheChangesItCommitted(@TempDir final Path indexDir)
        throws InterruptedException
    {
        final GigaMap<Doc> map = GigaMap.New();
        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(DIM)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .onDisk(true)
            .indexDirectory(indexDir)
            .persistenceIntervalMs(600_000L) // only the flush the threshold requests runs
            .flushThreshold(10)
            .build();
        try(final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
            .add("embeddings", config, new ComputedDocVectorizer()))
        {
            final VectorIndex.Default<Doc> def = (VectorIndex.Default<Doc>)index;
            for(int i = 0; i < 10; i++)
            {
                map.add(new Doc("doc" + i, vec(i)));
            }

            final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            while(def.flushCount() < 1 || def.backgroundTaskManager.getPersistenceChangeCount() > 0)
            {
                assertTrue(System.nanoTime() < deadline, "the committed flush must retire its changes, "
                    + "count " + def.backgroundTaskManager.getPersistenceChangeCount());
                Thread.sleep(20);
            }
        }
    }

    /**
     * An explicit persist retires the changes it froze, and only those: a change made while its write
     * runs stays counted for the next persist.
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void explicitFlush_retiresTheChangesItFroze_andKeepsLaterOnes(@TempDir final Path indexDir)
    {
        final GigaMap<Doc> map = GigaMap.New();
        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(DIM)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .onDisk(true)
            .indexDirectory(indexDir)
            .persistenceIntervalMs(600_000L)
            .build();
        try(final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
            .add("embeddings", config, new ComputedDocVectorizer()))
        {
            final VectorIndex.Default<Doc> def = (VectorIndex.Default<Doc>)index;
            for(int i = 0; i < 5; i++)
            {
                map.add(new Doc("doc" + i, vec(i)));
            }
            FlushHooks.onceAt(def, FlushStage.FROZEN, () -> map.add(new Doc("late", vec(50))));

            index.persistToDisk();

            assertEquals(1, def.flushCount());
            assertEquals(1, def.backgroundTaskManager.getPersistenceChangeCount(),
                "only the change made after the freeze stays counted");
        }
    }

    /**
     * Mutations reaching the flush threshold while a flush holds the permit skip their request; the
     * flush asks once it is done. It must ask also when a removeAll() abandoned it, for the graph the
     * removeAll() installed, or that graph waits for the scheduled persist.
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void thresholdReachedDuringAnAbandonedFlush_flushesOnceItIsDone(@TempDir final Path indexDir)
        throws InterruptedException
    {
        final GigaMap<Doc> map = GigaMap.New();
        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(DIM)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .onDisk(true)
            .indexDirectory(indexDir)
            .persistenceIntervalMs(600_000L) // only the flushes the threshold requests run
            .flushThreshold(10)
            .build();
        try(final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
            .add("embeddings", config, new ComputedDocVectorizer()))
        {
            final VectorIndex.Default<Doc> def = (VectorIndex.Default<Doc>)index;
            for(int i = 0; i < 5; i++)
            {
                map.add(new Doc("first" + i, vec(i)));
            }
            FlushHooks.onceAt(def, FlushStage.CLEANED, () ->
            {
                map.removeAll();
                for(int i = 0; i < 12; i++)
                {
                    map.add(new Doc("second" + i, vec(100 + i)));
                }
            });
            index.persistToDisk();
            assertEquals(0, def.flushCount(), "the explicit flush was abandoned");

            final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            while(def.flushCount() < 1)
            {
                assertTrue(System.nanoTime() < deadline, "the abandoned flush must ask for the flush that is due");
                Thread.sleep(20);
            }
            assertEquals(12, all(index, vec(101), 100).size());
        }
    }

    /**
     * A flush must not need the parent monitor between its freeze and its commit: a thread holding it
     * can starve the ForkJoin workers the flush scores and encodes on by joining the same common pool,
     * or simply hold the write up for as long as a mutation takes. A computed vectorizer's live view
     * takes the monitor for its ordinal bound only, which jVector's {@code cleanup()} never asks for and
     * the write replaces by the segment's own local ordinals, even when it encodes PQ codes. A monitor
     * held by another thread throughout the cleanup and the write must therefore not hold them up.
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void flushWrite_computedVectorizer_doesNotNeedTheMonitor(@TempDir final Path indexDir)
        throws InterruptedException
    {
        final GigaMap<Doc> map = GigaMap.New();
        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(DIM)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .onDisk(true)
            .indexDirectory(indexDir)
            .approximateScoring(ApproximateScoring.FUSED_PQ) // the write encodes over the whole bound
            .pqSubspaces(DIM / 4)
            .build();
        try(final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
            .add("embeddings", config, new ComputedDocVectorizer()))
        {
            final VectorIndex.Default<Doc> def = (VectorIndex.Default<Doc>)index;
            for(int i = 0; i < 1_000; i++)
            {
                map.add(new Doc("doc" + i, vec(i)));
            }
            final CountDownLatch held    = new CountDownLatch(1);
            final CountDownLatch release = new CountDownLatch(1);
            final CountDownLatch cleaned = new CountDownLatch(1);
            final Thread holder = new Thread(() ->
            {
                synchronized(map)
                {
                    held.countDown();
                    await(release);
                }
            });
            def.flushStageTestHook = stage ->
            {
                if(stage == FlushStage.OPS_APPLIED)
                {
                    holder.start();
                    await(held);
                }
                else if(stage == FlushStage.SEGMENT_WRITTEN)
                {
                    def.flushStageTestHook = null;
                    cleaned.countDown();
                }
            };
            final Thread persister = new Thread(index::persistToDisk);
            persister.start();
            try
            {
                assertTrue(cleaned.await(20, TimeUnit.SECONDS), "the cleanup and write must not wait for the monitor");
            }
            finally
            {
                release.countDown();
                persister.join(TimeUnit.SECONDS.toMillis(30));
                holder.join(TimeUnit.SECONDS.toMillis(30));
            }
            assertEquals(1, def.flushCount());
            assertEquals(map.size(), all(index, vec(1), 1_100).size());
        }
    }

    /**
     * A mutation reaching the flush threshold freezes on its own thread only when that is quick. With no
     * source slot free, the freeze would have to consolidate - rebuild everything while holding the
     * monitor and the write lock - so it is left to the background persist instead.
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void mutationReachingTheThreshold_withNoFreeSlot_doesNotConsolidateOnItsThread(@TempDir final Path indexDir)
    {
        final GigaMap<Doc> map = GigaMap.New();
        final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
            .dimension(DIM)
            .similarityFunction(VectorSimilarityFunction.COSINE)
            .onDisk(true)
            .indexDirectory(indexDir)
            .persistenceIntervalMs(600_000L) // only the flush the threshold requests runs
            .flushThreshold(10)
            .build();
        try(final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
            .add("embeddings", config, new ComputedDocVectorizer()))
        {
            final VectorIndex.Default<Doc> def = (VectorIndex.Default<Doc>)index;
            map.add(new Doc("first", vec(0)));
            final OwnerTable             owners = def.sourceSet().owners();
            final List<OwnerTable.Lease> taken  = new ArrayList<>();
            try
            {
                for(OwnerTable.Lease lease = owners.allocate(); lease != null; lease = owners.allocate())
                {
                    taken.add(lease);
                }
                // Holding the monitor keeps the background task thread from freezing meanwhile.
                synchronized(map)
                {
                    for(int i = 1; i < 10; i++)
                    {
                        map.add(new Doc("doc" + i, vec(i)));
                    }
                    assertEquals(0, def.consolidationCount(), "the mutating thread must not consolidate");
                }
            }
            finally
            {
                taken.forEach(OwnerTable.Lease::release);
            }
        }
    }

    /**
     * The root as the typed map: the storage manager is not generic over the root.
     */
    @SuppressWarnings("unchecked")
    private static GigaMap<Doc> rootOf(final EmbeddedStorageManager storage)
    {
        return (GigaMap<Doc>)storage.root();
    }
}
