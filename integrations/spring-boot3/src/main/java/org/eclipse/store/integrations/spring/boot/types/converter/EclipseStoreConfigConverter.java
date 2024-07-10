package org.eclipse.store.integrations.spring.boot.types.converter;

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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.eclipse.store.integrations.spring.boot.types.configuration.EclipseStoreProperties;
import org.eclipse.store.integrations.spring.boot.types.configuration.StorageFilesystem;
import org.eclipse.store.integrations.spring.boot.types.configuration.aws.AbstractAwsProperties;
import org.eclipse.store.integrations.spring.boot.types.configuration.aws.Aws;
import org.eclipse.store.integrations.spring.boot.types.configuration.aws.S3;
import org.eclipse.store.integrations.spring.boot.types.configuration.azure.Azure;
import org.eclipse.store.integrations.spring.boot.types.configuration.oraclecloud.Oraclecloud;
import org.eclipse.store.integrations.spring.boot.types.configuration.sql.AbstractSqlConfiguration;
import org.eclipse.store.integrations.spring.boot.types.configuration.sql.Sql;
import org.eclipse.store.storage.embedded.configuration.types.EmbeddedStorageConfigurationPropertyNames;

/**
 * The {@code EclipseStoreConfigConverter} class is a Spring component that converts the Eclipse Store properties into a map.
 * This map can then be used to configure the Eclipse Store.
 */
public class EclipseStoreConfigConverter
{

    // Fields for testing if all keys from Eclipse Store Configuration are covered by these module.
    // These fields are protected, so they can be accessed in tests.
    // Each field represents a key in the Eclipse Store Configuration.

    // Fields for the storage directory configuration
    protected static final String STORAGE_DIRECTORY = EmbeddedStorageConfigurationPropertyNames.STORAGE_DIRECTORY;
    protected static final String STORAGE_FILESYSTEM = EmbeddedStorageConfigurationPropertyNames.STORAGE_FILESYSTEM;
    protected static final String DELETION_DIRECTORY = EmbeddedStorageConfigurationPropertyNames.DELETION_DIRECTORY;
    protected static final String TRUNCATION_DIRECTORY = EmbeddedStorageConfigurationPropertyNames.TRUNCATION_DIRECTORY;
    protected static final String BACKUP_DIRECTORY = EmbeddedStorageConfigurationPropertyNames.BACKUP_DIRECTORY;
    protected static final String BACKUP_FILESYSTEM = EmbeddedStorageConfigurationPropertyNames.BACKUP_FILESYSTEM;

    // Fields for the channel configuration
    protected static final String CHANNEL_COUNT = EmbeddedStorageConfigurationPropertyNames.CHANNEL_COUNT;
    protected static final String CHANNEL_DIRECTORY_PREFIX = EmbeddedStorageConfigurationPropertyNames.CHANNEL_DIRECTORY_PREFIX;

    // Fields for configuring file names
    protected static final String DATA_FILE_PREFIX = EmbeddedStorageConfigurationPropertyNames.DATA_FILE_PREFIX;
    protected static final String DATA_FILE_SUFFIX = EmbeddedStorageConfigurationPropertyNames.DATA_FILE_SUFFIX;
    protected static final String TRANSACTION_FILE_PREFIX = EmbeddedStorageConfigurationPropertyNames.TRANSACTION_FILE_PREFIX;
    protected static final String TRANSACTION_FILE_SUFFIX = EmbeddedStorageConfigurationPropertyNames.TRANSACTION_FILE_SUFFIX;
    protected static final String TRANSACTION_FILE_MAXIMUM_SIZE = EmbeddedStorageConfigurationPropertyNames.TRANSACTION_FILE_MAXIMUM_SIZE;
    protected static final String TYPE_DICTIONARY_FILE_NAME = EmbeddedStorageConfigurationPropertyNames.TYPE_DICTIONARY_FILE_NAME;
    protected static final String RESCUED_FILE_SUFFIX = EmbeddedStorageConfigurationPropertyNames.RESCUED_FILE_SUFFIX;
    protected static final String LOCK_FILE_NAME = EmbeddedStorageConfigurationPropertyNames.LOCK_FILE_NAME;

