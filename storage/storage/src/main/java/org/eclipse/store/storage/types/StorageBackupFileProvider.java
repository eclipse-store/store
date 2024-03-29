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

import static org.eclipse.serializer.util.X.mayNull;
import static org.eclipse.serializer.util.X.notNull;

import org.eclipse.serializer.afs.types.ADirectory;
import org.eclipse.serializer.afs.types.AFile;
import org.eclipse.serializer.afs.types.AFileSystem;
import org.eclipse.serializer.persistence.types.PersistenceTypeDictionaryFileHandler;
import org.eclipse.store.afs.nio.types.NioFileSystem;


public interface StorageBackupFileProvider extends StorageFileProvider
{
	public default StorageBackupDataFile provideBackupDataFile(
		final StorageDataFile dataFile
	)
	{
		return this.provideBackupDataFile(dataFile.channelIndex(), dataFile.number());
	}
	
	public StorageBackupDataFile provideBackupDataFile(
		int  channelIndex,
		long fileNumber
	);
		
	public StorageBackupTransactionsFile provideBackupTransactionsFile(
		int channelIndex
	);
	
	public AFile provideTypeDictionaryFile();
	
	
	
	public static StorageBackupFileProvider New()
	{
		return Storage.BackupFileProviderBuilder()
			.createFileProvider()
		;
	}
	
	public static StorageBackupFileProvider New(final ADirectory storageDirectory)
	{
		return Storage.BackupFileProviderBuilder(storageDirectory.fileSystem())
			.setDirectory(storageDirectory)
			.createFileProvider()
		;
	}
	
	public static StorageBackupFileProvider.Default New(
		final ADirectory                                   baseDirectory      ,
		final ADirectory                                   deletionDirectory  ,
		final ADirectory                                   truncationDirectory,
		final StorageDirectoryStructureProvider            structureProvider  ,
		final StorageFileNameProvider                      fileNameProvider   ,
		final PersistenceTypeDictionaryFileHandler.Creator fileHandlerCreator
	)
	{
		return new StorageBackupFileProvider.Default(
			notNull(baseDirectory)      , // base directory must at least be a relative directory name.
			mayNull(deletionDirectory)  ,
			mayNull(truncationDirectory),
			notNull(structureProvider)  ,
			notNull(fileNameProvider)   ,
			notNull(fileHandlerCreator)
		);
	}
	
	public final class Default
	extends StorageFileProvider.Abstract
	implements StorageBackupFileProvider
	{
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		Default(
			final ADirectory                                   baseDirectory      ,
			final ADirectory                                   deletionDirectory  ,
			final ADirectory                                   truncationDirectory,
			final StorageDirectoryStructureProvider            structureProvider  ,
			final StorageFileNameProvider                      fileNameProvider   ,
			final PersistenceTypeDictionaryFileHandler.Creator fileHandlerCreator
		)
		{
			super(
				baseDirectory,
				deletionDirectory,
				truncationDirectory,
				structureProvider,
				fileNameProvider,
				fileHandlerCreator
			);
		}
		


		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////
				
		@Override
		public StorageBackupDataFile provideBackupDataFile(
			final int  channelIndex,
			final long fileNumber
		)
		{
			final AFile file = this.provideDataFile(channelIndex, fileNumber);
			
			return StorageBackupDataFile.New(file, channelIndex, fileNumber);
		}
			
		@Override
		public StorageBackupTransactionsFile provideBackupTransactionsFile(
			final int channelIndex
		)
		{
			final AFile file = this.provideTransactionsFile(channelIndex);
			
			return StorageBackupTransactionsFile.New(file, channelIndex);
		}
		
		@Override
		public AFile provideTypeDictionaryFile()
		{
			return this.defineTypeDictionaryFile();
		}
							
	}
	
	/**
	 * Pseudo-constructor method to create a new {@link StorageBackupFileProvider.Builder} instance.
	 * <p>
	 * For explanations and customizing values, see {@link StorageBackupFileProvider.Builder}.
	 * 
	 * @return a new {@link StorageBackupFileProvider.Builder} instance.
	 */
	public static StorageBackupFileProvider.Builder<?> Builder()
	{
		// note that the backup's file system may potentially be completely different from the live file system.
		final NioFileSystem nfs = Storage.DefaultFileSystem();
		
		return Builder(nfs);
	}
	
	public static StorageBackupFileProvider.Builder<?> Builder(final AFileSystem fileSystem)
	{
		return new StorageBackupFileProvider.Builder.Default(
			notNull(fileSystem)
		);
	}
	
	public interface Builder<B extends Builder<?>> extends StorageFileProvider.Builder<B>
	{
		@Override
		public StorageBackupFileProvider createFileProvider();
		
		
		
		public class Default
		extends StorageFileProvider.Builder.Abstract<StorageBackupFileProvider.Builder.Default>
		implements StorageBackupFileProvider.Builder<StorageBackupFileProvider.Builder.Default>
		{
			///////////////////////////////////////////////////////////////////////////
			// instance fields //
			////////////////////
			
			Default(final AFileSystem fileSystem)
			{
				super(fileSystem);
			}
			
			
			@Override
			protected ADirectory getBaseDirectory()
			{
				if(this.directory() != null)
				{
					return this.directory();
				}
				
				// no idea how to prevent this a little more ... elegantly
				throw new NullPointerException("Missing backup directory.");
			}
		
			@Override
			public StorageBackupFileProvider createFileProvider()
			{
				return StorageBackupFileProvider.New(
					this.getBaseDirectory(),
					this.getDeletionDirectory(),
					this.getTruncationDirectory(),
					this.getDirectoryStructureProvider(),
					this.getFileNameProvider(),
					this.getTypeDictionaryFileHandler()
				);
			}
			
		}
		
	}

}
