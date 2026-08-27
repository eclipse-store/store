package test.eclipse.store.various.storer;

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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;

import org.eclipse.serializer.persistence.types.BatchStorer;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class BatchStorerTest
{
    @TempDir
    Path tempDir;


    @Test
    void bathStorerBasicTest() throws InterruptedException
    {

        ArrayList<String> data = new ArrayList<>();

        try (EmbeddedStorageManager storage = EmbeddedStorage.start(data, tempDir)) {
            try (BatchStorer storer = storage.createBatchStorer(
                    BatchStorer.Controller(Duration.ofMillis(500)),
                    Duration.ofMillis(200)
            )) {
                for (int i = 0; i < 1_000; i++) {
                    String s = "String " + i;
                    data.add(s);
                    storer.store(data);
//              XThreads.sleep(10);
                }
                //storer.commit();
            }
        }

        try (EmbeddedStorageManager storage = EmbeddedStorage.start(tempDir)) {
            ArrayList<String> list = storage.root();

            System.out.println("List size: " + list.size());
            System.out.println("Last Element: " + list.get(list.size() - 1));
            assertEquals(1000, list.size());
            assertEquals("String 999", list.get(list.size() - 1));
        }

    }
}
