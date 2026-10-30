package test.eclipse.store.various.storer;

/*-
 * #%L
 * EclipseStore Integration Tests
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

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.ref.WeakReference;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import org.eclipse.serializer.persistence.types.BatchStorer;
import org.eclipse.serializer.persistence.types.Storer;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Regression test for the leak where a {@code Batching} storer dropped without {@code close()} was
 * pinned forever by its own background flush scheduler: the scheduled task strongly referenced the
 * storer, so its buffers, hash slots and trusted-object-id set plus one {@code batch-storer-flush}
 * daemon thread leaked for the JVM lifetime.
 * <p>
 * Both tests wait for garbage collection with a bounded deadline loop ({@link #awaitGc}) rather than a
 * fixed number of {@code System.gc()} rounds, so they fail deterministically instead of depending on
 * GC timing. Mirrors {@code VectorIndexAbandonmentLeakTest}, the sibling abandonment-leak test.
 */
public class BatchStorerAbandonedLeakTest
{
	private static final String FLUSH_THREAD_PREFIX = "batch-storer-flush";

	private static int flushThreadCount()
	{
		int n = 0;
		for(final Thread t : Thread.getAllStackTraces().keySet())
		{
			if(t.getName() != null && t.getName().startsWith(FLUSH_THREAD_PREFIX))
			{
				n++;
			}
		}
		return n;
	}

	/**
	 * Repeatedly triggers GC and waits until {@code condition} holds or the timeout elapses.
	 *
	 * @return {@code true} if the condition became true within the timeout
	 */
	private static boolean awaitGc(final BooleanSupplier condition, final long timeoutMs) throws InterruptedException
	{
		final long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
		while(System.nanoTime() < deadline)
		{
			System.gc();
			if(condition.getAsBoolean())
			{
				return true;
			}
			Thread.sleep(100L);
		}
		return condition.getAsBoolean();
	}

	@Test
	@Timeout(value = 120, unit = TimeUnit.SECONDS)
	public void abandonedBatchStorersLeakTheirFlushThreads(@TempDir final Path dir) throws Exception
	{
		final int n = 30;
		try(final EmbeddedStorageManager storage = EmbeddedStorage.start(new ArrayList<String>(), dir))
		{
			final int before = flushThreadCount();

			for(int i = 0; i < n; i++)
			{
				BatchStorer storer = storage.createBatchStorer(
					BatchStorer.Controller(Duration.ofMillis(500)),
					Duration.ofMillis(50)
				);
				storer.store(storage.root());
				storer = null; // abandon WITHOUT close()
			}

			// Release the live-storer registry hold so the abandoned storers become collectable; each
			// storer's flush scheduler then self-terminates on its next tick once the storer is gone.
			storage.issueFullGarbageCollection();
			final boolean released = awaitGc(() -> flushThreadCount() <= before, 60_000L);

			assertTrue(
				released,
				"abandoning " + n + " batch storers without close() leaked "
					+ (flushThreadCount() - before) + " '" + FLUSH_THREAD_PREFIX + "' threads; the flush "
					+ "scheduler must self-terminate when its storer is garbage-collected"
			);
		}
	}

	@Test
	@Timeout(value = 120, unit = TimeUnit.SECONDS)
	public void abandonedBatchStorerInstanceIsPinnedByItsScheduler(@TempDir final Path dir) throws Exception
	{
		try(final EmbeddedStorageManager storage = EmbeddedStorage.start(new ArrayList<String>(), dir))
		{
			final WeakReference<Object> batchRef = createAbandonedBatchStorer(storage);
			final WeakReference<Object> plainRef = createAbandonedDefaultStorer(storage);

			// A full GC sweep releases every live storer from the object manager's live-storer registry.
			storage.issueFullGarbageCollection();

			assertTrue(
				awaitGc(() -> plainRef.get() == null, 30_000L),
				"control: an abandoned non-batch storer must be collectable after a GC sweep"
			);
			assertTrue(
				awaitGc(() -> batchRef.get() == null, 30_000L),
				"abandoned batch storer must not be pinned by its flush scheduler and must be collectable"
			);
			assertNull(batchRef.get());
		}
	}

	private static WeakReference<Object> createAbandonedBatchStorer(final EmbeddedStorageManager storage)
	{
		final BatchStorer storer = storage.createBatchStorer(
			BatchStorer.Controller(Duration.ofMillis(500)),
			Duration.ofMillis(50)
		);
		storer.store(storage.root());
		return new WeakReference<>(storer); // storer goes out of scope on return -> abandoned, not closed
	}

	private static WeakReference<Object> createAbandonedDefaultStorer(final EmbeddedStorageManager storage)
	{
		final Storer storer = storage.createStorer();
		storer.store(storage.root());
		return new WeakReference<>(storer);
	}
}
