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

import java.io.File;
import java.time.Duration;

import org.eclipse.serializer.collections.types.XGettingCollection;
import org.eclipse.serializer.configuration.types.ByteSize;
import org.eclipse.serializer.configuration.types.Configuration;
import org.eclipse.serializer.configuration.types.ConfigurationValueMapperProvider;
import org.eclipse.serializer.typing.KeyValue;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageFoundation;
import org.eclipse.store.storage.types.StorageEntityCacheEvaluator;


/**
 * A specialized {@link Configuration.Builder}, containing setter methods for
 * properties used in storage configurations.
 * <p>
 * Use {@link #createEmbeddedStorageFoundation()} as a shortcut to create a
 * storage foundation and finally a storage manager:
 * <pre>
 * EmbeddedStorageManager storage = EmbeddedStorageConfigurationBuilder.New()
 * 	.setChannelCount(4)
 * 	.setStorageDirectory("/path/to/storage/")
 * 	.createEmbeddedStorageFoundation()
 * 	.start();
 * </pre>
 * Or load a configuration from an external source:
 * <pre>
 * EmbeddedStorageManager storage = EmbeddedStorageConfiguration.load()
 * 	.createEmbeddedStorageFoundation()
 * 	.start();
 * </pre>
 *
 * @see EmbeddedStorageConfiguration
 * @see EmbeddedStorageConfigurationPropertyNames
 *
 */
public interface EmbeddedStorageConfigurationBuilder extends Configuration.Builder
{
	/**
	 * The base directory of the storage in the file system.
	 * @param storageDirectory the storage directory
	 * @return this
	 */
	public EmbeddedStorageConfigurationBuilder setStorageDirectory(String storageDirectory);

	/**
	 * The base directory of the storage in the file system.
	 *
	 * @param storageDirectoryInUserHome relative location in the user home directory
	 * @return this
	 */
	public default EmbeddedStorageConfigurationBuilder setStorageDirectoryInUserHome(
		final String storageDirectoryInUserHome
	)
	{
		final File userHomeDir = new File(System.getProperty("user.home"));
		this.setStorageDirectory(new File(userHomeDir, storageDirectoryInUserHome).getAbsolutePath());
		return this;
	}

	/**
	 * The deletion directory.
	 * @param deletionDirectory the deletion directory
	 * @return this
	 */
	public EmbeddedStorageConfigurationBuilder setDeletionDirectory(String deletionDirectory);

	/**
	 * The truncation directory.
	 * @param truncationDirectory the trunctation directory
	 * @return this
	 */
	public EmbeddedStorageConfigurationBuilder setTruncationDirectory(String truncationDirectory);

	/**
	 * The backup directory.
	 * @param backupDirectory the backup directory
	 * @return this
	 */
	public EmbeddedStorageConfigurationBuilder setBackupDirectory(String backupDirectory);

	/**
	 * The backup directory.
	 *
	 * @param backupDirectoryInUserHome relative location in the user home directory
	 * @return this
	 */
	public default EmbeddedStorageConfigurationBuilder setBackupDirectoryInUserHome(
		final String backupDirectoryInUserHome
	)
	{
		final File userHomeDir = new File(System.getProperty("user.home"));
		this.setBackupDirectory(new File(userHomeDir, backupDirectoryInUserHome).getAbsolutePath());
		return this;
	}

	/**
	 * The number of threads and number of directories used by the storage
	 * engine. Every thread has exclusive access to its directory. Default is
	 * <code>1</code>.
	 *
	 * @param channelCount the new channel count, must be a power of 2
	 * @return this
	 */
	public EmbeddedStorageConfigurationBuilder setChannelCount(int channelCount);

	/**
	 * Name prefix of the subdirectories used by the channel threads. Default is
	 * <code>"channel_"</code>.
	 *
	 * @param channelDirectoryPrefix new prefix
	 * @return this
	 */
	public EmbeddedStorageConfigurationBuilder setChannelDirectoryPrefix(String channelDirectoryPrefix);

	/**
	 * Name prefix of the storage files. Default is <code>"channel_"</code>.
	 *
	 * @param dataFilePrefix new prefix
	 * @return this
	 */
	public EmbeddedStorageConfigurationBuilder setDataFilePrefix(String dataFilePrefix);