    // Fields for the housekeeping configuration
    protected static final String HOUSEKEEPING_INTERVAL = EmbeddedStorageConfigurationPropertyNames.HOUSEKEEPING_INTERVAL;
    protected static final String HOUSEKEEPING_TIME_BUDGET = EmbeddedStorageConfigurationPropertyNames.HOUSEKEEPING_TIME_BUDGET;
    protected static final String HOUSEKEEPING_ADAPTIVE = EmbeddedStorageConfigurationPropertyNames.HOUSEKEEPING_ADAPTIVE;
    protected static final String HOUSEKEEPING_INCREASE_THRESHOLD = EmbeddedStorageConfigurationPropertyNames.HOUSEKEEPING_INCREASE_THRESHOLD;
    protected static final String HOUSEKEEPING_INCREASE_AMOUNT = EmbeddedStorageConfigurationPropertyNames.HOUSEKEEPING_INCREASE_AMOUNT;
    protected static final String HOUSEKEEPING_MAXIMUM_TIME_BUDGET = EmbeddedStorageConfigurationPropertyNames.HOUSEKEEPING_MAXIMUM_TIME_BUDGET;

    // Fields for the entity cache configuration
    protected static final String ENTITY_CACHE_THRESHOLD = EmbeddedStorageConfigurationPropertyNames.ENTITY_CACHE_THRESHOLD;
    protected static final String ENTITY_CACHE_TIMEOUT = EmbeddedStorageConfigurationPropertyNames.ENTITY_CACHE_TIMEOUT;

    // Fields for the data file configuration
    protected static final String DATA_FILE_MINIMUM_SIZE = EmbeddedStorageConfigurationPropertyNames.DATA_FILE_MINIMUM_SIZE;
    protected static final String DATA_FILE_MAXIMUM_SIZE = EmbeddedStorageConfigurationPropertyNames.DATA_FILE_MAXIMUM_SIZE;
    protected static final String DATA_FILE_MINIMUM_USE_RATIO = EmbeddedStorageConfigurationPropertyNames.DATA_FILE_MINIMUM_USE_RATIO;
    protected static final String DATA_FILE_CLEANUP_HEAD_FILE = EmbeddedStorageConfigurationPropertyNames.DATA_FILE_CLEANUP_HEAD_FILE;


    /**
     * Converts the provided Eclipse Store properties into a map.
     * The keys of the map are the configuration keys and the values are the corresponding configuration values.
     *
     * @param properties The Eclipse Store properties to convert.
     * @return A map representing the provided Eclipse Store properties.
     */
    public Map<String, String> convertConfigurationToMap(final EclipseStoreProperties properties)
    {

        final Map<String, String> configValues = new HashMap<>();
        configValues.put(STORAGE_DIRECTORY, properties.getStorageDirectory());

        if (properties.getStorageFilesystem() != null)
        {
            configValues.putAll(this.prepareFileSystem(properties.getStorageFilesystem(), STORAGE_FILESYSTEM));
        }

        configValues.put(DELETION_DIRECTORY, properties.getDeletionDirectory());
        configValues.put(TRUNCATION_DIRECTORY, properties.getTruncationDirectory());
        configValues.put(BACKUP_DIRECTORY, properties.getBackupDirectory());

        if (properties.getBackupFilesystem() != null)
        {
            configValues.putAll(this.prepareFileSystem(properties.getBackupFilesystem(), BACKUP_FILESYSTEM));
        }

        configValues.put(CHANNEL_COUNT, properties.getChannelCount());
        configValues.put(CHANNEL_DIRECTORY_PREFIX, properties.getChannelDirectoryPrefix());
        configValues.put(DATA_FILE_PREFIX, properties.getDataFilePrefix());
        configValues.put(DATA_FILE_SUFFIX, properties.getDataFileSuffix());
        configValues.put(TRANSACTION_FILE_PREFIX, properties.getTransactionFilePrefix());
        configValues.put(TRANSACTION_FILE_SUFFIX, properties.getTransactionFileSuffix());
        configValues.put(TRANSACTION_FILE_MAXIMUM_SIZE, properties.getTransactionFileMaximumSize());
        configValues.put(TYPE_DICTIONARY_FILE_NAME, properties.getTypeDictionaryFileName());
        configValues.put(RESCUED_FILE_SUFFIX, properties.getRescuedFileSuffix());
        configValues.put(LOCK_FILE_NAME, properties.getLockFileName());
        configValues.put(HOUSEKEEPING_INTERVAL, properties.getHousekeepingInterval());
        configValues.put(HOUSEKEEPING_TIME_BUDGET, properties.getHousekeepingTimeBudget());
        configValues.put(HOUSEKEEPING_ADAPTIVE, properties.isHousekeepingAdaptive() ? "true" : null);
        configValues.put(HOUSEKEEPING_INCREASE_THRESHOLD, properties.getHousekeepingIncreaseThreshold());
        configValues.put(HOUSEKEEPING_INCREASE_AMOUNT, properties.getHousekeepingIncreaseAmount());
        configValues.put(HOUSEKEEPING_MAXIMUM_TIME_BUDGET, properties.getHousekeepingMaximumTimeBudget());
        configValues.put(ENTITY_CACHE_THRESHOLD, properties.getEntityCacheThreshold());
        configValues.put(ENTITY_CACHE_TIMEOUT, properties.getEntityCacheTimeout());
        configValues.put(DATA_FILE_MINIMUM_SIZE, properties.getDataFileMinimumSize());
        configValues.put(DATA_FILE_MAXIMUM_SIZE, properties.getDataFileMaximumSize());
        configValues.put(DATA_FILE_MINIMUM_USE_RATIO, properties.getDataFileMinimumUseRatio());
        configValues.put(DATA_FILE_CLEANUP_HEAD_FILE, properties.getDataFileCleanupHeadFile());


        //remove keys with null value
        configValues.values().removeIf(Objects::isNull);


        return configValues;
    }

