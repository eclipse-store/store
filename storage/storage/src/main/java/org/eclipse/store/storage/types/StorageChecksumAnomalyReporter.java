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

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.eclipse.serializer.util.logging.Logging;
import org.eclipse.store.storage.exceptions.StorageExceptionChunkBoundaryMismatch;
import org.eclipse.store.storage.exceptions.StorageExceptionChunkChecksumMismatch;
import org.eclipse.store.storage.exceptions.StorageExceptionConsistency;
import org.eclipse.store.storage.types.StorageChunkChecksumPolicy.Anomaly;
import org.eclipse.store.storage.types.StorageChunkChecksumPolicy.Reaction;
import org.slf4j.Logger;

/**
 * Per-channel sink that applies the {@link StorageChunkChecksumPolicy}'s {@link Reaction} to each load-time
 * {@link Anomaly}:
 * <ul>
 *   <li>gates behind {@link StorageChunkChecksumPolicy#verify()} (and, for {@link Anomaly#MISSING_HEADER},
 *       additionally behind {@link StorageChunkChecksumPolicy#requireCoverage()}) &mdash; "not verifying"
 *       reacts to nothing;</li>
 *   <li>{@code IGNORE}: returns; {@code FAIL}: throws the anomaly's typed exception on the first occurrence;
 *       {@code LOG}: warns once per {@code (file, anomaly, kind)} and counts the rest, surfaced as a
 *       "+N more" summary at {@link #onFileWalkComplete(long)};</li>
 *   <li>builds the failure exception and the log message <i>lazily</i>.</li>
 * </ul>
 * One instance per storage channel; the dedupe state needs no synchronization because the load walk is
 * single-threaded per channel (matching the {@link StorageMetaRecordRegistry} contract).
 */
public interface StorageChecksumAnomalyReporter
{
	/**
	 * Reports a chunk-checksum mismatch (recompute &ne; stored) at {@code offset} in file {@code fileNumber}.
	 * No-op unless verifying; throws {@link StorageExceptionChunkChecksumMismatch} when the reaction is FAIL.
	 */
	public void onChecksumMismatch(long fileNumber, long offset, long kind, byte[] expected, byte[] actual);

	/**
	 * Reports a chunk-boundary mismatch: a covering record's stored chunk start ({@code expectedChunkStart})
	 * disagrees with the boundary the load walk reconstructed ({@code actualChunkStart}) &mdash; a framing
	 * desync. No-op unless verifying; throws {@link StorageExceptionChunkBoundaryMismatch} when the reaction is FAIL.
	 */
	public void onChunkBoundaryMismatch(long fileNumber, long recordOffset, long expectedChunkStart, long actualChunkStart);

	/**
	 * Reports a meta record whose KIND has no registered handler. No-op unless verifying; throws
	 * {@link StorageExceptionConsistency} when the reaction is FAIL.
	 */
	public void onUnknownKind(long fileNumber, long kind);

	/**
	 * Reports a file that carries data but no {@code FileHeaderV1}. No-op unless verifying and coverage is
	 * required; throws {@link StorageExceptionConsistency} when the reaction is FAIL.
	 */
	public void onMissingHeader(long fileNumber);

	/**
	 * Reports a covered file holding live content past its last chunk-checksum record (uncovered tail). Unlike
	 * {@link #onMissingHeader(long)} this is <b>not</b> gated behind {@link StorageChunkChecksumPolicy#requireCoverage()}
	 * &mdash; an uncovered tail on a covered file is corruption, not a legacy state. No-op unless verifying; throws
	 * {@link StorageExceptionConsistency} when the reaction is FAIL.
	 */
	public void onUncoveredData(long fileNumber, long offset);

	/**
	 * Called by the walker after each file: emits any "+N more" summaries for {@code LOG}ged anomalies and
	 * clears that file's dedupe state.
	 */
	public void onFileWalkComplete(long fileNumber);



	public static StorageChecksumAnomalyReporter New(final StorageChunkChecksumPolicy policy)
	{
		return new Default(notNull(policy));
	}

	/**
	 * Creates a non-throwing reporter for the on-demand integrity check: each non-{@code IGNORE} anomaly is
	 * recorded into {@code sink} (tagged with {@code channelIndex}) instead of thrown or logged. The same
	 * verify / requireCoverage gates as {@link Default} apply, so a verify-off policy collects nothing.
	 *
	 * @param policy       the configured policy (routes IGNORE vs collect); must be non-{@code null}.
	 * @param channelIndex the channel this reporter collects for.
	 * @param sink         the per-channel result to record findings into; must be non-{@code null}.
	 * @return a new collecting {@link StorageChecksumAnomalyReporter}.
	 */
	public static StorageChecksumAnomalyReporter NewCollecting(
		final StorageChunkChecksumPolicy        policy      ,
		final int                               channelIndex,
		final StorageIntegrityCheckResult.Default sink
	)
	{
		return new Collecting(notNull(policy), channelIndex, notNull(sink));
	}



