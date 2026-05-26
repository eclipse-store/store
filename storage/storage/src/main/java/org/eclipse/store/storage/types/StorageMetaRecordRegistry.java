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
import java.util.HashMap;
import java.util.Map;

import org.eclipse.serializer.memory.XMemory;
import org.eclipse.store.storage.exceptions.StorageExceptionConsistency;

/**
 * Per-channel, kind-agnostic registry and dispatch point for in-gap meta records during the load walk.
 * Owns a {@code KIND -> handler} map and delegates the forward-compat behavior for unrecognized KINDs to the
 * channel's {@link StorageChecksumAnomalyReporter}; it knows nothing about any particular kind's payload. One
 * instance exists per storage channel; each feature that owns a meta-record kind registers its handler via
 * {@link #register(long, StorageMetaRecordHandler)} during channel setup. The walker
 * ({@link StorageEntityInitializer}) then hands every negative-length entry
 * to {@link #dispatch(StorageLiveDataFile.Default, ByteBuffer, long, long)}, which:
 * <ul>
 *   <li>returns {@code chunkStart} unchanged for a non-meta negative-length entry (a legacy opaque gap);</li>
 *   <li>delegates to the {@link StorageMetaRecordHandler} registered for the record's KIND, if any;</li>
 *   <li>otherwise reports an {@link StorageChunkChecksumPolicy.Anomaly#UNKNOWN_KIND} via the reporter
 *       (IGNORE / LOG / FAIL per policy).</li>
 * </ul>
 * After each file the walker calls {@link #onFileComplete(StorageLiveDataFile.Default, long, long, boolean)},
 * which raises the coverage anomalies ({@link StorageChunkChecksumPolicy.Anomaly#MISSING_HEADER} /
 * {@link StorageChunkChecksumPolicy.Anomaly#UNCOVERED_DATA}) through the reporter and flushes its per-file
 * log-dedupe state.
 *
 * @see StorageMetaRecordHandler
 * @see StorageChecksumAnomalyReporter
 * @see #New(StorageChecksumAnomalyReporter)
 */
public interface StorageMetaRecordRegistry
{
	/**
	 * Dispatches the negative-length entry at {@code address} to its registered handler, or reports an
	 * unknown-KIND anomaly, or leaves a legacy gap untouched.
	 *
	 * @param file       the data file currently being walked.
	 * @param buffer     the direct buffer holding the file's resident bytes.
	 * @param address    absolute memory address of the entry's start (offset 0 = LENGTH); its LENGTH is
	 *                   already established to be negative by the caller.
	 * @param chunkStart the running chunk-boundary cursor threaded by the walker.
	 * @return the updated {@code chunkStart} for the next entry.
	 */
	public long dispatch(
		StorageLiveDataFile.Default file      ,
		ByteBuffer                  buffer    ,
		long                        address   ,
		long                        chunkStart
	);

	/**
	 * Called by the walker once per file, after its entries have been walked, to raise coverage anomalies and
	 * flush the reporter's per-file log-dedupe. A file with no parsed {@code FileHeaderV1} (cached
	 * {@code chunkChecksumKind == 0}) that carries live data is a {@link StorageChunkChecksumPolicy.Anomaly#MISSING_HEADER};
	 * a covered file whose last covering record is followed by further content
	 * ({@code lastChunkOffset < fileContentLength}) has {@link StorageChunkChecksumPolicy.Anomaly#UNCOVERED_DATA}.
	 * Both are gated by the reporter: {@code MISSING_HEADER} is inert unless verifying <i>and</i> coverage is
	 * required (legacy tolerance), while {@code UNCOVERED_DATA} (corruption on a covered file) fires whenever
	 * verifying.
	 *
	 * @param file              the data file just walked.
	 * @param lastChunkOffset   in-file offset where the last (uncovered) chunk begins (end of the last meta record).
	 * @param fileContentLength the file's resident content length walked.
	 * @param hasLiveData       whether the file held any positive-length (live) entity.
	 */
	public void onFileComplete(
		StorageLiveDataFile.Default file             ,
		long                        lastChunkOffset  ,
		long                        fileContentLength,
		boolean                     hasLiveData
	);

	/**
	 * Registers {@code handler} as the load-time handler for meta records of the given {@code kind}. Called
	 * during per-channel setup by each feature that owns a meta-record kind; a kind may be registered only
	 * once. Not thread-safe — all registration happens before the load walk begins.
	 *
	 * @param kind    the meta-record KIND code this handler recognizes.
	 * @param handler the handler; must be non-{@code null}.
	 * @return this registry, for chaining.
	 */
	public StorageMetaRecordRegistry register(long kind, StorageMetaRecordHandler handler);



