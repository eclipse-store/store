
package org.eclipse.store.integrations.cdi;

/*-
 * #%L
 * EclipseStore Integrations CDI 4
 * %%
 * Copyright (C) 2023 - 2024 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import org.eclipse.microprofile.config.Config;
import org.eclipse.store.storage.embedded.configuration.types.EmbeddedStorageConfigurationPropertyNames;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.StreamSupport;


/**
 * The relation with the properties from Eclipe Store docs:
 * <a href="https://docs.eclipsestore.io/manual/storage/configuration/properties.html">Configuration properties</a>
 */
public enum ConfigurationCoreProperties
{
	/**
	 * The base directory of the storage in the file system. Default is "storage" in the working directory.
	 */
	STORAGE_DIRECTORY(
		Constants.PREFIX + "storage.directory",
		EmbeddedStorageConfigurationPropertyNames.STORAGE_DIRECTORY
	),
	
	/**
	 * The live file system configuration. See storage targets configuration.
	 */
	STORAGE_FILESYSTEM(
			Constants.PREFIX + "storage.filesystem",
		EmbeddedStorageConfigurationPropertyNames.STORAGE_FILESYSTEM
	),
	
	/**
	 * If configured, the storage will not delete files. Instead of deleting a file it will be moved to this directory.
	 */
	DELETION_DIRECTORY(
			Constants.PREFIX + "deletion.directory",
		EmbeddedStorageConfigurationPropertyNames.DELETION_DIRECTORY
	),
	
	/**
	 * If configured, files that will get truncated are copied into this directory.
	 */
	TRUNCATION_DIRECTORY(
			Constants.PREFIX + "truncation.directory",
		EmbeddedStorageConfigurationPropertyNames.TRUNCATION_DIRECTORY
	),
	
	/**
	 * The backup directory.
	 */
	BACKUP_DIRECTORY(
			Constants.PREFIX + "backup.directory",
		EmbeddedStorageConfigurationPropertyNames.BACKUP_DIRECTORY
	),
	
	/**
	 * The backup file system configuration. See storage targets configuration.
	 */
	BACKUP_FILESYSTEM(
			Constants.PREFIX + "backup.filesystem",
		EmbeddedStorageConfigurationPropertyNames.BACKUP_FILESYSTEM
	),
	
	/**
	 * The number of threads and number of directories used by the storage engine. Every thread has exclusive access
	 * to its directory. Default is 1.
	 */
	CHANNEL_COUNT(
			Constants.PREFIX + "channel.count",
		EmbeddedStorageConfigurationPropertyNames.CHANNEL_COUNT
	),
	
	/**
	 * Name prefix of the subdirectories used by the channel threads. Default is "channel_".
	 */
	CHANNEL_DIRECTORY_PREFIX(
			Constants.PREFIX + "channel.directory.prefix",
		EmbeddedStorageConfigurationPropertyNames.CHANNEL_DIRECTORY_PREFIX
	),
	
	/**
	 * Name prefix of the storage files. Default is "channel_".
	 */
	DATA_FILE_PREFIX(
			Constants.PREFIX + "data.file.prefix",
		EmbeddedStorageConfigurationPropertyNames.DATA_FILE_PREFIX
	),
	
	/**
	 * Name suffix of the storage files. Default is ".dat".
	 */
	DATA_FILE_SUFFIX(
			Constants.PREFIX + "data.file.suffix",
		EmbeddedStorageConfigurationPropertyNames.DATA_FILE_SUFFIX
	),
	
	/**
	 * Name prefix of the storage transaction file. Default is "transactions_".
	 */
	TRANSACTION_FILE_PREFIX(
			Constants.PREFIX + "transaction.file.prefix",
		EmbeddedStorageConfigurationPropertyNames.TRANSACTION_FILE_PREFIX
	),
	
