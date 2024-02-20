
package org.eclipse.store.integrations.cdi.types;

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


import io.smallrye.config.inject.ConfigExtension;
import org.eclipse.microprofile.config.Config;
import org.eclipse.store.integrations.cdi.ConfigurationCoreProperties;
import org.eclipse.store.integrations.cdi.types.logging.TestAppender;
import org.eclipse.store.storage.embedded.configuration.types.EmbeddedStorageConfigurationPropertyNames;
import org.jboss.weld.junit5.auto.AddExtensions;
import org.jboss.weld.junit5.auto.EnableAutoWeld;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@EnableAutoWeld  // So that Weld container is started
@AddExtensions(ConfigExtension.class)  // SmallRye Config extension to Support MicroProfile Config within this test
class ConfigurationCorePropertiesTest
{
	// Testing the ConfigurationCoreProperties functionality.
	// - convert the MicroProfile config key/values to Map entries as EclipseStore config values.

	@Inject
	private Config config;

	@BeforeEach
	public void setup()
	{
		TestAppender.events.clear();
	}

	private static final String PREFIX = "org.eclipse.store.";

	@Test
	void shouldLoadFromPropertiesFile()
	{
		final Map<String, String> properties = ConfigurationCoreProperties.getProperties(this.config);
		Assertions.assertNotNull(properties);
		Assertions.assertEquals(4, properties.keySet()
				.size(),properties.keySet().toString());
		Assertions.assertEquals(Set.of("xml", "ini", "properties", "storage-directory"), properties.keySet());
	}

	@Test
	void shouldLoadPropertiesFromConfiguration()
	{
		String microProfileKey = ConfigurationCoreProperties.STORAGE_DIRECTORY.getMicroProfile();
		try
		{
			System.setProperty(microProfileKey, "target/");
			final Map<String, String> properties = ConfigurationCoreProperties.getProperties(this.config);
			final String storageDirectory = properties.get(ConfigurationCoreProperties.STORAGE_DIRECTORY.getEclipseStore(microProfileKey));
			Assertions.assertNotNull(storageDirectory);
			Assertions.assertEquals("target/", storageDirectory);
		} finally
		{
			System.clearProperty(microProfileKey);
		}
	}
	
	@Test
	public void shouldAddCustomConfiguration()
	{

		final String customProperty = "custom.test";
		final String key = ConfigurationCoreProperties.Constants.PREFIX + customProperty;
		try
		{
			System.setProperty(key, "random_value");
			final Map<String, String> properties = ConfigurationCoreProperties.getProperties(this.config);
			final String value = properties.get(customProperty);
			Assertions.assertEquals("random_value", value);
		} finally
		{
			System.clearProperty(key);
		}
	}

	@Test
	void shouldMapStorageFileSystem()
	{
		final String keyMicroProfile = PREFIX + "storage.filesystem.sql.postgres.data-source-provider";
		final String keyMicroStream = "storage-filesystem.sql.postgres.data-source-provider";
		try
		{
			System.setProperty(keyMicroProfile, "some_value");

			final Map<String, String> properties = ConfigurationCoreProperties.getProperties(this.config);
			final String value = properties.get(keyMicroStream);
			Assertions.assertEquals("some_value", value);

		} finally
		{
			System.clearProperty(keyMicroProfile);
		}

	}

	@Test
	void shouldSupportMicroStreamKeys()
	{
		final String keyMicroProfile = PREFIX +"storage-directory";
		try
		{
			System.setProperty(keyMicroProfile, "/storage");
			final Map<String, String> properties = ConfigurationCoreProperties.getProperties(this.config);
			final String value = properties.get(EmbeddedStorageConfigurationPropertyNames.STORAGE_DIRECTORY);
			Assertions.assertEquals("/storage", value, value);

		} finally
		{
			System.clearProperty(keyMicroProfile);
		}

	}

	@Test
	void shouldMapStorageFileSystem_directly()
	{
		// Without CDI version, testing ConfigurationCoreProperties 'directly'

		final String keyMicroProfile = PREFIX + "storage.filesystem.sql.postgres.data-source-provider";
		final String keyMicroStream = "storage-filesystem.sql.postgres.data-source-provider";

		Optional<ConfigurationCoreProperties> property = ConfigurationCoreProperties.get(keyMicroProfile);
		Assertions.assertTrue(property.isPresent());

		String convertedKey = property.get().getEclipseStore(keyMicroProfile);
		Assertions.assertEquals(keyMicroStream, convertedKey);
	}

	@Test
	void shouldMapExactMatches()
	{
		// Not using CDI

		final String keyMicroProfile = "org.eclipse.store.storage.directory";
		final String keyMicroStream = "storage-directory";

		Optional<ConfigurationCoreProperties> property = ConfigurationCoreProperties.get(keyMicroProfile);
		Assertions.assertTrue(property.isPresent());

		String convertedKey = property.get().getEclipseStore(keyMicroProfile);
		Assertions.assertEquals(keyMicroStream, convertedKey);
	}

	@Test
	void findEnumValue()
	{
		// Not using CDI
		String key = PREFIX + "storage.directory";
		final Optional<ConfigurationCoreProperties> property = ConfigurationCoreProperties.get(key);
		Assertions.assertTrue(property.isPresent());
		Assertions.assertEquals(ConfigurationCoreProperties.STORAGE_DIRECTORY,  property.get());
	}

	@Test
	void findEnumValue_partial()
	{
		// Not using CDI
		String key = PREFIX + "storage.filesystem.sql.postgres.data-source-provider";
		final Optional<ConfigurationCoreProperties> property = ConfigurationCoreProperties.get(key);
		Assertions.assertTrue(property.isPresent());
		Assertions.assertEquals(ConfigurationCoreProperties.STORAGE_FILESYSTEM,  property.get());
		Assertions.assertEquals("storage-filesystem.sql.postgres.data-source-provider", property.get().getEclipseStore(key));
	}

	@Test
	void findEnumValue_NotCaseSensitive()
	{
		// Not using CDI
		String key = PREFIX + "Storage.Directory";
		final Optional<ConfigurationCoreProperties> property = ConfigurationCoreProperties.get(key);
		Assertions.assertTrue(property.isEmpty());
	}
}
