package org.eclipse.store.gigamap.types;

/*-
 * #%L
 * EclipseStore GigaMap
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

import org.eclipse.serializer.collections.types.XGettingCollection;
import org.eclipse.serializer.util.X;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A threaded query reads the map on threads it gets from an {@link IterationThreadProvider}. Such a thread can
 * outlive its query (the caller is interrupted, starting the threads fails half way, an iterator is closed
 * early). The query must not wait for it then, but the map must stay protected: mutations wait until the thread
 * is done. A provider that does not run the logics as expected must not go unnoticed.
 */
@Timeout(120)
public class QueryWorkersDetachTest
{
	static final int ENTITY_COUNT = 20_000;
	static final int KEY0_COUNT   = ENTITY_COUNT / 2;

	@Test
	void interruptedThreadedExecute_returnsAndMutationsWaitForItsConsumers() throws InterruptedException
	{
		assertInterruptedExecuteReturns(IterationThreadProvider.Creating(ThreadCountProvider.Fixed(2)));
	}

	/**
	 * A pooled thread runs its logic outside its own monitor, so the interrupted caller can stop waiting for it.
	 */
	@Test
	void interruptedPooledExecute_returnsAndMutationsWaitForItsConsumers() throws InterruptedException
	{
		final IterationThreadProvider pool = IterationThreadProvider.Pooling(2, ThreadCountProvider.Fixed(2));
		try
		{
			assertInterruptedExecuteReturns(pool);
		}
		finally
		{
			pool.shutdown();
		}
	}

	private static void assertInterruptedExecuteReturns(final IterationThreadProvider threads) throws InterruptedException
	{
		final GigaMap<Entity>         map        = newMap(ENTITY_COUNT);
		final CountDownLatch          inConsumer = new CountDownLatch(1);
		final CountDownLatch          go         = new CountDownLatch(1);
		final Consumer<Entity>        blocked    = entity ->
		{
			inConsumer.countDown();
			awaitUninterruptibly(go);
		};

		final AtomicReference<Throwable> thrown      = new AtomicReference<>();
		final AtomicBoolean              interrupted = new AtomicBoolean();
		final Thread caller = start(() ->
		{
			try
			{
				map.query(threads).and(new KeyIndexer().is(0)).execute(blocked, blocked);
			}
			finally
			{
				interrupted.set(Thread.currentThread().isInterrupted());
			}
		}, thrown);
		try
		{
			await(inConsumer, thrown);
			caller.interrupt();
			join(caller);

			// not silently: the consumers have not received everything when the call returns
			assertInterruptedFailure(thrown.get());
			assertTrue(interrupted.get(), "the interrupt status must be kept for the caller");
			assertFalse(map.isReadOnly(), "the query itself is over");

			assertMutationWaitsUntil(map, go);
		}
		finally
		{
			go.countDown();
		}
	}

	@Test
	void failedStartOfThreadedExecute_reachesCallerAndMutationsWaitForTheStartedConsumer() throws InterruptedException
	{
		final GigaMap<Entity>  map        = newMap(ENTITY_COUNT);
		final CountDownLatch   inConsumer = new CountDownLatch(1);
		final CountDownLatch   go         = new CountDownLatch(1);
		final Consumer<Entity> blocked    = entity ->
		{
			inConsumer.countDown();
			awaitUninterruptibly(go);
		};

		// starts the first logic, then fails like a thread that cannot be created, once that logic reads
		final IllegalStateException   startFailure = new IllegalStateException("simulated: unable to create native thread");
		final IterationThreadProvider failing      = new Provider(logicProvider ->
		{
			startDaemon(logicProvider.provideIterationLogic());
			logicProvider.provideIterationLogic();
			awaitUninterruptibly(inConsumer);
			throw startFailure;
		});

		final AtomicReference<Throwable> thrown = new AtomicReference<>();
		final Thread caller = start(
			() -> map.query(failing).and(new KeyIndexer().is(0)).execute(blocked, blocked),
			thrown
		);
		try
		{
			join(caller);
			assertSame(startFailure, thrown.get());
			assertFalse(map.isReadOnly(), "the query itself is over");

			assertMutationWaitsUntil(map, go);
		}
		finally
		{
			go.countDown();
		}
	}

