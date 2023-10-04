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

import org.eclipse.serializer.afs.types.AFile;

public interface StorageChannelFile extends StorageClosableFile, StorageHashChannelPart
{
	@Override
	public int channelIndex();
	
	
	
	
	public abstract class Abstract
	extends StorageFile.Abstract
	implements StorageChannelFile
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////
		
		private final int channelIndex;

		
		
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////
		
		protected Abstract(final AFile file, final int channelIndex)
		{
			super(file);
			this.channelIndex = channelIndex;
		}
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////
		
		@Override
		public final int channelIndex()
		{
			return this.channelIndex;
		}
		
//		@Override
//		public synchronized boolean isOpen()
//		{
//			return this.internalIsOpen();
//		}
//
//		@Override
//		public synchronized boolean close()
//		{
//			return this.internalClose();
//		}
		
	}
	
}
