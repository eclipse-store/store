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


public class StorageExceptionBackupEmptyStorageForNonEmptyBackup
extends StorageExceptionBackupChannelIndex
{
	///////////////////////////////////////////////////////////////////////////
	// instance fields //
	////////////////////
	
	private final XGettingTable<Long, StorageBackupDataFile> backupFiles;

	

	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////

	public StorageExceptionBackupEmptyStorageForNonEmptyBackup(
		final long                                       channelIndex,
		final XGettingTable<Long, StorageBackupDataFile> backupFiles
	)
	{
		super(channelIndex);
		this.backupFiles = backupFiles;
	}

	public StorageExceptionBackupEmptyStorageForNonEmptyBackup(
		final long                                       channelIndex,
		final XGettingTable<Long, StorageBackupDataFile> backupFiles ,
		final String                                     message
	)
	{
		super(channelIndex, message);
		this.backupFiles = backupFiles;
	}

	public StorageExceptionBackupEmptyStorageForNonEmptyBackup(
		final long                                       channelIndex,
		final XGettingTable<Long, StorageBackupDataFile> backupFiles ,
		final Throwable                                  cause
	)
	{
		super(channelIndex, cause);
		this.backupFiles = backupFiles;
	}

	public StorageExceptionBackupEmptyStorageForNonEmptyBackup(
		final long                                       channelIndex,
		final XGettingTable<Long, StorageBackupDataFile> backupFiles ,
		final String                                     message     ,
		final Throwable                                  cause
	)
	{
		super(channelIndex, message, cause);
		this.backupFiles = backupFiles;
	}

	public StorageExceptionBackupEmptyStorageForNonEmptyBackup(
		final long                                       channelIndex      ,
		final XGettingTable<Long, StorageBackupDataFile> backupFiles       ,
		final String                                     message           ,
		final Throwable                                  cause             ,
		final boolean                                    enableSuppression ,
		final boolean                                    writableStackTrace
	)
	{
		super(channelIndex, message, cause, enableSuppression, writableStackTrace);
		this.backupFiles = backupFiles;
	}
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////
	
	public final XGettingTable<Long, StorageBackupDataFile> backupFiles()
	{
		return this.backupFiles;
	}
	
}
