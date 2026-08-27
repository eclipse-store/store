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


public interface StorageRequestTaskGarbageCollection extends StorageRequestTask
{
	public boolean result();



	public final class Default
	extends StorageChannelSynchronizingTask.AbstractCompletingTask<Boolean>
	implements StorageRequestTaskGarbageCollection, StorageChannelTaskStoreEntities
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////

		private final StorageTask actualTask      ;
		private final long        nanoTimeBudget  ;
		private       boolean     completed       ;
		private       boolean     abortSignalArmed;




		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		Default(
			final long                       timestamp     ,
			final int                        channelCount  ,
			final long                       nanoTimeBudget,
			final StorageTask                actualTask    ,
			final StorageOperationController controller
		)
		{
			super(timestamp, channelCount, controller);
			this.actualTask     = actualTask    ;
			this.nanoTimeBudget = nanoTimeBudget;
		}



		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////

		@Override
		protected final Boolean internalProcessBy(final StorageChannel channel)
		{
			this.armAbortSignalOnce(channel);

			// returns true if nothing to do or completed sweep (=done), false otherwise (in marking phase)
			return channel.issuedGarbageCollection(this.nanoTimeBudget);
		}

		/**
		 * Clears a potential stale marking-abort of a PREVIOUS issued garbage collection, arming
		 * this task's attempt. Done exactly once per task, by whichever channel starts processing
		 * first. Race-free: no channel can reach this task before every channel exited the
		 * previous task's processing (the completion barrier), and any failure within THIS task
		 * necessarily happens after this single clear.
		 */
		private synchronized void armAbortSignalOnce(final StorageChannel channel)
		{
			if(!this.abortSignalArmed)
			{
				channel.clearGarbageCollectionAbort();
				this.abortSignalArmed = true;
			}
		}

		private synchronized void setActualTask() // must be synchronized to set exactely only once for all channels
		{
			// (16.09.2014 TM)NOTE: changed from "!=" this.actualTask to " == ". Ought to be typo.
			if(this.next() != null && this.next() == this.actualTask)
			{
				return; // already set by another channel
			}
			this.setNext(this.actualTask);
		}

		/**
		 * Failure-path counterpart of {@code setActualTask()}: links the follow-up task AND
		 * propagates this task's problems into it first, so the follow-up's waiter fails loudly.
		 * This matters for {@code exportChannels(..., true)}: the caller waits on the EXPORT
		 * task, never on this GC task - without the propagation the export would silently
		 * succeed with gc=false semantics although the "definite minimum" contract was
		 * explicitly requested. For an issued garbage collection the follow-up is a Dummy task
		 * nobody waits on, so the propagation is harmless there.
		 */
		private synchronized void repairChain()
		{
			if(this.next() == this.actualTask)
			{
				return; // already repaired/chained
			}

			if(this.actualTask instanceof StorageChannelTask)
			{
				final Throwable[] problems = this.problems();
				for(int i = 0; i < problems.length; i++)
				{
					if(problems[i] != null)
					{
						((StorageChannelTask)this.actualTask).addProblem(i, problems[i]);
					}
				}
			}

			this.setNext(this.actualTask);

			/*
			 * Wake channels already parked in awaitNext on THIS task: the failing channel's
			 * cleanUp runs before the completion barrier and (deliberately, see cleanUp) does
			 * not repair yet - it then parks on this task's monitor until the repair by the
			 * last-finishing channel. setNext() itself does not notify, so without this the
			 * parked channel would sleep out its full awaitNext timeout and stall the follow-up
			 * task's completion barrier for that long.
			 */
			synchronized(this)
			{
				this.notifyAll();
			}
		}

		@Override
		protected final void succeed(final StorageChannel channel, final Boolean completedSweep)
		{
			this.completed = true;
			if(this.actualTask != null)
			{
				this.setActualTask();
			}
		}

		@Override
		protected final void fail(final StorageChannel channel, final Boolean result)
		{
			// nothing to do here
		}

		@Override
		protected final void cleanUp(final StorageChannel channel)
		{
			/*
			 * The follow-up task is normally chained by succeed(). If the garbage collection fails
			 * (e.g. a throwing StorageGCZombieOidHandler or an error during marking), succeed() never
			 * runs and the task chain would be severed: every subsequently enqueued task - including
			 * a shutdown - is attached behind the unreachable follow-up task, and the channel hangs
			 * INDEFINITELY (awaitNext times out after the housekeeping interval but reverts to this
			 * dead task and parks again, forever). Repair the linkage here, in the always-executed
			 * per-channel cleanup.
			 */
			if(!this.hasProblems() || this.actualTask == null)
			{
				return;
			}

			/*
			 * First free potentially stuck sibling channels: a channel that failed mid-marking
			 * (its cleanUp runs BEFORE the completion barrier) leaves pending marks the siblings
			 * wait for inside their (effectively unbounded) issued GC processing - without this
			 * signal they would never return and the repaired chain would be unreachable.
			 * Idempotent; runs for every channel's cleanup with problems.
			 */
			channel.signalGarbageCollectionAbort();

			/*
			 * Repair the chain only once ALL channels have finished processing this task: chaining
			 * earlier would let the follow-up task run on this channel while sibling channels are
			 * still executing the garbage collection - an interleaving that cannot occur on the
			 * succeed() path (which chains strictly after the completion barrier). The failing
			 * channel's cleanUp typically runs BEFORE the barrier (it skips it) and therefore
			 * skips the repair here; it may park in awaitNext on this task until the repair -
			 * repairChain() explicitly wakes it. In the single-channel case the failing channel
			 * is trivially the last one and repairs immediately.
			 */
			if(this.isProcessed())
			{
				this.repairChain(); // synchronized and idempotent across channels
			}
		}

		@Override
		public final boolean result()
		{
			return this.completed;
		}

	}

}
