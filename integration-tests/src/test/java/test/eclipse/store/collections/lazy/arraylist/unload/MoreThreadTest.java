package test.eclipse.store.collections.lazy.arraylist.unload;

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
import java.util.stream.Collectors;

import org.eclipse.serializer.collections.lazy.LazyArrayList;
import org.eclipse.serializer.collections.lazy.LazySegmentUnloader;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;


public class MoreThreadTest
{

    @TempDir
    Path location;

    private EmbeddedStorageManager storageManager;

    @AfterEach
    void stopStorage()
    {
        if (storageManager != null) {
            if (storageManager.isActive()) {
                storageManager.shutdown();
            }
        }
    }

    @Test
    void manual_unloader_test_tryUnload_false() throws InterruptedException
    {
        LazyArrayList<Person> personList;
        personList = (LazyArrayList<Person>) Generator.generatePersons(20_000, new LazySegmentUnloader.Default());

        List<Person> copyPersons = new ArrayList<>(personList);

        storageManager = EmbeddedStorage.start(personList, location);

        Assertions.assertIterableEquals(copyPersons, personList);

        int threadCount = 5;
        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            Thread thread = new Thread(() -> {
                personList.parallelStream()
                        .map(p -> p.toString())
                        .collect(Collectors.toList());
                Assertions.assertIterableEquals(copyPersons, personList);
            });
            threads[i] = thread;
            thread.start();
        }

        for (int i = 0; i < threadCount; i++) {
            threads[i].join();
        }
    }
}
