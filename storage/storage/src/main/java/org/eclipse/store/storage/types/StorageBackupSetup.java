package org.eclipse.store.storage.types;

import static org.eclipse.serializer.util.X.notNull;

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
 * Configures continuous, asynchronous backup for an embedded storage.
 * <p>
 * A {@link StorageBackupSetup} bundles the {@link StorageBackupFileProvider} that resolves where
 * backup files live with the wiring needed to install a backup-aware {@link StorageFileWriter}
 * provider and to spin up a {@link StorageBackupHandler} that mirrors live writes to the backup
 * location. When passed to a {@link StorageConfiguration}, this enables the storage to back up its
 * data and transaction files concurrently with normal operation.
 * <p>
 * If continuous backup is not desired, {@link StorageConfiguration} accepts {@code null} as the
 * backup setup; create a setup via one of the {@code New} factory methods only when backup is
 * actually wanted.
 *
 * @see StorageBackupHandler
 * @see StorageBackupFileProvider
 */
public interface StorageBackupSetup
{
	/**
	 * Returns the {@link StorageBackupFileProvider} that resolves where backup files are written.
	 *
	 * @return the configured {@link StorageBackupFileProvider}.
	 */
	public StorageBackupFileProvider backupFileProvider();

	/**
	 * Wraps the passed live-storage {@link StorageFileWriter.Provider} into a backup-aware variant
	 * that mirrors every write into the shared backup item queue.
	 *
	 * @param writerProvider the live-storage writer provider to wrap.
	 *
	 * @return a writer provider that emits backup-aware writers.
	 */
	public StorageFileWriter.Provider setupWriterProvider(
		StorageFileWriter.Provider writerProvider
	);

	/**
	 * Builds the {@link StorageBackupHandler} that drains the backup item queue and replays the
	 * captured writes against the backup file system.
	 *
	 * @param operationController            the operation controller of the host storage.
	 * @param writeController                the {@link StorageWriteController} of the host storage,
	 *                                       used by the backup handler to honor write-disabled state.
	 * @param backupDataFileValidatorCreator the validator-creator used to verify the backup files.
	 * @param storageTypeDictionary          the host storage's type dictionary.
	 *
	 * @return a fully wired {@link StorageBackupHandler} ready to be started.
	 */
	public StorageBackupHandler setupHandler(
		StorageOperationController       operationController           ,
		StorageWriteController           writeController               ,
		StorageDataFileValidator.Creator backupDataFileValidatorCreator,
		StorageTypeDictionary            storageTypeDictionary
	);
	

	
	/**
	 * Pseudo-constructor method to create a new {@link StorageBackupSetup} instance
	 * using the passed directory as the backup location.
	 * <p>
	 * For explanations and customizing values, see {@link StorageBackupSetup#New(StorageBackupFileProvider)}.
	 * 
	 * @param backupDirectory the directory where the backup shall be located.
	 * 
	 * @return a new {@link StorageBackupSetup} instance.
	 * 
	 * @see StorageBackupSetup#New(StorageBackupFileProvider)
	 * @see StorageBackupHandler
	 */
	
	public static StorageBackupSetup New(final ADirectory backupDirectory)
	{
		final StorageBackupFileProvider backupFileProvider = StorageBackupFileProvider.Builder(
			backupDirectory.fileSystem()
		)
			.setDirectory(backupDirectory)
			.createFileProvider()
		;
		return New(backupFileProvider);
	}
	
	/**
	 * Pseudo-constructor method to create a new {@link StorageBackupSetup} instance
	 * using the passed {@link StorageLiveFileProvider}.
	 * <p>
	 * A StorageBackupSetup basically defines where the backup files will be located by the {@link StorageBackupHandler}.
	 * 
	 * @param backupFileProvider the {@link StorageBackupFileProvider} to define where the backup files will be located.
	 * 
	 * @return a new {@link StorageBackupSetup} instance.
	 * 
	 * @see StorageBackupSetup#New(ADirectory)
	 * @see StorageBackupHandler
	 */
	public static StorageBackupSetup New(final StorageBackupFileProvider backupFileProvider)
	{
		return new StorageBackupSetup.Default(
			notNull(backupFileProvider) ,
			StorageBackupItemQueue.New()
		);
	}
	
	/**
	 * Default {@link StorageBackupSetup} implementation: pairs a {@link StorageBackupFileProvider}
	 * with a shared {@link StorageBackupItemQueue} that the backup-aware writer fills and that the
	 * {@link StorageBackupHandler} drains.
	 */
	public final class Default implements StorageBackupSetup
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////
		
		private final StorageBackupFileProvider backupFileProvider;
		private final StorageBackupItemQueue    itemQueue         ;
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////
		
		Default(
			final StorageBackupFileProvider backupFileProvider,
			final StorageBackupItemQueue    itemQueue
		)
		{
			super();
			this.backupFileProvider = backupFileProvider;
			this.itemQueue          = itemQueue         ;
		}
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////

		@Override
		public final StorageBackupFileProvider backupFileProvider()
		{
			return this.backupFileProvider;
		}
		
		@Override
		public StorageFileWriter.Provider setupWriterProvider(
			final StorageFileWriter.Provider writerProvider
		)
		{
			return StorageFileWriterBackupping.Provider(this.itemQueue, writerProvider);
		}
		
		@Override
		public StorageBackupHandler setupHandler(
			final StorageOperationController       operationController,
			final StorageWriteController           writeController    ,
			final StorageDataFileValidator.Creator validatorCreator   ,
			final StorageTypeDictionary            typeDictionary
		)
		{
			final int channelCount = operationController.channelCountProvider().getChannelCount();
			return StorageBackupHandler.New(
				this               ,
				channelCount       ,
				this.itemQueue     ,
				operationController,
				writeController    ,
				validatorCreator   ,
				typeDictionary
			);
		}
		
	}
	
}
