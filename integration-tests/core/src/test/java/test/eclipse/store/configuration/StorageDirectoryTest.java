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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.eclipse.serializer.afs.types.ADirectory;
import org.eclipse.serializer.io.XIO;
import org.eclipse.store.storage.embedded.configuration.types.EmbeddedStorageConfiguration;
import org.eclipse.store.storage.embedded.configuration.types.EmbeddedStorageConfigurationBuilder;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StorageDirectoryTest
{

    @TempDir
    Path location;

    Path configFilePath;


    @Test
    void storageDirectoryTest() throws IOException
    {
        this.configFilePath = this.location.resolve("storage-directory.ini");
        FileUtils.writeStringToFile(this.configFilePath.toFile(), "storage-directory = " + this.location.toString(), "UTF-8");

        final Customer customer = CustomerGenerator.generateNewCustomer();

        final EmbeddedStorageConfigurationBuilder configuration =
                EmbeddedStorageConfiguration.load(this.configFilePath.toString());

        final EmbeddedStorageManager storageManager = configuration.createEmbeddedStorageFoundation().createEmbeddedStorageManager(customer).start();

        final List<File> files = (List<File>) FileUtils.listFiles(this.location.toFile(), null, true);

        assertTrue(files.size() > 2, files.toString());

        storageManager.shutdown();
    }

    @Test
    void storageDirectoryXMLTest() throws IOException
    {

        this.configFilePath = this.location.resolve("storage-directory.xml");

        final String xmlConfig =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<properties>\n" +
                        "\t<property name=\"storage-directory\" value=\"" + this.location.toString() + "\"/>\n" +
                        "</properties>";

        FileUtils.writeStringToFile(this.configFilePath.toFile(), xmlConfig, "UTF-8");

        final Customer customer = CustomerGenerator.generateNewCustomer();

        final EmbeddedStorageConfigurationBuilder configuration =
                EmbeddedStorageConfiguration.load(this.configFilePath.toString());

        final EmbeddedStorageManager storageManager = configuration.createEmbeddedStorageFoundation().createEmbeddedStorageManager(customer).start();

        final List<File> files = (List<File>) FileUtils.listFiles(this.location.toFile(), null, true);

        assertTrue(files.size() > 2);

        storageManager.shutdown();
    }


    @Test
    void storageDirectoryHomeTest() throws IOException
    {
        this.configFilePath = this.location.resolve("baseDirectory.xml");

        final String xmlConfig =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<properties>\n" +
                        "\t<property name=\"storage-directory\" value=\"~/db\"/>\n" +
                        "</properties>";

        FileUtils.writeStringToFile(this.configFilePath.toFile(), xmlConfig, "UTF-8");

        final ADirectory baseDirectory = EmbeddedStorageConfiguration.load(this.configFilePath.toString())
                .createEmbeddedStorageFoundation()
                .getConfiguration()
                .fileProvider()
                .baseDirectory();

        final Path basePath = XIO.Path(baseDirectory.toPath());
        final Path userHomePath = Paths.get(System.getProperty("user.home"));
        assertTrue(basePath.startsWith(userHomePath));
    }
}
