package test.eclipse.store.persister;

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

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * https://docs.microstream.one/manual/storage/customizing/optional-storage-manager-reference-in-entities.html
 */
public class InjectPersisterTest
{

    @TempDir
    Path location;


    @Test
    public void injectPersisterTest()
    {
        MyEntity root = new MyEntity("ahoj", 10);
        EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, location);
        storageManager.storeRoot();

        storageManager.shutdown();

        storageManager = EmbeddedStorage.start(root, location);

        Assertions.assertNotNull(root.getStorage());

        storageManager.shutdown();

    }
}
