package org.eclipse.store.integrations.spring.boot.types.storages;

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

import org.eclipse.store.integrations.spring.boot.types.EclipseStoreProvider;
import org.eclipse.store.integrations.spring.boot.types.configuration.EclipseStoreProperties;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;

@TestConfiguration
public class TwoBeanConfiguration
{

    @Autowired
    private EclipseStoreProvider provider;

    @Bean("first_config")
    @ConfigurationProperties("org.eclipse.store.first")
    EclipseStoreProperties firstStoreProperties()
    {
        return new EclipseStoreProperties();
    }

    @Bean("second_config")
    @ConfigurationProperties("org.eclipse.store.second")
    EclipseStoreProperties secondStoreProperties()
    {
        return new EclipseStoreProperties();
    }

    @Bean
    @Lazy
    @Qualifier("first_storage")
    EmbeddedStorageManager createFirstStorage(@Qualifier("first_config") EclipseStoreProperties firstStoreProperties)
    {
        return provider.createStorage(firstStoreProperties);
    }

    @Bean
    @Lazy
    @Qualifier("second_storage")
    EmbeddedStorageManager createSecondStorage(@Qualifier("second_config") EclipseStoreProperties secondStoreProperties)
    {
        return provider.createStorage(secondStoreProperties);
    }


}
