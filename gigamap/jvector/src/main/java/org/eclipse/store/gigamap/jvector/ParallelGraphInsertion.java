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


import io.github.jbellis.jvector.util.PhysicalCoreExecutor;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntConsumer;

/**
 * Runs the per-node work of a full graph build on several threads.
 * <p>
 * {@code GraphIndexBuilder.addGraphNode} is safe for concurrent callers, which is how jVector's own
 * {@code build(ravv)} parallelises. That method is not usable here: it assumes the dense ordinal
 * range {@code 0..size-1} and finishes with {@code cleanup()}, whereas graph ordinals are entity
 * ids with holes and the builder stays live after the rebuild. So this class provides only the
 * parallel loop and leaves the per-node step to the caller.
 * <p>
 * Each call runs on a ForkJoinPool of its own rather than on a shared one
 * ({@code PhysicalCoreExecutor.pool()} or the common pool). The callers hold the parent GigaMap
 * monitor and wait for the loop to finish, while other work on a shared pool, such as graph cleanup
 * or a disk write of another index on the same GigaMap, may be blocked on that very monitor. If such
 * tasks occupied every worker, the loop would never get a thread and the caller would wait forever.
 * A private pool cannot be occupied by anyone else. It is sized like jVector's own build pool and
 * shut down before the call returns. The range is split by an explicit {@link RecursiveAction}
 * rather than a parallel stream, so which pool runs the work does not hinge on how streams pick
 * their pool.
 * <p>
 * Not every parallel graph step goes through this class. The degree enforcement of an in-memory
 * optimization ({@code VectorIndex.Default#maintainLiveGraph}) runs as a parallel stream on the common
 * pool, under the parent monitor, the builder write lock and the structure lock; its per-node step
 * takes no lock and never needs the monitor. jVector's own {@code removeDeletedNodes()} and
 * {@code cleanup()} use its shared pools as well.
 * <p>
 * <b>The one-shot calls are not for repeated use against one long-lived builder.</b> Every such call
 * starts fresh threads, and {@code GraphIndexBuilder} keeps per-thread scratch arrays and searchers in
 * {@code ExplicitThreadLocal}s keyed by thread id, released only by {@code builder.close()}. Each
 * call therefore leaves one set of those per worker thread behind in the builder it inserted into.
 * That is fine for a full rebuild into a builder that sees at most one of them. A caller inserting
 * batch after batch into the same builder uses {@link #newPool(String)} and
 * {@link #forEach(int, IntConsumer, ForkJoinPool)} instead: a pool of its own whose threads outlive
 * the calls, so the builder sees the same few threads every time.
 * <p>
 * Thread-safe; holds no state.
 */
final class ParallelGraphInsertion
{
    ///////////////////////////////////////////////////////////////////////////
    // constants //
    //////////////

    /**
     * Below this many nodes the loop runs on the calling thread. Starting a pool costs more than a
     * few hundred inserts into a small graph, and a sequential build of a small index stays easy to
     * reason about in tests and logs.
     */
    static final int MIN_PARALLEL_NODES = 256;

    /**
     * How many chunks the range is cut into per worker thread. Inserts differ in cost (a node high
     * in the hierarchy searches more layers), so more chunks than threads keep idle workers stealing
     * instead of waiting for the slowest one.
     */
    private static final int CHUNKS_PER_THREAD = 8;

    /**
     * How long an idle worker of a {@link #newPool(String) long-lived pool} lives. Long, because a
     * replacement thread has a new id, for which the builder allocates its scratch arrays again.
     */
    private static final long POOL_KEEP_ALIVE_DAYS = 365L;

    /**
     * Test seam: when positive, caps {@link #parallelism()}, so that a test can run the same build
     * sequentially (with {@code 1}) for comparison. {@code 0}, the production value, means no cap.
     */
    static volatile int parallelismLimitForTest;


    ///////////////////////////////////////////////////////////////////////////
    // static methods //
    ///////////////////

