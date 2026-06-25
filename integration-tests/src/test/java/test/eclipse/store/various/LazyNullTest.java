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

import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Path;

import org.eclipse.serializer.reference.Lazy;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class LazyNullTest
{
    @TempDir
    Path tempDir;

    @Test
    void lazyNullTest()
    {
        Root root = new Root();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            assertNull(root.getLazyString().get());
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(tempDir)) {
            root = (Root) storageManager.root();
            assertNull(root.getLazyString().get());
        }

    }

    private static class Root {
        private Lazy<String> lazyString = Lazy.Reference(null);

        public Lazy<String> getLazyString()
        {
            return lazyString;
        }

    }
}
