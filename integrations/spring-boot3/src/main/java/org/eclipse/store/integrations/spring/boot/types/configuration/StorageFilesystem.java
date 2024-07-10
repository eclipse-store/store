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
	/**
	 * The target type of the file system
	 */
	private String target;

	@NestedConfigurationProperty
	private Sql sql;
	
	@NestedConfigurationProperty
	private Aws aws;
	
	@NestedConfigurationProperty
	private Azure azure;
	
	/**
	 * Supported properties
	 * All supported properties of Kafka, see <a href="https://kafka.apache.org/documentation/">...</a>
	 */
	private Map<String, String> kafkaProperties;
	
	@NestedConfigurationProperty
	private Oraclecloud oraclecloud;
	
	@NestedConfigurationProperty
	private Googlecloud googlecloud;
	
	@NestedConfigurationProperty
	private Redis redis;
	
	public String getTarget()
	{
		return this.target;
	}
	
	public void setTarget(final String target)
	{
		this.target = target;
	}
	
	public Sql getSql()
	{
	    return this.sql;
	}
	
	public void setSql(final Sql sql)
	{
	    this.sql = sql;
	}
	
	public Aws getAws()
	{
	    return this.aws;
	}
	
	public void setAws(final Aws aws)
	{
	    this.aws = aws;
	}
	
	public Map<String, String> getKafkaProperties()
	{
	    return this.kafkaProperties;
	}
	
	public void setKafkaProperties(final Map<String, String> kafkaProperties)
	{
	    this.kafkaProperties = kafkaProperties;
	}
	
	public Oraclecloud getOraclecloud()
	{
	    return this.oraclecloud;
	}
	
	public void setOraclecloud(final Oraclecloud oraclecloud)
	{
	    this.oraclecloud = oraclecloud;
	}
	
	public Redis getRedis()
	{
	    return this.redis;
	}
	
	public void setRedis(final Redis redis)
	{
	    this.redis = redis;
	}
	
	public Azure getAzure()
	{
	    return this.azure;
	}
	
	public void setAzure(final Azure azure)
	{
	    this.azure = azure;
	}
	
	public Googlecloud getGooglecloud()
	{
	    return this.googlecloud;
	}
	
	public void setGooglecloud(final Googlecloud googlecloud)
	{
	    this.googlecloud = googlecloud;
	}
}
