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
import org.eclipse.store.storage.types.StorageChunkChecksumPolicy;
import org.eclipse.store.storage.types.StorageChunkChecksumPolicy.Anomaly;
import org.eclipse.store.storage.types.StorageChunkChecksumPolicy.Reaction;
import org.eclipse.store.storage.types.StorageChunkChecksumProvider;
import org.eclipse.store.storage.types.StorageConfiguration;
import org.eclipse.store.storage.types.StorageDataFileEvaluator;
import org.eclipse.store.storage.types.StorageEntityCacheEvaluator;
import org.eclipse.store.storage.types.StorageFileNameProvider;
import org.eclipse.store.storage.types.StorageHousekeepingController;
import org.eclipse.store.storage.types.StorageLiveFileProvider;
import org.eclipse.store.storage.types.StorageReferenceValidationPolicy;


/**
 * {@link EmbeddedStorageFoundation.Creator} implementation that builds an
 * {@link EmbeddedStorageFoundation} from a generic {@link Configuration}.
 * <p>
 * Each call to {@link #createEmbeddedStorageFoundation()} reads the property names defined in
 * {@link EmbeddedStorageConfigurationPropertyNames} from the configuration and translates them into the
 * matching parts of a {@link StorageConfiguration} (file provider, channel count provider, housekeeping
 * controller, data file evaluator, entity cache evaluator, optional backup setup, and the underlying
 * {@link AFileSystem} for both the storage and the backup directory). Unset properties fall back to the
 * defaults of the respective storage component.
 *
 * @see EmbeddedStorageConfiguration
 * @see EmbeddedStorageConfigurationBuilder
 * @see EmbeddedStorageConfigurationPropertyNames
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
	
	/**
	 * Default implementation that wraps a {@link Configuration} and assembles an
	 * {@link EmbeddedStorageFoundation} from it on each call to
	 * {@link #createEmbeddedStorageFoundation()}.
	 * <p>
	 * Any non-{@link ConfigurationException} thrown during assembly is wrapped in a
	 * {@link ConfigurationException} that carries the offending {@link Configuration} as context.
	 */
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

			final StorageChunkChecksumProvider ccp = this.createChunkChecksumProvider();
			if(ccp != null)
			{
				configBuilder.setChunkChecksumProvider(ccp);
			}

			this.configuration.opt(REFERENCE_VALIDATION)
				.map(StorageReferenceValidationPolicy::parse)
				.ifPresent(configBuilder::setReferenceValidationPolicy)
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

		/**
		 * Builds a {@link StorageChunkChecksumProvider} from the {@code chunk-checksum-*} properties, or returns
		 * {@code null} (the sentinel telling the caller to skip the setter and keep the framework default) when
		 * none is present. The profile supplies a coherent base policy; any present per-axis override replaces
		 * that axis and the result is handed to {@link StorageChunkChecksumPolicy#NewCustom} &mdash; whose own
		 * fail-early validation is the only policy guard (no bespoke sanity layer). Two documented silent rules:
		 * {@code algorithm=none} forces an off policy (profile / overrides ignored), and {@code seed} is unused
		 * by any algorithm other than {@code sha256-chained}.
		 */
		private StorageChunkChecksumProvider createChunkChecksumProvider()
		{
			if(!this.hasAnyChunkChecksumProperty())
			{
				return null;
			}

			final String algorithm = this.configuration.opt(CHUNK_CHECKSUM_ALGORITHM).orElse("sha256-chained").trim().toLowerCase();

			// none => off, regardless of profile / overrides (documented silent rule); short-circuit so a
			// discarded policy can never throw.
			if("none".equals(algorithm))
			{
				return StorageChunkChecksumProvider.NewNone();
			}

			final StorageChunkChecksumPolicy policy = this.createChunkChecksumPolicy();

			switch(algorithm)
			{
				case "crc32c":
					return StorageChunkChecksumProvider.NewCrc32c(policy);
				case "sha256-chained":
					return StorageChunkChecksumProvider.NewSha256Chained(
						policy,
						this.configuration.opt(CHUNK_CHECKSUM_SEED).map(this::parseChunkChecksumSeed).orElse(null)
					);
				default:
					throw new ConfigurationException(
						this.configuration,
						"Unknown " + CHUNK_CHECKSUM_ALGORITHM + ": '" + algorithm
						+ "' (expected none, crc32c or sha256-chained)."
					);
			}
		}

		private boolean hasAnyChunkChecksumProperty()
		{
			for(final String key : this.configuration.keys())
			{
				if(key.startsWith("chunk-checksum-"))
				{
					return true;
				}
			}
			return false;
		}

		private StorageChunkChecksumPolicy createChunkChecksumPolicy()
		{
			final StorageChunkChecksumPolicy base = this.chunkChecksumProfile();

			// Each axis defaults off the profile base; any present key overrides it. NewCustom re-validates the
			// resulting tuple and throws on a contradiction (the only policy guard) — wrapped as a
			// ConfigurationException by createEmbeddedStorageFoundation().
			return StorageChunkChecksumPolicy.NewCustom(
				this.configuration.optBoolean(CHUNK_CHECKSUM_EMIT)              .orElse(base.emit())              ,
				this.configuration.optBoolean(CHUNK_CHECKSUM_VERIFY)            .orElse(base.verify())            ,
				this.chunkChecksumReaction(CHUNK_CHECKSUM_ON_CHECKSUM_MISMATCH, base.reactionTo(Anomaly.CHECKSUM_MISMATCH))      ,
				this.chunkChecksumReaction(CHUNK_CHECKSUM_ON_BOUNDARY_MISMATCH, base.reactionTo(Anomaly.CHUNK_BOUNDARY_MISMATCH)),
				this.chunkChecksumReaction(CHUNK_CHECKSUM_ON_UNKNOWN_KIND     , base.reactionTo(Anomaly.UNKNOWN_KIND))           ,
				this.chunkChecksumReaction(CHUNK_CHECKSUM_ON_MISSING_HEADER   , base.reactionTo(Anomaly.MISSING_HEADER))         ,
				this.chunkChecksumReaction(CHUNK_CHECKSUM_ON_UNCOVERED_DATA   , base.reactionTo(Anomaly.UNCOVERED_DATA))         ,
				this.configuration.optBoolean(CHUNK_CHECKSUM_REQUIRE_COVERAGE)   .orElse(base.requireCoverage())   ,
				this.configuration.optBoolean(CHUNK_CHECKSUM_CONTINUOUS_COVERAGE).orElse(base.continuousCoverage())
			);
		}

		private StorageChunkChecksumPolicy chunkChecksumProfile()
		{
			final String profile = this.configuration.opt(CHUNK_CHECKSUM_PROFILE).orElse("default").trim().toLowerCase();
			switch(profile)
			{
				case "default":                return StorageChunkChecksumPolicy.New()                    ;
				case "off":                    return StorageChunkChecksumPolicy.NewOff()                 ;
				case "observe":                return StorageChunkChecksumPolicy.NewObserve()             ;
				case "strict":                 return StorageChunkChecksumPolicy.NewStrict()              ;
				case "strict-tolerate-legacy": return StorageChunkChecksumPolicy.NewStrictTolerateLegacy();
				default:
					throw new ConfigurationException(
						this.configuration,
						"Unknown " + CHUNK_CHECKSUM_PROFILE + ": '" + profile
						+ "' (expected default, off, observe, strict or strict-tolerate-legacy)."
					);
			}
		}

		private Reaction chunkChecksumReaction(final String key, final Reaction defaultReaction)
		{
			return this.configuration.opt(key)
				.map(token -> this.parseChunkChecksumReaction(key, token))
				.orElse(defaultReaction);
		}

		private Reaction parseChunkChecksumReaction(final String key, final String token)
		{
			switch(token.trim().toLowerCase())
			{
				case "ignore": return Reaction.IGNORE;
				case "log":    return Reaction.LOG   ;
				case "fail":   return Reaction.FAIL  ;
				default:
					throw new ConfigurationException(
						this.configuration,
						"Unknown reaction for " + key + ": '" + token + "' (expected ignore, log or fail)."
					);
			}
		}

		private byte[] parseChunkChecksumSeed(final String hex)
		{
			final String s = hex.trim();
			if(s.length() != 64)
			{
				throw new ConfigurationException(
					this.configuration,
					CHUNK_CHECKSUM_SEED + " must be a 64-character hex string (32 bytes), got length " + s.length() + "."
				);
			}
			final byte[] out = new byte[s.length() / 2];
			for(int i = 0; i < out.length; i++)
			{
				final int hi = Character.digit(s.charAt(i << 1)      , 16);
				final int lo = Character.digit(s.charAt((i << 1) + 1), 16);
				if(hi < 0 || lo < 0)
				{
					throw new ConfigurationException(this.configuration, CHUNK_CHECKSUM_SEED + " is not a valid hex string.");
				}
				out[i] = (byte)((hi << 4) | lo);
			}
			return out;
		}

	}
	
}
