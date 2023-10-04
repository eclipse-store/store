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

import org.eclipse.serializer.memory.XMemory;
import org.eclipse.serializer.util.X;


public interface StorageImportSourceByteBuffer extends StorageImportSource
{
	public static class Default
	extends    StorageImportSource.Abstract
	implements StorageImportSourceByteBuffer
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////
		
		private final int        channelIndex;
		private final ByteBuffer buffer      ;
	    
		              
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////
		              
	    Default(
			final int                               channelIndex,
			final ByteBuffer                        buffer      ,
			final StorageChannelImportBatch.Default headBatch
		)
		{
	    	super(headBatch);
			this.channelIndex = channelIndex;
			this.buffer       = buffer      ;
		}
	    
	    
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////
	    
	    @Override
	    public long copyTo(final StorageFile target, final long sourcePosition, final long length)
	    {
	    	return target.writeBytes(
	    		X.ArrayView(XMemory.slice(this.buffer, sourcePosition, length))
	    	);
	    }
	    
	    @Override
	    public boolean close()
	    {
	    	// no-op
	    	return true;
	    }

		@Override
		public String toString()
		{
			return Integer.toString(this.channelIndex) + " "
				+ (this.buffer == null ? "<Dummy>"  : this.buffer + " " + this.headBatch)
			;
		}
		
	}

}
