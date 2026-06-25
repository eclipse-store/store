package test.eclipse.store.handler.special;

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

import java.io.IOException;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import test.eclipse.store.handler.basic.PrimitiveTypes;

class BinaryHandlerObjectTest {

    @TempDir
    Path workDir;
    private EmbeddedStorageManager storage;

    @AfterEach
    void cleanStorage() throws IOException {
        if (null != storage && !storage.isShutdown()) {
            storage.shutdown();
        }
        FileUtils.deleteDirectory(workDir.toFile());
    }

    @Test
    void binaryHandlerObjectTest() {
        Object original = PrimitiveTypes.fillSample();
        Object copy = new PrimitiveTypes();

        saveAndReload(original, copy);

        assertEquals(original, copy);
    }


//    @Test
//    void exportImportTest() {
//        Object original = PrimitiveTypes.fillSample();
//        Object copy = new PrimitiveTypes();
//
//        storage = startStorage(original);
//        StorageConnection connection = storage.createConnection();
//        String fileSuffix = "bin";
//        assertNotNull(workDir);
//        StorageEntityTypeExportStatistics exportResult = connection.exportTypes(
//                new StorageEntityTypeExportFileProvider.Default(workDir, fileSuffix),
//                typeHandler -> true // export all, customize if necessary
//        );
//        XSequence<Path> exportFiles = CQL
//                .from(exportResult.typeStatistics().values())
//                .project(s -> (new File(s.file().identifier())).toPath())
//                .execute();
//        storage.shutdown();
//
//        storage = startStorage(copy);
//        StorageConnection  loadConnection = storage.createConnection();
//
//        loadConnection.importFiles(HashEnum.New(exportFiles));
//        assertEquals(original, copy);
//    }


    <O> O saveAndReload(O original, O loaded) {
        storage = startStorage(original);
        storage.storeRoot();
        storage.shutdown();

        storage = startStorage(loaded);
        return loaded;
    }

    private EmbeddedStorageManager startStorage(Object root) {
        return EmbeddedStorage.start(root, workDir);
    }
}
