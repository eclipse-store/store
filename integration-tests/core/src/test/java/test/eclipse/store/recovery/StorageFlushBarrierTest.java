package test.eclipse.store.recovery;

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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.eclipse.serializer.afs.types.WriteController;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.types.StorageWriteControllerReadOnlyMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * The explicit storage-flush barrier and its honesty contract: it reports {@code true} only when it
 * actually synchronized every channel, and {@code false} when it was skipped - so a caller can never
 * mistake a skipped flush for a durability guarantee it does not hold.
 * <p>
 * New-feature guard: the storage-flush barrier did not exist before this change.
 */
@Timeout(120)
class StorageFlushBarrierTest
{
	private static final int MAX_FILE_SIZE = 50_000_000;

	@TempDir
	Path directory;

	private EmbeddedStorageManager storage;

	@AfterEach
	void shutdown()
	{
		if(this.storage != null && this.storage.isRunning())
		{
			try
			{
				this.storage.shutdown();
			}
			catch(final RuntimeException ignored)
			{
				// best effort
			}
		}
	}

	@Test
	void flushReportsSuccessWhenWritable()
	{
		this.storage = EmbeddedStorage.start(RecoverySimulation.quietConfig(this.directory, 2, MAX_FILE_SIZE));
		final Root root = new Root();
		this.storage.setRoot(root);
		root.payload = new byte[512];
		this.storage.storeRoot();

		assertTrue(this.storage.issueStorageFlush(), "a writable flush must report success");
	}

	@Test
	void repeatedFlushWithoutStoresReportsSuccess()
	{
		this.storage = EmbeddedStorage.start(RecoverySimulation.quietConfig(this.directory, 2, MAX_FILE_SIZE));
		this.storage.setRoot(new Root());
		this.storage.storeRoot();

		assertTrue(this.storage.issueStorageFlush(), "first flush must succeed");
		assertTrue(this.storage.issueStorageFlush(), "a no-op flush must still report success");
	}

	@Test
	void flushReportsSkipInReadOnlyMode()
	{
		final StorageWriteControllerReadOnlyMode writeController =
			new StorageWriteControllerReadOnlyMode(WriteController.Enabled());
		writeController.setReadOnly(false);

		this.storage = EmbeddedStorage.Foundation(RecoverySimulation.quietConfig(this.directory, 2, MAX_FILE_SIZE))
			.setWriteController(writeController)
			.start();
		final Root root = new Root();
		this.storage.setRoot(root);
		root.payload = new byte[512];
		this.storage.storeRoot();

		assertTrue(this.storage.issueStorageFlush(), "writable: flush must succeed");

		writeController.setReadOnly(true);
		assertFalse(this.storage.issueStorageFlush(), "read-only: a skipped flush must report no durability");

		writeController.setReadOnly(false);
		assertTrue(this.storage.issueStorageFlush(), "writable again: flush must succeed again");
	}


	public static class Root
	{
		byte[] payload;
	}
}
