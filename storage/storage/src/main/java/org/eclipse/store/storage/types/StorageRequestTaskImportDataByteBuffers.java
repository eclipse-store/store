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

import org.eclipse.serializer.collections.types.XGettingEnum;
import org.eclipse.serializer.memory.XMemory;
import org.eclipse.serializer.persistence.binary.types.BinaryEntityRawDataIterator;


public interface StorageRequestTaskImportDataByteBuffers extends StorageRequestTaskImportData<ByteBuffer>
{
	public final class Default
	extends    StorageRequestTaskImportData.Abstract<ByteBuffer>
	implements StorageRequestTaskImportDataByteBuffers
	{
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		Default(
			final long                          timestamp             ,
			final int                           channelCount          ,
			final StorageObjectIdRangeEvaluator objectIdRangeEvaluator,
			final XGettingEnum<ByteBuffer>      importData,
			final StorageOperationController    controller
		)
		{
			super(timestamp, channelCount, controller, objectIdRangeEvaluator, importData);
		}
		
		@Override
		protected StorageImportSource.Abstract createImportSource(
			final int                               channelIndex,
			final ByteBuffer                        buffer      ,
			final StorageChannelImportBatch.Default headBatch
		)
		{
			return new StorageImportSourceByteBuffer.Default(channelIndex, buffer, headBatch);
		}
		
		@Override
		protected void iterateSource(final ByteBuffer buffer, final ItemAcceptor itemAcceptor)
		{
			final long address = XMemory.getDirectByteBufferAddress(buffer);
	    	BinaryEntityRawDataIterator.New().iterateEntityRawData(
	    		address,
	    		address + buffer.limit(),
	    		(entityStartAddress, dataBoundAddress) -> itemAcceptor.accept(
	    			entityStartAddress                   , // start is the same
	    			dataBoundAddress - entityStartAddress  // map to available item length
	    		)
	    	);
		}

	}

}
