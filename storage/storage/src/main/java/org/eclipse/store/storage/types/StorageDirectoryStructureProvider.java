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

import org.eclipse.serializer.afs.types.ADirectory;

public interface StorageDirectoryStructureProvider
{
	public ADirectory provideChannelDirectory(
		ADirectory              storageRootDirectory,
		int                     channelIndex        ,
		StorageFileNameProvider fileNameProvider
	);
	
	
	public interface Defaults
	{
		public static StorageDirectoryStructureProvider defaultDirectoryStructureProvider()
		{
			return Default.DEFAULT;
		}
	}
	
		
	public static StorageDirectoryStructureProvider New()
	{
		return new StorageDirectoryStructureProvider.Default();
	}
	
	public final class Default implements StorageDirectoryStructureProvider
	{
		///////////////////////////////////////////////////////////////////////////
		// constants //
		//////////////
		
		static final StorageDirectoryStructureProvider.Default DEFAULT = new StorageDirectoryStructureProvider.Default();
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////
		
		Default()
		{
			super();
		}

		
		
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////

		@Override
		public final ADirectory provideChannelDirectory(
			final ADirectory              storageRootDirectory,
			final int                     channelIndex        ,
			final StorageFileNameProvider fileNameProvider
		)
		{
			final String channelDirectoryName = fileNameProvider.provideChannelDirectoryName(channelIndex);
			final ADirectory channelDirectory = storageRootDirectory.ensureDirectory(channelDirectoryName);
			
			channelDirectory.ensureExists();
			
			return channelDirectory;
		}
		
	}
	
}