	/**
	 * Only that the failure reaches the caller and nothing keeps the map from being modified afterwards. The
	 * worker that was started is done quickly on a map this small, so that a mutation waits for it is not shown
	 * here (see the same case for an execution above).
	 */
	@Test
	void failedStartOfThreadedIterator_reachesCallerAndLeavesTheMapUsable() throws InterruptedException
	{
		final GigaMap<Entity> map = newMap(ENTITY_COUNT);

		final IllegalStateException   startFailure = new IllegalStateException("simulated: unable to create native thread");
		final IterationThreadProvider failing      = new Provider(logicProvider ->
		{
			startDaemon(logicProvider.provideIterationLogic());
			logicProvider.provideIterationLogic();
			throw startFailure;
		});

		assertSame(startFailure, assertThrows(IllegalStateException.class, () ->
			map.query(failing).and(new KeyIndexer().is(0)).iterator()
		));
		assertFalse(map.isReadOnly(), "no reader was opened");

		final AtomicReference<Throwable> failure = new AtomicReference<>();
		join(start(() -> map.add(new Entity(0)), failure));
		assertNull(failure.get());
		assertEquals(ENTITY_COUNT + 1, map.size());
	}

	@Test
	void consumerOfAnInterruptedExecute_mutating_failsInsteadOfWaitingForItself() throws InterruptedException
	{
		final GigaMap<Entity>         map        = newMap(ENTITY_COUNT);
		final IterationThreadProvider threads    = IterationThreadProvider.Creating(ThreadCountProvider.Fixed(2));
		final CountDownLatch          inConsumer = new CountDownLatch(1);
		final CountDownLatch          go         = new CountDownLatch(1);
		final CountDownLatch          done       = new CountDownLatch(1);
		final AtomicInteger           calls      = new AtomicInteger();

		final AtomicReference<Throwable> mutation = new AtomicReference<>();
		final Consumer<Entity> mutatingOnce = entity ->
		{
			if(calls.getAndIncrement() != 0)
			{
				return;
			}
			inConsumer.countDown();
			awaitUninterruptibly(go);
			try
			{
				map.add(new Entity(0));
			}
			catch(final Throwable t)
			{
				mutation.set(t);
			}
			done.countDown();
		};

		final AtomicReference<Throwable> thrown = new AtomicReference<>();
		final Thread caller = start(
			() -> map.query(threads).and(new KeyIndexer().is(0)).execute(mutatingOnce, entity -> {}),
			thrown
		);
		try
		{
			await(inConsumer, thrown);
			caller.interrupt();
			join(caller);
			assertInterruptedFailure(thrown.get());
		}
		finally
		{
			// the consumer's query is over, but its thread still reads
			go.countDown();
		}
		await(done, thrown);
		assertTrue(mutation.get() instanceof IllegalStateException, "got " + mutation.get());
		assertEquals(ENTITY_COUNT, map.size());

		// once the consumers are done, the map is mutable again
		final AtomicReference<Throwable> failure = new AtomicReference<>();
		join(start(() -> map.add(new Entity(0)), failure));
		assertNull(failure.get());
		assertEquals(ENTITY_COUNT + 1, map.size());
	}

	@Test
	void providerNotRunningALogic_failsInsteadOfDeliveringAPart()
	{
		final GigaMap<Entity>  map      = newMap(ENTITY_COUNT);
		final AtomicInteger    consumed = new AtomicInteger();
		final Consumer<Entity> counting = entity -> consumed.incrementAndGet();

		final IterationThreadProvider skipping = new Provider(logicProvider ->
		{
			final Runnable first = logicProvider.provideIterationLogic();
			logicProvider.provideIterationLogic();
			first.run();
		});

		assertIncompleteFailure(assertThrows(IllegalStateException.class, () ->
			map.query(skipping).and(new KeyIndexer().is(0)).execute(counting, counting)
		));

		// the first logic covers the first half of the ids
		assertEquals(KEY0_COUNT / 2, consumed.get(), "only a part was consumed");

		// no hold is left behind
		assertFalse(map.isReadOnly());
		map.add(new Entity(0));
	}

