package org.eclipse.store.integrations.spring.boot.types.configuration.azure;

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

public class Storage
{

    @NestedConfigurationProperty
    private Credentials credentials;

    /**
     * Sets the blob service endpoint, additionally parses it for information (SAS token).
     */
    private String endpoint;

    /**
     * Sets the connection string to connect to the service.
     */
    private String connectionString;

    /**
     * Sets the encryption scope that is used to encrypt blob contents on the server.
     */
    private String encryptionScope;

    public Credentials getCredentials()
    {
        return this.credentials;
    }

    public void setCredentials(final Credentials credentials)
    {
        this.credentials = credentials;
    }

    public String getEndpoint()
    {
        return this.endpoint;
    }

    public void setEndpoint(final String endpoint)
    {
        this.endpoint = endpoint;
    }

    public String getConnectionString()
    {
        return this.connectionString;
    }

    public void setConnectionString(final String connectionString)
    {
        this.connectionString = connectionString;
    }

    public String getEncryptionScope()
    {
        return this.encryptionScope;
    }

    public void setEncryptionScope(final String encryptionScope)
    {
        this.encryptionScope = encryptionScope;
    }
}