	/**
	 * Name suffix of the storage transaction file. Default is ".sft".
	 */
	TRANSACTION_FILE_SUFFIX(
			Constants.PREFIX + "transaction.file.suffix",
		EmbeddedStorageConfigurationPropertyNames.TRANSACTION_FILE_SUFFIX
	),
	
	/**
	 * The name of the dictionary file. Default is "PersistenceTypeDictionary.ptd".
	 */
	TYPE_DICTIONARY_FILE_NAME(
			Constants.PREFIX + "type.dictionary.file.name",
		EmbeddedStorageConfigurationPropertyNames.TYPE_DICTIONARY_FILE_NAME
	),
	
	/**
	 * Name suffix of the storage rescue files. Default is ".bak".
	 */
	RESCUED_FILE_SUFFIX(
			Constants.PREFIX + "rescued.file.suffix",
		EmbeddedStorageConfigurationPropertyNames.RESCUED_FILE_SUFFIX
	),
	
	/**
	 * Name of the lock file. Default is "used.lock".
	 */
	LOCK_FILE_NAME(
			Constants.PREFIX + "lock.file.name",
		EmbeddedStorageConfigurationPropertyNames.LOCK_FILE_NAME
	),
	
	/**
	 * Interval for the housekeeping. This is work like garbage collection or cache checking. In combination with
	 * houseKeepingNanoTimeBudget the maximum processor time for housekeeping work can be set. Default is 1 second.
	 */
	HOUSEKEEPING_INTERVAL(
			Constants.PREFIX + "housekeeping.interval",
		EmbeddedStorageConfigurationPropertyNames.HOUSEKEEPING_INTERVAL
	),
	
	/**
	 * Number of nanoseconds used for each housekeeping cycle. Default is 10 milliseconds = 0.01 seconds.
	 */
	HOUSEKEEPING_TIME_BUDGET(
			Constants.PREFIX + "housekeeping.time.budget",
		EmbeddedStorageConfigurationPropertyNames.HOUSEKEEPING_TIME_BUDGET
	),
	
	/**
	 * Abstract threshold value for the lifetime of entities in the cache. Default is 1000000000.
	 */
	ENTITY_CACHE_THRESHOLD(
			Constants.PREFIX + "entity.cache.threshold",
		EmbeddedStorageConfigurationPropertyNames.ENTITY_CACHE_THRESHOLD
	),
	
	/**
	 * Timeout in milliseconds for the entity cache evaluator. If an entity wasn’t
	 * accessed in this timespan it will be removed from the cache. Default is 1 day.
	 */
	ENTITY_CACHE_TIMEOUT(
			Constants.PREFIX + "entity.cache.timeout",
		EmbeddedStorageConfigurationPropertyNames.ENTITY_CACHE_TIMEOUT
	),
	
	/**
	 * Minimum file size for a data file to avoid cleaning it up. Default is 1024^2 = 1 MiB.
	 */
	DATA_FILE_MINIMUM_SIZE(
			Constants.PREFIX + "data.file.minimum.size",
		EmbeddedStorageConfigurationPropertyNames.DATA_FILE_MINIMUM_SIZE
	),
	
	/**
	 * Maximum file size for a data file to avoid cleaning it up. Default is 1024^2*8 = 8 MiB.
	 */
	DATA_FILE_MAXIMUM_SIZE(
			Constants.PREFIX + "data.file.maximum.size",
		EmbeddedStorageConfigurationPropertyNames.DATA_FILE_MAXIMUM_SIZE
	),
	
	/**
	 * The ratio (value in ]0.0;1.0]) of non-gap data contained in a storage file to prevent the file from being
	 * dissolved. Default is 0.75 (75%).
	 */
	DATA_FILE_MINIMUM_USE_RATIO(
			Constants.PREFIX + "data.file.minimum.use.ratio",
		EmbeddedStorageConfigurationPropertyNames.DATA_FILE_MINIMUM_USE_RATIO
	),
	
