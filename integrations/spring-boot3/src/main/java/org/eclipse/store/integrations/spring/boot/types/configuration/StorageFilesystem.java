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


import java.util.Map;

import org.eclipse.store.integrations.spring.boot.types.configuration.aws.Aws;
import org.eclipse.store.integrations.spring.boot.types.configuration.azure.Azure;
import org.eclipse.store.integrations.spring.boot.types.configuration.googlecloud.Googlecloud;
import org.eclipse.store.integrations.spring.boot.types.configuration.oraclecloud.Oraclecloud;
import org.eclipse.store.integrations.spring.boot.types.configuration.redis.Redis;
import org.eclipse.store.integrations.spring.boot.types.configuration.sql.Sql;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

public class StorageFilesystem
{

    @NestedConfigurationProperty
    private Sql sql;

    @NestedConfigurationProperty
    private Aws aws;

    @NestedConfigurationProperty
    private Azure azure;

    /**
     * Supported properties
     * All supported properties of Kafka, see https://kafka.apache.org/documentation/
     */
    private Map<String, String> kafkaProperties;

    @NestedConfigurationProperty
    private Oraclecloud oraclecloud;

    @NestedConfigurationProperty
    private Googlecloud googlecloud;

    @NestedConfigurationProperty
    private Redis redis;

    public Sql getSql()
    {
        return sql;
    }

    public void setSql(Sql sql)
    {
        this.sql = sql;
    }

    public Aws getAws()
    {
        return aws;
    }

    public void setAws(Aws aws)
    {
        this.aws = aws;
    }

    public Map<String, String> getKafkaProperties()
    {
        return kafkaProperties;
    }

    public void setKafkaProperties(Map<String, String> kafkaProperties)
    {
        this.kafkaProperties = kafkaProperties;
    }

    public Oraclecloud getOraclecloud()
    {
        return oraclecloud;
    }

    public void setOraclecloud(Oraclecloud oraclecloud)
    {
        this.oraclecloud = oraclecloud;
    }

    public Redis getRedis()
    {
        return redis;
    }

    public void setRedis(Redis redis)
    {
        this.redis = redis;
    }

    public Azure getAzure()
    {
        return azure;
    }

    public void setAzure(Azure azure)
    {
        this.azure = azure;
    }

    public Googlecloud getGooglecloud()
    {
        return googlecloud;
    }

    public void setGooglecloud(Googlecloud googlecloud)
    {
        this.googlecloud = googlecloud;
    }
}
