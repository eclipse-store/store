package test.eclipse.store.various.restart;

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

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Lifecycle / state-guard behaviour of the now-{@code synchronized} {@code start()} / {@code shutdown()}.
 * Verifies {@code isRunning()} tracks each transition, shutdown reports success, repeated transitions
 * are safe, and operations against a shut-down storage fail rather than silently corrupting state.
 */
public class LifecycleRestartTest
{
    @Test
    @Timeout(30)
    public void isRunning_tracksTransitions(@TempDir final Path dir)
    {
        final EmbeddedStorageManager storage = EmbeddedStorage.start(dir);
        assertTrue(storage.isRunning(), "running after initial start");

        final boolean shutdownResult = storage.shutdown();
        assertTrue(shutdownResult, "successful shutdown must report true");
        assertFalse(storage.isRunning(), "not running after shutdown");

        storage.start();
        assertTrue(storage.isRunning(), "running again after restart");

        assertTrue(storage.shutdown());
        assertFalse(storage.isRunning());
    }

    @Test
    @Timeout(30)
    public void doubleShutdown_isSafe(@TempDir final Path dir)
    {
        final EmbeddedStorageManager storage = EmbeddedStorage.start(dir);
        assertTrue(storage.shutdown());
        // a second shutdown on an already-stopped storage must not throw or change the stopped state
        assertDoesNotThrow(storage::shutdown);
        assertFalse(storage.isRunning());
    }

    @Test
    @Timeout(30)
    public void startWhenAlreadyRunning_rejectedAndStaysRunning(@TempDir final Path dir)
    {
        final EmbeddedStorageManager storage = EmbeddedStorage.start(dir);
        assertTrue(storage.isRunning());
        // starting an already-running storage is rejected rather than silently re-initializing
        assertThrows(Exception.class, storage::start, "start on a running storage must be rejected");
        // the rejected start must leave the running storage intact
        assertTrue(storage.isRunning(), "storage must stay running after a rejected start");
        assertDoesNotThrow(storage::shutdown);
    }

    @Test
    @Timeout(30)
    public void storeAfterShutdown_fails(@TempDir final Path dir)
    {
        final List<String> root = new ArrayList<>();
        root.add("alpha");
        final EmbeddedStorageManager storage = EmbeddedStorage.start(root, dir);
        storage.storeRoot();
        storage.shutdown();

        // storing against a shut-down storage must fail loudly, not silently swallow the write
        assertThrows(Exception.class, () -> storage.store(root),
                "store on a shut-down storage must throw");

        // recovery: a restart must still work and preserve the pre-shutdown data
        storage.start();
        final List<String> reloaded = storage.root();
        assertEquals(1, reloaded.size());
        assertEquals("alpha", reloaded.get(0));
        storage.shutdown();
    }
}
