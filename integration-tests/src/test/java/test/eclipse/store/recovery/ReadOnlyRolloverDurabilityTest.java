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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;

import org.eclipse.serializer.afs.types.WriteController;
import org.eclipse.serializer.persistence.exceptions.PersistenceExceptionStoringDisabled;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.types.StorageWriteControllerReadOnlyMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Read-only mode and file rollovers.
 * <p>
 * Storing is rejected up front in read-only mode, so no write - and therefore no rollover - can
 * originate while the storage is read-only. Toggling read-only around a rolling store must leave the
 * storage healthy: the channel keeps running, writes and rollovers resume once writable again, and no
 * committed data is lost across a restart.
 * <p>
 * Regression guard: this behaviour holds before and after this change; it locks in read-only store
 * rejection and durability across a read-only round-trip.
 */
@Timeout(120)
class ReadOnlyRolloverDurabilityTest
{
	private static final int CHANNELS      = 2      ;
	private static final int MAX_FILE_SIZE = 20_000 ;
	private static final int BLOB_SIZE     = 25_000 ; // > file size: every stored blob forces a rollover

	@TempDir
	Path directory;

	private EmbeddedStorageManager storage ;
	private EmbeddedStorageManager reloaded;

	@AfterEach
	void shutdown()
	{
		for(final EmbeddedStorageManager manager : new EmbeddedStorageManager[]{this.reloaded, this.storage})
		{
			if(manager != null && manager.isRunning())
			{
				try
				{
					manager.shutdown();
				}
				catch(final RuntimeException ignored)
				{
					// best effort
				}
			}
		}
	}

	@Test
	void readOnlyRejectsStoresAndSurvivesTogglingWithoutDataLoss()
	{
		final StorageWriteControllerReadOnlyMode writeController =
			new StorageWriteControllerReadOnlyMode(WriteController.Enabled());
		writeController.setReadOnly(false);

		this.storage = EmbeddedStorage.Foundation(RecoverySimulation.quietConfig(this.directory, CHANNELS, MAX_FILE_SIZE))
			.setWriteController(writeController)
			.start();

		final Root root = new Root();
		this.storage.setRoot(root);
		root.blobs.add(new byte[BLOB_SIZE]); // rollover while writable
		this.storage.store(root.blobs);
		this.storage.storeRoot();

		// read-only rejects stores up front, so a rollover cannot originate here
		writeController.setReadOnly(true);
		assertThrows(PersistenceExceptionStoringDisabled.class, () -> this.storage.store(new byte[BLOB_SIZE]),
			"storing must be rejected in read-only mode");
		assertTrue(this.storage.isRunning(), "a rejected read-only store must not disrupt the channel");

		// writable again: stores and rollovers resume and stay durable
		writeController.setReadOnly(false);
		root.blobs.add(new byte[BLOB_SIZE]);
		this.storage.store(root.blobs);
		assertTrue(this.storage.issueStorageFlush(), "flush must succeed once writable again");

		final int expected = root.blobs.size();
		this.storage.shutdown();
		this.storage = null;

		this.reloaded = EmbeddedStorage.start(RecoverySimulation.quietConfig(this.directory, CHANNELS, MAX_FILE_SIZE));
		final Root reloadedRoot = (Root)this.reloaded.root();
		assertEquals(expected, reloadedRoot.blobs.size(), "no data lost across the read-only toggling");
	}


	public static class Root
	{
		ArrayList<byte[]> blobs = new ArrayList<>();
	}
}