	/**
	 * A flag defining whether the current head file (the only file actively written to)
	 * shall be subjected to file cleanups as well.
	 */
	DATA_FILE_CLEANUP_HEAD_FILE(
			Constants.PREFIX + "data.file.cleanup.head.file",
			EmbeddedStorageConfigurationPropertyNames.DATA_FILE_CLEANUP_HEAD_FILE
	),

	/**
	 * Store-time validation of trusted reference object ids: off, log or fail. Default log.
	 */
	REFERENCE_VALIDATION(
			Constants.PREFIX + "reference.validation",
			EmbeddedStorageConfigurationPropertyNames.REFERENCE_VALIDATION
	),

	/**
	 * Primary chunk-checksum algorithm: none, crc32c or sha256-chained. Default sha256-chained.
	 */
	CHUNK_CHECKSUM_ALGORITHM(
			Constants.PREFIX + "chunk.checksum.algorithm",
			EmbeddedStorageConfigurationPropertyNames.CHUNK_CHECKSUM_ALGORITHM
	),

	/**
	 * Chunk-checksum base policy profile: default, off, observe, strict or strict-tolerate-legacy. Default default.
	 */
	CHUNK_CHECKSUM_PROFILE(
			Constants.PREFIX + "chunk.checksum.profile",
			EmbeddedStorageConfigurationPropertyNames.CHUNK_CHECKSUM_PROFILE
	),

	/**
	 * Initial chain seed (hex, 64 chars = 32 bytes) for the sha256-chained algorithm; unused otherwise.
	 */
	CHUNK_CHECKSUM_SEED(
			Constants.PREFIX + "chunk.checksum.seed",
			EmbeddedStorageConfigurationPropertyNames.CHUNK_CHECKSUM_SEED
	),

	/**
	 * Expert override: whether to emit checksum records on write. Defaults to the profile's value.
	 */
	CHUNK_CHECKSUM_EMIT(
			Constants.PREFIX + "chunk.checksum.emit",
			EmbeddedStorageConfigurationPropertyNames.CHUNK_CHECKSUM_EMIT
	),

	/**
	 * Expert override: whether to recompute and check checksum records on load. Defaults to the profile's value.
	 */
	CHUNK_CHECKSUM_VERIFY(
			Constants.PREFIX + "chunk.checksum.verify",
			EmbeddedStorageConfigurationPropertyNames.CHUNK_CHECKSUM_VERIFY
	),

	/**
	 * Expert override: reaction (ignore / log / fail) to a checksum mismatch. Defaults to the profile's value.
	 */
	CHUNK_CHECKSUM_ON_CHECKSUM_MISMATCH(
			Constants.PREFIX + "chunk.checksum.on.checksum.mismatch",
			EmbeddedStorageConfigurationPropertyNames.CHUNK_CHECKSUM_ON_CHECKSUM_MISMATCH
	),

	/**
	 * Expert override: reaction (ignore / log / fail) to a chunk-boundary mismatch. Defaults to the profile's value.
	 */
	CHUNK_CHECKSUM_ON_BOUNDARY_MISMATCH(
			Constants.PREFIX + "chunk.checksum.on.boundary.mismatch",
			EmbeddedStorageConfigurationPropertyNames.CHUNK_CHECKSUM_ON_BOUNDARY_MISMATCH
	),

	/**
	 * Expert override: reaction (ignore / log / fail) to an unknown record kind. Defaults to the profile's value.
	 */
	CHUNK_CHECKSUM_ON_UNKNOWN_KIND(
			Constants.PREFIX + "chunk.checksum.on.unknown.kind",
			EmbeddedStorageConfigurationPropertyNames.CHUNK_CHECKSUM_ON_UNKNOWN_KIND
	),

