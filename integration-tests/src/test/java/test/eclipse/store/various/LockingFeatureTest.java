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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.types.Storage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class LockingFeatureTest
{

    @TempDir
    static Path workDir;

    ArrayList<String> data = new ArrayList<>();

    //https://docs.eclipsestore.io/manual/storage/configuration/lock-file.html
    @Test
    void lockingFeature()
    {
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.Foundation(workDir)
                .setLockFileSetupProvider(Storage.LockFileSetupProvider())
                .start(data);
        ) {
            data.add("some data");
            storageManager.store(data);

            Boolean exists = Files.exists(Paths.get(workDir.toString(), "used.lock"));
            assertTrue(exists);

        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.Foundation(workDir)
                .setLockFileSetupProvider(Storage.LockFileSetupProvider())
                .start(data);
        ) {
            data.add("some more data");
            storageManager.store(data);
            Boolean exists = Files.exists(Paths.get(workDir.toString(), "used.lock"));
            assertTrue(exists);
        }

        //Question: Should the file stands there?
//        Boolean b = Files.exists(Paths.get(workDir.toString(), "used.lock"));
//        assertFalse(b);
    }

}
