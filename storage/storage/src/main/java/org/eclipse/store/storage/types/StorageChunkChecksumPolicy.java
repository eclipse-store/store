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

import org.eclipse.serializer.typing.Immutable;

/**
 * Policy governing the per-chunk checksum meta records. Two orthogonal capability axes &mdash; {@link #emit()}
 * (write {@code FileHeaderV1} / chunk-checksum records) and {@link #verify()} (recompute and check on load)
 * &mdash; plus one uniform {@link Reaction} per load-time {@link Anomaly}, and two coverage options
 * ({@link #requireCoverage()}, {@link #continuousCoverage()}).
 * <p>
 * Every load-time anomaly (a checksum mismatch, a chunk-boundary mismatch, an unrecognized record kind, a
 * missing file header, uncovered data) is an {@link Anomaly} answered by a {@link Reaction}: {@code IGNORE}
 * (skip silently), {@code LOG} (warn and
 * continue) or {@code FAIL} (throw and abort the load). Reactions are consulted <b>only when {@link #verify()}
 * is true</b> &mdash; "not verifying" reacts to nothing, so an off policy cannot surprise.
 * <p>
 * Pick a profile rather than assembling the axes by hand:
 * <ul>
 *   <li>{@link #New()} &mdash; lenient default: emit + verify, {@code FAIL} on corruption, everything else
 *       {@code IGNORE} (forward-compatible).</li>
 *   <li>{@link #NewOff()} &mdash; emit nothing, verify nothing; files indistinguishable from a pre-feature
 *       engine.</li>
 *   <li>{@link #NewObserve()} &mdash; emit + verify, all anomalies {@code LOG}; learns about every anomaly via
 *       logs without ever refusing to start.</li>
 *   <li>{@link #NewStrict()} &mdash; emit + verify, all anomalies {@code FAIL}, full coverage required;
 *       zero-tolerance, for a fully-migrated store.</li>
 *   <li>{@link #NewStrictTolerateLegacy()} &mdash; like {@link #NewStrict()} but pre-existing legacy coverage
 *       gaps are {@code LOG}ged, not fatal; the migrating-to-strict profile.</li>
 *   <li>{@link #NewCustom(boolean, boolean, Reaction, Reaction, Reaction, Reaction, boolean, boolean)} &mdash;
 *       full control, with fail-early validation.</li>
 * </ul>
 * The hash algorithm and the emitted chunk-checksum KIND live on {@link StorageChunkChecksumProvider} (which
 * carries a policy), not on this value object.
 *
 * @see #New()
 * @see Anomaly
 * @see Reaction
 */
public interface StorageChunkChecksumPolicy
{
	/**
	 * @return whether to emit {@code FileHeaderV1} and chunk-checksum records on write.
	 */
	public boolean emit();

	/**
	 * @return whether to recompute and check chunk-checksum records on load.
	 */
	public boolean verify();

	/**
	 * @return whether the load walk treats a whole unprotected file ({@link Anomaly#MISSING_HEADER}, a legacy
	 *         file with no {@code FileHeaderV1}) as an anomaly. Only meaningful when {@link #verify()} is true.
	 *         Does <b>not</b> gate {@link Anomaly#UNCOVERED_DATA}: an uncovered tail on a covered file is
	 *         corruption, raised whenever {@link #verify()} is true.
	 */
	public boolean requireCoverage();

	/**
	 * @return whether enabling emit forces a fresh, covered head file at init so new writes are protected
	 *         immediately instead of after the next rollover. Only meaningful when {@link #emit()} is true.
	 */
	public boolean continuousCoverage();

	/**
	 * The reaction to a given load-time {@link Anomaly}. Consulted only when {@link #verify()} is true (and,
	 * for {@link Anomaly#MISSING_HEADER}, only when {@link #requireCoverage()} is also true).
	 *
	 * @param anomaly the anomaly category; must be non-{@code null}.
	 * @return the configured {@link Reaction}.
	 */
	public Reaction reactionTo(Anomaly anomaly);



	/**
	 * The load-time anomaly categories a chunk-checksum walk can encounter.
	 */
	public enum Anomaly
	{
		/** A recomputed checksum did not match the stored one &mdash; corruption or tampering. */
		CHECKSUM_MISMATCH,
		/** A covering record's stored chunk start disagreed with the boundary the load walk reconstructed: a
		 *  framing desync (a corrupted entity LENGTH rerouted the walk past a record), not content bit-rot. */
		CHUNK_BOUNDARY_MISMATCH,
		/** A meta-record KIND with no registered handler (a removed or future record kind). */
		UNKNOWN_KIND,
		/** A file carries live data but no {@code FileHeaderV1} while coverage was expected (legacy file). */
		MISSING_HEADER,
		/** A covered file holds live content past its last covering record. A covered file always ends at a
		 *  covering record after recovery, so this is corruption (typically an overshoot of the final record to
		 *  EOF). Raised whenever verifying (not gated by requireCoverage). */
		UNCOVERED_DATA
	}

