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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Disabled("https://github.com/eclipse-serializer/serializer/issues/247")
public class ClazzVoidTest
{

    @TempDir
    Path tempDir;

    @Test
    void clazzVoidTest()
    {
        VoidClazz root = new VoidClazz(void.class);

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(root, tempDir)) {
        }

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
            VoidClazz loaded = (VoidClazz) manager.root();
            assertEquals(loaded.getClazz(), void.class);
        }

    }


    private static class VoidClazz
    {
        Class<Void> clazz;

        public VoidClazz()
        {
        }

        public VoidClazz(Class<Void> clazz)
        {
            this.clazz = clazz;
        }

        public Class getClazz()
        {
            return clazz;
        }

    }
}
