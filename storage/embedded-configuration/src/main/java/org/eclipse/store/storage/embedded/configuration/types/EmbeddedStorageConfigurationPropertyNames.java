
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
 * All supported properties for external configuration files.
 *
 */
public interface EmbeddedStorageConfigurationPropertyNames
{
	/**
	 * @see EmbeddedStorageConfigurationBuilder#setStorageDirectory(String)
	 */
	public final static String STORAGE_DIRECTORY             = "storage-directory";

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

}
