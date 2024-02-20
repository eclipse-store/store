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
import jakarta.inject.Inject;

import io.smallrye.config.inject.ConfigExtension;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.store.integrations.cdi.types.logging.TestAppender;
import org.eclipse.store.storage.types.StorageManager;
import org.jboss.weld.junit5.auto.AddExtensions;
import org.jboss.weld.junit5.auto.EnableAutoWeld;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;


import java.io.File;
import java.util.List;


@EnableAutoWeld
@AddExtensions(ConfigExtension.class)  // SmallRye Config extension to Support MicroProfile Config within this test
@DisplayName("Check if the Storage Manager will load using a XML file")
public class StorageManagerConverterXMLTest extends AbstractStorageManagerConverterTest
{
	@Inject
	@ConfigProperty(name = "org.eclipse.store.xml")
	private StorageManager manager;
	
	@Test
	public void shouldLoadFromXML()
	{
		Assertions.assertNotNull(this.manager);
		Assertions.assertTrue(this.manager instanceof StorageManagerProxy);
		final boolean active = this.manager.isActive();// We have the proxy, need to start it to see the log messages.
		// Don't use this.manager.isActive() as it is started already by the proxy.
		Assertions.assertTrue(active);

		final List<ILoggingEvent> messages = TestAppender.getMessagesOfLevel(Level.INFO);

		hasMessage(messages, "Loading configuration to start the class StorageManager from the key: storage.xml");
		hasMessage(messages, "Embedded storage manager initialized");

		this.directoryHasChannels(new File("target/xml"), 4);
	}
}
