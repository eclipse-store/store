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
import org.eclipse.serializer.afs.types.AFile;
import org.eclipse.serializer.afs.types.AFileSystem;
import org.eclipse.serializer.persistence.types.PersistenceTypeDictionaryFileHandler;

import static org.eclipse.serializer.util.X.mayNull;
import static org.eclipse.serializer.util.X.notNull;

/**
 * Resolves the on-disk locations of the live storage files of a running embedded storage.
 * <p>
 * For every {@link StorageChannel} the provider supplies the data files (one or more per channel,
 * indexed by file number), the channel's transaction log file, and the JVM-wide lock file that
 * enforces single-writer semantics on the data location. The provider also owns the
 * {@link #getStorageLocationIdentifier() storage location identifier} that is recorded in the
 * lock file and used in diagnostic messages.
 * <p>
 * The default wiring resolves files inside a single base directory using the framework default
 * {@link StorageDirectoryStructureProvider} and {@link StorageFileNameProvider}; use
 * {@link #Builder()} for fluent configuration of an alternative file system, base directory, or
 * naming scheme.
 *
 * @see StorageBackupFileProvider
 * @see Storage#FileProvider()
 */
public interface StorageLiveFileProvider extends StorageFileProvider
{
	/**
	 * Returns a String that uniquely identifies the storage location.
	 *
	 * @return a String that uniquely identifies the storage location.
	 */
	public String getStorageLocationIdentifier();

	/**
	 * Resolves the data file with the passed file number for the channel with the passed index.
	 *
	 * @param channelIndex the channel index.
	 * @param fileNumber   the per-channel data file number.
	 *
	 * @return the {@link AFile} representing the requested data file.
	 */
	public AFile provideDataFile(int channelIndex, long fileNumber);

	/**
	 * Resolves the transaction log file for the channel with the passed index.
	 *
	 * @param channelIndex the channel index.
	 *
	 * @return the {@link AFile} representing the channel's transaction log file.
	 */
	public AFile provideTransactionsFile(int channelIndex);

	/**
	 * Resolves the JVM-wide lock file that the storage uses to assert single-writer access to its
	 * data location.
	 *
	 * @return the {@link AFile} representing the storage lock file.
	 */
	public AFile provideLockFile();



	/**
	 * Static factory exposing the framework default storage directory name used when no custom
	 * directory is set on the {@link Builder}.
	 */
	public interface Defaults
	{
		public static String defaultStorageDirectory()
		{
			return "storage";
		}

	}

	
	
	/**
	 * Pseudo-constructor method to create a new {@link StorageLiveFileProvider} instance with default values
	 * provided by {@link StorageLiveFileProvider.Defaults}.
	 * <p>
	 * For explanations and customizing values, see {@link StorageLiveFileProvider.Builder}.
	 * 
	 * @return a new {@link StorageLiveFileProvider} instance.
	 * 
	 * @see StorageLiveFileProvider#New(ADirectory)
	 * @see StorageLiveFileProvider.Builder
	 * @see StorageLiveFileProvider.Defaults
	 */
	public static StorageLiveFileProvider New()
	{
		return Storage.FileProviderBuilder()
			.createFileProvider()
		;
	}
	
	/**
	 * Pseudo-constructor method to create a new {@link StorageLiveFileProvider} instance with the passed file
	 * as the storage directory and defaults provided by {@link StorageLiveFileProvider.Defaults}.
	 * <p>
	 * For explanations and customizing values, see {@link StorageLiveFileProvider.Builder}.
	 * 
	 * @param storageDirectory the directory where the storage will be located.
	 * 
	 * @return a new {@link StorageLiveFileProvider} instance.
	 * 
	 * @see StorageLiveFileProvider#New()
	 * @see StorageLiveFileProvider.Builder
	 * @see StorageLiveFileProvider.Defaults
	 */
	public static StorageLiveFileProvider New(final ADirectory storageDirectory)
	{
		return Storage.FileProviderBuilder(storageDirectory.fileSystem())
			.setDirectory(storageDirectory)
			.createFileProvider()
		;
	}
	
