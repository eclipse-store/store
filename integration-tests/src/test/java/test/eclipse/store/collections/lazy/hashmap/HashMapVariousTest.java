package test.eclipse.store.collections.lazy.hashmap;

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

import org.eclipse.serializer.collections.lazy.LazyHashMap;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import test.eclipse.store.library.types.PrimitiveTypes;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HashMapVariousTest {

    @TempDir
    Path location;

    @Test
    void isSegmentModifiedTest() {
        LazyHashMap<Integer, String> map = new LazyHashMap<>();
        map.put(1, "ahoj");

        try (EmbeddedStorageManager storageManager = Util.startStorage(map, location)) {

            map.segments()
                    .forEach(s -> {
                        assertTrue(s.isLoaded());
                        assertFalse(s.isModified());
                    });

            map.put(1, "super");

            map.segments()
                    .forEach(s -> {
                        assertTrue(s.isLoaded());
                        assertTrue(s.isModified());
                    });

        }
    }

    @Test
    void isSegmentLoadedTest() {
        int count = 1;
        LazyHashMap<Integer, PrimitiveTypes> map = generateLazyHashMap(count);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(map, location)) {

            map.segments()
                    .forEach(s -> {
                        assertTrue(s.isLoaded());
                        assertFalse(s.isModified());
                    });

            map.segments()
                    .forEach(LazyHashMap.Segment::unloadSegment);
            map.segments()
                    .forEach(s ->
                            assertFalse(s.isLoaded()));

            map.put(1, null);

            map.segments()
                    .forEach(s -> {
                        assertTrue(s.isLoaded());
                    });

        }
    }

    private static LazyHashMap<Integer, PrimitiveTypes> generateLazyHashMap(int count) {
        LazyHashMap<Integer, PrimitiveTypes> map = new LazyHashMap<>();
        for (int i = 0; i < count; i++) {
            PrimitiveTypes type = new PrimitiveTypes();
            type.fillSampleData();
            map.put(i, type);
        }

        return map;
    }
}
