
package org.eclipse.store.gigamap.types;

/*-
 * #%L
 * EclipseStore GigaMap
 * %%
 * Copyright (C) 2023 - 2025 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Verifies that {@link IterationThreadProvider.Pooling} deactivates its
 * {@code PoolThread}s when the {@code Pooling} instance becomes unreachable —
 * via {@link java.lang.ref.Cleaner}, not the deprecated FinalizerThread.
 * <p>
 * Also verifies that explicit {@code shutdown()} is the supported path for
 * deterministic deactivation and that the post-GC clean is then a no-op.
 */
class IterationThreadProviderCleanerTest
{
	private static final long CLEANUP_TIMEOUT_NS = TimeUnit.SECONDS.toNanos(5);
	private static final long POLL_INTERVAL_MS   = 50;

	@Test
	void cleanerDeactivatesPoolThreadsAfterGc() throws Exception
	{
		// Allocate threads + capture a sentinel PoolThread inside a helper so
		// the Pooling local falls out of scope cleanly when the helper returns.
		final Thread sentinel = createPoolingAndAllocateThreads();

		final Field isRunningField = sentinel.getClass().getDeclaredField("isRunning");
		isRunningField.setAccessible(true);

		assertTrue(isRunningField.getBoolean(sentinel),
			"Pre-condition: PoolThread.isRunning must be true while Pooling is alive");

		final long deadline = System.nanoTime() + CLEANUP_TIMEOUT_NS;
		while(System.nanoTime() < deadline)
		{
			System.gc();
			if(!isRunningField.getBoolean(sentinel))
			{
				return;
			}
			Thread.sleep(POLL_INTERVAL_MS);
		}

		fail("Pooling cleanup did not run within "
			+ TimeUnit.NANOSECONDS.toSeconds(CLEANUP_TIMEOUT_NS)
			+ "s — either the Cleanup is capturing the Pooling instance or GC has not "
			+ "reclaimed it yet.");
	}

	@Test
	void shutdownDeactivatesPoolThreadsImmediately() throws Exception
	{
		final IterationThreadProvider provider = IterationThreadProvider.Pooling(
			2, ThreadCountProvider.Fixed(2)
		);
		allocateAndDispose(provider);

		final Thread sentinel = firstReservedThread(provider);
		final Field isRunningField = sentinel.getClass().getDeclaredField("isRunning");
		isRunningField.setAccessible(true);

		assertTrue(isRunningField.getBoolean(sentinel),
			"Pre-condition: PoolThread.isRunning must be true before shutdown()");

		provider.shutdown();

		assertFalse(isRunningField.getBoolean(sentinel),
			"shutdown() must deactivate PoolThreads synchronously via cleanable.clean()");

		// Idempotent: cleanable.clean() runs at most once, second call is a no-op.
		provider.shutdown();
	}

	// Helper: builds a Pooling, drives one start/dispose cycle so the
	// reservedThreads list is populated, returns a sentinel PoolThread.
	// The Pooling local does not escape this method.
	private static Thread createPoolingAndAllocateThreads() throws Exception
	{
		final IterationThreadProvider provider = IterationThreadProvider.Pooling(
			2, ThreadCountProvider.Fixed(2)
		);
		allocateAndDispose(provider);
		return firstReservedThread(provider);
	}

	private static void allocateAndDispose(final IterationThreadProvider provider)
	{
		// Force thread creation. The threads do nothing useful here — a no-op
		// Runnable is sufficient. The point is to populate reservedThreads.
		final var threads = provider.startIterationThreads(null, 2, () -> () -> {
		});
		provider.disposeIterationThreads(threads);
	}

	private static Thread firstReservedThread(final IterationThreadProvider provider) throws Exception
	{
		final Field reservedThreadsField = provider.getClass().getDeclaredField("reservedThreads");
		reservedThreadsField.setAccessible(true);
		final Object reservedThreads = reservedThreadsField.get(provider);

		final Method firstMethod = reservedThreads.getClass().getMethod("first");
		return (Thread)firstMethod.invoke(reservedThreads);
	}
}
