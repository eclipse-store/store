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
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.eclipse.store.storage.embedded.configuration.types.EmbeddedStorageConfiguration;
import org.eclipse.store.storage.embedded.configuration.types.EmbeddedStorageConfigurationBuilder;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TypeDictionaryFilenameTest
{

    @TempDir
    Path location;

    @Test
    void transactionFileSuffixTest() throws IOException
    {

        final Customer customer = CustomerGenerator.generateNewCustomer();

        final EmbeddedStorageConfigurationBuilder configuration = EmbeddedStorageConfiguration.load(
                "configuration/typeDictionaryFilename.ini"
        );

        final EmbeddedStorageManager storageManager = configuration.setStorageDirectory(this.location.toString())
                .createEmbeddedStorageFoundation().createEmbeddedStorageManager(customer).start();

        final List<File> files = (List<File>) FileUtils.listFiles(this.location.toFile(), null, true);

        final StringBuilder builder = new StringBuilder();
        for (final File file : files) {
            builder.append(file.getCanonicalPath());
        }
        assertTrue(builder.toString().contains("somesupertypeDictionaryFilename.some_suffix"));

        storageManager.shutdown();

    }

    @Test
    void transactionFileSuffixXmlTest() throws IOException
    {

        final Customer customer = CustomerGenerator.generateNewCustomer();

        final EmbeddedStorageConfigurationBuilder configuration = EmbeddedStorageConfiguration.load(
                "configuration/typeDictionaryFilename.xml"
        );

        final EmbeddedStorageManager storageManager = configuration.setStorageDirectory(this.location.toString())
                .createEmbeddedStorageFoundation().createEmbeddedStorageManager(customer).start();

        final List<File> files = (List<File>) FileUtils.listFiles(this.location.toFile(), null, true);

        final StringBuilder builder = new StringBuilder();
        for (final File file : files) {
            builder.append(file.getCanonicalPath());
        }
        assertTrue(builder.toString().contains("somesupertypeDictionaryFilename.some_suffix"));

        storageManager.shutdown();

    }


}
