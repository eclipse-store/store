package org.eclipse.store.integrations.spring.boot.types;

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


import org.eclipse.store.integrations.spring.boot.types.configuration.EclipseStoreProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;

/**
 * The {@code EclipseStoreSpringBoot} class is responsible for the auto-configuration of the Spring Boot application.
 * It sets up the configuration properties for the Eclipse Store and initiates a scan of the base packages for any necessary Spring components.
 *
 * <p>Here's an example of how to use this class in a Spring Boot application:</p>
 * <pre>
 * <code>
 *
 * {@literal @}SpringBootApplication
 * {@literal @}Import(EclipseStoreSpringBoot.class)
 *  public class SomeSpringApplication {
 *     public static void main(String... args) {
 *         SpringApplication.run(SomeSpringApplication.class, args);
 *     }
 *  }
 * </code>
 * </pre>
 */

@AutoConfiguration
@ComponentScan(basePackages = "org.eclipse.store.integrations.spring.boot.types")
@EnableConfigurationProperties(EclipseStoreProperties.class)
public class EclipseStoreSpringBoot
{
}
