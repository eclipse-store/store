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
import org.eclipse.store.storage.types.StorageManager;
import org.jboss.weld.junit5.EnableWeld;
import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldSetup;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@EnableWeld
class StorageManagerProducerFromConfigPropertyInjectionTest
{
    // Test the StorageManagerProducer
    // - Use the StorageManager from MicroProfile Config Converter (which uses external files like ini or xml)

    @WeldSetup
    public WeldInitiator weld = WeldInitiator.of(StorageManagerProducer.class, StorageManagerProducerFromConfigPropertyInjectionTest.class);

    @Inject
    private StorageManagerProducer storageManagerProducer;

    @BeforeEach
    public void setup()
    {
        TestAppender.events.clear();
    }

    private static Config configMock;

    @Produces
    Config produceConfigMock()
    {
        configMock = Mockito.mock(Config.class);
        return configMock;
    }


    private static StorageExtension storageExtensionMock;

    @Produces
    StorageExtension produceStorageExtension()
    {
        storageExtensionMock = Mockito.mock(StorageExtension.class);
        // This means we do have a @Inject @ConfigProperty StorageManager construct
        // And thus should 'take' StorageManger from MicroProfileConfig directly.
        final Set<String> names = Set.of("org.eclipse.store.ini");
        Mockito.when(storageExtensionMock.getStorageManagerConfigInjectionNames())
                .thenReturn(names);

        return storageExtensionMock;
    }

    @Test
    void getStoreManager_fromConfigPropertyInjection()
    {

        StorageManager storageManager = storageManagerProducer.getStorageManager();
        Assertions.assertNull(storageManager);  // Since we did not mock ConfigMock.getValue()

        //
        Mockito.verify(configMock)
                .getValue("org.eclipse.store.ini", StorageManager.class);  // Test if StorageManager from Config taken
        Mockito.verifyNoMoreInteractions(configMock);

        // Another test to prove we did not create a real Storage Manager
        List<ILoggingEvent> messages = TestAppender.getMessagesOfLevel(Level.INFO);
        Optional<ILoggingEvent> loggingEvent = messages.stream()
                .filter(le -> le.getMessage()
                        .equals("Embedded storage manager initialized"))
                .findAny();

        Assertions.assertFalse(loggingEvent.isPresent());

    }
}