    /**
     * Calls {@code action} once for every index in {@code [0, count)}, in parallel when
     * {@code count} reaches {@link #MIN_PARALLEL_NODES} and more than one core is available.
     * <p>
     * Precondition: {@code action} is safe to call concurrently for distinct indices and never
     * waits for a lock the calling thread holds. Postcondition: every call has returned when this
     * method returns, normally or exceptionally, and no worker thread of the call is left running.
     * Calls are in no particular order.
     * <p>
     * A failing call ends the run: the calls already running finish, the ones not yet started are
     * skipped, and only then does the failure propagate. Do not count on any particular index having
     * run or having been skipped.
     *
     * @param count            the number of indices, {@code >= 0}
     * @param action           the per-index work
     * @param threadNamePrefix prefix for the worker thread names, for thread dumps
     * @throws RuntimeException the first exception thrown by {@code action}, the instance it threw
     *                          (an {@link Error} is rethrown as such); failures of other calls of the
     *                          same run are attached to it as suppressed exceptions
     */
    static void forEach(
        final int         count           ,
        final IntConsumer action          ,
        final String      threadNamePrefix
    )
    {
        forEach(count, action, threadNamePrefix, parallelism());
    }

    /**
     * {@link #forEach(int, IntConsumer, String)} on at most {@code parallelism} threads, for work that
     * runs in the background next to the work it is for and should leave cores to it.
     *
     * @param count            the number of indices, {@code >= 0}
     * @param action           the per-index work
     * @param threadNamePrefix prefix for the worker thread names, for thread dumps
     * @param parallelism      the most worker threads to use, positive; below {@code 2} the loop runs on
     *                         the calling thread
     * @throws RuntimeException the first exception thrown by {@code action}, as for
     *                          {@link #forEach(int, IntConsumer, String)}
     */
    static void forEach(
        final int         count           ,
        final IntConsumer action          ,
        final String      threadNamePrefix,
        final int         parallelism
    )
    {
        if(count < MIN_PARALLEL_NODES || parallelism < 2)
        {
            for(int i = 0; i < count; i++)
            {
                action.accept(i);
            }
            return;
        }

        final int chunkSize = Math.max(1, count / (parallelism * CHUNKS_PER_THREAD));
        try(final ForkJoinPool pool = new ForkJoinPool(parallelism, threadFactory(threadNamePrefix), null, false))
        {
            invokeToCompletion(pool, count, chunkSize, action);
        }
    }

    /**
     * Runs the action over {@code [0, count)} on {@code pool} and returns only once every call that started has
     * returned. A failing call does not throw out of the pool, where {@code ForkJoinTask.invokeAll} would rethrow it
     * while chunks already running carry on, and chunks forked but not yet started would run after the caller
     * has gone on (with its locks released, its batch rolled back). It is recorded instead: calls not yet started
     * skip, and the first failure is rethrown here, as thrown, the later ones suppressed.
     */
    private static void invokeToCompletion(
        final ForkJoinPool pool     ,
        final int          count    ,
        final int          chunkSize,
        final IntConsumer  action
    )
    {
        final Failures failures = new Failures();
        pool.invoke(new RangeAction(0, count, chunkSize, action, failures));
        failures.rethrow();
    }

    /**
     * Creates a pool for repeated parallel inserts into one long-lived builder: private to its owner,
     * so no other work can occupy its workers while the owner waits for them holding the parent
     * monitor, and with workers that stay alive between the calls, see the class comment. The owner
     * shuts it down when it is done with it. Workers are daemon threads, start on first use and never
     * outnumber {@link #parallelism()}.
     *
     * @param threadNamePrefix prefix for the worker thread names, for thread dumps
     * @return the new pool, with {@link #parallelism()} workers
     */
    static ForkJoinPool newPool(final String threadNamePrefix)
    {
        final int parallelism = parallelism();
        return new ForkJoinPool(
            parallelism,
            threadFactory(threadNamePrefix),
            null,
            false,
            parallelism,
            // No compensating threads beyond the parallelism: every extra thread would leave its own scratch
            // state in the builder. A worker that waits in a join helps with other tasks instead, and should
            // it have to block, the pool carries on with one runnable thread fewer (the saturate predicate)
            // rather than failing the batch.
            parallelism,
            1,
            pool -> true,
            POOL_KEEP_ALIVE_DAYS,
            TimeUnit.DAYS
        );
    }

