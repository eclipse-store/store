package org.eclipse.store.storage.types;

/*-
 * #%L
 * EclipseStore Storage
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

import static org.eclipse.serializer.util.X.checkArrayRange;
import static org.eclipse.serializer.util.X.notNull;
import static org.eclipse.serializer.util.X.toBytes;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Map;
import java.util.zip.CRC32C;

import org.eclipse.serializer.exceptions.WrapperRuntimeException;
import org.eclipse.serializer.memory.XMemory;
import org.eclipse.serializer.persistence.binary.types.Binary;
import org.eclipse.store.storage.exceptions.StorageExceptionConsistency;
import org.eclipse.store.storage.types.StorageMetaRecord.FileHeaderV1Payload;

/**
 * Per-channel component owning all chunk-checksum work: writing the {@code FileHeaderV1} and chunk-checksum
 * meta records, and recognizing / verifying them on load. One instance is created per storage channel by
 * {@link StorageChunkChecksumProvider#createCalculator()}, so the stateful native algorithm objects
 * ({@link MessageDigest}, {@link CRC32C}) live as plain instance fields without thread-safety concerns &mdash;
 * each channel calls its own calculator from a single thread.
 * <p>
 * The pluggable extension point is {@link Algorithm}: each kind's primitives (record layout, write,
 * compute, read-stored) live in one {@code Algorithm} subclass. The calculator carries a primary
 * algorithm (the configured kind, used for writes) and a kind-keyed registry of all known algorithms
 * (used for verify-time dispatch by the on-disk KIND, so mixed-kind files from a provider change between
 * runs verify cleanly). Adding a new algorithm in the future is a localized change: a new {@code Algorithm}
 * subclass plus a new {@link StorageChunkChecksumProvider} subclass that registers it.
 */
public interface StorageChunkChecksumCalculator
{
	/**
	 * @return the {@link StorageChunkChecksumPolicy} governing the mode and strictness knobs.
	 */
	public StorageChunkChecksumPolicy policy();

	/**
	 * @return the KIND code of the primary algorithm (the one that will be used for writes).
	 */
	public long chunkChecksumKind();

	/**
	 * @return the byte length of the {@code FileHeaderV1} meta record this calculator emits.
	 */
	public long fileHeaderRecordLength();

	/**
	 * @return the byte length of the chunk-checksum meta record the primary algorithm emits.
	 */
	public long chunkChecksumRecordLength();

	/**
	 * @return whether the policy mode emits meta records on write.
	 */
	public boolean isWriting();

	/**
	 * @return whether the policy mode verifies chunk-checksum records on load.
	 */
	public boolean isVerifying();

	/**
	 * Whether a chunk-checksum record should cover writes appended to the given head file: the policy
	 * mode must write AND the head file must already carry a {@code FileHeaderV1} of this calculator's
	 * primary kind. Legacy/pre-feature head files (kind {@code 0}) and head files written by a different
	 * algorithm are gated out (a file is single-kind).
	 */
	public boolean coversChunkWrites(StorageLiveDataFile.Default headFile);

	/**
	 * Convenience: {@link #chunkChecksumRecordLength()} when {@link #coversChunkWrites(StorageLiveDataFile.Default)}
	 * is true for the given head file, {@code 0L} otherwise. The bytes the trailing chunk-checksum
	 * record reserves; every write path uses this to keep raw file size within {@code fileMaximumSize}.
	 */
	public default long reservedLengthFor(final StorageLiveDataFile.Default headFile)
	{
		return this.coversChunkWrites(headFile) ? this.chunkChecksumRecordLength() : 0L;
	}

	/**
	 * Writes a {@code FileHeaderV1} meta record into {@code target} at its current position (advances by
	 * {@link #fileHeaderRecordLength()}), stamping {@code chainRoot} as the file's chain seed. Target must
	 * be a direct buffer in native byte order with at least {@link #fileHeaderRecordLength()} bytes
	 * remaining. {@code chainRoot} is {@link #LENGTH_HASH_SHA256} bytes — all-zero for non-chained kinds,
	 * the predecessor file's chain tip (or the configured initial seed) for chained kinds; see
	 * {@link #nextChainRoot(StorageLiveDataFile.Default)}.
	 */
	public void writeFileHeaderRecord(ByteBuffer target, byte[] chainRoot);

	/**
	 * Determines the {@code chainRoot} to stamp into the {@code FileHeaderV1} of a newly created file. For
	 * chained kinds this is {@code predecessor}'s current chain tip (or the configured initial seed when
	 * {@code predecessor} is {@code null} or carries no tip — i.e. the first file). For non-chained kinds
	 * it is an all-zero array (the chain root is unused).
	 *
	 * @param predecessor the file that was the head before this new file, or {@code null} for the first file.
	 * @return the {@link StorageMetaRecord#LENGTH_HASH_SHA256}-byte chain root for the new file's header.
	 */
	public byte[] nextChainRoot(StorageLiveDataFile.Default predecessor);

	/**
	 * Caches on {@code file} the per-file header state that a re-load would parse from the
	 * {@code FileHeaderV1} just written by {@link #writeFileHeaderRecord(ByteBuffer, byte[])} (kind and the
	 * given {@code chainRoot}, which also seeds the file's running chain tip).
	 */
	public void cacheFileHeaderOn(StorageLiveDataFile.Default file, byte[] chainRoot);

	/**
	 * Computes the primary algorithm's checksum over {@code dataBuffers} (each from its current position
	 * to its limit, without advancing them) and writes the covering chunk-checksum meta record into
	 * {@code target} at its current position (advances by {@link #chunkChecksumRecordLength()}). The record
	 * stores {@code chunkStartOffset} &mdash; the in-file offset where the covered chunk begins &mdash; so the
	 * load walk can detect framing desync. For a chained primary kind the previous tip is read from
	 * {@code file} and the newly computed hash becomes the calculator's pending tip; {@code file} is <b>not</b>
	 * modified until {@link #commitChunkWrite} is called. For non-chained kinds {@code file} is not modified at all.
	 *
	 * @param file             the head file the chunk is being appended to.
	 * @param chunkStartOffset the in-file offset where the covered chunk's bytes begin.
	 * @param target           the meta-record buffer to write into.
	 * @param dataBuffers      the chunk's data buffers (hashed, not advanced).
	 */
	public void writeChunkChecksumRecord(StorageLiveDataFile.Default file, long chunkStartOffset, ByteBuffer target, ByteBuffer... dataBuffers);

	/**
	 * Applies the per-file state advanced by the preceding {@link #writeChunkChecksumRecord} once that write
	 * has been committed &mdash; currently advancing the chained tip on {@code file} to the newly written hash.
	 * No-op for non-chained kinds. A chunk write that is never committed (because it failed, or was rolled back
	 * after a sibling channel failed) therefore never advances the file tip.
	 *
	 * @param file the head file whose just-committed chunk write should advance the per-file state.
	 */
	public void commitChunkWrite(StorageLiveDataFile.Default file);

