package test.eclipse.store.entity;

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


import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.io.FileUtils;
import org.eclipse.serializer.afs.types.ADirectory;
import org.eclipse.serializer.afs.types.AFile;
import org.eclipse.serializer.collections.types.XEnum;
import org.eclipse.serializer.collections.types.XSequence;
import org.eclipse.serializer.util.X;
import org.eclipse.serializer.util.cql.CQL;
import org.eclipse.store.afs.nio.types.NioFileSystem;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.types.StorageConnection;
import org.eclipse.store.storage.types.StorageEntityTypeExportFileProvider;
import org.eclipse.store.storage.types.StorageEntityTypeExportStatistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import test.eclipse.serializer.fixtures.types.BinaryHandlerTestData;

public abstract class AbstractHandlerTest<T extends BinaryHandlerTestData>
{

    private Class<T> aClass;

    private EmbeddedStorageManager storage;

    private Path actualDirectory;

    public AbstractHandlerTest(Class<T> aClass)
    {
        this.aClass = aClass;
    }


    public abstract void proveResult(T original, T copy);

    @Test
    void saveAndLoadTest(@TempDir Path tempDir) throws IllegalAccessException, InstantiationException, NoSuchMethodException, InvocationTargetException
    {
        System.out.println(tempDir);
        System.out.println(this);
        T original = aClass.getDeclaredConstructor().newInstance();
        original.fillSampleData();
        T copy = aClass.getDeclaredConstructor().newInstance();
        saveAndReload(original, copy, tempDir);
        proveResult(original, copy);
        storage.shutdown(); //must be closed here, in prove result is for example lazy test and then needs to load some data from storage.
    }


    @Test
    void exportImportTest(@TempDir Path tempDir) throws IllegalAccessException, InstantiationException, InterruptedException, NoSuchMethodException, InvocationTargetException
    {
        //System.out.println(tempDir);
        //System.out.println(this);
        T original = aClass.getDeclaredConstructor().newInstance();
        original.fillSampleData();
        T copy = aClass.getDeclaredConstructor().newInstance();

        Thread.sleep(10);
        actualDirectory = tempDir.resolve(String.valueOf(System.currentTimeMillis()));
        storage = startStorage(original);
        StorageConnection connection = storage.createConnection();
        String fileSuffix = "bin";
        assertNotNull(tempDir);

        final NioFileSystem fs = NioFileSystem.New();

        final ADirectory dir = fs.ensureDirectoryPath(tempDir.toFile().getAbsolutePath());


        StorageEntityTypeExportStatistics exportResult = connection.exportTypes(
                new StorageEntityTypeExportFileProvider.Default(dir, fileSuffix),
                typeHandler -> true // export all, customize if necessary
        );
        XSequence<Path> exportFiles = CQL
                .from(exportResult.typeStatistics().values())
                .project(s -> Paths.get(s.file().identifier()))
                .execute();
        storage.shutdown();

        storage = startStorage(copy);
        StorageConnection loadConnection = storage.createConnection();

        XEnum<AFile> importFiles = X.Enum();

        for (Path p : exportFiles) {
            Path fullPath = tempDir.resolve(p);

            importFiles.add(fs.ensureFile(fullPath));
        }

        loadConnection.importFiles(importFiles);
        proveResult(original, copy);
        storage.shutdown();
    }

    @AfterEach
    void cleanStorage() throws IOException
    {
        if (null != storage && !storage.isShutdown()) {
            storage.shutdown();
        }
        FileUtils.deleteDirectory(actualDirectory.toFile());
    }

    <O> O saveAndReload(O original, O loaded, Path targetDirectory)
    {
        this.actualDirectory = targetDirectory.resolve(String.valueOf(System.currentTimeMillis()));
        storage = startStorage(original);
        storage.storeRoot();
        storage.shutdown();

        storage = startStorage(loaded);
        return loaded;
    }

    private EmbeddedStorageManager startStorage(Object root)
    {
        return EmbeddedStorage.start(root, actualDirectory);
    }
}
