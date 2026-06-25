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
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class BinaryHandlerTreeSetComparatorTest
{

    @TempDir
    Path tempDir;


    @Test
    void treeMapComparatorTest()
    {
        TreeSet<Integer> reverseSet = new TreeSet<>(Comparator.reverseOrder());
        reverseSet.add(2);
        reverseSet.add(1);
        reverseSet.add(3);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(reverseSet, tempDir)) {
            reverseSet.add(-4);
            storageManager.storeRoot();
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(tempDir)) {
            TreeSet<Integer> loadedSet = (TreeSet<Integer>) storageManager.root();

            assertNotNull(loadedSet.comparator());

            List<Integer> expectedKeys = List.of(3, 2, 1, -4);
            assertIterableEquals(expectedKeys, loadedSet);
        }

    }
}
