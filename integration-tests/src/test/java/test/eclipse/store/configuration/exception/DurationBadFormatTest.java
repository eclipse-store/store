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

import java.io.IOException;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;
import org.eclipse.serializer.configuration.exceptions.ConfigurationException;
import org.eclipse.store.storage.embedded.configuration.types.EmbeddedStorageConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class DurationBadFormatTest {

    @TempDir
    Path location;

    @Test
    public void badFormatWithUnit() throws IOException {

        final Path configFilePath = this.location.resolve("deleteDirectory.xml");
        final Path deleteLocation = this.location.resolve("deleted");

        final StringBuilder builder = new StringBuilder();
        builder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        builder.append("<properties>\n");
        builder.append("\t<property name=\"deletion-directory\" value=\"" + deleteLocation.toString().replace("\\", "//") + "\"/>\n");
        builder.append("\t<property name=\"data-file-minimum-size\" value=\"1KiB\"/>\n");
        builder.append("\t<property name=\"data-file-maximum-size\" value=\"2KiB\"/>\n");
        builder.append("\t<property name=\"housekeeping-interval\" value=\"10aams\"/>\n");
        builder.append("</properties>");

        FileUtils.writeStringToFile(configFilePath.toFile(), builder.toString(), "UTF-8");

        assertThrows(ConfigurationException.class, () -> {
        	EmbeddedStorageConfiguration.load(configFilePath.toString())
        		.createEmbeddedStorageFoundation();
        });
    }

    @Test
    public void badFormatWithoutUnit() throws IOException {

        final Path configFilePath = this.location.resolve("deleteDirectory.xml");
        final Path deleteLocation = this.location.resolve("deleted");

        final StringBuilder builder = new StringBuilder();
        builder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        builder.append("<properties>\n");
        builder.append("\t<property name=\"deletion-directory\" value=\"" + deleteLocation.toString().replace("\\", "//") + "\"/>\n");
        builder.append("\t<property name=\"data-file-minimum-size\" value=\"1KiB\"/>\n");
        builder.append("\t<property name=\"data-file-maximum-size\" value=\"2KiB\"/>\n");
        builder.append("\t<property name=\"housekeeping-interval\" value=\"10aa5\"/>\n");
        builder.append("</properties>");


        FileUtils.writeStringToFile(configFilePath.toFile(), builder.toString(), "UTF-8");

		assertThrows(ConfigurationException.class, () -> {
			EmbeddedStorageConfiguration.load(configFilePath.toString())
				.createEmbeddedStorageFoundation();
		});
    }
}
