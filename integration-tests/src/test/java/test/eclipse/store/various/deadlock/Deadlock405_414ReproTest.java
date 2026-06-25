package test.eclipse.store.various.deadlock;

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

import static org.junit.jupiter.api.Assertions.fail;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.serializer.persistence.types.Storer;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Reproducer for deadlock issues #405 and #414 in BinaryStorer.Default.
 *
 * Old documented lock order (in BinaryStorer.Default before the fix):
 *   1) ObjectManager: synchronized(objectRegistry)
 *   2) Storer:        synchronized(this.head)
 *
 * Forbidden direction: head -> registry.
 *
 * BinaryStorer.Default.storeItem violated this in the legacy code: it held
 * this.head across typeHandler.store(...), which recurses through
 * apply -> register -> objectManager.ensureObjectId — and that acquires the
 * registry. Concurrently another thread inside ensureObjectId holds the
 * registry and then walks peer storers via synchCheckLocalRegistries, which
 * acquires the peer's head. The two-thread cycle:
 *
 *   Thread A: holds storer1.head, wants objectRegistry  (illegal direction)
 *   Thread B: holds objectRegistry, wants storer1.head  (legal direction)
 *
 * The fix collapses storer.head's monitor role into the registry monitor
 * (objectRegistryMonitor == objectRegistry), so the two-level hierarchy
 * becomes one level and the cycle cannot form.
 *
 * <p>Test strategy:
 * <ul>
 *   <li>Spawn many worker threads, each owning its own lazy storer.</li>
 *   <li>Each worker repeatedly builds a fresh graph of unique nodes,
 *       calls store + commit on its own storer.</li>
 *   <li>Fresh instances force the ensureObjectId path that walks peer
 *       local registries (synchCheckLocalRegistries).</li>
 *   <li>A monitor thread polls {@link ThreadMXBean#findDeadlockedThreads()}
 *       and fails fast with a thread dump on detection.</li>
 * </ul>
 */
public class Deadlock405_414ReproTest
{
	private static final int    THREADS         = 16;
	private static final int    ROUNDS_PER_THR  = 200;
	private static final int    GRAPH_NODES     = 200;
	private static final long   WALL_BUDGET_MS  = 60_000L;
	private static final long   POLL_INTERVAL   = 250L;

	@TempDir
	Path tempDir;

	/** Linked chain — deep recursion through typeHandler.store. */
	static final class Node
	{
		String name;
		Node   next;

		Node(final String name)
		{
			this.name = name;
		}
	}

	@Test
	void parallelLazyStorers_doNotDeadlock() throws Exception
	{
		try (EmbeddedStorageManager storage = EmbeddedStorage.start(new ArrayList<>(), this.tempDir))
		{
			final CountDownLatch              start    = new CountDownLatch(1);
			final CountDownLatch              done     = new CountDownLatch(THREADS);
			final AtomicReference<Throwable>  failure  = new AtomicReference<>();
			final Thread[]                    workers  = new Thread[THREADS];

			for (int t = 0; t < THREADS; t++)
			{
				final int idx = t;
				workers[t] = new Thread(() ->
				{
					try
					{
						start.await();
						final Storer storer = storage.createStorer();
						for (int r = 0; r < ROUNDS_PER_THR; r++)
						{
							final List<Node> graph = buildChain(idx, r, GRAPH_NODES);
							storer.store(graph);
							storer.commit();
						}
					}
					catch (final Throwable th)
					{
						failure.compareAndSet(null, th);
					}
					finally
					{
						done.countDown();
					}
				}, "deadlock-worker-" + idx);
				workers[t].setDaemon(true);
				workers[t].start();
			}

			start.countDown();

			final ThreadMXBean tmx      = ManagementFactory.getThreadMXBean();
			final long         deadline = System.currentTimeMillis() + WALL_BUDGET_MS;

			while (System.currentTimeMillis() < deadline)
			{
				if (done.await(POLL_INTERVAL, TimeUnit.MILLISECONDS))
				{
					break;
				}
				final long[] cycle = tmx.findDeadlockedThreads();
				if (cycle != null && cycle.length > 0)
				{
					failWithDeadlockReport(tmx, cycle);
				}
			}

			if (done.getCount() > 0)
			{
				final long[] cycle = tmx.findDeadlockedThreads();
				if (cycle != null && cycle.length > 0)
				{
					failWithDeadlockReport(tmx, cycle);
				}
				failWithAllThreadDump("Workers did not finish within "
					+ WALL_BUDGET_MS + " ms but no formal JMX deadlock cycle was detected.");
			}

			if (failure.get() != null)
			{
				throw new AssertionError("Worker threw", failure.get());
			}
		}
	}

	private static List<Node> buildChain(final int threadIdx, final int round, final int nodes)
	{
		final List<Node> list = new ArrayList<>(nodes);
		Node prev = null;
		for (int i = 0; i < nodes; i++)
		{
			final Node n = new Node("t" + threadIdx + "-r" + round + "-n" + i);
			if (prev != null)
			{
				prev.next = n;
			}
			prev = n;
			list.add(n);
		}
		return list;
	}

	private static void failWithDeadlockReport(final ThreadMXBean tmx, final long[] ids)
	{
		final StringBuilder sb = new StringBuilder();
		sb.append("DEADLOCK DETECTED across ").append(ids.length).append(" threads:\n\n");
		final ThreadInfo[] infos = tmx.getThreadInfo(ids, true, true);
		for (final ThreadInfo ti : infos)
		{
			sb.append(ti).append('\n');
		}
		fail(sb.toString());
	}

	private static void failWithAllThreadDump(final String header)
	{
		final ThreadMXBean tmx   = ManagementFactory.getThreadMXBean();
		final ThreadInfo[] infos = tmx.dumpAllThreads(true, true);
		final StringBuilder sb   = new StringBuilder(header).append("\n\nFull thread dump:\n\n");
		for (final ThreadInfo ti : infos)
		{
			sb.append(ti).append('\n');
		}
		fail(sb.toString());
	}
}
