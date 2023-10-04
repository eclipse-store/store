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
import static org.eclipse.serializer.math.XMath.notNegative;

import org.eclipse.serializer.afs.types.AFile;

public interface StorageLiveTransactionsFile
extends StorageTransactionsFile, StorageLiveChannelFile<StorageLiveTransactionsFile>
{
	@Override
	public default StorageBackupTransactionsFile ensureBackupFile(final StorageBackupInventory backupInventory)
	{
		return backupInventory.ensureTransactionsFile(this);
	}
	
	
	public <P extends StorageTransactionsAnalysis.EntryIterator> P processBy(P iterator);
	
	
	public static StorageLiveTransactionsFile New(
		final AFile file        ,
		final int   channelIndex
	)
	{
		return new StorageLiveTransactionsFile.Default(
			    notNull(file),
			notNegative(channelIndex)
		);
	}
	
	
	
	
	public final class Default
	extends StorageLiveFile.Abstract<StorageLiveTransactionsFile>
	implements StorageLiveTransactionsFile
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////
		
		private final int channelIndex;
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		Default(final AFile file, final int channelIndex)
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
		
		@Override
		public <P extends StorageTransactionsAnalysis.EntryIterator> P processBy(final P iterator)
		{
			StorageTransactionsAnalysis.Logic.processInputFile(
				this.ensureReadable(),
				iterator
			);
			
			return iterator;
		}
		
	}
	
}