    private Map<String, String> prepareFileSystem(final StorageFilesystem properties, final String key)
    {
        final Map<String, String> values = new HashMap<>();
        
        final String target = properties.getTarget();
        if(target != null)
        {
        	values.put(this.composeKey(key, ConfigKeys.TARGET.value()), target);
        }
        
        if (properties.getSql() != null)
        {
            values.putAll(this.prepareSql(properties.getSql(), this.composeKey(key, ConfigKeys.SQL.value())));
        }
        if (properties.getAws() != null)
        {
            values.putAll(this.prepareAws(properties.getAws(), this.composeKey(key, ConfigKeys.AWS.value())));
        }
        if (properties.getAzure() != null)
        {
            values.putAll(this.prepareAzure(properties.getAzure(), this.composeKey(key, ConfigKeys.AZURE.value())));
        }
        if (properties.getOraclecloud() != null)
        {
            values.putAll(this.prepareOracleCloud(properties.getOraclecloud(), this.composeKey(key, ConfigKeys.ORACLECLOUD.value())));
        }
        if (properties.getRedis() != null)
        {
            values.put(ConfigKeys.REDIS_URI.value(), properties.getRedis().getUri());
        }


        return values;
    }

    private Map<String, String> prepareOracleCloud(final Oraclecloud oraclecloud, final String key)
    {
        final Map<String, String> values = new HashMap<>();
        values.put(this.composeKey(key, ConfigKeys.ORACLECLOUD_CONFIG_FILE_PATH.value()), oraclecloud.getObjectStorage().getConfigFile().getPath());
        values.put(this.composeKey(key, ConfigKeys.ORACLECLOUD_CONFIG_FILE_PROFILE.value()), oraclecloud.getObjectStorage().getConfigFile().getProfile());
        values.put(this.composeKey(key, ConfigKeys.ORACLECLOUD_CONFIG_FILE_CHARSET.value()), oraclecloud.getObjectStorage().getConfigFile().getCharset());
        values.put(this.composeKey(key, ConfigKeys.ORACLECLOUD_CLIENT_CONNECTION_TIMEOUT_MILLIS.value()), oraclecloud.getObjectStorage().getClient().getConnectionTimeoutMillis());
        values.put(this.composeKey(key, ConfigKeys.ORACLECLOUD_CLIENT_READ_TIMEOUT_MILLIS.value()), oraclecloud.getObjectStorage().getClient().getReadTimeoutMillis());
        values.put(this.composeKey(key, ConfigKeys.ORACLECLOUD_CLIENT_MAX_ASYNC_THREADS.value()), oraclecloud.getObjectStorage().getClient().getMaxAsyncThreads());
        values.put(this.composeKey(key, ConfigKeys.ORACLECLOUD_REGION.value()), oraclecloud.getObjectStorage().getRegion());
        values.put(this.composeKey(key, ConfigKeys.ORACLECLOUD_ENDPOINT.value()), oraclecloud.getObjectStorage().getEndpoint());
        return values;
    }


