
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


import java.io.File;
import java.util.List;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import io.smallrye.config.inject.ConfigExtension;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.store.integrations.cdi.types.logging.TestAppender;
import org.eclipse.store.storage.types.StorageManager;
import org.jboss.weld.junit5.auto.AddExtensions;
import org.jboss.weld.junit5.auto.EnableAutoWeld;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;


@EnableAutoWeld
@AddExtensions(ConfigExtension.class)  // SmallRye Config extension to Support MicroProfile Config within this test
@DisplayName("Check if the Storage Manager will load using a INI file")
public class StorageManagerConverterINITest extends AbstractStorageManagerConverterTest
{
    @Inject
    @ConfigProperty(name = "org.eclipse.store.ini")
    private StorageManager manager;

    @ApplicationScoped
    @Produces
    // To verify if the 'manager' above comes from the StorageManagerConverter and not just injection
    // of the mock.  The creation of the channel directories is also an indication.
    private StorageManager storageManagerMock = Mockito.mock(StorageManager.class);

    @Test
    public void shouldLoadFromIni()
    {
        Assertions.assertNotNull(this.manager);
        Assertions.assertTrue(this.manager instanceof StorageManagerProxy);
        final boolean active = this.manager.isActive();// We have the proxy, need to start it to see the log messages.
        // Don't use this.manager.isActive() as it is started already by the proxy.
        Assertions.assertTrue(active);

        final List<ILoggingEvent> messages = TestAppender.getMessagesOfLevel(Level.INFO);

        hasMessage(messages, "Loading configuration to start the class StorageManager from the key: storage.ini");
        hasMessage(messages, "Embedded storage manager initialized");

        this.directoryHasChannels(new File("target/ini"), 1);

        Assertions.assertEquals("ini-based", this.manager.databaseName());
    }
}
