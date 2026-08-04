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
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import org.eclipse.serializer.afs.types.ADirectory;
import org.eclipse.serializer.afs.types.AFile;
import org.eclipse.serializer.collections.types.XEnum;
import org.eclipse.serializer.util.X;
import org.eclipse.store.afs.nio.types.NioFileSystem;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageFoundation;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.types.Storage;
import org.eclipse.store.storage.types.StorageConfiguration;
import org.eclipse.store.storage.types.StorageFileWriter;
import org.eclipse.store.storage.types.StorageLiveDataFile;
import org.eclipse.store.storage.types.StorageLiveFileProvider;
import org.eclipse.store.storage.types.StorageLiveTransactionsFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * A commit-phase failure during {@code importFiles} must not leave a silently torn import behind.
 * <p>
 * Injected failure: channel 1's transactions-log store entry write throws inside its import
 * commit. Depending on completion order, channel 0 may already have committed its part - the
 * torn-import live window. Expected with the escalation + torn-import deferral fix:
 * <ol>
 * <li>{@code importFiles} throws, with the injected cause in the chain;</li>
 * <li>the storage is DISRUPTED - further requests are refused, so no subsequent store can advance
 *     the restart consensus past the torn commit, and no housekeeping can dissolve the files the
 *     rollback needs (issuing a file check explicitly must fail too);</li>
 * <li>a restart undoes the import completely via the cross-channel consensus: the root is the
 *     pre-import state, and the storage is fully operational again.</li>
 * </ol>
 * Housekeeping is configured aggressively on purpose: without the fix, it may dissolve the
 * pre-import head between the failed commit and the restart, making the torn import unrecoverable
 * (rollback refusal) or leaving it half-applied.
 */
class TornImportCommitFailureEscalationTest
{
	private static final int CHANNELS = 2;

	@Test
	@Timeout(value = 180, unit = TimeUnit.SECONDS)
	void commitFailureMustDisruptAndRestartMustUndoTheImport(@TempDir final Path tempDir) throws Exception
	{
		final NioFileSystem fs         = NioFileSystem.New();
		final Path          storageDir = tempDir.resolve("storage");
		final Path          exportDir  = tempDir.resolve("export");

		// --- state v1, exported ---
		EmbeddedStorageManager storage = start(storageDir, null);
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

		// --- evolve to v2: this is the pre-import state a torn import must be undone to ---
		storage = start(storageDir, null);
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

		// --- import the v1 export; channel 1's commit entry write fails ---
		final InjectingWriterProvider injectingProvider = new InjectingWriterProvider();
		storage = start(storageDir, injectingProvider);
		try
		{
			final XEnum<AFile> importFiles = X.Enum();
			for(final Path p : listFiles(exportDir, ".dat"))
			{
				importFiles.add(fs.ensureFile(p));
			}

			final var connection = storage.createConnection();
			injectingProvider.armed.set(true);
			final Exception importFailure = assertThrows(
				Exception.class,
				() -> connection.importFiles(importFiles),
				"importFiles must fail when a channel's commit entry write fails"
			);
			injectingProvider.armed.set(false);
			assertTrue(
				causeChainContains(importFailure, InjectingWriterProvider.INJECTED_MESSAGE),
				"the injected failure must surface from importFiles: " + causeChain(importFailure)
			);

			// the commit-phase failure must have disrupted the storage: no further request may be
			// served, or a later store would seal the torn state permanently
			final EmbeddedStorageManager disrupted = storage;
			assertThrows(
				Exception.class,
				() -> disrupted.createConnection().issueFullFileCheck(),
				"a file check after a torn import commit must be refused (storage disrupted)"
			);
			assertThrows(
				Exception.class,
				disrupted::storeRoot,
				"a store after a torn import commit must be refused (storage disrupted)"
			);
		}
		finally
		{
			try
			{
				storage.shutdown();
			}
			catch(final Exception e)
			{
				// a disrupted storage may report its disruption once more on shutdown
				System.out.println("shutdown after disruption reported: " + e);
			}
		}

		// --- restart: the consensus must undo the torn import completely ---
		try
		{
			storage = start(storageDir, null);
		}
		catch(final Exception e)
		{
			fail("storage did not start after a torn import commit: " + causeChain(e));
			return;
		}
		try
		{
			@SuppressWarnings("unchecked")
			final ArrayList<String> root = (ArrayList<String>)storage.root();
			assertEquals(
				List.of("v2-a", "v2-b"),
				root,
				"a torn import must be undone completely, leaving the pre-import state"
			);

			// the storage must be fully operational again
			root.add("v3-after-recovery");
			storage.storeRoot();
		}
		finally
		{
			storage.shutdown();
		}
	}

	/**
	 * Housekeeping is deliberately aggressive (short interval, generous budget): the fix must hold
	 * even when housekeeping gets every chance to run between the failed commit and the restart.
	 */
	private static EmbeddedStorageManager start(
		final Path                       storageDir,
		final StorageFileWriter.Provider writerProvider
	)
	{
		final EmbeddedStorageFoundation<?> foundation = EmbeddedStorage.Foundation(
			StorageConfiguration.Builder()
				.setStorageFileProvider(Storage.FileProvider(storageDir))
				.setChannelCountProvider(Storage.ChannelCountProvider(CHANNELS))
				.setHousekeepingController(Storage.HousekeepingController(10, 100_000_000L))
				.createConfiguration()
		);
		if(writerProvider != null)
		{
			foundation.setWriterProvider(writerProvider);
		}
		return foundation.start(new ArrayList<String>());
	}

	/**
	 * Provides the default writer for every channel except channel 1, whose writer throws on the
	 * next transactions-log store entry write while {@link #armed}. During {@code importFiles},
	 * the only store entry written is the import commit's, so arming right before the call
	 * injects the failure precisely into channel 1's commit phase.
	 */
	private static final class InjectingWriterProvider implements StorageFileWriter.Provider
	{
		static final String INJECTED_MESSAGE = "injected commit-phase failure (channel 1 entry-store write)";

		final AtomicBoolean armed = new AtomicBoolean(false);

		@Override
		public StorageFileWriter provideWriter()
		{
			return new StorageFileWriter.Default();
		}

		@Override
		public StorageFileWriter provideWriter(final int channelIndex)
		{
			if(channelIndex != 1)
			{
				return new StorageFileWriter.Default();
			}
			return new StorageFileWriter()
			{
				@Override
				public long writeTransactionEntryStore(
					final StorageLiveTransactionsFile    transactionFile,
					final Iterable<? extends ByteBuffer> byteBuffers    ,
					final StorageLiveDataFile            dataFile       ,
					final long                           dataFileOffset ,
					final long                           storeLength
				)
				{
					if(InjectingWriterProvider.this.armed.get())
					{
						throw new RuntimeException(INJECTED_MESSAGE);
					}
					return StorageFileWriter.super.writeTransactionEntryStore(
						transactionFile, byteBuffers, dataFile, dataFileOffset, storeLength
					);
				}
			};
		}
	}

	private static boolean causeChainContains(final Throwable t, final String message)
	{
		for(Throwable c = t; c != null; c = c.getCause())
		{
			if(c.getMessage() != null && c.getMessage().contains(message))
			{
				return true;
			}
		}
		return false;
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
}
