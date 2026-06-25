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

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.HashMap;

import org.eclipse.serializer.collections.lazy.LazyHashMap;
import org.eclipse.serializer.reference.Lazy;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class TwoStoragesTest {

    @TempDir
    Path location;

    private static PrintStream originalErr;

    @BeforeAll
    static void setupErr()
    {
        originalErr = System.err;
        System.setErr(new PrintStream(new OutputStream()
        {
            @Override
            public void write(int b)
            {
                // Discard output
            }
        }));
    }

    @AfterAll
    static void restoreErr()
    {
        System.setErr(originalErr);
    }

    @Test
    public void hashMapkeySetRemoveTest(@TempDir final Path path) {
        final HashMap<Integer, String> map = new HashMap<>();

        map.put(101, "some text");

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(map, path)) {
            //no op
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(map, path)) {

            map.keySet()
                    .remove(101);
        }
    }

    @Test
    public void keySetRemoveTest(@TempDir final Path path) {
        LazyHashMap<Integer, String> map = new LazyHashMap<>();

        map.put(101, "some text");

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(map, path)) {
            //no op
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(map, path)) {

            map.keySet()
                    .remove(101);
        }
    }

    @Test
    public void saveSecondTimeTest(@TempDir final Path secondLocation) {
        LazyHashMap<Integer, String> map = generateMap(11);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(map, this.location)) {
            //map.segments().forEach(s -> s.unloadSegment());
        }


        assertThrows(IllegalStateException.class, () ->
                EmbeddedStorage.start(map, secondLocation));


    }

    public static class MyRoot {
        Lazy<String> lazy;

        public MyRoot(final String content) {
            super();
            this.lazy = Lazy.Reference(content);
        }

    }

    public static LazyHashMap<Integer, String> generateMap(final Integer count) {
        return generateMap(count, 0);
    }

    public static LazyHashMap<Integer, String> generateMap(final Integer count, final int keyStart) {

        LazyHashMap<Integer, String> map = new LazyHashMap<>();

        for (int i = 0; i < count; i++) {
            map.put(i + keyStart, "Hello World " + i);
        }

        return map;
    }

}