	/**
	 * Registers this calculator's load-time handlers into the channel's {@link StorageMetaRecordRegistry}:
	 * the shared {@code FileHeaderV1} handler under {@link StorageMetaRecord#KIND_FileHeaderV1} (parses and
	 * caches the per-file header), and a single chunk-checksum handler under every chunk-checksum algorithm
	 * KIND this calculator knows (verifies a chunk-checksum record when {@link #isVerifying()}, dispatching
	 * the {@link Algorithm} by the record's on-disk KIND, so a file written by one algorithm verifies even
	 * when another is the configured primary). Unknown KINDs are reported via {@code reporter}.
	 * <p>
	 * Called once per channel during setup (in the {@code StorageSystem.createChannels()} flow), so each
	 * channel's registry references that channel's own stateful {@link Algorithm} instances.
	 *
	 * @param registry the channel's registry to register into; must be non-{@code null}.
	 * @param reporter the channel's anomaly reporter, used by the chunk handler to react to a mismatch;
	 *                 must be non-{@code null}.
	 */
	public void registerMetaRecordHandlers(StorageMetaRecordRegistry registry, StorageChecksumAnomalyReporter reporter);

	/**
	 * Re-verifies an already-resident data file's chunk-checksum records for the on-demand integrity check,
	 * reporting anomalies through {@code reporter}. <b>Side-effect-free</b>: it reconstructs the per-file header
	 * kind, chain tip and chunk boundary in local variables and never mutates {@code file} (its cached header
	 * state and running chain tip belong to the live write path), but otherwise mirrors the load-time
	 * {@link ChunkChecksumHandler} and per-file coverage checks, so the anomalies match a startup load walk.
	 * {@code buffer} must already hold the bytes to verify in {@code [0, limit)} (the caller bounds the head
	 * file to its committed length).
	 *
	 * @param file     the data file being re-verified (read only; never mutated).
	 * @param buffer   the direct buffer holding the file's resident bytes in {@code [0, limit)}.
	 * @param reporter the sink for detected anomalies; must be non-{@code null}.
	 */
	public void verifyDataFile(StorageLiveDataFile.Default file, ByteBuffer buffer, StorageChecksumAnomalyReporter reporter);

	/**
	 * Streaming variant of {@link #verifyDataFile} for a data file too large to hold resident: reads the file in
	 * windows of {@code window}'s capacity rather than requiring the whole file in one buffer, bounding the
	 * integrity check's direct-memory use to one window per channel regardless of the configured data-file size.
	 * Produces exactly the same anomalies as {@link #verifyDataFile} (and the load walk): each covering record's
	 * chunk hash is recomputed incrementally over its bytes via {@link Algorithm#beginChunk(byte[])} /
	 * {@link Algorithm#updateChunk(ByteBuffer)} / {@link Algorithm#finishChunk()}, so a single chunk larger than
	 * the window still verifies. <b>Side-effect-free</b>: never mutates {@code file}.
	 *
	 * @param file         the data file being re-verified (read only; never mutated).
	 * @param verifyLength the number of bytes to verify from the file start (the caller bounds the head file).
	 * @param window       a reusable direct buffer used as the sliding read window; its capacity is the window size.
	 * @param reporter     the sink for detected anomalies; must be non-{@code null}.
	 */
	public void verifyDataFileStreaming(StorageLiveDataFile.Default file, long verifyLength, ByteBuffer window, StorageChecksumAnomalyReporter reporter);



	///////////////////////////////////////////////////////////////////////////
	// load-time meta-record handlers //
	///////////////////////////////////

	/**
	 * Handles the shared {@code FileHeaderV1} kind: parses the per-file header and caches it on the file.
	 * Always caches regardless of verify mode — the write side gates on the file's cached
	 * {@code chunkChecksumKind}, so the cache must reflect what is on disk independent of policy.
	 */
	public final class FileHeaderV1Handler implements StorageMetaRecordHandler
	{
		private final StorageChecksumAnomalyReporter reporter;

		FileHeaderV1Handler(final StorageChecksumAnomalyReporter reporter)
		{
			super();
			this.reporter = reporter;
		}

		@Override
		public final long onLoad(
			final StorageLiveDataFile.Default file      ,
			final ByteBuffer                  buffer    ,
			final long                        address   ,
			final long                        chunkStart
		)
		{
			// Hostile/corrupt-input guard: the full 46 B header must be resident before we read its
			// payload (a truncated header would read past the buffer). Report-and-skip on overrun.
			final long bufferBoundAddress = XMemory.getDirectByteBufferAddress(buffer) + buffer.limit();
			if(address + StorageMetaRecord.LENGTH_FILEHEADERV1 > bufferBoundAddress)
			{
				this.reporter.onUnknownKind(file.number(), StorageMetaRecord.KIND_FileHeaderV1);
				return chunkStart;
			}

			// By format there is exactly one FileHeaderV1 per file, its first entry. A second/misplaced one
			// (non-zero cached kind ⇒ a header was already parsed) is anomalous: do NOT let it silently
			// re-seed the file's chunk-checksum kind / chain tip (which would rebase verification of every
			// following chunk). Report it and skip past, accounting its bytes as meta.
			if(file.chunkChecksumKind() != 0L)
			{
				this.reporter.onUnknownKind(file.number(), StorageMetaRecord.KIND_FileHeaderV1);
				file.registerMetaLength(StorageMetaRecord.LENGTH_FILEHEADERV1);
				return address + StorageMetaRecord.LENGTH_FILEHEADERV1;
			}

			final FileHeaderV1Payload header = StorageMetaRecord.readFileHeaderV1(address);
			file.setFileHeaderV1(header.chunkChecksumKind(), header.chainRoot());
			// Account the header's bytes as meta (not gap) so dataFillRatio treats it as useful overhead.
			file.registerMetaLength(StorageMetaRecord.LENGTH_FILEHEADERV1);
			return address + StorageMetaRecord.LENGTH_FILEHEADERV1;
		}
	}



	/**
	 * Handles every chunk-checksum kind: when verifying, re-checksums the preceding chunk in place and
	 * compares against the stored checksum, dispatching the {@link Algorithm} by the record's on-disk
	 * KIND (so a file of one kind verifies even when another is the configured primary). Normally registered
	 * under exactly the KINDs present in {@code algorithmsByKind}; a lookup miss (registry/map drift, or a
	 * corrupt on-disk KIND that slipped the marker gate) is reported as an unknown-KIND anomaly rather than
	 * trusted.
	 */
	public final class ChunkChecksumHandler implements StorageMetaRecordHandler
	{
		private final StorageChunkChecksumPolicy       policy          ;
		private final Map<Long, Algorithm>             algorithmsByKind;
		private final StorageChecksumAnomalyReporter   reporter        ;

		ChunkChecksumHandler(
			final StorageChunkChecksumPolicy       policy          ,
			final Map<Long, Algorithm>             algorithmsByKind,
			final StorageChecksumAnomalyReporter   reporter
		)
		{
			super();
			this.policy           = policy          ;
			this.algorithmsByKind = algorithmsByKind;
			this.reporter         = reporter        ;
		}

