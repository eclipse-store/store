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
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Path;

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
 * Regression test for internal issue #96: a storage-backed JCache configured with a size-based
 * eviction policy must not delete entries from the backing {@code EmbeddedStorage} on mere
 * heap eviction.
 * <p>
 * Eviction only frees heap space. Per the JSR-107 contract, eviction — like expiry — is a
 * cache-internal removal and must not invoke {@code CacheWriter.delete}; only application-driven
 * {@code remove}/{@code removeAll} operations do. An evicted entry therefore stays durable in the
 * backing store and is reloaded via read-through on the next {@code get()}.
 */
public class CacheEvictionDeletesFromStorageReproTest
{
    private static final long MAX_CACHE_SIZE = 3;

    @Test
    @Timeout(60)
    void sizeBasedEvictionMustNotDeleteFromBackingStorage(@TempDir final Path tempdir)
    {
        final EmbeddedStorageManager storageManager = EmbeddedStorage.start(tempdir);

        final CachingProvider provider     = Caching.getCachingProvider();
        final CacheManager    cacheManager = provider.getCacheManager();

        final CacheConfiguration<Integer, String> configuration = CacheConfiguration
            .Builder(Integer.class, String.class, "evictCache", storageManager)
            // bounded heap working set + full durable backing store — the headline use case
            .evictionManagerFactory(() ->
                EvictionManager.OnEntryCreation(EvictionPolicy.LeastRecentlyUsed(MAX_CACHE_SIZE)))
            .build();

        final Cache<Integer, String> cache = cacheManager.createCache("evictCache", configuration);
        try
        {
            // Put more than the cap; key 0 becomes the least-recently-used and is evicted from heap.
            for(int i = 0; i <= (int)MAX_CACHE_SIZE; i++)
            {
                cache.put(i, "value-" + i);
                // touch the survivors so key 0 is the definite LRU victim
                for(int j = 1; j <= i; j++)
                {
                    cache.get(j);
                }
            }

            // Key 0 was only EVICTED FROM HEAP. It must remain durable and reload via read-through.
            final String reloaded = cache.get(0);
            assertNotNull(reloaded,
                "heap-evicted entry was deleted from backing storage (size-based eviction called "
                + "CacheWriter.delete) — durable value lost");
            assertEquals("value-0", reloaded, "the durable value must be intact after heap eviction");
        }
        finally
        {
            cacheManager.close();
            storageManager.shutdown();
            LazyReferenceManager.get().stop();
        }
    }
}
