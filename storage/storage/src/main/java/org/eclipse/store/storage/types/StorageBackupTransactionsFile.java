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

import static org.eclipse.serializer.util.X.notNull;
import static org.eclipse.serializer.math.XMath.notNegative;

public interface StorageBackupTransactionsFile extends StorageTransactionsFile, StorageBackupChannelFile
{
	public static StorageBackupTransactionsFile New(
		final AFile file        ,
		final int   channelIndex
	)
	{
		return new StorageBackupTransactionsFile.Default(
			    notNull(file)        ,
			notNegative(channelIndex)
		);
	}
	
	public final class Default extends StorageChannelFile.Abstract implements StorageBackupTransactionsFile
	{
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		protected Default(final AFile file, final int channelIndex)
		{
			super(file, channelIndex);
		}
		
	}
}
