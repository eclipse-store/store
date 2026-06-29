package test.eclipse.store.handler;

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
import java.util.PriorityQueue;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class BinaryHandlerPriorityQueueTest
{

    @TempDir
    Path storagePath;

    /**
     * test for issue: https://github.com/microstream-one/microstream-private/issues/580
     * Load empty PriorityQueue
     */
    @Test
    public void saveAndLoadTest()
    {

        PriorityQueue<Integer> queue = new PriorityQueue<>();
        PriorityQueue<Integer> copy = new PriorityQueue<>();

        EmbeddedStorageManager storageManager = EmbeddedStorage.start(queue, storagePath);
        storageManager.shutdown();

        storageManager = EmbeddedStorage.start(copy, storagePath);

        storageManager.shutdown();
    }
}
