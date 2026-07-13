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

import java.nio.ByteBuffer;

import org.eclipse.serializer.persistence.binary.types.Binary;
import org.eclipse.serializer.typing.KeyValue;
import org.eclipse.serializer.util.X;

public interface StorageRequestTaskStoreEntities extends StorageRequestTask
{
	
	/* (11.08.2018 TM)TODO:
	 * The overly complex "KeyValue<ByteBuffer[], long[]>" construct could be replaced by a simple Long containing
	 * the channel's basePosition at which the chunk is stored as determined in StorageFileManager#storeChunks.
	 * Every sub-chunk's (ByteBuffer content's) file position could be calculated on the file by this while
	 * iterating them in the postCompletionSuccess logic.
	 * Preferable to a meaningless Long would be a "StorageChunkFilePosition" instance, containing a long value
	 * and, why not, a reference to the ByteBuffer[].
	 * The performance gain would probably not be noticeable, but it would simplify the source code.
	 * But for now (and while actually working on a network persistence demo and not the storage), the
	 * "never touch a running system" proverb applies.
	 */
	
	public final class Default
	extends StorageChannelSynchronizingTask.AbstractCompletingTask<KeyValue<ByteBuffer[], long[]>>
	implements StorageRequestTaskStoreEntities, StorageChannelTaskStoreEntities
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////

		private final Binary                           data                      ;
		private final StorageReferenceValidationPolicy referenceValidationPolicy ;
		private final long[][]                         trustedObjectIdsPerChannel;
		// per-channel "rolled a file over this task" flags; if any channel did, the completion barrier
		// makes every channel durable so the new file's baseline collapse is never over a non-durable store.
		private final StorageChannelSynchronizingTask.ChannelResults rolledOver;



		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		Default(final long timestamp, final Binary data, final StorageOperationController controller)
		{
			// falls back to the documented product default (see StorageConfiguration).
			this(timestamp, data, controller, StorageReferenceValidationPolicy.LOG);
		}

		Default(
			final long                             timestamp                ,
			final Binary                           data                     ,
			final StorageOperationController       controller               ,
			final StorageReferenceValidationPolicy referenceValidationPolicy
		)
		{
			// every channel has to store at least a chunk header, so progress count is always equal to channel count
			super(timestamp, data.channelCount(), controller);
			this.data                       = data;
			this.referenceValidationPolicy  = X.notNull(referenceValidationPolicy);
			this.trustedObjectIdsPerChannel = referenceValidationPolicy.isValidating()
				? partitionPerChannel(data.trustedObjectIds(), data.channelCount())
				: null
			;
			this.rolledOver = new StorageChannelSynchronizingTask.ChannelResults(data.channelCount());
		}

		/**
		 * Partitions the passed trusted object ids by their owning channel, using the same
		 * objectId-to-channel hash the entity registry uses. Runs once on the issuing thread,
		 * off the channel threads. Returns {@code null} if there is nothing to validate.
		 */
		private static long[][] partitionPerChannel(final long[] trustedObjectIds, final int channelCount)
		{
			if(trustedObjectIds == null || trustedObjectIds.length == 0)
			{
				return null;
			}

			final int   channelHashModulo = channelCount - 1;
			final int[] counts            = new int[channelCount];
			for(final long objectId : trustedObjectIds)
			{
				counts[StorageEntityCache.Default.oidChannelIndex(objectId, channelHashModulo)]++;
			}

			final long[][] perChannel = new long[channelCount][];
			for(int i = 0; i < channelCount; i++)
			{
				perChannel[i] = new long[counts[i]];
				counts[i] = 0;
			}
			for(final long objectId : trustedObjectIds)
			{
				final int channelIndex = StorageEntityCache.Default.oidChannelIndex(objectId, channelHashModulo);
				perChannel[channelIndex][counts[channelIndex]++] = objectId;
			}

			return perChannel;
		}



		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////

		@Override
		protected final KeyValue<ByteBuffer[], long[]> internalProcessBy(final StorageChannel channel)
		{
			if(this.trustedObjectIdsPerChannel != null)
			{
				// validate before writing: a detected dangling reference fails this channel's processing,
				// which causes the task-wide fail/rollback of whatever the other channels already wrote.
				channel.validateTrustedReferences(
					this.trustedObjectIdsPerChannel[channel.channelIndex()],
					this.referenceValidationPolicy
				);
			}

			final KeyValue<ByteBuffer[], long[]> stored =
				channel.storeEntities(this.timestamp(), this.data.channelChunk(channel.channelIndex()));

			// record whether this channel rolled a file over, for the completion barrier below. Set
			// before finishProcessing so every channel's succeed sees it after waitOnProcessing.
			this.rolledOver.set(channel.channelIndex(), channel.pollRolloverOccurred());

			return stored;
		}

		@Override
		protected final void succeed(final StorageChannel channel, final KeyValue<ByteBuffer[], long[]> result)
		{
			// Eager rollover durability: if any channel rolled a file over, every channel fsyncs here in
			// its own succeed, so the just-sealed store is durable everywhere before the store returns.
			// The processing barrier has passed, so rolledOver reflects all channels. Ordered BEFORE the
			// commit: a channel then never commits entities its own fsync failed to make durable, and
			// the failing channel leaves nothing committed (its uncommitted write heals as an
			// unconfirmed tail on the next start). Channels completing later roll back via fail();
			// a channel that already committed did so only after its own successful fsync, and the
			// restart's consensus reconciliation restores all-or-nothing across channels.
			if(this.rolledOver.anyTrue())
			{
				try
				{
					channel.flushStorage();
				}
				catch(final RuntimeException e)
				{
					// fsync failed on a medium fault. Escalate as the flush task does: register the
					// disruption and stop processing so the fault surfaces loudly instead of the channel
					// accepting further non-durable stores. Rethrow so this store also reports it.
					this.controller.registerDisruption(e);
					this.controller.setChannelProcessingEnabled(false);
					throw e;
				}
			}

			// no storing operation of the other hash channels failed, so definitely commit the write here.
			channel.commitChunkStorage();
		}

		@Override
		protected final void postCompletionSuccess(
			final StorageChannel                 channel,
			final KeyValue<ByteBuffer[], long[]> result
		)
			throws InterruptedException
		{
			/* Post-completion logic that updates the storage channel's entity cache with the new entity data.
			 * this MIGHT come "too late" in terms of an entity that just got sweeped by GC but would now be
			 * referenced again.
			 * If such a case should pose a problem in an application (i.e. first releasing the last reference to an
			 * entity but at some later point wanting to reference it again without actually containing it in the data)
			 * has to be considered a business logic error that does not have to be covered by storage-level logic.
			 *
			 * If entity cache update should fail (which should never do)
			 * the problem has to (and can) be corrected (the necessary data has already been stored successfully).
			 * The task itself has already been reported as successful and the thread that issued
			 * and waited for the task already continued working.
			 */
			channel.postStoreUpdateEntityCache(result.key(), result.value());
		}

		@Override
		protected final void fail(final StorageChannel channel, final KeyValue<ByteBuffer[], long[]> result)
		{
			channel.rollbackChunkStorage();
		}

		@Override
		protected final void cleanUp(final StorageChannel channel)
		{
			// signal channel to clean up the current store, e.g. remove pending store updates to re-enable GC sweeping
			channel.cleanupStore();
		}

	}

}