	/**
	 * Creates an empty registry that reports unknown / coverage anomalies through {@code reporter}. Handlers
	 * are added afterwards via {@link #register(long, StorageMetaRecordHandler)}.
	 *
	 * @param reporter the channel's anomaly reporter; must be non-{@code null}.
	 * @return a new, empty {@link StorageMetaRecordRegistry}.
	 */
	public static StorageMetaRecordRegistry New(
		final StorageChecksumAnomalyReporter reporter
	)
	{
		return new Default(notNull(reporter));
	}



	public final class Default implements StorageMetaRecordRegistry
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////

		private final StorageChecksumAnomalyReporter      reporter      ;
		private final Map<Long, StorageMetaRecordHandler> handlersByKind;



		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		Default(
			final StorageChecksumAnomalyReporter reporter
		)
		{
			super();
			this.reporter       = reporter      ;
			this.handlersByKind = new HashMap<>();
		}



		///////////////////////////////////////////////////////////////////////////
		// override methods //
		/////////////////////

		@Override
		public final StorageMetaRecordRegistry register(final long kind, final StorageMetaRecordHandler handler)
		{
			// StorageMetaRecord.kindOf masks the on-disk KIND to 0xFFFF; a KIND with bits above 16 would be
			// registered under a value the walker can never produce, so its handler would silently never
			// dispatch — reject it here.
			if(kind != (kind & 0xFFFFL))
			{
				throw new StorageExceptionConsistency(
					"Meta-record KIND 0x" + Long.toHexString(kind) + " does not fit the 2-byte on-disk KIND field."
				);
			}

			final StorageMetaRecordHandler previous = this.handlersByKind.putIfAbsent(kind, notNull(handler));
			if(previous != null)
			{
				throw new StorageExceptionConsistency(
					"Meta-record KIND 0x" + Long.toHexString(kind) + " already has a registered handler."
				);
			}
			return this;
		}

		@Override
		public final long dispatch(
			final StorageLiveDataFile.Default file      ,
			final ByteBuffer                  buffer    ,
			final long                        address   ,
			final long                        chunkStart
		)
		{
			// Corrupt-input guard: the envelope (LENGTH 8B + META_MARKER 2B + KIND 2B = 12B) must be fully
			// resident before we read the marker/KIND. An entry starting within fewer than 12 bytes of the
			// buffer end cannot be a meta record (reading its marker would run past the buffer), so treat it
			// as a legacy opaque gap. Only fires on a truncated/corrupted file.
			final long bufferBoundAddress = XMemory.getDirectByteBufferAddress(buffer) + buffer.limit();
			if(address + StorageMetaRecord.OFFSET_PAYLOAD > bufferBoundAddress)
			{
				return chunkStart;
			}

			if(!StorageMetaRecord.isMetaRecord(address))
			{
				return chunkStart; // legacy gap — chunk boundary unchanged
			}

			final long                     kind    = StorageMetaRecord.kindOf(address);
			final StorageMetaRecordHandler handler = this.handlersByKind.get(kind);
			if(handler != null)
			{
				return handler.onLoad(file, buffer, address, chunkStart);
			}

			// unknown KIND — forward-compat, reaction applied (and gated) by the reporter.
			this.reporter.onUnknownKind(file.number(), kind);
			return chunkStart;
		}

		@Override
		public final void onFileComplete(
			final StorageLiveDataFile.Default file             ,
			final long                        lastChunkOffset  ,
			final long                        fileContentLength,
			final boolean                     hasLiveData
		)
		{
			final long kind = file.chunkChecksumKind();
			if(kind == 0L)
			{
				// no FileHeaderV1 was parsed: a legacy/pre-feature file.
				if(hasLiveData)
				{
					this.reporter.onMissingHeader(file.number());
				}
			}
			else if(lastChunkOffset < fileContentLength)
			{
				// covered file, but content trails the last covering record: an uncovered tail.
				// NOTE: lastChunkOffset is the walker's chunkStart cursor, advanced past each header /
				// covering record by the handlers' return values, so this comparison is coupled to them —
				// a header-only file is "covered" only because the header handler advanced the cursor.
				this.reporter.onUncoveredData(file.number(), lastChunkOffset);
			}
			this.reporter.onFileWalkComplete(file.number());
		}

	}

}
