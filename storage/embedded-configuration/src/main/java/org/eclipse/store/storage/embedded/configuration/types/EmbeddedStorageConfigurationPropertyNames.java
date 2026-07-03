
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

import org.eclipse.serializer.configuration.types.ByteSize;

/**
 * Property name constants for all settings supported by external EclipseStore configuration files
 * (e.g. {@code eclipsestore.properties} or the equivalent INI/XML format).
 * <p>
 * Each constant matches a setter in {@link EmbeddedStorageConfigurationBuilder}: when a configuration is
 * loaded, the value of the property with the constant's name is forwarded to that setter. The constants are
 * also used by {@link EmbeddedStorageFoundationCreatorConfigurationBased} to look the values up while
 * assembling a {@link org.eclipse.store.storage.embedded.types.EmbeddedStorageFoundation}.
 */
public interface EmbeddedStorageConfigurationPropertyNames
{
	/**
	 * @see EmbeddedStorageConfigurationBuilder#setStorageDirectory(String)
	 */
	public final static String STORAGE_DIRECTORY             = "storage-directory";

	/**
	 * Configuration sub-tree selecting and configuring the file system to be used as the storage backend.
	 * <p>
	 * Used together with the optional {@code storage-filesystem.target} key to pick a specific
	 * {@code AFileSystem} provider (e.g. NIO, S3, SQL); if no provider is configured, a NIO file system is
	 * used by default.
	 */
	public final static String STORAGE_FILESYSTEM            = "storage-filesystem";

	/**
	 * @see EmbeddedStorageConfigurationBuilder#setDeletionDirectory(String)
	 */
	public final static String DELETION_DIRECTORY            = "deletion-directory";

	/**
	 * @see EmbeddedStorageConfigurationBuilder#setTruncationDirectory(String)
	 */
	public final static String TRUNCATION_DIRECTORY          = "truncation-directory";

	/**
	 * @see EmbeddedStorageConfigurationBuilder#setBackupDirectory(String)
	 */
	public final static String BACKUP_DIRECTORY              = "backup-directory";

	/**
	 * Configuration sub-tree selecting and configuring the file system to be used for the backup location.
	 * <p>
	 * Has the same shape as {@link #STORAGE_FILESYSTEM}. If the backup directory is configured but no backup
	 * file system is specified, the storage file system is reused.
	 */
	public final static String BACKUP_FILESYSTEM             = "backup-filesystem";

	/**
	 * @see EmbeddedStorageConfigurationBuilder#setChannelCount(int)
	 */
	public final static String CHANNEL_COUNT                 = "channel-count";

	/**
	 * @see EmbeddedStorageConfigurationBuilder#setChannelDirectoryPrefix(String)
	 */
	public final static String CHANNEL_DIRECTORY_PREFIX      = "channel-directory-prefix";

	/**
	 * @see EmbeddedStorageConfigurationBuilder#setDataFilePrefix(String)
	 */
	public final static String DATA_FILE_PREFIX              = "data-file-prefix";

	/**
	 * @see EmbeddedStorageConfigurationBuilder#setDataFileSuffix(String)
	 */
	public final static String DATA_FILE_SUFFIX              = "data-file-suffix";

	/**
	 * @see EmbeddedStorageConfigurationBuilder#setTransactionFilePrefix(String)
	 */
	public final static String TRANSACTION_FILE_PREFIX       = "transaction-file-prefix";

	/**
	 * @see EmbeddedStorageConfigurationBuilder#setTransactionFileSuffix(String)
	 */
	public final static String TRANSACTION_FILE_SUFFIX       = "transaction-file-suffix";

	/**
	 * @see EmbeddedStorageConfigurationBuilder#setTypeDictionaryFileName(String)
	 */
	public final static String TYPE_DICTIONARY_FILE_NAME      = "type-dictionary-file-name";

	/**
	 * @see EmbeddedStorageConfigurationBuilder#setRescuedFileSuffix(String)
	 */
	public final static String RESCUED_FILE_SUFFIX           = "rescued-file-suffix";

	/**
	 * @see EmbeddedStorageConfigurationBuilder#setLockFileName(String)
	 */
	public final static String LOCK_FILE_NAME                = "lock-file-name";

	/**
	 * @see EmbeddedStorageConfigurationBuilder#setHousekeepingInterval(java.time.Duration)
	 */
	public final static String HOUSEKEEPING_INTERVAL         = "housekeeping-interval";

	/**
	 * @see EmbeddedStorageConfigurationBuilder#setHousekeepingTimeBudget(java.time.Duration)
	 */
	public final static String HOUSEKEEPING_TIME_BUDGET      = "housekeeping-time-budget";

