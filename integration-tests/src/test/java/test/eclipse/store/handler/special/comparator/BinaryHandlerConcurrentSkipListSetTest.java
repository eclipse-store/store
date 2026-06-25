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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.ConcurrentSkipListSet;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class BinaryHandlerConcurrentSkipListSetTest
{

    @TempDir
    Path tempDir;

    @Test
    void binaryHandlerConcurrentSkipListSetTest()
    {
        ConcurrentSkipListSet<Integer> loadedMap = new ConcurrentSkipListSet<>(Comparator.reverseOrder());
        loadedMap.add(1);
        loadedMap.add(2);
        loadedMap.add(3);
        loadedMap.add(4);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loadedMap, tempDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(tempDir)) {
            ConcurrentSkipListSet<Integer> root = (ConcurrentSkipListSet<Integer>) storageManager.root();

            // Check if the comparator is set
            if (root.comparator() == null) {
                throw new IllegalStateException("The comparator should not be null.");
            }


            // Check the order of elements
            Integer[] expectedKeys = {4, 3, 2, 1};
            Integer[] actualKeys = root.toArray(new Integer[0]);
            assertArrayEquals(expectedKeys, actualKeys, "The keys are not in reverse order.");

            root.add(-1);
            root.add(6);

            storageManager.storeRoot();
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(tempDir)) {
            ConcurrentSkipListSet<Integer> rootLoaded = (ConcurrentSkipListSet< Integer>) storageManager.root();

            // Check if the comparator is set
            if (rootLoaded.comparator() == null) {
                throw new IllegalStateException("The comparator should not be null.");
            }

            // Check the order of elements after adding a new element
            Integer[] expectedKeysAfterAdd = {6, 4, 3, 2, 1, -1};
            Integer[] actualKeysAfterAdd = rootLoaded.toArray(new Integer[0]);
            assertArrayEquals(expectedKeysAfterAdd, actualKeysAfterAdd);
        }


    }
}
