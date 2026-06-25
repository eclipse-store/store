package test.eclipse.store.configuration;

/*-
 * #%L
 * EclipseStore Integration Tests
 * %%
 * Copyright (C) 2023 - 2026 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.eclipse.serializer.configuration.hocon.types.ConfigurationParserHocon;
import org.eclipse.serializer.configuration.types.ConfigurationLoader;
import org.eclipse.serializer.configuration.yaml.types.ConfigurationParserYaml;
import org.eclipse.store.storage.embedded.configuration.types.EmbeddedStorageConfiguration;
import org.eclipse.store.storage.embedded.configuration.types.EmbeddedStorageConfigurationBuilder;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;


import static org.junit.jupiter.api.Assertions.assertTrue;

class ChannelDirectoryPrefixTest {

    @TempDir
    Path location;

    @Test
    void channelDirectoryPrefixTest() throws IOException {

        final Customer customer = CustomerGenerator.generateNewCustomer();
        
        final EmbeddedStorageConfigurationBuilder configuration = EmbeddedStorageConfiguration.load(
        	"configuration/channelDirectoryPrefix.ini"
        );

        final EmbeddedStorageManager storageManager = configuration.setStorageDirectory(this.location.toString())
        	.createEmbeddedStorageFoundation().createEmbeddedStorageManager(customer).start();

        final List<File> files = (List<File>) FileUtils.listFiles(this.location.toFile(), null, true);

        final StringBuilder builder = new StringBuilder();
        for (final File file : files) {
            builder.append(file.getCanonicalPath());
        }
        assertTrue(builder.toString().contains("channelXX_"));

        storageManager.shutdown();

    }

    @Test
    void channelDirectoryPrefixYmlTest() throws IOException {

        final Customer customer = CustomerGenerator.generateNewCustomer();

        final EmbeddedStorageConfigurationBuilder configuration = EmbeddedStorageConfiguration.load(
                ConfigurationLoader.New("configuration/channelDirectoryPrefix.yml"),
                ConfigurationParserYaml.New()
        );

        final EmbeddedStorageManager storageManager = configuration.setStorageDirectory(this.location.toString())
                .createEmbeddedStorageFoundation().createEmbeddedStorageManager(customer).start();

        final List<File> files = (List<File>) FileUtils.listFiles(this.location.toFile(), null, true);

        final StringBuilder builder = new StringBuilder();
        for (final File file : files) {
            builder.append(file.getCanonicalPath());
        }
        assertTrue(builder.toString().contains("channelXX_"));

        storageManager.shutdown();

    }

    @Test
    void channelDirectoryPrefixHoconTest() throws IOException {

        final Customer customer = CustomerGenerator.generateNewCustomer();

        final EmbeddedStorageConfigurationBuilder configuration = EmbeddedStorageConfiguration.load(
                ConfigurationLoader.New("configuration/channelDirectoryPrefix.json"),
                ConfigurationParserHocon.New()
        );

        final EmbeddedStorageManager storageManager = configuration.setStorageDirectory(this.location.toString())
                .createEmbeddedStorageFoundation().createEmbeddedStorageManager(customer).start();

        final List<File> files = (List<File>) FileUtils.listFiles(this.location.toFile(), null, true);

        final StringBuilder builder = new StringBuilder();
        for (final File file : files) {
            builder.append(file.getCanonicalPath());
        }
        assertTrue(builder.toString().contains("channelXX_"));

        storageManager.shutdown();

    }

    @Test
    void channelDirectoryPrefixXMLTest() throws IOException {

        final Customer customer = CustomerGenerator.generateNewCustomer();

        final EmbeddedStorageConfigurationBuilder configuration = EmbeddedStorageConfiguration.load(
        	"configuration/channelDirectoryPrefix.xml"
        );

        final EmbeddedStorageManager storageManager = configuration.setStorageDirectory(this.location.toString())
        	.createEmbeddedStorageFoundation().createEmbeddedStorageManager(customer).start();

        final List<File> files = (List<File>) FileUtils.listFiles(this.location.toFile(), null, true);

        final StringBuilder builder = new StringBuilder();
        for (final File file : files) {
            builder.append(file.getCanonicalPath());
        }
        assertTrue(builder.toString().contains("channelXX_"));

        storageManager.shutdown();

    }


}
