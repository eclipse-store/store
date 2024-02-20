package org.eclipse.store.integrations.cdi.types.cache;

/*-
 * #%L
 * Eclipse Store Integrations CDI 4 - lite
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


import java.util.Optional;

import io.smallrye.config.inject.ConfigExtension;
import jakarta.inject.Inject;
import javax.cache.Cache;
import org.eclipse.store.integrations.cdi.ConfigurationCoreProperties;
import org.eclipse.store.integrations.cdi.types.config.StorageManagerProducer;
import org.eclipse.store.integrations.cdi.types.extension.StorageExtension;
import org.eclipse.store.integrations.cdi.types.logging.TestAppender;
import org.eclipse.store.storage.types.Database;
import org.eclipse.store.storage.types.Databases;
import org.eclipse.store.storage.types.StorageManager;
import org.jboss.weld.junit5.auto.AddBeanClasses;
import org.jboss.weld.junit5.auto.AddExtensions;
import org.jboss.weld.junit5.auto.EnableAutoWeld;
import org.junit.jupiter.api.*;

@EnableAutoWeld  // So that Weld container is started
@AddBeanClasses({StorageCacheProducer.class, StorageManagerProducer.class})  // For @StorageCache
@AddExtensions({StorageExtension.class, ConfigExtension.class})
// SmallRye Config extension And Eclipse Store extension for StorageManager
public class StorageCacheTest
{

    @Inject
    @StorageCache("storage")
    private Cache<Integer, String> cache;

    @BeforeAll
    public static void beforeAll()
    {
        System.setProperty(CacheProperties.STORAGE.get(), Boolean.TRUE.toString());
        System.setProperty(ConfigurationCoreProperties.STORAGE_DIRECTORY.getMicroProfile(), "target/cache");
        TestAppender.events.clear();
    }

    @AfterAll
    public static void afterAll()
    {
        System.clearProperty(CacheProperties.STORAGE.get());
        System.clearProperty(ConfigurationCoreProperties.STORAGE_DIRECTORY.getMicroProfile());
    }

    @AfterEach
    public void cleanup()
    {
        // The @Disposes (calling StorageManager.shutdown) is not picked up by Weld-Unit,
        // Need to shut down it here.
        Databases databases = Databases.get();
        Database generic = databases.get("Generic");
        Optional.ofNullable(generic.storage()).ifPresent(StorageManager::shutdown);
    }

    @Test
    public void shouldCacheValues()
    {
        Assertions.assertNotNull(cache);
        this.cache.put(1, "one");
        Assertions.assertNotNull(cache.get(1));
    }

}
