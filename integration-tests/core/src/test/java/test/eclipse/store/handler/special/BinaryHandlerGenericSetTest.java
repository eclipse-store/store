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

import java.util.HashSet;

import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;

class BinaryHandlerGenericSetTest extends AbstractSpecialHandlerTest
{

    @Test
    void binaryHandlerGenericSetTest()
    {
        HashSet<Integer> original = new HashSet<>();
        original.add(100);
        HashSet<Integer> copy = new HashSet<>();


        EmbeddedStorageManager storage = startStorageWithCustomTypeHashSetHandler(original);
        storage.storeRoot();
        storage.shutdown();

        storage = startStorageWithCustomTypeHashSetHandler(copy);

        assertIterableEquals(original, copy);
        storage.shutdown();

    }

//    @Test
//    void binaryHandlerGenericSetExportImportTest() {
//        EmbeddedStorageManager storage;
//        HashSet<Integer> original = new HashSet<>();
//        original.add(100);
//        HashSet<Integer> copy = new HashSet<>();
//
//        storage = startStorageWithCustomTypeHashSetHandler(original);
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
//        storage = startStorageWithCustomTypeHashSetHandler(copy);
//
//        StorageConnection loadConnection = storage.createConnection();
//
//        loadConnection.importFiles(HashEnum.New(exportFiles));
//        storage.shutdown();
//    }

}
