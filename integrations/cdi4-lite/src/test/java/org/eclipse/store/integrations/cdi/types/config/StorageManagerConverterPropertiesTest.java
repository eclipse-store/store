package org.eclipse.store.integrations.cdi.types.config;

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



import io.smallrye.config.inject.ConfigExtension;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.store.integrations.cdi.types.logging.TestLogger;
import org.eclipse.store.storage.types.StorageManager;
import org.jboss.weld.junit5.auto.AddExtensions;
import org.jboss.weld.junit5.auto.EnableAutoWeld;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;
import org.slf4j.event.LoggingEvent;

import jakarta.inject.Inject;
import java.io.File;
import java.util.List;


@EnableAutoWeld
@AddExtensions(ConfigExtension.class)  // SmallRye Config extension to Support MicroProfile Config within this test
@DisplayName("Check if the Storage Manager will load using a property file")
public class StorageManagerConverterPropertiesTest extends AbstractStorageManagerConverterTest
{
	@Inject
	@ConfigProperty(name = "org.eclipse.store.properties")
	private StorageManager manager;

	@Test
	public void shouldLoadFromProperties()
	{
		Assertions.assertNotNull(this.manager);
		Assertions.assertTrue(this.manager instanceof StorageManagerProxy);
		final boolean active = this.manager.isActive();// We have the proxy, need to start it to see the log messages.
		// Don't use this.manager.isActive() as it is started already by the proxy.
		Assertions.assertTrue(active);

		final List<LoggingEvent> messages = TestLogger.getMessagesOfLevel(Level.INFO);

		hasMessage(messages, "Loading configuration to start the class StorageManager from the key: storage.properties");
		hasMessage(messages, "Embedded storage manager initialized");

		this.directoryHasChannels(new File("target/prop"), 2);

		// No database-name defined in file so it takes the filename (value of "org.eclipse.store.properties" MP key)
		Assertions.assertEquals("storage.properties", this.manager.databaseName());
	}
}
