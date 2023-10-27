package org.eclipse.store.integrations.spring.boot.types.configuration.redis;

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

public class Redis
{

    /**
     * The RedisURI contains the host/port and can carry authentication/database details. On a successful connect you get authenticated, and the database is selected afterward. This applies also after re-establishing a connection after a connection loss.
     */
    private String uri;

    public String getUri()
    {
        return uri;
    }

    public void setUri(String uri)
    {
        this.uri = uri;
    }
}
