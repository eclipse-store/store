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



	public abstract class Abstract extends StorageChannelTask.Abstract<ChunksBuffer>
	implements StorageRequestTaskLoad
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////

		private final ChunksBuffer[] result;
		


		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		protected Abstract(final long timestamp, final int channelCount, final StorageOperationController controller)
		{
			super(timestamp, channelCount, controller);
			this.result = new ChunksBuffer[channelCount];
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
