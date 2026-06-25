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

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Path;

import org.eclipse.serializer.collections.lazy.LazyArrayList;
import org.eclipse.serializer.persistence.exceptions.PersistenceException;
import org.eclipse.serializer.reference.Lazy;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

//@Disabled("Will not work beacuse auf Lazy.Default also does not support this")
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
    public void removeTest(@TempDir final Path path) {
        LazyArrayList<String> list = new LazyArrayList<>();

        list.add("some text");

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(list, path)) {
            //no op
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(list, path)) {

            list.remove(0);
        }
    }


    public static class MyRoot {
        Lazy<String> lazy;

        public MyRoot(final String content) {
            super();
            this.lazy = Lazy.Reference(content);
        }

    }

    @Test
    public void saveDefaultLazySecondTimeTest(@TempDir final Path secondLocation) {
        final MyRoot myRoot = new MyRoot("Hello World");

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(myRoot, this.location)) {
            //no op
        }

        myRoot.lazy.clear();

        assertThrows(PersistenceException.class, () -> EmbeddedStorage.start(myRoot, secondLocation));

    }

    public static LazyArrayList<String> generateList(final Integer count) {
        return generateList(count, 0);
    }

    public static LazyArrayList<String> generateList(final Integer count, final int keyStart) {

        LazyArrayList<String> list = new LazyArrayList<>();

        for (int i = 0; i < count; i++) {
            list.add("Hello World " + i);
        }

        return list;
    }

}
