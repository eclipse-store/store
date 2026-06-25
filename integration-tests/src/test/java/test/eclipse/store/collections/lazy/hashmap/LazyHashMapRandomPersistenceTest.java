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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Random;

public class LazyHashMapRandomPersistenceTest extends LazyHashMapRandomTest {

    private final int cycles = 100;

    @SuppressWarnings("unchecked")
    @Test
    @Disabled("This test takes to long to run.")
    void randomPersistenceTest(@TempDir final Path path) {

        this.rnd = new Random(System.nanoTime());

        this.lazyMap = new LazyHashMap<>(13);
        try (EmbeddedStorageManager storage = EmbeddedStorage.start(lazyMap, path)) {

        }

        for (int i = 0; i < this.cycles; i++) {
            try (EmbeddedStorageManager storage = EmbeddedStorage.start(path)) {
                this.lazyMap = (LazyHashMap<String, String>) storage.root();
                this.compare();
                this.randomAction();
                storage.store(this.lazyMap);
            }

            this.lazyMap = null;
        }
    }

}