    private Map<String, String> prepareAzure(final Azure azure, final String key)
    {
        final Map<String, String> values = new HashMap<>();
        values.put(this.composeKey(key, ConfigKeys.AZURE_STORAGE_CONNECTION_STRING.value()), azure.getStorage().getConnectionString());
        values.put(this.composeKey(key, ConfigKeys.AZURE_STORAGE_ENCRYPTION_SCOPE.value()), azure.getStorage().getEncryptionScope());
        values.put(this.composeKey(key, ConfigKeys.AZURE_STORAGE_CREDENTIALS_TYPE.value()), azure.getStorage().getCredentials().getType());
        values.put(this.composeKey(key, ConfigKeys.AZURE_STORAGE_CREDENTIALS_USERNAME.value()), azure.getStorage().getCredentials().getUsername());
        values.put(this.composeKey(key, ConfigKeys.AZURE_STORAGE_CREDENTIALS_PASSWORD.value()), azure.getStorage().getCredentials().getPassword());
        values.put(this.composeKey(key, ConfigKeys.AZURE_STORAGE_CREDENTIALS_ACCOUNT_NAME.value()), azure.getStorage().getCredentials().getAccountMame());
        values.put(this.composeKey(key, ConfigKeys.AZURE_STORAGE_CREDENTIALS_ACCOUNT_KEY.value()), azure.getStorage().getCredentials().getAccountKey());
        return values;
    }

    private Map<String, String> prepareAws(final Aws aws, final String key)
    {
        final Map<String, String> values = new HashMap<>();
        if (aws.getDynamodb() != null)
        {
            values.putAll(this.prepareAwsProperties(aws.getDynamodb(), this.composeKey(key, ConfigKeys.DYNAMODB.value())));
        }
        final S3 s3 = aws.getS3();
        if (s3 != null)
        {
            final String s3Key = this.composeKey(key, ConfigKeys.S3.value());
            values.putAll(this.prepareAwsProperties(s3, s3Key));
            values.put(this.composeKey(s3Key, ConfigKeys.AWS_DIRECTORY_BUCKET.value()), Boolean.toString(s3.isDirectoryBucket()));
        }
        return values;
    }


    private Map<String, String> prepareAwsProperties(final AbstractAwsProperties awsProperties, final String key)
    {
        final Map<String, String> values = new HashMap<>();
        values.put(this.composeKey(key, ConfigKeys.CACHE.value()), Boolean.toString(awsProperties.isCache()));
        values.put(this.composeKey(key, ConfigKeys.AWS_ENDPOINT_OVERRIDE.value()), awsProperties.getEndpointOverride());
        values.put(this.composeKey(key, ConfigKeys.AWS_REGION.value()), awsProperties.getRegion());
        values.put(this.composeKey(key, ConfigKeys.AWS_CREDENTIALS_TYPE.value()), awsProperties.getCredentials().getType());
        values.put(this.composeKey(key, ConfigKeys.AWS_CREDENTIALS_ACCESS_KEY_ID.value()), awsProperties.getCredentials().getAccessKeyId());
        values.put(this.composeKey(key, ConfigKeys.AWS_CREDENTIALS_SECRET_ACCESS_KEY.value()), awsProperties.getCredentials().getSecretAccessKey());
        return values;
    }


    private Map<String, String> prepareSql(final Sql sql, final String key)
    {
        final Map<String, String> values = new HashMap<>();
        if (sql.getMariadb() != null)
        {
            values.putAll(this.prepareSqlBasic(sql.getMariadb(), this.composeKey(key, ConfigKeys.MARIADB.value())));
        }
        if (sql.getOracle() != null)
        {
            values.putAll(this.prepareSqlBasic(sql.getOracle(), this.composeKey(key, ConfigKeys.ORACLE.value())));
        }
        if (sql.getPostgres() != null)
        {
            values.putAll(this.prepareSqlBasic(sql.getPostgres(), this.composeKey(key, ConfigKeys.POSTGRES.value())));
        }
        if (sql.getSqlite() != null)
        {
            values.putAll(this.prepareSqlBasic(sql.getSqlite(), this.composeKey(key, ConfigKeys.SQLITE.value())));
        }

        return values;
    }

    private Map<String, String> prepareSqlBasic(final AbstractSqlConfiguration properties, final String key)
    {
        final Map<String, String> values = new HashMap<>();
        values.put(this.composeKey(key, ConfigKeys.SQL_DATA_SOURCE_PROVIDER.value()), properties.getDataSourceProvider());
        values.put(this.composeKey(key, ConfigKeys.SQL_CATALOG.value()), properties.getCatalog());
        values.put(this.composeKey(key, ConfigKeys.SQL_SCHEMA.value()), properties.getSchema());
        values.put(this.composeKey(key, ConfigKeys.SQL_URL.value()), properties.getUrl());
        values.put(this.composeKey(key, ConfigKeys.SQL_USER.value()), properties.getUser());
        values.put(this.composeKey(key, ConfigKeys.SQL_PASSWORD.value()), properties.getPassword());
        return values;
    }

    protected String composeKey(final String prefix, final String suffix)
    {
        return prefix + "." + suffix;
    }

}
