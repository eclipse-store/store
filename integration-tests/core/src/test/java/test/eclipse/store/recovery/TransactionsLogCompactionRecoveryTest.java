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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.types.Storage;
import org.eclipse.store.storage.types.StorageConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Compacting the transactions log must preserve the exact reconciliation state - the store anchors
 * and the post-store transfer entries recovery re-reads - so a restart after a compaction rebuilds
 * the same live state as before. A compaction that dropped a transfer would leave recovery unable to
 * find relocated bytes; this proves the compacted log still restarts with complete data.
 * <p>
 * Regression guard: compaction recovery works before and after this change; this only locks it against regressions.
 */
@Timeout(180)
class TransactionsLogCompactionRecoveryTest
{
	private static final int SLOTS     = 16  ;
	private static final int BLOB_SIZE = 2048;
	private static final int ROUNDS    = 8   ;
	private static final int MAX_FILE  = 8192;
	private static final int CHANNELS  = 2   ;

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
	void compactedLogRestartsWithCompleteState() throws InterruptedException
	{
		this.storage = EmbeddedStorage.start(this.config());
		final Root root = new Root();
		this.storage.setRoot(root);
		this.storage.storeRoot();

		this.overwriteAllSlots(root);

		// dissolve garbage (produces transfers into the head), then compact the log
		for(int attempt = 0; attempt < 20; attempt++)
		{
			System.gc();
			this.storage.issueFullGarbageCollection();
			this.storage.issueFullFileCheck();
			this.storage.issueTransactionsLogCleanup();
		}
		final long compactedSize = this.totalTransactionsLogSize();

		// keep going after the compaction: further stores append onto the compacted log
		this.overwriteAllSlots(root);

		this.storage.shutdown();
		this.storage = null;

		this.reloaded = EmbeddedStorage.start(this.config());
		final Root reloadedRoot = (Root)this.reloaded.root();
		assertNotNull(reloadedRoot);
		for(int i = 0; i < SLOTS; i++)
		{
			assertNotNull(reloadedRoot.slots[i], "slot " + i + " lost after compaction");
			assertEquals((byte)(ROUNDS - 1), reloadedRoot.slots[i][0], "slot " + i + " has the wrong version");
		}
		assertTrue(compactedSize > 0, "the transactions log must not be empty after compaction");
	}

	@Test
	void compactionReducesAGarbageHeavyLog() throws InterruptedException
	{
		this.storage = EmbeddedStorage.start(this.config());
		final Root root = new Root();
		this.storage.setRoot(root);
		this.storage.storeRoot();
		this.overwriteAllSlots(root);

		// dissolve and delete files, filling the log with creation/deletion entries
		for(int attempt = 0; attempt < 20; attempt++)
		{
			System.gc();
			this.storage.issueFullGarbageCollection();
			this.storage.issueFullFileCheck();
		}
		final long beforeCompaction = this.totalTransactionsLogSize();

		long afterCompaction = beforeCompaction;
		for(int attempt = 0; attempt < 20 && afterCompaction >= beforeCompaction; attempt++)
		{
			this.storage.issueTransactionsLogCleanup();
			afterCompaction = this.totalTransactionsLogSize();
			if(afterCompaction >= beforeCompaction)
			{
				Thread.sleep(50);
			}
		}

		assertTrue(afterCompaction < beforeCompaction,
			"compaction must shrink a garbage-heavy log (before " + beforeCompaction + ", after " + afterCompaction + ")");
	}


	private void overwriteAllSlots(final Root root)
	{
		for(int round = 0; round < ROUNDS; round++)
		{
			for(int i = 0; i < SLOTS; i++)
			{
				final byte[] blob = new byte[BLOB_SIZE];
				blob[0] = (byte)round;
				root.slots[i] = blob;
			}
			this.storage.store(root.slots);
		}
	}

	private long totalTransactionsLogSize()
	{
		long total = 0;
		for(int channel = 0; channel < CHANNELS; channel++)
		{
			total += RecoverySimulation.size(RecoverySimulation.transactionsFile(this.directory, channel));
		}
		return total;
	}

	private StorageConfiguration config()
	{
		return StorageConfiguration.Builder()
			.setStorageFileProvider(Storage.FileProvider(this.directory))
			.setChannelCountProvider(Storage.ChannelCountProvider(CHANNELS))
			.setHousekeepingController(Storage.HousekeepingController(50, 5_000_000L))
			.setDataFileEvaluator(Storage.DataFileEvaluator(1024, MAX_FILE, 0.75))
			.createConfiguration();
	}


	public static class Root
	{
		byte[][] slots = new byte[SLOTS][];
	}
}