	@Test
	void providerRequestingFewerLogicsThanConsumers_failsInsteadOfDeliveringAPart()
	{
		final GigaMap<Entity>  map      = newMap(ENTITY_COUNT);
		final AtomicInteger    consumed = new AtomicInteger();
		final Consumer<Entity> counting = entity -> consumed.incrementAndGet();

		// runs everything it asked for, but asks for one logic for two consumers
		final IterationThreadProvider oneOnly = new Provider(logicProvider -> logicProvider.provideIterationLogic().run());
		assertIncompleteFailure(assertThrows(IllegalStateException.class, () ->
			map.query(oneOnly).and(new KeyIndexer().is(0)).execute(counting, counting)
		));
		assertEquals(KEY0_COUNT / 2, consumed.get(), "only a part was consumed");

		final IterationThreadProvider nothing = new Provider(logicProvider -> {});
		assertIncompleteFailure(assertThrows(IllegalStateException.class, () ->
			map.query(nothing).and(new KeyIndexer().is(0)).execute(counting, counting)
		));
		assertEquals(KEY0_COUNT / 2, consumed.get());

		assertFalse(map.isReadOnly());
		map.add(new Entity(0));
	}

	@Test
	void providerRunningALogicTwice_everyEntityIsConsumedOnce()
	{
		final GigaMap<Entity>  map      = newMap(ENTITY_COUNT);
		final AtomicInteger    consumed = new AtomicInteger();
		final Consumer<Entity> counting = entity -> consumed.incrementAndGet();

		final IterationThreadProvider repeating = new Provider(logicProvider ->
		{
			final Runnable first  = logicProvider.provideIterationLogic();
			final Runnable second = logicProvider.provideIterationLogic();
			first.run();
			first.run();
			second.run();
		});

		map.query(repeating).and(new KeyIndexer().is(0)).execute(counting, counting);
		assertEquals(KEY0_COUNT, consumed.get());
	}

	@Test
	void providerReturningBeforeItsLogicsRan_failsAndTheLogicsNeverRead() throws InterruptedException
	{
		final GigaMap<Entity>  map      = newMap(ENTITY_COUNT);
		final AtomicInteger    consumed = new AtomicInteger();
		final Consumer<Entity> counting = entity -> consumed.incrementAndGet();

		// hands the logics to threads that run them only after the provider has returned
		final CountDownLatch executeReturned = new CountDownLatch(1);
		final Thread[]       late            = new Thread[2];
		final IterationThreadProvider notWaiting = new Provider(logicProvider ->
		{
			for(int i = 0; i < late.length; i++)
			{
				final Runnable logic = logicProvider.provideIterationLogic();
				late[i] = startDaemon(() ->
				{
					awaitUninterruptibly(executeReturned);
					logic.run();
				});
			}
		});

		try
		{
			assertIncompleteFailure(assertThrows(IllegalStateException.class, () ->
				map.query(notWaiting).and(new KeyIndexer().is(0)).execute(counting, counting)
			));
			assertFalse(map.isReadOnly());
		}
		finally
		{
			executeReturned.countDown();
		}

		// the query is over: its logics must not read the map anymore
		join(late);
		assertEquals(0, consumed.get(), "a logic ran after its query had ended");
		map.add(new Entity(0));
	}