		@Override
		public final long onLoad(
			final StorageLiveDataFile.Default file      ,
			final ByteBuffer                  buffer    ,
			final long                        address   ,
			final long                        chunkStart
		)
		{
			final long      kind     = StorageMetaRecord.kindOf(address);
			final Algorithm verifier = this.algorithmsByKind.get(kind);
			if(verifier == null)
			{
				// This handler is registered for a KIND with no algorithm (registry/map drift) or the
				// on-disk KIND is corrupt: cannot verify — report and skip (chunk boundary unchanged).
				this.reporter.onUnknownKind(file.number(), kind);
				return chunkStart;
			}

			// Hostile/corrupt-input guard: the full fixed-size record must be resident before we read the
			// stored checksum (a truncated record would read past the buffer). Report-and-skip on overrun.
			final long bufferBoundAddress = XMemory.getDirectByteBufferAddress(buffer) + buffer.limit();
			if(address + verifier.recordLength() > bufferBoundAddress)
			{
				this.reporter.onUnknownKind(file.number(), kind);
				return chunkStart;
			}

			final boolean chained = verifier.isChained();
			if(this.policy.verify())
			{
				// shared verify core; on a chained kind the per-file running tip is the seed in and the tip out.
				final byte[] tip = verifyChunk(
					file.number(), buffer, address, chunkStart, verifier,
					chained ? file.chainTip() : null, this.reporter
				);
				if(chained)
				{
					file.setChainTip(tip);
				}
			}
			else if(chained)
			{
				// Not verifying, but a chained file must still advance its per-file tip during the load
				// walk so that writes appended to this (head) file after init continue the chain. The tip
				// after chunk i is simply that chunk's stored hash — read it, no recompute needed.
				file.setChainTip(verifier.readStoredChecksumBytes(address));
			}
			// Account the chunk-checksum record's bytes as meta (not gap).
			file.registerMetaLength(verifier.recordLength());
			return address + verifier.recordLength();
		}
	}



	/**
	 * Shared framing cross-check (file-relative offsets), used by both the resident {@link #verifyChunk} and the
	 * streaming {@link Default#verifyDataFileStreaming} so the two cannot diverge: a covering record is consistent
	 * with the walk when its stored chunk start equals the walker's reconstructed chunk start and the record does
	 * not precede that start (a corrupted entity LENGTH otherwise leaves the stored start != cursor, or drives the
	 * chunk start past the record). When inconsistent, {@code [chunkStart, record)} is wrong and must not be hashed.
	 */
	private static boolean isChunkFramingConsistent(
		final int  storedChunkStart,
		final long walkerChunkStart,
		final long recordOffset
	)
	{
		return storedChunkStart == walkerChunkStart && recordOffset >= walkerChunkStart;
	}


	/**
	 * Shared, side-effect-free verification of one covering record's chunk {@code [chunkStart, address)} against
	 * the stored checksum, used by both the load-time {@link ChunkChecksumHandler} and the on-demand
	 * {@link Default#verifyDataFile} so the two cannot diverge. Touches no {@link StorageLiveDataFile}:
	 * {@code inboundTip} is the chained running tip in, the return value the tip to carry forward (stored tip on
	 * a chained match or boundary re-anchor; {@code inboundTip} unchanged for non-chained). The caller has
	 * confirmed the record is fully resident in {@code buffer}.
	 */
	private static byte[] verifyChunk(
		final long                           fileNumber ,
		final ByteBuffer                     buffer     ,
		final long                           address    ,
		final long                           chunkStart ,
		final Algorithm                      verifier   ,
		final byte[]                         inboundTip ,
		final StorageChecksumAnomalyReporter reporter
	)
	{
		final boolean chained            = verifier.isChained();
		final long    bufferStartAddress = XMemory.getDirectByteBufferAddress(buffer);

		// Framing cross-check: the record's stored chunk start vs the boundary the walk reconstructed from entity
		// LENGTH prefixes. A corrupted LENGTH leaves the stored start != cursor, or drives chunkStart past the
		// record (negative chunk length). Either way [chunkStart, address) is wrong: report and re-anchor rather
		// than hash it (and never slice a negative length).
		final int  storedChunkStart = StorageMetaRecord.readChunkChecksumChunkStart(address);
		final long walkerChunkStart = chunkStart - bufferStartAddress;
		final long recordOffset     = address - bufferStartAddress;
		if(!isChunkFramingConsistent(storedChunkStart, walkerChunkStart, recordOffset))
		{
			reporter.onChunkBoundaryMismatch(fileNumber, recordOffset, storedChunkStart, walkerChunkStart);
			return chained ? verifier.readStoredChecksumBytes(address) : inboundTip;
		}

		// Checksum the chunk in place: it is already resident in `buffer`, so slice that view rather than copying
		// [chunkStart, address) into a fresh array. Dispatch by on-disk KIND, not the primary.
		final ByteBuffer chunk    = XMemory.slice(buffer, walkerChunkStart, address - chunkStart);
		final byte[]     expected = verifier.readStoredChecksumBytes(address);
		// chained kinds fold the running tip (seeded from the header chainRoot, advanced per verified chunk) into
		// the recompute, so reorder/insert/delete/substitute breaks here.
		if(chained)
		{
			byte[] seed = inboundTip;
			if(seed == null)
			{
				// Chained covering record but no parsed FileHeaderV1: the engine never writes one to a header-less
				// file, so the header was lost/corrupted. Report it and verify against the initial seed so a real
				// defect still surfaces as a checksum mismatch.
				reporter.onMissingHeader(fileNumber);
				seed = verifier.initialSeed();
			}
			verifier.setChainTip(seed);
		}
		final byte[] actual = verifier.computeChecksumBytes(chunk);
		if(!Arrays.equals(actual, expected))
		{
			reporter.onChecksumMismatch(fileNumber, walkerChunkStart, StorageMetaRecord.kindOf(address), expected, actual);
		}
		// chained: continue from the stored tip (== recomputed on match), the basis the writer chained from.
		return chained ? expected : inboundTip;
	}



	/**
	 * Algorithm-agnostic implementation: holds the policy, a primary {@link Algorithm} (for writes), and
	 * a registry of all known algorithms keyed by KIND (for verify-time dispatch).
	 */
	public final class Default implements StorageChunkChecksumCalculator
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////

		private final StorageChunkChecksumPolicy policy          ;
		private final Algorithm                  primaryAlgorithm;
		private final Map<Long, Algorithm>       algorithmsByKind;



		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		Default(
			final StorageChunkChecksumPolicy policy          ,
			final Algorithm                  primaryAlgorithm,
			final Map<Long, Algorithm>       algorithmsByKind
		)
		{
			super();
			this.policy           = notNull(policy)          ;
			this.primaryAlgorithm = notNull(primaryAlgorithm);
			this.algorithmsByKind = notNull(algorithmsByKind);
		}



		///////////////////////////////////////////////////////////////////////////
		// override methods //
		/////////////////////

		@Override
		public final StorageChunkChecksumPolicy policy()
		{
			return this.policy;
		}

		@Override
		public final long chunkChecksumKind()
		{
			return this.primaryAlgorithm.kind();
		}

		@Override
		public final long fileHeaderRecordLength()
		{
			return StorageMetaRecord.LENGTH_FILEHEADERV1;
		}

		@Override
		public final long chunkChecksumRecordLength()
		{
			return this.primaryAlgorithm.recordLength();
		}

		@Override
		public final boolean isWriting()
		{
			return this.policy.emit();
		}

