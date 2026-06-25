package test.eclipse.store.various.restart;

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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.eclipse.serializer.reference.Lazy;
import org.eclipse.store.storage.embedded.configuration.types.EmbeddedStorageConfigurationBuilder;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageFoundation;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.types.StorageWriteControllerReadOnlyMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Restart coverage for read-only storage. A shutdown/start cycle on a read-only
 * {@link EmbeddedStorageManager} must not write anything to the storage files — the startup
 * root reconciliation that would normally re-store the roots is suppressed when the storage is
 * not writable (commit {@code ac151127}). Data written before opening read-only must stay
 * readable across the restart.
 */
public class ReadOnlyRestartTest
{
	@Test
	public void readOnlyRestart_writesNothing_dataReadable(@TempDir final Path dir)
	{
		// phase 1 — write data with a normal writable manager
		final List<String> seed = new ArrayList<>();
		seed.add("alpha");
		seed.add("beta");
		try(final EmbeddedStorageManager writer = EmbeddedStorage.start(seed, dir))
		{
			writer.storeRoot();
		}

		final Map<String, Long> sizesBeforeReadOnly = fileSizes(dir);

		// phase 2 — open read-only and restart on the same instance
		final EmbeddedStorageFoundation<?> foundation = EmbeddedStorage.Foundation(dir);
		final StorageWriteControllerReadOnlyMode writeController =
			new StorageWriteControllerReadOnlyMode(foundation.getWriteController());
		writeController.setReadOnly(true);
		foundation.setWriteController(writeController);

		final EmbeddedStorageManager storage = foundation
			.setRoot(new ArrayList<String>())
			.createEmbeddedStorageManager()
			.start();

		final List<String> loaded = storage.root();
		assertNotNull(loaded);
		assertEquals(2, loaded.size(), "read-only must load the persisted root");
		assertEquals("alpha", loaded.get(0));
		assertEquals("beta", loaded.get(1));

		storage.shutdown();
		storage.start();

		final List<String> reloaded = storage.root();
		assertNotNull(reloaded);
		assertEquals(2, reloaded.size(), "data must still be readable after read-only restart");
		assertEquals("alpha", reloaded.get(0));
		assertEquals("beta", reloaded.get(1));

		storage.shutdown();

		final Map<String, Long> sizesAfterReadOnly = fileSizes(dir);
		assertEquals(sizesBeforeReadOnly, sizesAfterReadOnly,
			"read-only restart must not modify any storage file");
	}

	@Test
	public void restartWithoutStore_dataStable(@TempDir final Path dir)
	{
		final List<String> seed = new ArrayList<>();
		seed.add("x");
		seed.add("y");
		seed.add("z");

		final EmbeddedStorageManager storage = EmbeddedStorage.start(seed, dir);
		storage.storeRoot();
		storage.shutdown();

		// several restart cycles with no store in between — content must remain intact
		for(int i = 0; i < 5; i++)
		{
			storage.start();
			final List<String> reloaded = storage.root();
			assertEquals(3, reloaded.size(), "size after restart " + i);
			assertEquals("x", reloaded.get(0));
			assertEquals("z", reloaded.get(2));
			storage.shutdown();
		}
	}

	@Test
	public void readOnly_manyRestartCycles_neverWrites(@TempDir final Path dir)
	{
		final List<String> seed = new ArrayList<>();
		seed.add("a");
		seed.add("b");
		seed.add("c");
		try(final EmbeddedStorageManager writer = EmbeddedStorage.start(seed, dir))
		{
			writer.storeRoot();
		}

		final Map<String, Long> baseline = fileSizes(dir);

		final EmbeddedStorageManager storage = openReadOnly(EmbeddedStorage.Foundation(dir), new ArrayList<String>());
		assertEquals(baseline, fileSizes(dir), "read-only start must not write");

		for(int i = 0; i < 5; i++)
		{
			assertEquals(3, storage.<List<String>>root().size(), "content after read-only restart " + i);
			storage.shutdown();
			assertEquals(baseline, fileSizes(dir), "read-only must not write on cycle " + i);
			storage.start();
		}
		storage.shutdown();
		assertEquals(baseline, fileSizes(dir), "read-only must not write across the whole run");
	}

