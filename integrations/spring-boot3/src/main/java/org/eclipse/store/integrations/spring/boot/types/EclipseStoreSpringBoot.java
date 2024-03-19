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


import org.eclipse.serializer.reflect.ClassLoaderProvider;
import org.eclipse.store.integrations.spring.boot.types.converter.EclipseStoreConfigConverter;
import org.eclipse.store.integrations.spring.boot.types.factories.EmbeddedStorageFoundationFactory;
import org.eclipse.store.integrations.spring.boot.types.factories.EmbeddedStorageManagerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * The {@code EclipseStoreSpringBoot} class is responsible for the auto-configuration of the Spring Boot application.
 * It sets up the configuration properties for the Eclipse Store and initiates a scan of the base packages for any necessary Spring components.
 */

@Configuration
public class EclipseStoreSpringBoot
{
    /**
     * Properties converter.
     *
     * @return properties converter.
     */
    @Bean
    @ConditionalOnMissingBean
    public EclipseStoreConfigConverter eclipseStoreConfigConverter()
    {
        return new EclipseStoreConfigConverter();
    }

    /**
     * Class loader provider based on Spring class loader.
     *
     * @param applicationContext application context.
     * @return class loader provider.
     */
    @Bean
    @Lazy
    public ClassLoaderProvider classLoaderProvider(ApplicationContext applicationContext)
    {
        return (typeName) -> applicationContext.getClassLoader();
    }

    /**
     * Creates embedded storage foundation factory.
     *
     * @param eclipseStoreConfigConverter converter to map Spring properties to EclipseStore foundation configuration.
     * @param classLoaderProvider         classloader to use in foundation.
     * @return factory.
     */
    @Bean
    public EmbeddedStorageFoundationFactory embeddedStorageFoundationFactory(
            EclipseStoreConfigConverter eclipseStoreConfigConverter,
            ClassLoaderProvider classLoaderProvider
    )
    {
        return new EmbeddedStorageFoundationFactory(eclipseStoreConfigConverter, classLoaderProvider);
    }

    /**
     * Creates a new embedded storage manager factory.
     *
     * @return factory.
     */
    @Bean
    public EmbeddedStorageManagerFactory embeddedStorageManagerFactory()
    {
        return new EmbeddedStorageManagerFactory();
    }
}