		@Override
		public final boolean isVerifying()
		{
			return this.policy.verify();
		}

		@Override
		public final boolean coversChunkWrites(final StorageLiveDataFile.Default headFile)
		{
			return this.isWriting() && headFile.chunkChecksumKind() == this.primaryAlgorithm.kind();
		}

		@Override
		public final void writeFileHeaderRecord(final ByteBuffer target, final byte[] chainRoot)
		{
			StorageMetaRecord.writeFileHeaderV1(
				target                      ,
				this.primaryAlgorithm.kind(),
				chainRoot
			);
		}

		@Override
		public final byte[] nextChainRoot(final StorageLiveDataFile.Default predecessor)
		{
			if(!this.primaryAlgorithm.isChained())
			{
				// non-chained kinds use an all-zero chain root (fixed 32-byte field in the format).
				return new byte[StorageMetaRecord.LENGTH_HASH_SHA256];
			}
			// chained: continue from the predecessor's tip, or seed the first file from the primary
			// algorithm's configured initial seed (tip_0).
			return predecessor != null && predecessor.chainTip() != null
				? predecessor.chainTip()
				: this.primaryAlgorithm.initialSeed()
			;
		}

		@Override
		public final void cacheFileHeaderOn(final StorageLiveDataFile.Default file, final byte[] chainRoot)
		{
			file.setFileHeaderV1(this.primaryAlgorithm.kind(), chainRoot);
		}

		@Override
		public final void writeChunkChecksumRecord(
			final StorageLiveDataFile.Default file            ,
			final long                        chunkStartOffset,
			final ByteBuffer                  target          ,
			final ByteBuffer...               dataBuffers
		)
		{
			// chunkStartOffset fits an int (files capped at Integer.MAX_VALUE); fail loud, never truncate.
			final int chunkStart = checkArrayRange(chunkStartOffset);
			if(this.primaryAlgorithm.isChained())
			{
				// fold the file's running tip into this chunk's hash. The newly computed hash is held as the
				// algorithm's pending tip; the file tip is advanced only by commitChunkWrite(file), once this
				// write is committed. A write that is never committed (it failed, or was rolled back after a
				// sibling channel failed) thus leaves the file tip untouched.
				this.primaryAlgorithm.setChainTip(file.chainTip());
				this.primaryAlgorithm.writeRecord(target, chunkStart, dataBuffers);
			}
			else
			{
				this.primaryAlgorithm.writeRecord(target, chunkStart, dataBuffers);
			}
		}

		@Override
		public final void commitChunkWrite(final StorageLiveDataFile.Default file)
		{
			// advance the file's running tip to the hash computed by the preceding writeChunkChecksumRecord,
			// now that the write is committed. No-op for non-chained kinds (no per-file tip).
			if(this.primaryAlgorithm.isChained())
			{
				file.setChainTip(this.primaryAlgorithm.chainTip());
			}
		}

		@Override
		public final void registerMetaRecordHandlers(
			final StorageMetaRecordRegistry      registry,
			final StorageChecksumAnomalyReporter reporter
		)
		{
			final StorageMetaRecordHandler fileHeaderHandler    = new FileHeaderV1Handler(reporter);
			final StorageMetaRecordHandler chunkChecksumHandler = new ChunkChecksumHandler(this.policy, this.algorithmsByKind, reporter);

			registry.register(StorageMetaRecord.KIND_FileHeaderV1, fileHeaderHandler);
			for(final Long kind : this.algorithmsByKind.keySet())
			{
				// One handler instance serves every chunk-checksum KIND; it dispatches the Algorithm by the
				// record's on-disk KIND at verify time.
				registry.register(kind, chunkChecksumHandler);
			}
		}

		@Override
		public final void verifyDataFile(
			final StorageLiveDataFile.Default    file    ,
			final ByteBuffer                     buffer  ,
			final StorageChecksumAnomalyReporter reporter
		)
		{
			final long bufferStartAddress = XMemory.getDirectByteBufferAddress(buffer);
			final long bufferBoundAddress = bufferStartAddress + buffer.limit();
			final long fileNumber         = file.number();

			// Per-file header state reconstructed from disk into LOCALS (the live file object is never mutated).
			long    headerKind  = 0L;   // 0 == no FileHeaderV1 parsed (legacy file)
			byte[]  chainTip    = null; // running chained tip, seeded from the parsed header's chainRoot
			boolean hasLiveData = false;

			// Start of the current chunk's bytes; advances past the FileHeaderV1 and each covering record.
			long chunkStart = bufferStartAddress;

			for(long address = bufferStartAddress; address < bufferBoundAddress;)
			{
				// A trailing remnant shorter than an entity LENGTH prefix cannot be a valid item; reading 8 bytes
				// would read past the verified region. The startup load walk (StorageEntityInitializer) throws on
				// this; the on-demand check instead stops and lets the end-of-walk coverage check report the
				// uncovered tail. A well-formed file ends exactly on an item boundary, so this never trips there.
				if(bufferBoundAddress - address < Long.BYTES)
				{
					break;
				}
				final long itemLength = Binary.getEntityLengthRawValue(address);
				if(itemLength > 0)
				{
					hasLiveData = true;
					address += itemLength;
					continue;
				}
				if(itemLength == 0)
				{
					throw new StorageExceptionConsistency("Zero length data item.");
				}

				// negative-length entry: legacy opaque gap, or a structured meta record (mirror the envelope
				// guard in StorageMetaRecordRegistry.dispatch before reading the marker/KIND).
				if(address + StorageMetaRecord.OFFSET_PAYLOAD <= bufferBoundAddress
					&& StorageMetaRecord.isMetaRecord(address))
				{
					final long kind = StorageMetaRecord.kindOf(address);
					if(kind == StorageMetaRecord.KIND_FileHeaderV1)
					{
						if(address + StorageMetaRecord.LENGTH_FILEHEADERV1 > bufferBoundAddress)
						{
							// truncated header
							reporter.onUnknownKind(fileNumber, StorageMetaRecord.KIND_FileHeaderV1);
						}
						else if(headerKind != 0L)
						{
							// second/misplaced header: report, do not re-seed (would rebase every following chunk).
							reporter.onUnknownKind(fileNumber, StorageMetaRecord.KIND_FileHeaderV1);
							chunkStart = address + StorageMetaRecord.LENGTH_FILEHEADERV1;
						}
						else
						{
							final FileHeaderV1Payload header = StorageMetaRecord.readFileHeaderV1(address);
							headerKind = header.chunkChecksumKind();
							chainTip   = header.chainRoot(); // seed the running tip; chunks advance it
							chunkStart = address + StorageMetaRecord.LENGTH_FILEHEADERV1;
						}
					}
					else
					{
						final Algorithm verifier = this.algorithmsByKind.get(kind);
						if(verifier != null && address + verifier.recordLength() <= bufferBoundAddress)
						{
							chainTip   = verifyChunk(fileNumber, buffer, address, chunkStart, verifier, chainTip, reporter);
							chunkStart = address + verifier.recordLength();
						}
						else
						{
							// no algorithm for this KIND (drift / corrupt KIND), or a truncated record.
							reporter.onUnknownKind(fileNumber, kind);
							// Mirror StorageMetaRecordRegistry.dispatch: an unknown KIND reached AFTER the
							// FileHeaderV1 was parsed is a genuine forward-compat record, so advance the chunk
							// boundary past it (|LENGTH| == -itemLength) — otherwise a later covering record's
							// stored start would no longer match the walker's cursor, raising a false
							// CHUNK_BOUNDARY_MISMATCH / UNCOVERED_DATA. A truncated KNOWN record (verifier != null)
							// leaves chunkStart unadvanced, mirroring ChunkChecksumHandler.onLoad's report-and-skip;
							// so does an unknown KIND before the header is parsed (a corrupted/lost FileHeaderV1),
							// so the drift still surfaces instead of loading silently.
							if(verifier == null && headerKind != 0L)
							{
								chunkStart = address - itemLength;
							}
						}
					}
				}

				// advance by the on-disk |LENGTH| regardless (legacy gaps and meta records alike).
				address -= itemLength;
			}

			// per-file coverage checks (mirror StorageMetaRecordRegistry.onFileComplete, using the locals).
			if(headerKind == 0L)
			{
				if(hasLiveData)
				{
					reporter.onMissingHeader(fileNumber);
				}
			}
			else if(chunkStart - bufferStartAddress < buffer.limit())
			{
				reporter.onUncoveredData(fileNumber, chunkStart - bufferStartAddress);
			}
			reporter.onFileWalkComplete(fileNumber);
		}

