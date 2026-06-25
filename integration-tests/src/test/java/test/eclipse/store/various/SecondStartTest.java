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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.serializer.afs.types.ADirectory;
import org.eclipse.serializer.afs.types.AFileSystem;
import org.eclipse.store.afs.nio.types.NioFileSystem;
import org.eclipse.store.storage.embedded.configuration.types.EmbeddedStorageConfigurationBuilder;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageFoundation;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.types.Storage;
import org.eclipse.store.storage.types.StorageBackupFileProvider;
import org.eclipse.store.storage.types.StorageBackupSetup;
import org.eclipse.store.storage.types.StorageLiveFileProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import test.eclipse.store.library.TypeRegister;

public class SecondStartTest
{

    @Test
    public void twoStartTest(@TempDir Path dir)
    {
        final EmbeddedStorageManager storage = EmbeddedStorage.start(dir);
        storage.shutdown();
        storage.start();
        storage.shutdown();
    }

    @Test
    public void twoStart_defaultRoot_withTempDir(@TempDir Path dir)
    {
        final EmbeddedStorageManager storage = EmbeddedStorage.start(dir);
        storage.shutdown();
        storage.start();
        storage.shutdown();
    }

    @Test
    public void twoStart_pojoRoot(@TempDir Path dir)
    {
        final List<String> root = new ArrayList<>();
        root.add("alpha");

        final EmbeddedStorageManager storage = EmbeddedStorage.start(root, dir);
        storage.storeRoot();
        storage.shutdown();

        storage.start();
        final List<String> reloaded = storage.root();
        assertNotNull(reloaded);
        assertEquals(1, reloaded.size());
        assertEquals("alpha", reloaded.get(0));
        storage.shutdown();
    }

    @Test
    public void twoStart_typeRegisterRoot(@TempDir Path dir)
    {
        final TypeRegister register = new TypeRegister();
        register.fillSampleDate();

        final EmbeddedStorageManager storage = EmbeddedStorage.start(register, dir);
        storage.storeRoot();
        storage.shutdown();

        storage.start();
        final TypeRegister reloaded = storage.root();
        assertNotNull(reloaded);
        storage.shutdown();
    }

    @Test
    public void twoStart_setRootAfterFirstStart(@TempDir Path dir)
    {
        final EmbeddedStorageManager storage = EmbeddedStorage.start(dir);
        assertNull(storage.root());

        final List<String> newRoot = new ArrayList<>();
        newRoot.add("beta");
        storage.setRoot(newRoot);
        storage.storeRoot();
        storage.shutdown();

        storage.start();
        final List<String> reloaded = storage.root();
        assertNotNull(reloaded);
        assertEquals(1, reloaded.size());
        assertEquals("beta", reloaded.get(0));
        storage.shutdown();
    }

    @Test
    public void twoStart_withBackup_builder(@TempDir Path dir, @TempDir Path backup)
    {
        final List<String> root = new ArrayList<>();
        root.add("alpha");

        final EmbeddedStorageManager storage = EmbeddedStorageConfigurationBuilder.New()
                .setStorageDirectory(dir.toAbsolutePath().toString())
                .setBackupDirectory(backup.toAbsolutePath().toString())
                .createEmbeddedStorageFoundation()
                .createEmbeddedStorageManager(root)
                .start();
        storage.storeRoot();
        storage.shutdown();

        final long backupSizeAfterFirstShutdown = totalFileSize(backup);
        assertTrue(backupSizeAfterFirstShutdown > 0,
                "Backup must contain data after first shutdown, got " + backupSizeAfterFirstShutdown + " bytes");

        storage.start();
        final List<String> reloaded = storage.root();
        assertNotNull(reloaded);
        assertEquals(1, reloaded.size());
        assertEquals("alpha", reloaded.get(0));

        reloaded.add("beta");
        storage.store(reloaded);
        storage.shutdown();

        final long backupSizeAfterSecondShutdown = totalFileSize(backup);
        final long liveSizeAfterSecondShutdown = totalFileSize(dir);

        // Independently verify the backup is a faithful copy by opening it as primary storage.
        final EmbeddedStorageManager verifier = EmbeddedStorage.start(new ArrayList<String>(), backup);
        final List<String> fromBackup = verifier.root();
        assertNotNull(fromBackup);
        assertEquals(2, fromBackup.size(),
                "Backup must contain both entries written across two start cycles. "
                        + "backup bytes: " + backupSizeAfterFirstShutdown + " -> " + backupSizeAfterSecondShutdown
                        + ", live bytes after 2nd shutdown: " + liveSizeAfterSecondShutdown
                        + ", fromBackup: " + fromBackup);
        assertEquals("alpha", fromBackup.get(0));
        assertEquals("beta", fromBackup.get(1));
        verifier.shutdown();

        assertEquals(liveSizeAfterSecondShutdown, backupSizeAfterSecondShutdown,
                "Backup must mirror the live storage byte-for-byte in total size after shutdown");
        assertTrue(backupSizeAfterSecondShutdown > backupSizeAfterFirstShutdown,
                "Backup must keep receiving writes after restart: "
                        + backupSizeAfterFirstShutdown + " -> " + backupSizeAfterSecondShutdown + " bytes");
    }

    private static long totalFileSize(final Path directory)
    {
        try (java.util.stream.Stream<Path> stream = java.nio.file.Files.walk(directory)) {
            return stream
                    .filter(java.nio.file.Files::isRegularFile)
                    .mapToLong(p -> {
                        try {
                            return java.nio.file.Files.size(p);
                        } catch (java.io.IOException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .sum();
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void twoStart_foundationApi_withBackup(@TempDir Path dir, @TempDir Path backup)
    {
        final AFileSystem fs = NioFileSystem.New();
        final ADirectory dataDir = fs.ensureDirectoryPath(dir.toFile().getAbsolutePath());
        final ADirectory backupDir = fs.ensureDirectoryPath(backup.toFile().getAbsolutePath());

        final StorageBackupSetup backupSetup = StorageBackupSetup.New(
                StorageBackupFileProvider.New(backupDir)
        );

        final EmbeddedStorageFoundation<?> foundation = EmbeddedStorage.Foundation(
                Storage.ConfigurationBuilder()
                        .setBackupSetup(backupSetup)
                        .setStorageFileProvider(StorageLiveFileProvider.New(dataDir))
                        .createConfiguration()
        );

        final TypeRegister register = new TypeRegister();
        register.fillSampleDate();

        final EmbeddedStorageManager storage = foundation
                .createEmbeddedStorageManager(register)
                .start();
        storage.storeRoot();
        storage.shutdown();

        storage.start();
        final TypeRegister reloaded = storage.root();
        assertNotNull(reloaded);
        storage.shutdown();
    }

    @Test
    public void threeStartCycles_pojoRoot(@TempDir Path dir)
    {
        final List<String> root = new ArrayList<>();
        root.add("entry-0");

        final EmbeddedStorageManager storage = EmbeddedStorage.start(root, dir);
        storage.storeRoot();
        storage.shutdown();

        for (int i = 0; i < 3; i++) {
            storage.start();
            assertNotNull(storage.root());
            storage.shutdown();
        }
    }
}
