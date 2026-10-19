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
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Lock order of {@link VectorIndex#invalidateGraph()} (internal#155, see {@code VectorIndexLockOrderTest}): a
 * thread holding the GigaMap monitor must never wait for the builder lock, because the lock's holders may need the
 * monitor. Replication and merge layers call {@code invalidateGraph()} from exactly such a thread.
 * <p>
 * Interleavings are built with latches; every call that could deadlock runs on a daemon thread joined with a
 * timeout, so a regression fails the test instead of hanging the build.
 */
class VectorIndexInvalidateGraphLockOrderTest
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

    /** Embedded vectorizer: one designated thread can be paused inside a search's scoring, holding the read lock. */
    static class PausableVectorizer extends Vectorizer<Doc>
    {
        volatile Thread      pauseThread;
        final CountDownLatch paused = new CountDownLatch(1);
        final CountDownLatch resume = new CountDownLatch(1);
        volatile Runnable    onCall;

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

    private static GigaMap<Doc> populatedMap()
    {
        final GigaMap<Doc> map = GigaMap.New();
        for(int i = 0; i < COUNT; i++)
        {
            map.add(new Doc(i));
        }
        return map;
    }

    private static VectorIndex<Doc> register(final GigaMap<Doc> map, final PausableVectorizer vectorizer)
    {
        return map.index().register(VectorIndices.Category()).add("vec", VectorIndexConfiguration.builder()
            .dimension(4)
            .similarityFunction(VectorSimilarityFunction.EUCLIDEAN)
            .build(), vectorizer);
    }

    private static float[] query()
    {
        return new float[]{5, 5, 2, 2};
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

    /** Waits until the thread is blocked or waiting in two samples 50 ms apart. */
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
            if(thread.getState() == Thread.State.TERMINATED)
            {
                fail(thread.getName() + " finished before it started to wait");
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
        return state == Thread.State.BLOCKED
            || state == Thread.State.WAITING
            || state == Thread.State.TIMED_WAITING;
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
     * An embedded search holds the read lock and reads entities via GigaMap.get (which needs the monitor) while a
     * merge thread invalidates the graph from inside {@code synchronized(map)}. The invalidation must not hold the
     * monitor while it waits for the write lock.
     */
    @Test
    void invalidateUnderTheMonitorWhileAnEmbeddedSearchHoldsTheReadLock() throws Exception
    {
        final GigaMap<Doc> map = populatedMap();
        final PausableVectorizer vectorizer = new PausableVectorizer();
        final VectorIndex<Doc> index = register(map, vectorizer);
        assertEquals(5, index.search(query(), 5).size());

        final Worker searcher = new Worker("searcher", () -> index.search(query(), 5));
        vectorizer.pauseThread = searcher;
        searcher.begin();
        await(vectorizer.paused);

        final Worker merger = new Worker("merger", () ->
        {
            synchronized(map)
            {
                index.invalidateGraph();
            }
        }).begin();
        awaitWaiting(merger);
        vectorizer.resume.countDown();

        assertDone("embedded search vs invalidateGraph under the monitor", searcher, merger);
        assertEquals(5, index.search(query(), 5).size(), "the graph is rebuilt on the next search");
        index.close();
    }

    /** Without the monitor, invalidateGraph() simply waits for the running search and then retires the graph. */
    @Test
    void invalidateWithoutTheMonitorWaitsForARunningSearch() throws Exception
    {
        final GigaMap<Doc> map = populatedMap();
        final PausableVectorizer vectorizer = new PausableVectorizer();
        final VectorIndex<Doc> index = register(map, vectorizer);
        assertEquals(5, index.search(query(), 5).size());

        final Worker searcher = new Worker("searcher", () -> index.search(query(), 5));
        vectorizer.pauseThread = searcher;
        searcher.begin();
        await(vectorizer.paused);

        final Worker merger = new Worker("merger", index::invalidateGraph).begin();
        awaitWaiting(merger);
        vectorizer.resume.countDown();

        assertDone("embedded search vs invalidateGraph", searcher, merger);
        assertEquals(5, index.search(query(), 5).size());
        index.close();
    }

    /** invalidateGraph() from within a search of the same index (read lock held): rejected, not a hang. */
    @Test
    void invalidateFromWithinASearchIsRejected() throws Exception
    {
        final GigaMap<Doc> map = populatedMap();
        final PausableVectorizer vectorizer = new PausableVectorizer();
        final VectorIndex<Doc> index = register(map, vectorizer);
        assertEquals(5, index.search(query(), 5).size());

        final AtomicReference<Throwable> thrown = new AtomicReference<>();
        final Worker searcher = new Worker("searcher", () ->
        {
            vectorizer.onCall = () ->
            {
                try
                {
                    index.invalidateGraph();
                }
                catch(final Throwable t)
                {
                    thrown.set(t);
                }
            };
            index.search(query(), 5);
        }).begin();

        assertDone("invalidateGraph from within a search", searcher);
        assertInstanceOf(IllegalStateException.class, thrown.get());
        index.close();
    }

    /** invalidateGraph() under the monitor completes while searches keep overlapping each other. */
    @Test
    void invalidateUnderTheMonitorProgressesUnderOverlappingSearches() throws Exception
    {
        final GigaMap<Doc> map = populatedMap();
        final VectorIndex<Doc> index = register(map, new PausableVectorizer());
        assertEquals(10, index.search(query(), 10).size());

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
            final Worker merger = new Worker("merger", () ->
            {
                for(int round = 0; round < 20; round++)
                {
                    synchronized(map)
                    {
                        index.invalidateGraph();
                    }
                }
            }).begin();
            merger.join(TIMEOUT_MS);
            assertFalse(merger.isAlive(), "invalidateGraph() starved by overlapping searches");
            assertNull(merger.failure.get());
        }
        finally
        {
            stop.set(true);
        }
        assertDone("searchers", searchers);
        assertEquals(10, index.search(query(), 10).size());
        index.close();
    }
}
