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
import java.util.List;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageFoundation;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.exceptions.StorageExceptionInitialization;
import org.eclipse.store.storage.types.StorageWriteControllerReadOnlyMode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Experimental verification of microstream-one/internal#25:
 * a second, read-only EmbeddedStorageManager on the same storage directory
 * within the same JVM while the first (writing) manager is still running.
 */
public class ReadOnlyConcurrentIssue25ExperimentTest
{
    @TempDir
    Path workDir;

    /**
     * The exact scenario from the issue: second foundation on the same directory
     * with the default database name. Expected to still fail with
     * "Active storage ... already exists" (Database.guaranteeNoActiveStorage).
     */
    @Test
    void originalRepro_secondManagerWhileFirstRunning_stillThrows()
    {
        List<String> root = new ArrayList<>(List.of("a", "b", "c"));

        try (EmbeddedStorageManager writer = EmbeddedStorage.start(root, workDir)) {
            EmbeddedStorageFoundation<?> foundation = EmbeddedStorage.Foundation(workDir);
            StorageWriteControllerReadOnlyMode writeController =
                    new StorageWriteControllerReadOnlyMode(foundation.getWriteController());
            writeController.setReadOnly(true);
            foundation.setWriteController(writeController);

            StorageExceptionInitialization e = Assertions.assertThrows(
                    StorageExceptionInitialization.class,
                    () -> foundation.setRoot(new ArrayList<String>()).createEmbeddedStorageManager()
            );
            Assertions.assertTrue(e.getMessage().contains("already exists"), e.getMessage());
        }
    }

    /**
     * Same scenario, but the second foundation gets its own database name,
     * which bypasses the Databases-registry guard. The read-only manager
     * should start next to the running writer, read the data and reject writes.
     */
    @Test
    void setDataBaseName_bypassesGuard_readOnlyManagerReadsLiveStorage()
    {
        List<String> root = new ArrayList<>(List.of("a", "b", "c"));

        try (EmbeddedStorageManager writer = EmbeddedStorage.start(root, workDir)) {
            EmbeddedStorageFoundation<?> foundation = EmbeddedStorage.Foundation(workDir);
            StorageWriteControllerReadOnlyMode writeController =
                    new StorageWriteControllerReadOnlyMode(foundation.getWriteController());
            writeController.setReadOnly(true);
            foundation.setWriteController(writeController);
            foundation.setDataBaseName("read-only-experiment");

            try (EmbeddedStorageManager readOnly =
                         foundation.setRoot(new ArrayList<String>()).createEmbeddedStorageManager().start()) {
                Assertions.assertTrue(writer.isRunning());
                Assertions.assertTrue(readOnly.isRunning());

                @SuppressWarnings("unchecked")
                List<String> loaded = (List<String>) readOnly.root();
                Assertions.assertEquals(List.of("a", "b", "c"), loaded);

                RuntimeException writeRejected = Assertions.assertThrows(
                        RuntimeException.class,
                        () -> readOnly.store(loaded)
                );
                System.out.println("[experiment] write on read-only manager rejected with: "
                        + writeRejected.getClass().getName() + ": " + writeRejected.getMessage());
            }

            // writer must stay alive and writable after the read-only manager is closed
            Assertions.assertTrue(writer.isRunning());
            root.add("d");
            writer.store(root);
        }
    }
}
