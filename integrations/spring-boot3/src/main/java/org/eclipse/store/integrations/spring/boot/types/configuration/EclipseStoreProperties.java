package org.eclipse.store.integrations.spring.boot.types.configuration;

/*-
 * #%L
 * integrations-spring-boot3
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

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * The {@code EclipseStoreProperties} class holds the configuration properties for the Eclipse Store.
 * These properties are loaded from the application's configuration files and can be used to configure the Eclipse Store.
 *
 * <p>This class is annotated with {@code @Configuration}, {@code @Primary}, and {@code @ConfigurationProperties},
 * which means it is a Spring configuration class, it is the primary bean of its type, and its properties are bound to the "org.eclipse.store" prefix in the configuration files.</p>
 *
 * <p>Each property in this class corresponds to a configuration option for the Eclipse Store.
 * The properties are loaded from the configuration files when the application starts.</p>
 */
@Configuration
@Primary
@ConfigurationProperties(prefix = "org.eclipse.store")
public class EclipseStoreProperties
{

    /**
     * Specify the complete path for the class, which will serve as the root. This class must have a public parameterless constructor.
     * Example: "org.eclipse.store.Root"
     */
    private Class<?> root;

    /**
     * The base directory of the storage in the file system. Default is "storage" in the working directory.
     */
    private String storageDirectory;

    /**
     * The live file system configuration
     */
    @NestedConfigurationProperty
    private StorageFilesystem storageFilesystem;

    /**
     * If configured, the storage will not delete files. Instead of deleting a file it will be moved to this directory.
     */
    private String deletionDirectory;

    /**
     * If configured, files that will get truncated are copied into this directory.
     */
    private String truncationDirectory;

    /**
     * The backup directory.
     */
    private String backupDirectory;

    /**
     * The backup file system configuration. See storage targets configuration.
     */
    @NestedConfigurationProperty
    private StorageFilesystem backupFilesystem;

    /**
     * The number of threads and number of directories used by the storage engine. Every thread has exclusive access to its directory. Default is 1.
     */
    private String channelCount;

    /**
     * Name prefix of the subdirectories used by the channel threads. Default is "channel_".
     */
    private String channelDirectoryPrefix;

    /**
     * Name prefix of the storage files. Default is "channel_".
     */
    private String dataFilePrefix;

    /**
     * Name suffix of the storage files. Default is ".dat".
     */
    private String dataFileSuffix;

    /**
     * Name prefix of the storage transaction file. Default is "transactions_".
     */
    private String transactionFilePrefix;

    /**
     * Name suffix of the storage transaction file. Default is ".sft".
     */
    private String transactionFileSuffix;

    /**
     * The name of the dictionary file. Default is "PersistenceTypeDictionary.ptd".
     */
    private String typeDictionaryFileName;

    /**
     * Name suffix of the storage rescue files. Default is ".bak".
     */
    private String rescuedFileSuffix;

    /**
     * Name of the lock file. Default is "used.lock".
     */
    private String lockFileName;

    /**
     * Interval for the housekeeping. This is work like garbage collection or cache checking.
     * In combination with houseKeepingNanoTimeBudget the maximum processor time for housekeeping work can be set.
     * Default is 1 second.
     */
    private String housekeepingInterval;

    /**
     * Number of nanoseconds used for each housekeeping cycle.
     * Default is 10 milliseconds = 0.01 seconds.
     */
    private String housekeepingTimeBudget;

    /**
     * Usage of an adaptive housekeeping controller, which will increase the time budgets on demand,
     * if the garbage collector needs more time to reach the sweeping phase.
     */
    private boolean housekeepingAdaptive = false;

    /**
     * The threshold of the adaption cycle to calculate new budgets for the housekeeping process. Default is 5 seconds.
     */
    private String housekeepingIncreaseThreshold;

    /**
     * The amount the housekeeping budgets will be increased each cycle. Default is 50 ms.
     */
    private String housekeepingIncreaseAmount;

    /**
     * The upper limit of the housekeeping time budgets. Default is 0.5 seconds.
     */
    private String housekeepingMaximumTimeBudget;

    /**
     * The maximum size of a transaction file. If the file is larger than this value, it will be split into multiple files.
     * Default is 1 GiB.
     */
    private String transactionFileMaximumSize;

    /**
     * Timeout in milliseconds for the entity cache evaluator.
     * If an entity was not accessed in this timespan it will be removed from the cache. Default is 1 day.
     */
    private String entityCacheTimeout;

    /**
     * Abstract threshold value for the lifetime of entities in the cache. Default is 1000000000.
     */
    private String entityCacheThreshold;