	@Test
	void consumerFailure_reachesTheCaller_poolStaysUsable()
	{
		final GigaMap<Entity>         map  = newMap(ENTITY_COUNT);
		final IterationThreadProvider pool = IterationThreadProvider.Pooling(2, ThreadCountProvider.Fixed(2));
		try
		{
			final UnsupportedOperationException failure = new UnsupportedOperationException("from a consumer");
			assertSame(failure, assertThrows(UnsupportedOperationException.class, () ->
				map.query(pool).and(new KeyIndexer().is(0)).execute(entity -> {throw failure;}, entity -> {})
			));
			assertFalse(map.isReadOnly());

			// the pooled thread that ran the failing consumer must still work
			final AtomicInteger    consumed = new AtomicInteger();
			final Consumer<Entity> counting = entity -> consumed.incrementAndGet();
			for(int i = 0; i < 20; i++)
			{
				map.query(pool).and(new KeyIndexer().is(0)).execute(counting, counting);
			}
			assertEquals(20 * KEY0_COUNT, consumed.get());
		}
		finally
		{
			pool.shutdown();
		}
	}

	/**
	 * A pooled thread can be given back while its logic still runs (a threaded iterator that is closed early). It
	 * must not be handed out again before it is done: the next query would wait for it while holding the map's
	 * monitor, which the running logic may need. Once its logic is done, it is back in the pool.
	 */
	@Test
	void pooledThreadReleasedWhileItsLogicRuns_isHandedOutAgainOnlyWhenDone() throws InterruptedException
	{
		final IterationThreadProvider pool    = IterationThreadProvider.Pooling(1, ThreadCountProvider.Fixed(1));
		final CountDownLatch          started = new CountDownLatch(1);
		final CountDownLatch          go      = new CountDownLatch(1);
		final CountDownLatch          ended   = new CountDownLatch(1);
		try
		{
			final XGettingCollection<? extends Thread> busy = pool.startIterationThreads(null, 1, () -> () ->
			{
				started.countDown();
				awaitUninterruptibly(go);
				ended.countDown();
			});
			final Thread busyThread = busy.get();
			assertTrue(started.await(30, TimeUnit.SECONDS), "the logic did not start");
			pool.disposeIterationThreads(busy);

			// on another thread: handed the busy thread, the start itself would block
			final CountDownLatch secondRan = new CountDownLatch(1);
			final AtomicReference<XGettingCollection<? extends Thread>> second = new AtomicReference<>();
			final AtomicReference<Throwable> failure = new AtomicReference<>();
			join(start(() -> second.set(pool.startIterationThreads(null, 1, () -> secondRan::countDown)), failure));
			assertNull(failure.get());
			final Thread secondThread = second.get().get();
			assertTrue(secondThread != busyThread, "a thread still running its logic was handed out again");

			// given back first and idle, so the thread that returns by itself is the one handed out next
			awaitIdle(secondThread, secondRan);
			pool.disposeIterationThreads(second.get());
			go.countDown();
			awaitIdle(busyThread, ended);

			final CountDownLatch thirdRan = new CountDownLatch(1);
			final XGettingCollection<? extends Thread> third = pool.startIterationThreads(null, 1, () -> thirdRan::countDown);
			assertSame(busyThread, third.get(), "the released thread must be back in the pool once its logic is done");

			// idle again, so that the shutdown below reaches it
			awaitIdle(busyThread, thirdRan);
			pool.disposeIterationThreads(third);
		}
		finally
		{
			go.countDown();
			pool.shutdown();
		}
	}

