package test.microstream.afs.redis;

/*-
 * #%L
 * EclipseStore Integration Tests - AFS - redis
 * %%
 * Copyright (C) 2023 - 2026 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

public class Container {

    public static GenericContainer container;

    static {
        container = new GenericContainer(DockerImageName.parse("redis"))
                .withExposedPorts(6379);
        container.start();
    }

    public static String redisUrl() {
        return "redis://" + container.getHost() + ":" + container.getFirstMappedPort() + "/0";
    }
}
