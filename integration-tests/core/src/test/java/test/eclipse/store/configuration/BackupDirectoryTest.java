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
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.eclipse.serializer.afs.types.ADirectory;
import org.eclipse.serializer.afs.types.AFileSystem;
import org.eclipse.store.afs.nio.types.NioFileSystem;
import org.eclipse.store.storage.embedded.configuration.types.EmbeddedStorageConfiguration;
import org.eclipse.store.storage.embedded.configuration.types.EmbeddedStorageConfigurationBuilder;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageFoundation;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.types.Storage;
import org.eclipse.store.storage.types.StorageBackupFileProvider;
import org.eclipse.store.storage.types.StorageBackupSetup;
import org.eclipse.store.storage.types.StorageLiveFileProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import test.eclipse.serializer.fixtures.types.BasicNonPrimitive;
import test.eclipse.serializer.fixtures.types.BasicNonPrimitiveArrayTypes;

class BackupDirectoryTest
{

    @TempDir
    Path location;

    @TempDir
    Path backup;

    Path configFilePath;

    EmbeddedStorageManager storageManager;

    @AfterEach
    public void closeStorage()
    {
        if (this.storageManager != null) {
            if (!this.storageManager.isShutdown()) {
                this.storageManager.shutdown();
            }
        }
    }


    /**
     * https://github.com/microstream-one/microstream/issues/197
     *
     * @param secondBackup
     * @throws InterruptedException
     */
    @Test
    public void backup_in_another_folder_as_original(@TempDir Path secondBackup) throws InterruptedException
    {

        final BasicNonPrimitive basicNonPrimitive = new BasicNonPrimitive();
        basicNonPrimitive.fillSampleData();

        storageManager = EmbeddedStorageConfigurationBuilder.New()
                .setStorageDirectory(location.toAbsolutePath().toString())
                .setBackupDirectory(backup.toAbsolutePath().toString())
                .createEmbeddedStorageFoundation()
                .start(basicNonPrimitive);

        this.storageManager.shutdown();

        List<File> firstBackupFiles = (List<File>) FileUtils.listFiles(backup.toFile(), null, true);
        assertTrue(firstBackupFiles.size() > 1, firstBackupFiles.toString());


        // second start

        storageManager = EmbeddedStorageConfigurationBuilder.New()
                .setStorageDirectory(location.toAbsolutePath().toString())
                .setBackupDirectory(secondBackup.toAbsolutePath().toString())
                .createEmbeddedStorageFoundation()
                .start();

        storageManager.shutdown();

        List<File> secondBackupFiles = (List<File>) FileUtils.listFiles(secondBackup.toFile(), null, true);

        StringBuilder buffer = new StringBuilder();
        secondBackupFiles.forEach(buffer::append);
        assertTrue(buffer.toString().contains("PersistenceTypeDictionary.ptd"), "Check if the type dictionary is in backup folder");

        assertTrue(secondBackupFiles.size() > 1, secondBackupFiles.toString());
    }

    @Test
    public void backup_without_configuration_layer_Test() throws InterruptedException
    {

        final AFileSystem aFileSystem = NioFileSystem.New();
        final ADirectory backupDir = aFileSystem.ensureDirectoryPath(this.backup.toFile().getAbsolutePath());
        final ADirectory dataDir = aFileSystem.ensureDirectoryPath(this.location.toFile().getAbsolutePath());

        final StorageBackupSetup backupSetup = StorageBackupSetup.New(
                StorageBackupFileProvider.New(backupDir)
        );

        final StorageLiveFileProvider provider = StorageLiveFileProvider.New(dataDir);

        final EmbeddedStorageFoundation<?> foundation = EmbeddedStorage.Foundation(
                Storage.ConfigurationBuilder()
                        .setBackupSetup(backupSetup)
                        .setStorageFileProvider(provider)
                        .createConfiguration()

        );

        final BasicNonPrimitive basicNonPrimitive = new BasicNonPrimitive();
        basicNonPrimitive.fillSampleData();

        this.storageManager = foundation.createEmbeddedStorageManager(basicNonPrimitive).start();
        this.storageManager.shutdown();


        final List<File> files = (List<File>) FileUtils.listFiles(backup.toFile(), null, true);
//        System.out.println("file sizes: " + files.size() + "");
//        files.forEach(System.out::println);

        assertTrue(files.size() > 1, files.toString());


    }

