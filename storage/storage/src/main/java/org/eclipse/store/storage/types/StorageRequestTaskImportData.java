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

import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.serializer.collections.XArrays;
import org.eclipse.serializer.collections.types.XGettingEnum;
import org.eclipse.serializer.concurrency.XThreads;
import org.eclipse.serializer.persistence.binary.types.Binary;
import org.eclipse.serializer.util.X;
import org.eclipse.store.storage.exceptions.StorageException;
import org.eclipse.store.storage.exceptions.StorageExceptionImportFailed;


public interface StorageRequestTaskImportData<S> extends StorageRequestTask
{
	public static abstract class Abstract<S>
	extends    StorageChannelSynchronizingTask.AbstractCompletingTask<Void>
	implements StorageRequestTaskImportData<S>, StorageChannelTaskStoreEntities
	{
		///////////////////////////////////////////////////////////////////////////
		// constants //
		//////////////
		
		/* (14.11.2019 TM)TODO: weird waiting time
		 * This should be removed or at least configurable.
		 */
		private static final int SOURCE_WAIT_TIME_MS = 100;
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////

		private final XGettingEnum<S>               sources               ;
		private final StorageEntityCache.Default[]  entityCaches          ;
		private final StorageObjectIdRangeEvaluator objectIdRangeEvaluator;
		
		// adding point for the reader
		private final StorageImportSource.Abstract[] sourceHeads;
		
		// starting point for the channels to process
		private final StorageImportSource.Abstract[] sourceTails;

		private final    AtomicBoolean complete  = new AtomicBoolean();
		private volatile long          maxObjectId;
		private          Thread        readThread ;
		private volatile Throwable     readProblem;
		// whether any channel entered the import commit phase; a channel taking fail() after that
		// is a succeed/fail split the succeed() catch cannot see - it must disrupt (see fail()).
		private volatile boolean       commitStarted;
		Abstract(
			final long                          timestamp             ,
			final int                           channelCount          ,
			final StorageOperationController    controller            ,
			final StorageObjectIdRangeEvaluator objectIdRangeEvaluator,
			final XGettingEnum<S>               sources
		)
		{
			// every channel has to store at least a chunk header, so progress count is always equal to channel count
			super(timestamp, channelCount, controller);
			this.sources                = sources;
			this.objectIdRangeEvaluator = objectIdRangeEvaluator;
			this.entityCaches           = new StorageEntityCache.Default[channelCount];
			this.sourceTails            = this.createImportSources(channelCount);
			this.sourceHeads            = this.sourceTails.clone();
		}



		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////
		
		private StorageImportSource.Abstract[] createImportSources(final int channelCount)
		{
			final StorageImportSource.Abstract[] inputSources = new StorageImportSource.Abstract[channelCount];
			for(int i = 0; i < channelCount; i++)
			{
				inputSources[i] = this.createImportSource(i, null, null);
			}
			
			return inputSources;
		}
		
		protected abstract StorageImportSource.Abstract createImportSource(
			int                               channelIndex,
			S                                 source      ,
			StorageChannelImportBatch.Default headBatch
		);
		
		private boolean entityCacheCollectionNotComplete()
		{
			for(final StorageEntityCache.Default entityCache : this.entityCaches)
			{
				if(entityCache == null)
				{
					return true;
				}
			}
			return false;
		}

		private synchronized void ensureReaderThread()
		{
			if(this.readThread != null || this.entityCacheCollectionNotComplete())
			{
				return;
			}
			this.readThread = XThreads.start((Runnable)this::readSources);
		}

		final void readSources()
		{
			try
			{
				final ItemReader itemReader = new ItemReader(this.entityCaches, this.sourceHeads);

				for(final S source : this.sources)
				{
					try
					{
						itemReader.setSource(source);
						this.iterateSource(source, itemReader);
						itemReader.completeCurrentSource();
					}
					catch(final Exception e)
					{
						throw new StorageExceptionImportFailed("Exception while reading import source " + source, e);
					}
				}
			}
			catch(final Throwable t)
			{
				// this thread has no uncaught exception handler, a failure escaping here would be
				// swallowed; hand it to the channels, which must roll back instead of committing
				// the batches published before the read failed
				this.readProblem = t;
			}
			finally
			{
				// the only signal that no further source will arrive - left unset, every channel
				// parks forever in #internalProcessBy. Written after #readProblem, so observing
				// completion also observes the cause.
				this.complete.set(true);
			}
		}

		/**
		 * The reader thread can fail, never start at all (a channel failing before registering its
		 * entity cache leaves it unstarted, see {@link #ensureReaderThread()}), or a storage-wide
		 * disruption can make waiting pointless. None of these set {@link #complete}, so a waiting
		 * channel must observe them explicitly or it freezes the whole storage, shutdown included.
		 *
		 * @return the cause to register as the waiting channel's problem, or {@code null}. Sibling
		 *         problems are not reported here: those already route every channel into fail().
		 */
		private Throwable importAbortCause()
		{
			final Throwable readProblem = this.readProblem;
			if(readProblem != null)
			{
				return readProblem;
			}

			if(this.controller.hasDisruptions())
			{
				return this.controller.disruptions().first();
			}

			return null;
		}

		protected abstract void iterateSource(S source, ItemAcceptor itemAcceptor);

		@FunctionalInterface
		public interface ItemAcceptor
		{
			public boolean accept(long address, long availableItemLength);
		}
		
		private final class ItemReader implements ItemAcceptor
		{
			///////////////////////////////////////////////////////////////////////////
			// instance fields //
			////////////////////
			
			private final StorageEntityCache.Default[]   entityCaches         ;
			private final StorageImportSource.Abstract[] sourceHeads          ;
			private final ChannelItem[]                  channelItems         ;
			private final int                            channelHash          ;
			private       S                              source               ;
			private       int                            currentBatchChannel  ;
			private       long                           currentSourcePosition;
			
			
			///////////////////////////////////////////////////////////////////////////
			// constructors //
			/////////////////
			
			public ItemReader(
				final StorageEntityCache.Default[]   entityCaches,
				final StorageImportSource.Abstract[] sourceHeads
			)
			{
				super();
				this.entityCaches = entityCaches          ;
				this.sourceHeads  = sourceHeads           ;
				this.channelHash  = sourceHeads.length - 1;
				this.channelItems = XArrays.fill(
					new ChannelItem[sourceHeads.length],
					() ->
						new ChannelItem().resetChains()
				);
			}
			
			@Override
			public boolean accept(final long address, final long availableItemLength)
			{
				final long length = Binary.getEntityLengthRawValue(address);

				// check for a gap
				if(length < 0)
				{

					// keep track of current source position to offset the next batch correctly
					this.currentSourcePosition += X.checkArrayRange(-length);

					// batch is effectively interrupted by the gap, even if the next entity belongs to the same channel
					this.currentBatchChannel = -1;

					// signal to calling context that item has been processed completely
					return true;
				}

				// check for incomplete entity header
				if(availableItemLength < Binary.entityHeaderLength())
				{
					// signal to calling context that entity cannot be processed and header must be reloaded
					return false;
				}

				final int intLength = X.checkArrayRange(length);

				// read and validate entity head information
				final long                      objectId     = Binary.getEntityObjectIdRawValue(address);
				final int                       channelIndex = (int)objectId & this.channelHash;
				final StorageEntityType.Default type         = this.entityCaches[channelIndex].validateEntity(
					intLength,
					Binary.getEntityTypeIdRawValue(address),
					objectId
				);

				// register entity accordingly (either new batch required or current batch can be enlarged)
				if(channelIndex != this.currentBatchChannel)
				{
					this.currentBatchChannel = channelIndex;
					this.startNewBatch(intLength, objectId, type);
				}
				else
				{
					this.addToCurrentBatch(intLength, objectId, type);
				}

				if(objectId >= StorageRequestTaskImportData.Abstract.this.maxObjectId)
				{
					 StorageRequestTaskImportData.Abstract.this.maxObjectId = objectId;
				}

				// keep track of current source position to offset the batch correctly
				this.currentSourcePosition += intLength;

				return true;
			}

			private void startNewBatch(
				final int                       length  ,
				final long                      objectId,
				final StorageEntityType.Default type
			)
			{
				final ChannelItem item = this.channelItems[this.currentBatchChannel];

				item.tailEntity = item.tailBatch = item.tailBatch.batchNext = new StorageChannelImportBatch.Default(
					this.currentSourcePosition,
					length,
					objectId,
					type
				);
			}

			private void addToCurrentBatch(
				final int                       length  ,
				final long                      objectId,
				final StorageEntityType.Default type
			)
			{
				final ChannelItem item = this.channelItems[this.currentBatchChannel];

				// intentionally ignores max file length for sake of import efficiency
				item.tailEntity = item.tailEntity.next = new StorageChannelImportEntity.Default(
					length,
					objectId,
					type
				);

				// update batch length and total length
				item.tailBatch.batchLength += length;
			}

			final void setSource(final S source)
			{
				// next source is set up
				this.currentBatchChannel   =     -1; // invalid value to guarantee change on first entity.
				this.currentSourcePosition =      0; // source starts at 0, of course.
				this.source                = source;
			}

			final void completeCurrentSource()
			{
				final StorageImportSource.Abstract[] sourceHeads  = this.sourceHeads ;
				final ChannelItem[]                  channelItems = this.channelItems;
				for(int i = 0; i < sourceHeads.length; i++)
				{
					final StorageImportSource.Abstract oldSourceHead = sourceHeads[i];
					final ChannelItem                  currentItem   = channelItems[i];

					sourceHeads[i] = sourceHeads[i].next =
						StorageRequestTaskImportData.Abstract.this
							.createImportSource(i, this.source, currentItem.headBatch.batchNext)
					;
					currentItem.resetChains();

					// notify storage thread that a new source is ready for processing
					synchronized(oldSourceHead)
					{
						oldSourceHead.notifyAll();
					}
				}
			}

		}

		@Override
		protected final Void internalProcessBy(final StorageChannel channel)
		{
			/*
			 * signal the channel to prepare for the import
			 * (register the import with the gc, keep current head file and create a new one).
			 * Deliberately called outside the entityCaches lock: the preparation may quiesce a
			 * pending gc sweep (see StorageEntityCache.Default#registerPendingImportUpdate), which must
			 * not be serialized across channels by the shared array's monitor.
			 */
			final StorageEntityCache.Default entityCache = channel.prepareImportData();
			synchronized(this.entityCaches)
			{
				this.entityCaches[channel.channelIndex()] = entityCache;
			}

			/*
			 * the last thread to enter this method starts a single reader thread,
			 * all other threads return here right away
			 */
			this.ensureReaderThread();

			// the tail array is always initialized with an empty dummy source which serves as an entry point.
			StorageImportSource.Abstract currentSource = this.sourceTails[channel.channelIndex()];

			// quite a braces mountain, however it is logically necessary
			try
			{
				importLoop:
				while(true)
				{
					// acquire a lock on the channel-exclusive signalling instance to wait for the reader's notification
					synchronized(currentSource)
					{
						// wait for the next batch to import (successor of the current batch)
						while(currentSource.next == null)
						{
							if(this.complete.get())
							{
								// there will be no more next source, so abort (task is complete)
								break importLoop;
							}

							// no source can arrive any more (see #importAbortCause, or a sibling
							// already failed the task); the cause is registered after the loop
							if(this.hasProblems() || this.importAbortCause() != null)
							{
								break importLoop;
							}

							// better check again after some time, indefinite wait caused a deadlock once
							// (16.04.2016)TODO: isn't the above comment a bug? Test and change or comment better.
							currentSource.wait(SOURCE_WAIT_TIME_MS);
							// note: completion adds an empty dummy source to avoid special case handling here
						}
						// at this point, there definitely is a new/next batch to process, so advance tail and process
						currentSource = currentSource.next;
					}

					// process the batch outside the lock to not block the central reader thread by channel-local work
					channel.importData(currentSource);
				}
			}
			catch(final InterruptedException e)
			{
				// being interrupted is a normal problem here, causing to abort the task, no further handling required.

				/* (16.04.2016)TODO: storage import interruption handling.
				 * Shouldn't an import be properly interruptible in the first place?
				 * Either change code or comment accordingly.
				 */
				throw new StorageException(e);
			}

			/*
			 * Registering the cause is what routes every channel into fail() (rollback). Merely
			 * leaving the loop would let succeed() commit the batches published before the abort -
			 * a partial import, silently reported as successful.
			 */
			final Throwable abortCause = this.importAbortCause();
			if(abortCause != null)
			{
				this.addProblem(channel.channelIndex(), abortCause);
			}

			return null;
		}

		@Override
		protected final void succeed(final StorageChannel channel, final Void result)
		{
			/*
			 * An aborted waitOnProcessing (async disruption) reaches succeed() WITHOUT the processing
			 * barrier. Committing into a storage that is going down would only create a torn commit
			 * for the restart to undo - abort; the throw registers as this channel's problem.
			 */
			if(this.controller.hasDisruptions())
			{
				throw new StorageException(
					"Aborting import commit: the storage is disrupted", this.controller.disruptions().first()
				);
			}

			try
			{
				// evaluate (validate or update if possible) objectId before committing the import
				this.objectIdRangeEvaluator.evaluateObjectIdRange(0, this.maxObjectId);

				// from here on a fail() on any channel is a succeed/fail split, see fail()
				this.commitStarted = true;

				/* on success, signal the channel to commit the imported data (register entities in cache)
				 * All channels use the same timestamp (this task's issuing timestamp) for consistency checks
				 */
				channel.commitImportData(this.timestamp());
			}
			catch(final Throwable t)
			{
				/*
				 * A commit-phase throw can leave siblings committed but not this channel - a torn
				 * import. Escalate so processing stops: further stores or housekeeping could make
				 * the torn state permanent (consensus advanced past it, rollback files dissolved).
				 * The restart's consensus then undoes it; the rethrow fails the task as before.
				 */
				this.controller.registerDisruption(t);
				this.controller.setChannelProcessingEnabled(false);

				// close the sources as fail() would - with the last-completing channel failing
				// here, no sibling takes the fail() path. Safe: the disruption check above filtered
				// the barrier-less path, so every channel passed the processing barrier.
				try
				{
					this.cleanUpResources();
				}
				catch(final Throwable suppressed)
				{
					t.addSuppressed(suppressed);
				}

				throw t;
			}
		}

		@Override
		protected void postCompletionSuccess(final StorageChannel channel, final Void result)
			throws InterruptedException
		{
			/*
			 * hasProblems() was false when THIS channel completed, but a sibling can still fail in
			 * its own succeed(). Problems are registered before the completion counter increments,
			 * so the problem state is final once the task is complete - wait for that before lifting
			 * the deferral, or a store queued behind the import could roll the head over and seal a
			 * torn commit out of the head-file-only recovery.
			 */
			try
			{
				this.waitOnCompletion();
			}
			catch(final StorageException e)
			{
				// a sibling failed after this channel completed: the deferral must hold until the
				// restart undoes the commit; the sibling disrupted the storage, closing the sources
				return;
			}

			// every channel committed: lift this channel's torn-import deferral of file lifecycle events
			channel.confirmImportData();
			this.cleanUpResources();
		}

		@Override
		protected final void fail(final StorageChannel channel, final Void result)
		{
			/*
			 * fail() after a sibling entered the commit phase is a succeed/fail split the succeed()
			 * catch cannot see (e.g. a problem surfacing only in the completion phase). The lone
			 * commit must not be buried by later stores advancing the restart consensus past it -
			 * stop the storage; the next start undoes it.
			 */
			if(this.commitStarted)
			{
				final Throwable problem = this.problemForChannel(channel);
				this.controller.registerDisruption(
					problem != null
						? problem
						: new StorageException("Import failed after a channel already began its commit")
				);
				this.controller.setChannelProcessingEnabled(false);
			}

			try
			{
				this.cleanUpResources();
			}
			finally
			{
				// must run even when a source close throws; skipping the rollback leaves unlogged
				// import bytes in the head file
				channel.rollbackImportData(this.problemForChannel(channel));
			}
		}

		@Override
		protected final void cleanUp(final StorageChannel channel)
		{
			/*
			 * Ultimate cleanup, no matter the task outcome (committed, rolled back, aborted):
			 * release the gc coordination signal acquired in StorageChannel#prepareImportData,
			 * allowing sweeps to be initiated again. Idempotent and robust if the preparation
			 * never ran or failed halfway.
			 */
			channel.cleanupImportData();
		}

		private void cleanUpResources()
		{
			final DisruptionCollectorExecuting<StorageImportSource> closer = DisruptionCollectorExecuting.New(
				StorageImportSource::close
			);
			
			for(final StorageImportSource.Abstract tail : this.sourceTails)
			{
				// the first slice is a dummy with no FileChannel instance
				for(StorageImportSource.Abstract source = tail; (source = source.next) != null;)
				{
					closer.executeOn(source);
				}
			}
			
			if(closer.hasDisruptions())
			{
				throw new StorageException(closer.toMultiCauseException());
			}
		}
		
		
		///////////////////////////////////////////////////////////////////////////
		// helper classes //
		///////////////////

		static final class ChannelItem
		{
			///////////////////////////////////////////////////////////////////////////
			// instance fields //
			////////////////////
			
			final StorageChannelImportBatch.Default  headBatch  = new StorageChannelImportBatch.Default();
			      StorageChannelImportBatch.Default  tailBatch ;
			      StorageChannelImportEntity.Default tailEntity;
			
			
			
			///////////////////////////////////////////////////////////////////////////
			// methods //
			////////////

			ChannelItem resetChains()
			{
				(this.tailBatch = this.headBatch).next = null;
				this.headBatch.batchNext = null;
				this.tailEntity = null; // gets assigned with the first actual batch
				return this;
			}
			
		}
		
	}
	
}
