package test.eclipse.store.various;

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
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;

public class BigListTest {

    @TempDir
    Path tempDir;


    @Test
    void bigListSaveTest() {
        int n = 1_000_000;

        List<String> list = IntStream.range(0, n)
                .parallel()
                .mapToObj(i -> {
                    StringBuilder sb = new StringBuilder();
                    int length = ThreadLocalRandom.current().nextInt(10) + 1;
                    for (int j = 0; j < length; j++) {
                        char c = (char) (ThreadLocalRandom.current().nextInt(26) + 'a');
                        sb.append(c);
                    }
                    return sb.toString();
                })
                .collect(Collectors.toList());

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(list, tempDir)) {

        }
        List<String> loaded = new ArrayList<>();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded, tempDir )) {

        }
        Assertions.assertIterableEquals(list, loaded);
    }
}
