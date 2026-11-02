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
import static test.eclipse.store.locking.LockFileTestSupport.assertRefused;
import static test.eclipse.store.locking.LockFileTestSupport.startManager;

import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies that the storage-directory lock file excludes a second, independently-identified storage
 * from opening a directory that is already in use - including when the lock file has been emptied,
 * which must never be interpreted as a free directory.
 */
public class StorageLockFileExclusionTest
{
	@Test
	@Timeout(120)
	void secondProcessIsRefusedWhileFirstIsActive(@TempDir final Path dir)
	{
		final EmbeddedStorageManager first = startManager(dir, "lock-excl-active-a", "PROCESS-A", 60_000L);
		try
		{
			first.setRoot("owned-by-A");
			first.storeRoot();

			assertRefused(() -> startManager(dir, "lock-excl-active-b", "PROCESS-B", 60_000L));
		}
		finally
		{
			first.shutdown();
		}
	}

	@Test
	@Timeout(120)
	void secondProcessIsRefusedWhenLockFileIsCleared(@TempDir final Path dir) throws Exception
	{
		final Path lockFile = dir.resolve("used.lock");

		// A very large interval keeps the active manager from rewriting the lock file during the test,
		// so the emptied state below is what the second manager actually observes.
		final EmbeddedStorageManager first = startManager(dir, "lock-excl-cleared-a", "PROCESS-A", 600_000_000L);
		try
		{
			first.setRoot("owned-by-A");
			first.storeRoot();
			assertTrue(Files.size(lockFile) > 0, "lock file must exist while the storage is in use");

			// Emptying the lock file models both the truncate window of a heartbeat and an externally
			// cleared file; neither must be treated as a free directory.
			Files.write(lockFile, new byte[0]);
			assertTrue(Files.size(lockFile) == 0L, "precondition: lock file must be empty");

			assertRefused(() -> startManager(dir, "lock-excl-cleared-b", "PROCESS-B", 600_000_000L));
		}
		finally
		{
			first.shutdown();
		}
	}
}
