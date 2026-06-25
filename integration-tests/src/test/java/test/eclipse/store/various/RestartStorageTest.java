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

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.types.StorageEntityCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import test.eclipse.serializer.fixtures.TypeRegister;

import java.nio.file.Path;

public class RestartStorageTest {

    @TempDir
    Path location;

    @TempDir
    Path storageLocation;
    private EmbeddedStorageManager manager;

    @Test
    public void restartTest() throws InterruptedException {
        StorageEntityCache.Default.setGarbageCollectionEnabled(true);
        TypeRegister register = new TypeRegister();
        register.fillSampleDate();
        manager = EmbeddedStorage.start(register, storageLocation);
        manager.store(register);
        manager.shutdown();

        manager = EmbeddedStorage.start(register, storageLocation);
        manager.store(register);
        manager.shutdown();

    }
}
