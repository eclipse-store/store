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


/**
 * Resolves the on-disk locations of the backup-side files mirrored by a
 * {@link StorageBackupSetup}.
 * <p>
 * For every live data or transaction file there is a backup counterpart resolved by this
 * provider; in addition the backup location holds a {@link #provideTypeDictionaryFile() type
 * dictionary file} so that the backup is self-contained and can be restored without the live
 * storage. The backup-side file system can be entirely different from the live file system (e.g.
 * NIO local files mirrored to S3), as long as both providers agree on the channel layout.
 *
 * @see StorageBackupSetup
 * @see StorageLiveFileProvider
 */
public interface StorageBackupFileProvider extends StorageFileProvider
{
	/**
	 * Resolves the backup-side counterpart of the passed live {@link StorageDataFile}, using its
	 * channel index and per-channel file number.
	 *
	 * @param dataFile the live data file whose backup counterpart shall be resolved.
	 *
	 * @return the {@link StorageBackupDataFile} mirroring the passed live data file.
	 */
	public default StorageBackupDataFile provideBackupDataFile(
		final StorageDataFile dataFile
	)
	{
		return this.provideBackupDataFile(dataFile.channelIndex(), dataFile.number());
	}

	/**
	 * Resolves the backup-side data file for the passed channel index and per-channel file number.
	 *
	 * @param channelIndex the channel index.
	 * @param fileNumber   the per-channel data file number.
	 *
	 * @return the {@link StorageBackupDataFile} for the requested channel/file pair.
	 */
	public StorageBackupDataFile provideBackupDataFile(
		int  channelIndex,
		long fileNumber
	);

	/**
	 * Resolves the backup-side transaction log file for the passed channel index.
	 *
	 * @param channelIndex the channel index.
	 *
	 * @return the {@link StorageBackupTransactionsFile} for the requested channel.
	 */
	public StorageBackupTransactionsFile provideBackupTransactionsFile(
		int channelIndex
	);

	/**
	 * Resolves the backup-side type dictionary file. The backup contains a copy of the live type
	 * dictionary so that it can be restored as a stand-alone storage.
	 *
	 * @return the {@link AFile} of the backup-side type dictionary.
	 */
	public AFile provideTypeDictionaryFile();



	/**
	 * Pseudo-constructor method to create a new {@link StorageBackupFileProvider} with default
	 * values from {@link Storage#BackupFileProviderBuilder()}.
	 *
	 * @return a new {@link StorageBackupFileProvider}.
	 */
	public static StorageBackupFileProvider New()
	{
		return Storage.BackupFileProviderBuilder()
			.createFileProvider()
		;
	}

	/**
	 * Pseudo-constructor method to create a new {@link StorageBackupFileProvider} rooted at the
	 * passed directory, using the same file system as the directory.
	 *
	 * @param storageDirectory the backup base directory.
	 *
	 * @return a new {@link StorageBackupFileProvider} rooted at the passed directory.
	 */
	public static StorageBackupFileProvider New(final ADirectory storageDirectory)
	{
		return Storage.BackupFileProviderBuilder(storageDirectory.fileSystem())
			.setDirectory(storageDirectory)
			.createFileProvider()
		;
	}

	/**
	 * Pseudo-constructor method to create a new {@link StorageBackupFileProvider.Default} with
	 * fully-specified directory layout and file naming. {@code deletionDirectory} and
	 * {@code truncationDirectory} are optional and may be {@code null}; everything else is required.
	 *
	 * @param baseDirectory       the backup base directory; must be non-{@code null}.
	 * @param deletionDirectory   optional directory used as a quarantine for deleted files; may be {@code null}.
	 * @param truncationDirectory optional directory used as a quarantine for truncated files; may be {@code null}.
	 * @param structureProvider   the {@link StorageDirectoryStructureProvider} for per-channel layout.
	 * @param fileNameProvider    the {@link StorageFileNameProvider} for file naming.
	 * @param fileHandlerCreator  the {@link PersistenceTypeDictionaryFileHandler.Creator} used to
	 *                            access the type dictionary file.
	 *
	 * @return a new {@link StorageBackupFileProvider.Default}.
	 */
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

	/**
	 * Default {@link StorageBackupFileProvider} implementation, extending
	 * {@link StorageFileProvider.Abstract} with the backup-specific resolution of data, transactions
	 * and type dictionary files.
	 */
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
	
	/**
	 * Pseudo-constructor method to create a new {@link StorageBackupFileProvider.Builder} on the
	 * passed file system.
	 *
	 * @param fileSystem the file system the backup files shall live in; must be non-{@code null}.
	 *
	 * @return a new {@link StorageBackupFileProvider.Builder}.
	 */
	public static StorageBackupFileProvider.Builder<?> Builder(final AFileSystem fileSystem)
	{
		return new StorageBackupFileProvider.Builder.Default(
			notNull(fileSystem)
		);
	}

	/**
	 * Fluent builder for {@link StorageBackupFileProvider} instances, extending the shared
	 * {@link StorageFileProvider.Builder} surface with a backup-specific
	 * {@link #createFileProvider()} that returns the more specific {@link StorageBackupFileProvider}
	 * type.
	 *
	 * @param <B> the self-type of the concrete builder.
	 */
	public interface Builder<B extends Builder<?>> extends StorageFileProvider.Builder<B>
	{
		@Override
		public StorageBackupFileProvider createFileProvider();



		/**
		 * Default {@link Builder} implementation. Unlike the live-storage builder, this builder has
		 * no implicit default base directory: a directory must be set explicitly via
		 * {@link #setDirectory(ADirectory)} before {@link #createFileProvider()} is called, otherwise
		 * a {@link NullPointerException} is thrown.
		 */
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
