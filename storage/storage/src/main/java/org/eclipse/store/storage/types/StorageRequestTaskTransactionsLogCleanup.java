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

public interface StorageRequestTaskTransactionsLogCleanup extends StorageRequestTask
{
	public boolean result();

	
	public final class Default
	extends StorageChannelSynchronizingTask.AbstractCompletingTask<Void>
	implements StorageRequestTaskTransactionsLogCleanup, StorageChannelTaskStoreEntities
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////

	    boolean completed;

	    
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		Default(
			final long                       timestamp     ,
			final int                        channelCount  ,
			final StorageOperationController controller
		)
		{
			super(timestamp, channelCount, controller);
		}



		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////

		@Override
		protected final Void internalProcessBy(final StorageChannel channel)
		{
			this.completed = channel.issuedTransactionsLogCleanup();
			return null;
		}

		@Override
		public final boolean result()
		{
			return this.completed;
		}

	}

}