    /**
     * {@link #forEach(int, IntConsumer, String)} on {@code pool}, a pool from {@link #newPool(String)},
     * for a caller that inserts batch after batch into the same builder. The caller must not be a
     * worker of {@code pool}, and must not let two calls share the pool at the same time.
     *
     * @param count  the number of indices, {@code >= 0}
     * @param action the per-index work
     * @param pool   the pool to run on, not shut down
     * @throws RuntimeException the first exception thrown by {@code action}, as for
     *                          {@link #forEach(int, IntConsumer, String)}
     */
    static void forEach(
        final int          count ,
        final IntConsumer  action,
        final ForkJoinPool pool
    )
    {
        final int parallelism = pool.getParallelism();
        if(count < MIN_PARALLEL_NODES || parallelism < 2)
        {
            for(int i = 0; i < count; i++)
            {
                action.accept(i);
            }
            return;
        }
        invokeToCompletion(pool, count, Math.max(1, count / (parallelism * CHUNKS_PER_THREAD)), action);
    }

    /**
     * @return the number of worker threads a parallel run uses
     */
    static int parallelism()
    {
        // The same sizing jVector applies to its own build pool, including the
        // jvector.physical_core_count override.
        final int cores = Math.max(1, PhysicalCoreExecutor.getPhysicalCoreCount());
        final int limit = parallelismLimitForTest;
        return limit > 0 ? Math.min(limit, cores) : cores;
    }

    private static ForkJoinPool.ForkJoinWorkerThreadFactory threadFactory(final String threadNamePrefix)
    {
        return pool ->
        {
            final ForkJoinWorkerThread thread = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
            thread.setName(threadNamePrefix + "-" + thread.getPoolIndex());
            return thread;
        };
    }


    ///////////////////////////////////////////////////////////////////////////
    // nested types //
    /////////////////

    /**
     * Calls the action for {@code [from, to)}, splitting the range in halves until a piece is at most
     * {@code chunkSize} long. Forked pieces run on the pool the root task was invoked on.
     */
    private static final class RangeAction extends RecursiveAction
    {
        private final int         from     ;
        private final int         to       ;
        private final int         chunkSize;
        private final IntConsumer action   ;
        private final Failures    failures ;

        RangeAction(
            final int         from     ,
            final int         to       ,
            final int         chunkSize,
            final IntConsumer action   ,
            final Failures    failures
        )
        {
            this.from      = from     ;
            this.to        = to       ;
            this.chunkSize = chunkSize;
            this.action    = action   ;
            this.failures  = failures ;
        }

        @Override
        protected void compute()
        {
            if(this.to - this.from <= this.chunkSize)
            {
                for(int i = this.from; i < this.to; i++)
                {
                    if(this.failures.failed())
                    {
                        return;
                    }
                    try
                    {
                        this.action.accept(i);
                    }
                    catch(final Throwable t)
                    {
                        this.failures.record(t);
                        return;
                    }
                }
                return;
            }
            final int middle = (this.from + this.to) >>> 1;
            invokeAll(
                new RangeAction(this.from, middle, this.chunkSize, this.action, this.failures),
                new RangeAction(middle, this.to, this.chunkSize, this.action, this.failures)
            );
        }
    }

    /**
     * The failures of one parallel run: the first is rethrown on the calling thread, later ones are attached to it.
     */
    private static final class Failures
    {
        private static final int MAX_SUPPRESSED = 10;

        private final AtomicReference<Throwable> first = new AtomicReference<>();

        boolean failed()
        {
            return this.first.get() != null;
        }

        void record(final Throwable failure)
        {
            if(this.first.compareAndSet(null, failure))
            {
                return;
            }
            final Throwable first = this.first.get();
            if(first == failure)
            {
                return;
            }
            synchronized(first)
            {
                if(first.getSuppressed().length < MAX_SUPPRESSED)
                {
                    first.addSuppressed(failure);
                }
            }
        }

        void rethrow()
        {
            final Throwable failure = this.first.get();
            if(failure instanceof final RuntimeException exception)
            {
                throw exception;
            }
            if(failure instanceof final Error error)
            {
                throw error;
            }
            if(failure != null)
            {
                // IntConsumer throws nothing checked; a sneaky checked exception is wrapped.
                throw new IllegalStateException(failure);
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////
    // constructors //
    /////////////////

    private ParallelGraphInsertion()
    {
        throw new UnsupportedOperationException();
    }

}
