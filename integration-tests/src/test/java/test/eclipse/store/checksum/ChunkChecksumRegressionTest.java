package test.eclipse.store.checksum;

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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.eclipse.store.storage.embedded.configuration.types.EmbeddedStorageConfigurationBuilder;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageFoundation;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.types.StorageIntegrityCheckResult;
import org.eclipse.store.storage.types.StorageIntegrityCheckResult.Anomaly;
import org.eclipse.store.storage.types.StorageIntegrityCheckResult.Finding;
import org.eclipse.store.storage.types.StorageWriteControllerReadOnlyMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Regression tests for fixes made to the storage chunk-checksum feature (PR #717 review).
 * Each test pins a specific behavioral fix so a future change that reintroduces the bug fails here.
 */
class ChunkChecksumRegressionTest
{
	///////////////////////////////////////////////////////////////////////////
	// #1 -- checksum-mismatch offset is the chunk's FILE OFFSET, not its byte length
	/////////////////////////////////////////////////////////////////////////////

	/**
	 * A checksum mismatch must report the corrupted chunk's <em>file offset</em>, not its byte length.
	 * <p>
	 * The fixed bug passed {@code address - chunkStart} (the chunk's byte length) where every consumer
	 * treats the value as a file offset. We store a single large object (one covered chunk that starts a
	 * few dozen bytes into the file, right after the {@code FileHeaderV1}), corrupt a byte deep inside it,
	 * then assert the reported position is that small chunk-start offset &mdash; it must be at or before the
	 * corrupted byte, whereas the bug reported the chunk length (which exceeds the file midpoint).
	 */
	@Test
	void checksumMismatchReportsChunkFileOffsetNotByteLength(@TempDir final Path workDir) throws IOException
	{
		final Path storageDir = workDir.resolve("storage");

		final int    payloadSize = 200_000;
		final byte[] payload     = new byte[payloadSize];
		for(int i = 0; i < payloadSize; i++)
		{
			payload[i] = (byte)(i * 31 + 7);
		}

		// session 1: write with checksums emitting + verifying (observe = emit+verify, all anomalies LOG)
		try(final EmbeddedStorageManager mgr = checksumConfig(storageDir, "observe", "crc32c")
			.createEmbeddedStorageFoundation()
			.createEmbeddedStorageManager(payload)
			.start())
		{
			mgr.storeRoot();
		}

		// corrupt one byte deep inside the chunk (file midpoint is well past the header, inside the data)
		final Path dataFile  = largestDataFile(storageDir);
		final long fileSize  = Files.size(dataFile);
		final long corruptAt = fileSize / 2;
		flipByte(dataFile, corruptAt);

		// session 2: reopen and scan; observe logs the mismatch on load (no throw), the on-demand check collects it
		final StorageIntegrityCheckResult result;
		try(final EmbeddedStorageManager mgr = checksumConfig(storageDir, "observe", "crc32c")
			.createEmbeddedStorageFoundation()
			.createEmbeddedStorageManager()
			.start())
		{
			result = mgr.issueFullIntegrityCheck();
		}

		assertFalse(result.isClean(), "on-disk corruption of a covered chunk must be detected");

		final Finding mismatch = firstOf(result, Anomaly.CHECKSUM_MISMATCH);
		assertNotNull(mismatch, "expected a CHECKSUM_MISMATCH finding, got: " + result);

		assertTrue(mismatch.position() >= 0L, "offset must be non-negative, got " + mismatch.position());
		assertTrue(
			mismatch.position() <= corruptAt,
			"checksum-mismatch position must be the chunk's FILE OFFSET (<= the corrupted byte at "
				+ corruptAt + "), not its byte length; got " + mismatch.position()
				+ " (a value > " + corruptAt + " indicates the reverted bug that reported chunk length)"
		);
	}

	///////////////////////////////////////////////////////////////////////////
	// #5 -- read-only open with continuousCoverage must not write at startup
	/////////////////////////////////////////////////////////////////////////

	/**
	 * Opening an existing legacy (un-checksummed) store <em>read-only</em> with a {@code continuousCoverage}
	 * profile must not write at startup. The fixed bug unconditionally rolled the head file over (writing a
	 * {@code FileHeaderV1} + transaction-log entry) when the head was not covered, which throws under a
	 * disabled {@link StorageWriteControllerReadOnlyMode}. We assert the read-only open succeeds, the data is
	 * readable, and no new data file was written.
	 */
	@Test
	void readOnlyOpenWithContinuousCoverageOverLegacyStoreDoesNotWrite(@TempDir final Path workDir) throws IOException
	{
		final Path storageDir = workDir.resolve("storage");

		final ArrayList<String> root = new ArrayList<>(List.of("alpha", "beta", "gamma"));

		// session 1: legacy store -- no chunk-checksum property set, so the engine default (off) is used
		try(final EmbeddedStorageManager mgr = EmbeddedStorageConfigurationBuilder.New()
			.setStorageDirectory(storageDir.toString())
			.setChannelCount(1)
			.createEmbeddedStorageFoundation()
			.createEmbeddedStorageManager(root)
			.start())
		{
			mgr.storeRoot();
		}

		final Map<String, Long> dataFilesBefore = dataFileSizes(storageDir);

		// session 2: reopen READ-ONLY with strict-tolerate-legacy (continuousCoverage = true, legacy tolerated)
		final EmbeddedStorageFoundation<?> foundation =
			checksumConfig(storageDir, "strict-tolerate-legacy", "sha256-chained")
				.createEmbeddedStorageFoundation();
		final StorageWriteControllerReadOnlyMode readOnly =
			new StorageWriteControllerReadOnlyMode(foundation.getWriteController());
		readOnly.setReadOnly(true);
		foundation.setWriteController(readOnly);

		final Object loadedRoot;
		try(final EmbeddedStorageManager mgr = foundation.createEmbeddedStorageManager().start())
		{
			// reaching here at all is the core assertion: before the fix start() threw AfsExceptionReadOnly
			loadedRoot = mgr.root();
		}

		assertNotNull(loadedRoot, "root must be loadable after a read-only reopen");
		assertEquals(root, loadedRoot, "legacy data must be intact after a read-only reopen");
		assertEquals(
			dataFilesBefore, dataFileSizes(storageDir),
			"read-only open with continuousCoverage must not have written or rolled over a data file"
		);
	}

	///////////////////////////////////////////////////////////////////////////
	// helpers //
	////////////

	private static EmbeddedStorageConfigurationBuilder checksumConfig(
		final Path   storageDir,
		final String profile   ,
		final String algorithm
	)
	{
		return EmbeddedStorageConfigurationBuilder.New()
			.setStorageDirectory(storageDir.toString())
			.setChannelCount(1)
			.setChunkChecksumProfile(profile)
			.setChunkChecksumAlgorithm(algorithm);
	}

	private static Path largestDataFile(final Path storageDir) throws IOException
	{
		try(final Stream<Path> stream = Files.walk(storageDir))
		{
			return stream
				.filter(Files::isRegularFile)
				.filter(p -> p.getFileName().toString().endsWith(".dat"))
				.max(Comparator.comparingLong(ChunkChecksumRegressionTest::sizeOf))
				.orElseThrow(() -> new IllegalStateException("no .dat data file under " + storageDir));
		}
	}

	private static long sizeOf(final Path p)
	{
		try
		{
			return Files.size(p);
		}
		catch(final IOException e)
		{
			throw new RuntimeException(e);
		}
	}

	private static void flipByte(final Path file, final long index) throws IOException
	{
		final byte[] bytes = Files.readAllBytes(file);
		final int    i     = Math.toIntExact(index);
		bytes[i] = (byte)(bytes[i] ^ 0xFF);
		Files.write(file, bytes);
	}

	private static Map<String, Long> dataFileSizes(final Path storageDir) throws IOException
	{
		final Map<String, Long> result = new LinkedHashMap<>();
		try(final Stream<Path> stream = Files.walk(storageDir))
		{
			stream
				.filter(Files::isRegularFile)
				.filter(p -> p.getFileName().toString().endsWith(".dat"))
				.sorted(Comparator.comparing(Path::toString))
				.forEach(p -> result.put(storageDir.relativize(p).toString(), sizeOf(p)));
		}
		return result;
	}

	private static Finding firstOf(final StorageIntegrityCheckResult result, final Anomaly anomaly)
	{
		for(final Finding f : result.anomalies())
		{
			if(f.anomaly() == anomaly)
			{
				return f;
			}
		}
		return null;
	}
}
