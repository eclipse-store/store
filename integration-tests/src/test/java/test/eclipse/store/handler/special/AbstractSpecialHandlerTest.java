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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.PriorityQueue;

import org.eclipse.serializer.persistence.binary.java.util.BinaryHandlerGenericCollection;
import org.eclipse.serializer.persistence.binary.java.util.BinaryHandlerGenericMap;
import org.eclipse.serializer.persistence.binary.java.util.BinaryHandlerGenericQueue;
import org.eclipse.serializer.persistence.binary.java.util.BinaryHandlerGenericSet;
import org.junit.jupiter.api.io.TempDir;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;

abstract class AbstractSpecialHandlerTest {

    @TempDir
    Path tmpDir;

    EmbeddedStorageManager startStorageWithCustomTypeArrayListHandler(ArrayList<Integer> root) {
        return EmbeddedStorage.Foundation(tmpDir)
                .onConnectionFoundation(f -> f.registerCustomTypeHandler(
                        BinaryHandlerGenericCollection.New(ArrayList.class)
                ))
                .start(root);

    }

    EmbeddedStorageManager startStorageWithCustomTypeHashSetHandler(HashSet<Integer> root) {
        return EmbeddedStorage.Foundation(tmpDir)
                .onConnectionFoundation(f -> f.registerCustomTypeHandler(
                        BinaryHandlerGenericSet.New(HashSet.class)
                ))
                .start(root);

    }

    EmbeddedStorageManager startStorageWithCustomTypeHashMapHandler(HashMap root) {
        return EmbeddedStorage.Foundation(tmpDir)
                .onConnectionFoundation(f -> f.registerCustomTypeHandler(
                        BinaryHandlerGenericMap.New(HashMap.class)
                ))
                .start(root);

    }

    EmbeddedStorageManager startStorageWithCustomTypeQueueHandler(PriorityQueue<Integer> root) {
        return EmbeddedStorage.Foundation(tmpDir)
                .onConnectionFoundation(f -> f.registerCustomTypeHandler(
                        BinaryHandlerGenericQueue.New(PriorityQueue.class)
                ))
                .start(root);

    }
}
