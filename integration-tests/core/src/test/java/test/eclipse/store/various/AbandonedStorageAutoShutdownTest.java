package test.eclipse.store.various;

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

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.types.StorageSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Regression test for internal#97: the storage's auto-shutdown-by-unreachability mechanism —
 * when the application drops all references to its {@link EmbeddedStorageManager} WITHOUT calling
 * {@code shutdown()}, the {@code StorageOperationController}'s {@code WeakReference<StorageSystem>}
 * clears, the channel threads observe processing disabled and exit, and the whole engine (threads +
 * off-heap entity cache) is released.
 * <p>
 * store#755 (commit 24e8ccc0) added a strong {@code StorageSystem} field to {@code StorageTaskBroker}
 * (to reach the shared mark monitor for the task-scoped pending-load gate). Every channel holds the
 * broker and the live channel threads are GC roots, so the chain Thread &rarr; channel &rarr; broker
 * &rarr; StorageSystem keeps the system strongly reachable forever: the weak reference never clears,
 * auto-shutdown never fires, the non-daemon channel threads run forever and pin the off-heap cache.
 * <p>
 * This test wraps the (reflectively obtained) StorageSystem in a WeakReference, drops the manager,
 * and asserts the reference clears within a bounded GC budget. RED on the buggy engine (never
 * clears). Safe for CI: on the buggy path the leaked-but-still-reachable system is shut down in the
 * finally block via the WeakReference's own referent, so no non-daemon thread is left running.
 */
public class AbandonedStorageAutoShutdownTest
{
	@Test
	@Timeout(value = 120, unit = TimeUnit.SECONDS)
	void abandonedManagerWithoutShutdown_engineMustBecomeUnreachable(@TempDir final Path dir) throws Exception
	{
		WeakReference<StorageSystem> weakSystem = null;
		try
		{
			weakSystem = startAndAbandon(dir);

			// Force GC repeatedly; on a correct engine the weak StorageSystem clears within a cycle or
			// two (channel threads then self-terminate). On the buggy engine the live channel threads
			// pin the system through the broker's strong reference, so it never clears.
			final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
			while(weakSystem.get() != null && System.nanoTime() < deadline)
			{
				System.gc();
				Thread.sleep(50);
			}

			assertNull(weakSystem.get(),
				"internal#97: StorageSystem is still strongly reachable after the manager was abandoned "
				+ "without shutdown() — auto-shutdown-by-unreachability cannot fire, channel threads leak");
		}
		finally
		{
			// If the system leaked (buggy engine), it is still reachable via the weak referent — shut it
			// down so no non-daemon channel thread survives the test and wedges the surefire fork.
			final StorageSystem leaked = weakSystem == null ? null : weakSystem.get();
			if(leaked != null)
			{
				try
				{
					leaked.shutdown();
				}
				catch(final Throwable ignored)
				{
					// best effort
				}
			}
		}
	}

	/**
	 * Starts a storage, reflectively wraps its StorageSystem in a WeakReference, and returns that
	 * reference. The manager and all strong references to the system created here go out of scope on
	 * return, so only the (buggy) internal retention can keep the system alive.
	 */
	private static WeakReference<StorageSystem> startAndAbandon(final Path dir) throws Exception
	{
		final EmbeddedStorageManager manager = EmbeddedStorage.start(new ArrayList<>(), dir);
		manager.storeRoot();

		final Field f = manager.getClass().getDeclaredField("storageSystem");
		f.setAccessible(true);
		final StorageSystem system = (StorageSystem)f.get(manager);

		return new WeakReference<>(system);
		// manager + system are local: eligible for GC on return unless internally retained.
	}
}