		@Override
		public final void verifyDataFileStreaming(
			final StorageLiveDataFile.Default    file        ,
			final long                           verifyLength,
			final ByteBuffer                     window      ,
			final StorageChecksumAnomalyReporter reporter
		)
		{
			final long         fileNumber = file.number();
			final WindowReader reader     = new WindowReader(file, window, verifyLength);

			// Per-file header state reconstructed from disk into LOCALS (the live file is never mutated) — the same
			// reconstruction verifyDataFile does, but driven over a sliding read window instead of a resident buffer.
			long    headerKind  = 0L;   // 0 == no FileHeaderV1 parsed (legacy file)
			byte[]  chainTip    = null; // running chained tip, seeded from the parsed header's chainRoot
			boolean hasLiveData = false;

			// Start of the current chunk's bytes (file offset); advances past the FileHeaderV1 and each covering record.
			long chunkStart = 0L;

			for(long walkCursor = 0L; walkCursor < verifyLength;)
			{
				// A trailing remnant shorter than an entity LENGTH prefix cannot be a valid item; reading 8 bytes
				// would read past the verified region (into stale window slack). Stop and let the end-of-walk
				// coverage check report the uncovered tail — the same condition the startup load walk rejects.
				// A well-formed file ends exactly on an item boundary, so this never trips there.
				if(verifyLength - walkCursor < Long.BYTES)
				{
					break;
				}
				final long itemLength = Binary.getEntityLengthRawValue(reader.ensureResident(walkCursor, Long.BYTES));
				if(itemLength > 0)
				{
					// data entity: part of the current chunk. Its bytes are hashed lazily as the raw range
					// [chunkStart, record) at the covering record (feed), so nothing is fed here.
					hasLiveData = true;
					walkCursor += itemLength;
					continue;
				}
				if(itemLength == 0)
				{
					throw new StorageExceptionConsistency("Zero length data item.");
				}

				// negative-length entry: legacy opaque gap, or a structured meta record (mirror the envelope guard
				// in verifyDataFile before reading the marker/KIND).
				if(walkCursor + StorageMetaRecord.OFFSET_PAYLOAD <= verifyLength
					&& StorageMetaRecord.isMetaRecord(reader.ensureResident(walkCursor, StorageMetaRecord.OFFSET_PAYLOAD)))
				{
					final long kind = StorageMetaRecord.kindOf(reader.ensureResident(walkCursor, StorageMetaRecord.OFFSET_PAYLOAD));
					if(kind == StorageMetaRecord.KIND_FileHeaderV1)
					{
						if(walkCursor + StorageMetaRecord.LENGTH_FILEHEADERV1 > verifyLength)
						{
							// truncated header
							reporter.onUnknownKind(fileNumber, StorageMetaRecord.KIND_FileHeaderV1);
						}
						else if(headerKind != 0L)
						{
							// second/misplaced header: report, do not re-seed (would rebase every following chunk).
							reporter.onUnknownKind(fileNumber, StorageMetaRecord.KIND_FileHeaderV1);
							chunkStart = walkCursor + StorageMetaRecord.LENGTH_FILEHEADERV1;
						}
						else
						{
							final FileHeaderV1Payload header = StorageMetaRecord.readFileHeaderV1(
								reader.ensureResident(walkCursor, StorageMetaRecord.LENGTH_FILEHEADERV1)
							);
							headerKind = header.chunkChecksumKind();
							chainTip   = header.chainRoot(); // seed the running tip; chunks advance it
							chunkStart = walkCursor + StorageMetaRecord.LENGTH_FILEHEADERV1;
						}
					}
					else
					{
						final Algorithm verifier = this.algorithmsByKind.get(kind);
						if(verifier != null && walkCursor + verifier.recordLength() <= verifyLength)
						{
							// covering record: re-verify the chunk [chunkStart, walkCursor). Mirrors verifyChunk, but
							// the chunk bytes are streamed into the digest in windows (begin/feed/finish) rather than
							// sliced from a resident buffer, so a chunk larger than the window still verifies. Dispatch
							// the Algorithm by the record's on-disk KIND (the record bytes are read BEFORE feeding,
							// since feed slides the window away from the record).
							final int    recordLen        = checkArrayRange(verifier.recordLength());
							final long   recordAddress     = reader.ensureResident(walkCursor, recordLen);
							final int    storedChunkStart  = StorageMetaRecord.readChunkChecksumChunkStart(recordAddress);
							final byte[] expected          = verifier.readStoredChecksumBytes(recordAddress);
							final boolean chained           = verifier.isChained();

							if(isChunkFramingConsistent(storedChunkStart, chunkStart, walkCursor))
							{
								// resolve the chained seed exactly as verifyChunk: a chained covering record with no
								// parsed FileHeaderV1 means the header was lost/corrupted — report and verify against
								// the initial seed so a real defect still surfaces as a checksum mismatch.
								byte[] seed = chained ? chainTip : null;
								if(chained && seed == null)
								{
									reporter.onMissingHeader(fileNumber);
									seed = verifier.initialSeed();
								}
								verifier.beginChunk(seed);
								reader.feed(verifier, chunkStart, walkCursor); // hash [chunkStart, record) in windows
								final byte[] actual = verifier.finishChunk();
								if(!Arrays.equals(actual, expected))
								{
									reporter.onChecksumMismatch(fileNumber, chunkStart, kind, expected, actual);
								}
								// chained: continue from the stored tip (== recomputed on match), the writer's basis.
								chainTip = chained ? expected : chainTip;
							}
							else
							{
								reporter.onChunkBoundaryMismatch(fileNumber, walkCursor, storedChunkStart, chunkStart);
								if(chained)
								{
									chainTip = expected; // re-anchor from the stored tip
								}
							}
							chunkStart = walkCursor + recordLen;
						}
						else
						{
							// no algorithm for this KIND (drift / corrupt KIND), or a truncated record.
							reporter.onUnknownKind(fileNumber, kind);
							// Same forward-compat rule as verifyDataFile: advance past an unknown KIND only once a
							// FileHeaderV1 has been parsed; otherwise leave chunkStart so the drift still surfaces.
							if(verifier == null && headerKind != 0L)
							{
								chunkStart = walkCursor - itemLength;
							}
						}
					}
				}

				// advance by the on-disk |LENGTH| regardless (legacy gaps and meta records alike). Gap bytes inside a
				// chunk are not fed here; the covering record's feed of [chunkStart, record) covers them as raw bytes.
				walkCursor -= itemLength;
			}

			// per-file coverage checks (identical to verifyDataFile, in file-relative offsets).
			if(headerKind == 0L)
			{
				if(hasLiveData)
				{
					reporter.onMissingHeader(fileNumber);
				}
			}
			else if(chunkStart < verifyLength)
			{
				reporter.onUncoveredData(fileNumber, chunkStart);
			}
			reporter.onFileWalkComplete(fileNumber);
		}

