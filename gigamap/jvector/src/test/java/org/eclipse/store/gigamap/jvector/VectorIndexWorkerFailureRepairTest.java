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
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Eventual indexing: a graph operation that fails on the background worker was already counted on the caller's
 * thread, so the graph is behind its witnesses. The index must repair itself from the source of truth, must never
 * persist the damaged graph, and must say so from {@code search()} when the repair fails too.
 * <p>
 * The failure is injected into the embedded vectorizer when it is called on the worker thread, which happens while
 * an insertion scores its neighbours and while a rebuild collects the vectors. The worker is awaited through the
 * manager's own drain, twice: a repair requested by the first drain is queued behind it, and the second drain is
 * queued behind the repair.
 */
class VectorIndexWorkerFailureRepairTest
{
    private static final int    COUNT         = 30;
    private static final int    POISON        = 3;
    private static final String WORKER_PREFIX = "VectorIndex-Background-";
    private static final long   TIMEOUT_MS    = 10_000;

    /**
     * How many more worker-thread calls for {@link #POISON} throw. Static: the vectorizer is persisted with the
     * index, so it must not carry test state in instance fields.
     */
    private static final AtomicInteger failuresLeft   = new AtomicInteger();
    private static final AtomicInteger workerFailures = new AtomicInteger();
    /** the entity whose lookup fails on the worker; {@link #POISON} unless a test moves it */
    private static volatile int        poison         = POISON;

