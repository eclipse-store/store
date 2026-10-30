package one.microstream.test.gettingstarted.todo;

/*-
 * #%L
 * EclipseStore Integration Tests - Skills - getting-started
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TodoBootstrapTest
{
    @Test
    void twoArgStart_persists_across_restart(@TempDir Path tmp)
    {
        Path storageDir = tmp.resolve("storage");

        // session 1: seed
        TodoRoot root1 = new TodoRoot();
        EmbeddedStorageManager m1 = TodoStorage.startTwoArg(root1, storageDir);
        root1.todos().add(new Todo("write skill review", false));
        m1.store(root1.todos());
        m1.shutdown();

        // session 2: reopen and assert
        TodoRoot root2 = new TodoRoot();
        EmbeddedStorageManager m2 = TodoStorage.startTwoArg(root2, storageDir);
        try
        {
            assertEquals(1, root2.todos().size(),
                "expected 1 persisted todo after restart");
            Todo loaded = root2.todos().get(0);
            assertEquals("write skill review", loaded.title());
            assertFalse(loaded.done());
        }
        finally
        {
            m2.shutdown();
        }
    }

    @Test
    void foundation_persists_across_restart(@TempDir Path tmp) throws IOException
    {
        Path storageDir = tmp.resolve("storage");

        TodoRoot root1 = new TodoRoot();
        EmbeddedStorageManager m1 = TodoStorage.startFoundation(root1, storageDir);
        try
        {
            root1.todos().add(new Todo("foundation pattern", true));
            m1.store(root1.todos());

            // channelCount=2 must produce channel_0/ and channel_1/ on disk
            assertTrue(Files.isDirectory(storageDir.resolve("channel_0")),
                "channel_0 directory missing on disk");
            assertTrue(Files.isDirectory(storageDir.resolve("channel_1")),
                "channel_1 directory missing on disk");
            assertFalse(Files.exists(storageDir.resolve("channel_2")),
                "channel_2 should not exist for channelCount=2");
        }
        finally
        {
            m1.shutdown();
        }

        TodoRoot root2 = new TodoRoot();
        EmbeddedStorageManager m2 = TodoStorage.startFoundation(root2, storageDir);
        try
        {
            assertEquals(1, root2.todos().size());
            Todo loaded = root2.todos().get(0);
            assertEquals("foundation pattern", loaded.title());
            assertTrue(loaded.done());
        }
        finally
        {
            m2.shutdown();
        }
    }

    @Test
    void ensureRoot_supplier_invoked_only_on_fresh_db(@TempDir Path tmp)
    {
        Path storageDir = tmp.resolve("storage");

        // session 1: fresh DB — supplier MUST be invoked
        AtomicInteger calls1 = new AtomicInteger();
        EmbeddedStorageManager m1 = TodoStorage.startEnsureRoot(() -> {
            calls1.incrementAndGet();
            return new TodoRoot();
        }, storageDir);
        try
        {
            assertEquals(1, calls1.get(),
                "supplier must be invoked on fresh DB");
            TodoRoot root = (TodoRoot) m1.root();
            root.todos().add(new Todo("ensure root", false));
            m1.store(root.todos());
        }
        finally
        {
            m1.shutdown();
        }

        // session 2: populated DB — supplier MUST NOT be invoked
        AtomicInteger calls2 = new AtomicInteger();
        EmbeddedStorageManager m2 = TodoStorage.startEnsureRoot(() -> {
            calls2.incrementAndGet();
            return new TodoRoot();
        }, storageDir);
        try
        {
            assertEquals(0, calls2.get(),
                "supplier must NOT be invoked when a root already exists");
            TodoRoot root = (TodoRoot) m2.root();
            assertEquals(1, root.todos().size());
            assertEquals("ensure root", root.todos().get(0).title());
        }
        finally
        {
            m2.shutdown();
        }
    }
}
