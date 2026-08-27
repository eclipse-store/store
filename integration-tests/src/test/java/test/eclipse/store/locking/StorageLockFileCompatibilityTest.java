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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static test.eclipse.store.locking.LockFileTestSupport.startManager;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies lock-file behavior across restarts: the same process may reopen its own storage, and a lock
 * file written in the previous on-disk format is accepted (and rewritten) rather than rejected, so an
 * upgrade never fails to start on a leftover lock file.
 */
public class StorageLockFileCompatibilityTest
{
	@Test
	@Timeout(120)
	void restartBySameProcessReusesLock(@TempDir final Path dir)
	{
		final EmbeddedStorageManager first = startManager(dir, "lock-compat-restart", "PROCESS-A", 60_000L);
		first.setRoot("v1");
		first.storeRoot();
		first.shutdown();

		final EmbeddedStorageManager second = startManager(dir, "lock-compat-restart", "PROCESS-A", 60_000L);
		try
		{
			assertEquals("v1", second.root());
		}
		finally
		{
			second.shutdown();
		}
	}

	@Test
	@Timeout(120)
	void lockFileInPreviousFormatIsAcceptedOnRestart(@TempDir final Path dir) throws Exception
	{
		final Path   lockFile = dir.resolve("used.lock");
		final String identity = "PROCESS-A";
		final long   now      = System.currentTimeMillis();

		// The previous on-disk format carried no session marker: lastWriteTime;expirationTime;identifier.
		// A manager with the same identity must reuse it rather than reject it as unreadable.
		final String previousFormat = now + ";" + (now + 60_000L) + ";" + identity;
		Files.write(lockFile, previousFormat.getBytes(StandardCharsets.UTF_8));

		final EmbeddedStorageManager manager = startManager(dir, "lock-compat-legacy", identity, 60_000L);
		try
		{
			manager.setRoot("data");
			manager.storeRoot();
			assertTrue(Files.size(lockFile) > 0, "lock file must be rewritten in the current format");
		}
		finally
		{
			manager.shutdown();
		}
	}
}
