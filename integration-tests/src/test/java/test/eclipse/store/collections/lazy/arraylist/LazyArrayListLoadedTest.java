package test.eclipse.store.collections.lazy.arraylist;

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

import org.eclipse.serializer.collections.lazy.LazyArrayList;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import test.eclipse.serializer.fixtures.types.PrimitiveTypes;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class LazyArrayListLoadedTest {

    @TempDir
    Path storageLocation;

    @Test
    public void lazyArraylistLoadTest() throws InterruptedException {

        LazyArrayList<PrimitiveTypes> list = Stream.generate(PrimitiveTypes::new)
                .limit(100_000)
                .collect(Collectors.toCollection(LazyArrayList::new));

        PrimitiveTypes types = new PrimitiveTypes();
        types.fillSampleData();
        list.add(50, types);

        AtomicInteger isLoaded = new AtomicInteger(0);

        try (EmbeddedStorageManager storage = EmbeddedStorage.start(list, storageLocation)) {


            list.iterateLazyReferences(i -> i.clear());

            list.iterateLazyReferences(i -> {
                        if (i.isLoaded()) {
                            isLoaded.incrementAndGet();
                        }
                    }
            );


            list.remove(60);

            list.iterateLazyReferences(i -> {
                        if (i.isLoaded()) {
                            isLoaded.incrementAndGet();
                        }
                    }
            );


            Assertions.assertEquals(1, isLoaded.get());
        }
    }

    private static void printMemory() {
        Long memoryAmount = Runtime.getRuntime()
                .totalMemory() - Runtime.getRuntime()
                .freeMemory();
        //System.out.println(memoryAmount);
    }

}
