package org.eclipse.store.storage.embedded.configuration.types;

/*-
 * #%L
 * EclipseStore Storage Embedded Configuration
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

import static org.eclipse.serializer.util.X.notNull;

import java.time.Duration;
import java.util.function.Supplier;

import org.eclipse.serializer.afs.types.ADirectory;
import org.eclipse.serializer.afs.types.AFileSystem;
import org.eclipse.serializer.chars.XChars;
import org.eclipse.serializer.configuration.exceptions.ConfigurationException;
import org.eclipse.serializer.configuration.types.ByteSize;
import org.eclipse.serializer.configuration.types.Configuration;
import org.eclipse.serializer.configuration.types.ConfigurationBasedCreator;
import org.eclipse.store.afs.nio.types.NioFileSystem;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageFoundation;
import org.eclipse.store.storage.types.Storage;
import org.eclipse.store.storage.types.StorageChannelCountProvider;
import org.eclipse.store.storage.types.StorageConfiguration;
import org.eclipse.store.storage.types.StorageDataFileEvaluator;
import org.eclipse.store.storage.types.StorageEntityCacheEvaluator;
import org.eclipse.store.storage.types.StorageFileNameProvider;
import org.eclipse.store.storage.types.StorageHousekeepingController;
import org.eclipse.store.storage.types.StorageLiveFileProvider;


/**
 * Creator for a storage foundation, based on a configuration.
 *
 *
 */
public interface EmbeddedStorageFoundationCreatorConfigurationBased extends EmbeddedStorageFoundation.Creator
{
	/**
	 * Pseudo-constructor method to create a new foundation creator.
	 * @param configuration the configuration the foundation will be based on
	 * @return a new foundation creator
	 */
	public static EmbeddedStorageFoundationCreatorConfigurationBased New(
		final Configuration configuration
	)
	{
		return new EmbeddedStorageFoundationCreatorConfigurationBased.Default(
			notNull(configuration)
		);
	}
	
