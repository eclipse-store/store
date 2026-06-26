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

import static org.junit.jupiter.api.Assertions.assertIterableEquals;

import java.util.HashMap;

import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;

class BinaryHandlerGenericMapTest extends AbstractSpecialHandlerTest
{

    @Test
    void binaryHandlerGenericMapTest()
    {
        HashMap<Integer, Integer> original = new HashMap<>();
        original.put(1, 100);
        HashMap<Integer, Integer> copy = new HashMap<>();

        EmbeddedStorageManager storage = startStorageWithCustomTypeHashMapHandler(original);
        storage.storeRoot();
        storage.shutdown();

        storage = startStorageWithCustomTypeHashMapHandler(copy);

        assertIterableEquals(original.entrySet(), copy.entrySet());
        assertIterableEquals(original.values(), copy.values());
        storage.shutdown();
    }

//    @Test
//    void binaryHandlerGenericMapExportImportTest() {
//        EmbeddedStorageManager storage;
//        HashMap<Integer, Integer> original = new HashMap<>();
//        original.put(1, 100);
//        HashMap<Integer, Integer> copy = new HashMap<>();
//
//        storage = startStorageWithCustomTypeHashMapHandler(original);
//
//        StorageConnection connection = storage.createConnection();
//        String fileSuffix = "bin";
//
//        StorageEntityTypeExportStatistics exportResult = connection.exportTypes(
//                new StorageEntityTypeExportFileProvider.Default(tmpDir, fileSuffix),
//                typeHandler -> true // export all, customize if necessary
//        );
//        XSequence<Path> exportFiles = CQL
//                .from(exportResult.typeStatistics().values())
//                .project(s -> (new File(s.file().identifier())).toPath())
//                .execute();
//        storage.shutdown();
//
//        storage = startStorageWithCustomTypeHashMapHandler(copy);
//        StorageConnection loadConnection = storage.createConnection();
//
//        loadConnection.importFiles(HashEnum.New(exportFiles));
//        assertIterableEquals(original.entrySet(), copy.entrySet());
//        assertIterableEquals(original.values(), copy.values());
//        storage.shutdown();
//    }

}
