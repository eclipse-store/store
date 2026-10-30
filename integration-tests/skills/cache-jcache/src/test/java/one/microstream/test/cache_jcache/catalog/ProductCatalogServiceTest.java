package one.microstream.test.cache_jcache.catalog;

/*-
 * #%L
 * EclipseStore Integration Tests - Skills - cache-jcache
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
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProductCatalogServiceTest
{
    @Test
    void cache_miss_then_hit_then_evict(@TempDir final Path tmp) throws IOException
    {
        final Path storageDir = tmp.resolve("storage");
        Files.createDirectories(storageDir);

        final Map<String, Product> db = ProductCatalogService.seedDatabase();

        try (ProductCatalogService svc = new ProductCatalogService(storageDir, db))
        {
            // 1. first read: miss
            final Product first = svc.findBySku("SKU-1");
            assertNotNull(first, "DB has SKU-1");
            assertEquals(0L, svc.cacheHits(),   "no hits yet");
            assertEquals(1L, svc.cacheMisses(), "one miss after first read");

            // 2. second read: hit
            final Product second = svc.findBySku("SKU-1");
            assertNotNull(second);
            assertEquals(first, second);
            assertEquals(1L, svc.cacheHits(),   "second read is a hit");
            assertEquals(1L, svc.cacheMisses(), "miss count unchanged");

            // 3. evict, third read: miss again
            svc.evict("SKU-1");
            final Product third = svc.findBySku("SKU-1");
            assertNotNull(third);
            assertEquals(1L, svc.cacheHits(),   "still one hit");
            assertEquals(2L, svc.cacheMisses(), "miss after evict + read");
        }
    }

    @Test
    void cached_entries_survive_jvm_restart(@TempDir final Path tmp) throws IOException
    {
        final Path storageDir = tmp.resolve("storage");
        Files.createDirectories(storageDir);

        final Map<String, Product> db = ProductCatalogService.seedDatabase();

        // ---- session 1: populate cache ----
        try (ProductCatalogService svc = new ProductCatalogService(storageDir, db))
        {
            svc.findBySku("SKU-1");
            svc.findBySku("SKU-2");
            svc.findBySku("SKU-3");
            assertEquals(3L, svc.cacheMisses(), "3 misses on first reads");
            assertEquals(3L, svc.dbLookups(),   "3 DB lookups");
        }

        // ---- session 2: restart with an EMPTY database; entries must come from storage ----
        final Map<String, Product> emptyDb = new java.util.HashMap<>();
        try (ProductCatalogService svc = new ProductCatalogService(storageDir, emptyDb))
        {
            final Product p1 = svc.findBySku("SKU-1");
            final Product p2 = svc.findBySku("SKU-2");
            final Product p3 = svc.findBySku("SKU-3");

            // Storage-backed cache must have served these without touching the (empty) DB.
            assertNotNull(p1, "SKU-1 must survive restart");
            assertNotNull(p2, "SKU-2 must survive restart");
            assertNotNull(p3, "SKU-3 must survive restart");

            assertEquals(db.get("SKU-1"), p1);
            assertEquals(db.get("SKU-2"), p2);
            assertEquals(db.get("SKU-3"), p3);

            assertEquals(0L, svc.dbLookups(),
                "no DB lookups after restart — all served from storage-backed cache");

            // The standard semantics: a fresh CacheManager session reports a fresh stats counter,
            // so these were 3 hits (loaded from storage on get), 0 misses.
            // We do NOT assert exact hit numbers here because how a storage-backed cache reports
            // a load-from-storage as hit vs. miss is implementation-defined; what matters is that
            // the DB was not touched.

            // Entry that does NOT exist anywhere is a miss and returns null.
            final Product missing = svc.findBySku("SKU-DOES-NOT-EXIST");
            assertNull(missing);
        }
    }
}
