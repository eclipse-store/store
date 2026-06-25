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

import org.eclipse.store.storage.embedded.configuration.types.EmbeddedStorageConfigurationBuilder;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.exceptions.StorageExceptionStructureValidation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import test.eclipse.store.configuration.Customer;
import test.eclipse.store.configuration.CustomerGenerator;

import java.nio.file.Path;

public class ChannelCountValidatorTest {

    @TempDir
    Path location;

    @Test
    public void channelCountDowngradeValidatorTest() {
        final Customer customer = CustomerGenerator.generateNewCustomer();

        EmbeddedStorageConfigurationBuilder configuration = EmbeddedStorageConfigurationBuilder.New();
        configuration.setChannelCount(4);
        configuration.setStorageDirectory(location.toAbsolutePath().toString());
        configuration.buildConfiguration();

        EmbeddedStorageManager embeddedStorageManager = configuration.createEmbeddedStorageFoundation().createEmbeddedStorageManager(customer).start();

        embeddedStorageManager.shutdown();

        Assertions.assertThrows(StorageExceptionStructureValidation.class, () -> EmbeddedStorage.start(customer, location));
    }

    @Test
    public void channelCountUpgradeValidatorTest() {
        final Customer customer = CustomerGenerator.generateNewCustomer();

        EmbeddedStorageManager start = EmbeddedStorage.start(customer, location);
        start.shutdown();

        EmbeddedStorageConfigurationBuilder configuration = EmbeddedStorageConfigurationBuilder.New();
        configuration.setChannelCount(4);
        configuration.setStorageDirectory(location.toAbsolutePath().toString());
        configuration.buildConfiguration();

        EmbeddedStorageManager embeddedStorageManager = configuration.createEmbeddedStorageFoundation().createEmbeddedStorageManager(customer);
        Assertions.assertThrows(StorageExceptionStructureValidation.class, embeddedStorageManager::start);

        Assertions.assertFalse(embeddedStorageManager.isRunning());

    }

}
