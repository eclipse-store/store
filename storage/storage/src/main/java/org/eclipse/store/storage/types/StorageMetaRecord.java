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

import static org.eclipse.serializer.util.X.notNull;

import java.nio.ByteBuffer;

import org.eclipse.serializer.memory.XMemory;
import org.eclipse.serializer.persistence.binary.types.Binary;

/**
 * Format-only spec for in-gap meta records inside {@code .dat} data files.
 * <p>
 * A meta record is a negative-length entry whose interior carries a structured payload
 * identified by a high-entropy {@code META_MARKER} at offset 8 and a {@code KIND} code
 * at offset 10. Old readers (without meta-record awareness) skip the whole entry by
 * {@code |LENGTH|} without inspecting its content; new readers recognize the marker and
 * dispatch on the kind code to parse the kind-specific payload.
 * <p>
 * The cross-kind envelope and one shared kind &mdash; {@code FileHeaderV1} &mdash; live here:
 * <ul>
 *   <li>{@link #KIND_FileHeaderV1} &mdash; one per data file, emitted as the file's first
 *       entry at creation. Carries the chosen chunk-checksum kind and the chain root
 *       (used by chained-hash variants). Total size: 46 bytes (LENGTH = -46).</li>
 * </ul>
 * The framing for each chunk-checksum kind (its KIND code, record layout, writer and reader) lives
 * with its algorithm &mdash; see {@code StorageChunkChecksumCalculator.Algorithm}; each implementation
 * owns its KIND, layout, write and read, and is supplied to {@code StorageChunkChecksumProvider}.
 * <p>
 * Envelope layout (any kind):
 * <pre>
 *   offset  0   LENGTH        8 B   negative; magnitude = total entry size
 *   offset  8   META_MARKER   2 B   reserved; same value for every kind
 *   offset 10   KIND          2 B   identifies the record type
 *   offset 12   payload ...         kind-specific; total length implied by LENGTH
 * </pre>
 * <p>
 * The {@code META_MARKER} and {@code KIND} codes are the first 2 bytes of a SHA-256 hash of
 * fixed strings (see the constants below). The marker and kind together form a 32-bit gate: a
 * non-meta negative-length entry is misread as a meta record only if its offset-8 2-byte value
 * matches {@code META_MARKER} <i>and</i> its offset-10 2-byte value matches a registered KIND &mdash;
 * otherwise it falls through to the unknown-KIND strictness (skip by default).
 *
 * @see Binary
 */
public final class StorageMetaRecord
{
	///////////////////////////////////////////////////////////////////////////
	// constants //
	//////////////

	/**
	 * Reserved marker at offset 8 of every meta record. Distinguishes a structured meta
	 * record from any other negative-length entry (e.g., legacy gap tombstones).
	 * <p>
	 * Derivation: the first 2 bytes (big-endian) of
	 * {@code SHA-256("EclipseStoreMetaRecord/V1")}, interpreted as a Java {@code short}.
	 * <p>
	 * Test vector: {@code SHA-256("EclipseStoreMetaRecord/V1")} =
	 * {@code 215eeffbf92a641c860ad7aa243f9563f16c118a653c0b0e119042a2621b8b4f}; first 2
	 * bytes = {@code 0x215e}.
	 */
	public static final short META_MARKER = (short)0x215e;

	/**
	 * KIND code for {@code FileHeaderV1} &mdash; emitted as the first entry in every new
	 * data file. Carries the file's chunk-checksum kind selection and chain root.
	 * <p>
	 * Derivation: the first 2 bytes (big-endian) of
	 * {@code SHA-256("EclipseStoreMetaRecord/Kind/FileHeaderV1")}, as an unsigned 16-bit
	 * value held in a {@code long}.
	 * <p>
	 * Test vector: first 2 bytes = {@code 0xe5a3}.
	 */
	public static final long KIND_FileHeaderV1 = 0xe5a3L;

	// Envelope offsets (every kind): LENGTH 8 B, META_MARKER 2 B, KIND 2 B
	public static final int OFFSET_LENGTH      =  0;
	public static final int OFFSET_META_MARKER =  8;
	public static final int OFFSET_KIND        = 10;
	public static final int OFFSET_PAYLOAD     = 12;

	// FileHeaderV1 payload layout: kind_selection 2 B, chain_root 32 B
	public static final int OFFSET_FH_KIND_SELECTION = 12;
	public static final int OFFSET_FH_CHAIN_ROOT     = 14;
	public static final int LENGTH_FILEHEADERV1      = 46;

	// Chunk-checksum record payload: chunk_start (4 B, in-file offset of the covered chunk) then the algorithm's
	// checksum bytes. The stored chunk start lets the load walk detect framing desync (a corrupted LENGTH that
	// reroutes it past a record). Fits an int: data files are capped at Integer.MAX_VALUE.
	public static final int OFFSET_CC_CHUNK_START = 12;
	public static final int OFFSET_CC_CHECKSUM    = 16;

	// SHA-256 output length in bytes — also the FileHeaderV1.chain_root field size (fixed by format,
	// shared across chunk-checksum kinds, used by every chained-variant provider as seed length).
	public static final int LENGTH_HASH_SHA256 = 32;



	///////////////////////////////////////////////////////////////////////////
	// recognizer //
	////////////////

	/**
	 * Returns {@code true} if the negative-length entry at {@code address} is a structured
	 * meta record (META_MARKER present at offset 8). Caller must have already established
	 * that the entry's LENGTH (at offset 0) is negative; this method does not re-check.
	 *
	 * @param address absolute memory address of the entry's start (offset 0 = LENGTH).
	 * @return {@code true} if offset 8 contains the META_MARKER.
	 */
	public static boolean isMetaRecord(final long address)
	{
		return XMemory.get_short(address + OFFSET_META_MARKER) == META_MARKER;
	}