	/**
	 * Name suffix of the storage files. Default is <code>".dat"</code>.
	 *
	 * @param dataFileSuffix new suffix
	 * @return this
	 */
	public EmbeddedStorageConfigurationBuilder setDataFileSuffix(String dataFileSuffix);

	/**
	 * Name prefix of the storage transaction file. Default is <code>"transactions_"</code>.
	 *
	 * @param transactionFilePrefix new prefix
	 * @return this
	 */
	public EmbeddedStorageConfigurationBuilder setTransactionFilePrefix(String transactionFilePrefix);

	/**
	 * Name suffix of the storage transaction file. Default is <code>".sft"</code>.
	 *
	 * @param transactionFileSuffix new suffix
	 * @return this
	 */
	public EmbeddedStorageConfigurationBuilder setTransactionFileSuffix(String transactionFileSuffix);

	/**
	 * The name of the dictionary file. Default is
	 * <code>"PersistenceTypeDictionary.ptd"</code>.
	 *
	 * @param typeDictionaryFileName new name
	 * @return this
	 */
	public EmbeddedStorageConfigurationBuilder setTypeDictionaryFileName(String typeDictionaryFileName);

	/**
	 * Suffix used to mark storage files that have been rescued during recovery from a corrupt state, instead
	 * of being deleted. Default is <code>"bak"</code>.
	 *
	 * @param rescuedFileSuffix new suffix
	 * @return this
	 */
	public EmbeddedStorageConfigurationBuilder setRescuedFileSuffix(String rescuedFileSuffix);

	/**
	 * Name of the storage lock file used to prevent concurrent access to the storage directory by multiple
	 * processes. Default is <code>"used.lock"</code>.
	 *
	 * @param lockFileName new name
	 * @return this
	 */
	public EmbeddedStorageConfigurationBuilder setLockFileName(String lockFileName);

	/**
	 * Interval for the housekeeping. This is work like garbage
	 * collection or cache checking. In combination with
	 * {@link #setHousekeepingTimeBudget(Duration)} the maximum processor
	 * time for housekeeping work can be set. Default is one second.
	 *
	 * @param housekeepingInterval the new interval
	 * @return this
	 *
	 * @see #setHousekeepingTimeBudget(Duration)
	 */
	public EmbeddedStorageConfigurationBuilder setHousekeepingInterval(Duration housekeepingInterval);

	/**
	 * Duration used for each housekeeping cycle. However, no
	 * matter how low the number is, one item of work will always be completed.
	 * But if there is nothing to clean up, no processor time will be wasted.
	 * Default is 10 milliseconds = 0.01 seconds.
	 *
	 * @param housekeepingTimeBudget the new time budget
	 * @return this
	 *
	 * @see #setHousekeepingInterval(Duration)
	 */
	public EmbeddedStorageConfigurationBuilder setHousekeepingTimeBudget(Duration housekeepingTimeBudget);

	/**
	 * Usage of an adaptive housekeeping controller, which will increase the time budgets on demand,
	 * if the garbage collector needs more time to reach the sweeping phase.
	 * 
	 * @param adaptive <code>true</code> if an adaptive controller should be used
	 * @return this
	 * 
	 * @see #setHousekeepingIncreaseThreshold(Duration)
	 * @see #setHousekeepingIncreaseAmount(Duration)
	 * @see #setHousekeepingMaximumTimeBudget(Duration)
	 */
	public EmbeddedStorageConfigurationBuilder setHousekeepingAdaptive(boolean adaptive);

	/**
	 * The threshold of the adaption cycle to calculate new budgets for the housekeeping process.
	 * <p>
	 * Only used when {@link #setHousekeepingAdaptive(boolean)} is <code>true</code>.
	 * 
	 * @param housekeepingIncreaseThreshold the new increase threshold
	 * @return this
	 */
	public EmbeddedStorageConfigurationBuilder setHousekeepingIncreaseThreshold(Duration housekeepingIncreaseThreshold);

	/**
	 * The amount the housekeeping budgets will be increased each cycle.
	 * <p>
	 * Only used when {@link #setHousekeepingAdaptive(boolean)} is <code>true</code>.
	 * 
	 * @param housekeepingIncreaseAmount the new increase amount
	 * @return this
	 */
	public EmbeddedStorageConfigurationBuilder setHousekeepingIncreaseAmount(Duration housekeepingIncreaseAmount);