	/**
	 * @see EmbeddedStorageConfigurationBuilder#setHousekeepingAdaptive(boolean)
	 */
	public final static String HOUSEKEEPING_ADAPTIVE         = "housekeeping-adaptive";

	/**
	 * @see EmbeddedStorageConfigurationBuilder#setHousekeepingIncreaseThreshold(java.time.Duration)
	 */
	public final static String HOUSEKEEPING_INCREASE_THRESHOLD  = "housekeeping-increase-threshold";

	/**
	 * @see EmbeddedStorageConfigurationBuilder#setHousekeepingIncreaseAmount(java.time.Duration)
	 */
	public final static String HOUSEKEEPING_INCREASE_AMOUNT     = "housekeeping-increase-amount";

	/**
	 * @see EmbeddedStorageConfigurationBuilder#setHousekeepingMaximumTimeBudget(java.time.Duration)
	 */
	public final static String HOUSEKEEPING_MAXIMUM_TIME_BUDGET = "housekeeping-maximum-time-budget";

	/**
	 * @see EmbeddedStorageConfigurationBuilder#setEntityCacheThreshold(long)
	 */
	public final static String ENTITY_CACHE_THRESHOLD        = "entity-cache-threshold";

	/**
	 * @see EmbeddedStorageConfigurationBuilder#setEntityCacheTimeout(java.time.Duration)
	 */
	public final static String ENTITY_CACHE_TIMEOUT          = "entity-cache-timeout";

	/**
	 * @see EmbeddedStorageConfigurationBuilder#setDataFileMinimumSize(ByteSize)
	 */
	public final static String DATA_FILE_MINIMUM_SIZE        = "data-file-minimum-size";

	/**
	 * @see EmbeddedStorageConfigurationBuilder#setDataFileMaximumSize(ByteSize)
	 */
	public final static String DATA_FILE_MAXIMUM_SIZE        = "data-file-maximum-size";

	/**
	 * @see EmbeddedStorageConfigurationBuilder#setDataFileMinimumUseRatio(double)
	 */
	public final static String DATA_FILE_MINIMUM_USE_RATIO   = "data-file-minimum-use-ratio";
	
	/**
	 * @see EmbeddedStorageConfigurationBuilder#setTransactionFileMaximumSize(ByteSize)
	 */
	public final static String TRANSACTION_FILE_MAXIMUM_SIZE = "transaction-file-maximum-size";

	/**
	 * @see EmbeddedStorageConfigurationBuilder#setDataFileCleanupHeadFile(boolean)
	 */
	public final static String DATA_FILE_CLEANUP_HEAD_FILE   = "data-file-cleanup-head-file";

	/**
	 * Store-time validation of trusted reference object ids (references written into a store's data
	 * whose entities are not part of the store itself): {@code off}, {@code log}, {@code fail} or
	 * {@code heal}. Default (unset) is {@code log}: detected dangling references are logged as an
	 * error but the store proceeds. {@code fail} rejects such a store atomically; {@code heal}
	 * additionally repairs it automatically (re-storing the still-live referenced instances under
	 * their existing object ids and retrying); {@code off} disables collection and validation
	 * entirely (zero overhead).
	 *
	 * @see EmbeddedStorageConfigurationBuilder#setReferenceValidation(String)
	 */
	public final static String REFERENCE_VALIDATION             = "reference-validation";

	/**
	 * Reaction of the storage garbage collector to an encountered zombie object id (a persisted
	 * binary record referencing a non-existing entity, i.e. a dangling reference already present in
	 * the storage): {@code log} or {@code fail}. Default (unset) is {@code log}: the zombie is
	 * WARN-logged and reported to the event logger, the garbage collection continues. {@code fail}
	 * throws a {@code StorageExceptionConsistencyZombieOid}, halting the affected channel while the
	 * evidence may still be recoverable (diagnosis-focused deployments).
	 *
	 * @see EmbeddedStorageConfigurationBuilder#setGcZombieOidHandling(String)
	 */
	public final static String GC_ZOMBIE_OID_HANDLING           = "gc-zombie-oid-handling";

	/**
	 * Primary chunk-checksum algorithm: {@code none}, {@code crc32c} or
	 * {@code sha256-chained}. When this key is unset but another {@code chunk-checksum-*} key is present,
	 * the default is {@code sha256-chained}; setting no {@code chunk-checksum-*} key at all keeps the
	 * framework default (no checksum &mdash; the feature off).
	 *
	 * @see EmbeddedStorageConfigurationBuilder#setChunkChecksumAlgorithm(String)
	 */
	public final static String CHUNK_CHECKSUM_ALGORITHM            = "chunk-checksum-algorithm";

