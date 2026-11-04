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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.types.Storage;
import org.eclipse.store.storage.types.StorageConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * The physical deletion of a dissolved data file is gated on all-channel durability: a not-yet-durable
 * store could still be rolled back and would then re-read the transferred bytes from that file. The
 * deferral must resolve on its own - the deferring channel requests a flush, and once the flush
 * raises the all-durable watermark the file cleanup deletes the file. This proves the gate does not
 * starve the cleanup and never drops live data in the process.
 * <p>
 * Regression guard: the outcome (files reclaimed, nothing lost) holds before and after this change;
 * this locks it against regressions in the durability gate added here.
 */
@Timeout(180)
class DurabilityGateDeferralTest
{
	private static final int SLOTS      = 16  ;
	private static final int BLOB_SIZE  = 2048;
	private static final int ROUNDS     = 8   ;
	private static final int MAX_FILE   = 8192;

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
	void dissolvedFilesAreEventuallyDeletedMultiChannel() throws InterruptedException
	{
		this.runConvergence(2);
	}

	@Test
	void dissolvedFilesAreEventuallyDeletedSingleChannel() throws InterruptedException
	{
		this.runConvergence(1);
	}


	private void runConvergence(final int channelCount) throws InterruptedException
	{
		this.storage = EmbeddedStorage.start(this.config(channelCount));

		// full overwrites turn earlier data files into garbage: they fall below the fill ratio, get
		// dissolved (live entities moved to the head) and become deletion candidates the gate defers
		final Root root = new Root();
		this.storage.setRoot(root);
		this.storage.storeRoot();
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

		final long peak = this.countDataFiles();

		// GC marks the garbage dead, the file check dissolves and deletes; the flush the check enqueues
		// clears the durability gate. System.gc() first so the superseded blobs' registry entries clear.
		long remaining = peak;
		for(int attempt = 0; attempt < 100 && remaining >= peak; attempt++)
		{
			System.gc();
			this.storage.issueFullGarbageCollection();
			this.storage.issueFullFileCheck();
			remaining = this.countDataFiles();
			if(remaining >= peak)
			{
				Thread.sleep(100);
			}
		}

		assertTrue(remaining < peak,
			"no data file was ever deleted (peak " + peak + ", now " + remaining + "): the gate starves cleanup");
		assertTrue(remaining <= channelCount * 10L,
			"file cleanup did not converge: " + remaining + " files remain of peak " + peak);

		// the surviving files must still carry the complete latest state
		this.storage.shutdown();
		this.storage = null;
		this.reloaded = EmbeddedStorage.start(this.config(channelCount));
		final Root reloadedRoot = (Root)this.reloaded.root();
		assertNotNull(reloadedRoot);
		for(int i = 0; i < SLOTS; i++)
		{
			assertNotNull(reloadedRoot.slots[i], "slot " + i + " lost");
			assertEquals((byte)(ROUNDS - 1), reloadedRoot.slots[i][0], "slot " + i + " has the wrong version");
		}
	}

	private long countDataFiles()
	{
		try(Stream<Path> paths = Files.walk(this.directory))
		{
			return paths.filter(path -> path.getFileName().toString().endsWith(".dat")).count();
		}
		catch(final IOException e)
		{
			throw new UncheckedIOException(e);
		}
	}

	private StorageConfiguration config(final int channelCount)
	{
		return StorageConfiguration.Builder()
			.setStorageFileProvider(Storage.FileProvider(this.directory))
			.setChannelCountProvider(Storage.ChannelCountProvider(channelCount))
			.setHousekeepingController(Storage.HousekeepingController(50, 5_000_000L))
			.setDataFileEvaluator(Storage.DataFileEvaluator(1024, MAX_FILE, 0.75))
			.createConfiguration();
	}


	public static class Root
	{
		byte[][] slots = new byte[SLOTS][];
	}
}
