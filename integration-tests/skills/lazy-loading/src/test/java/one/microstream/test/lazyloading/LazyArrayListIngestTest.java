package one.microstream.test.lazyloading;

/*-
 * #%L
 * EclipseStore Integration Tests - Skills - lazy-loading
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

import java.nio.file.Path;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import one.microstream.test.lazyloading.events.Event;
import one.microstream.test.lazyloading.events.EventRoot;

/**
 * SKILL Pattern E — {@code LazyArrayList<E>}: segmented list, segments are lazy.
 *
 * <p>Ingest N events, shutdown, reopen, assert {@code size()} is correct
 * immediately (cached, no segment loads per the skill) and a targeted
 * {@code get(index)} returns the expected element.
 */
class LazyArrayListIngestTest extends LazyTestBase
{
    private static final int N = 5_000;

    @Test
    void ingest_then_reopen_size_is_immediate_and_index_lookup_works(@TempDir Path tmp)
    {
        final Path dir = tmp.resolve("storage");

        // session 1: ingest N events
        {
            final EventRoot root = new EventRoot();
            final EmbeddedStorageManager storage = EmbeddedStorage.start(root, dir);
            try
            {
                for (int i = 0; i < N; i++)
                {
                    root.events().add(new Event(i, "payload-" + i));
                }
                // store the lazy collection itself — its segments are the persisted units
                storage.store(root.events());
            }
            finally
            {
                storage.shutdown();
            }
        }

        // session 2: reopen and probe
        {
            final EventRoot root = new EventRoot();
            final EmbeddedStorageManager storage = EmbeddedStorage.start(root, dir);
            try
            {
                // SKILL: size() is cached, free even when no segments are loaded
                assertEquals(N, root.events().size(),
                    "LazyArrayList.size() must report N immediately after reopen");

                // Targeted index lookup — loads only the segment containing index 4242
                final int probeIdx = 4_242;
                final Event probe = root.events().get(probeIdx);
                assertEquals(probeIdx, probe.id());
                assertEquals("payload-" + probeIdx, probe.payload());

                // A second probe on a different segment
                final Event first = root.events().get(0);
                assertEquals(0, first.id());
                assertEquals("payload-0", first.payload());

                final Event last = root.events().get(N - 1);
                assertEquals(N - 1, last.id());
                assertEquals("payload-" + (N - 1), last.payload());
            }
            finally
            {
                storage.shutdown();
            }
        }
    }
}
