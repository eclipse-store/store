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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;

import org.eclipse.serializer.configuration.exceptions.ConfigurationException;
import org.eclipse.serializer.configuration.types.ByteSize;
import org.eclipse.store.storage.embedded.configuration.types.EmbeddedStorageConfigurationBuilder;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageFoundation;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.datafaker.Faker;

public class TransactionFileSizeTest
{

    @TempDir
    Path location;

    private Faker faker = new Faker();

    //@Test
    @Test
    void setSizeConfigTest() throws InterruptedException
    {
        EmbeddedStorageFoundation<?> embeddedStorageFoundation = EmbeddedStorageConfigurationBuilder.New()
                .setStorageDirectory(location.toAbsolutePath()
                        .toString())
                .setTransactionFileMaximumSize(ByteSize.New("1024"))
                .createEmbeddedStorageFoundation();

        Customer customer = new Customer("first");
        try (EmbeddedStorageManager storageManager = embeddedStorageFoundation.start(customer)) {
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
    void setBadConfigTest()
    {
        assertThrows(ConfigurationException.class, () -> EmbeddedStorageConfigurationBuilder.New()
                .setStorageDirectory(location.toAbsolutePath()
                        .toString())
                .setTransactionFileMaximumSize(ByteSize.New("50"))
                .createEmbeddedStorageFoundation());
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