	/**
	 * Reads the KIND code at offset 10. Caller must have already called
	 * {@link #isMetaRecord(long)} and confirmed it returned {@code true}.
	 *
	 * @param address absolute memory address of the meta record's start.
	 * @return the KIND code as written.
	 */
	public static long kindOf(final long address)
	{
		return XMemory.get_short(address + OFFSET_KIND) & 0xFFFFL;
	}



	///////////////////////////////////////////////////////////////////////////
	// payload writers //
	/////////////////////

	/**
	 * Writes a {@code FileHeaderV1} entry into {@code target} at its current position
	 * (advances position by {@value #LENGTH_FILEHEADERV1}). The {@code target} buffer must
	 * be in native byte order &mdash; the encoded values are read back via
	 * {@link XMemory#get_long(long)} which performs no byte-order transformation.
	 *
	 * @param target              direct ByteBuffer in native byte order; must have at
	 *                            least {@value #LENGTH_FILEHEADERV1} bytes remaining.
	 * @param chunkChecksumKind   KIND code of the chunk-checksum variant used in this file
	 *                            (the value of the corresponding provider's {@code KIND}).
	 * @param chainRoot32         32-byte chain seed for chained variants; all-zeros for
	 *                            non-chained kinds.
	 */
	public static void writeFileHeaderV1(
		final ByteBuffer target            ,
		final long       chunkChecksumKind ,
		final byte[]     chainRoot32
	)
	{
		notNull(target)     ;
		notNull(chainRoot32);
		if(chainRoot32.length != LENGTH_HASH_SHA256)
		{
			throw new IllegalArgumentException(
				"chainRoot32 must be " + LENGTH_HASH_SHA256 + " bytes, got " + chainRoot32.length
			);
		}

		target.putLong (-(long)LENGTH_FILEHEADERV1);
		target.putShort(META_MARKER               );
		target.putShort((short)KIND_FileHeaderV1   );
		target.putShort((short)chunkChecksumKind   );
		target.put     (chainRoot32                );
	}

	/**
	 * Writes the shared chunk-checksum record envelope (negative LENGTH, {@link #META_MARKER}, {@code kind},
	 * {@code chunkStart}) into {@code target}, advancing by {@value #OFFSET_CC_CHECKSUM} bytes; the calling
	 * algorithm appends its checksum bytes after. {@code target} must be a native-order direct buffer.
	 *
	 * @param target       direct ByteBuffer in native byte order.
	 * @param recordLength total record length (envelope + chunk start + checksum); written negated as LENGTH.
	 * @param kind         the chunk-checksum algorithm KIND code.
	 * @param chunkStart   in-file offset where the covered chunk begins.
	 */
	public static void writeChunkChecksumHeader(
		final ByteBuffer target      ,
		final long       recordLength,
		final long       kind        ,
		final int        chunkStart
	)
	{
		notNull(target);
		target.putLong (-recordLength);
		target.putShort(META_MARKER  );
		target.putShort((short)kind  );
		target.putInt  (chunkStart   );
	}



	///////////////////////////////////////////////////////////////////////////
	// payload readers //
	/////////////////////

	/**
	 * Parses a {@code FileHeaderV1} entry at {@code address}. Caller is responsible for
	 * having confirmed (via {@link #isMetaRecord(long)} + {@link #kindOf(long)}) that the
	 * entry at this address is a FileHeaderV1.
	 *
	 * @param address absolute memory address of the entry's start.
	 * @return the parsed payload.
	 */
	public static FileHeaderV1Payload readFileHeaderV1(final long address)
	{
		final long   kindSelection = XMemory.get_short(address + OFFSET_FH_KIND_SELECTION) & 0xFFFFL;
		final byte[] chainRoot     = new byte[LENGTH_HASH_SHA256]                       ;
		XMemory.copyRangeToArray(address + OFFSET_FH_CHAIN_ROOT, chainRoot);
		return new FileHeaderV1Payload(kindSelection, chainRoot);
	}

	/**
	 * Reads the covered chunk's in-file start offset from a chunk-checksum record at {@code address}. Caller must
	 * have confirmed (via {@link #isMetaRecord(long)} + {@link #kindOf(long)}) that the entry at this address is a
	 * chunk-checksum record.
	 *
	 * @param address absolute memory address of the record's start.
	 * @return the stored chunk-start in-file offset.
	 */
	public static int readChunkChecksumChunkStart(final long address)
	{
		return XMemory.get_int(address + OFFSET_CC_CHUNK_START);
	}



	///////////////////////////////////////////////////////////////////////////
	// nested payload types //
	//////////////////////////

	/**
	 * Parsed {@code FileHeaderV1} payload. Pure data carrier &mdash; no behavior.
	 */
	public static final class FileHeaderV1Payload
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////

		private final long   chunkChecksumKind;
		private final byte[] chainRoot        ;



		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		FileHeaderV1Payload(final long chunkChecksumKind, final byte[] chainRoot)
		{
			super();
			this.chunkChecksumKind = chunkChecksumKind;
			this.chainRoot         = chainRoot        ;
		}



		///////////////////////////////////////////////////////////////////////////
		// getters //
		////////////

		public long chunkChecksumKind()
		{
			return this.chunkChecksumKind;
		}

		public byte[] chainRoot()
		{
			return this.chainRoot;
		}
	}



	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////

	/**
	 * Dummy constructor to prevent instantiation of this static-only utility class.
	 *
	 * @throws UnsupportedOperationException when called.
	 */
	private StorageMetaRecord()
	{
		// static only
		throw new UnsupportedOperationException();
	}
}
