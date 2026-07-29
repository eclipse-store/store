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
 * Executes each channel's deferred durability-covered maintenance (head-file rollover, log
 * compaction, the file-cleanup pass at the configured file-check budget); see
 * {@link StorageChannel#executeDurabilityCoveredMaintenance()}.
 * <p>
 * Always enqueued by the broker directly behind a gate-requested
 * {@link StorageRequestTaskStorageFlush} under the same monitor hold (public on-demand barriers
 * stay pure): queue adjacency guarantees no store task is processed between the barrier's
 * watermark raise and this maintenance, so the durability gates provably pass for the
 * maintenance's whole duration - the property that makes the barrier the only reclamation slot
 * under sustained store traffic. Enqueued separately (instead of running inside the barrier
 * task) so issuers waiting on the barrier wake at pure file-synchronization latency and the two
 * concerns stay independent.
 * <p>
 * Self-guarded: if the barrier failed or was skipped (read-only), the watermark was not raised
 * and every gate inside the maintenance simply re-defers and re-arms its flush request - this
 * task then degrades to a cheap no-op. Fire-and-forget; a maintenance failure escalates to the
 * operation controller inside the channel's maintenance method itself.
 */
public interface StorageRequestTaskDurabilityMaintenance extends StorageRequestTask
{
	public final class Default
	extends StorageChannelSynchronizingTask.AbstractCompletingTask<Void>
	implements StorageRequestTaskDurabilityMaintenance
	{
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		Default(
			final long                       timestamp   ,
			final int                        channelCount,
			final StorageOperationController controller
		)
		{
			super(timestamp, channelCount, controller);
		}



		///////////////////////////////////////////////////////////////////////////
		// override methods //
		/////////////////////

		@Override
		protected final Void internalProcessBy(final StorageChannel channel)
		{
			channel.executeDurabilityCoveredMaintenance();
			return null;
		}

	}

}
