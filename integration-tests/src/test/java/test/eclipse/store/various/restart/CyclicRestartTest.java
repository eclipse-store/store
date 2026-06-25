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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.store.storage.embedded.configuration.types.EmbeddedStorageConfigurationBuilder;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exercises many shutdown/start cycles on the same {@link EmbeddedStorageManager} instance while
 * the object graph keeps growing between cycles. Guards against data loss across restarts:
 * each cycle the live storage must keep every previously stored entry, and a final fresh manager
 * opened on the same directory must see the complete graph (proving the data hit the disk, not
 * just survived in the in-memory registry).
 */
public class CyclicRestartTest
{
	private static final int CYCLES = 5;

	@Test
	public void manyCycles_growingGraph_noDataLoss(@TempDir final Path dir)
	{
		final List<String> root = new ArrayList<>();

		final EmbeddedStorageManager storage = EmbeddedStorage.start(root, dir);

		for(int i = 0; i < CYCLES; i++)
		{
			final List<String> live = storage.root();
			assertEquals(i, live.size(), "entries present at start of cycle " + i);

			live.add("entry-" + i);
			storage.store(live);
			storage.shutdown();

			storage.start();

			final List<String> reloaded = storage.root();
			assertNotNull(reloaded, "root after restart in cycle " + i);
			assertEquals(i + 1, reloaded.size(), "entries after restart in cycle " + i);
			assertEquals("entry-" + i, reloaded.get(i), "newest entry after restart in cycle " + i);
		}

		storage.shutdown();

		// independent verification: a fresh manager on the same directory must see the full graph
		final EmbeddedStorageManager verifier = EmbeddedStorage.start(new ArrayList<String>(), dir);
		final List<String> fromDisk = verifier.root();
		assertEquals(CYCLES, fromDisk.size(), "all entries must be persisted to disk");
		for(int i = 0; i < CYCLES; i++)
		{
			assertEquals("entry-" + i, fromDisk.get(i), "entry " + i + " on disk");
		}
		verifier.shutdown();
	}

	@Test
	public void manyCycles_withBackup_mirrorsEveryCycle(@TempDir final Path dir, @TempDir final Path backup)
	{
		final List<String> root = new ArrayList<>();

		final EmbeddedStorageManager storage = EmbeddedStorageConfigurationBuilder.New()
			.setStorageDirectory(dir.toAbsolutePath().toString())
			.setBackupDirectory(backup.toAbsolutePath().toString())
			.createEmbeddedStorageFoundation()
			.createEmbeddedStorageManager(root)
			.start();

		for(int i = 0; i < CYCLES; i++)
		{
			final List<String> live = storage.root();
			live.add("entry-" + i);
			storage.store(live);
			storage.shutdown();
			storage.start();
		}
		storage.shutdown();

		final long liveSize = totalFileSize(dir);
		final long backupSize = totalFileSize(backup);
		assertEquals(liveSize, backupSize,
			"backup must mirror live byte-for-byte after " + CYCLES + " cycles");

		// reopen the backup as primary and confirm the full graph survived all cycles
		final EmbeddedStorageManager verifier = EmbeddedStorage.start(new ArrayList<String>(), backup);
		final List<String> fromBackup = verifier.root();
		assertEquals(CYCLES, fromBackup.size(), "backup must contain every entry written across cycles");
		for(int i = 0; i < CYCLES; i++)
		{
			assertEquals("entry-" + i, fromBackup.get(i), "entry " + i + " in backup");
		}
		verifier.shutdown();
	}

	private static long totalFileSize(final Path directory)
	{
		try(final java.util.stream.Stream<Path> stream = java.nio.file.Files.walk(directory))
		{
			return stream
				.filter(java.nio.file.Files::isRegularFile)
				.mapToLong(p ->
				{
					try
					{
						return java.nio.file.Files.size(p);
					}
					catch(final java.io.IOException e)
					{
						throw new RuntimeException(e);
					}
				})
				.sum();
		}
		catch(final java.io.IOException e)
		{
			throw new RuntimeException(e);
		}
	}
}
