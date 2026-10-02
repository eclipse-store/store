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

import org.eclipse.store.gigamap.types.GigaIterator;
import org.eclipse.store.gigamap.types.GigaMap;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Lock order between the GigaMap monitor and a vector index's builder lock (internal#155): a thread holding the
 * monitor must never wait for the builder lock, because the lock's holders may need the monitor.
 * <p>
 * Each test builds one interleaving deterministically (latches, the persist Phase-2 hook) instead of racing. All
 * calls that could deadlock run on daemon threads and are joined with a timeout, so a regression fails the test
 * instead of hanging the build. An index whose threads hung is never closed: close() would wait for the lock.
 * <p>
 * The two tests that run a persist or optimize go last: a hung one leaves graph-cleanup tasks blocked on
 * {@code ForkJoinPool.commonPool}, which would also stall every later test that persists.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class VectorIndexLockOrderTest
{
    private static final int  COUNT      = 200;
    private static final long TIMEOUT_MS = 10_000;

    static class Doc
    {
        final float[] vector;

        Doc(final int no)
        {
            this.vector = new float[]{1 + no, 1 + no % 7, no * 0.5f, 1 + no % 3};
        }
    }

    /**
     * Embedded vectorizer whose calls on one designated thread can be paused, so that thread can be held inside a
     * search's scoring (holding the read lock).
     */
    static class PausableVectorizer extends Vectorizer<Doc>
    {
        volatile Thread         pauseThread;
        final CountDownLatch    paused  = new CountDownLatch(1);
        final CountDownLatch    resume  = new CountDownLatch(1);
        volatile Runnable       onCall;

        @Override
        public float[] vectorize(final Doc entity)
        {
            if(Thread.currentThread() == this.pauseThread)
            {
                this.pauseThread = null;
                this.paused.countDown();
                await(this.resume);
            }
            final Runnable call = this.onCall;
            if(call != null)
            {
                this.onCall = null;
                call.run();
            }
            return entity.vector;
        }

        @Override
        public boolean isEmbedded()
        {
            return true;
        }
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

    private static VectorIndex<Doc> register(
        final GigaMap<Doc>       map       ,
        final PausableVectorizer vectorizer,
        final Path               indexDir
    )
    {
        final VectorIndexConfiguration.Builder builder = VectorIndexConfiguration.builder()
            .dimension(4)
            .similarityFunction(VectorSimilarityFunction.EUCLIDEAN);
        if(indexDir != null)
        {
            builder.onDisk(true).indexDirectory(indexDir);
        }
        return map.index().register(VectorIndices.Category()).add("vec", builder.build(), vectorizer);
    }

    private static float[] query()
    {
        return new float[]{5, 5, 2, 2};
    }

    /**
     * Thread that records its failure; {@link #assertDone} joins it with the timeout.
     */
    static final class Worker extends Thread
    {
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        private final Runnable action;

        Worker(final String name, final Runnable action)
        {
            super(name);
            this.action = action;
            this.setDaemon(true);
        }

        @Override
        public void run()
        {
            try
            {
                this.action.run();
            }
            catch(final Throwable t)
            {
                this.failure.set(t);
            }
        }

        Worker begin()
        {
            this.start();
            return this;
        }
    }

    private static void assertDone(final String what, final Worker... workers) throws InterruptedException
    {
        final long deadline = System.currentTimeMillis() + TIMEOUT_MS;
        for(final Worker worker : workers)
        {
            worker.join(Math.max(1, deadline - System.currentTimeMillis()));
        }
        for(final Worker worker : workers)
        {
            assertFalse(worker.isAlive(), what + ": " + worker.getName() + " did not complete (deadlock?)");
            assertNull(worker.failure.get(), what + ": " + worker.getName() + " failed: " + worker.failure.get());
        }
    }

    /**
     * Waits until the thread is blocked or waiting in two samples 50 ms apart. A single sample could catch a
     * short, unrelated block (e.g. class loading) before the thread reaches the lock or monitor the test means.
     */
    private static void awaitWaiting(final Thread thread) throws InterruptedException
    {
        final long deadline = System.currentTimeMillis() + TIMEOUT_MS;
        while(true)
        {
            if(isWaiting(thread))
            {
                Thread.sleep(50);
                if(isWaiting(thread))
                {
                    return;
                }
            }
            if(System.currentTimeMillis() > deadline)
            {
                fail(thread.getName() + " never started to wait");
            }
            Thread.sleep(5);
        }
    }

    private static boolean isWaiting(final Thread thread)
    {
        final Thread.State state = thread.getState();
        return state != Thread.State.RUNNABLE && state != Thread.State.NEW;
    }

    private static void await(final CountDownLatch latch)
    {
        try
        {
            if(!latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS))
            {
                throw new IllegalStateException("latch timed out");
            }
        }
        catch(final InterruptedException e)
        {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /**
     * Cycle 1: a persist holds the write lock in Phase 2, whose workers read entities via GigaMap.get, while
     * reindex() runs. reindex() holds the monitor and must not wait for the write lock.
     */
    @Test
    @Order(Integer.MAX_VALUE - 1)
    void reindexDuringPersistPhase2(@TempDir final Path tempDir) throws Exception
    {
        final GigaMap<Doc> map = populatedMap();
        final PausableVectorizer vectorizer = new PausableVectorizer();
        final VectorIndex.Default<Doc> index = (VectorIndex.Default<Doc>)register(map, vectorizer, tempDir.resolve("index"));

        final AtomicReference<Worker> reindexer = new AtomicReference<>();
        index.persistPhase2TestHook = () ->
        {
            index.persistPhase2TestHook = null;
            reindexer.set(new Worker("reindexer", map::reindex).begin());
            try
            {
                awaitWaiting(reindexer.get());
            }
            catch(final InterruptedException e)
            {
                Thread.currentThread().interrupt();
            }
        };
        final Worker persister = new Worker("persister", index::persistToDisk).begin();
        persister.join(TIMEOUT_MS);
        assertNotNull(reindexer.get(), "persist did not reach Phase 2");
        assertDone("persist Phase 2 vs reindex", persister, reindexer.get());
        index.close();
    }

    /**
     * Cycle 2: an embedded search holds the read lock and reads entities via GigaMap.get while removeAll() runs.
     */
    @Test
    void removeAllDuringEmbeddedSearch() throws Exception
    {
        final GigaMap<Doc> map = populatedMap();
        final PausableVectorizer vectorizer = new PausableVectorizer();
        final VectorIndex<Doc> index = register(map, vectorizer, null);

        final Worker searcher = new Worker("searcher", () -> index.search(query(), 5));
        vectorizer.pauseThread = searcher;
        searcher.begin();
        await(vectorizer.paused);

        final Worker remover = new Worker("remover", map::removeAll).begin();
        awaitWaiting(remover);
        vectorizer.resume.countDown();

        assertDone("embedded search vs removeAll", searcher, remover);
        assertEquals(0, map.size());
        index.close();
    }

    /**
     * A thread holding the monitor (apply logic) searches while reindex() waits for the write lock. reindex()
     * must not hold, or queue for, the write lock while it cannot enter the monitor: a queued writer keeps the
     * apply thread's search out of the read lock.
     * <p>
     * Order: a search holds the read lock (paused in scoring), reindex() starts and waits for the write lock, the
     * apply thread enters the monitor, the paused search resumes (and now waits for the monitor), the apply
     * thread searches.
     */
    @Test
    void searchInsideApplyWhileReindexWaits() throws Exception
    {
        final GigaMap<Doc> map = populatedMap();
        final PausableVectorizer vectorizer = new PausableVectorizer();
        final VectorIndex<Doc> index = register(map, vectorizer, null);

        final Worker searcher = new Worker("searcher", () -> index.search(query(), 5));
        vectorizer.pauseThread = searcher;
        searcher.begin();
        await(vectorizer.paused);

        final Worker reindexer = new Worker("reindexer", map::reindex).begin();
        awaitWaiting(reindexer);

        final CountDownLatch inApply = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final Worker applier = new Worker("applier", () -> map.apply(3L, e ->
        {
            inApply.countDown();
            await(release);
            return index.search(e.vector, 5).size();
        })).begin();
        await(inApply);

        vectorizer.resume.countDown();
        awaitWaiting(searcher);
        release.countDown();

        assertDone("search inside apply vs reindex", applier, searcher, reindexer);
        index.close();
    }

    /**
     * persistToDisk() / optimize() on a thread holding the monitor would wait for their own workers, which read
     * entities through that monitor: rejected instead.
     */
    @Test
    @Order(Integer.MAX_VALUE)
    void persistAndOptimizeRejectedWhileHoldingTheMonitor(@TempDir final Path tempDir) throws Exception
    {
        final GigaMap<Doc> map = populatedMap();
        final VectorIndex<Doc> index = register(map, new PausableVectorizer(), tempDir.resolve("index"));

        final AtomicReference<Throwable> underMonitorPersist  = new AtomicReference<>();
        final AtomicReference<Throwable> insideApplyPersist   = new AtomicReference<>();
        final AtomicReference<Throwable> underMonitorOptimize = new AtomicReference<>();
        final Worker caller = new Worker("caller", () ->
        {
            synchronized(map)
            {
                underMonitorPersist.set(assertThrows(IllegalStateException.class, index::persistToDisk));
                underMonitorOptimize.set(assertThrows(IllegalStateException.class, index::optimize));
            }
            map.apply(3L, e ->
            {
                insideApplyPersist.set(assertThrows(IllegalStateException.class, index::persistToDisk));
                return null;
            });
        }).begin();

        assertDone("persist/optimize under the monitor", caller);
        assertNotNull(underMonitorPersist.get());
        assertNotNull(underMonitorOptimize.get());
        assertNotNull(insideApplyPersist.get());
        index.close();
    }

    /** removeAll() / reindex() from within a search of the same index (read lock held): rejected, not a hang. */
    @Test
    void reindexFromWithinASearchIsRejected() throws Exception
    {
        final GigaMap<Doc> map = populatedMap();
        final PausableVectorizer vectorizer = new PausableVectorizer();
        final VectorIndex<Doc> index = register(map, vectorizer, null);

        final AtomicReference<Throwable> thrown = new AtomicReference<>();
        final Worker searcher = new Worker("searcher", () ->
        {
            vectorizer.onCall = () ->
            {
                try
                {
                    map.reindex();
                }
                catch(final Throwable t)
                {
                    thrown.set(t);
                }
            };
            index.search(query(), 5);
        }).begin();

        assertDone("reindex from within a search", searcher);
        assertInstanceOf(IllegalStateException.class, thrown.get());
        index.close();
    }

    /**
     * reindex() fails its first try (a search holds the read lock), then a reader is open, and the reader's thread
     * searches before closing it. New searches are held back while reindex() waits for the lock, but not while it
     * waits for that reader.
     */
    @Test
    void readerWhoseThreadSearchesWhileReindexWaits() throws Exception
    {
        final GigaMap<Doc> map = populatedMap();
        final PausableVectorizer vectorizer = new PausableVectorizer();
        final VectorIndex<Doc> index = register(map, vectorizer, null);

        final Worker searcher = new Worker("searcher", () -> index.search(query(), 5));
        vectorizer.pauseThread = searcher;
        searcher.begin();
        await(vectorizer.paused);

        final Worker reindexer = new Worker("reindexer", map::reindex).begin();
        awaitWaiting(reindexer);

        final CountDownLatch readerOpen = new CountDownLatch(1);
        final Worker reader = new Worker("reader", () ->
        {
            try(GigaIterator<Doc> iterator = map.iterator())
            {
                iterator.hasNext();
                readerOpen.countDown();
                Thread.sleep(200);
                index.search(query(), 5);
            }
            catch(final InterruptedException e)
            {
                Thread.currentThread().interrupt();
            }
        }).begin();
        await(readerOpen);
        vectorizer.resume.countDown();

        assertDone("reader searching while reindex waits", searcher, reader, reindexer);
        index.close();
    }

    /** reindex() completes while searches keep overlapping each other (no pause between them). */
    @Test
    void reindexProgressesUnderOverlappingSearches() throws Exception
    {
        final GigaMap<Doc> map = populatedMap();
        final VectorIndex<Doc> index = register(map, new PausableVectorizer(), null);

        final AtomicBoolean stop = new AtomicBoolean();
        final Worker[] searchers = new Worker[4];
        for(int i = 0; i < searchers.length; i++)
        {
            searchers[i] = new Worker("searcher-" + i, () ->
            {
                while(!stop.get())
                {
                    index.search(query(), 10);
                }
            }).begin();
        }
        try
        {
            final Worker reindexer = new Worker("reindexer", map::reindex).begin();
            reindexer.join(TIMEOUT_MS);
            assertFalse(reindexer.isAlive(), "reindex() starved by overlapping searches");
            assertNull(reindexer.failure.get());
        }
        finally
        {
            stop.set(true);
        }
        assertDone("searchers", searchers);
        index.close();
    }

    /** internalRemoveAll() would wait for the write lock while holding the monitor: rejected without it. */
    @Test
    void internalRemoveAllRequiresTheWriteLock() throws Exception
    {
        final GigaMap<Doc> map = populatedMap();
        final VectorIndex.Default<Doc> index = (VectorIndex.Default<Doc>)register(map, new PausableVectorizer(), null);

        final AtomicReference<Throwable> thrown = new AtomicReference<>();
        final Worker caller = new Worker("caller", () ->
        {
            synchronized(map)
            {
                thrown.set(assertThrows(IllegalStateException.class, index::internalRemoveAll));
            }
        }).begin();

        assertDone("internalRemoveAll without the write lock", caller);
        assertNotNull(thrown.get());
        assertEquals(COUNT, index.search(query(), COUNT * 2).size());
        index.close();
    }
}
