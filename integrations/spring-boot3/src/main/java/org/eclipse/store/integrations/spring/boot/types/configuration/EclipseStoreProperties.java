package org.eclipse.store.integrations.spring.boot.types.configuration;

/*-
 * #%L
 * spring-boot3
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

    public Class<?> getRoot()
    {
        return root;
    }

    public void setRoot(Class<?> root)
    {
        this.root = root;
    }

    public String getStorageDirectory()
    {
        return storageDirectory;
    }

    public void setStorageDirectory(String storageDirectory)
    {
        this.storageDirectory = storageDirectory;
    }

    public StorageFilesystem getStorageFilesystem()
    {
        return storageFilesystem;
    }

    public void setStorageFilesystem(StorageFilesystem storageFilesystem)
    {
        this.storageFilesystem = storageFilesystem;
    }

    public String getDeletionDirectory()
    {
        return deletionDirectory;
    }

    public void setDeletionDirectory(String deletionDirectory)
    {
        this.deletionDirectory = deletionDirectory;
    }

    public String getTruncationDirectory()
    {
        return truncationDirectory;
    }

    public void setTruncationDirectory(String truncationDirectory)
    {
        this.truncationDirectory = truncationDirectory;
    }

    public String getBackupDirectory()
    {
        return backupDirectory;
    }

    public void setBackupDirectory(String backupDirectory)
    {
        this.backupDirectory = backupDirectory;
    }

    public StorageFilesystem getBackupFilesystem()
    {
        return backupFilesystem;
    }

    public void setBackupFilesystem(StorageFilesystem backupFilesystem)
    {
        this.backupFilesystem = backupFilesystem;
    }

    public String getChannelCount()
    {
        return channelCount;
    }

    public void setChannelCount(String channelCount)
    {
        this.channelCount = channelCount;
    }

    public String getChannelDirectoryPrefix()
    {
        return channelDirectoryPrefix;
    }

    public void setChannelDirectoryPrefix(String channelDirectoryPrefix)
    {
        this.channelDirectoryPrefix = channelDirectoryPrefix;
    }

    public String getDataFilePrefix()
    {
        return dataFilePrefix;
    }

    public void setDataFilePrefix(String dataFilePrefix)
    {
        this.dataFilePrefix = dataFilePrefix;
    }

    public String getDataFileSuffix()
    {
        return dataFileSuffix;
    }

    public void setDataFileSuffix(String dataFileSuffix)
    {
        this.dataFileSuffix = dataFileSuffix;
    }

    public String getTransactionFilePrefix()
    {
        return transactionFilePrefix;
    }

    public void setTransactionFilePrefix(String transactionFilePrefix)
    {
        this.transactionFilePrefix = transactionFilePrefix;
    }

    public String getTransactionFileSuffix()
    {
        return transactionFileSuffix;
    }

    public void setTransactionFileSuffix(String transactionFileSuffix)
    {
        this.transactionFileSuffix = transactionFileSuffix;
    }

    public String getTypeDictionaryFileName()
    {
        return typeDictionaryFileName;
    }

    public void setTypeDictionaryFileName(String typeDictionaryFileName)
    {
        this.typeDictionaryFileName = typeDictionaryFileName;
    }

    public String getRescuedFileSuffix()
    {
        return rescuedFileSuffix;
    }

    public void setRescuedFileSuffix(String rescuedFileSuffix)
    {
        this.rescuedFileSuffix = rescuedFileSuffix;
    }

    public String getLockFileName()
    {
        return lockFileName;
    }

    public void setLockFileName(String lockFileName)
    {
        this.lockFileName = lockFileName;
    }

    public String getHousekeepingInterval()
    {
        return housekeepingInterval;
    }

    public void setHousekeepingInterval(String housekeepingInterval)
    {
        this.housekeepingInterval = housekeepingInterval;
    }

    public String getHousekeepingTimeBudget()
    {
        return housekeepingTimeBudget;
    }

    public void setHousekeepingTimeBudget(String housekeepingTimeBudget)
    {
        this.housekeepingTimeBudget = housekeepingTimeBudget;
    }

    public String getEntityCacheTimeout()
    {
        return entityCacheTimeout;
    }

    public void setEntityCacheTimeout(String entityCacheTimeout)
    {
        this.entityCacheTimeout = entityCacheTimeout;
    }

    public String getEntityCacheThreshold()
    {
        return entityCacheThreshold;
    }

    public void setEntityCacheThreshold(String entityCacheThreshold)
    {
        this.entityCacheThreshold = entityCacheThreshold;
    }

    public String getDataFileMinimumSize()
    {
        return dataFileMinimumSize;
    }

    public void setDataFileMinimumSize(String dataFileMinimumSize)
    {
        this.dataFileMinimumSize = dataFileMinimumSize;
    }

    public String getDataFileMaximumSize()
    {
        return dataFileMaximumSize;
    }

    public void setDataFileMaximumSize(String dataFileMaximumSize)
    {
        this.dataFileMaximumSize = dataFileMaximumSize;
    }

    public String getDataFileMinimumUseRatio()
    {
        return dataFileMinimumUseRatio;
    }

    public void setDataFileMinimumUseRatio(String dataFileMinimumUseRatio)
    {
        this.dataFileMinimumUseRatio = dataFileMinimumUseRatio;
    }

    public String getDataFileCleanupHeadFile()
    {
        return dataFileCleanupHeadFile;
    }

    public void setDataFileCleanupHeadFile(String dataFileCleanupHeadFile)
    {
        this.dataFileCleanupHeadFile = dataFileCleanupHeadFile;
    }

    public boolean getAutoStart()
    {
        return autoStart;
    }

    public void setAutoStart(boolean autoStart)
    {
        this.autoStart = autoStart;
    }

    public boolean isAutoStart()
    {
        return autoStart;
    }

    public boolean isRegisterJdk17Handlers()
    {
        return registerJdk17Handlers;
    }

    public void setRegisterJdk17Handlers(boolean registerJdk17Handlers)
    {
        this.registerJdk17Handlers = registerJdk17Handlers;
    }
}
