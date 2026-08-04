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
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.eclipse.serializer.afs.types.ADirectory;
import org.eclipse.serializer.afs.types.AFile;
import org.eclipse.serializer.collections.types.XEnum;
import org.eclipse.serializer.util.X;
import org.eclipse.store.afs.nio.types.NioFileSystem;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.types.Storage;
import org.eclipse.store.storage.types.StorageConfiguration;
import org.eclipse.store.storage.types.StorageDataFileEvaluator;
import org.eclipse.store.storage.types.StorageLiveDataFile;
import org.eclipse.store.storage.types.StorageLiveFileProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Does the cross-channel restart consensus undo a TORN import?
 * <p>
 * Every channel logs its import commit as a store entry carrying the same task timestamp, so the
 * channels' timestamp sequences should remain prefixes of one global sequence and the consensus
 * (StorageChannelTaskInitialize#determineConsistentStoreTimestamp = the minimum of the channels'
 * latest timestamps) should roll every channel that is ahead back to the length its own log recorded
 * for that timestamp - which for the import's head file is its creation length.
 * <p>
 * Simulated by surgery: run a complete import, then restore ONE channel's files from a snapshot taken
 * before the import. That is the state a power loss between two channels' commits leaves behind - the
 * restored channel never logged the import, the other did.
 */
class TornImportConsensusTest
{
	private static final int CHANNELS = 2;

	/**
	 * The tear happens immediately after the import, before anything could dissolve the old head.
	 * That premise must be enforced, not hoped for: pinning the housekeeping interval alone does NOT
	 * prevent in-session dissolution, because the eager-rollover-durability flush armed by the
	 * import's own rollover carries an interval-independent maintenance pass that may dissolve the
	 * undersized pre-import head right after the (fully confirmed) import - legitimately, and racing
	 * the test's shutdown. The scenarios therefore disable dissolution outright.
	 */
	@Test
	@Timeout(value = 180, unit = TimeUnit.SECONDS)
	void tornImportMustBeUndoneOnRestart(@TempDir final Path tempDir) throws Exception
	{
		runTornImport(tempDir, "channel_1");
	}

	@Test
	@Timeout(value = 180, unit = TimeUnit.SECONDS)
	void tornImportMustBeUndoneOnRestartWhicheverChannelIsBehind(@TempDir final Path tempDir) throws Exception
	{
		runTornImport(tempDir, "channel_0");
	}

	private static void runTornImport(final Path tempDir, final String revertedChannel) throws Exception
	{
		final NioFileSystem fs         = NioFileSystem.New();
		final Path          storageDir = tempDir.resolve("storage");
		final Path          exportDir  = tempDir.resolve("export");
		final Path          snapshot   = tempDir.resolve("snapshot");

		// --- state v1, exported ---
		EmbeddedStorageManager storage = start(storageDir);
		try
		{
			@SuppressWarnings("unchecked")
			final ArrayList<String> root = (ArrayList<String>)storage.root();
			root.add("v1-a");
			root.add("v1-b");
			storage.storeRoot();

			final ADirectory aExportDir = fs.ensureDirectoryPath(exportDir.toFile().getAbsolutePath());
			storage.createConnection().exportChannels(StorageLiveFileProvider.New(aExportDir), true);
		}
		finally
		{
			storage.shutdown();
		}

		// --- evolve to v2, then snapshot: this is the pre-import on-disk state ---
		storage = start(storageDir);
		try
		{
			@SuppressWarnings("unchecked")
			final ArrayList<String> root = (ArrayList<String>)storage.root();
			root.clear();
			root.add("v2-a");
			root.add("v2-b");
			storage.storeRoot();
		}
		finally
		{
			storage.shutdown();
		}
		copyDir(storageDir, snapshot);

		// --- import the v1 export: reverts the root on every channel ---
		storage = start(storageDir);
		try
		{
			final XEnum<AFile> importFiles = X.Enum();
			for(final Path p : listFiles(exportDir, ".dat"))
			{
				importFiles.add(fs.ensureFile(p));
			}
			storage.createConnection().importFiles(importFiles);
		}
		finally
		{
			storage.shutdown();
		}

		/*
		 * Verify the import took effect on a COPY, so the storage under test is not restarted and
		 * housekeeping gets no chance to dissolve the pre-import head.
		 */
		final Path verifyCopy = tempDir.resolve("verify");
		copyDir(storageDir, verifyCopy);
		storage = start(verifyCopy);
		try
		{
			@SuppressWarnings("unchecked")
			final ArrayList<String> root = (ArrayList<String>)storage.root();
			assertEquals(List.of("v1-a", "v1-b"), root, "precondition: the import must have been committed");
		}
		finally
		{
			storage.shutdown();
		}

		// --- the tear: one channel never logged the import ---
		deleteDir(storageDir.resolve(revertedChannel));
		copyDir(snapshot.resolve(revertedChannel), storageDir.resolve(revertedChannel));
		System.out.println("reverted " + revertedChannel + " to its pre-import state");

		// --- restart: consensus should undo the import on the channel that is ahead ---
		try
		{
			storage = start(storageDir);
		}
		catch(final Exception e)
		{
			fail("storage did not start after a torn import: " + causeChain(e));
			return;
		}
		try
		{
			@SuppressWarnings("unchecked")
			final ArrayList<String> root = (ArrayList<String>)storage.root();
			System.out.println("root after restarting a torn import: " + root);
			assertEquals(
				List.of("v2-a", "v2-b"),
				root,
				"a torn import must be undone completely, leaving the pre-import state"
			);
		}
		finally
		{
			storage.shutdown();
		}
	}

