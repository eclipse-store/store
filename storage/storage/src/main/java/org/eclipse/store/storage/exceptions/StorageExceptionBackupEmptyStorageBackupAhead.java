package org.eclipse.store.storage.exceptions;

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
import org.eclipse.store.storage.types.StorageBackupDataFile;
import org.eclipse.store.storage.types.StorageInventory;


public class StorageExceptionBackupEmptyStorageBackupAhead
extends StorageExceptionBackupChannelIndex
{
	///////////////////////////////////////////////////////////////////////////
	// instance fields //
	////////////////////
	
	private final StorageInventory                           storageInventory;
	private final XGettingTable<Long, StorageBackupDataFile> backupFiles     ;

	

	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////

	public StorageExceptionBackupEmptyStorageBackupAhead(
		final StorageInventory                           storageInventory,
		final XGettingTable<Long, StorageBackupDataFile> backupFiles
	)
	{
		super(storageInventory.channelIndex());
		this.storageInventory = storageInventory;
		this.backupFiles      = backupFiles     ;
	}

	public StorageExceptionBackupEmptyStorageBackupAhead(
		final StorageInventory                           storageInventory,
		final XGettingTable<Long, StorageBackupDataFile> backupFiles     ,
		final String                                     message
	)
	{
		super(storageInventory.channelIndex(), message);
		this.storageInventory = storageInventory;
		this.backupFiles      = backupFiles     ;
	}

	public StorageExceptionBackupEmptyStorageBackupAhead(
		final StorageInventory                           storageInventory,
		final XGettingTable<Long, StorageBackupDataFile> backupFiles     ,
		final Throwable                                  cause
	)
	{
		super(storageInventory.channelIndex(), cause);
		this.storageInventory = storageInventory;
		this.backupFiles      = backupFiles     ;
	}

	public StorageExceptionBackupEmptyStorageBackupAhead(
		final StorageInventory                           storageInventory,
		final XGettingTable<Long, StorageBackupDataFile> backupFiles     ,
		final String                                     message         ,
		final Throwable                                  cause
	)
	{
		super(storageInventory.channelIndex(), message, cause);
		this.storageInventory = storageInventory;
		this.backupFiles      = backupFiles     ;
	}

	public StorageExceptionBackupEmptyStorageBackupAhead(
		final StorageInventory                           storageInventory  ,
		final XGettingTable<Long, StorageBackupDataFile> backupFiles       ,
		final String                                     message           ,
		final Throwable                                  cause             ,
		final boolean                                    enableSuppression ,
		final boolean                                    writableStackTrace
	)
	{
		super(storageInventory.channelIndex(), message, cause, enableSuppression, writableStackTrace);
		this.storageInventory = storageInventory;
		this.backupFiles      = backupFiles     ;
	}
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////
	
	public final StorageInventory storageInventory()
	{
		return this.storageInventory;
	}
	
	public final XGettingTable<Long, StorageBackupDataFile> backupFiles()
	{
		return this.backupFiles;
	}
	
}
