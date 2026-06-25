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

import org.eclipse.serializer.reference.Lazy;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class TooManyFilesOpen
{
    @TempDir
    Path storagePath;

    @Test
    @Disabled
    void tooManyFilesOpen()
    {
        ArrayList<Data> root = new ArrayList<>();

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, storagePath)) {
            for (int i = 0; i < 100_000; i++) {
                root.add(new Data("Data " + i));
                storageManager.store(root);
                System.out.println(root.size());
            }

        }

    }


    public static class Data {
        String name;
        Lazy<String> lazy;

        public Data(final String name) {
            super();
            this.name = name;
            this.lazy = Lazy.Reference("Lazy content " + name);
        }
    }
}
