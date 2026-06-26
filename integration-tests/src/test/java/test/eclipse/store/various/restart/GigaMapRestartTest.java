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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Path;

import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Restart coverage for a {@link GigaMap} held as the storage root, exercised across
 * shutdown/start cycles on the same {@link EmbeddedStorageManager} instance.
 */
public class GigaMapRestartTest
{
    private static final int CYCLES = 10;
    private static final int PER_CYCLE = 5;

    @Test
    public void gigaMapRoot_growsAcrossCycles_noDataLoss(@TempDir final Path dir)
    {
        final EmbeddedStorageManager storage = EmbeddedStorage.start(dir);
        final GigaMap<String> gigaMap = storage.ensureRoot(GigaMap::New);

        for (int cycle = 0; cycle < CYCLES; cycle++) {
            final GigaMap<String> live = storage.root();
            assertEquals((long) cycle * PER_CYCLE, live.size(), "size at start of cycle " + cycle);

            for (int j = 0; j < PER_CYCLE; j++) {
                live.add("c" + cycle + "-e" + j);
            }
            live.store();
            storage.shutdown();

            storage.start();

            final GigaMap<String> reloaded = storage.root();
            assertNotNull(reloaded);
            assertEquals((long) (cycle + 1) * PER_CYCLE, reloaded.size(),
                    "size after restart in cycle " + cycle);
        }

        storage.shutdown();

        // independent verification from a fresh manager
        final EmbeddedStorageManager verifier = EmbeddedStorage.start(dir);
        final GigaMap<String> fromDisk = verifier.root();
        assertEquals((long) CYCLES * PER_CYCLE, fromDisk.size(), "all entries persisted to disk");
        assertEquals("c0-e0", fromDisk.get(0));
        assertEquals("c" + (CYCLES - 1) + "-e" + (PER_CYCLE - 1),
                fromDisk.get((long) CYCLES * PER_CYCLE - 1), "last entry on disk");
        verifier.shutdown();
    }
}