	/**
	 * A shutdown only reaches the idle threads of a pool. A thread that is handed out at that time, busy or not,
	 * must end when it is given back instead of waiting in the pool for work that never comes. The pool stays
	 * usable after the shutdown.
	 */
	@Test
	void pooledThreadsGivenBackAfterShutdown_end() throws InterruptedException
	{
		final IterationThreadProvider pool    = IterationThreadProvider.Pooling(2, ThreadCountProvider.Fixed(2));
		final CountDownLatch          started = new CountDownLatch(1);
		final CountDownLatch          go      = new CountDownLatch(1);
		try
		{
			// busy, and given back while its logic runs, like the worker of an iterator closed early
			final XGettingCollection<? extends Thread> busy = pool.startIterationThreads(null, 1, () -> () ->
			{
				started.countDown();
				awaitUninterruptibly(go);
			});
			assertTrue(started.await(30, TimeUnit.SECONDS), "the logic did not start");
			pool.disposeIterationThreads(busy);

			// done, but not given back yet
			final CountDownLatch                       idleRan = new CountDownLatch(1);
			final XGettingCollection<? extends Thread> idle    = pool.startIterationThreads(null, 1, () -> idleRan::countDown);
			awaitIdle(idle.get(), idleRan);

			pool.shutdown();

			go.countDown();
			join(busy.get());
			pool.disposeIterationThreads(idle);
			join(idle.get());

			// usable after the shutdown, with a new thread
			final CountDownLatch                       nextRan = new CountDownLatch(1);
			final XGettingCollection<? extends Thread> next    = pool.startIterationThreads(null, 1, () -> nextRan::countDown);
			awaitIdle(next.get(), nextRan);
			assertTrue(next.get() != busy.get() && next.get() != idle.get());
			pool.disposeIterationThreads(next);
		}
		finally
		{
			go.countDown();
			pool.shutdown();
		}
	}

	/**
	 * A collection of pool threads that is disposed again must not release a thread that another query has got
	 * meanwhile: that thread would be handed out a second time while the other query still uses it.
	 */
	@Test
	void pooledThreadsDisposedTwice_doNotReleaseAThreadOfAnotherQuery() throws InterruptedException
	{
		final IterationThreadProvider pool = IterationThreadProvider.Pooling(1, ThreadCountProvider.Fixed(1));
		try
		{
			final CountDownLatch                       firstRan = new CountDownLatch(1);
			final XGettingCollection<? extends Thread> first    = pool.startIterationThreads(null, 1, () -> firstRan::countDown);
			awaitIdle(first.get(), firstRan);
			pool.disposeIterationThreads(first);

			final CountDownLatch                       secondRan = new CountDownLatch(1);
			final XGettingCollection<? extends Thread> second    = pool.startIterationThreads(null, 1, () -> secondRan::countDown);
			assertSame(first.get(), second.get(), "the idle thread is handed out again");
			awaitIdle(second.get(), secondRan);

			// stale: the thread belongs to the second query now, which has not given it back
			pool.disposeIterationThreads(first);

			final CountDownLatch                       thirdRan = new CountDownLatch(1);
			final XGettingCollection<? extends Thread> third    = pool.startIterationThreads(null, 1, () -> thirdRan::countDown);
			assertTrue(third.get() != second.get(), "a thread still handed out was handed out again");
			awaitIdle(third.get(), thirdRan);

			pool.disposeIterationThreads(second);
			pool.disposeIterationThreads(third);
		}
		finally
		{
			pool.shutdown();
		}
	}

	/**
	 * The reader of a threaded iterator waits for its workers to fill the registry. A worker that fails leaves
	 * its slot unfilled: the waiting reader must get the failure instead of waiting forever. No worker runs here,
	 * so the reader is certainly waiting; the failure is recorded the way a failing worker records it.
	 */
	@Test
	void threadedIterator_workerFailure_reachesTheWaitingReader() throws Exception
	{
		final GigaMap<Entity> map = newMap(ENTITY_COUNT);

		final AtomicReference<IterationThreadProvider.IterationLogicProvider> iterator = new AtomicReference<>();
		final IterationThreadProvider notRunning = new Provider(logicProvider ->
		{
			iterator.set(logicProvider);
			logicProvider.provideIterationLogic();
			logicProvider.provideIterationLogic();
		});

		final AtomicReference<Throwable> thrown = new AtomicReference<>();
		final Thread reader = start(() ->
		{
			try(GigaIterator<Entity> it = map.query(notRunning).and(new KeyIndexer().is(0)).iterator())
			{
				it.hasNext();
			}
		}, thrown);
		try
		{
			awaitWaitingReader(reader, iterator);

			final IllegalStateException failure = new IllegalStateException("simulated worker failure");
			workersOf(iterator.get()).guard(() -> {throw failure;}).run();

			join(reader);
			assertSame(failure, thrown.get());
		}
		finally
		{
			releaseReader(iterator.get());
		}
		assertFalse(map.isReadOnly());
		map.add(new Entity(0));
	}