    @Test
    void backup_without_configuration_level() throws InterruptedException
    {
        this.location = this.location.resolve("backup_without_configuration_level");
        final Path backupPath = this.location.resolve("backup");

        final AFileSystem aFileSystem = NioFileSystem.New();
        final ADirectory backupDir = aFileSystem.ensureDirectoryPath(backupPath.toFile().getAbsolutePath());

        final ADirectory storageDir = aFileSystem.ensureDirectoryPath(this.location.toFile().getAbsolutePath());

        final StorageBackupSetup backupSetup = StorageBackupSetup.New(
                StorageBackupFileProvider.New(backupDir)
        );

        final EmbeddedStorageFoundation<?> foundation = EmbeddedStorage.Foundation(
                Storage.ConfigurationBuilder()
                        .setBackupSetup(backupSetup)
                        .setStorageFileProvider(StorageLiveFileProvider.New(storageDir))
                        .createConfiguration()
        );

        final BasicNonPrimitiveArrayTypes basicNonPrimitiveArrayTypes = new BasicNonPrimitiveArrayTypes();
        basicNonPrimitiveArrayTypes.fillSampleData();

        this.storageManager = foundation.createEmbeddedStorageManager(basicNonPrimitiveArrayTypes).start();

        this.storageManager.shutdown();

        final List<File> files = (List<File>) FileUtils.listFiles(backupPath.toFile(), null, true);
//        System.out.println("file sizes: " + files.size() + "");
//        files.forEach(System.out::println);

        assertTrue(files.size() > 2, files.toString());


    }

    @Test
    void backupDirectoryTest(@TempDir Path configPath) throws IOException, InterruptedException
    {
        this.configFilePath = configPath.resolve("backupDirectory.ini");

        FileUtils.writeStringToFile(this.configFilePath.toFile(), "backup-directory = " + this.backup.toString(), "UTF-8");

        final List<Customer> customers = new ArrayList<>();
        customers.addAll(CustomerGenerator.generateCustomers(300));

        final EmbeddedStorageConfigurationBuilder configuration =
                EmbeddedStorageConfiguration.load(this.configFilePath.toString());

        this.storageManager = configuration.setStorageDirectory(location.toString()).createEmbeddedStorageFoundation().createEmbeddedStorageManager(customers).start();

        this.storageManager.shutdown();

        final List<File> files = (List<File>) FileUtils.listFiles(this.backup.toFile(), null, true);

        assertTrue(files.size() > 2, files.toString());


    }

    @Test
    void backupDirectoryXMLTest() throws IOException, InterruptedException
    {
        this.configFilePath = this.location.resolve("backupDirectory.xml");
        final Path backupLocation = this.location.resolve("backup");
        backupLocation.toFile().mkdir();

        final StringBuilder builder = new StringBuilder()
                .append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<properties>\n")
                .append("\t<property name=\"backup-directory\" value=\"" + backupLocation.toString().replace("\\", "//") + "\"/>\n")
                .append("</properties>");

        FileUtils.writeStringToFile(this.configFilePath.toFile(), builder.toString(), "UTF-8");

        final Customer customer = CustomerGenerator.generateNewCustomer();

        final EmbeddedStorageConfigurationBuilder configuration =
                EmbeddedStorageConfiguration.load(this.configFilePath.toString());

        this.storageManager = configuration.setStorageDirectory(this.location.toString()).createEmbeddedStorageFoundation().createEmbeddedStorageManager(customer).start();

        this.storageManager.shutdown();

        final List<File> files = (List<File>) FileUtils.listFiles(backupLocation.toFile(), null, true);

        assertTrue(files.size() > 2, files.toString());

    }

    @Test
    void backupDirectoryXMLFullTest() throws IOException, InterruptedException
    {
        this.configFilePath = this.location.resolve("backupDirectory.xml");

        final String xmlConfig = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<properties>\n" +
                "\t<property name=\"backup-directory\" value=\"" + this.backup.toString() + "\"/>\n" +
                "\t<property name=\"storage-directory\" value=\"" + this.location.toString() + "\"/>\n" +
                "</properties>";
        FileUtils.writeStringToFile(this.configFilePath.toFile(), xmlConfig, "UTF-8");

        final Customer customer = CustomerGenerator.generateNewCustomer();

        final EmbeddedStorageConfigurationBuilder configuration =
                EmbeddedStorageConfiguration.load(this.configFilePath.toString());

        this.storageManager = configuration.createEmbeddedStorageFoundation().createEmbeddedStorageManager(customer).start();

        this.storageManager.shutdown();

        final List<File> files = (List<File>) FileUtils.listFiles(this.backup.toFile(), null, true);

        assertTrue(files.size() > 1, files.toString());

    }

}
