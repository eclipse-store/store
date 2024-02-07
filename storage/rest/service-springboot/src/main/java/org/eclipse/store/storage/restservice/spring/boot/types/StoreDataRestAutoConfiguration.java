package org.eclipse.store.storage.restservice.spring.boot.types;

/*-
 * #%L
 * integrations-spring-boot3-console
 * %%
 * Copyright (C) 2023 - 2024 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import org.eclipse.store.integrations.spring.boot.types.EclipseStoreSpringBoot;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.restadapter.types.StorageRestAdapter;
import org.eclipse.store.storage.restservice.spring.boot.types.configuration.StoreDataRestServiceProperties;
import org.eclipse.store.storage.restservice.spring.boot.types.rest.StoreDataRestController;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for the Spring Web MVC Rest Service.
 */
@Configuration
@AutoConfigureAfter({EclipseStoreSpringBoot.class})
@EnableConfigurationProperties(StoreDataRestServiceProperties.class)
public class StoreDataRestAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public StorageRestAdapter defaultStorageRestAdapter(EmbeddedStorageManager storage) {
    return StorageRestAdapter.New(storage);
  }

  @Bean
  @ConditionalOnProperty(prefix = "org.eclipse.store.rest", name = "enabled", havingValue = "true")
  public StoreDataRestController storageDataRestController(StorageRestAdapter storageRestAdapter,
                                                           StoreDataRestServiceProperties properties) {
    return new StoreDataRestController(storageRestAdapter, properties);
  }

}