	/**
	 * Another thread closes a threaded iterator while its reader waits for a registry slot that no worker will
	 * fill anymore: the reader must end, and the logics that did not start must not read afterwards.
	 */
	@Test
	void threadedIterator_closedByAnotherThreadWhileTheReaderWaits_readerEnds() throws Exception
	{
		final GigaMap<Entity> map = newMap(ENTITY_COUNT);

		final AtomicReference<IterationThreadProvider.IterationLogicProvider> iterator = new AtomicReference<>();
		final Runnable[]                              logics   = new Runnable[2];
		final IterationThreadProvider notRunning = new Provider(logicProvider ->
		{
			iterator.set(logicProvider);
			for(int i = 0; i < logics.length; i++)
			{
				logics[i] = logicProvider.provideIterationLogic();
			}
		});

		final AtomicReference<Throwable> thrown  = new AtomicReference<>();
		final AtomicBoolean              hasNext = new AtomicBoolean(true);
		final AtomicReference<GigaIterator<Entity>> opened = new AtomicReference<>();
		final Thread reader = start(() ->
		{
			opened.set(map.query(notRunning).and(new KeyIndexer().is(0)).iterator());
			hasNext.set(opened.get().hasNext());
		}, thrown);
		try
		{
			awaitWaitingReader(reader, iterator);
			opened.get().close();

			join(reader);
			assertNull(thrown.get());
			assertFalse(hasNext.get(), "the remaining segments are skipped");
		}
		finally
		{
			releaseReader(iterator.get());
		}
		assertFalse(map.isReadOnly());
		map.add(new Entity(0));

		// the query is over: its logics do not touch the registry anymore
		for(final Runnable logic : logics)
		{
			logic.run();
		}
		assertTrue(workersOf(iterator.get()).isDone());
	}

	/**
	 * An early close does not wait for the iterator's workers, which still scan the rest of the map here. The
	 * mutations right after it wait for them, and the next iterator takes its threads from the same pool. Shown
	 * is that nothing hangs or fails and that the map's content is right afterwards.
	 */
	@Test
	void earlyClosedThreadedIterators_followedByMutations() throws InterruptedException
	{
		// several registry segments for the workers, so they are not done when the first element is there
		final int count = 1_000_000;
		final GigaMap<Entity>         map  = newMap(count);
		final IterationThreadProvider pool = IterationThreadProvider.Pooling(2, ThreadCountProvider.Fixed(2));
		try
		{
			final int rounds = 100;
			final AtomicReference<Throwable> failure = new AtomicReference<>();
			join(start(() ->
			{
				for(int round = 0; round < rounds; round++)
				{
					try(GigaIterator<Entity> iterator = map.query(pool).and(new KeyIndexer().is(0)).iterator())
					{
						assertTrue(iterator.hasNext());
						assertEquals(0, iterator.next().key);
					}
					// frees and reallocates segment memory, and changes both keys' bitmaps
					map.removeById(map.add(new Entity(0)));
					map.add(new Entity(round % 2));
				}
			}, failure));
			assertNull(failure.get());
			assertFalse(map.isReadOnly());
			assertEquals(count + rounds, map.size());
			assertEquals(count / 2 + rounds / 2, map.query(new KeyIndexer(), 0).count());
			assertEquals(count / 2 + rounds / 2, map.query(new KeyIndexer(), 1).count());
		}
		finally
		{
			pool.shutdown();
		}
	}

