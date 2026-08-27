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

/**
 * All-channel durability barrier: every channel synchronizes its storage files (head data file,
 * then transactions log) during processing; on completion every store issued before this task's
 * timestamp is durable on every channel.
 * <p>
 * Each channel learns this from its own participation alone: on success it raises its local
 * all-durable watermark to this task's timestamp. The reasoning is the task queue's global order -
 * a store task with a smaller timestamp was processed by every channel before this one, so its
 * data and log entries were written before the synchronization.
 * <p>
 * Two issuers create these barriers ({@link #carriesMaintenance()} tells them apart): a channel
 * whose durability gate deferred a file lifecycle event (source-file deletion, head rollover,
 * log compaction; see {@code StorageFileManager}) requests a GATE barrier, and the public
 * {@code StorageConnection#issueStorageFlush()} requests a PURE on-demand barrier. Either way
 * this task ONLY synchronizes files and raises the watermark - the deferred maintenance the
 * watermark enables is a separate concern, handled by a
 * {@link StorageRequestTaskDurabilityMaintenance} the broker enqueues directly behind
 * gate-requested (or tail-upgraded) instances under the same monitor hold: queue adjacency
 * guarantees no store is processed between the watermark raise and the maintenance, so the
 * durability gates hold for the maintenance exactly as they do here. Issuers waiting on this
 * task wake at pure file-synchronization latency.
 * <p>
 * Failure surfacing differs per issuer: a gate-requested barrier is enqueued fire-and-forget
 * (a channel cannot wait on a barrier it must itself process without deadlocking), so a failed
 * synchronization has no caller to surface it - it escalates to the operation controller (see
 * {@link #fail}): the channel stops and the next request reports the fault, instead of the
 * barrier silently ceasing to raise the watermark. A public on-demand barrier additionally HAS a
 * waiting caller, which observes a skip via {@link #result()} returning {@code false} and a
 * failure via the task problem thrown from its wait; the {@link #fail} escalation applies to
 * both flavors alike.
 */
public interface StorageRequestTaskStorageFlush extends StorageRequestTask, StorageChannelSynchronizingTask
{
	/**
	 * Whether every channel actually synchronized its files. {@code false} if any channel
	 * skipped (e.g. read-only mode) or failed. Only meaningful after the task completed.
	 *
	 * @return whether the storage is durable through this task's timestamp.
	 */
	public boolean result();

	/**
	 * Whether this barrier has a {@link StorageRequestTaskDurabilityMaintenance} enqueued
	 * directly behind it. THE canonical statement of the barrier-flavor invariant:
	 * <p>
	 * Gate-requested barriers carry the maintenance ({@code true}) - the adjacent task is the
	 * slot where the durability gates' deferred work (source-file deletion, head rollover, log
	 * compaction) provably passes, and only such barriers may serve as the broker's coalescing
	 * target: a gate request coalescing onto a maintenance-less barrier would be swallowed
	 * without its deferred work ever running, starving reclamation under sustained store
	 * traffic. Public on-demand barriers are pure ({@code false}) - file synchronization and
	 * watermark raise only, never a coalescing target; the deferred work they incidentally
	 * enable is picked up when the gates next run.
	 * <p>
	 * Constructor-set intent, with one broker-side transition: a gate request finding a pure
	 * barrier at the queue tail appends the maintenance task behind it and
	 * {@linkplain #upgradeToCarryMaintenance() upgrades} the flag, so it stays truthful. In the
	 * broker's shutdown race the maintenance enqueue behind a gate barrier can fail, but such a
	 * barrier is then never registered as a coalescing target, so this flag is never consulted
	 * for it.
	 *
	 * @return whether a durability-maintenance task is enqueued directly behind this barrier.
	 */
	public boolean carriesMaintenance();

	/**
	 * Broker-internal: records that a durability-maintenance task was appended directly behind
	 * this (previously pure) barrier while it was the queue tail, making
	 * {@link #carriesMaintenance()} true in fact and in flag. Must only be called under the
	 * task broker's monitor, which also guards every read of the flag.
	 */
	public void upgradeToCarryMaintenance();


	public final class Default
	extends StorageChannelSynchronizingTask.AbstractCompletingTask<Boolean>
	implements StorageRequestTaskStorageFlush
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////

		private final ChannelResults flushed           ;
		private       boolean        carriesMaintenance;



		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		Default(
			final long                       timestamp         ,
			final int                        channelCount      ,
			final StorageOperationController controller        ,
			final boolean                    carriesMaintenance
		)
		{
			super(timestamp, channelCount, controller);
			this.flushed            = new ChannelResults(channelCount);
			this.carriesMaintenance = carriesMaintenance;
		}



		///////////////////////////////////////////////////////////////////////////
		// override methods //
		/////////////////////

		@Override
		protected final Boolean internalProcessBy(final StorageChannel channel)
		{
			final boolean flushed = channel.flushStorage();
			// record every channel's result BEFORE the processing barrier, so succeed() (which runs
			// only after waitOnProcessing) can consult the all-channel outcome, not just this channel's.
			this.flushed.set(channel.channelIndex(), flushed);
			return flushed;
		}

		@Override
		public final boolean result()
		{
			return this.flushed.allTrue();
		}

		@Override
		public final boolean carriesMaintenance()
		{
			return this.carriesMaintenance;
		}

		@Override
		public final void upgradeToCarryMaintenance()
		{
			this.carriesMaintenance = true;
		}

		@Override
		protected final void succeed(final StorageChannel channel, final Boolean flushed)
		{
			// The all-durable watermark means "durable on EVERY channel", so raise it only when the
			// whole barrier synchronized: a channel skipping (read-only toggled mid-barrier) must not
			// let others mark stores durable it never fsynced. A skipped channel re-armed, so the
			// barrier retries once all are writable. allTrue() is safe here - waitOnProcessing has
			// passed, so every result is set and visible via the same monitor.
			if(this.flushed.allTrue())
			{
				channel.commitStorageFlush(this.timestamp());
			}
		}

		@Override
		protected final void fail(final StorageChannel channel, final Boolean result)
		{
			// A durability barrier that cannot fsync is unrecoverable in place and has no waiting caller.
			// Escalate the channel's cause to the operation controller (as a broken log compaction does)
			// so processing stops and the next request surfaces the fault instead of silent degradation.
			final Throwable cause = this.problemForChannel(channel);
			// An InterruptedException is an orderly stop signal (an external thread interrupting a
			// channel mid-barrier), not an fsync failure - do not escalate it to a storage disruption.
			if(cause != null && !(cause instanceof InterruptedException))
			{
				this.controller.registerDisruption(cause);
				this.controller.setChannelProcessingEnabled(false);
			}
		}

	}

}
