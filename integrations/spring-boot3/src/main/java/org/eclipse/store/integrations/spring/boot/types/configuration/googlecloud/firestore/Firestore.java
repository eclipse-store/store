package org.eclipse.store.integrations.spring.boot.types.configuration.googlecloud.firestore;

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

import org.springframework.boot.context.properties.NestedConfigurationProperty;

public class Firestore
{
    /**
     * the database ID to use with this Firestore client.
     */
    private String databaseId;

    /**
     * The emulator host to use with this Firestore client.
     */
    private String emulatorHost;

    /**
     * The service host.
     */
    private String host;

    /**
     * The project ID. If no project ID is set, the project ID from the environment will be used.
     */
    private String projectId;

    /**
     * The project ID that specifies the project used for quota and billing purposes.
     */
    private String quotaProjectId;

    @NestedConfigurationProperty
    private Credentials credentials;

    public String getDatabaseId()
    {
        return this.databaseId;
    }

    public void setDatabaseId(final String databaseId)
    {
        this.databaseId = databaseId;
    }

    public String getEmulatorHost()
    {
        return this.emulatorHost;
    }

    public void setEmulatorHost(final String emulatorHost)
    {
        this.emulatorHost = emulatorHost;
    }

    public String getHost()
    {
        return this.host;
    }

    public void setHost(final String host)
    {
        this.host = host;
    }

    public String getProjectId()
    {
        return this.projectId;
    }

    public void setProjectId(final String projectId)
    {
        this.projectId = projectId;
    }

    public String getQuotaProjectId()
    {
        return this.quotaProjectId;
    }

    public void setQuotaProjectId(final String quotaProjectId)
    {
        this.quotaProjectId = quotaProjectId;
    }

    public Credentials getCredentials()
    {
        return this.credentials;
    }

    public void setCredentials(final Credentials credentials)
    {
        this.credentials = credentials;
    }

}