	/**
	 * Chunk-checksum base policy profile: {@code default}, {@code off}, {@code observe}, {@code strict} or
	 * {@code strict-tolerate-legacy}. Default (unset) is {@code default}.
	 *
	 * @see EmbeddedStorageConfigurationBuilder#setChunkChecksumProfile(String)
	 */
	public final static String CHUNK_CHECKSUM_PROFILE             = "chunk-checksum-profile";

	/**
	 * Initial chain seed for the {@code sha256-chained} algorithm as a hex string (64 chars = 32 bytes);
	 * unused by any other algorithm.
	 *
	 * @see EmbeddedStorageConfigurationBuilder#setChunkChecksumSeed(String)
	 */
	public final static String CHUNK_CHECKSUM_SEED               = "chunk-checksum-seed";

	/**
	 * Expert override: whether to emit checksum records on write. Defaults to the profile's value.
	 *
	 * @see EmbeddedStorageConfigurationBuilder#setChunkChecksumEmit(boolean)
	 */
	public final static String CHUNK_CHECKSUM_EMIT               = "chunk-checksum-emit";

	/**
	 * Expert override: whether to recompute and check checksum records on load. Defaults to the profile's value.
	 *
	 * @see EmbeddedStorageConfigurationBuilder#setChunkChecksumVerify(boolean)
	 */
	public final static String CHUNK_CHECKSUM_VERIFY             = "chunk-checksum-verify";

	/**
	 * Expert override: reaction ({@code ignore} / {@code log} / {@code fail}) to a checksum mismatch.
	 * Defaults to the profile's value.
	 *
	 * @see EmbeddedStorageConfigurationBuilder#setChunkChecksumOnChecksumMismatch(String)
	 */
	public final static String CHUNK_CHECKSUM_ON_CHECKSUM_MISMATCH = "chunk-checksum-on-checksum-mismatch";

	/**
	 * Expert override: reaction ({@code ignore} / {@code log} / {@code fail}) to a chunk-boundary mismatch.
	 * Defaults to the profile's value.
	 *
	 * @see EmbeddedStorageConfigurationBuilder#setChunkChecksumOnBoundaryMismatch(String)
	 */
	public final static String CHUNK_CHECKSUM_ON_BOUNDARY_MISMATCH = "chunk-checksum-on-boundary-mismatch";

	/**
	 * Expert override: reaction ({@code ignore} / {@code log} / {@code fail}) to an unknown record kind.
	 * Defaults to the profile's value.
	 *
	 * @see EmbeddedStorageConfigurationBuilder#setChunkChecksumOnUnknownKind(String)
	 */
	public final static String CHUNK_CHECKSUM_ON_UNKNOWN_KIND     = "chunk-checksum-on-unknown-kind";

	/**
	 * Expert override: reaction ({@code ignore} / {@code log} / {@code fail}) to a missing file header.
	 * Defaults to the profile's value.
	 *
	 * @see EmbeddedStorageConfigurationBuilder#setChunkChecksumOnMissingHeader(String)
	 */
	public final static String CHUNK_CHECKSUM_ON_MISSING_HEADER   = "chunk-checksum-on-missing-header";

	/**
	 * Expert override: reaction ({@code ignore} / {@code log} / {@code fail}) to uncovered data.
	 * Defaults to the profile's value.
	 *
	 * @see EmbeddedStorageConfigurationBuilder#setChunkChecksumOnUncoveredData(String)
	 */
	public final static String CHUNK_CHECKSUM_ON_UNCOVERED_DATA   = "chunk-checksum-on-uncovered-data";

	/**
	 * Expert override: whether missing/uncovered files are raised as anomalies. Defaults to the profile's value.
	 *
	 * @see EmbeddedStorageConfigurationBuilder#setChunkChecksumRequireCoverage(boolean)
	 */
	public final static String CHUNK_CHECKSUM_REQUIRE_COVERAGE    = "chunk-checksum-require-coverage";

	/**
	 * Expert override: whether enabling emit forces an immediately-covered head file. Defaults to the
	 * profile's value.
	 *
	 * @see EmbeddedStorageConfigurationBuilder#setChunkChecksumContinuousCoverage(boolean)
	 */
	public final static String CHUNK_CHECKSUM_CONTINUOUS_COVERAGE = "chunk-checksum-continuous-coverage";

}
