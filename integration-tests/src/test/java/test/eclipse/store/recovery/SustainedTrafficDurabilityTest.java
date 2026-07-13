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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Under continuous store traffic the durability-covered maintenance must keep making progress: a
 * store rolling in behind every barrier must not permanently defer the file cleanup, or dissolved
 * files would accumulate at the write rate. The data-file count must stay bounded during sustained
 * traffic and converge afterwards, with no live data lost.
 * <p>
 * Regression guard: cleanup keeps up before and after this change; this locks it against regressions
 * in the maintenance path added here.
 */
@Tag("slow")
@Timeout(180)
class SustainedTrafficDurabilityTest
{
	private static final int  SLOTS         = 16    ;
	private static final int  BLOB_SIZE     = 2048  ;
	private static final int  MAX_FILE      = 8192  ;
	private static final int  CHANNELS      = 2     ;
	private static final long TRAFFIC_MS    = 4000  ;
	private static final int  SENTINEL      = 0x5A  ;

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
	void fileCleanupKeepsUpUnderContinuousStoreTraffic() throws InterruptedException
	{
		this.storage = EmbeddedStorage.start(this.config());
		final Root root = new Root();
		this.storage.setRoot(root);
		this.storage.storeRoot();

		final long deadline = java.lang.System.currentTimeMillis() + TRAFFIC_MS;
		final Thread traffic = new Thread(() ->
		{
			int round = 0;
			while(java.lang.System.currentTimeMillis() < deadline)
			{
				for(int i = 0; i < SLOTS; i++)
				{
					final byte[] blob = new byte[BLOB_SIZE];
					blob[0] = (byte)round;
					root.slots[i] = blob;
				}
				this.storage.store(root.slots);
				round++;
			}
		}, "recovery-traffic");
		traffic.start();

		// drive the durability-covered maintenance concurrently with the store traffic
		while(traffic.isAlive())
		{
			this.storage.issueFullGarbageCollection();
			this.storage.issueFullFileCheck();
			Thread.sleep(50);
		}
		traffic.join();

		// settle: a final known write, then converge - cleanup must reclaim the accumulated garbage
		for(int i = 0; i < SLOTS; i++)
		{
			final byte[] blob = new byte[BLOB_SIZE];
			blob[0] = (byte)SENTINEL;
			root.slots[i] = blob;
		}
		this.storage.store(root.slots);

		long remaining = Long.MAX_VALUE;
		for(int attempt = 0; attempt < 40 && remaining > CHANNELS * 12L; attempt++)
		{
			System.gc();
			this.storage.issueFullGarbageCollection();
			this.storage.issueFullFileCheck();
			remaining = this.countDataFiles();
			if(remaining > CHANNELS * 12L)
			{
				Thread.sleep(50);
			}
		}
		assertTrue(remaining <= CHANNELS * 12L,
			"file cleanup did not converge after sustained traffic: " + remaining + " files remain");

		this.storage.shutdown();
		this.storage = null;
		this.reloaded = EmbeddedStorage.start(this.config());
		final Root reloadedRoot = (Root)this.reloaded.root();
		assertNotNull(reloadedRoot);
		for(int i = 0; i < SLOTS; i++)
		{
			assertNotNull(reloadedRoot.slots[i], "slot " + i + " lost");
			assertEquals((byte)SENTINEL, reloadedRoot.slots[i][0], "slot " + i + " has the wrong version");
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
