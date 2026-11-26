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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.eclipse.serializer.afs.types.AFile;
import org.eclipse.serializer.collections.types.XEnum;
import org.eclipse.serializer.util.X;
import org.eclipse.store.afs.nio.types.NioFileSystem;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.types.Storage;
import org.eclipse.store.storage.types.StorageConfiguration;
import org.eclipse.store.storage.types.StorageLiveFileProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * The warning about an import that replaced already existing entities is logged by its own logger,
 * {@code org.eclipse.store.storage.types.StorageEntityCache.ImportConflicts}, so that it can be switched
 * off without silencing the other messages of the entity cache.
 * <p>
 * The test captures {@code System.err}, where the test logging (slf4j-simple, default level warn) writes
 * with the short logger name.
 */
class ImportConflictLoggerTest
{
	private static final Pattern DEDICATED_LOGGER_WARNING = Pattern.compile(
		"WARN ImportConflicts - Channel \\d+: import replaced \\d+ already existing entities"
	);

	/** The same warning under the entity cache's own logger name, as before the dedicated logger. */
	private static final Pattern STANDARD_LOGGER_WARNING = Pattern.compile(
		"StorageEntityCache - Channel \\d+: import replaced"
	);

	@Test
	@Timeout(value = 120, unit = TimeUnit.SECONDS)
	void replacedEntitiesAreReportedByTheDedicatedLogger(@TempDir final Path tempDir) throws Exception
	{
		final NioFileSystem fs        = NioFileSystem.New();
		final Path          exportDir = tempDir.resolve("export");

		try(EmbeddedStorageManager storage = start(tempDir.resolve("storage")))
		{
			root(storage).addAll(List.of("v1-a", "v1-b"));
			storage.storeRoot();
			storage.createConnection().exportChannels(
				StorageLiveFileProvider.New(fs.ensureDirectoryPath(exportDir.toFile().getAbsolutePath())), true
			);

			final ArrayList<String> root = root(storage);
			root.clear();
			root.addAll(List.of("v2-a", "v2-b"));
			storage.storeRoot();

			final XEnum<AFile> files = X.Enum();
			for(final Path p : listFiles(exportDir, ".dat"))
			{
				files.add(fs.ensureFile(p));
			}

			final String log = captureErr(() -> storage.createConnection().importFiles(files));

			assertTrue(
				DEDICATED_LOGGER_WARNING.matcher(log).find(),
				"expected the warning from the dedicated logger, log was:\n" + log
			);
			assertFalse(
				STANDARD_LOGGER_WARNING.matcher(log).find(),
				"the warning must not be logged by the entity cache's own logger, log was:\n" + log
			);
		}

		// the import still wins; checked in storage, as loaded instances keep their heap state
		try(EmbeddedStorageManager copy = start(copyOf(tempDir.resolve("storage"), tempDir.resolve("copy"))))
		{
			assertEquals(List.of("v1-a", "v1-b"), root(copy), "the import must have replaced the root");
		}
	}

	private static Path copyOf(final Path source, final Path target) throws IOException
	{
		try(Stream<Path> walk = Files.walk(source))
		{
			for(final Path p : walk.toList())
			{
				final Path t = target.resolve(source.relativize(p).toString());
				if(Files.isDirectory(p))
				{
					Files.createDirectories(t);
				}
				else
				{
					Files.copy(p, t);
				}
			}
		}
		return target;
	}

	@SuppressWarnings("unchecked")
	private static ArrayList<String> root(final EmbeddedStorageManager storage)
	{
		return (ArrayList<String>)storage.root();
	}

	private static EmbeddedStorageManager start(final Path storageDir)
	{
		return EmbeddedStorage.start(
			new ArrayList<String>(),
			StorageConfiguration.Builder()
				.setStorageFileProvider(Storage.FileProvider(storageDir))
				.setChannelCountProvider(Storage.ChannelCountProvider(2))
				.createConfiguration()
		);
	}

	private static String captureErr(final Runnable action)
	{
		final PrintStream           original = System.err;
		final ByteArrayOutputStream bytes    = new ByteArrayOutputStream();
		try(PrintStream capture = new PrintStream(bytes, true, StandardCharsets.UTF_8))
		{
			System.setErr(capture);
			action.run();
		}
		finally
		{
			System.setErr(original);
		}
		return bytes.toString(StandardCharsets.UTF_8);
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
