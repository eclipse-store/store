package test.eclipse.store.customtypehandler;

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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.file.Path;

import org.eclipse.serializer.persistence.binary.jdk8.types.BinaryHandlersJDK8;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageFoundation;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CustomBufferedImageHandlerTest {

    @TempDir
    Path location;

    private MyRoot myRoot = new MyRoot();

    @Test
    void customBufferedImageHandlerTest() {
        try (EmbeddedStorageManager storage = EmbeddedStorage
                .Foundation(location)
                .onConnectionFoundation(f -> f.registerCustomTypeHandlers(new CustomBufferedImageHandler()))
                .start(myRoot)
        ) {
            // no op
        }
    }

    //https://github.com/eclipse-store/store/issues/204
    @Test
    void initializationTest()
    {
        EmbeddedStorageFoundation<?> foundation = EmbeddedStorage.Foundation(location);

        try (EmbeddedStorageManager storage = foundation
                .onConnectionFoundation(BinaryHandlersJDK8::registerJDK8TypeHandlers)
                .onConnectionFoundation(f -> f.registerCustomTypeHandlers(new CustomBufferedImageHandler()))
                .start(myRoot)) {
            // no op

            assertTrue(CustomBufferedImageHandler.stored);

        }

    }

    public static class MyRoot {
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);

        public BufferedImage getImage() {
            return image;
        }
    }
}
