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

import org.eclipse.serializer.persistence.types.Persistence;
import org.eclipse.serializer.persistence.types.PersistenceRefactoringMappingProvider;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies the foundation accepts a refactoring mapping provider and the
 * wiring survives start/shutdown with an empty domain. No legacy data is
 * involved — this is API-surface only.
 */
class FoundationWiringTest
{
    @Test
    void foundation_accepts_refactoring_provider_and_starts(@TempDir final Path tmp)
    {
        final PersistenceRefactoringMappingProvider provider =
            Persistence.RefactoringMapping(
                "old;current\n",
                ';'
            );
        assertNotNull(provider);

        final AppRoot root = new AppRoot();
        final EmbeddedStorageManager storage = EmbeddedStorage.Foundation(tmp.resolve("storage"))
            .setRefactoringMappingProvider(provider)
            .createEmbeddedStorageManager(root)
            .start();

        try
        {
            assertTrue(storage.isRunning(), "manager must be running after start");
            assertNotNull(storage.root(), "root must be set");
        }
        finally
        {
            storage.shutdown();
        }
    }
}
