package test.eclipse.store.locking;

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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static test.eclipse.store.locking.LockFileTestSupport.awaitLockThreadCount;
import static test.eclipse.store.locking.LockFileTestSupport.deleteQuietly;
import static test.eclipse.store.locking.LockFileTestSupport.fieldValueOfType;
import static test.eclipse.store.locking.LockFileTestSupport.forceStop;
import static test.eclipse.store.locking.LockFileTestSupport.liveLockThreadCount;
import static test.eclipse.store.locking.LockFileTestSupport.startManager;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies that the lock file's non-daemon worker thread is always released when the storage stops,
 * both on a graceful shutdown and on a non-graceful (kill) teardown, so a stopped storage never keeps
 * the JVM alive. Counts are taken relative to a baseline so the assertions are independent of any
 * unrelated threads that may exist in the test JVM.
 */
public class StorageLockFileThreadLifecycleTest
{
	@Test
	@Timeout(120)
	void gracefulShutdownStopsLockThread(@TempDir final Path dir) throws Exception
	{
		final long baseline = liveLockThreadCount();

		final EmbeddedStorageManager manager = startManager(dir, "lock-life-graceful", "PROCESS-A", 60_000L);
		assertTrue(awaitLockThreadCount(baseline + 1, 2_000L), "lock thread must be running after start");

		manager.shutdown();
		assertTrue(awaitLockThreadCount(baseline, 3_000L), "lock thread must be gone after shutdown");
	}

	@Test
	@Timeout(120)
	void nonGracefulStopStopsLockThread() throws Exception
	{
		// A manually managed directory (not @TempDir) is used because a non-graceful stop does not join
		// the channel threads, so files may still be held briefly; cleanup is therefore best-effort.
		final Path dir = Files.createTempDirectory("lockfile-kill-");
		final long baseline = liveLockThreadCount();

		final EmbeddedStorageManager manager = startManager(dir, "lock-life-kill", "PROCESS-A", 60_000L);
		Object lockFileManager = null;
		try
		{
			assertTrue(awaitLockThreadCount(baseline + 1, 2_000L), "lock thread must be running after start");

			final Object storageSystem = fieldValueOfType(manager, "org.eclipse.store.storage.types.StorageSystem");
			lockFileManager = fieldValueOfType(storageSystem, "org.eclipse.store.storage.types.StorageLockFileManager");

			final Method killStorage = storageSystem.getClass().getMethod("killStorage", Throwable.class);
			killStorage.invoke(storageSystem, (Throwable)null);

			assertTrue(awaitLockThreadCount(baseline, 3_000L), "lock thread must be gone after a non-graceful stop");
		}
		finally
		{
			forceStop(lockFileManager);
			deleteQuietly(dir);
		}
	}
}