    /**
     * Minimum file size for a data file to avoid cleaning it up. Default is 1024^2 = 1 MiB.
     */
    private String dataFileMinimumSize;

    /**
     * Maximum file size for a data file to avoid cleaning it up. Default is 1024^2*8 = 8 MiB.
     */
    private String dataFileMaximumSize;

    /**
     * The ratio (value in ]0.0;1.0]) of non-gap data contained in a storage file to prevent the file from being dissolved. Default is 0.75 (75%).
     */
    private String dataFileMinimumUseRatio;

    /**
     * A flag defining whether the current head file (the only file actively written to) shall be subjected to file cleanups as well.
     */
    private String dataFileCleanupHeadFile;

    /**
     * Is the {@code StorageManager} started when the CDI bean for the instance is created or not.
     * Be aware that when you don't rely on the autostart of the StorageManager, you are responsible for starting it
     * and might result in Exceptions when code is executed that relies on a started StorageManager.
     * Default value is true.
     */
    private boolean autoStart = true;

    /**
     * A flag defining whether the default handlers for JDK 1.7 classes shall be registered or not.
     * Default value is true.
     */
    private boolean registerJdk17Handlers = true;

    /**
     * Determines whether to register default handlers for JDK 1.8 classes.
     * By default, this is set to false for compatibility reasons with previously created storage.
     * Enabling this requires adding these handlers to your storage configuration later when used in another application with Eclipse Store.
     * Default value is false.
     */
    private boolean registerJdk8Handlers;

    public Class<?> getRoot()
    {
        return this.root;
    }

    public void setRoot(final Class<?> root)
    {
        this.root = root;
    }

    public String getStorageDirectory()
    {
        return this.storageDirectory;
    }

    public void setStorageDirectory(final String storageDirectory)
    {
        this.storageDirectory = storageDirectory;
    }

    public StorageFilesystem getStorageFilesystem()
    {
        return this.storageFilesystem;
    }

    public void setStorageFilesystem(final StorageFilesystem storageFilesystem)
    {
        this.storageFilesystem = storageFilesystem;
    }

    public String getDeletionDirectory()
    {
        return this.deletionDirectory;
    }

    public void setDeletionDirectory(final String deletionDirectory)
    {
        this.deletionDirectory = deletionDirectory;
    }

    public String getTruncationDirectory()
    {
        return this.truncationDirectory;
    }

    public void setTruncationDirectory(final String truncationDirectory)
    {
        this.truncationDirectory = truncationDirectory;
    }

    public String getBackupDirectory()
    {
        return this.backupDirectory;
    }

    public void setBackupDirectory(final String backupDirectory)
    {
        this.backupDirectory = backupDirectory;
    }

    public StorageFilesystem getBackupFilesystem()
    {
        return this.backupFilesystem;
    }

    public void setBackupFilesystem(final StorageFilesystem backupFilesystem)
    {
        this.backupFilesystem = backupFilesystem;
    }

    public String getChannelCount()
    {
        return this.channelCount;
    }

    public void setChannelCount(final String channelCount)
    {
        this.channelCount = channelCount;
    }

    public String getChannelDirectoryPrefix()
    {
        return this.channelDirectoryPrefix;
    }

    public void setChannelDirectoryPrefix(final String channelDirectoryPrefix)
    {
        this.channelDirectoryPrefix = channelDirectoryPrefix;
    }

    public String getDataFilePrefix()
    {
        return this.dataFilePrefix;
    }

    public void setDataFilePrefix(final String dataFilePrefix)
    {
        this.dataFilePrefix = dataFilePrefix;
    }

    public String getDataFileSuffix()
    {
        return this.dataFileSuffix;
    }

    public void setDataFileSuffix(final String dataFileSuffix)
    {
        this.dataFileSuffix = dataFileSuffix;
    }

    public String getTransactionFilePrefix()
    {
        return this.transactionFilePrefix;
    }

    public void setTransactionFilePrefix(final String transactionFilePrefix)
    {
        this.transactionFilePrefix = transactionFilePrefix;
    }

    public String getTransactionFileSuffix()
    {
        return this.transactionFileSuffix;
    }

    public void setTransactionFileSuffix(final String transactionFileSuffix)
    {
        this.transactionFileSuffix = transactionFileSuffix;
    }

    public String getTypeDictionaryFileName()
    {
        return this.typeDictionaryFileName;
    }

    public void setTypeDictionaryFileName(final String typeDictionaryFileName)
    {
        this.typeDictionaryFileName = typeDictionaryFileName;
    }

    public String getRescuedFileSuffix()
    {
        return this.rescuedFileSuffix;
    }