	/**
	 * The upper limit of the housekeeping time budgets.
	 * <p>
	 * Only used when {@link #setHousekeepingAdaptive(boolean)} is <code>true</code>.
	 * 
	 * @param housekeepingMaximumTimeBudget the new maximum time budget
	 * @return this
	 */
	public EmbeddedStorageConfigurationBuilder setHousekeepingMaximumTimeBudget(Duration housekeepingMaximumTimeBudget);

	/**
	 * Abstract threshold value for the lifetime of entities in the cache. See
	 * {@link StorageEntityCacheEvaluator#New(long, long)}. Default is <code>1.000.000.000</code>.
	 *
	 * @param entityCacheThreshold the new threshold
	 * @return this
	 *
	 * @see #setEntityCacheTimeout(Duration)
	 */
	public EmbeddedStorageConfigurationBuilder setEntityCacheThreshold(long entityCacheThreshold);

	/**
	 * Timeout for the entity cache evaluator. If an entity
	 * wasn't accessed in this timespan it will be removed from the cache.
	 * Default is one day.
	 * See {@link StorageEntityCacheEvaluator#New(long, long)}.
	 *
	 * @param entityCacheTimeout the new timeout
	 * @return this
	 *
	 * @see Duration
	 * @see #setEntityCacheThreshold(long)
	 */
	public EmbeddedStorageConfigurationBuilder setEntityCacheTimeout(Duration entityCacheTimeout);

	/**
	 * Minimum file size for a data file to avoid cleaning it up. Default is 1 MiB.
	 *
	 * @param dataFileMinimumSize the new minimum file size
	 * @return this
	 *
	 * @see #setDataFileMinimumUseRatio(double)
	 */
	public EmbeddedStorageConfigurationBuilder setDataFileMinimumSize(ByteSize dataFileMinimumSize);

	/**
	 * Maximum file size for a data file to avoid cleaning it up. Default is 8 MiB.
	 *
	 * @param dataFileMaximumSize the new maximum file size
	 * @return this
	 *
	 * @see #setDataFileMinimumUseRatio(double)
	 */
	public EmbeddedStorageConfigurationBuilder setDataFileMaximumSize(ByteSize dataFileMaximumSize);

	/**
	 * The ratio (value in ]0.0;1.0]) of non-gap data contained in a storage file to prevent
	 * the file from being dissolved. "Gap" data is anything that is not the latest version of an entity's data,
	 * including older versions of an entity and "comment" bytes (a sequence of bytes beginning with its length
	 * as a negative value length header).<br>
	 * The closer this value is to 1.0 (100%), the less disk space is occupied by storage files, but the more
	 * file dissolving (data transfers to new files) is required and vice versa.
	 *
	 * @param dataFileMinimumUseRatio the new minimum use ratio
	 * @return this
	 */
	public EmbeddedStorageConfigurationBuilder setDataFileMinimumUseRatio(double dataFileMinimumUseRatio);

	/**
	 * A flag defining whether the current head file (the only file actively written to)
	 * shall be subjected to file cleanups as well.
	 *
	 * @param dataFileCleanupHeadFile the new clean head file
	 * @return this
	 */
	public EmbeddedStorageConfigurationBuilder setDataFileCleanupHeadFile(boolean dataFileCleanupHeadFile);

	/**
	 * Maximum file size for a transaction file to avoid cleaning it up. Default is 1 GiB.
	 *
	 * @param transactionFileMaximumSize the new maximum file size
	 * @return this
	 */
	public EmbeddedStorageConfigurationBuilder setTransactionFileMaximumSize(ByteSize transactionFileMaximumSize);

	/**
	 * Store-time validation of trusted reference object ids: {@code off}, {@code log}, {@code fail}
	 * or {@code heal}. Default is {@code log}.
	 *
	 * @param referenceValidation the policy token
	 * @return this
	 */
	public EmbeddedStorageConfigurationBuilder setReferenceValidation(String referenceValidation);

	/**
	 * Reaction of the storage garbage collector to an encountered zombie object id:
	 * {@code log} or {@code fail}. Default is {@code log}.
	 *
	 * @param gcZombieOidHandling the reaction token
	 * @return this
	 */
	public EmbeddedStorageConfigurationBuilder setGcZombieOidHandling(String gcZombieOidHandling);