	/**
	 * What to do when an {@link Anomaly} is encountered on load.
	 */
	public enum Reaction
	{
		/** Skip silently, no trace. */
		IGNORE,
		/** Log (warn) and continue; deduped per file + kind by the reporter. */
		LOG,
		/** Throw and abort the load on the first occurrence. */
		FAIL
	}



	/**
	 * @return the framework-default (lenient) policy: emit + verify, {@code FAIL} on the corruption-class
	 *         anomalies (checksum mismatch, chunk-boundary mismatch, uncovered tail), {@code IGNORE} the
	 *         forward-compatible ones (unknown kinds and legacy files / missing headers are tolerated).
	 */
	public static StorageChunkChecksumPolicy New()
	{
		return NewCustom(
			true, true, Reaction.FAIL, Reaction.FAIL, Reaction.IGNORE, Reaction.IGNORE, Reaction.FAIL, false, false
		);
	}

	/**
	 * @return a "no checksum" policy: emit nothing, verify nothing; data files are indistinguishable from
	 *         those of a pre-feature engine.
	 */
	public static StorageChunkChecksumPolicy NewOff()
	{
		return NewCustom(
			false, false,
			Reaction.IGNORE, Reaction.IGNORE, Reaction.IGNORE, Reaction.IGNORE, Reaction.IGNORE,
			false, false
		);
	}

	/**
	 * @return an audit policy: emit + verify, every anomaly {@code LOG} (never {@code FAIL}); learns about
	 *         corruption / unknown kinds / coverage gaps via logs without risking a store that won't start.
	 */
	public static StorageChunkChecksumPolicy NewObserve()
	{
		return NewCustom(
			true, true, Reaction.LOG, Reaction.LOG, Reaction.LOG, Reaction.LOG, Reaction.LOG, true, false
		);
	}

	/**
	 * @return a zero-tolerance policy: emit + verify, every anomaly {@code FAIL}, full coverage required and
	 *         continuous. Use once a store is fully migrated to assert total coverage.
	 */
	public static StorageChunkChecksumPolicy NewStrict()
	{
		return NewCustom(
			true, true, Reaction.FAIL, Reaction.FAIL, Reaction.FAIL, Reaction.FAIL, Reaction.FAIL, true, true
		);
	}

	/**
	 * @return a strict policy that tolerates pre-existing legacy files: {@code FAIL} on corruption
	 *         ({@link Anomaly#CHECKSUM_MISMATCH}) and unknown kinds ({@link Anomaly#UNKNOWN_KIND}), but
	 *         {@code LOG} (not fail) on coverage gaps ({@link Anomaly#MISSING_HEADER} /
	 *         {@link Anomaly#UNCOVERED_DATA}). With {@link #continuousCoverage()} on, new writes are still
	 *         fully covered &mdash; the migrating-to-strict profile.
	 */
	public static StorageChunkChecksumPolicy NewStrictTolerateLegacy()
	{
		return NewCustom(
			true, true, Reaction.FAIL, Reaction.FAIL, Reaction.FAIL, Reaction.LOG, Reaction.LOG, true, true
		);
	}

