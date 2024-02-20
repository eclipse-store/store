package org.eclipse.store.integrations.cdi.types.config;

/*-
 * #%L
 * EclipseStore Integrations CDI 4
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


import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.eclipse.store.integrations.cdi.types.extension.StorageExtension;

import org.eclipse.microprofile.config.Config;
import org.eclipse.store.integrations.cdi.types.logging.TestAppender;
import org.eclipse.store.storage.types.Database;
import org.eclipse.store.storage.types.Databases;
import org.eclipse.store.storage.types.StorageManager;
import org.jboss.weld.junit5.EnableWeld;
import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldSetup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.event.LoggingEvent;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@EnableWeld
class StorageManagerProducerNoConfigPropertyTest
{
    public static final String STORAGE_DIRECTORY = "org.eclipse.store.storage-directory";
    // Test the StorageManagerProducer
    // - Support for creating a StorageManager based on configuration values (when no MicroProfile Config one used)

    @WeldSetup
    public WeldInitiator weld = WeldInitiator.of(StorageManagerProducer.class, StorageManagerProducerNoConfigPropertyTest.class);

    @Inject
    private StorageManagerProducer storageManagerProducer;

    @BeforeEach
    public void setup()
    {
        TestAppender.events.clear();
    }

    @AfterEach
    public void cleanup()
    {
        // The @Disposes (calling StorageManager.shutdown) is not picked up by Weld-Unit,
        // Need to shut down it here.
        Databases databases = Databases.get();
        Database generic = databases.get("Generic");
        Optional.ofNullable(generic.storage())
                .ifPresent(StorageManager::shutdown);
    }

    private static Config configMock;

    @Produces
    Config produceConfigMock()
    {
        configMock = Mockito.mock(Config.class);
        Mockito.when(configMock.getPropertyNames())
                .thenReturn(Arrays.asList(STORAGE_DIRECTORY));
        Mockito.when(configMock.getOptionalValue(STORAGE_DIRECTORY, String.class))
                .thenReturn(Optional.of("target/storage"));
        return configMock;
    }


    private static StorageExtension storageExtensionMock;

    @Produces
    StorageExtension produceStorageExtension()
    {
        storageExtensionMock = Mockito.mock(StorageExtension.class);
        // This means we have no @Inject @ConfigProperty StorageManager
        // And thus should create StorageManger using Builder and load all Config Property keys.
        final Set<String> names = Collections.emptySet();
        Mockito.when(storageExtensionMock.getStorageManagerConfigInjectionNames())
                .thenReturn(names);

        return storageExtensionMock;
    }

    @Test
    void getStoreManager_noConfigPropertyInjection()
    {

        StorageManager storageManager = storageManagerProducer.getStorageManager();
        Assertions.assertNotNull(storageManager);

        Mockito.verify(configMock)
                .getPropertyNames();  // Test if all config property keys are used.

        // Another test to prove a real StorageManager is created to have a look at the logs
        List<ILoggingEvent> messages = TestAppender.getMessagesOfLevel(Level.INFO);

        Optional<ILoggingEvent> loggingEvent = messages.stream()
                .filter(le -> le.getMessage()
                        .equals("Embedded storage manager initialized"))
                .findAny();

        Assertions.assertTrue(loggingEvent.isPresent());

    }
}