    static class Doc
    {
        final int     no;
        final float[] vector;

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
     * Embedded: throws for {@link #POISON} on the worker thread while {@link #failuresLeft} is positive.
     */
    static class WorkerFlakyVectorizer extends Vectorizer<Doc>
    {
        @Override
        public float[] vectorize(final Doc entity)
        {
            if(entity.no == poison
                && Thread.currentThread().getName().startsWith(WORKER_PREFIX)
                && failuresLeft.getAndUpdate(left -> left > 0 ? left - 1 : 0) > 0)
            {
                workerFailures.incrementAndGet();
                throw new IllegalStateException("simulated embedding lookup failure on the worker for " + entity.no);
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
    void reset()
    {
        failuresLeft.set(0);
        workerFailures.set(0);
        poison = POISON;
    }

    private static VectorIndexConfiguration.Builder eventual()
    {
        return VectorIndexConfiguration.builder()
            .dimension(4)
            .similarityFunction(VectorSimilarityFunction.EUCLIDEAN)
            .eventualIndexing(true);
    }

    private static Set<Long> allIds()
    {
        final Set<Long> ids = new TreeSet<>();
        for(long id = 0; id < COUNT; id++)
        {
            ids.add(id);
        }
        return ids;
    }

    private static Set<Long> foundIds(final VectorIndex<Doc> index)
    {
        final Set<Long> ids = new TreeSet<>();
        index.search(position(0), COUNT * 2).toList().forEach(e -> ids.add(e.entityId()));
        return ids;
    }

    private static void addAll(final GigaMap<Doc> map)
    {
        for(int i = 0; i < COUNT; i++)
        {
            map.add(new Doc(i));
        }
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

    /**
     * Waits until the worker has applied every queued operation and run any repair those operations requested.
     */
    private static void awaitWorker(final VectorIndex<Doc> index)
    {
        final BackgroundTaskManager manager = internalState(index, "backgroundTaskManager");
        assertNotNull(manager, "precondition: eventual indexing has a background task manager");
        manager.drainQueue();
        manager.drainQueue();
    }

    /**
     * One flaky lookup fails one insertion on the worker. Every call returned normally, so the only acceptable
     * outcome is a complete index: the worker reports the failure and the index rebuilds the graph from the
     * entities before anything else observes it.
     */
    @Test
    void failedWorkerOperationIsRepairedFromTheEntities()
    {
        final GigaMap<Doc>       map = GigaMap.New();
        final VectorIndices<Doc> vi  = map.index().register(VectorIndices.Category());
        try(final VectorIndex<Doc> index = vi.add("emb", eventual().build(), new WorkerFlakyVectorizer()))
        {
            failuresLeft.set(1);
            addAll(map);
            awaitWorker(index);

            assertEquals(1, workerFailures.get(), "precondition: one insertion failed on the worker");
            assertEquals(allIds(), foundIds(index), "entities are missing after a failed worker operation");
            assertFalse((boolean)internalState(index, "graphIncomplete"), "the repair did not clear graphIncomplete");
        }
    }

    /**
     * On disk, the damaged graph used to be written with witnesses that match the store, so a restart accepted it
     * and every search failed inside the graph library. With the failure recorded, the persist writes the repaired
     * graph, and the restart loads it.
     */
    @Test
    void failedWorkerOperationIsNeverPersisted(@TempDir final Path tempDir)
    {
        final Path storageDir = tempDir.resolve("storage");
        try(EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final GigaMap<Doc> map = GigaMap.New();
            storage.setRoot(map);
            storage.storeRoot();
            final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
                .add("emb", eventual().onDisk(true).indexDirectory(tempDir.resolve("index")).build(),
                    new WorkerFlakyVectorizer());

            failuresLeft.set(1);
            addAll(map);
            awaitWorker(index);
            assertEquals(1, workerFailures.get(), "precondition: one insertion failed on the worker");

            index.persistToDisk();
            map.store();
        }

        try(EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final GigaMap<Doc>     map   = storage.root();
            final VectorIndex<Doc> index = map.index().get(VectorIndices.class).get("emb");
            assertEquals(allIds(), foundIds(index), "entities are missing after a restart");
            assertTrue((boolean)internalState(index, "incrementalMode"),
                "precondition: the persisted files were accepted after the restart");
        }
    }

    /**
     * The lookup keeps failing on the worker, so the repair fails as well: the index must say so instead of
     * answering from a graph it knows to be incomplete. Once the cause is fixed, {@code reindex()} rebuilds the
     * graph and searches work again.
     */
    @Test
    void failedRepairMakesSearchesThrowUntilReindex()
    {
        final GigaMap<Doc>       map = GigaMap.New();
        final VectorIndices<Doc> vi  = map.index().register(VectorIndices.Category());
        try(final VectorIndex<Doc> index = vi.add("emb", eventual().build(), new WorkerFlakyVectorizer()))
        {
            failuresLeft.set(Integer.MAX_VALUE);
            addAll(map);
            awaitWorker(index);
            assertTrue(workerFailures.get() > 1, "precondition: the worker failed on insertions and on the repair");

            final IllegalStateException e = assertThrows(IllegalStateException.class, () -> foundIds(index),
                "a search answered from a graph whose repair failed");
            assertTrue(e.getMessage().contains("incomplete"), e.getMessage());
            assertNotNull(e.getCause(), "the failure is attached as the cause");
            assertTrue(e.getCause().getMessage().contains("simulated"), e.getCause().getMessage());

            failuresLeft.set(0);
            map.reindex();
            awaitWorker(index);
            assertEquals(allIds(), foundIds(index), "entities are missing after reindex()");
        }
    }

    /**
     * Test double for the two manager tests below: records the calls the tests assert on.
     */
    static class RecordingCallback implements BackgroundTaskManager.Callback
    {
        final AtomicInteger  persists        = new AtomicInteger();
        final AtomicInteger  incompleteMarks = new AtomicInteger();
        final CountDownLatch secondPersist   = new CountDownLatch(2);
        final CountDownLatch applied         = new CountDownLatch(1);
        volatile boolean     failNextApply ;
        volatile boolean     failNextPersist;

        @Override
        public void applyGraphAdd(final VectorEntry entry, final long epoch)
        {
            if(this.failNextApply)
            {
                this.failNextApply = false;
                throw new Error("simulated error in a graph operation");
            }
            this.applied.countDown();
        }

        @Override
        public void applyGraphBatchAdd(final List<VectorEntry> entries, final long epoch)
        {
            // not used
        }

        @Override
        public void applyGraphUpdate(final VectorEntry entry, final long epoch)
        {
            // not used
        }

        @Override
        public void applyGraphRemove(final int ordinal, final long epoch)
        {
            // not used
        }

        @Override
        public void markDirtyForBackgroundManagers(final int count)
        {
            // not used
        }

        @Override
        public void markGraphIncomplete(final Throwable cause)
        {
            this.incompleteMarks.incrementAndGet();
        }

        @Override
        public void repairGraph()
        {
            // not used
        }

        @Override
        public void doOptimize()
        {
            // not used
        }

        @Override
        public void doPersistToDisk(final boolean onShutdown)
        {
            this.persists.incrementAndGet();
            this.secondPersist.countDown();
            if(this.failNextPersist)
            {
                this.failNextPersist = false;
                throw new Error("simulated error in a persist");
            }
        }
    }

    private static BackgroundTaskManager manager(final RecordingCallback callback, final boolean backgroundPersistence)
    {
        return new BackgroundTaskManager(
            callback, "test", true, false, 60_000, 1, backgroundPersistence, 20, 1, 1_000
        );
    }

    /**
     * An {@link Error} in a graph operation is recorded like an exception, and the operations behind it are still
     * applied; the worker thread is not lost.
     */
    @Test
    void errorInAGraphOperationIsRecordedAndTheQueueContinues() throws InterruptedException
    {
        final RecordingCallback     callback = new RecordingCallback();
        final BackgroundTaskManager manager  = manager(callback, false);
        try
        {
            callback.failNextApply = true;
            manager.enqueueAdd(new VectorEntry(0, position(0)), 0);
            manager.enqueueAdd(new VectorEntry(1, position(1)), 0);
            assertTrue(callback.applied.await(TIMEOUT_MS, TimeUnit.MILLISECONDS),
                "the operation behind the failed one was not applied");
            assertEquals(1, callback.incompleteMarks.get(), "the failed operation was not recorded");
        }
        finally
        {
            manager.shutdown(false, false, false);
        }
    }

    /**
     * A throwable escaping a scheduled task cancels the task for good. An {@link Error} out of a background persist
     * (its inline drain included) must therefore be caught, or background persistence silently ends.
     */
    @Test
    void errorInABackgroundPersistKeepsThePeriodicTaskAlive() throws InterruptedException
    {
        final RecordingCallback     callback = new RecordingCallback();
        final BackgroundTaskManager manager  = manager(callback, true);
        try
        {
            callback.failNextPersist = true;
            manager.markDirty(1);
            assertTrue(callback.secondPersist.await(TIMEOUT_MS, TimeUnit.MILLISECONDS),
                "background persistence stopped after an Error: " + callback.persists.get() + " persist(s)");
        }
        finally
        {
            manager.shutdown(false, false, false);
        }
    }

    /**
     * An on-disk index that has never been persisted serves from memory; its repair is a rebuild in memory, not an
     * unrequested first write to disk with the mode switch and PQ training that come with it.
     */
    @Test
    void repairOfANeverPersistedOnDiskIndexStaysInMemory(@TempDir final Path tempDir)
    {
        final GigaMap<Doc>       map = GigaMap.New();
        final VectorIndices<Doc> vi  = map.index().register(VectorIndices.Category());
        try(final VectorIndex<Doc> index = vi.add("emb",
            eventual().onDisk(true).indexDirectory(tempDir.resolve("index")).build(), new WorkerFlakyVectorizer()))
        {
            failuresLeft.set(1);
            addAll(map);
            awaitWorker(index);

            assertEquals(1, workerFailures.get(), "precondition: one insertion failed on the worker");
            assertFalse((boolean)internalState(index, "incrementalMode"),
                "the repair of a never-persisted index wrote it to disk and switched it to serving from disk");
            assertEquals(allIds(), foundIds(index), "entities are missing after the repair");
        }
    }

    /**
     * An index serving from disk is repaired by a persist, so it returns to serving from disk instead of keeping
     * the whole rebuilt graph in heap until some later persist. The failing lookup is one of the entities added
     * after the persist: in incremental mode the worker scores only the new nodes.
     */
    @Test
    void repairOfAnIndexServingFromDiskReturnsToIncrementalMode(@TempDir final Path tempDir)
    {
        final GigaMap<Doc>       map = GigaMap.New();
        final VectorIndices<Doc> vi  = map.index().register(VectorIndices.Category());
        try(final VectorIndex<Doc> index = vi.add("emb",
            eventual().onDisk(true).indexDirectory(tempDir.resolve("index")).build(), new WorkerFlakyVectorizer()))
        {
            addAll(map);
            awaitWorker(index);
            index.persistToDisk();
            assertTrue((boolean)internalState(index, "incrementalMode"), "precondition: serving from disk");

            poison = COUNT; // the first entity added after the persist; later insertions score it on the worker
            failuresLeft.set(1);
            for(int i = 0; i < 10; i++)
            {
                map.add(new Doc(COUNT + i));
            }
            awaitWorker(index);

            assertEquals(1, workerFailures.get(), "precondition: one insertion failed on the worker");
            assertTrue((boolean)internalState(index, "incrementalMode"),
                "the repair of an index serving from disk left the full graph in heap instead of persisting it");
            final java.util.Set<Long> expected = allIds();
            for(long id = COUNT; id < COUNT + 10; id++)
            {
                expected.add(id);
            }
            assertEquals(expected, foundIds(index), "entities are missing after the repair");
        }
    }
}