	@Test
	public void readOnlyRestart_lazyLoadsFromDisk(@TempDir final Path dir)
	{
		final List<Lazy<String>> seed = new ArrayList<>();
		for(int i = 0; i < 5; i++)
		{
			seed.add(Lazy.Reference("payload-" + i));
		}
		try(final EmbeddedStorageManager writer = EmbeddedStorage.start(seed, dir))
		{
			writer.store(seed);
		}

		final Map<String, Long> baseline = fileSizes(dir);

		final EmbeddedStorageManager storage = openReadOnly(EmbeddedStorage.Foundation(dir), new ArrayList<Lazy<String>>());
		storage.shutdown();
		storage.start();

		final List<Lazy<String>> reloaded = storage.root();
		assertEquals(5, reloaded.size());
		for(int i = 0; i < 5; i++)
		{
			final Lazy<String> lazy = reloaded.get(i);
			Lazy.clear(lazy);
			assertEquals("payload-" + i, lazy.get(),
				"read-only must lazy-load from disk after restart (i=" + i + ")");
		}
		storage.shutdown();
		assertEquals(baseline, fileSizes(dir), "lazy loads on read-only must not write");
	}

	@Test
	public void storeOnReadOnly_acrossRestart_rejectedAndWritesNothing(@TempDir final Path dir)
	{
		final List<String> seed = new ArrayList<>();
		seed.add("alpha");
		try(final EmbeddedStorageManager writer = EmbeddedStorage.start(seed, dir))
		{
			writer.storeRoot();
		}

		final Map<String, Long> baseline = fileSizes(dir);

		final EmbeddedStorageManager storage = openReadOnly(EmbeddedStorage.Foundation(dir), new ArrayList<String>());
		storage.shutdown();
		storage.start();

		final List<String> reloaded = storage.root();
		reloaded.add("beta");
		assertThrows(Exception.class, () -> storage.store(reloaded),
			"store on a read-only storage must be rejected");

		storage.shutdown();
		assertEquals(baseline, fileSizes(dir),
			"a rejected store on read-only must not write anything, even across a restart");
	}

	@Test
	public void readOnlyRestart_withBackup_writesNothing(@TempDir final Path dir, @TempDir final Path backup)
	{
		final List<String> seed = new ArrayList<>();
		seed.add("alpha");
		seed.add("beta");
		try(final EmbeddedStorageManager writer = EmbeddedStorageConfigurationBuilder.New()
			.setStorageDirectory(dir.toAbsolutePath().toString())
			.setBackupDirectory(backup.toAbsolutePath().toString())
			.createEmbeddedStorageFoundation()
			.createEmbeddedStorageManager(seed)
			.start())
		{
			writer.storeRoot();
		}

		final Map<String, Long> baselineData   = fileSizes(dir);
		final Map<String, Long> baselineBackup = fileSizes(backup);

		final EmbeddedStorageFoundation<?> foundation = EmbeddedStorageConfigurationBuilder.New()
			.setStorageDirectory(dir.toAbsolutePath().toString())
			.setBackupDirectory(backup.toAbsolutePath().toString())
			.createEmbeddedStorageFoundation();
		final EmbeddedStorageManager storage = openReadOnly(foundation, new ArrayList<String>());

		storage.shutdown();
		storage.start();

		final List<String> reloaded = storage.root();
		assertEquals(2, reloaded.size(), "data readable on read-only restart with backup");
		assertEquals("alpha", reloaded.get(0));
		storage.shutdown();

		assertEquals(baselineData, fileSizes(dir), "read-only restart must not write to the live directory");
		assertEquals(baselineBackup, fileSizes(backup), "read-only restart must not write to the backup directory");
	}

	private static EmbeddedStorageManager openReadOnly(final EmbeddedStorageFoundation<?> foundation, final Object root)
	{
		final StorageWriteControllerReadOnlyMode writeController =
			new StorageWriteControllerReadOnlyMode(foundation.getWriteController());
		writeController.setReadOnly(true);
		foundation.setWriteController(writeController);
		return foundation.setRoot(root).createEmbeddedStorageManager().start();
	}

	private static Map<String, Long> fileSizes(final Path directory)
	{
		final Map<String, Long> sizes = new HashMap<>();
		try(final Stream<Path> stream = Files.walk(directory))
		{
			stream.filter(Files::isRegularFile).forEach(p ->
			{
				try
				{
					sizes.put(directory.relativize(p).toString(), Files.size(p));
				}
				catch(final IOException e)
				{
					throw new RuntimeException(e);
				}
			});
		}
		catch(final IOException e)
		{
			throw new RuntimeException(e);
		}
		return sizes;
	}
}