	public final class Default implements StorageChecksumAnomalyReporter
	{
		///////////////////////////////////////////////////////////////////////////
		// constants //
		//////////////

		private final static Logger logger = Logging.getLogger(StorageChecksumAnomalyReporter.class);



		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////

		private final StorageChunkChecksumPolicy policy     ;
		private       long                       currentFile = -1L;
		private final HashMap<Long, long[]>      logCounts   = new HashMap<>(); // dedupe key -> {count}



		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		Default(final StorageChunkChecksumPolicy policy)
		{
			super();
			this.policy = policy;
		}



		///////////////////////////////////////////////////////////////////////////
		// declared methods //
		/////////////////////

		private void apply(
			final Anomaly                              anomaly   ,
			final long                                 fileNumber,
			final long                                 kind      ,
			final Supplier<? extends RuntimeException> failure   ,
			final Supplier<String>                     logMessage
		)
		{
			final Reaction reaction = this.policy.reactionTo(anomaly);
			if(reaction == Reaction.IGNORE)
			{
				return;
			}
			if(reaction == Reaction.FAIL)
			{
				throw failure.get(); // first occurrence — fail early, no accumulation
			}

			// LOG, deduped per (file, anomaly, kind). KIND fits 16 bits (enforced at
			// StorageMetaRecordRegistry.register), so a 16-bit shift keeps the ordinal clear of the KIND.
			this.rollFileIfChanged(fileNumber);
			final long   key   = (kind << 16) | anomaly.ordinal();
			final long[] count = this.logCounts.get(key);
			if(count == null)
			{
				this.logCounts.put(key, new long[]{1L});
				logger.warn(logMessage.get());
			}
			else
			{
				count[0]++; // suppressed; surfaced as "+N more" at onFileWalkComplete
			}
		}

		private void rollFileIfChanged(final long fileNumber)
		{
			if(fileNumber != this.currentFile)
			{
				this.logCounts.clear();
				this.currentFile = fileNumber;
			}
		}



		///////////////////////////////////////////////////////////////////////////
		// override methods //
		/////////////////////

		@Override
		public final void onChecksumMismatch(
			final long   fileNumber,
			final long   offset    ,
			final long   kind      ,
			final byte[] expected  ,
			final byte[] actual
		)
		{
			if(!this.policy.verify())
			{
				return;
			}
			this.apply(
				Anomaly.CHECKSUM_MISMATCH, fileNumber, kind,
				() -> new StorageExceptionChunkChecksumMismatch(fileNumber, offset, expected, actual),
				() -> "Chunk-checksum mismatch in data file #" + fileNumber + " at offset " + offset
			);
		}

		@Override
		public final void onChunkBoundaryMismatch(
			final long fileNumber        ,
			final long recordOffset      ,
			final long expectedChunkStart,
			final long actualChunkStart
		)
		{
			if(!this.policy.verify())
			{
				return;
			}
			this.apply(
				Anomaly.CHUNK_BOUNDARY_MISMATCH, fileNumber, 0L,
				() -> new StorageExceptionChunkBoundaryMismatch(
					fileNumber, recordOffset, expectedChunkStart, actualChunkStart),
				() -> "Chunk-boundary mismatch in data file #" + fileNumber + " at record offset " + recordOffset
					+ " (record covers chunk start " + expectedChunkStart + ", walk reconstructed " + actualChunkStart + ")"
			);
		}

		@Override
		public final void onUnknownKind(final long fileNumber, final long kind)
		{
			if(!this.policy.verify())
			{
				return;
			}
			this.apply(
				Anomaly.UNKNOWN_KIND, fileNumber, kind,
				() -> new StorageExceptionConsistency(
					"Unknown meta-record KIND 0x" + Long.toHexString(kind) + " in data file #" + fileNumber),
				() -> "Skipping unknown meta-record KIND 0x" + Long.toHexString(kind)
					+ " in data file #" + fileNumber + " (forward-compat)."
			);
		}

		@Override
		public final void onMissingHeader(final long fileNumber)
		{
			if(!this.policy.verify() || !this.policy.requireCoverage())
			{
				return;
			}
			this.apply(
				Anomaly.MISSING_HEADER, fileNumber, 0L,
				() -> new StorageExceptionConsistency(
					"Missing FileHeaderV1 (unprotected legacy file) in data file #" + fileNumber),
				() -> "Unprotected legacy file (no FileHeaderV1): data file #" + fileNumber
			);
		}

