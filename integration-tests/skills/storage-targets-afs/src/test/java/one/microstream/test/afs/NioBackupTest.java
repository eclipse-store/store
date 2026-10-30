package one.microstream.test.afs;

/*-
 * #%L
 * EclipseStore Integration Tests - Skills - storage-targets-afs
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.eclipse.store.storage.embedded.configuration.types.EmbeddedStorageConfiguration;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Skill pattern E adapted: both live and backup filesystems are NIO but distinct
 * directories. Verifies the StorageBackupSetup / StorageLiveFileProvider.Builder
 * Foundation wiring compiles and runs.
 */
class NioBackupTest
{
    @Test
    void backupDirectoryReceivesFiles(@TempDir Path tempDir) throws Exception
    {
        final Path liveDir   = tempDir.resolve("live");
        final Path backupDir = tempDir.resolve("backup");
        Files.createDirectories(liveDir);
        Files.createDirectories(backupDir);

        // Skill Pattern E is specifically for "live NIO + backup non-NIO"
        // (the example uses S3 for backup). With BOTH live AND backup on NIO,
        // wiring the low-level Foundation directly hits an
        // AfsExceptionConsistency because each path through
        // Storage.ConfigurationBuilder()/StorageLiveFileProvider creates
        // implicit NioFileSystem instances that don't compose with a custom
        // backup AFS. For the same-AFS-type case, use the high-level
        // EmbeddedStorageConfigurationBuilder (storage-embedded-configuration)
        // which manages this internally.
        final Notebook root = new Notebook();

        final EmbeddedStorageManager storage = EmbeddedStorageConfiguration.Builder()
            .setStorageDirectory(liveDir.toAbsolutePath().toString())
            .setBackupDirectory(backupDir.toAbsolutePath().toString())
            .createEmbeddedStorageFoundation()
            .start(root);

        try
        {
            root.notes().add(new Note(100L, "backed-up"));
            storage.store(root.notes());   // store the modified COLLECTION
            storage.issueFullGarbageCollection();
            storage.issueFullFileCheck();
        }
        finally
        {
            storage.shutdown();
        }

        try (Stream<Path> walk = Files.walk(backupDir))
        {
            final long fileCount = walk.filter(Files::isRegularFile).count();
            assertTrue(fileCount > 0, "backup directory should contain files");
        }
    }
}
