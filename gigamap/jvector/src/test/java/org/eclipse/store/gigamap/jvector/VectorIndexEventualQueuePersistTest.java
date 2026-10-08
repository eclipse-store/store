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
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Eventual indexing: an operation is counted in the persist witnesses when it is enqueued, on the caller's thread,
 * and applied to the graph later by the worker. A persist must not capture a graph that is behind those witnesses,
 * and an operation that outlives the graph it was enqueued for (a rebuild from the source of truth, or a new
 * generation after {@code removeAll()}) must not be applied to the graph that replaced it.
 * <p>
 * The interleavings are built deterministically on daemon threads joined with a timeout. The persist test runs
 * last: a hung persist leaves graph-cleanup tasks blocked on {@code ForkJoinPool.commonPool}.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class VectorIndexEventualQueuePersistTest
{
    private static final int  COUNT      = 50;
    private static final long TIMEOUT_MS = 10_000;

    static class Doc
    {
        final int     no;
        float[] vector; // mutable: the deadlock test updates embeddings in place

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
     * Embedded; pauses {@link #pauseThread} once, inside scoring, until {@link #release} is counted down. Test state
     * is transient: the vectorizer is persisted with the index.
     */
    static class PausingVectorizer extends Vectorizer<Doc>
    {
        transient volatile Thread pauseThread;
        transient CountDownLatch  paused  = new CountDownLatch(1);
        transient CountDownLatch  release = new CountDownLatch(1);

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
                    this.release.await(TIMEOUT_MS * 3, TimeUnit.MILLISECONDS);
                }
                catch(final InterruptedException e)
                {
                    current.interrupt();
                }
            }
            return entity.vector;
        }

        @Override
        public boolean isEmbedded()
        {
            return true;
        }
    }

    /**
     * Computed: the index stores a copy of each vector and scores from it, so a stale graph node keeps its position
     * and can be told apart from the entity that reuses its id.
     */
    static class ComputedVectorizer extends Vectorizer<Doc>
    {
        @Override
        public float[] vectorize(final Doc entity)
        {
            return entity.vector.clone();
        }
    }

    private static VectorIndexConfiguration.Builder eventual()
    {
        return VectorIndexConfiguration.builder()
            .dimension(4)
            .similarityFunction(VectorSimilarityFunction.EUCLIDEAN)
            .eventualIndexing(true);
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

    private static BackgroundTaskManager manager(final VectorIndex<?> index)
    {
        final BackgroundTaskManager manager = internalState(index, "backgroundTaskManager");
        assertNotNull(manager, "precondition: eventual indexing has a background task manager");
        return manager;
    }

    /**
     * Waits until the worker has applied every queued operation and any repair those operations requested.
     */
    private static void awaitWorker(final VectorIndex<?> index)
    {
        final BackgroundTaskManager manager = manager(index);
        manager.drainQueue();
        manager.drainQueue();
    }

    private static boolean foundExactly(final VectorIndex<Doc> index, final Doc doc, final long id)
    {
        return index.search(doc.vector, 3).toList().stream().anyMatch(e -> e.entityId() == id && e.score() > 0.99f);
    }

    private static Thread daemon(final String name, final Runnable action)
    {
        final Thread thread = new Thread(action, name);
        thread.setDaemon(true);
        return thread;
    }

    private static void await(final CountDownLatch latch, final String what) throws InterruptedException
    {
        assertTrue(latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS), what);
    }

    private static void awaitQueueLength(final ReentrantReadWriteLock lock, final int length, final String what)
        throws InterruptedException
    {
        final long until = System.currentTimeMillis() + TIMEOUT_MS;
        while(lock.getQueueLength() < length && System.currentTimeMillis() < until)
        {
            Thread.sleep(5);
        }
        assertTrue(lock.getQueueLength() >= length, what);
    }

    private static void awaitNoPendingOps(final BackgroundTaskManager manager) throws InterruptedException
    {
        final long until = System.currentTimeMillis() + TIMEOUT_MS;
        while(manager.pendingGraphOps() > 0 && System.currentTimeMillis() < until)
        {
            Thread.sleep(5);
        }
        assertEquals(0, manager.pendingGraphOps(), "the worker did not finish its operations");
    }

    /**
     * Test double for the manager test: blocks inside the first add until released.
     */
    static class BlockingCallback implements BackgroundTaskManager.Callback
    {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);

        @Override
        public void applyGraphAdd(final VectorEntry entry, final long epoch)
        {
            this.entered.countDown();
            try
            {
                this.release.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            }
            catch(final InterruptedException e)
            {
                Thread.currentThread().interrupt();
            }
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
            // not used
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
            // not used
        }
    }

    /**
     * The pending count must include an operation the worker has polled and is executing: the queue size alone
     * would report zero for it, and a persist would take the graph for current.
     */
    @Test
    @Order(1)
    void pendingGraphOpsCountsAPolledOperation() throws InterruptedException
    {
        final BlockingCallback      callback = new BlockingCallback();
        final BackgroundTaskManager manager  = new BackgroundTaskManager(
            callback, "test", true, false, 60_000, 1, false, 60_000, 1, 1_000
        );
        try
        {
            manager.enqueueAdd(new VectorEntry(0, position(0)), 0);
            await(callback.entered, "the worker did not start the operation");
            assertEquals(1, manager.pendingGraphOps(), "a polled operation is not pending");
            assertEquals(0, manager.getPendingIndexingCount(), "precondition: the operation left the queue");

            manager.enqueueAdd(new VectorEntry(1, position(1)), 0);
            assertEquals(2, manager.pendingGraphOps(), "a queued operation is not pending");

            callback.release.countDown();
            awaitNoPendingOps(manager);
        }
        finally
        {
            callback.release.countDown();
            manager.shutdown(false, false, false);
        }
    }

    /**
     * {@code removeAll()} shuts the worker down and discards its queue, but an operation the worker has already
     * polled and is blocked on (the write lock is held) runs as soon as the lock is released, against the builder
     * of the new generation. It must be dropped: its ordinal now belongs to whatever entity gets that id next.
     */
    @Test
    @Order(2)
    void stalePolledOperationIsDroppedAfterRemoveAll() throws Exception
    {
        final GigaMap<Doc>     map   = populatedMap();
        final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
            .add("emb", eventual().build(), new ComputedVectorizer());
        awaitWorker(index);

        final ReentrantReadWriteLock     lock     = internalState(index, "builderLock");
        final CountDownLatch             held     = new CountDownLatch(1);
        final CountDownLatch             enqueued = new CountDownLatch(1);
        final CountDownLatch             removed  = new CountDownLatch(1);
        final CountDownLatch             go       = new CountDownLatch(1);
        final AtomicReference<Throwable> failure  = new AtomicReference<>();
        final Thread remover = daemon("remover", () ->
        {
            lock.writeLock().lock();
            try
            {
                held.countDown();
                enqueued.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);
                map.removeAll(); // re-acquires the write lock reentrantly, never waits
                removed.countDown();
                go.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            }
            catch(final Throwable t)
            {
                failure.set(t);
            }
            finally
            {
                lock.writeLock().unlock();
            }
        });
        remover.start();
        await(held, "the remover did not take the write lock");

        final Doc  stale   = new Doc(COUNT);
        final long staleId = map.add(stale); // the worker polls this op and queues on the read lock
        awaitQueueLength(lock, 1, "the worker did not queue on the read lock");
        final BackgroundTaskManager oldManager = manager(index);
        assertEquals(1, oldManager.pendingGraphOps(), "precondition: the add is pending on the old worker");
        enqueued.countDown();

        await(removed, "removeAll() did not finish");
        assertEquals(0, map.size(), "precondition: removeAll() emptied the map");
        go.countDown();
        remover.join(TIMEOUT_MS);
        assertFalse(remover.isAlive(), "the remover did not finish");
        assertNull(failure.get(), "the remover failed: " + failure.get());

        // the old worker's blocked op runs now, against the new generation's builder
        awaitNoPendingOps(oldManager);
        assertEquals(0, index.search(stale.vector, 10).toList().size(),
            "the new (empty) generation's graph answers for removed entity " + staleId);

        // ids are handed out from 0 again: the entity that gets the stale id must be found at its own position,
        // not at the stale node's (the idempotent add would keep the stale node if it were still there)
        Doc  reused   = null;
        long reusedId = -1;
        for(int i = 0; i <= staleId; i++)
        {
            final Doc  doc = new Doc(COUNT + 5 + i);
            final long id  = map.add(doc);
            if(id == staleId)
            {
                reused   = doc;
                reusedId = id;
            }
        }
        awaitWorker(index);
        assertNotNull(reused, "precondition: the stale id was handed out again");
        assertTrue(foundExactly(index, reused, reusedId), "the entity reusing the id is not found at its position");
        assertFalse(foundExactly(index, stale, staleId), "the stale node still answers at the removed position");
    }

    /**
     * {@code persistToDisk()} drains the worker queue without a lock and only then waits for the write lock. An add
     * in that window is counted at once; its op is polled by the worker and blocks on the read lock behind the
     * waiting persist, a second one stays queued. Both must be in the written graph, which a restart accepts because
     * the witnesses match the store: before, the first was missing after the restart.
     * <p>
     * Interleaving: a search is held inside scoring (read lock held); the persist drains the empty queue and queues
     * on the write lock; the two adds run; the search is released.
     */
    @Test
    @Order(Integer.MAX_VALUE - 1)
    void addsEnqueuedWhileAPersistWaitsArePersisted(@TempDir final Path tempDir) throws Exception
    {
        final Path storageDir = tempDir.resolve("storage");
        final Doc  inFlight   = new Doc(COUNT + 7);
        final Doc  queued     = new Doc(COUNT + 11);
        final long inFlightId;
        final long queuedId;
        try(EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final GigaMap<Doc> map = populatedMap();
            storage.setRoot(map);
            storage.storeRoot();
            final PausingVectorizer vectorizer = new PausingVectorizer();
            final VectorIndex<Doc> index = map.index().register(VectorIndices.Category())
                .add("emb", eventual().onDisk(true).indexDirectory(tempDir.resolve("index")).build(), vectorizer);
            awaitWorker(index); // the back-fill
            map.store();
            assertFalse((boolean)internalState(index, "incrementalMode"),
                "precondition: not persisted yet, so the persist writes from the in-memory graph");

            final ReentrantReadWriteLock     lock           = internalState(index, "builderLock");
            final BackgroundTaskManager      manager        = manager(index);
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
                vectorizer.pauseThread = searcher;
                searcher.start();
                await(vectorizer.paused, "the search did not reach scoring");

                persister.start();
                awaitQueueLength(lock, 1, "the persist did not queue on the write lock");
                assertTrue(lock.hasQueuedThread(persister), "precondition: the queued thread is the persist");

                inFlightId = map.add(inFlight);
                awaitQueueLength(lock, 2, "the worker did not queue on the read lock behind the persist");
                queuedId = map.add(queued);
                assertEquals(2, manager.pendingGraphOps(), "precondition: one op in flight, one queued");
                assertEquals(1, manager.getPendingIndexingCount(), "precondition: the second op is still queued");
            }
            finally
            {
                vectorizer.release.countDown();
            }
            searcher.join(TIMEOUT_MS);
            persister.join(TIMEOUT_MS * 3);
            assertFalse(persister.isAlive(), "the persist did not finish");
            assertNull(persistFailure.get(), "the persist failed: " + persistFailure.get());
            awaitNoPendingOps(manager);

            assertTrue(foundExactly(index, inFlight, inFlightId), "the in-flight entity is missing in the session");
            assertTrue(foundExactly(index, queued, queuedId), "the queued entity is missing in the session");
            map.store();
        }

        try(EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir))
        {
            final GigaMap<Doc>     map   = storage.root();
            final VectorIndex<Doc> index = map.index().get(VectorIndices.class).get("emb");
            index.search(position(0), 1); // initializes the index from disk
            assertTrue((boolean)internalState(index, "incrementalMode"),
                "precondition: the written files were accepted after the restart");
            assertTrue(foundExactly(index, inFlight, inFlightId),
                "the entity whose op was in flight when the persist captured is missing after a restart");
            assertTrue(foundExactly(index, queued, queuedId),
                "the entity whose op was queued when the persist captured is missing after a restart");
        }
    }

    /**
     * Embedded vectorizer, background persistence. Every embedded update enqueues an op whose application calls
     * jvector's {@code removeDeletedNodes()}, which scores on a ForkJoinPool whose workers read entities through the
     * parent-map monitor. The persist applies still-queued ops inline; doing so while holding that monitor
     * deadlocks the persist, and with it every search and mutation. The ops must be applied before the monitor is
     * taken.
     * <p>
     * Interleaving: a search is held inside scoring (read lock held); the background persist drains its empty queue
     * and queues on the write lock; three updates are enqueued (the persist thread is the worker, so they stay
     * queued); the search is released. Last in the class: a hung persist poisons the common pool. The index is closed
     * only if the persist finished: {@code close()} waits for the write lock a hung persist never releases.
     */
    @Test
    @Order(Integer.MAX_VALUE)
    void embeddedUpdatesQueuedBehindABackgroundPersistDoNotDeadlock(@TempDir final Path tempDir) throws Exception
    {
        final GigaMap<Doc>      map        = populatedMap();
        final PausingVectorizer vectorizer = new PausingVectorizer();
        final VectorIndex<Doc>  index      = map.index().register(VectorIndices.Category()).add("emb",
            eventual().onDisk(true).indexDirectory(tempDir.resolve("index"))
                .persistenceIntervalMs(1_500).minChangesBetweenPersists(1).build(),
            vectorizer);
        awaitWorker(index); // the back-fill; the first background persist is still 1.5 s away
        assertFalse((boolean)internalState(index, "incrementalMode"), "precondition: nothing persisted yet");

        final ReentrantReadWriteLock lock     = internalState(index, "builderLock");
        final BackgroundTaskManager  manager  = manager(index);
        final Thread                 searcher = daemon("searcher", () -> index.search(position(10), 5));
        try
        {
            vectorizer.pauseThread = searcher;
            searcher.start();
            await(vectorizer.paused, "the search did not reach scoring");

            // the background persist (interval 1.5 s, one change is enough) queues on the write lock
            final long until = System.currentTimeMillis() + TIMEOUT_MS;
            while(!lock.hasQueuedThreads() && System.currentTimeMillis() < until)
            {
                Thread.sleep(10);
            }
            assertTrue(lock.hasQueuedThreads(), "the background persist did not queue on the write lock");

            for(int i = 0; i < 3; i++)
            {
                final int no = i;
                map.update((long)no, d -> d.vector = position(COUNT + 20 + no)); // embedded vec→vec: an Update op
            }
            assertEquals(3, manager.pendingGraphOps(), "precondition: the updates are pending");
            assertEquals(3, manager.getPendingIndexingCount(), "precondition: nothing is in flight, the worker is the persist");
        }
        finally
        {
            vectorizer.release.countDown();
        }
        searcher.join(TIMEOUT_MS);

        // the persist must finish: write lock released, nothing queued, the ops applied
        final long until = System.currentTimeMillis() + TIMEOUT_MS;
        while((lock.isWriteLocked() || lock.hasQueuedThreads() || manager.pendingGraphOps() > 0)
            && System.currentTimeMillis() < until)
        {
            Thread.sleep(10);
        }
        final boolean hung = lock.isWriteLocked();
        if(!hung)
        {
            try
            {
                assertEquals(0, manager.pendingGraphOps(), "the queued updates were not applied");
                assertTrue((boolean)internalState(index, "incrementalMode"), "the persist did not complete");
                assertNotNull(index.search(position(0), 1), "the index is not usable after the persist");
            }
            finally
            {
                index.close();
            }
        }
        assertFalse(hung, "the persist dead-locked while applying the queued updates (write lock still held)");
    }
}
