package test.eclipse.store.handler.special;

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

import static org.junit.jupiter.api.Assertions.assertIterableEquals;

import java.nio.file.Path;
import java.util.concurrent.CopyOnWriteArraySet;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class BinaryHandlerCopyOnWriteArraySetUpdate
{

    @TempDir
    Path tempDir;

    @Test
    public void binaryHandlerCopyOnWriteArraySetUpdateTest()
    {

        CopyOnWriteArraySet<Integer> original = new CopyOnWriteArraySet<>();
        original.add(100);
        CopyOnWriteArraySet<Integer> copy = new CopyOnWriteArraySet<>();

        EmbeddedStorageManager storage = EmbeddedStorage.start(original, tempDir);

        storage.storeRoot();
        storage.shutdown();

        storage = EmbeddedStorage.start(original, tempDir);

        original.add(200);
        storage.storeRoot();
        storage.shutdown();

        storage = EmbeddedStorage.start(copy, tempDir);

        assertIterableEquals(original, copy);
        storage.shutdown();


    }
}
