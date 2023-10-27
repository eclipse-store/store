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
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * The {@code EclipseStoreBeanFactory} class is a Spring component that provides a factory for creating {@code EmbeddedStorageManager} beans.
 * It uses the {@code EclipseStoreProvider} and {@code EclipseStoreProperties} to create the {@code EmbeddedStorageManager}.
 */
@Component
@Lazy
public class EclipseStoreBeanFactory
{
    private final EclipseStoreProvider eclipseStoreProvider;
    private final EclipseStoreProperties eclipseStoreProperties;

    /**
     * Constructs a new {@code EclipseStoreBeanFactory} with the provided {@code EclipseStoreProvider} and {@code EclipseStoreProperties}.
     *
     * @param eclipseStoreProvider The provider used to create {@code EmbeddedStorageManager} instances.
     * @param eclipseStoreProperties The properties used to configure the {@code EmbeddedStorageManager}.
     */
    public EclipseStoreBeanFactory(EclipseStoreProvider eclipseStoreProvider, EclipseStoreProperties eclipseStoreProperties)
    {
        this.eclipseStoreProvider = eclipseStoreProvider;
        this.eclipseStoreProperties = eclipseStoreProperties;
    }


    /**
     * Creates a new {@code EmbeddedStorageManager} bean using the {@code EclipseStoreProvider} and {@code EclipseStoreProperties}.
     * This bean is marked as primary and lazy, meaning it will be the default choice for autowiring and will only be initialized on demand.
     *
     * @return A new {@code EmbeddedStorageManager} instance.
     */
    @Bean
    @Primary
    @Lazy
    public EmbeddedStorageManager embeddedStorageManager()
    {
        return eclipseStoreProvider.createStorage(eclipseStoreProperties);
    }


}