	/**
	 * Housekeeping is pushed far out AND dissolution is disabled via the evaluator: the interval
	 * alone does not prevent it, because the eager-rollover-durability flush armed by the import's
	 * rollover runs an interval-independent maintenance pass that may dissolve the pre-import head
	 * right after the import (see the first test method's javadoc).
	 */
	private static EmbeddedStorageManager start(final Path storageDir)
	{
		return EmbeddedStorage.start(
			new ArrayList<String>(),
			StorageConfiguration.Builder()
				.setStorageFileProvider(Storage.FileProvider(storageDir))
				.setChannelCountProvider(Storage.ChannelCountProvider(CHANNELS))
				.setHousekeepingController(Storage.HousekeepingController(24 * 60 * 60 * 1000L, 1_000_000L))
				.setDataFileEvaluator(neverDissolvingEvaluator())
				.createConfiguration()
		);
	}

	/** Delegates everything to the default evaluator except dissolution, which it forbids. */
	private static StorageDataFileEvaluator neverDissolvingEvaluator()
	{
		final StorageDataFileEvaluator defaults = Storage.DataFileEvaluator();
		return new StorageDataFileEvaluator()
		{
			@Override
			public boolean needsDissolving(final StorageLiveDataFile storageFile)
			{
				return false;
			}

			@Override
			public boolean needsRetirement(final long fileTotalLength)
			{
				return defaults.needsRetirement(fileTotalLength);
			}

			@Override
			public int fileMinimumSize()
			{
				return defaults.fileMinimumSize();
			}

			@Override
			public int fileMaximumSize()
			{
				return defaults.fileMaximumSize();
			}

			@Override
			public int transactionFileMaximumSize()
			{
				return defaults.transactionFileMaximumSize();
			}

			@Override
			public long coalesceChunkTargetBytes()
			{
				return defaults.coalesceChunkTargetBytes();
			}
		};
	}

	private static String causeChain(final Throwable t)
	{
		final StringBuilder sb = new StringBuilder();
		for(Throwable c = t; c != null; c = c.getCause())
		{
			sb.append("\n  <- ").append(c);
		}
		return sb.toString();
	}

	private static List<Path> listFiles(final Path dir, final String suffix) throws IOException
	{
		try(Stream<Path> walk = Files.walk(dir))
		{
			return walk.filter(Files::isRegularFile)
				.filter(p -> p.getFileName().toString().endsWith(suffix))
				.toList()
			;
		}
	}

	private static void copyDir(final Path source, final Path target) throws IOException
	{
		Files.walkFileTree(source, new SimpleFileVisitor<Path>()
		{
			@Override
			public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs)
				throws IOException
			{
				Files.createDirectories(target.resolve(source.relativize(dir).toString()));
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs)
				throws IOException
			{
				Files.copy(file, target.resolve(source.relativize(file).toString()));
				return FileVisitResult.CONTINUE;
			}
		});
	}

	private static void deleteDir(final Path dir) throws IOException
	{
		Files.walkFileTree(dir, new SimpleFileVisitor<Path>()
		{
			@Override
			public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs)
				throws IOException
			{
				Files.delete(file);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(final Path d, final IOException e) throws IOException
			{
				Files.delete(d);
				return FileVisitResult.CONTINUE;
			}
		});
	}
}