		@Override
		public final void onUncoveredData(final long fileNumber, final long offset)
		{
			// Verify-only gate (not requireCoverage, unlike onMissingHeader): a covered file always ends at a
			// covering record after recovery, so a trailing uncovered region is corruption, not a legacy state.
			if(!this.policy.verify())
			{
				return;
			}
			this.apply(
				Anomaly.UNCOVERED_DATA, fileNumber, 0L,
				() -> new StorageExceptionConsistency(
					"Uncovered data (no chunk-checksum record) in data file #" + fileNumber + " at offset " + offset),
				() -> "Uncovered data (no chunk-checksum record) in data file #" + fileNumber + " at offset " + offset
			);
		}

		@Override
		public final void onFileWalkComplete(final long fileNumber)
		{
			if(fileNumber != this.currentFile)
			{
				return;
			}
			for(final Map.Entry<Long, long[]> e : this.logCounts.entrySet())
			{
				final long extra = e.getValue()[0] - 1L;
				if(extra > 0L)
				{
					logger.warn("... and {} more suppressed occurrence(s) in data file #{}.", extra, fileNumber);
				}
			}
			this.logCounts.clear();
			this.currentFile = -1L;
		}

	}



	/**
	 * Non-throwing reporter for the on-demand integrity check: same gates and IGNORE routing as {@link Default},
	 * but records every non-ignored anomaly into a per-channel {@link StorageIntegrityCheckResult} instead of
	 * throwing on FAIL or logging on LOG.
	 */
	public final class Collecting implements StorageChecksumAnomalyReporter
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////

		private final StorageChunkChecksumPolicy          policy      ;
		private final int                                 channelIndex;
		private final StorageIntegrityCheckResult.Default sink        ;



		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		Collecting(
			final StorageChunkChecksumPolicy          policy      ,
			final int                                 channelIndex,
			final StorageIntegrityCheckResult.Default sink
		)
		{
			super();
			this.policy       = policy      ;
			this.channelIndex = channelIndex;
			this.sink         = sink        ;
		}



		///////////////////////////////////////////////////////////////////////////
		// declared methods //
		/////////////////////

		private boolean ignores(final Anomaly anomaly)
		{
			return this.policy.reactionTo(anomaly) == Reaction.IGNORE;
		}



		///////////////////////////////////////////////////////////////////////////
		// override methods //
		/////////////////////

		@Override
		public final void onChecksumMismatch(
			final long   fileNumber,
			final long   offset    ,
			final long   kind      ,
			final byte[] expected  ,
			final byte[] actual
		)
		{
			if(!this.policy.verify() || this.ignores(Anomaly.CHECKSUM_MISMATCH))
			{
				return;
			}
			this.sink.add(new StorageIntegrityCheckResult.Finding(
				this.channelIndex, fileNumber, offset,
				StorageIntegrityCheckResult.Anomaly.CHECKSUM_MISMATCH, kind, expected, actual
			));
		}

		@Override
		public final void onChunkBoundaryMismatch(
			final long fileNumber        ,
			final long recordOffset      ,
			final long expectedChunkStart,
			final long actualChunkStart
		)
		{
			if(!this.policy.verify() || this.ignores(Anomaly.CHUNK_BOUNDARY_MISMATCH))
			{
				return;
			}
			this.sink.add(new StorageIntegrityCheckResult.Finding(
				this.channelIndex, fileNumber, recordOffset,
				StorageIntegrityCheckResult.Anomaly.CHUNK_BOUNDARY_MISMATCH, 0L, null, null
			));
		}

		@Override
		public final void onUnknownKind(final long fileNumber, final long kind)
		{
			if(!this.policy.verify() || this.ignores(Anomaly.UNKNOWN_KIND))
			{
				return;
			}
			this.sink.add(new StorageIntegrityCheckResult.Finding(
				this.channelIndex, fileNumber, -1L,
				StorageIntegrityCheckResult.Anomaly.UNKNOWN_KIND, kind, null, null
			));
		}

		@Override
		public final void onMissingHeader(final long fileNumber)
		{
			if(!this.policy.verify() || !this.policy.requireCoverage() || this.ignores(Anomaly.MISSING_HEADER))
			{
				return;
			}
			this.sink.add(new StorageIntegrityCheckResult.Finding(
				this.channelIndex, fileNumber, -1L,
				StorageIntegrityCheckResult.Anomaly.MISSING_HEADER, 0L, null, null
			));
		}

		@Override
		public final void onUncoveredData(final long fileNumber, final long offset)
		{
			if(!this.policy.verify() || this.ignores(Anomaly.UNCOVERED_DATA))
			{
				return;
			}
			this.sink.add(new StorageIntegrityCheckResult.Finding(
				this.channelIndex, fileNumber, offset,
				StorageIntegrityCheckResult.Anomaly.UNCOVERED_DATA, 0L, null, null
			));
		}

		@Override
		public final void onFileWalkComplete(final long fileNumber)
		{
			// no dedupe state to flush in collecting mode.
		}

	}

}
