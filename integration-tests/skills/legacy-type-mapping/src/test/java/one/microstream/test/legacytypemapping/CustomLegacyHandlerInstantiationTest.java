package one.microstream.test.legacytypemapping;

/*-
 * #%L
 * EclipseStore Integration Tests - Skills - legacy-type-mapping
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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import one.microstream.test.legacytypemapping.place.LegacyTypeHandlerNicePlace;

/**
 * Verifies that a {@code BinaryLegacyTypeHandler.AbstractCustom<NicePlace>} can
 * be instantiated and registered on the foundation via
 * {@code onConnectionFoundation(f -> f.getCustomTypeHandlerRegistry().registerLegacyTypeHandler(...))}.
 *
 * Because no legacy {@code NicePlace} binaries exist in storage, the handler does
 * not actually fire. The point is to prove the API surface compiles and accepts
 * the registration.
 */
class CustomLegacyHandlerInstantiationTest
{
    @Test
    void register_legacy_handler_and_start(@TempDir final Path tmp)
    {
        final LegacyTypeHandlerNicePlace handler = new LegacyTypeHandlerNicePlace();
        assertNotNull(handler);

        final AppRoot root = new AppRoot();
        final EmbeddedStorageManager storage = EmbeddedStorage.Foundation(tmp.resolve("storage"))
            .onConnectionFoundation(f ->
                f.getCustomTypeHandlerRegistry().registerLegacyTypeHandler(handler)
            )
            .createEmbeddedStorageManager(root)
            .start();

        try
        {
            assertTrue(storage.isRunning(),
                "manager must be running after registering a custom legacy type handler");
        }
        finally
        {
            storage.shutdown();
        }
    }
}
