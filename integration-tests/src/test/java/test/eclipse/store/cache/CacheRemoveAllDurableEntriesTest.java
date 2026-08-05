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
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.configuration.MutableCacheEntryListenerConfiguration;
import javax.cache.event.CacheEntryRemovedListener;
import javax.cache.integration.CacheWriter;
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
 * removeAll() must remove ALL mappings of the cache, including entries that were evicted
 * from the heap but remain durable in a backing CacheStore. Per JSR-107, for every mapping
 * that exists, removeAll() calls the registered CacheEntryRemovedListeners and, for a
 * write-through cache, the CacheWriter. The purge must not depend on whether a
 * removed-listener is registered (old values are loaded only for listeners).
 * <p>
 * For a generic (non-CacheStore) CacheWriter the external resource is not owned by the
 * cache: removeAll() must pass only the mappings the cache holds, never enumerate the
 * external resource.
 */
public class CacheRemoveAllDurableEntriesTest
{
    private static final long MAX_CACHE_SIZE = 3;
    private static final int  ENTRY_COUNT    = (int)MAX_CACHE_SIZE + 1;

    @Test
    @Timeout(60)
    void removeAllDeletesEvictedEntriesDurably(@TempDir final Path tempdir)
    {
        EmbeddedStorageManager storageManager = EmbeddedStorage.start(tempdir);

        final CachingProvider provider     = Caching.getCachingProvider();
        CacheManager          cacheManager = provider.getCacheManager();

        Cache<Integer, String> cache =
            createStorageBackedLruCache(cacheManager, storageManager, "removeAllCache");
        try
        {
            putUntilEvicted(cache);

            // Registered after the puts, so it observes only the removeAll() events.
            final Map<Integer, String> removedEvents = new HashMap<>();
            final CacheEntryRemovedListener<Integer, String> listener = events ->
                events.forEach(event -> removedEvents.put(event.getKey(), event.getOldValue()));
            cache.registerCacheEntryListener(new MutableCacheEntryListenerConfiguration<>(
                () -> listener,
                null,
                true, // old value required
                true  // synchronous
            ));

            cache.removeAll();

            // Every mapping - heap-resident and heap-evicted alike - must be gone...
            for(int i = 0; i < ENTRY_COUNT; i++)
            {
                assertNull(cache.get(i), "entry survived removeAll() and resurrected via read-through");
            }
            // ...and every mapping must have produced a REMOVED event with its old value.
            assertEquals(ENTRY_COUNT, removedEvents.size(),
                "removeAll() must raise a REMOVED event for every mapping, including heap-evicted ones");
            for(int i = 0; i < ENTRY_COUNT; i++)
            {
                assertEquals("value-" + i, removedEvents.get(i),
                    "REMOVED event must carry the old value");
            }

            // Restart and re-read: the purge must hold against the storage on disk,
            // not merely against in-heap state.
            cacheManager.close();
            storageManager.close();

            storageManager = EmbeddedStorage.start(tempdir);
            cacheManager   = provider.getCacheManager();
            final CacheConfiguration<Integer, String> restartedConfiguration = CacheConfiguration
                .Builder(Integer.class, String.class, "removeAllCache", storageManager)
                .build();
            cache = cacheManager.createCache("removeAllCache", restartedConfiguration);

            for(int i = 0; i < ENTRY_COUNT; i++)
            {
                assertNull(cache.get(i), "entry survived removeAll() durably across a storage restart");
            }
        }
        finally
        {
            cacheManager.close();
            storageManager.close();
            LazyReferenceManager.get().stop();
        }
    }

    @Test
    @Timeout(60)
    void removeAllPurgesEvictedEntriesWithoutRemovedListener(@TempDir final Path tempdir)
    {
        final EmbeddedStorageManager storageManager = EmbeddedStorage.start(tempdir);

        final CachingProvider provider     = Caching.getCachingProvider();
        final CacheManager    cacheManager = provider.getCacheManager();

        final Cache<Integer, String> cache =
            createStorageBackedLruCache(cacheManager, storageManager, "noListenerCache");
        try
        {
            putUntilEvicted(cache);

            // No CacheEntryRemovedListener is registered: the purge must not depend on
            // the old-value loading that only removed-listeners require.
            cache.removeAll();

            for(int i = 0; i < ENTRY_COUNT; i++)
            {
                assertNull(cache.get(i), "entry survived removeAll() without a removed-listener");
            }
        }
        finally
        {
            cacheManager.close();
            storageManager.close();
            LazyReferenceManager.get().stop();
        }
    }

    @Test
    @Timeout(60)
    void removeAllWithGenericWriterPassesHeapKeysOnly()
    {
        final Set<Integer> deletedKeys = new HashSet<>();
        final CacheWriter<Integer, String> recordingWriter = new CacheWriter<>()
        {
            @Override
            public void write(final Cache.Entry<? extends Integer, ? extends String> entry)
            {
                // external resource not relevant for this test
            }

            @Override
            public void writeAll(final Collection<Cache.Entry<? extends Integer, ? extends String>> entries)
            {
                // external resource not relevant for this test
            }

            @Override
            public void delete(final Object key)
            {
                deletedKeys.add((Integer)key);
            }

            @Override
            public void deleteAll(final Collection<?> keys)
            {
                keys.forEach(key -> deletedKeys.add((Integer)key));
                keys.clear(); // all deletes succeeded
            }
        };

        final CachingProvider provider     = Caching.getCachingProvider();
        final CacheManager    cacheManager = provider.getCacheManager();

        final CacheConfiguration<Integer, String> configuration = CacheConfiguration
            .Builder(Integer.class, String.class)
            .cacheWriterFactory(() -> recordingWriter)
            .writeThrough()
            .evictionManagerFactory(() ->
                EvictionManager.OnEntryCreation(EvictionPolicy.LeastRecentlyUsed(MAX_CACHE_SIZE)))
            .build();

        final Cache<Integer, String> cache =
            cacheManager.createCache("genericWriterCache", configuration);
        try
        {
            final Set<Integer> heapKeys = putUntilEvicted(cache);

            deletedKeys.clear(); // ignore anything recorded before removeAll()
            cache.removeAll();

            // The external resource is not owned by the cache: only the cache's own
            // (heap) mappings may be passed to the writer, nothing may be enumerated.
            assertEquals(heapKeys, deletedKeys,
                "removeAll() with a generic CacheWriter must delete exactly the heap mappings");
        }
        finally
        {
            cacheManager.close();
        }
    }

    private static Cache<Integer, String> createStorageBackedLruCache(
        final CacheManager           cacheManager  ,
        final EmbeddedStorageManager storageManager,
        final String                 cacheName
    )
    {
        final CacheConfiguration<Integer, String> configuration = CacheConfiguration
            .Builder(Integer.class, String.class, cacheName, storageManager)
            .evictionManagerFactory(() ->
                EvictionManager.OnEntryCreation(EvictionPolicy.LeastRecentlyUsed(MAX_CACHE_SIZE)))
            .build();
        return cacheManager.createCache(cacheName, configuration);
    }

    /**
     * Puts more entries than the eviction cap and returns the keys still heap-resident.
     * containsKey never calls the loader (JSR-107), so it probes the heap only: keys
     * missing from the result were evicted. Fails if no eviction occurred, so no test
     * can pass without exercising the eviction path.
     */
    private static Set<Integer> putUntilEvicted(final Cache<Integer, String> cache)
    {
        for(int i = 0; i < ENTRY_COUNT; i++)
        {
            cache.put(i, "value-" + i);
        }
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
        return heapKeys;
    }
}
