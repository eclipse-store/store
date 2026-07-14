package org.eclipse.store.storage.types;

/*-
 * #%L
 * EclipseStore Storage
 * %%
 * Copyright (C) 2023 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import org.eclipse.serializer.persistence.binary.types.ChunksBuffer;
import org.eclipse.store.storage.exceptions.StorageExceptionRequest;

public interface StorageRequestTaskLoad extends StorageRequestTask
{
	public ChunksBuffer result() throws StorageExceptionRequest;

	/**
	 * Arms the task-scoped pending-load gate (internal#85): the task keeps the passed mark monitor so
	 * that it can clear the gate when it has completed on all channels (or on the enqueue-failure
	 * path). Called by the task broker at enqueue, before the task is signaled and made visible to any
	 * channel. Returns whether the gate was armed; the broker only signals (and, on failure, clears)
	 * the gate when this returned {@code true}, so a load-task implementation that does not clear the
	 * gate cannot cause it to leak. Default returns {@code false} (not armed), preserving pre-existing
	 * behavior for implementations that do not participate.
	 *
	 * @param markMonitor the shared mark monitor whose gate this task will clear on completion.
	 * @return {@code true} if this task will clear the gate on completion.
	 */
	public default boolean registerPendingLoadTaskGate(final StorageEntityMarkMonitor markMonitor)
	{
		return false;
	}



	public abstract class Abstract extends StorageChannelTask.Abstract<ChunksBuffer>
	implements StorageRequestTaskLoad
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////

		private final    ChunksBuffer[]          result     ;
		// set by the broker at enqueue via registerPendingLoadTaskGate(), before the task is published
		// to any channel; read in onLastCompletion() on the (later) completing channel thread.
		private volatile StorageEntityMarkMonitor markMonitor;



		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		protected Abstract(
			final long                       timestamp   ,
			final int                        channelCount,
			final StorageOperationController controller
		)
		{
			super(timestamp, channelCount, controller);
			this.result = new ChunksBuffer[channelCount];
		}

		@Override
		public boolean registerPendingLoadTaskGate(final StorageEntityMarkMonitor markMonitor)
		{
			this.markMonitor = markMonitor;
			return true;
		}



		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////
		
		protected final ChunksBuffer[] resultArray()
		{
			return this.result;
		}
		
		@Override
		protected void complete(final StorageChannel channel, final ChunksBuffer result) throws InterruptedException
		{
			this.result[channel.channelIndex()] = result;
			this.incrementCompletionProgress();
		}

		@Override
		protected void completeExceptionally(final StorageChannel channel) throws InterruptedException
		{
			// complete() above never waits on siblings, so reaching it directly on this channel's
			// own failure is safe: the null result slot is harmless (already the array's default).
			this.complete(channel, null);
		}

		@Override
		protected void onLastCompletion()
		{
			// Release the task-scoped pending-load gate signaled at enqueue (internal#85). Runs
			// exactly once, when the task has completed on all channels - by then every channel has
			// finished its collect and enqueued its gray marks, so pendingMarksCount keeps
			// isMarkingComplete() false until those are drained; the gate can be released safely.
			if(this.markMonitor != null)
			{
				this.markMonitor.clearPendingLoadTask();
			}
		}

		@Override
		public final ChunksBuffer result() throws StorageExceptionRequest
		{
			if(this.hasProblems())
			{
				throw new StorageExceptionRequest(this.problems());
			}
			
			// all channel result instances share the result array and there is always at least one channel
			return this.result[0];
		}

	}

}