		/**
		 * Sliding read window over a data file for {@link #verifyDataFileStreaming}: keeps at most one window of the
		 * file resident in the supplied direct buffer and serves both the forward framing walk (small fixed reads via
		 * {@link #ensureResident(long, int)}) and the incremental chunk hashing (the raw chunk range via
		 * {@link #feed(Algorithm, long, long)}). Reads never pass {@code fileLength} (the caller's bounded verify
		 * length), so the head file is read only up to its committed length. Single-threaded (one channel).
		 */
		private static final class WindowReader
		{
			private final StorageLiveDataFile.Default file         ;
			private final ByteBuffer                  buffer       ;
			private final long                        bufferAddress;
			private final int                         capacity     ;
			private final long                        fileLength   ;
			private       long                        windowOffset ; // file offset of buffer[0]; -1 == empty
			private       long                        windowLength ; // resident byte count

			WindowReader(final StorageLiveDataFile.Default file, final ByteBuffer buffer, final long fileLength)
			{
				super();
				this.file          = file                                  ;
				this.buffer        = buffer                                ;
				this.bufferAddress = XMemory.getDirectByteBufferAddress(buffer);
				this.capacity      = buffer.capacity()                     ;
				this.fileLength    = fileLength                            ;
				this.windowOffset  = -1L                                   ;
				this.windowLength  = 0L                                    ;
			}

			private void refill(final long offset)
			{
				final long toRead = Math.min(this.capacity, this.fileLength - offset);
				this.buffer.clear();
				this.buffer.limit(checkArrayRange(toRead));
				this.file.readBytes(this.buffer, offset, toRead);
				this.windowOffset = offset;
				this.windowLength = toRead;
			}

			/**
			 * Ensures {@code [offset, offset+need)} is resident ({@code need} fits the window capacity) and returns
			 * the off-heap address of {@code offset}. The address is valid only until the next refill (any
			 * {@code ensureResident}/{@code feed} that slides the window).
			 */
			long ensureResident(final long offset, final int need)
			{
				if(this.windowOffset < 0L
					|| offset < this.windowOffset
					|| offset + need > this.windowOffset + this.windowLength)
				{
					this.refill(offset);
				}
				return this.bufferAddress + (offset - this.windowOffset);
			}

