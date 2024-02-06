package org.microstream.spring.boot.example;

/*-
 * #%L
 * spring-boot3-simple
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

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The {@code EclipseStoreProperties} class holds the configuration properties for the Eclipse Store.
 * These properties are loaded from the application's configuration files and can be used to configure the Eclipse Store.
 *
 * <p>This class is annotated with {@code @Configuration}, {@code @Primary}, and {@code @ConfigurationProperties},
 * which means it is a Spring configuration class, it is the primary bean of its type,
 * and its properties are bound to the "org.eclipse.store" prefix in the configuration files.</p>
 *
 * <p>Each property in this class corresponds to a configuration option for the Eclipse Store.
 * The properties are loaded from the configuration files when the application starts.</p>
 */
@SpringBootApplication
public class JokesApplication
{
    public static void main(String... args)
    {
        SpringApplication.run(JokesApplication.class, args);
    }
}
