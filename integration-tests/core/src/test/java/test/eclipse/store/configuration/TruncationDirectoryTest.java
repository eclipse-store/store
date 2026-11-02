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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.eclipse.store.storage.embedded.configuration.types.EmbeddedStorageConfiguration;
import org.eclipse.store.storage.embedded.configuration.types.EmbeddedStorageConfigurationBuilder;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TruncationDirectoryTest
{

    @TempDir
    Path location;

    Path configFilePath;

    private final String TRANSACTION_FILENAME = "transactions_1.sft";

    @Test
    void truncationDirectoryTest() throws IOException
    {
        this.configFilePath = this.location.resolve("truncationDirectory.ini");
        final Path truncationPath = this.location.resolve("trunc");
        truncationPath.toFile().mkdir();
        //FileUtils.writeStringToFile(configFilePath.toFile(),"truncationDirectory = "+ truncationPath.toString()+"\nchannelCount = 2", "UTF-8");
        FileUtils.writeStringToFile(this.configFilePath.toFile(), "channel-count = 2", "UTF-8");

        List<Customer> customers = CustomerGenerator.generateCustomers(100);

        final EmbeddedStorageConfigurationBuilder configuration =
                EmbeddedStorageConfiguration.load(this.configFilePath.toString());

        final Path backupTransaction;
        try (EmbeddedStorageManager storageManager = configuration.setStorageDirectory(this.location.toString())
                .setTruncationDirectory(truncationPath.toString()).createEmbeddedStorageFoundation().createEmbeddedStorageManager(customers).start()) {

        }
        // copy trans file
        backupTransaction = this.location.resolve("trans-backup");
        FileUtils.copyFile(this.location.resolve("channel_1/" + this.TRANSACTION_FILENAME).toFile(), backupTransaction.resolve(this.TRANSACTION_FILENAME).toFile());

        try (EmbeddedStorageManager storageManager = configuration.setStorageDirectory(this.location.toString())
                .setTruncationDirectory(truncationPath.toString()).createEmbeddedStorageFoundation().createEmbeddedStorageManager(customers).start()) {
            // add one Customer
            customers.add(CustomerGenerator.generateNewCustomer());
            storageManager.store(customers);
        }

        //change file content
        byte[] data = IOUtils.toByteArray(this.location.resolve("channel_1/channel_1_1.dat").toUri());
        data = this.removeLastBytes(data, 4);
        FileUtils.writeByteArrayToFile(this.location.resolve("channel_1/channel_1_1.dat").toFile(), data);


        // replace old transaction file
        FileUtils.copyFile(backupTransaction.resolve(this.TRANSACTION_FILENAME).toFile(), this.location.resolve("channel_1/" + this.TRANSACTION_FILENAME).toFile());

        //load again
        customers = new ArrayList<>();

        try (EmbeddedStorageManager storageManager = configuration.setStorageDirectory(this.location.toString())
                .createEmbeddedStorageFoundation().createEmbeddedStorageManager(customers).start()) {
            final List<File> files = (List<File>) FileUtils.listFiles(truncationPath.toFile(), null, true);
            assertTrue(files.size() > 1, files.toString());
        }
    }

    @Test
    void truncationDirectoryXMLTest() throws IOException, InterruptedException
    {
        this.configFilePath = this.location.resolve("truncationDirectory.xml");
        final Path truncationPath = this.location.resolve("trunc");
        truncationPath.toFile().mkdir();

        final StringBuilder builder = new StringBuilder();
        builder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        builder.append("<properties>\n");
        builder.append("\t<property name=\"truncation-directory\" value=\"" + truncationPath + "\"/>\n");
        builder.append("\t<property name=\"channel-count\" value=\"2\"/>\n");
        builder.append("</properties>");

        FileUtils.writeStringToFile(this.configFilePath.toFile(), builder.toString(), "UTF-8");

        List<Customer> customers = CustomerGenerator.generateCustomers(100);

        final EmbeddedStorageConfigurationBuilder configuration =
                EmbeddedStorageConfiguration.load(this.configFilePath.toString());

        try (EmbeddedStorageManager storageManager = configuration.setStorageDirectory(this.location.toString())
                .createEmbeddedStorageFoundation().createEmbeddedStorageManager(customers).start()) {

        }

        //printFiles(this.location.toFile());

        final Path backupTransaction;
        try (EmbeddedStorageManager storageManager = configuration.setStorageDirectory(this.location.toString())
                .createEmbeddedStorageFoundation().createEmbeddedStorageManager(customers).start()) {

            // copy trans file
            backupTransaction = this.location.resolve("trans-backup");
            FileUtils.copyFile(this.location.resolve("channel_1/" + this.TRANSACTION_FILENAME).toFile(), backupTransaction.resolve(this.TRANSACTION_FILENAME).toFile());

            // add one Customer ok
            customers.add(CustomerGenerator.generateNewCustomer());
            storageManager.store(customers);
        }
        //

        //change file content
        byte[] data = IOUtils.toByteArray(this.location.resolve("channel_1/channel_1_1.dat").toUri());
        data = this.removeLastBytes(data, 4);
        FileUtils.writeByteArrayToFile(this.location.resolve("channel_1/channel_1_1.dat").toFile(), data);

        // replace old transaction file
        FileUtils.copyFile(backupTransaction.resolve(this.TRANSACTION_FILENAME).toFile(), this.location.resolve("channel_1/" + this.TRANSACTION_FILENAME).toFile());

        //load again
        customers = new ArrayList<>();

        try (EmbeddedStorageManager storageManager = configuration.setStorageDirectory(this.location.toString())
                .createEmbeddedStorageFoundation().createEmbeddedStorageManager(customers).start()) {
            final List<File> files = (List<File>) FileUtils.listFiles(truncationPath.toFile(), null, true);
            assertTrue(files.size() > 1, files.toString());
        }
    }

    private void printFiles(File dir)
    {
        Collection<File> files = FileUtils.listFiles(dir, null, true);
        files.forEach(System.out::println);
    }

    private byte[] removeLastBytes(byte[] data, final int numberOfBytesToRemove)
    {
        final int size = data.length;
        for (int i = size; i > (size - numberOfBytesToRemove); i--) {
            data = ArrayUtils.remove(data, i - 1);
        }
        return data;
    }

}
