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

/**
 * The {@code ConfigKeys} enum provides a list of configuration keys used in the application.
 * These keys are used to retrieve configuration values from the application's configuration files.
 * The keys are grouped by the service they are related to (e.g., AWS, Azure, Oracle Cloud, etc.).
 */
public enum ConfigKeys
{

    //storage-filesystem
    SQL("sql"),
    MARIADB("mariadb"),
    ORACLE("oracle"),
    POSTGRES("postgres"),
    SQLITE("sqlite"),
    
    // commons
    CACHE("cache"),
    TARGET("target"),
    
    //aws
    AWS("aws"),
    DYNAMODB("dynamodb"),
    S3("s3"),
    AWS_CREDENTIALS_TYPE("credentials.type"),
    AWS_CREDENTIALS_ACCESS_KEY_ID("credentials.access-key-id"),
    AWS_CREDENTIALS_SECRET_ACCESS_KEY("credentials.secret-access-key"),
    AWS_ENDPOINT_OVERRIDE("endpoint-override"),
    AWS_REGION("region"),
    AWS_DIRECTORY_BUCKET("directory-bucket"),

    //azure
    AZURE("azure"),
    AZURE_STORAGE_ENDPOINT("storage.endpoint"),
    AZURE_STORAGE_CONNECTION_STRING("storage.connection-string"),
    AZURE_STORAGE_ENCRYPTION_SCOPE("storage.encryption-scope"),
    AZURE_STORAGE_CREDENTIALS_TYPE("storage.credentials.type"),
    AZURE_STORAGE_CREDENTIALS_USERNAME("storage.credentials.username"),
    AZURE_STORAGE_CREDENTIALS_PASSWORD("storage.credentials.password"),
    AZURE_STORAGE_CREDENTIALS_ACCOUNT_NAME("storage.credentials.account-name"),
    AZURE_STORAGE_CREDENTIALS_ACCOUNT_KEY("storage.credentials.account-key"),

    //Hazelcast
    HAZELCAST_CONFIGURATION("hazelcast.configuration"),

    // Oracle Cloud
    ORACLECLOUD("oraclecloud"),
    ORACLECLOUD_CONFIG_FILE_PATH("object-storage.config-file.path"),
    ORACLECLOUD_CONFIG_FILE_PROFILE("object-storage.config-file.profile"),
    ORACLECLOUD_CONFIG_FILE_CHARSET("object-storage.config-file.charset"),
    ORACLECLOUD_CLIENT_CONNECTION_TIMEOUT_MILLIS("object-storage.client.connection-timeout-millis"),
    ORACLECLOUD_CLIENT_READ_TIMEOUT_MILLIS("object-storage.client.read-timeout-millis"),
    ORACLECLOUD_CLIENT_MAX_ASYNC_THREADS("object-storage.client.max-async-threads"),
    ORACLECLOUD_REGION("object-storage.region"),
    ORACLECLOUD_ENDPOINT("object-storage.endpoint"),

    //Coherence
    COHERENCE("coherence"),
    COHERENCE_CACHE_NAME("cache-name"),
    COHERENCE_CACHE_CONFIG("cache-config"),

    //redis
    REDIS_URI("redis.uri"),

    //sql
    SQL_DATA_SOURCE_PROVIDER("data-source-provider"),
    SQL_CATALOG("catalog"),
    SQL_SCHEMA("schema"),
    SQL_URL("url"),
    SQL_USER("user"),
    SQL_PASSWORD("password");

    private final String value;

    /**
     * Constructs a new {@code ConfigKeys} enum with the provided value.
     *
     * @param value The string value of the configuration key.
     */
    ConfigKeys(final String value)
    {
        this.value = value;
    }

    /**
     * Returns the string value of the configuration key.
     *
     * @return The string value of the configuration key.
     */
    public String value()
    {
        return this.value;
    }
}