	/**
	 * 
	 * @param baseDirectory may <b>not</b> be null.
	 * @param fileHandlerCreator may <b>not</b> be null.
	 * @param deletionDirectory may be null.
	 * @param truncationDirectory may be null.
	 * @param structureProvider may <b>not</b> be null.
	 * @param fileNameProvider may <b>not</b> be null.
	 * 
	 * @return a new {@link StorageLiveFileProvider} instance
	 */
	public static StorageLiveFileProvider.Default New(
		final ADirectory                                   baseDirectory      ,
		final ADirectory                                   deletionDirectory  ,
		final ADirectory                                   truncationDirectory,
		final StorageDirectoryStructureProvider            structureProvider  ,
		final StorageFileNameProvider                      fileNameProvider   ,
		final PersistenceTypeDictionaryFileHandler.Creator fileHandlerCreator
	)
	{
		return new StorageLiveFileProvider.Default(
			notNull(baseDirectory)      , // base directory must at least be a relative directory name.
			mayNull(deletionDirectory)  ,
			mayNull(truncationDirectory),
			notNull(structureProvider)  ,
			notNull(fileNameProvider)   ,
			notNull(fileHandlerCreator)
		);
	}
	
	/**
	 * Default {@link StorageLiveFileProvider} implementation: extends {@link StorageFileProvider.Abstract}
	 * with the live-storage-specific {@link #getStorageLocationIdentifier()} derived from the base
	 * directory's path.
	 */
	public final class Default
	extends StorageFileProvider.Abstract
	implements StorageLiveFileProvider
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
		public String getStorageLocationIdentifier()
		{
			return this.baseDirectory().toPathString();
		}
					
	}
	
	/**
	 * Pseudo-constructor method to create a new {@link StorageLiveFileProvider.Builder} instance
	 * with the default file system.
	 * <p>
	 * For explanations and customizing values, see {@link StorageLiveFileProvider.Builder}.
	 * 
	 * @see Storage#DefaultFileSystem()
	 * @return a new {@link StorageLiveFileProvider.Builder} instance.
	 */
	public static StorageLiveFileProvider.Builder<?> Builder()
	{
		return Builder(Storage.DefaultFileSystem());
	}

	/**
	 * Pseudo-constructor method to create a new {@link StorageLiveFileProvider.Builder} instance.
	 * <p>
	 * For explanations and customizing values, see {@link StorageLiveFileProvider.Builder}.
	 * 
	 * @param fileSystem the file system to use
	 * @return a new {@link StorageLiveFileProvider.Builder} instance.
	 */
	public static StorageLiveFileProvider.Builder<?> Builder(final AFileSystem fileSystem)
	{
		return new StorageLiveFileProvider.Builder.Default(
			notNull(fileSystem)
		);
	}
	
	/**
	 * Fluent builder for {@link StorageLiveFileProvider} instances, extending the shared
	 * {@link StorageFileProvider.Builder} surface with a live-storage-specific
	 * {@link #createFileProvider()} that returns the more specific {@link StorageLiveFileProvider}
	 * type.
	 *
	 * @param <B> the self-type of the concrete builder.
	 */
	public interface Builder<B extends Builder<?>> extends StorageFileProvider.Builder<B>
	{
		@Override
		public StorageLiveFileProvider createFileProvider();



		/**
		 * Default {@link Builder} implementation. If no directory has been configured, the resulting
		 * file provider uses a relative directory named
		 * {@link StorageLiveFileProvider.Defaults#defaultStorageDirectory()} inside the current
		 * working directory.
		 */
		public class Default
		extends StorageFileProvider.Builder.Abstract<StorageLiveFileProvider.Builder.Default>
		implements StorageLiveFileProvider.Builder<StorageLiveFileProvider.Builder.Default>
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
				
				// note: relative root directory inside the current working directory
				return this.fileSystem().ensureRoot(Defaults.defaultStorageDirectory());
			}
		
			@Override
			public StorageLiveFileProvider createFileProvider()
			{
				return StorageLiveFileProvider.New(
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