	/**
	 * An exhausted iterator closes itself, and a try-with-resources block closes it again. The second close must
	 * not give back the threads again: a pool may have handed them to another query meanwhile.
	 */
	@Test
	void threadedIteratorClosedTwice_givesItsThreadsBackOnce()
	{
		final GigaMap<Entity> map = newMap(ENTITY_COUNT);

		final AtomicInteger disposed  = new AtomicInteger();
		final AtomicInteger completed = new AtomicInteger();
		final IterationThreadProvider creating = IterationThreadProvider.Creating(ThreadCountProvider.Fixed(2));
		final IterationThreadProvider counting = new IterationThreadProvider()
		{
			@Override
			public int provideThreadCount(final GigaMap<?> parent, final BitmapResult[] results)
			{
				return creating.provideThreadCount(parent, results);
			}

			@Override
			public XGettingCollection<? extends Thread> startIterationThreads(
				final GigaMap<?>             parent       ,
				final int                    threadCount  ,
				final IterationLogicProvider logicProvider
			)
			{
				return creating.startIterationThreads(parent, threadCount, logicProvider);
			}

			@Override
			public void executeThreaded(
				final GigaMap<?>             parent       ,
				final int                    threadCount  ,
				final IterationLogicProvider logicProvider
			)
			{
				creating.executeThreaded(parent, threadCount, logicProvider);
			}

			@Override
			public void disposeIterationThreads(final XGettingCollection<? extends Thread> threads)
			{
				disposed.incrementAndGet();
			}

			@Override
			public void completeIteration()
			{
				completed.incrementAndGet();
			}
		};

		int iterated = 0;
		try(GigaIterator<Entity> iterator = map.query(counting).and(new KeyIndexer().is(0)).iterator())
		{
			while(iterator.hasNext())
			{
				iterator.next();
				iterated++;
			}
			assertEquals(1, disposed.get(), "the exhausted iterator has closed itself");
		}
		assertEquals(KEY0_COUNT, iterated);
		assertEquals(1, disposed.get(), "threads given back");
		assertEquals(1, completed.get(), "iteration completed");
	}



	///////////////////////////////////////////////////////////////////////////
	// fixture //
	////////////

	// half of the entities with key 0, half with key 1
	private static GigaMap<Entity> newMap(final int count)
	{
		final GigaMap<Entity> map = GigaMap.New();
		map.index().bitmap().add(new KeyIndexer());
		for(int i = 0; i < count; i++)
		{
			map.add(new Entity(i % 2));
		}
		return map;
	}

	/**
	 * A mutation from another thread must wait while logics of an ended query are still running (they are blocked
	 * on the passed latch), and proceed once they are done.
	 */
	private static void assertMutationWaitsUntil(final GigaMap<Entity> map, final CountDownLatch logicsMayEnd)
		throws InterruptedException
	{
		final long size = map.size();
		final AtomicReference<Throwable> failure = new AtomicReference<>();
		final Thread mutator = start(() -> map.add(new Entity(0)), failure);
		awaitWaiting(mutator);
		assertTrue(mutator.isAlive(), "the mutation must wait for the logics still running, failure: " + failure.get());
		assertEquals(size, map.size());

		logicsMayEnd.countDown();
		join(mutator);
		assertNull(failure.get());
		assertEquals(size + 1, map.size());
	}

	// an execution that returned to an interrupted caller while its consumers had not received everything
	private static void assertInterruptedFailure(final Throwable thrown)
	{
		assertTrue(thrown instanceof IllegalStateException, "got " + thrown);
		assertTrue(thrown.getMessage().contains("Interrupted"), thrown.getMessage());
	}

	// an execution whose thread provider returned although not all logics had run
	private static void assertIncompleteFailure(final IllegalStateException thrown)
	{
		assertTrue(thrown.getMessage().contains("returned without"), thrown.getMessage());
	}

	/** A thread provider whose way of running the provided logics is the test's. */
	static final class Provider implements IterationThreadProvider
	{
		private final Consumer<IterationLogicProvider> logic;

		Provider(final Consumer<IterationLogicProvider> logic)
		{
			super();
			this.logic = logic;
		}

		@Override
		public int provideThreadCount(final GigaMap<?> parent, final BitmapResult[] results)
		{
			return 2;
		}

