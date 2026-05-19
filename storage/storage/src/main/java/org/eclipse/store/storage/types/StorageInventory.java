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

/**
 * Per-channel snapshot of the on-disk state of a {@link StorageChannel}, taken when its files are
 * read during startup or via an explicit inventory operation.
 * <p>
 * The snapshot lists the channel's data files keyed by file number and the analyzed contents of
 * its transaction log. Both views together describe everything the channel has persisted up to
 * the point in time the inventory was taken; the storage uses them to reconstruct the channel's
 * in-memory state during initialization.
 *
 * @see StorageDataInventoryFile
 * @see StorageTransactionsAnalysis
 */
public interface StorageInventory extends StorageHashChannelPart
{
	/**
	 * Returns the channel's data files indexed by file number, in ascending order.
	 *
	 * @return a table of data files keyed by per-channel file number.
	 */
	public XGettingTable<Long, StorageDataInventoryFile> dataFiles();

	/**
	 * Returns the analyzed contents of the channel's transaction log.
	 *
	 * @return the {@link StorageTransactionsAnalysis} for this channel.
	 */
	public StorageTransactionsAnalysis transactionsFileAnalysis();



	/**
	 * Pseudo-constructor method to create a new {@link StorageInventory} from the passed channel
	 * data.
	 *
	 * @param channelIndex         the index of the channel this inventory belongs to.
	 * @param dataFiles            the channel's data files keyed by file number.
	 * @param transactionsAnalysis the channel's transaction log analysis.
	 *
	 * @return a new {@link StorageInventory}.
	 */
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

	/**
	 * Default {@link StorageInventory} implementation: an immutable value holder for the channel
	 * index, its data files and its transaction log analysis.
	 */
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
