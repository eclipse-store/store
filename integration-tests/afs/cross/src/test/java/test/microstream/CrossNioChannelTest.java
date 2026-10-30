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

import org.eclipse.store.afs.nio.types.NioFileSystem;
import org.eclipse.store.storage.types.Storage;
import org.eclipse.store.storage.types.StorageBackupSetup;
import org.eclipse.store.storage.types.StorageChannelCountProvider;
import org.eclipse.store.storage.types.StorageConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.eclipse.store.storage.embedded.types.EmbeddedStorageFoundation;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;

public class CrossNioChannelTest {

    @TempDir
    Path tempDir;




    @Test
    public void crossNioBackupChannelTest() {

        final Path backupPath = this.tempDir.resolve("backup");
        final Path dataPath = this.tempDir.resolve("data-dir");


        final NioFileSystem fileSystem     = NioFileSystem.New();
        final NioFileSystem          fileSystem_2   = NioFileSystem.New();

        final EmbeddedStorageManager storageManager = EmbeddedStorageFoundation.New()
                .setConfiguration(
                        StorageConfiguration.Builder()
                                .setStorageFileProvider(
                                        Storage.FileProviderBuilder(fileSystem)
                                                .setDirectory(fileSystem.ensureDirectoryPath(dataPath.toAbsolutePath().toString()))
                                                .createFileProvider()
                                )
                                .setChannelCountProvider(StorageChannelCountProvider.New(4))
                                .setBackupSetup(StorageBackupSetup.New(
                                        fileSystem_2.ensureDirectoryPath(String.valueOf(backupPath.toAbsolutePath()))
                                ))
                                .createConfiguration()
                )
                .createEmbeddedStorageManager();


        storageManager.start();
        storageManager.setRoot("Hello World");
        storageManager.storeRoot();
        storageManager.shutdown();
    }
}