			/**
			 * Feeds the raw byte range {@code [from, to)} into {@code verifier}'s open chunk, reading the range in
			 * windows. This is how a chunk larger than the window is hashed.
			 */
			void feed(final Algorithm verifier, long from, final long to)
			{
				while(from < to)
				{
					if(this.windowOffset < 0L
						|| from < this.windowOffset
						|| from >= this.windowOffset + this.windowLength)
					{
						this.refill(from);
					}
					final long windowEnd = this.windowOffset + this.windowLength;
					final long n         = Math.min(to, windowEnd) - from;
					verifier.updateChunk(XMemory.slice(this.buffer, from - this.windowOffset, n));
					from += n;
				}
			}
		}

	}



	/**
	 * Per-kind primitive: owns one algorithm's record layout, write logic, compute primitive, and
	 * stored-checksum reader. Each instance owns its stateful native object ({@link MessageDigest},
	 * {@link CRC32C}, ...) and is bound to a single channel by virtue of being held by that channel's
	 * {@link StorageChunkChecksumCalculator}.
	 * <p>
	 * Adding a new algorithm = adding a new sibling subclass here, plus a {@link StorageChunkChecksumProvider}
	 * subclass that produces it as the primary algorithm and registers it in the kind registry.
	 */
	public interface Algorithm
	{
		/**
		 * @return the KIND code identifying this algorithm's chunk-checksum record on disk.
		 */
		public long kind();

		/**
		 * @return the byte length of this algorithm's chunk-checksum record.
		 */
		public long recordLength();

		/**
		 * Computes this algorithm's checksum over {@code dataBuffers} and writes the covering
		 * chunk-checksum meta record into {@code target} at its current position (advances by
		 * {@link #recordLength()}). The record stores {@code chunkStartOffset} &mdash; the in-file offset
		 * where the covered chunk begins &mdash; in its shared envelope (see
		 * {@link StorageMetaRecord#writeChunkChecksumHeader(ByteBuffer, long, long, int)}).
		 *
		 * @param target           the meta-record buffer to write into.
		 * @param chunkStartOffset the in-file offset where the covered chunk's bytes begin.
		 * @param dataBuffers      the chunk's data buffers (hashed, not advanced).
		 */
		public void writeRecord(ByteBuffer target, int chunkStartOffset, ByteBuffer... dataBuffers);

		/**
		 * Computes this algorithm's checksum over a single chunk slice (verify path).
		 */
		public byte[] computeChecksumBytes(ByteBuffer chunk);

		/**
		 * Reads the stored checksum bytes from a chunk-checksum record at {@code recordAddress}.
		 */
		public byte[] readStoredChecksumBytes(long recordAddress);

		/**
		 * @return whether this algorithm chains: each chunk's hash folds in the previous chunk's hash, so
		 *         reorder / insert / delete / substitute of chunks within a file is detectable. Non-chained kinds return
		 *         {@code false} and ignore {@link #setChainTip(byte[])} / {@link #chainTip()}.
		 */
		public default boolean isChained()
		{
			return false;
		}

		/**
		 * Sets the running chain tip (the previous chunk's hash) that the next {@link #writeRecord} /
		 * {@link #computeChecksumBytes} call folds in. No-op for non-chained kinds.
		 */
		public default void setChainTip(final byte[] tip)
		{
			// non-chained: ignored
		}

		/**
		 * @return the running chain tip as advanced by the last {@link #writeRecord} /
		 *         {@link #computeChecksumBytes}; {@code null} for non-chained kinds.
		 */
		public default byte[] chainTip()
		{
			return null;
		}

		/**
		 * @return this algorithm's initial chain seed ({@code tip_0}), stamped into the first data file's
		 *         {@code FileHeaderV1.chainRoot} and folded into that file's first chunk hash. Chained kinds
		 *         carry a configurable seed; non-chained kinds return an all-zero
		 *         {@link StorageMetaRecord#LENGTH_HASH_SHA256}-byte array (unused).
		 */
		public default byte[] initialSeed()
		{
			return new byte[StorageMetaRecord.LENGTH_HASH_SHA256];
		}

		/**
		 * Begins an incremental chunk hash for the streaming verify path (a chunk too large to hold resident).
		 * Resets this algorithm's running digest and, for chained kinds, folds in {@code seed} (the running tip)
		 * exactly as one-shot {@link #computeChecksumBytes(ByteBuffer)} does. Followed by zero or more
		 * {@link #updateChunk(ByteBuffer)} calls and one {@link #finishChunk()}. Abandoning an open chunk (no
		 * {@code finishChunk}) is safe: the next {@code beginChunk} resets the digest. Default throws: only
		 * verifying algorithms support it (the no-op {@code None} is never verified).
		 */
		public default void beginChunk(final byte[] seed)
		{
			throw new UnsupportedOperationException();
		}

		/**
		 * Feeds one window of the current chunk's bytes into the hash opened by {@link #beginChunk(byte[])}.
		 */
		public default void updateChunk(final ByteBuffer partial)
		{
			throw new UnsupportedOperationException();
		}

		/**
		 * Finalizes the chunk hash opened by {@link #beginChunk(byte[])} and returns its bytes; for chained kinds
		 * this also advances the running tip to the returned hash, mirroring {@link #computeChecksumBytes(ByteBuffer)}.
		 */
		public default byte[] finishChunk()
		{
			throw new UnsupportedOperationException();
		}



		/**
		 * CRC32C algorithm: non-cryptographic integrity checksum, hardware-accelerated, zero-copy on
		 * direct buffers. Detects accidental corruption; <b>not</b> collision-resistant or tamper-evident.
		 * Emits {@code ChunkChecksumCRC32CV1} records (20 B).
		 */
		public final class Crc32c implements Algorithm
		{
			///////////////////////////////////////////////////////////////////////////
			// constants //
			//////////////

			/**
			 * KIND code for the CRC32C chunk-checksum record ({@code ChunkChecksumCRC32CV1}). Pinned
			 * by test vector: first 2 bytes of {@code SHA-256("EclipseStoreMetaRecord/Kind/ChunkChecksumCRC32CV1")}.
			 */
			public static final long KIND = 0x4667L;

			/**
			 * Total record length in bytes: envelope (12) + chunk start (4) + crc (4) = 20. The CRC is stored
			 * as a real 4-byte field — meta records are read via unaligned {@code XMemory} loads, so no padding
			 * for 8-byte alignment is needed.
			 */
			public static final int RECORD_LENGTH = 20;

			private static final int OFFSET_CRC = StorageMetaRecord.OFFSET_CC_CHECKSUM;



			///////////////////////////////////////////////////////////////////////////
			// instance fields //
			////////////////////

			// CRC32C is stateful and requires reset() before each computation.
			private final CRC32C crc;



			///////////////////////////////////////////////////////////////////////////
			// constructors //
			/////////////////

			public Crc32c()
			{
				super();
				this.crc = new CRC32C();
			}



			///////////////////////////////////////////////////////////////////////////
			// static methods //
			///////////////////

			///////////////////////////////////////////////////////////////////////////
			// declared methods //
			/////////////////////

			private int compute(final ByteBuffer... buffers)
			{
				this.crc.reset();
				for(final ByteBuffer buffer : buffers)
				{
					notNull(buffer);
					this.crc.update(buffer.duplicate()); // direct-buffer fast path: zero-copy, in place
				}
				return (int)this.crc.getValue();
			}



			///////////////////////////////////////////////////////////////////////////
			// override methods //
			/////////////////////

			@Override
			public long kind()
			{
				return KIND;
			}

			@Override
			public long recordLength()
			{
				return RECORD_LENGTH;
			}

			@Override
			public byte[] computeChecksumBytes(final ByteBuffer chunk)
			{
				notNull(chunk);
				this.crc.reset();
				this.crc.update(chunk.duplicate());
				return toBytes((int)this.crc.getValue());
			}

			@Override
			public void beginChunk(final byte[] seed)
			{
				this.crc.reset(); // non-chained: seed ignored
			}

			@Override
			public void updateChunk(final ByteBuffer partial)
			{
				this.crc.update(partial.duplicate());
			}

			@Override
			public byte[] finishChunk()
			{
				return toBytes((int)this.crc.getValue());
			}

			@Override
			public byte[] readStoredChecksumBytes(final long recordAddress)
			{
				// Byte-order note: the CRC is persisted as a 4-byte int in NATIVE order (writeRecord uses
				// target.putInt on a native-order direct buffer) and read back via the matching native
				// get_int, so the round-trip recovers the same int value. X.toBytes (big-endian) is applied
				// to that int value on BOTH the read and compute sides purely to obtain comparable byte[]s —
				// it does not pin the on-disk layout, which is native-endian. Comparison is self-consistent.
				return toBytes(XMemory.get_int(recordAddress + OFFSET_CRC));
			}

			@Override
			public void writeRecord(
				final ByteBuffer    target          ,
				final int           chunkStartOffset,
				final ByteBuffer... dataBuffers
			)
			{
				final int crc = this.compute(dataBuffers);
				StorageMetaRecord.writeChunkChecksumHeader(target, RECORD_LENGTH, KIND, chunkStartOffset);
				target.putInt(crc);
			}

		}



		/**
		 * Chained SHA-256 algorithm: cryptographic and tamper-evident (SHA-256), but each chunk's
		 * stored hash folds in the previous chunk's hash &mdash; {@code tip_i = SHA-256(tip_{i-1} || chunk_i)},
		 * seeded by the file's {@code FileHeaderV1.chainRoot} ({@code tip_0}). Altering any chunk invalidates
		 * that chunk and every chunk after it in the same file, so reordering, insertion, deletion or
		 * substitution of chunks <i>within a file</i> is detectable &mdash; not just bit-rot of a single chunk.
		 * The chain is re-seeded per file from that file's own header, so it does not span files: deleting or
		 * reordering whole data files (as housekeeping legitimately does) is not detected by the chain. Emits
		 * {@code ChunkChecksumChainedV1} records (48 B; same envelope+hash layout as {@code ChunkChecksumV1},
		 * distinguished purely by KIND).
		 * <p>
		 * <b>Chaining is SHA-256 only.</b> A chained CRC32C is forgeable (CRC is linear and not
		 * collision-resistant), so it would provide no tamper-evidence while adding nothing for accidental
		 * corruption; only this cryptographic variant backs the chained provider.
		 * <p>
		 * The running tip is held here as a scratch register: the per-channel
		 * {@link StorageChunkChecksumCalculator} sets it from the authoritative per-file tip
		 * ({@link StorageLiveDataFile.Default#chainTip()}) immediately before each call and reads it back
		 * afterwards, so this instance carries no cross-file state of its own.
		 */
		public final class Sha256Chained implements Algorithm
		{
			///////////////////////////////////////////////////////////////////////////
			// constants //
			//////////////

			/**
			 * KIND code for the chained SHA-256 chunk-checksum record ({@code ChunkChecksumChainedV1}).
			 * Pinned by test vector: first 2 bytes of
			 * {@code SHA-256("EclipseStoreMetaRecord/Kind/ChunkChecksumChainedV1")} = {@code 0x56ba}.
			 */
			public static final long KIND = 0x56baL;

			/** Total record length in bytes: envelope (12) + chunk start (4) + hash (32) = 48. */
			public static final int RECORD_LENGTH = 48;

			private static final int OFFSET_HASH = StorageMetaRecord.OFFSET_CC_CHECKSUM;
			private static final int HASH_LENGTH = 32;

			private static final String ALGORITHM = "SHA-256";



			///////////////////////////////////////////////////////////////////////////
			// instance fields //
			////////////////////

			// MessageDigest.digest() auto-resets the instance for the next computation.
			private final MessageDigest digest;

			// tip_0: the configured initial chain seed, stamped into the first file's FileHeaderV1.chainRoot.
			private final byte[] initialSeed;

			// Scratch register for the running tip; set/read by the calculator around each use.
			private byte[] tip;



			///////////////////////////////////////////////////////////////////////////
			// constructors //
			/////////////////

			public Sha256Chained()
			{
				this(null);
			}

			public Sha256Chained(final byte[] initialSeed)
			{
				super();
				this.initialSeed = validateSeed(initialSeed);
				this.digest      = newDigest();
				this.tip         = this.initialSeed.clone(); // running tip starts at the seed; calculator re-sets it per file
			}



			///////////////////////////////////////////////////////////////////////////
			// static methods //
			///////////////////

			private static byte[] validateSeed(final byte[] seed)
			{
				if(seed == null)
				{
					return new byte[HASH_LENGTH];
				}
				if(seed.length != HASH_LENGTH)
				{
					throw new IllegalArgumentException(
						"Chain seed must be " + HASH_LENGTH + " bytes, got " + seed.length
					);
				}
				return seed.clone();
			}

			private static MessageDigest newDigest()
			{
				try
				{
					return MessageDigest.getInstance(ALGORITHM);
				}
				catch(final NoSuchAlgorithmException e)
				{
					// SHA-256 is mandated by every conforming JRE; this should never happen.
					throw new WrapperRuntimeException(e);
				}
			}



			///////////////////////////////////////////////////////////////////////////
			// override methods //
			/////////////////////

			@Override
			public boolean isChained()
			{
				return true;
			}

			@Override
			public byte[] initialSeed()
			{
				return this.initialSeed.clone();
			}

			@Override
			public void setChainTip(final byte[] tip)
			{
				this.tip = tip;
			}

			@Override
			public byte[] chainTip()
			{
				return this.tip;
			}

			@Override
			public long kind()
			{
				return KIND;
			}

			@Override
			public long recordLength()
			{
				return RECORD_LENGTH;
			}

			@Override
			public byte[] computeChecksumBytes(final ByteBuffer chunk)
			{
				notNull(chunk);
				this.digest.update(this.tip);          // fold in the previous tip
				this.digest.update(chunk.duplicate());
				return this.tip = this.digest.digest();// advance and return the new tip
			}

			@Override
			public void beginChunk(final byte[] seed)
			{
				// reset (clears any bytes from an abandoned chunk) and fold in the running tip, exactly as the
				// one-shot computeChecksumBytes does via digest.update(this.tip). `seed` is the resolved running
				// tip (never null for a chained verify: the caller substitutes initialSeed() on a missing header).
				this.digest.reset();
				if(seed != null)
				{
					this.digest.update(seed);
				}
			}

			@Override
			public void updateChunk(final ByteBuffer partial)
			{
				this.digest.update(partial.duplicate());
			}

			@Override
			public byte[] finishChunk()
			{
				return this.tip = this.digest.digest(); // advance and return the new tip (mirrors computeChecksumBytes)
			}

			@Override
			public byte[] readStoredChecksumBytes(final long recordAddress)
			{
				final byte[] hash = new byte[HASH_LENGTH];
				XMemory.copyRangeToArray(recordAddress + OFFSET_HASH, hash);
				return hash;
			}

			@Override
			public void writeRecord(
				final ByteBuffer    target          ,
				final int           chunkStartOffset,
				final ByteBuffer... dataBuffers
			)
			{
				this.digest.update(this.tip);          // fold in the previous tip
				for(final ByteBuffer buffer : dataBuffers)
				{
					notNull(buffer);
					this.digest.update(buffer.duplicate());
				}
				final byte[] hash = this.digest.digest();
				this.tip = hash;                       // advance the tip to this chunk's hash
				StorageMetaRecord.writeChunkChecksumHeader(target, RECORD_LENGTH, KIND, chunkStartOffset);
				target.put(hash);
			}

		}



		/**
		 * No-op algorithm representing "no checksum". It emits and verifies nothing; the only sanctioned use
		 * is as the primary of a {@link StorageChunkChecksumProvider#NewNone() None provider}, which pairs it
		 * with an off policy ({@link StorageChunkChecksumPolicy#NewOff()}) so neither the write nor the verify
		 * path ever invokes it. Its compute/write/read methods therefore throw: being asked to produce or check
		 * a checksum means this algorithm was paired with an emitting/verifying policy by mistake.
		 * <p>
		 * Its {@link #kind()} is a reserved <b>non-zero</b> sentinel ({@code 0x0001}), never written to disk.
		 * It must never be {@code 0}: kind {@code 0} is the on-disk marker for a legacy/pre-feature file, so a
		 * kind-0 primary would alias every legacy head file as "covered" by
		 * {@link StorageChunkChecksumCalculator#coversChunkWrites(StorageLiveDataFile.Default)}.
		 */
		public final class None implements Algorithm
		{
			///////////////////////////////////////////////////////////////////////////
			// constants //
			//////////////

			/** Reserved, non-zero KIND for the no-op "no checksum" algorithm; never written to disk. */
			public static final long KIND = 0x0001L;



			///////////////////////////////////////////////////////////////////////////
			// constructors //
			/////////////////

			public None()
			{
				super();
			}



			///////////////////////////////////////////////////////////////////////////
			// override methods //
			/////////////////////

			@Override
			public long kind()
			{
				return KIND;
			}

			@Override
			public long recordLength()
			{
				return 0L; // emits no record
			}

			@Override
			public void writeRecord(final ByteBuffer target, final int chunkStartOffset, final ByteBuffer... dataBuffers)
			{
				throw new UnsupportedOperationException(
					"No-checksum algorithm cannot write a checksum record. Use a writing algorithm, or pair "
					+ "this one with an off policy (see StorageChunkChecksumProvider.NewNone())."
				);
			}

			@Override
			public byte[] computeChecksumBytes(final ByteBuffer chunk)
			{
				throw new UnsupportedOperationException(
					"No-checksum algorithm cannot compute a checksum. Use a verifying algorithm, or pair "
					+ "this one with an off policy (see StorageChunkChecksumProvider.NewNone())."
				);
			}

			@Override
			public byte[] readStoredChecksumBytes(final long recordAddress)
			{
				throw new UnsupportedOperationException(
					"No-checksum algorithm has no stored checksum to read."
				);
			}

		}

	}

}