	/**
	 * The primary chunk-checksum algorithm: {@code none}, {@code crc32c} or
	 * {@code sha256-chained}. Default is {@code sha256-chained}. Setting any {@code chunk-checksum-*} property
	 * activates declarative configuration of the feature; leaving them all unset keeps the framework default.
	 *
	 * @param chunkChecksumAlgorithm the algorithm token
	 * @return this
	 */
	public EmbeddedStorageConfigurationBuilder setChunkChecksumAlgorithm(String chunkChecksumAlgorithm);

	/**
	 * The chunk-checksum base policy profile: {@code default}, {@code off}, {@code observe}, {@code strict}
	 * or {@code strict-tolerate-legacy}. Default is {@code default}.
	 *
	 * @param chunkChecksumProfile the profile token
	 * @return this
	 */
	public EmbeddedStorageConfigurationBuilder setChunkChecksumProfile(String chunkChecksumProfile);

	/**
	 * The initial chain seed for the {@code sha256-chained} algorithm, as a hex string (64 chars = 32 bytes).
	 * Unused by any other algorithm.
	 *
	 * @param chunkChecksumSeed the seed as a hex string
	 * @return this
	 */
	public EmbeddedStorageConfigurationBuilder setChunkChecksumSeed(String chunkChecksumSeed);

	/**
	 * Expert override: whether to emit checksum records on write. Defaults to the profile's value.
	 *
	 * @param chunkChecksumEmit whether to emit
	 * @return this
	 */
	public EmbeddedStorageConfigurationBuilder setChunkChecksumEmit(boolean chunkChecksumEmit);

	/**
	 * Expert override: whether to recompute and check checksum records on load. Defaults to the profile's value.
	 *
	 * @param chunkChecksumVerify whether to verify
	 * @return this
	 */
	public EmbeddedStorageConfigurationBuilder setChunkChecksumVerify(boolean chunkChecksumVerify);

	/**
	 * Expert override: reaction ({@code ignore} / {@code log} / {@code fail}) to a checksum mismatch.
	 * Defaults to the profile's value.
	 *
	 * @param reaction the reaction token
	 * @return this
	 */
	public EmbeddedStorageConfigurationBuilder setChunkChecksumOnChecksumMismatch(String reaction);

	/**
	 * Expert override: reaction ({@code ignore} / {@code log} / {@code fail}) to a chunk-boundary mismatch.
	 * Defaults to the profile's value.
	 *
	 * @param reaction the reaction token
	 * @return this
	 */
	public EmbeddedStorageConfigurationBuilder setChunkChecksumOnBoundaryMismatch(String reaction);

	/**
	 * Expert override: reaction ({@code ignore} / {@code log} / {@code fail}) to an unknown record kind.
	 * Defaults to the profile's value.
	 *
	 * @param reaction the reaction token
	 * @return this
	 */
	public EmbeddedStorageConfigurationBuilder setChunkChecksumOnUnknownKind(String reaction);

	/**
	 * Expert override: reaction ({@code ignore} / {@code log} / {@code fail}) to a missing file header.
	 * Defaults to the profile's value.
	 *
	 * @param reaction the reaction token
	 * @return this
	 */
	public EmbeddedStorageConfigurationBuilder setChunkChecksumOnMissingHeader(String reaction);

	/**
	 * Expert override: reaction ({@code ignore} / {@code log} / {@code fail}) to uncovered data.
	 * Defaults to the profile's value.
	 *
	 * @param reaction the reaction token
	 * @return this
	 */
	public EmbeddedStorageConfigurationBuilder setChunkChecksumOnUncoveredData(String reaction);

	/**
	 * Expert override: whether missing/uncovered files are raised as anomalies. Defaults to the profile's value.
	 *
	 * @param chunkChecksumRequireCoverage whether coverage is required
	 * @return this
	 */
	public EmbeddedStorageConfigurationBuilder setChunkChecksumRequireCoverage(boolean chunkChecksumRequireCoverage);

	/**
	 * Expert override: whether enabling emit forces an immediately-covered head file. Defaults to the
	 * profile's value.
	 *
	 * @param chunkChecksumContinuousCoverage whether coverage is continuous
	 * @return this
	 */
	public EmbeddedStorageConfigurationBuilder setChunkChecksumContinuousCoverage(boolean chunkChecksumContinuousCoverage);

