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


import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.expiry.CreatedExpiryPolicy;
import javax.cache.expiry.Duration;
import javax.cache.spi.CachingProvider;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.eclipse.serializer.reference.LazyReferenceManager;
import org.eclipse.store.cache.types.CacheConfiguration;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;


public class CacheEvictTest
{

    private static final String CACHED_VALUE = "one";
    private static final int KEY = 1;

    @Test
    void cacheItemEvicted() throws InterruptedException
    {
        CachingProvider provider = Caching.getCachingProvider();
        CacheManager cacheManager = provider.getCacheManager();


        MutableConfiguration<Integer, String> configuration = new MutableConfiguration<Integer, String>()
                .setExpiryPolicyFactory(CreatedExpiryPolicy.factoryOf(new Duration(TimeUnit.MILLISECONDS, 500)))
                .setStoreByValue(true);

        Cache<Integer, String> cache = cacheManager.createCache("jCache1", configuration);
        cache.put(KEY, CACHED_VALUE);

        Assertions.assertEquals(CACHED_VALUE, cache.get(KEY));

        Thread.sleep(700);  // More then the 1 sec expiration policy

        Assertions.assertNull(cache.get(KEY));
    }

    @Test
    void cacheItemEvictedWithStorage(@TempDir Path tempdir) throws InterruptedException
    {
        EmbeddedStorageManager storageManager = EmbeddedStorage.start(tempdir);

        CachingProvider provider = Caching.getCachingProvider();
        CacheManager cacheManager = provider.getCacheManager();


        CacheConfiguration<Integer, String> configuration = CacheConfiguration
                .Builder(Integer.class, String.class, "jCache", storageManager)
                .expiryPolicyFactory(CreatedExpiryPolicy.factoryOf(new Duration(TimeUnit.MILLISECONDS, 500)))
                .build();

        Cache<Integer, String> cache = cacheManager.createCache("jCache", configuration);
        cache.put(KEY, CACHED_VALUE);

        Assertions.assertEquals(CACHED_VALUE, cache.get(KEY));

        Thread.sleep(700);  // More than the 1 sec expiration policy

        Assertions.assertNull(cache.get(KEY));

        storageManager.close();

        LazyReferenceManager referenceManager = LazyReferenceManager.get();
        referenceManager.stop();
    }
}
