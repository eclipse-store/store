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

import org.eclipse.serializer.chars.VarString;
import org.eclipse.serializer.collections.BulkList;
import org.eclipse.serializer.collections.types.XGettingSequence;

/**
 * Aggregated result of an on-demand storage integrity check (see
 * {@link StorageConnection#issueFullIntegrityCheck()} / {@link StorageConnection#issueIntegrityCheck(long)}).
 * <p>
 * Collects every detected {@link Anomaly} rather than throwing, so one call surfaces all corruption across
 * all channels. The chunk-checksum anomalies are routed through the configured
 * {@link StorageChunkChecksumPolicy} ({@code IGNORE} ones are not collected; {@code LOG} / {@code FAIL} ones
 * are); {@link Anomaly#FILE_SIZE_CHANGED} is detected by the check itself and is always reported.
 * {@link #isComplete()} is {@code false} when a budgeted call ran out of time and carries only that call's
 * findings; repeat until complete (the full check always completes in one call).
 */
public interface StorageIntegrityCheckResult
{
	/**
	 * The categories of anomaly an integrity check can report. The first five mirror the load-time
	 * {@link StorageChunkChecksumPolicy.Anomaly} categories (raised via the configured policy); the last is
	 * specific to the on-demand check.
	 */
	public enum Anomaly
	{
		/** A recomputed checksum did not match the stored one. */
		CHECKSUM_MISMATCH,
		/** A covering record's stored chunk start disagreed with the boundary the walk reconstructed. */
		CHUNK_BOUNDARY_MISMATCH,
		/** A meta-record KIND with no registered handler. */
		UNKNOWN_KIND,
		/** A file carries live data but no {@code FileHeaderV1} while coverage was expected. */
		MISSING_HEADER,
		/** A covered file holds live content past its last covering record. */
		UNCOVERED_DATA,
		/** A sealed (non-head) data file's size changed since the scan began — a sealed file is immutable, so any
		 *  change (truncation, growth, rewrite) is corruption/tampering. The head file is exempt (it grows).
		 *  Detected by the check itself and always reported, regardless of the configured policy. */
		FILE_SIZE_CHANGED
	}

	/**
	 * @return {@code true} if no anomalies were collected.
	 */
	public boolean isClean();

	/**
	 * @return {@code true} if the scan covered all in-scope files (it was not cut short by a time budget).
	 */
	public boolean isComplete();

	/**
	 * @return the number of collected anomalies.
	 */
	public long anomalyCount();

	/**
	 * @return an immutable view of the collected anomalies.
	 */
	public XGettingSequence<Finding> anomalies();



	/**
	 * One detected anomaly: which channel and data file it occurred in, where, its kind, and &mdash; for a
	 * checksum mismatch &mdash; the stored vs recomputed checksum bytes. Pure data carrier.
	 */
	public final class Finding
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////

		private final int     channelIndex;
		private final long    fileNumber  ;
		private final long    position    ;
		private final Anomaly anomaly     ;
		private final long    kind        ;
		private final byte[]  expected    ;
		private final byte[]  actual      ;



		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		Finding(
			final int     channelIndex,
			final long    fileNumber  ,
			final long    position    ,
			final Anomaly anomaly     ,
			final long    kind        ,
			final byte[]  expected    ,
			final byte[]  actual
		)
		{
			super();
			this.channelIndex = channelIndex;
			this.fileNumber   = fileNumber  ;
			this.position     = position    ;
			this.anomaly      = anomaly     ;
			this.kind         = kind        ;
			this.expected     = expected    ;
			this.actual       = actual      ;
		}



		///////////////////////////////////////////////////////////////////////////
		// getters //
		////////////

		public int channelIndex()
		{
			return this.channelIndex;
		}

		public long fileNumber()
		{
			return this.fileNumber;
		}

		public long position()
		{
			return this.position;
		}

		public Anomaly anomaly()
		{
			return this.anomaly;
		}

		public long kind()
		{
			return this.kind;
		}

		/**
		 * @return the stored checksum bytes for a {@link Anomaly#CHECKSUM_MISMATCH}; {@code null} otherwise.
		 */
		public byte[] expected()
		{
			return this.expected;
		}

		/**
		 * @return the recomputed checksum bytes for a {@link Anomaly#CHECKSUM_MISMATCH}; {@code null} otherwise.
		 */
		public byte[] actual()
		{
			return this.actual;
		}

		@Override
		public String toString()
		{
			final VarString vs = VarString.New()
				.add(this.anomaly).add(" in channel #").add(this.channelIndex)
				.add(" data file #").add(this.fileNumber).add(" at offset ").add(this.position)
			;
			if(this.kind != 0L)
			{
				vs.add(" (kind 0x").add(Long.toHexString(this.kind)).add(')');
			}
			if(this.expected != null && this.actual != null)
			{
				vs.add(": expected ").addHexDec(this.expected).add(", got ").addHexDec(this.actual);
			}
			return vs.toString();
		}
	}



	public static StorageIntegrityCheckResult.Default New()
	{
		return new StorageIntegrityCheckResult.Default();
	}



	public final class Default implements StorageIntegrityCheckResult
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////

		private final BulkList<Finding> findings = BulkList.New();
		private       boolean           complete = true          ;



		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		Default()
		{
			super();
		}



		///////////////////////////////////////////////////////////////////////////
		// declared methods //
		/////////////////////

		final void add(final Finding finding)
		{
			this.findings.add(finding);
		}

		final void setComplete(final boolean complete)
		{
			this.complete = complete;
		}

		/**
		 * Folds a per-channel result into this aggregate: appends its findings, {@code AND}s its completion flag.
		 * Synchronized because each channel merges from its own thread; reads {@code other.findings} directly to
		 * avoid the defensive copy {@link #anomalies()} would make.
		 */
		final synchronized void merge(final StorageIntegrityCheckResult.Default other)
		{
			this.findings.addAll(other.findings);
			this.complete &= other.complete;
		}



		///////////////////////////////////////////////////////////////////////////
		// override methods //
		/////////////////////

		@Override
		public final boolean isClean()
		{
			return this.findings.isEmpty();
		}

		@Override
		public final boolean isComplete()
		{
			return this.complete;
		}

		@Override
		public final long anomalyCount()
		{
			return this.findings.size();
		}

		@Override
		public final XGettingSequence<Finding> anomalies()
		{
			return this.findings.immure();
		}

		@Override
		public final String toString()
		{
			final VarString vs = VarString.New()
				.add("StorageIntegrityCheckResult [")
				.add(this.isClean() ? "clean" : this.findings.size() + " anomalies")
				.add(this.complete ? "" : ", incomplete")
				.add(']')
			;
			for(final Finding finding : this.findings)
			{
				vs.lf().tab().add(finding);
			}
			return vs.toString();
		}

	}

}