	/**
	 * Creates an {@link EmbeddedStorageFoundation} based on the settings of this builder.
	 *
	 * @return an {@link EmbeddedStorageFoundation}
	 *
	 * @see EmbeddedStorageFoundationCreatorConfigurationBased
	 */
	public default EmbeddedStorageFoundation<?> createEmbeddedStorageFoundation()
	{
		return EmbeddedStorageFoundationCreatorConfigurationBased.New(
			this.buildConfiguration()
		)
		.createEmbeddedStorageFoundation()
		;
	}



	/**
	 * Pseudo-constructor method to create a new builder.
	 *
	 * @return a new {@link EmbeddedStorageConfigurationBuilder}
	 */
	public static EmbeddedStorageConfigurationBuilder New()
	{
		return new Default(Configuration.Builder());
	}

	/**
	 * Pseudo-constructor method to create a new builder, wrapping an existing one.
	 *
	 * @param delegate the delegate to wrap
	 * @return a new {@link EmbeddedStorageConfigurationBuilder}
	 */
	public static EmbeddedStorageConfigurationBuilder New(
		final Configuration.Builder delegate
	)
	{
		return new Default(
			notNull(delegate)
		);
	}


	/**
	 * Default implementation of {@link EmbeddedStorageConfigurationBuilder} that wraps a generic
	 * {@link Configuration.Builder} and translates each typed setter into a corresponding
	 * {@link EmbeddedStorageConfigurationPropertyNames property name}/value pair on the delegate.
	 */
	public static class Default implements EmbeddedStorageConfigurationBuilder, EmbeddedStorageConfigurationPropertyNames
	{
		private final Configuration.Builder delegate;

		Default(
			final Configuration.Builder delegate
		)
		{
			super();
			this.delegate = delegate;
		}

		// ############################
		// Delegate methods
		// ############################

		@Override
		public EmbeddedStorageConfigurationBuilder valueMapperProvider(
			final ConfigurationValueMapperProvider valueMapperProvider
		)
		{
			this.delegate.valueMapperProvider(valueMapperProvider);
			return this;
		}

		@Override
		public EmbeddedStorageConfigurationBuilder set(
			final String key  ,
			final String value
		)
		{
			this.delegate.set(key, value);
			return this;
		}

		@Override
		public EmbeddedStorageConfigurationBuilder setAll(
			final XGettingCollection<KeyValue<String, String>> properties
		)
		{
			this.delegate.setAll(properties);
			return this;
		}

		@SuppressWarnings("unchecked")
		@Override
		public EmbeddedStorageConfigurationBuilder setAll(
			final KeyValue<String, String>... properties
		)
		{
			this.delegate.setAll(properties);
			return this;
		}

		@Override
		public EmbeddedStorageConfigurationBuilder child(
			final String key
		)
		{
			this.delegate.child(key);
			return this;
		}

		@Override
		public Configuration buildConfiguration()
		{
			return this.delegate.buildConfiguration();
		}

		// ############################
		// Builder methods
		// ############################

		@Override
		public EmbeddedStorageConfigurationBuilder setStorageDirectory(
			final String storageDirectory
		)
		{
			return this.set(STORAGE_DIRECTORY, storageDirectory);
		}

		@Override
		public EmbeddedStorageConfigurationBuilder setDeletionDirectory(
			final String deletionDirectory
		)
		{
			return this.set(DELETION_DIRECTORY, deletionDirectory);
		}

		@Override
		public EmbeddedStorageConfigurationBuilder setTruncationDirectory(
			final String truncationDirectory
		)
		{
			return this.set(TRUNCATION_DIRECTORY, truncationDirectory);
		}

		@Override
		public EmbeddedStorageConfigurationBuilder setBackupDirectory(
			final String backupDirectory
		)
		{
			return this.set(BACKUP_DIRECTORY, backupDirectory);
		}

		@Override
		public EmbeddedStorageConfigurationBuilder setChannelCount(
			final int channelCount
		)
		{
			return this.set(CHANNEL_COUNT, Integer.toString(channelCount));
		}

		@Override
		public EmbeddedStorageConfigurationBuilder setChannelDirectoryPrefix(
			final String channelDirectoryPrefix
		)
		{
			return this.set(CHANNEL_DIRECTORY_PREFIX, channelDirectoryPrefix);
		}

		@Override
		public EmbeddedStorageConfigurationBuilder setDataFilePrefix(
			final String dataFilePrefix
		)
		{
			return this.set(DATA_FILE_PREFIX, dataFilePrefix);
		}

