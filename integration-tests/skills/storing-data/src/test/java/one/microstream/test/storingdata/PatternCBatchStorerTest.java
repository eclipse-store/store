package one.microstream.test.storingdata;

/*-
 * #%L
 * EclipseStore Integration Tests - Skills - storing-data
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
import java.time.Duration;

import org.eclipse.serializer.persistence.types.BatchStorer;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Pattern C — {@link BatchStorer} for ingest loops.
 * <p>
 * BatchStorer IS AutoCloseable — try-with-resources is correct here
 * (in contrast to {@code Storer}).
 */
class PatternCBatchStorerTest
{
    @Test
    void thousand_orders_persist_via_batch_storer(@TempDir Path tmp)
    {
        final Path dir = tmp.resolve("db");

        {
            final AppRoot root = new AppRoot();
            final EmbeddedStorageManager storage = EmbeddedStorage.start(root, dir);
            try
            {
                try (BatchStorer batch = storage.batchStorerBuilder()
                    .maxSize(100L)
                    .flushCycle(Duration.ofSeconds(1))
                    .build())
                {
                    for (int i = 0; i < 1000; i++)
                    {
                        final Order o = new Order("o-" + i, "x@acme.com");
                        root.orders().add(o);
                        batch.store(root.orders());
                    }
                    batch.commit();
                }
            }
            finally
            {
                storage.shutdown();
            }
        }

        {
            final AppRoot root = new AppRoot();
            final EmbeddedStorageManager storage = EmbeddedStorage.start(root, dir);
            try
            {
                assertEquals(1000, root.orders().size());
                assertEquals("o-0",   root.orders().get(0).id());
                assertEquals("o-999", root.orders().get(999).id());
            }
            finally
            {
                storage.shutdown();
            }
        }
    }
}
