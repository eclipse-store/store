package test.eclipse.store.various;

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
import java.nio.file.Path;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.types.Storage;
import org.eclipse.store.storage.types.StorageConfiguration;
import org.eclipse.store.storage.types.StorageDataFileEvaluator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.datafaker.Faker;

public class TransactionFileSizeTest
{

    @TempDir
    Path location;

    Faker faker = new Faker();

    @Test
    void setSizeConfigTest()
    {
        StorageDataFileEvaluator fileEvaluator = StorageDataFileEvaluator.New(1048576, 10485760, 0.75, true, 1025);
        final StorageConfiguration cfg = StorageConfiguration.Builder()
                .setStorageFileProvider(Storage.FileProvider(location))
                .setDataFileEvaluator(fileEvaluator)
                .createConfiguration();

        Customer customer = new Customer("first");
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(customer, cfg)) {
            for (int i = 0; i < 500; i++) {
                customer.setName(faker
                        .name()
                        .lastName());
                storageManager.store(customer);
            }
        }
        File file = Path.of(location.toAbsolutePath()
                        .toString(), "channel_0", "transactions_0.sft")
                .toFile();
        long length = file.length();
        //System.out.println(length);
        assertTrue(length < 15_000);
    }

    @Test
    void callManualTest()
    {

        Customer customer = new Customer("first");
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(customer, location)) {
            for (int i = 0; i < 10; i++) {
                customer.setName(faker
                        .name()
                        .lastName());
                storageManager.store(customer);
                File file = Path.of(location.toAbsolutePath()
                                .toString(), "channel_0", "transactions_0.sft")
                        .toFile();
                long length = file.length();
                storageManager.issueTransactionsLogCleanup();
                long lengthAfterCleanup = file.length();
                assertTrue(length > lengthAfterCleanup);
            }
        }
    }

    static class Customer
    {
        String name;

        public Customer(String name)
        {
            this.name = name;
        }

        public void setName(String name)
        {
            this.name = name;
        }
    }

}
