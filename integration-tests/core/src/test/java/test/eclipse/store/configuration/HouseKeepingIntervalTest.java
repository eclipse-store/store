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
import java.time.Duration;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.eclipse.serializer.configuration.types.ByteSize;
import org.eclipse.serializer.configuration.types.ByteUnit;
import org.eclipse.store.storage.embedded.configuration.types.EmbeddedStorageConfiguration;
import org.eclipse.store.storage.embedded.configuration.types.EmbeddedStorageConfigurationBuilder;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HouseKeepingIntervalTest
{

    @TempDir
    Path location;

    @Test
    void houseKeepingIntervalTest() throws InterruptedException
    {
        Customer customer = CustomerGenerator.generateNewCustomer();

        final Path deleteLocation = this.location.resolve("deleted");
        deleteLocation.toFile().mkdir();

        final EmbeddedStorageManager storageManager = EmbeddedStorageConfigurationBuilder.New()
                .setStorageDirectory(this.location.toString())
                .setDataFileMaximumSize(ByteSize.New(2, ByteUnit.KiB))
                .setDataFileMinimumSize(ByteSize.New(1, ByteUnit.KiB))
                .setDeletionDirectory(deleteLocation.toString())
                .setHousekeepingInterval(Duration.ofMillis(10))
                .createEmbeddedStorageFoundation()
                .createEmbeddedStorageManager(customer)
                .start();

        for (int i = 0; i < 50; i++) {
            customer = CustomerGenerator.generateNewCustomer();
            storageManager.store(customer);
        }

        Thread.sleep(200);

        final List<File> files = (List<File>) FileUtils.listFiles(this.location.resolve("deleted").toFile(), null, true);
        assertTrue(files.size() > 0, files.toString());

        storageManager.shutdown();

    }

    @Test
    void houseKeepingIntervalXMLTest() throws IOException, InterruptedException
    {
        Customer customer = CustomerGenerator.generateNewCustomer();

        final Path configFilePath = this.location.resolve("deleteDirectory.xml");
        final Path deleteLocation = this.location.resolve("deleted");
        deleteLocation.toFile().mkdir();

        final StringBuilder builder = new StringBuilder();
        builder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        builder.append("<properties>\n");
        builder.append("\t<property name=\"deletion-directory\" value=\"" + deleteLocation.toString().replace("\\", "//") + "\"/>\n");
        builder.append("\t<property name=\"data-file-minimum-size\" value=\"1KiB\"/>\n");
        builder.append("\t<property name=\"data-file-maximum-size\" value=\"2KiB\"/>\n");
        builder.append("\t<property name=\"housekeeping-interval\" value=\"10ms\"/>\n");
        builder.append("</properties>");

        FileUtils.writeStringToFile(configFilePath.toFile(), builder.toString(), "UTF-8");

        final EmbeddedStorageConfigurationBuilder configuration =
                EmbeddedStorageConfiguration.load(configFilePath.toString());

        final EmbeddedStorageManager storageManager = configuration
                .setStorageDirectory(this.location.toString())
                .createEmbeddedStorageFoundation()
                .createEmbeddedStorageManager(customer)
                .start();

        for (int i = 0; i < 50; i++) {
            customer = CustomerGenerator.generateNewCustomer();
            storageManager.store(customer);
        }


        final List<File> files = (List<File>) FileUtils.listFiles(this.location.resolve("deleted").toFile(), null, true);
        assertTrue(files.size() > 0, files.toString());

        storageManager.shutdown();

    }


}
