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

import static org.eclipse.serializer.util.X.notNull;

import org.eclipse.serializer.util.logging.Logging;
import org.eclipse.store.storage.exceptions.StorageException;

import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;

public interface StorageChannelTask extends StorageTask
{
	public void incrementCompletionProgress();

	public void addProblem(int hashIndex, Throwable problem);



	// (26.11.2014 TM)TODO: consolidate task naming

	public abstract class Abstract<R>
	extends StorageTask.Abstract
	implements StorageChannelTask
	{
		private final static Logger logger = Logging.getLogger(StorageChannelTask.class);
		
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////

		private int remainingForCompletion;
		private int remainingForProcessing;

		private final AtomicBoolean hasProblems = new AtomicBoolean();
		private final Throwable[]   problems   ; // unshared instance conveniently abused as a second lock
		
		/* (07.03.2022 TM)NOTE:
		 * Retrofitted to fix #285
		 * While it seems more reasonable at first to check for disruptions in a passed context instance,
		 * the static helper StorageRequestAcceptor#waitOnTask makes this approach a little tricky.
		 * Also, since the change from the channel-based architecture to the cell-based architecture
		 * will make all this cross-channel problem checking unnecessary in the future, this solution here
		 * is acceptable for the time being.
		 */
		protected final StorageOperationController controller ;



		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		public Abstract(
			final long                       timestamp   ,
			final int                        channelCount,
			final StorageOperationController controller
		)
		{
			super(timestamp);
			
			// (20.11.2019 TM)NOTE: inlined assignments caused an "Unsafe" error on an ARM machine.
			this.remainingForProcessing = channelCount               ;
			this.remainingForCompletion = channelCount               ;
			this.controller             = notNull(controller)        ;
			this.problems               = new Throwable[channelCount];
		}



		///////////////////////////////////////////////////////////////////////////
		// declared methods //
		/////////////////////

		private void checkForProblems()
		{
			if(this.controller.hasDisruptions())
			{
				throw new StorageException("Aborting after: ", this.controller.disruptions().first());
			}
						
			if(!this.hasProblems.get())
			{
				return;
			}
									
			// (30.05.2013 TM)FIXME: check why this is never reached when task fails?
			// (15.06.2013 TM)NOTE: should be fixed by double check in waitOnCompletion()
			// (09.12.2019 TM)NOTE: still needs to be investigated.
			for(int i = 0; i < this.problems.length; i++)
			{
				if(this.problems[i] != null)
				{
					throw new StorageException("Problem in channel #" + i, this.problems[i]);
				}
			}
		}



		///////////////////////////////////////////////////////////////////////////
		// declared methods //
		/////////////////////

		protected abstract R internalProcessBy(StorageChannel channel);

		protected abstract void complete(StorageChannel channel,  R value) throws InterruptedException;

		/**
		 * Completion for a channel whose OWN {@link #internalProcessBy} just threw. Unlike
		 * {@link #complete}, this outcome is already determined (the channel must fail) regardless
		 * of what sibling channels do, so implementations must NOT wait on siblings here:
		 * {@link StorageChannelSynchronizingTask.AbstractCompletingTask#complete} does exactly that
		 * via {@code waitOnProcessing()}, and some subclasses' {@link #cleanUp} (run immediately
		 * after this method returns) is what unblocks siblings still stuck inside their own
		 * {@code internalProcessBy} (e.g. a garbage-collection mark-wait) — waiting here would
		 * deadlock against that.
		 * <p>
		 * Declared abstract (no default delegating to {@link #complete}) so that every direct
		 * subclass makes this call explicitly: a future {@code complete()} that itself waits on
		 * siblings would silently reintroduce the same deadlock if this were inherited unnoticed.
		 */
		protected abstract void completeExceptionally(StorageChannel channel) throws InterruptedException;

		protected void finishProcessing()
		{
			// must notify other threads about progress even in case of error
			this.incrementProcessingProgress();
		}

		/* ultimate completion that has to be done in any case (resource closing etc.),
		 * no matter what problems occurred before.
		 */
		protected void cleanUp(final StorageChannel channel)
		{
			// no-op in general implementation
		}

		protected final int channelCount()
		{
			// a bit of a hack, but rarely used, so it's better off that way
			return this.problems.length;
		}


		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////

		@Override
		public final synchronized void incrementCompletionProgress()
		{
			// may never get negative or something is seriously broken
			// suffices as this method gets called by every manager thread exactly once.
			if(--this.remainingForCompletion == 0)
			{
				// exactly once, when the last channel has completed the task (normal or exceptional
				// path, since completeExceptionally routes here too). Runs under this task's lock,
				// before waiters are woken.
				this.onLastCompletion();
			}
			this.notifyAll();
		}

		/**
		 * Hook fired exactly once, when the last channel has completed this task (i.e.
		 * {@code remainingForCompletion} reaches zero), under the task lock and before any waiter is
		 * notified. No-op by default; overridden e.g. by the load task to release its task-scoped
		 * pending-load gate (see StorageRequestTaskLoad.Abstract).
		 */
		protected void onLastCompletion()
		{
			// no-op in general implementation
		}

		@Override
		public final synchronized boolean isComplete()
		{
			return this.remainingForCompletion == 0;
		}

		@Override
		public final synchronized void waitOnCompletion() throws InterruptedException
		{
			while(this.remainingForCompletion > 0)
			{
				this.checkForProblems(); // check for problems already while waiting
				this.wait(100);
			}
			this.checkForProblems(); // check for problems after every channel reported completion
		}

		@Override
		public final boolean hasProblems()
		{
			return this.hasProblems.get();
		}

		@Override
		public final Throwable[] problems()
		{
			return this.problems;
		}

		@Override
		public final Throwable problemForChannel(final StorageChannel channel)
		{
			return this.problems[channel.channelIndex()];
		}

		@Override
		public final void addProblem(final int hashIndex, final Throwable problem)
		{
			logger.error("Error occurred in storage channel#{}", hashIndex, problem);
			
			if(this.problems[hashIndex] == null)
			{
				this.problems[hashIndex] = problem;
				this.hasProblems.set(true);
			}
			else
			{
				this.problems[hashIndex].addSuppressed(problem);
			}
		}

		public final boolean isProcessed()
		{
			synchronized(this.problems)
			{
				return this.remainingForProcessing == 0;
			}
		}

		public final void waitOnProcessing() throws InterruptedException
		{
			synchronized(this.problems)
			{
				while(this.remainingForProcessing > 0)
				{
					this.problems.wait();
				}
			}
		}

		public final void incrementProcessingProgress()
		{
			synchronized(this.problems)
			{
				// may never get negative or something is seriously broken
				// suffices as this method gets called by every manager thread exactly once.
				this.remainingForProcessing--;
				this.problems.notifyAll();
			}
		}

		@Override
		public final void processBy(final StorageChannel storageChannel) throws InterruptedException
		{
			// separate outermost try-finally guarantees calling of clean up logic in any case
			try
			{
				final R result;
				try
				{
					result = this.internalProcessBy(storageChannel);
				}
				catch(final Throwable e)
				{
					// the problem is reported, but this channel must still reach a completion hook:
					// skipping it (as before) also skipped fail(), so a channel that threw AFTER a
					// partial write (e.g. mid-store) never got its own rollback. completeExceptionally
					// (not complete) is used deliberately - see its contract. The finally below still
					// runs finishProcessing() on this path, after this call and before the return.
					this.addProblem(storageChannel.channelIndex(), e);
					this.completeExceptionally(storageChannel);
					return;
				}
				finally
				{
					// processing is finishing in any case (e.g. notifying other thread about the task's progress)
					this.finishProcessing();
				}

				// task gets completed (must be done after finishing the processing)
				this.complete(storageChannel, result);
			}
			finally
			{
				this.cleanUp(storageChannel);
			}

		}

	}

}