    public void setRescuedFileSuffix(final String rescuedFileSuffix)
    {
        this.rescuedFileSuffix = rescuedFileSuffix;
    }

    public String getLockFileName()
    {
        return this.lockFileName;
    }

    public void setLockFileName(final String lockFileName)
    {
        this.lockFileName = lockFileName;
    }

    public String getHousekeepingInterval()
    {
        return this.housekeepingInterval;
    }

    public void setHousekeepingInterval(final String housekeepingInterval)
    {
        this.housekeepingInterval = housekeepingInterval;
    }

    public String getHousekeepingTimeBudget()
    {
        return this.housekeepingTimeBudget;
    }

    public void setHousekeepingTimeBudget(final String housekeepingTimeBudget)
    {
        this.housekeepingTimeBudget = housekeepingTimeBudget;
    }

    public boolean isHousekeepingAdaptive()
    {
        return this.housekeepingAdaptive;
    }

    public void setHousekeepingAdaptive(final boolean housekeepingAdaptive)
    {
        this.housekeepingAdaptive = housekeepingAdaptive;
    }

    public String getHousekeepingIncreaseThreshold()
    {
        return this.housekeepingIncreaseThreshold;
    }

    public void setHousekeepingIncreaseThreshold(final String housekeepingIncreaseThreshold)
    {
        this.housekeepingIncreaseThreshold = housekeepingIncreaseThreshold;
    }

    public String getHousekeepingIncreaseAmount()
    {
        return this.housekeepingIncreaseAmount;
    }

    public void setHousekeepingIncreaseAmount(final String housekeepingIncreaseAmount)
    {
        this.housekeepingIncreaseAmount = housekeepingIncreaseAmount;
    }

    public String getHousekeepingMaximumTimeBudget()
    {
        return this.housekeepingMaximumTimeBudget;
    }

    public void setHousekeepingMaximumTimeBudget(final String housekeepingMaximumTimeBudget)
    {
        this.housekeepingMaximumTimeBudget = housekeepingMaximumTimeBudget;
    }

    public String getTransactionFileMaximumSize()
    {
        return this.transactionFileMaximumSize;
    }

    public void setTransactionFileMaximumSize(final String transactionFileMaximumSize)
    {
        this.transactionFileMaximumSize = transactionFileMaximumSize;
    }

    public String getEntityCacheTimeout()
    {
        return this.entityCacheTimeout;
    }

    public void setEntityCacheTimeout(final String entityCacheTimeout)
    {
        this.entityCacheTimeout = entityCacheTimeout;
    }

    public String getEntityCacheThreshold()
    {
        return this.entityCacheThreshold;
    }

    public void setEntityCacheThreshold(final String entityCacheThreshold)
    {
        this.entityCacheThreshold = entityCacheThreshold;
    }

    public String getDataFileMinimumSize()
    {
        return this.dataFileMinimumSize;
    }

    public void setDataFileMinimumSize(final String dataFileMinimumSize)
    {
        this.dataFileMinimumSize = dataFileMinimumSize;
    }

    public String getDataFileMaximumSize()
    {
        return this.dataFileMaximumSize;
    }

    public void setDataFileMaximumSize(final String dataFileMaximumSize)
    {
        this.dataFileMaximumSize = dataFileMaximumSize;
    }

    public String getDataFileMinimumUseRatio()
    {
        return this.dataFileMinimumUseRatio;
    }

    public void setDataFileMinimumUseRatio(final String dataFileMinimumUseRatio)
    {
        this.dataFileMinimumUseRatio = dataFileMinimumUseRatio;
    }

    public String getDataFileCleanupHeadFile()
    {
        return this.dataFileCleanupHeadFile;
    }

    public void setDataFileCleanupHeadFile(final String dataFileCleanupHeadFile)
    {
        this.dataFileCleanupHeadFile = dataFileCleanupHeadFile;
    }

    public boolean isAutoStart()
    {
        return this.autoStart;
    }

    public void setAutoStart(final boolean autoStart)
    {
        this.autoStart = autoStart;
    }

    public boolean isRegisterJdk17Handlers()
    {
        return this.registerJdk17Handlers;
    }

    public void setRegisterJdk17Handlers(final boolean registerJdk17Handlers)
    {
        this.registerJdk17Handlers = registerJdk17Handlers;
    }

    public boolean isRegisterJdk8Handlers()
    {
        return this.registerJdk8Handlers;
    }

    public void setRegisterJdk8Handlers(final boolean registerJdk8Handlers)
    {
        this.registerJdk8Handlers = registerJdk8Handlers;
    }
}
