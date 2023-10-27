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
     * he database ID to use with this Firestore client.
     */
    private String databaseId;

    /**
     *
     * The emulator host to use with this Firestore client.
     */
    private String emulatorHost;

    /**
     * The service host.
     */
    private String host;

    /**
     *
     * The project ID. If no project ID is set, the project ID from the environment will be used.
     */
    private String projectId;

    /**
     * The project ID that specifies the project used for quota and billing purposes.
     */
    private String quotaProjectId;

    @NestedConfigurationProperty
    private Credentials credentials;
}
