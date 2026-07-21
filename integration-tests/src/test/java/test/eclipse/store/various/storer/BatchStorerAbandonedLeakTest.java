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

import org.eclipse.serializer.persistence.types.BatchStorer;
import org.eclipse.serializer.persistence.types.Storer;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Reproducer for internal#101: a {@code Batching} storer starts a single-thread
 * {@code ScheduledExecutorService} in its constructor and shuts it down only in {@code close()}. If
 * the scheduled task strongly references the storer, an abandoned batch storer (its buffers, hash
 * slots, trusted-object-id set) plus one background thread leak for the JVM lifetime.
 * <p>
 * Both tests turn green once the flush scheduler holds the storer weakly and self-terminates when the
 * referent has been collected.
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

	private static void gcAndWait() throws InterruptedException
	{
		for(int r = 0; r < 6; r++)
		{
			System.gc();
			Thread.sleep(150);
		}
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

			gcAndWait();
			storage.issueFullGarbageCollection(); // release the live-storer registry hold (internal#100)
			gcAndWait();

			final int leaked = flushThreadCount() - before;
			assertTrue(
				leaked < n,
				"abandoning " + n + " batch storers without close() leaked " + leaked
					+ " '" + FLUSH_THREAD_PREFIX + "' threads; the flush scheduler is shut down only in "
					+ "close() -- internal#101"
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
			gcAndWait();
			storage.issueFullGarbageCollection();
			gcAndWait();

			assertNull(
				plainRef.get(),
				"control: an abandoned non-batch storer must be collectable after a GC sweep"
			);
			assertNull(
				batchRef.get(),
				"abandoned batch storer is pinned forever by its flush scheduler and cannot be collected "
					+ "-- internal#101"
			);
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
