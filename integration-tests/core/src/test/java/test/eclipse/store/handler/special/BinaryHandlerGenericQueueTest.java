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

import java.util.PriorityQueue;

import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;

class BinaryHandlerGenericQueueTest extends AbstractSpecialHandlerTest
{

    @Test
    void binaryHandlerGenericQueueTest()
    {
        PriorityQueue<Integer> original = new PriorityQueue<>();
        original.add(100);
        PriorityQueue<Integer> copy = new PriorityQueue<>();

        EmbeddedStorageManager storage = startStorageWithCustomTypeQueueHandler(original);
        storage.storeRoot();
        storage.shutdown();

        storage = startStorageWithCustomTypeQueueHandler(copy);

        assertIterableEquals(original, copy);
        storage.shutdown();

    }

//    @Test
//    void binaryHandlerGenericQueueExportImportTest() {
//        EmbeddedStorageManager storage;
//        PriorityQueue<Integer> original = new PriorityQueue<>();
//        original.add(100);
//        PriorityQueue<Integer> copy = new PriorityQueue<>();
//
//        storage = startStorageWithCustomTypeQueueHandler(original);
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
//        storage = startStorageWithCustomTypeQueueHandler(copy);
//        StorageConnection loadConnection = storage.createConnection();
//
//        loadConnection.importFiles(HashEnum.New(exportFiles));
//        storage.shutdown();
//    }


}
