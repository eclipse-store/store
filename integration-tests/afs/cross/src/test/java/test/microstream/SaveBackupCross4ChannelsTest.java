package test.microstream;

/*-
 * #%L
 * EclipseStore Integration Tests - AFS - cross
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

import java.nio.file.Path;
import java.sql.SQLException;

import org.eclipse.serializer.afs.types.ADirectory;
import org.eclipse.serializer.afs.types.AFileSystem;
import org.eclipse.store.afs.nio.types.NioFileSystem;
import org.eclipse.store.afs.sql.types.SqlConnector;
import org.eclipse.store.afs.sql.types.SqlFileSystem;
import org.eclipse.store.afs.sql.types.SqlProviderMariaDb;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.types.Storage;
import org.eclipse.store.storage.types.StorageBackupSetup;
import org.eclipse.store.storage.types.StorageChannelCountProvider;
import org.eclipse.store.storage.types.StorageConfiguration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mariadb.jdbc.MariaDbDataSource;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import net.datafaker.Faker;
import test.microstream.cross.db.DataRoot;
import test.microstream.cross.entity.Customer;

@Testcontainers
public class SaveBackupCross4ChannelsTest {

    @TempDir
    Path tempDir;

    @Container
    private final MariaDBContainer MARIA_DB_CONTAINER = new MariaDBContainer("mariadb:latest").withDatabaseName("jpa-test")
            .withPassword("test");

    @Test
    public void backupTestCross_nio_mysql() throws InterruptedException, SQLException {

        final DataRoot root = new DataRoot();

        final MariaDbDataSource dataSource = new MariaDbDataSource();
        dataSource.setUrl(MARIA_DB_CONTAINER.getJdbcUrl());
        dataSource.setUser(MARIA_DB_CONTAINER.getUsername());
        dataSource.setPassword(MARIA_DB_CONTAINER.getPassword());

        AFileSystem aFileSystem = NioFileSystem.New();

        AFileSystem backupFileSystem = SqlFileSystem.New(
                SqlConnector.Caching(
                        SqlProviderMariaDb.New(dataSource)
                )
        );

        ADirectory aDirectory = aFileSystem.ensureDirectoryPath(tempDir.toFile()
                .getAbsolutePath());
        ADirectory backupDirectory = backupFileSystem.ensureDirectoryPath("backup");

        StorageBackupSetup backupSetup = StorageBackupSetup.New(backupDirectory);

        StorageConfiguration configuration = StorageConfiguration.Builder()
                .setStorageFileProvider(Storage.FileProvider(aDirectory))
                .setBackupSetup(backupSetup)
                .setChannelCountProvider(StorageChannelCountProvider.New(4))
                .createConfiguration();

        EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, configuration);

        final Faker faker = new Faker();

        for (int i = 0; i < 60; i++) {
            final Customer customer = new Customer();
            customer.setFirstName(faker.name()
                    .firstName());
            customer.setLastName(faker.name()
                    .lastName());
            customer.setAddress(faker.address()
                    .streetAddress());
            customer.setCompanyName(faker.company()
                    .name());
            root.getCustomers()
                    .add(customer);
        }
        storageManager.store(root.getCustomers()); //save data
        //Thread.sleep(10_000);
        storageManager.shutdown();

        root.getCustomers()
                .clear(); // clear data


        StorageConfiguration configuration2 = StorageConfiguration.Builder()
                .setStorageFileProvider(Storage.FileProvider(backupDirectory))
                .setChannelCountProvider(StorageChannelCountProvider.New(4))
                .createConfiguration();
        storageManager = EmbeddedStorage.start(root, configuration2); //load data from backup
        Assertions.assertEquals(60, root.getCustomers()
                .size()); //check, if data are loaded
        storageManager.shutdown();
    }
}
