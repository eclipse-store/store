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

import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class BinaryHandlerTreeMapComparatorTest
{

    @TempDir
    Path tempDir;


    @Test
    void treeMapComparatorTest()
    {
        TreeMap<String, Integer> reverseMap = new TreeMap<>(Comparator.reverseOrder());
        reverseMap.put("b", 2);
        reverseMap.put("a", 1);
        reverseMap.put("c", 3);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(reverseMap, tempDir)) {
            reverseMap.put("d", -4);
            storageManager.storeRoot();
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(tempDir)) {
            TreeMap<String, Integer> loadedMap = (TreeMap<String, Integer>) storageManager.root();

            assertNotNull(loadedMap.comparator());

            List<String> expectedKeys = List.of("d", "c", "b", "a");
            List<String> actualKeys = new ArrayList<>(loadedMap.keySet());
            assertIterableEquals(expectedKeys, actualKeys);
        }


    }
}