	/**
	 * Expert override: reaction (ignore / log / fail) to a missing file header. Defaults to the profile's value.
	 */
	CHUNK_CHECKSUM_ON_MISSING_HEADER(
			Constants.PREFIX + "chunk.checksum.on.missing.header",
			EmbeddedStorageConfigurationPropertyNames.CHUNK_CHECKSUM_ON_MISSING_HEADER
	),

	/**
	 * Expert override: reaction (ignore / log / fail) to uncovered data. Defaults to the profile's value.
	 */
	CHUNK_CHECKSUM_ON_UNCOVERED_DATA(
			Constants.PREFIX + "chunk.checksum.on.uncovered.data",
			EmbeddedStorageConfigurationPropertyNames.CHUNK_CHECKSUM_ON_UNCOVERED_DATA
	),

	/**
	 * Expert override: whether missing/uncovered files are raised as anomalies. Defaults to the profile's value.
	 */
	CHUNK_CHECKSUM_REQUIRE_COVERAGE(
			Constants.PREFIX + "chunk.checksum.require.coverage",
			EmbeddedStorageConfigurationPropertyNames.CHUNK_CHECKSUM_REQUIRE_COVERAGE
	),

	/**
	 * Expert override: whether enabling emit forces an immediately-covered head file. Defaults to the profile's value.
	 */
	CHUNK_CHECKSUM_CONTINUOUS_COVERAGE(
			Constants.PREFIX + "chunk.checksum.continuous.coverage",
			EmbeddedStorageConfigurationPropertyNames.CHUNK_CHECKSUM_CONTINUOUS_COVERAGE
	);

	private final String microProfile;
	private final String eclipseStore;

	ConfigurationCoreProperties(final String microProfile, final String eclipseStore)
	{
		this.microProfile = microProfile;
		this.eclipseStore = eclipseStore;
	}

	public String getMicroProfile()
	{
		return this.microProfile;
	}

	/**
	 * Returns the corresponding EclipseStore version of the config key.  It replaces the MicroProfile part
	 * with the EclipseStore part. So the keys can be 'longer' as the value defined in the enum. A typical
	 * example is the 'storage filesystem' element.
	 * @param key The Key as defined in MicroProfile config
	 * @return The corresponding EclipseStore version of this key.
	 */
	public String getEclipseStore(final String key)
	{
		return key.replaceAll(this.microProfile, this.eclipseStore);
	}

	/**
	 * Returns the {@code ConfigurationCoreProperties} enum entry that corresponds with the
	 * MicroProfile config key value. Optional.empty() when no matching entry is found.
	 *
	 * @param value The MicroProfile config key value to look for.
	 * @return The enum entry if there is a matching entry or {@code Optional.empty()}
	 */
	public static Optional<ConfigurationCoreProperties> get(final String value)
	{
		Objects.requireNonNull(value);
		return Arrays.stream(ConfigurationCoreProperties.values())
				.filter(ccp -> value.startsWith(ccp.getMicroProfile()))
				.findAny();
	}


	public static Map<String, String> getProperties(final Config config)
	{

		final Map<String, String> properties = new HashMap<>();

		StreamSupport.stream(config.getPropertyNames()
									 .spliterator(), false)
				.filter(n -> n.startsWith(Constants.PREFIX))
				.forEach(p -> addProperty(config, properties, p));

		return properties;
	}

	private static String asEclipseStoreConfigName(final String name)
	{
		final Optional<ConfigurationCoreProperties> coreProperty = ConfigurationCoreProperties.get(name);
		return coreProperty.isEmpty()
				? name.substring(Constants.PREFIX.length())
				: coreProperty.get()
					.getEclipseStore(name);
	}

	private static void addProperty(
			final Config config,
			final Map<String, String> properties,
			final String configName
	)
	{
		config.getOptionalValue(configName, String.class)
				.ifPresent(v -> properties.put(ConfigurationCoreProperties.asEclipseStoreConfigName(configName), v))
		;
	}

	public static class Constants
	{
		public static final String PREFIX = "org.eclipse.store.";
	}
}
