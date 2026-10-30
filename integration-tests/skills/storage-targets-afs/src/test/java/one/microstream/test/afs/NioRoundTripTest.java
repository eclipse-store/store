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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;

import org.eclipse.serializer.afs.types.ADirectory;
import org.eclipse.store.afs.nio.types.NioFileSystem;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Skill pattern A — local NIO via explicit NioFileSystem.New() + ensureDirectoryPath.
 */
class NioRoundTripTest
{
    @Test
    void roundTripPersistsNotes(@TempDir Path tempDir)
    {
        // Seed
        {
            final NioFileSystem fs        = NioFileSystem.New();
            final ADirectory    storageDir = fs.ensureDirectoryPath(tempDir.toAbsolutePath().toString(), "data");

            final Notebook root = new Notebook();
            final EmbeddedStorageManager storage = EmbeddedStorage.start(root, storageDir);
            root.notes().add(new Note(1L, "first"));
            root.notes().add(new Note(2L, "second"));
            storage.store(root.notes());   // store the modified COLLECTION, not the root
            storage.shutdown();
        }

        // Reopen
        {
            final NioFileSystem fs        = NioFileSystem.New();
            final ADirectory    storageDir = fs.ensureDirectoryPath(tempDir.toAbsolutePath().toString(), "data");

            final Notebook root = new Notebook();
            final EmbeddedStorageManager storage = EmbeddedStorage.start(root, storageDir);
            try
            {
                final Notebook loaded = (Notebook) storage.root();
                assertEquals(2, loaded.notes().size());
                assertEquals("first",  loaded.notes().get(0).text());
                assertEquals("second", loaded.notes().get(1).text());
            }
            finally
            {
                storage.shutdown();
            }
        }
    }
}
