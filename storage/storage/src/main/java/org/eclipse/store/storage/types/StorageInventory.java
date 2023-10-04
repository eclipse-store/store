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

import org.eclipse.serializer.collections.types.XGettingTable;

public interface StorageInventory extends StorageHashChannelPart
{
	public XGettingTable<Long, StorageDataInventoryFile> dataFiles();

	public StorageTransactionsAnalysis transactionsFileAnalysis();


	
	public static StorageInventory New(
		final int                                           channelIndex        ,
		final XGettingTable<Long, StorageDataInventoryFile> dataFiles           ,
		final StorageTransactionsAnalysis                   transactionsAnalysis
	)
	{
		return new StorageInventory.Default(
			channelIndex        ,
			dataFiles           ,
			transactionsAnalysis
		);
	}

	public final class Default implements StorageInventory
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////

		final int                                           channelIndex        ;
		final XGettingTable<Long, StorageDataInventoryFile> dataFiles           ;
		final StorageTransactionsAnalysis                   transactionsAnalysis;



		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		Default(
			final int                                           channelIndex        ,
			final XGettingTable<Long, StorageDataInventoryFile> dataFiles           ,
			final StorageTransactionsAnalysis                   transactionsAnalysis
		)
		{
			super();
			this.channelIndex         = channelIndex        ;
			this.dataFiles            = dataFiles           ;
			this.transactionsAnalysis = transactionsAnalysis;
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
		public final XGettingTable<Long, StorageDataInventoryFile> dataFiles()
		{
			return this.dataFiles;
		}

		@Override
		public final StorageTransactionsAnalysis transactionsFileAnalysis()
		{
			return this.transactionsAnalysis;
		}

	}

}
