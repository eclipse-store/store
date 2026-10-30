package test.eclipse.store.various.jdk;

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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class PathFileTest
{
    @TempDir
    Path tempDir;

    @Test
    void pathStoreAndReload() throws Exception
    {
        Path p = tempDir.resolve("subdir/test.txt");
        Files.createDirectories(p.getParent());
        Files.write(p, "hello".getBytes(), StandardOpenOption.CREATE, StandardOpenOption.WRITE);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(p, tempDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(tempDir)) {
            Path loaded = (Path) storageManager.root();

            assertEquals(p.toAbsolutePath().toString(), loaded.toAbsolutePath().toString(), "Path should be equal after storing and reloading");
        }
    }

    @Test
    void fileStoreAndReload() throws Exception
    {
        File f = tempDir.resolve("fileA.txt").toFile();
        Files.write(f.toPath(), "data".getBytes(), StandardOpenOption.CREATE, StandardOpenOption.WRITE);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(f, tempDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(tempDir)) {
            File loaded = (File) storageManager.root();

            assertEquals(f.getAbsolutePath(), loaded.getAbsolutePath(), "File should be equal after storing and reloading");
        }
    }

    @Test
    void savePathFileDataAndReload() throws Exception
    {
        Path p = tempDir.resolve("another.txt");
        Files.write(p, "x".getBytes(), StandardOpenOption.CREATE, StandardOpenOption.WRITE);

        PathFileData root = new PathFileData(p);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        PathFileData loadedRoot = new PathFileData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loadedRoot, tempDir)) {
            assertEquals(p.toAbsolutePath().toString(), loadedRoot.getValue().toAbsolutePath().toString(), "PathFileData should be equal after storing and reloading");
        }
    }

    private static class PathFileData
    {
        private Path value;

        public PathFileData(Path value)
        {
            this.value = value;
        }

        public PathFileData()
        {
        }

        public Path getValue()
        {
            return value;
        }

        public void setValue(Path value)
        {
            this.value = value;
        }
    }
}

