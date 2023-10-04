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

import org.eclipse.serializer.afs.types.ADirectory;

/**
 * This exception states that the fullBackup of a storage won't be performed because the backups target (directory)
 * is not empty. 
 */
@SuppressWarnings("serial")
public class StorageExceptionBackupFullBackupTargetNotEmpty extends StorageExceptionBackup 
{
	///////////////////////////////////////////////////////////////////////////
	// constructor //
	/////////////////
	
	public StorageExceptionBackupFullBackupTargetNotEmpty(final ADirectory targetDirectory)
	{
		super("FullBackup target " + targetDirectory.toPathString() + " not empty! Backup aborted!");
	}
}
