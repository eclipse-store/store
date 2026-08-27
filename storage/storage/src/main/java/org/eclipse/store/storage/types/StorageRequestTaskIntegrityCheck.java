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


public interface StorageRequestTaskIntegrityCheck extends StorageRequestTask
{
	public StorageIntegrityCheckResult result();



	public final class Default
	extends StorageChannelSynchronizingTask.AbstractCompletingTask<StorageIntegrityCheckResult>
	implements StorageRequestTaskIntegrityCheck, StorageChannelTaskStoreEntities
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////

		private final long                               nanoTimeBudget;
		private final boolean                            freshScan     ;
		private final StorageIntegrityCheckResult.Default result        ;



		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		Default(
			final long                       timestamp     ,
			final int                        channelCount  ,
			final long                       nanoTimeBudget,
			final boolean                    freshScan     ,
			final StorageOperationController controller
		)
		{
			super(timestamp, channelCount, controller);
			this.nanoTimeBudget = nanoTimeBudget;
			this.freshScan      = freshScan     ;
			this.result         = StorageIntegrityCheckResult.New();
		}



		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////

		@Override
		protected final StorageIntegrityCheckResult internalProcessBy(final StorageChannel channel)
		{
			return channel.issuedIntegrityCheck(this.nanoTimeBudget, this.freshScan);
		}

		@Override
		protected final void succeed(final StorageChannel channel, final StorageIntegrityCheckResult result)
		{
			// each channel folds its findings into the shared aggregate (merge is synchronized). Every per-channel
			// result is a Default (produced by StorageFileManager.verifyChunkChecksums).
			this.result.merge((StorageIntegrityCheckResult.Default)result);
		}

		@Override
		public final StorageIntegrityCheckResult result()
		{
			return this.result;
		}

	}

}