		@Override
		public EmbeddedStorageConfigurationBuilder setDataFileSuffix(
			final String dataFileSuffix
		)
		{
			return this.set(DATA_FILE_SUFFIX, dataFileSuffix);
		}

		@Override
		public EmbeddedStorageConfigurationBuilder setTransactionFilePrefix(
			final String transactionFilePrefix
		)
		{
			return this.set(TRANSACTION_FILE_PREFIX, transactionFilePrefix);
		}

		@Override
		public EmbeddedStorageConfigurationBuilder setTransactionFileSuffix(
			final String transactionFileSuffix
		)
		{
			return this.set(TRANSACTION_FILE_SUFFIX, transactionFileSuffix);
		}

		@Override
		public EmbeddedStorageConfigurationBuilder setTypeDictionaryFileName(
			final String typeDictionaryFileName
		)
		{
			return this.set(TYPE_DICTIONARY_FILE_NAME, typeDictionaryFileName);
		}

		@Override
		public EmbeddedStorageConfigurationBuilder setRescuedFileSuffix(
			final String rescuedFileSuffix
		)
		{
			this.set(RESCUED_FILE_SUFFIX, rescuedFileSuffix);
			return this;
		}

		@Override
		public EmbeddedStorageConfigurationBuilder setLockFileName(
			final String lockFileName
		)
		{
			return this.set(LOCK_FILE_NAME, lockFileName);
		}

		@Override
		public EmbeddedStorageConfigurationBuilder setHousekeepingInterval(
			final Duration houseKeepingInterval
		)
		{
			return this.set(HOUSEKEEPING_INTERVAL, houseKeepingInterval.toString());
		}

		@Override
		public EmbeddedStorageConfigurationBuilder setHousekeepingTimeBudget(
			final Duration housekeepingTimeBudget
		)
		{
			return this.set(HOUSEKEEPING_TIME_BUDGET, housekeepingTimeBudget.toString());
		}

		@Override
		public EmbeddedStorageConfigurationBuilder setHousekeepingAdaptive(
			final boolean adaptive
		)
		{
			return this.set(HOUSEKEEPING_ADAPTIVE, Boolean.toString(adaptive));
		}
		
		@Override
		public EmbeddedStorageConfigurationBuilder setHousekeepingIncreaseThreshold(
			final Duration housekeepingIncreaseThreshold
		)
		{
			return this.set(HOUSEKEEPING_INCREASE_THRESHOLD, housekeepingIncreaseThreshold.toString());
		}
		
		@Override
		public EmbeddedStorageConfigurationBuilder setHousekeepingIncreaseAmount(
			final Duration housekeepingIncreaseAmount
		)
		{
			return this.set(HOUSEKEEPING_INCREASE_AMOUNT, housekeepingIncreaseAmount.toString());
		}
		
		@Override
		public EmbeddedStorageConfigurationBuilder setHousekeepingMaximumTimeBudget(
			final Duration housekeepingMaximumTimeBudget
		)
		{
			return this.set(HOUSEKEEPING_MAXIMUM_TIME_BUDGET, housekeepingMaximumTimeBudget.toString());
		}

		@Override
		public EmbeddedStorageConfigurationBuilder setEntityCacheThreshold(
			final long entityCacheThreshold
		)
		{
			return this.set(ENTITY_CACHE_THRESHOLD, Long.toString(entityCacheThreshold));
		}

		@Override
		public EmbeddedStorageConfigurationBuilder setEntityCacheTimeout(
			final Duration entityCacheTimeout
		)
		{
			return this.set(ENTITY_CACHE_TIMEOUT, entityCacheTimeout.toString());
		}

		@Override
		public EmbeddedStorageConfigurationBuilder setDataFileMinimumSize(
			final ByteSize dataFileMinimumSize
		)
		{
			return this.set(DATA_FILE_MINIMUM_SIZE, dataFileMinimumSize.toString());
		}

		@Override
		public EmbeddedStorageConfigurationBuilder setDataFileMaximumSize(
			final ByteSize dataFileMaximumSize
		)
		{
			return this.set(DATA_FILE_MAXIMUM_SIZE, dataFileMaximumSize.toString());
		}

		@Override
		public EmbeddedStorageConfigurationBuilder setDataFileMinimumUseRatio(
			final double dataFileMinimumUseRatio
		)
		{
			return this.set(DATA_FILE_MINIMUM_USE_RATIO, Double.toString(dataFileMinimumUseRatio));
		}

