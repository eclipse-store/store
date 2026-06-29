package test.eclipse.store.handler.special.comparator;

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
import java.util.Comparator;
import java.util.concurrent.ConcurrentSkipListMap;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class BinaryHandlerConcurrentSkipListMapTest
{

    @TempDir
    Path tempDir;

    @Test
    void binaryHandlerConcurrentSkipListMapTest()
    {
        ConcurrentSkipListMap<Integer, Integer> loadedMap = new ConcurrentSkipListMap<>(Comparator.reverseOrder());
        loadedMap.put(1, 1);
        loadedMap.put(2, 2);
        loadedMap.put(3, 3);
        loadedMap.put(4, 4);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loadedMap, tempDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(tempDir)) {
            ConcurrentSkipListMap<Integer, Integer> root = (ConcurrentSkipListMap<Integer, Integer>) storageManager.root();

            // Check if the comparator is set
            if (root.comparator() == null) {
                throw new IllegalStateException("The comparator should not be null.");
            }


            // Check the order of elements
            Integer[] expectedKeys = {4, 3, 2, 1};
            Integer[] actualKeys = root.keySet().toArray(new Integer[0]);
            for (int i = 0; i < expectedKeys.length; i++) {
                if (!expectedKeys[i].equals(actualKeys[i])) {
                    throw new IllegalStateException("The keys are not in reverse order.");
                }
            }

            root.put(-1, -1);
            root.put(6, 6);

            storageManager.storeRoot();
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(tempDir)) {
            ConcurrentSkipListMap<Integer, Integer> rootLoaded = (ConcurrentSkipListMap<Integer, Integer>) storageManager.root();

            // Check if the comparator is set
            if (rootLoaded.comparator() == null) {
                throw new IllegalStateException("The comparator should not be null.");
            }

            // Check the order of elements after adding a new element
            Integer[] expectedKeysAfterAdd = {6, 4, 3, 2, 1, -1};
            Integer[] actualKeysAfterAdd = rootLoaded.keySet().toArray(new Integer[0]);
            for (int i = 0; i < expectedKeysAfterAdd.length; i++) {
                if (!expectedKeysAfterAdd[i].equals(actualKeysAfterAdd[i])) {
                    throw new IllegalStateException("The keys are not in reverse order after adding a new element.");
                }
            }
        }


    }
}