		@Override
		public XGettingCollection<? extends Thread> startIterationThreads(
			final GigaMap<?>             parent       ,
			final int                    threadCount  ,
			final IterationLogicProvider logicProvider
		)
		{
			this.logic.accept(logicProvider);
			return X.empty();
		}

		@Override
		public void executeThreaded(
			final GigaMap<?>             parent       ,
			final int                    threadCount  ,
			final IterationLogicProvider logicProvider
		)
		{
			this.logic.accept(logicProvider);
		}
	}

	// until the provider has been called and the reader waits for a registry slot
	private static void awaitWaitingReader(final Thread reader, final AtomicReference<?> providerCalled)
		throws InterruptedException
	{
		while(providerCalled.get() == null || reader.getState() != Thread.State.TIMED_WAITING)
		{
			assertTrue(reader.isAlive(), "the reader has ended");
			Thread.sleep(1);
		}
	}

	private static QueryWorkers workersOf(final IterationThreadProvider.IterationLogicProvider threadedIterator)
		throws ReflectiveOperationException
	{
		final Field field = ThreadedIterator.class.getDeclaredField("workers");
		field.setAccessible(true);
		return (QueryWorkers)field.get(threadedIterator);
	}

	// lets a reader that keeps waiting go, so a failing test does not leave it spinning
	private static void releaseReader(final IterationThreadProvider.IterationLogicProvider threadedIterator)
		throws ReflectiveOperationException
	{
		if(threadedIterator != null)
		{
			workersOf(threadedIterator).close();
		}
	}

	private static Thread startDaemon(final Runnable logic)
	{
		final Thread thread = new Thread(logic);
		thread.setDaemon(true);
		thread.start();

		return thread;
	}

	private static Thread start(final Runnable logic, final AtomicReference<Throwable> failure)
	{
		return startDaemon(() ->
		{
			try
			{
				logic.run();
			}
			catch(final Throwable t)
			{
				failure.compareAndSet(null, t);
			}
		});
	}

	// for a latch another thread counts down: fails with that thread's failure instead of waiting on
	private static void await(final CountDownLatch latch, final AtomicReference<Throwable> failure)
		throws InterruptedException
	{
		assertTrue(latch.await(30, TimeUnit.SECONDS), "not reached, failure of the other thread: " + failure.get());
	}

	// Returns once the thread waits (a mutation waiting for the map to become mutable) or has ended. A thread
	// that wrongly does not wait ends, so the caller's check that it is alive cannot pass by timing.
	private static void awaitWaiting(final Thread thread) throws InterruptedException
	{
		while(thread.isAlive()
			&& thread.getState() != Thread.State.WAITING
			&& thread.getState() != Thread.State.TIMED_WAITING
		)
		{
			Thread.sleep(1);
		}
	}

	// until a pooled thread is done with the logic that counts down the passed latch and waits for its next one
	private static void awaitIdle(final Thread pooledThread, final CountDownLatch logicRan) throws InterruptedException
	{
		assertTrue(logicRan.await(30, TimeUnit.SECONDS), "the logic did not run");
		while(pooledThread.getState() != Thread.State.WAITING)
		{
			assertTrue(pooledThread.isAlive(), "the pooled thread has ended");
			Thread.sleep(1);
		}
	}

	private static void join(final Thread... threads) throws InterruptedException
	{
		for(final Thread thread : threads)
		{
			thread.join(30_000);
			assertFalse(thread.isAlive(), "thread did not finish");
		}
	}

	private static void awaitUninterruptibly(final CountDownLatch latch)
	{
		boolean interrupted = false;
		while(true)
		{
			try
			{
				latch.await();
				break;
			}
			catch(final InterruptedException e)
			{
				interrupted = true;
			}
		}
		if(interrupted)
		{
			Thread.currentThread().interrupt();
		}
	}

	static class Entity
	{
		final int key;

		Entity(final int key)
		{
			this.key = key;
		}
	}

	static class KeyIndexer extends IndexerInteger.Abstract<Entity>
	{
		@Override
		protected Integer getInteger(final Entity entity)
		{
			return entity.key;
		}
	}
}