		@Override
		public EmbeddedStorageConfigurationBuilder setDataFileCleanupHeadFile(
			final boolean dataFileCleanupHeadFile
		)
		{
			return this.set(DATA_FILE_CLEANUP_HEAD_FILE, Boolean.toString(dataFileCleanupHeadFile));
		}

		@Override
		public EmbeddedStorageConfigurationBuilder setTransactionFileMaximumSize(
			final ByteSize transactionFileMaximumSize
		)
		{
			return this.set(TRANSACTION_FILE_MAXIMUM_SIZE, transactionFileMaximumSize.toString());
		}

		@Override
		public EmbeddedStorageConfigurationBuilder setReferenceValidation(
			final String referenceValidation
		)
		{
			return this.set(REFERENCE_VALIDATION, referenceValidation);
		}

		@Override
		public EmbeddedStorageConfigurationBuilder setGcZombieOidHandling(
			final String gcZombieOidHandling
		)
		{
			return this.set(GC_ZOMBIE_OID_HANDLING, gcZombieOidHandling);
		}

		@Override
		public EmbeddedStorageConfigurationBuilder setChunkChecksumAlgorithm(
			final String chunkChecksumAlgorithm
		)
		{
			return this.set(CHUNK_CHECKSUM_ALGORITHM, chunkChecksumAlgorithm);
		}

		@Override
		public EmbeddedStorageConfigurationBuilder setChunkChecksumProfile(
			final String chunkChecksumProfile
		)
		{
			return this.set(CHUNK_CHECKSUM_PROFILE, chunkChecksumProfile);
		}

		@Override
		public EmbeddedStorageConfigurationBuilder setChunkChecksumSeed(
			final String chunkChecksumSeed
		)
		{
			return this.set(CHUNK_CHECKSUM_SEED, chunkChecksumSeed);
		}

		@Override
		public EmbeddedStorageConfigurationBuilder setChunkChecksumEmit(
			final boolean chunkChecksumEmit
		)
		{
			return this.set(CHUNK_CHECKSUM_EMIT, Boolean.toString(chunkChecksumEmit));
		}

		@Override
		public EmbeddedStorageConfigurationBuilder setChunkChecksumVerify(
			final boolean chunkChecksumVerify
		)
		{
			return this.set(CHUNK_CHECKSUM_VERIFY, Boolean.toString(chunkChecksumVerify));
		}

		@Override
		public EmbeddedStorageConfigurationBuilder setChunkChecksumOnChecksumMismatch(
			final String reaction
		)
		{
			return this.set(CHUNK_CHECKSUM_ON_CHECKSUM_MISMATCH, reaction);
		}

		@Override
		public EmbeddedStorageConfigurationBuilder setChunkChecksumOnBoundaryMismatch(
			final String reaction
		)
		{
			return this.set(CHUNK_CHECKSUM_ON_BOUNDARY_MISMATCH, reaction);
		}

		@Override
		public EmbeddedStorageConfigurationBuilder setChunkChecksumOnUnknownKind(
			final String reaction
		)
		{
			return this.set(CHUNK_CHECKSUM_ON_UNKNOWN_KIND, reaction);
		}

		@Override
		public EmbeddedStorageConfigurationBuilder setChunkChecksumOnMissingHeader(
			final String reaction
		)
		{
			return this.set(CHUNK_CHECKSUM_ON_MISSING_HEADER, reaction);
		}

		@Override
		public EmbeddedStorageConfigurationBuilder setChunkChecksumOnUncoveredData(
			final String reaction
		)
		{
			return this.set(CHUNK_CHECKSUM_ON_UNCOVERED_DATA, reaction);
		}

		@Override
		public EmbeddedStorageConfigurationBuilder setChunkChecksumRequireCoverage(
			final boolean chunkChecksumRequireCoverage
		)
		{
			return this.set(CHUNK_CHECKSUM_REQUIRE_COVERAGE, Boolean.toString(chunkChecksumRequireCoverage));
		}

		@Override
		public EmbeddedStorageConfigurationBuilder setChunkChecksumContinuousCoverage(
			final boolean chunkChecksumContinuousCoverage
		)
		{
			return this.set(CHUNK_CHECKSUM_CONTINUOUS_COVERAGE, Boolean.toString(chunkChecksumContinuousCoverage));
		}

	}

}
