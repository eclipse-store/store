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

import org.eclipse.serializer.chars.VarString;
import org.eclipse.store.storage.types.StorageBackupFile;
import org.eclipse.store.storage.types.StorageChannelFile;


public class StorageExceptionBackupCopying
extends StorageExceptionBackup
{
	///////////////////////////////////////////////////////////////////////////
	// instance fields //
	////////////////////
	
	private final StorageChannelFile sourceFile    ;
	private final long               sourcePosition;
	private final long               length        ;
	private final StorageBackupFile  backupFile    ;

	

	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////

	public StorageExceptionBackupCopying(
		final StorageChannelFile sourceFile    ,
		final long               sourcePosition,
		final long               length        ,
		final StorageBackupFile  backupFile
	)
	{
		super();
		this.sourceFile     = sourceFile    ;
		this.sourcePosition = sourcePosition;
		this.length         = length        ;
		this.backupFile     = backupFile    ;
	}

	public StorageExceptionBackupCopying(
		final StorageChannelFile sourceFile   ,
		final long               sourcePosition,
		final long               length        ,
		final StorageBackupFile  backupFile    ,
		final String             message
	)
	{
		super(message);
		this.sourceFile     = sourceFile    ;
		this.sourcePosition = sourcePosition;
		this.length         = length        ;
		this.backupFile     = backupFile    ;
	}

	public StorageExceptionBackupCopying(
		final StorageChannelFile sourceFile   ,
		final long               sourcePosition,
		final long               length        ,
		final StorageBackupFile  backupFile    ,
		final Throwable          cause
	)
	{
		super(cause);
		this.sourceFile     = sourceFile    ;
		this.sourcePosition = sourcePosition;
		this.length         = length        ;
		this.backupFile     = backupFile    ;
	}

	public StorageExceptionBackupCopying(
		final StorageChannelFile sourceFile    ,
		final long               sourcePosition,
		final long               length        ,
		final StorageBackupFile  backupFile    ,
		final String             message       ,
		final Throwable          cause
	)
	{
		super(message, cause);
		this.sourceFile     = sourceFile    ;
		this.sourcePosition = sourcePosition;
		this.length         = length        ;
		this.backupFile     = backupFile    ;
	}

	public StorageExceptionBackupCopying(
		final StorageChannelFile sourceFile        ,
		final long               sourcePosition    ,
		final long               length            ,
		final StorageBackupFile  backupFile        ,
		final String             message           ,
		final Throwable          cause             ,
		final boolean            enableSuppression ,
		final boolean            writableStackTrace
	)
	{
		super(message, cause, enableSuppression, writableStackTrace);
		this.sourceFile     = sourceFile    ;
		this.sourcePosition = sourcePosition;
		this.length         = length        ;
		this.backupFile     = backupFile    ;
	}
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////
	
	public final StorageChannelFile sourceFile()
	{
		return this.sourceFile;
	}
	
	public final long sourcePosition()
	{
		return this.sourcePosition;
	}
	
	public final long length()
	{
		return this.length;
	}
	
	public final StorageBackupFile backupFile()
	{
		return this.backupFile;
	}
	
	@Override
	public String assembleDetailString()
	{
		return VarString.New()
			.add(this.sourceFile.identifier()).add('@').add(this.sourcePosition).add('+').add(this.length)
			.add(" -> ")
			.add(this.backupFile.identifier())
			.toString()
		;
	}
	
}
