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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The gate every logic of a threaded query passes: a logic runs at most once and only while the gate is open, and
 * the gate knows which logics are still running after it was closed.
 */
@Timeout(60)
public class QueryWorkersTest
{
	@Test
	void logicRunsAtMostOnce()
	{
		final QueryWorkers  workers = new QueryWorkers();
		final AtomicInteger runs    = new AtomicInteger();
		final Runnable      logic   = workers.guard(runs::incrementAndGet);

		assertFalse(workers.isComplete(1), "a guarded logic has not been run yet");
		logic.run();
		logic.run();

		assertEquals(1, runs.get());
		assertTrue(workers.isComplete(1));
	}

	@Test
	void everyGuardedLogicMustRunForCompleteness()
	{
		final QueryWorkers workers = new QueryWorkers();
		final Runnable     first   = workers.guard(() -> {});
		workers.guard(() -> {});

		// running one logic twice does not stand in for the other one
		first.run();
		first.run();

		assertFalse(workers.isComplete(2));
	}

	@Test
	void fewerLogicsThanTheQueryNeedsAreIncomplete()
	{
		final QueryWorkers workers = new QueryWorkers();
		workers.guard(() -> {}).run();

		// a provider that asked for one logic for two consumers ran everything it asked for
		assertFalse(workers.isComplete(2));
		assertTrue(workers.isComplete(1));
		assertFalse(new QueryWorkers().isComplete(1), "a provider that asked for nothing");
	}

	@Test
	void logicDoesNotStartOnceTheGateIsClosed()
	{
		final QueryWorkers  workers = new QueryWorkers();
		final AtomicInteger runs    = new AtomicInteger();
		final Runnable      logic   = workers.guard(runs::incrementAndGet);

		assertFalse(workers.close(), "nothing is running");
		assertTrue(workers.isClosed());
		assertTrue(workers.isDone());

		logic.run();
		assertEquals(0, runs.get());
		assertFalse(workers.isComplete(1));
	}

	@Test
	void logicsThatWereNeverStartedAreNotWaitedFor() throws InterruptedException
	{
		final QueryWorkers workers = new QueryWorkers();
		workers.guard(() -> {});

		// returns right away: whoever calls it decides what an incomplete gate means
		workers.awaitRunning();
		assertFalse(workers.isComplete(1));
	}

	@Test
	void closedGateIsDoneOnlyWhenItsRunningLogicsAre() throws InterruptedException
	{
		final QueryWorkers   workers = new QueryWorkers();
		final CountDownLatch started = new CountDownLatch(1);
		final CountDownLatch go      = new CountDownLatch(1);
		final Thread         thread  = new Thread(workers.guard(() ->
		{
			started.countDown();
			awaitUninterruptibly(go);
		}));
		thread.setDaemon(true);
		thread.start();
		try
		{
			assertTrue(started.await(30, TimeUnit.SECONDS), "the logic did not start");

			assertFalse(workers.isDone(), "an open gate is not done");
			assertTrue(workers.isRunningOn(thread));
			assertFalse(workers.isRunningOn(Thread.currentThread()));

			assertTrue(workers.close(), "a logic is still running");
			assertFalse(workers.isDone());
		}
		finally
		{
			go.countDown();
		}

		workers.awaitRunning();
		assertTrue(workers.isDone());
		assertFalse(workers.isRunningOn(thread));
		thread.join(30_000);
		assertFalse(thread.isAlive());
	}

	@Test
	void failureOfALogicIsKeptForTheCaller()
	{
		final QueryWorkers          workers = new QueryWorkers();
		final IllegalStateException failure = new IllegalStateException("first");
		final AtomicInteger         runs    = new AtomicInteger();

		// must not end the thread that runs it, which may be a pooled one
		workers.guard(() -> {throw failure;}).run();
		workers.guard(() -> {throw new IllegalArgumentException("second");}).run();
		workers.guard(runs::incrementAndGet).run();

		assertEquals(1, runs.get(), "a failed logic does not keep the others from running");
		assertTrue(workers.isComplete(3));
		assertFalse(workers.close(), "a failed logic is not running anymore");
		assertSame(failure, assertThrows(IllegalStateException.class, workers::throwFailure), "the first failure");
		assertEquals(1, failure.getSuppressed().length, "later failures are kept with the first one");
		assertEquals("second", failure.getSuppressed()[0].getMessage());
	}

	@Test
	void sharedFailureInstance_isNotSuppressedIntoItself()
	{
		final QueryWorkers          workers = new QueryWorkers();
		final IllegalStateException shared  = new IllegalStateException("thrown by every consumer");

		workers.guard(() -> {throw shared;}).run();
		workers.guard(() -> {throw shared;}).run();

		assertSame(shared, assertThrows(IllegalStateException.class, workers::throwFailure));
		assertEquals(0, shared.getSuppressed().length);
	}

	@Test
	void failureOfALogic_isKeptWithAnExceptionThatEndsTheQuery()
	{
		final QueryWorkers          workers  = new QueryWorkers();
		final IllegalStateException consumer = new IllegalStateException("consumer");
		final RuntimeException      provider = new RuntimeException("provider");

		workers.guard(() -> {throw consumer;}).run();
		workers.addFailureTo(provider);
		workers.addFailureTo(consumer);

		assertSame(consumer, provider.getSuppressed()[0]);
		assertEquals(0, consumer.getSuppressed().length, "not into itself");
	}

	@Test
	void hasGuarded_onlyOnceALogicWasRequested()
	{
		final QueryWorkers workers = new QueryWorkers();
		assertFalse(workers.hasGuarded());
		workers.guard(() -> {});
		assertTrue(workers.hasGuarded());
	}

	@Test
	void errorOfALogicIsRethrownAsIs()
	{
		final QueryWorkers   workers = new QueryWorkers();
		final AssertionError error   = new AssertionError("from a consumer");

		workers.guard(() -> {throw error;}).run();

		assertSame(error, assertThrows(AssertionError.class, workers::throwFailure));
	}

	@Test
	void noFailureNoThrow()
	{
		final QueryWorkers workers = new QueryWorkers();
		workers.guard(() -> {}).run();
		workers.throwFailure();
	}



	///////////////////////////////////////////////////////////////////////////
	// fixture //
	////////////

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
}
