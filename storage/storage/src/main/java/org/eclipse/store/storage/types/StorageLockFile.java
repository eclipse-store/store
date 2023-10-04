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

public interface StorageLockFile extends StorageClosableFile
{
	public static StorageLockFile New(final AFile file)
	{
		return new StorageLockFile.Default(
			notNull(file)
		);
	}
	
	public final class Default extends StorageFile.Abstract implements StorageLockFile
	{
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////
		
		protected Default(final AFile file)
		{
			super(file);
		}
				
	}
	
}
