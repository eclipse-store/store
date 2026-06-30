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

import org.eclipse.serializer.configuration.types.ByteSize;
import org.eclipse.serializer.configuration.types.ByteUnit;
import org.eclipse.store.storage.embedded.configuration.types.EmbeddedStorageConfigurationBuilder;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageFoundation;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.types.StorageIntegrityCheckResult;
import org.eclipse.store.storage.types.StorageIntegrityCheckResult.Anomaly;
import org.eclipse.store.storage.types.StorageIntegrityCheckResult.Finding;
import org.eclipse.store.storage.types.StorageWriteControllerReadOnlyMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
	// #4 -- bounded streaming verify for files larger than the integrity-check buffer cap
	///////////////////////////////////////////////////////////////////////////////////////

	// Mirrors StorageFileManager.INTEGRITY_VERIFY_BUFFER_LIMIT (package-private): files above this are verified
	// via the windowed streaming walk instead of being read whole.
	private static final int STREAMING_CAP = 8 * 1024 * 1024;

	/**
	 * A data file larger than the 8 MiB integrity-check buffer must be verified by the windowed streaming walk
	 * (StorageChunkChecksumCalculator.verifyDataFileStreaming) without reading the whole file resident. This
	 * stores a single ~20 MiB object, so its one covered chunk is larger than the window and is hashed
	 * incrementally across windows. Asserts the intact file verifies clean and that corrupting a byte deep past
	 * the first window is detected with the chunk's file offset (not its byte length). Run for both algorithms;
	 * sha256-chained additionally exercises the running tip across windows.
	 */
	@ParameterizedTest
	@ValueSource(strings = {"crc32c", "sha256-chained"})
	void largeSingleChunkIsStreamedVerifiedAndCorruptionDetected(final String algorithm, @TempDir final Path workDir)
		throws IOException
	{
		final Path storageDir = workDir.resolve("storage");

		final int    payloadSize = 20 * 1024 * 1024; // > 8 MiB window: one chunk spanning multiple windows
		final byte[] payload     = new byte[payloadSize];
		for(int i = 0; i < payloadSize; i++)
		{
			payload[i] = (byte)(i * 31 + 7);
		}

		try(final EmbeddedStorageManager mgr = largeFileConfig(storageDir, "observe", algorithm)
			.createEmbeddedStorageFoundation()
			.createEmbeddedStorageManager(payload)
			.start())
		{
			mgr.storeRoot();
		}

		final Path dataFile = largestDataFile(storageDir);
		final long fileSize = Files.size(dataFile);
		assertTrue(
			fileSize > STREAMING_CAP,
			"test must produce a data file larger than the streaming cap to exercise streaming; got " + fileSize
		);

		// intact: the streaming walk must report clean (no false anomalies across window boundaries)
		try(final EmbeddedStorageManager mgr = largeFileConfig(storageDir, "observe", algorithm)
			.createEmbeddedStorageFoundation()
			.createEmbeddedStorageManager()
			.start())
		{
			final StorageIntegrityCheckResult clean = mgr.issueFullIntegrityCheck();
			assertTrue(clean.isClean(), "intact large file must verify clean via streaming; got: " + clean);
		}

		// corrupt a byte at the file midpoint (well past the 8 MiB window -> a later window is exercised)
		final long corruptAt = fileSize / 2;
		flipByte(dataFile, corruptAt);

		final StorageIntegrityCheckResult result;
		try(final EmbeddedStorageManager mgr = largeFileConfig(storageDir, "observe", algorithm)
			.createEmbeddedStorageFoundation()
			.createEmbeddedStorageManager()
			.start())
		{
			result = mgr.issueFullIntegrityCheck();
		}

		assertFalse(result.isClean(), "corruption in a streamed large file must be detected");
		final Finding mismatch = firstOf(result, Anomaly.CHECKSUM_MISMATCH);
		assertNotNull(mismatch, "expected a CHECKSUM_MISMATCH finding, got: " + result);
		assertTrue(
			mismatch.position() >= 0L && mismatch.position() <= corruptAt,
			"mismatch position must be the chunk's file offset (<= corrupted byte " + corruptAt + "), got "
				+ mismatch.position()
		);
	}

	/**
	 * A large file made of several chunks (separate commits) exercises the streaming walk crossing window
	 * boundaries at real chunk boundaries and, for sha256-chained, carrying the running tip from one chunk to the
	 * next across windows. Asserts the intact file verifies clean, then that corruption in a later chunk is
	 * detected.
	 */
	@ParameterizedTest
	@ValueSource(strings = {"crc32c", "sha256-chained"})
	void largeMultiChunkFileIsStreamedVerified(final String algorithm, @TempDir final Path workDir)
		throws IOException
	{
		final Path storageDir = workDir.resolve("storage");

		final int chunkPayload = 6 * 1024 * 1024; // 6 MiB per commit
		final int commits      = 4;               // ~24 MiB across 4 chunks -> file > 8 MiB window

		final ArrayList<byte[]> root = new ArrayList<>();
		try(final EmbeddedStorageManager mgr = largeFileConfig(storageDir, "observe", algorithm)
			.createEmbeddedStorageFoundation()
			.createEmbeddedStorageManager(root)
			.start())
		{
			mgr.storeRoot();
			for(int c = 0; c < commits; c++)
			{
				final byte[] arr = new byte[chunkPayload];
				for(int i = 0; i < chunkPayload; i++)
				{
					arr[i] = (byte)(i + c);
				}
				// keep each array reachable from the root so housekeeping GC cannot dissolve its data file
				// between sessions; storing the updated root in its own commit yields one chunk + covering record
				// per array (so the file ends up with several chunks spread across the streaming windows).
				root.add(arr);
				mgr.store(root);
			}
		}

		final Path dataFile = largestDataFile(storageDir);
		assertTrue(
			Files.size(dataFile) > STREAMING_CAP,
			"expected a data file larger than the streaming cap, got " + Files.size(dataFile)
		);

		// many covering records spread across windows must verify clean (tip carried across windows for chained)
		try(final EmbeddedStorageManager mgr = largeFileConfig(storageDir, "observe", algorithm)
			.createEmbeddedStorageFoundation()
			.createEmbeddedStorageManager()
			.start())
		{
			final StorageIntegrityCheckResult clean = mgr.issueFullIntegrityCheck();
			assertTrue(clean.isClean(), "intact multi-chunk large file must verify clean via streaming; got: " + clean);
		}

		// corrupt a byte inside the last chunk (a later window) and assert detection
		final long corruptAt = Files.size(dataFile) - chunkPayload / 2;
		flipByte(dataFile, corruptAt);

		try(final EmbeddedStorageManager mgr = largeFileConfig(storageDir, "observe", algorithm)
			.createEmbeddedStorageFoundation()
			.createEmbeddedStorageManager()
			.start())
		{
			final StorageIntegrityCheckResult result = mgr.issueFullIntegrityCheck();
			assertFalse(result.isClean(), "corruption in a later chunk of a streamed file must be detected");
			assertNotNull(firstOf(result, Anomaly.CHECKSUM_MISMATCH), "expected a CHECKSUM_MISMATCH finding, got: " + result);
		}
	}

	///////////////////////////////////////////////////////////////////////////
	// helpers //
	////////////

	// Config with a large data-file maximum so a single growing head file exceeds the 8 MiB streaming cap.
	private static EmbeddedStorageConfigurationBuilder largeFileConfig(
		final Path   storageDir,
		final String profile   ,
		final String algorithm
	)
	{
		return EmbeddedStorageConfigurationBuilder.New()
			.setStorageDirectory(storageDir.toString())
			.setChannelCount(1)
			.setChunkChecksumProfile(profile)
			.setChunkChecksumAlgorithm(algorithm)
			.setDataFileMaximumSize(ByteSize.New(64, ByteUnit.MB));
	}

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