	public static class Default implements
	EmbeddedStorageFoundationCreatorConfigurationBased,
	EmbeddedStorageConfigurationPropertyNames
	{
		private final Configuration configuration;

		Default(
			final Configuration configuration
		)
		{
			super();
			this.configuration = configuration;
		}

		@Override
		public EmbeddedStorageFoundation<?> createEmbeddedStorageFoundation()
		{
			try
			{
				return this.internalCreateEmbeddedStorageFoundation();
			}
			catch(final ConfigurationException e)
			{
				throw e;
			}
			catch(final Exception e)
			{
				throw new ConfigurationException(this.configuration, e);
			}
		}
		
		
		private EmbeddedStorageFoundation<?> internalCreateEmbeddedStorageFoundation()
		{
			final EmbeddedStorageFoundation<?> foundation = EmbeddedStorage.Foundation();
			
			final AFileSystem fileSystem = this.createFileSystem(
				STORAGE_FILESYSTEM,
				NioFileSystem::New
			);
			
			final StorageConfiguration.Builder<?> configBuilder = Storage.ConfigurationBuilder()
				.setStorageFileProvider   (this.createFileProvider(fileSystem))
				.setChannelCountProvider  (this.createChannelCountProvider()  )
				.setHousekeepingController(this.createHousekeepingController(foundation))
				.setDataFileEvaluator     (this.createDataFileEvaluator()     )
				.setEntityCacheEvaluator  (this.createEntityCacheEvaluator()  )
			;

			this.configuration.opt(BACKUP_DIRECTORY)
				.filter(backupDirectory -> !XChars.isEmpty(backupDirectory))
				.map(fileSystem::resolvePath)
				.ifPresent(backupDirectory ->
				{
					final AFileSystem backupFileSystem = this.createFileSystem(
						BACKUP_FILESYSTEM,
						() -> fileSystem
					);
					configBuilder.setBackupSetup(Storage.BackupSetup(
						backupFileSystem.ensureDirectoryPath(backupDirectory)
					));
				})
			;

			foundation.setConfiguration(configBuilder.createConfiguration());
			
			return foundation;
		}
		
		private AFileSystem createFileSystem(
			final String                configurationKey         ,
			final Supplier<AFileSystem> defaultFileSystemSupplier
		)
		{
			final Configuration configuration = this.configuration.child(configurationKey);
			if(configuration != null)
			{
				final String  fileSystemTypeKey = this.configuration.opt(configurationKey + ".target").orElse(null);
				final boolean fileSystemTypeSet = !XChars.isEmpty(fileSystemTypeKey);
				for(final ConfigurationBasedCreator<AFileSystem> creator :
					ConfigurationBasedCreator.registeredCreators(AFileSystem.class))
				{
					if(!fileSystemTypeSet || creator.key().equals(fileSystemTypeKey))
					{
						final Configuration child = configuration.child(creator.key());
						if(child != null)
						{
							final AFileSystem fileSystem = creator.create(child);
							if(fileSystem != null)
							{
								return fileSystem;
							}
						}
					}
				}
				if(fileSystemTypeSet)
				{
					throw new IllegalStateException(
						"No " + configurationKey + " provider found for '" + fileSystemTypeKey +
						"'. Please ensure that all required dependencies are present."
					);
				}
			}
			
			return defaultFileSystemSupplier.get();
		}

		private StorageLiveFileProvider createFileProvider(final AFileSystem fileSystem)
		{
			final ADirectory baseDirectory = fileSystem.ensureDirectoryPath(
				fileSystem.resolvePath(
					this.configuration.opt(STORAGE_DIRECTORY)
						.orElse(StorageLiveFileProvider.Defaults.defaultStorageDirectory())
				)
			);
			
			final StorageFileNameProvider fileNameProvider = StorageFileNameProvider.New(
				this.configuration.opt(CHANNEL_DIRECTORY_PREFIX)
					.orElse(StorageFileNameProvider.Defaults.defaultChannelDirectoryPrefix()),
				this.configuration.opt(DATA_FILE_PREFIX)
					.orElse(StorageFileNameProvider.Defaults.defaultDataFilePrefix()),
				this.configuration.opt(DATA_FILE_SUFFIX)
					.orElse(StorageFileNameProvider.Defaults.defaultDataFileSuffix()),
				this.configuration.opt(TRANSACTION_FILE_PREFIX)
					.orElse(StorageFileNameProvider.Defaults.defaultTransactionsFilePrefix()),
				this.configuration.opt(TRANSACTION_FILE_SUFFIX)
					.orElse(StorageFileNameProvider.Defaults.defaultTransactionsFileSuffix()),
				this.configuration.opt(RESCUED_FILE_SUFFIX)
					.orElse(StorageFileNameProvider.Defaults.defaultRescuedFileSuffix()),
				this.configuration.opt(TYPE_DICTIONARY_FILE_NAME)
					.orElse(StorageFileNameProvider.Defaults.defaultTypeDictionaryFileName()),
				this.configuration.opt(LOCK_FILE_NAME)
					.orElse(StorageFileNameProvider.Defaults.defaultLockFileName())
			);
			
			final StorageLiveFileProvider.Builder<?> builder = Storage.FileProviderBuilder(fileSystem)
				.setDirectory(baseDirectory)
				.setFileNameProvider(fileNameProvider)
			;
			
			this.configuration.opt(DELETION_DIRECTORY)
				.filter(deletionDirectory -> !XChars.isEmpty(deletionDirectory))
				.ifPresent(deletionDirectory -> builder.setDeletionDirectory(
					fileSystem.ensureDirectoryPath(deletionDirectory)
				))
			;
			
			this.configuration.opt(TRUNCATION_DIRECTORY)
				.filter(truncationDirectory -> !XChars.isEmpty(truncationDirectory))
				.ifPresent(truncationDirectory -> builder.setTruncationDirectory(
					fileSystem.ensureDirectoryPath(truncationDirectory)
				))
			;
			
			return builder.createFileProvider();
		}

		private StorageChannelCountProvider createChannelCountProvider()
		{
			return Storage.ChannelCountProvider(
				this.configuration.optInteger(CHANNEL_COUNT)
					.orElse(StorageChannelCountProvider.Defaults.defaultChannelCount())
			);
		}

		private StorageHousekeepingController createHousekeepingController(final EmbeddedStorageFoundation<?> foundation)
		{
			StorageHousekeepingController controller = Storage.HousekeepingController(
				this.configuration.opt(HOUSEKEEPING_INTERVAL, Duration.class)
					.map(Duration::toMillis)
					.orElse(StorageHousekeepingController.Defaults.defaultHousekeepingIntervalMs()),
				this.configuration.opt(HOUSEKEEPING_TIME_BUDGET, Duration.class)
					.map(Duration::toNanos)
					.orElse(StorageHousekeepingController.Defaults.defaultHousekeepingTimeBudgetNs())
			);
			
			if(this.configuration.optBoolean(HOUSEKEEPING_ADAPTIVE).orElse(false))
			{
				controller = StorageHousekeepingController.Adaptive(
					controller,
					foundation.getEntityMarkMonitorCreator()::cachedInstance,
					this.configuration.opt(HOUSEKEEPING_INCREASE_THRESHOLD, Duration.class)
						.map(Duration::toMillis)
						.orElse(StorageHousekeepingController.Adaptive.Defaults.defaultAdaptiveHousekeepingIncreaseThresholdMs()),
					this.configuration.opt(HOUSEKEEPING_INCREASE_AMOUNT, Duration.class)
						.map(Duration::toNanos)
						.orElse(StorageHousekeepingController.Adaptive.Defaults.defaultAdaptiveHousekeepingIncreaseAmountNs()),
					this.configuration.opt(HOUSEKEEPING_MAXIMUM_TIME_BUDGET, Duration.class)
						.map(Duration::toNanos)
						.orElse(StorageHousekeepingController.Adaptive.Defaults.defaultAdaptiveHousekeepingMaximumTimeBudgetNs()),
					foundation
				);
			}
			
			return controller;
		}

		private StorageDataFileEvaluator createDataFileEvaluator()
		{
			return Storage.DataFileEvaluator(
				this.configuration.opt(DATA_FILE_MINIMUM_SIZE, ByteSize.class)
					.map(byteSize -> (int)byteSize.bytes())
					.orElse(StorageDataFileEvaluator.Defaults.defaultFileMinimumSize()),
				this.configuration.opt(DATA_FILE_MAXIMUM_SIZE, ByteSize.class)
					.map(byteSize -> (int)byteSize.bytes())
					.orElse(StorageDataFileEvaluator.Defaults.defaultFileMaximumSize()),
				this.configuration.optDouble(DATA_FILE_MINIMUM_USE_RATIO)
					.orElse(StorageDataFileEvaluator.Defaults.defaultMinimumUseRatio()),
				this.configuration.optBoolean(DATA_FILE_CLEANUP_HEAD_FILE)
					.orElse(StorageDataFileEvaluator.Defaults.defaultResolveHeadfile()),
				this.configuration.opt(TRANSACTION_FILE_MAXIMUM_SIZE, ByteSize.class)
					.map(byteSize -> (int)byteSize.bytes())
					.orElse(StorageDataFileEvaluator.Defaults.defaultTransactionFileMaximumSize())
			);
		}

		private StorageEntityCacheEvaluator createEntityCacheEvaluator()
		{
			return Storage.EntityCacheEvaluator(
				this.configuration.opt(ENTITY_CACHE_TIMEOUT, Duration.class)
					.map(Duration::toMillis)
					.orElse(StorageEntityCacheEvaluator.Defaults.defaultTimeoutMs()),
				this.configuration.optLong(ENTITY_CACHE_THRESHOLD)
					.orElse(StorageEntityCacheEvaluator.Defaults.defaultCacheThreshold())
			);
		}
		
	}
	
}