	/**
	 * Creates a fully specified policy, with fail-early validation: no constructed policy may carry dead or
	 * contradictory configuration.
	 * <ul>
	 *   <li>if {@code verify} is false, every reaction must be {@code IGNORE} (a reaction is gated behind
	 *       verify, so a non-{@code IGNORE} reaction could never fire);</li>
	 *   <li>{@code requireCoverage} requires {@code verify};</li>
	 *   <li>{@code continuousCoverage} requires {@code emit}.</li>
	 * </ul>
	 *
	 * @param emit                     whether to write checksum records.
	 * @param verify                   whether to recompute and check on load.
	 * @param onChecksumMismatch       reaction to a checksum mismatch; must be non-{@code null}.
	 * @param onChunkBoundaryMismatch  reaction to a chunk-boundary mismatch (framing desync); must be non-{@code null}.
	 * @param onUnknownKind            reaction to an unknown record KIND; must be non-{@code null}.
	 * @param onMissingHeader          reaction to a missing file header; must be non-{@code null}.
	 * @param onUncoveredData          reaction to uncovered data; must be non-{@code null}.
	 * @param requireCoverage          whether missing/uncovered are raised as anomalies.
	 * @param continuousCoverage       whether enabling emit forces an immediately-covered head file.
	 * @return a new {@link StorageChunkChecksumPolicy}.
	 * @throws IllegalArgumentException on a dead or contradictory combination (see above).
	 */
	public static StorageChunkChecksumPolicy NewCustom(
		final boolean  emit                    ,
		final boolean  verify                  ,
		final Reaction onChecksumMismatch      ,
		final Reaction onChunkBoundaryMismatch ,
		final Reaction onUnknownKind           ,
		final Reaction onMissingHeader         ,
		final Reaction onUncoveredData         ,
		final boolean  requireCoverage         ,
		final boolean  continuousCoverage
	)
	{
		notNull(onChecksumMismatch)     ;
		notNull(onChunkBoundaryMismatch);
		notNull(onUnknownKind)          ;
		notNull(onMissingHeader)        ;
		notNull(onUncoveredData)        ;

		// no dead reactions: a reaction that can never fire is a configuration error, not a silent no-op.
		if(!verify && (onChecksumMismatch      != Reaction.IGNORE
			||         onChunkBoundaryMismatch != Reaction.IGNORE
			||         onUnknownKind           != Reaction.IGNORE
			||         onMissingHeader         != Reaction.IGNORE
			||         onUncoveredData         != Reaction.IGNORE))
		{
			throw new IllegalArgumentException(
				"verify=false requires every reaction to be IGNORE (reactions are gated behind verify); "
				+ "use NewOff() or enable verify."
			);
		}
		if(requireCoverage && !verify)
		{
			throw new IllegalArgumentException("requireCoverage requires verify.");
		}
		if(continuousCoverage && !emit)
		{
			throw new IllegalArgumentException("continuousCoverage requires emit.");
		}

		return new Default(
			emit                    ,
			verify                  ,
			onChecksumMismatch      ,
			onChunkBoundaryMismatch ,
			onUnknownKind           ,
			onMissingHeader         ,
			onUncoveredData         ,
			requireCoverage         ,
			continuousCoverage
		);
	}



	public final class Default implements StorageChunkChecksumPolicy, Immutable
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////

		private final boolean  emit                    ;
		private final boolean  verify                  ;
		private final Reaction onChecksumMismatch      ;
		private final Reaction onChunkBoundaryMismatch ;
		private final Reaction onUnknownKind           ;
		private final Reaction onMissingHeader         ;
		private final Reaction onUncoveredData         ;
		private final boolean  requireCoverage         ;
		private final boolean  continuousCoverage      ;



		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		Default(
			final boolean  emit                    ,
			final boolean  verify                  ,
			final Reaction onChecksumMismatch      ,
			final Reaction onChunkBoundaryMismatch ,
			final Reaction onUnknownKind           ,
			final Reaction onMissingHeader         ,
			final Reaction onUncoveredData         ,
			final boolean  requireCoverage         ,
			final boolean  continuousCoverage
		)
		{
			super();
			this.emit                    = emit                   ;
			this.verify                  = verify                 ;
			this.onChecksumMismatch      = onChecksumMismatch     ;
			this.onChunkBoundaryMismatch = onChunkBoundaryMismatch;
			this.onUnknownKind           = onUnknownKind          ;
			this.onMissingHeader         = onMissingHeader        ;
			this.onUncoveredData         = onUncoveredData        ;
			this.requireCoverage         = requireCoverage        ;
			this.continuousCoverage      = continuousCoverage     ;
		}



		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////

		@Override
		public boolean emit()
		{
			return this.emit;
		}

		@Override
		public boolean verify()
		{
			return this.verify;
		}

		@Override
		public boolean requireCoverage()
		{
			return this.requireCoverage;
		}

		@Override
		public boolean continuousCoverage()
		{
			return this.continuousCoverage;
		}

		@Override
		public Reaction reactionTo(final Anomaly anomaly)
		{
			switch(notNull(anomaly))
			{
				case CHECKSUM_MISMATCH:       return this.onChecksumMismatch     ;
				case CHUNK_BOUNDARY_MISMATCH: return this.onChunkBoundaryMismatch;
				case UNKNOWN_KIND:            return this.onUnknownKind          ;
				case MISSING_HEADER:          return this.onMissingHeader        ;
				case UNCOVERED_DATA:          return this.onUncoveredData        ;
				default: throw new IllegalArgumentException("Unhandled anomaly: " + anomaly);
			}
		}

		@Override
		public String toString()
		{
			return this.getClass().getName()
				+ " [emit=" + this.emit
				+ ", verify=" + this.verify
				+ ", onChecksumMismatch=" + this.onChecksumMismatch
				+ ", onChunkBoundaryMismatch=" + this.onChunkBoundaryMismatch
				+ ", onUnknownKind=" + this.onUnknownKind
				+ ", onMissingHeader=" + this.onMissingHeader
				+ ", onUncoveredData=" + this.onUncoveredData
				+ ", requireCoverage=" + this.requireCoverage
				+ ", continuousCoverage=" + this.continuousCoverage + "]"
			;
		}

	}

}
