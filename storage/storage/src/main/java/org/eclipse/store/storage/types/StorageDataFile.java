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

public interface StorageDataFile extends StorageChannelFile, StorageBackupableFile //, StorageClosableFile, StorageCreatableFile
{
	@Override
	public int channelIndex();
	
	public long number();
	
	
	
	@Override
	public default StorageBackupDataFile ensureBackupFile(final StorageBackupInventory backupInventory)
	{
		return backupInventory.ensureDataFile(this);
	}
	
	
	
	public static int orderByNumber(
		final StorageDataFile file1,
		final StorageDataFile file2
	)
	{
		return Long.compare(file1.number(), file2.number());
	}
	
	
	
	@FunctionalInterface
	public interface Creator<F extends StorageDataFile>
	{
		public F createDataFile(AFile file, int channelIndex, long number);
	}
	
	
	
	public abstract class Abstract extends StorageChannelFile.Abstract implements StorageDataFile
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////
		
		private final long number;

		
		
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////
		
		protected Abstract(final AFile file, final int channelIndex, final long number)
		{
			super(file, channelIndex);
			this.number = number;
		}
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////
		
		@Override
		public final long number()
		{
			return this.number;
		}
		

	}
	
}
