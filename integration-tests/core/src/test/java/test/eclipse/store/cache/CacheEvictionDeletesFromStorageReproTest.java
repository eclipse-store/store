package test.eclipse.store.cache;

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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.spi.CachingProvider;

import org.eclipse.serializer.reference.LazyReferenceManager;
import org.eclipse.store.cache.types.CacheConfiguration;
import org.eclipse.store.cache.types.EvictionManager;
import org.eclipse.store.cache.types.EvictionPolicy;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Regression test for internal issue #96: a storage-backed JCache with a size-based eviction
 * policy must not delete entries from the backing {@code EmbeddedStorage} on mere heap eviction.
 * <p>
 * Eviction only frees heap space. Per the JSR-107 contract, eviction - like expiry - is a
 * cache-internal removal and must not invoke {@code CacheWriter.delete}; only application-driven
 * remove operations do. An evicted entry therefore stays durable in the backing store and is
 * reloaded via read-through on the next {@code get()}. Application {@code remove()}, in
 * contrast, must still delete the entry from the backing store.
 */
public class CacheEvictionDeletesFromStorageReproTest
{
    private static final int MAX_CACHE_SIZE = 3;
    private static final int ENTRY_COUNT    = MAX_CACHE_SIZE + 1;

    @Test
    @Timeout(60)
    void sizeBasedEvictionMustNotDeleteFromBackingStorage(@TempDir final Path tempdir)
    {
        final EmbeddedStorageManager storageManager = EmbeddedStorage.start(tempdir);

        final CachingProvider provider     = Caching.getCachingProvider();
        final CacheManager    cacheManager = provider.getCacheManager();

        final CacheConfiguration<Integer, String> configuration = CacheConfiguration
            .Builder(Integer.class, String.class, "internal96EvictCache", storageManager)
            // bounded heap working set + full durable backing store
            .evictionManagerFactory(() ->
                EvictionManager.OnEntryCreation(EvictionPolicy.LeastRecentlyUsed(MAX_CACHE_SIZE)))
            .build();

        final Cache<Integer, String> cache =
            cacheManager.createCache("internal96EvictCache", configuration);
        try
        {
            // Put more entries than the cap so the eviction policy must evict from the heap.
            for(int i = 0; i < ENTRY_COUNT; i++)
            {
                cache.put(i, "value-" + i);
            }

            // Determine the actual heap contents. Per JSR-107, containsKey never calls the
            // CacheLoader, so it probes the heap table only: keys missing here were evicted.
            // (The cache iterator is no probe - for storage-backed caches it walks the store.)
            final Set<Integer> heapKeys = new HashSet<>();
            for(int i = 0; i < ENTRY_COUNT; i++)
            {
                if(cache.containsKey(i))
                {
                    heapKeys.add(i);
                }
            }
            assertFalse(heapKeys.size() >= ENTRY_COUNT,
                "test setup: no entry was evicted, the eviction path was not exercised");

            // Every entry must still be retrievable: heap survivors directly, evicted entries
            // durably from the backing store via read-through. On the bug, eviction called
            // CacheWriter.delete and the evicted entries' get() returns null - durable value lost.
            for(int i = 0; i < ENTRY_COUNT; i++)
            {
                assertEquals("value-" + i, cache.get(i),
                    heapKeys.contains(i)
                        ? "heap-resident entry lost"
                        : "heap-evicted entry was deleted from the backing storage");
            }

            // Inverse contract: an application-driven remove must still delete durably.
            cache.remove(0);
            assertNull(cache.get(0), "remove() must delete the entry from the backing store");
        }
        finally
        {
            cacheManager.close();
            storageManager.close();
            LazyReferenceManager.get().stop();
        }
    }
}
