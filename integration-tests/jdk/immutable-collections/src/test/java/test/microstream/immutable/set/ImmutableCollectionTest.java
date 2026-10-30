package test.microstream.immutable.set;

/*-
 * #%L
 * EclipseStore Integration Tests - JDK - Immutable Collections
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
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageFoundation;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ImmutableCollectionTest {

    EmbeddedStorageManager storageManager;

    @TempDir
    Path tempDir;

    @Test
    public void immutableSetTest() {

        Set<String> root = Set.of("MyString");

        final EmbeddedStorageFoundation<?> foundation = EmbeddedStorage.Foundation(tempDir);
        storageManager = foundation.createEmbeddedStorageManager(root).start();

        storageManager.shutdown();

        final EmbeddedStorageFoundation<?> foundationLoad = EmbeddedStorage.Foundation(tempDir);
        storageManager = foundationLoad.createEmbeddedStorageManager(root).start();

        Assertions.assertEquals(1, root.size());

    }

    @Test
    public void immutableMapTest() {

        Map<String,String> root = Map.of("MyString","MyValue");

        final EmbeddedStorageFoundation<?> foundation = EmbeddedStorage.Foundation(tempDir);
        storageManager = foundation.createEmbeddedStorageManager(root).start();

        storageManager.shutdown();

        final EmbeddedStorageFoundation<?> foundationLoad = EmbeddedStorage.Foundation(tempDir);
        storageManager = foundationLoad.createEmbeddedStorageManager(root).start();

        Assertions.assertEquals(1, root.size(), root.toString());

    }

    @Test
    public void immutableListTest() {

        List<String> root = List.of("MyString");

        final EmbeddedStorageFoundation<?> foundation = EmbeddedStorage.Foundation(tempDir);
        storageManager = foundation.createEmbeddedStorageManager(root).start();

        storageManager.shutdown();

        final EmbeddedStorageFoundation<?> foundationLoad = EmbeddedStorage.Foundation(tempDir);
        storageManager = foundationLoad.createEmbeddedStorageManager(root).start();

        Assertions.assertEquals(1, root.size(), root.toString());

    }



    @AfterEach
    public void afterHook() {
        if (storageManager != null && storageManager.isRunning()) {
            storageManager.shutdown();
        }
    }
}
