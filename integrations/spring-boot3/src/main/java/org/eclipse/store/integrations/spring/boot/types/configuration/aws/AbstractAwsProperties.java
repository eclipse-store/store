package org.eclipse.store.integrations.spring.boot.types.configuration.aws;

/*-
 * #%L
 * microstream-integrations-spring-boot3
 * %%
 * Copyright (C) 2019 - 2023 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import org.springframework.boot.context.properties.NestedConfigurationProperty;

public abstract class AbstractAwsProperties
{

    @NestedConfigurationProperty
    private Credentials credentials;

    /**
     * The endpoint with which the SDK should communicate.
     */
    private String endpointOverride;

    /**
     * Configure the region with which the SDK should communicate. If this is not specified, the SDK will attempt to identify the endpoint automatically using the following logic:
     * <ol>
     * <li>Check the 'aws.region' system property for the region.</li>
     * <li>Check the 'AWS_REGION' environment variable for the region.</li>
     * <li>Check the {user.home}/.aws/credentials and {user.home}/.aws/config files for the region.</li>
     * <li>If running in EC2, check the EC2 metadata service for the region.</li>
     * </ol>
     */
    private String region;


    public Credentials getCredentials()
    {
        return credentials;
    }

    public void setCredentials(Credentials credentials)
    {
        this.credentials = credentials;
    }

    public String getEndpointOverride()
    {
        return endpointOverride;
    }

    public void setEndpointOverride(String endpointOverride)
    {
        this.endpointOverride = endpointOverride;
    }

    public String getRegion()
    {
        return region;
    }

    public void setRegion(String region)
    {
        this.region = region;
    }
}
