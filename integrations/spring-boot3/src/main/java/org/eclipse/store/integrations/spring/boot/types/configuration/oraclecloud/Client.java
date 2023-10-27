package org.eclipse.store.integrations.spring.boot.types.configuration.oraclecloud;

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

public class Client
{

    /**
     * The max time to wait for a connection, in millis. Default is 10000.
     */
    private String connectionTimeoutMillis;

    /**
     * The max time to wait for data, in millis. Default is 60000.
     */
    private String readTimeoutMillis;

    /**
     * The max number of async threads to use. Default is 50.
     */
    private String maxAsyncThreads;

    public String getConnectionTimeoutMillis()
    {
        return connectionTimeoutMillis;
    }

    public void setConnectionTimeoutMillis(String connectionTimeoutMillis)
    {
        this.connectionTimeoutMillis = connectionTimeoutMillis;
    }

    public String getReadTimeoutMillis()
    {
        return readTimeoutMillis;
    }

    public void setReadTimeoutMillis(String readTimeoutMillis)
    {
        this.readTimeoutMillis = readTimeoutMillis;
    }

    public String getMaxAsyncThreads()
    {
        return maxAsyncThreads;
    }

    public void setMaxAsyncThreads(String maxAsyncThreads)
    {
        this.maxAsyncThreads = maxAsyncThreads;
    }
}
