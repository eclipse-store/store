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

public interface StorageBackupDataFile extends StorageDataFile, StorageBackupChannelFile
{
	public static StorageBackupDataFile New(
		final AFile file        ,
		final int   channelIndex,
		final long  number
	)
	{
		return new StorageBackupDataFile.Default(
			    notNull(file)        ,
			notNegative(channelIndex),
			notNegative(number)
		);
	}
	
	public final class Default extends StorageDataFile.Abstract implements StorageBackupDataFile
	{
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		protected Default(final AFile file, final int channelIndex, final long number)
		{
			super(file, channelIndex, number);
		}
		
	}
	
}
