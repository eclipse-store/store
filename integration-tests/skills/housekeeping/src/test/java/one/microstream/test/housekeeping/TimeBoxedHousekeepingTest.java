package one.microstream.test.housekeeping;

/*-
 * #%L
 * EclipseStore Integration Tests - Skills - housekeeping
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Duration;

import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exercises SKILL Pattern H — time-boxed manual housekeeping. Distinguishes the
 * two return-value contracts the skill calls out:
 *   - issueGarbageCollection / issueFileCheck → true iff work completed in budget
 *   - issueCacheCheck                         → true iff used cache size is/became 0
 */
class TimeBoxedHousekeepingTest
{
    @Test
    void patternH_completion_flags_and_cache_size_semantics(@TempDir Path tmp)
    {
        Path storageDir = tmp.resolve("storage");

        OrdersRoot root = new OrdersRoot();
        EmbeddedStorageManager storage = OrdersStorage.start(root, storageDir);
        try
        {
            for (int i = 0; i < 20; i++)
            {
                root.orders().add(new Order("o-" + i, "c-" + i, i));
            }
            storage.store(root.orders());

            long budget = Duration.ofSeconds(5).toNanos();

            boolean gcDone     = storage.issueGarbageCollection(budget);
            boolean fileDone   = storage.issueFileCheck(budget);
            boolean cacheEmpty = storage.issueCacheCheck(budget);

            assertTrue(gcDone,
                "issueGarbageCollection(5s) should complete for a tiny graph");
            assertTrue(fileDone,
                "issueFileCheck(5s) should complete for a tiny graph");
            // Per SKILL.md / api-catalogue: issueCacheCheck returns true iff the
            // used cache size is or became 0. Recently-loaded entities with the
            // default 1-day timeout are still cached → expect false.
            assertFalse(cacheEmpty,
                "issueCacheCheck must return false on a non-empty live cache "
                + "(it reports cache-size-is-zero, not completion)");
        }
        finally
        {
            storage.shutdown();
        }
    }
}
