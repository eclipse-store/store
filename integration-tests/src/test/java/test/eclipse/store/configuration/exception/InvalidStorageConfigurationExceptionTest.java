package test.eclipse.store.configuration.exception;

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

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;
import org.eclipse.serializer.configuration.exceptions.ConfigurationException;
import org.eclipse.store.storage.embedded.configuration.types.EmbeddedStorageConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InvalidStorageConfigurationExceptionTest
{

    @TempDir
    Path location;

    @Test
    void invalidStorageConfigurationException() throws IOException, InterruptedException
    {

        final Path configFilePath = this.location.resolve("deleteDirectory.xml");
        final Path deleteLocation = this.location.resolve("deleted");

        final StringBuilder builder = new StringBuilder();
        builder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        builder.append("<properties>\n");
        builder.append("\t<property name=\"deletion-directory\" value=\"" + deleteLocation.toString().replace("\\", "//") + "\"/>\n");
        builder.append("\t<property name=\"data-file-minimum-size\" value=\"1asdfd\"/>\n");
        builder.append("\t<property name=\"data-file-maximum-size\" value=\"2KiB\"/>\n");
        builder.append("\t<property name=\"houseleeping-interval\" value=\"10ms\"/>\n");
        builder.append("</properties>");

        FileUtils.writeStringToFile(configFilePath.toFile(), builder.toString(), "UTF-8");

        assertThrows(ConfigurationException.class, () -> {
            EmbeddedStorageConfiguration.load(configFilePath.toString())
                    .createEmbeddedStorageFoundation();
        });

    }

    @Test
    void maxStorageSizeExceedException() throws IOException, InterruptedException
    {

        final Path configFilePath = this.location.resolve("deleteDirectory.xml");
        final Path deleteLocation = this.location.resolve("deleted");

        final StringBuilder builder = new StringBuilder();
        builder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        builder.append("<properties>\n");
        builder.append("\t<property name=\"deletion-directory\" value=\"" + deleteLocation.toString().replace("\\", "//") + "\"/>\n");
        builder.append("\t<property name=\"data-file-minimum-size\" value=\"2147483648\"/>\n");
        builder.append("\t<property name=\"data-file-maximum-size\" value=\"2KiB\"/>\n");
        builder.append("\t<property name=\"housekeeping-interval\" value=\"10ms\"/>\n");
        builder.append("</properties>");

        FileUtils.writeStringToFile(configFilePath.toFile(), builder.toString(), "UTF-8");

        assertThrows(ConfigurationException.class, () -> {
            EmbeddedStorageConfiguration.load(configFilePath.toString())
                    .createEmbeddedStorageFoundation();
        });
    }

    @Test
    void channelCountBadNumberTest() throws IOException
    {
        final Path configFilePath = this.location.resolve("channelCount.xml");

        final String xmlConfig =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<properties>\n" +
                        "\t<property name=\"channel-count\" value=\"5x\"/>\n" +
                        "</properties>";

        FileUtils.writeStringToFile(configFilePath.toFile(), xmlConfig, "UTF-8");

        assertThrows(ConfigurationException.class, () -> {
            EmbeddedStorageConfiguration.load(configFilePath.toString())
                    .createEmbeddedStorageFoundation();
        });

    }


}
