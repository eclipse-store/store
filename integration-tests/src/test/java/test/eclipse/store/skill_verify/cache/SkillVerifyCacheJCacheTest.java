package test.eclipse.store.skill_verify.cache;

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

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.expiry.CreatedExpiryPolicy;
import javax.cache.expiry.Duration;
import javax.cache.spi.CachingProvider;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.eclipse.store.cache.types.*;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies the API claims in PR #15 (cache-jcache skill rework).
 * Each test maps to a specific claim in the SKILL.md / api-catalogue.md.
 */
class SkillVerifyCacheJCacheTest
{

    /**
     * SKILL.md / api-catalogue:
     * The provider FQN is "org.eclipse.store.cache.types.CachingProvider"
     * (no PROVIDER_CLASS_NAME constant — magic string).
     */
    @Test
    void provider_fqn_resolves()
    {
        final CachingProvider provider = Caching.getCachingProvider(
                "org.eclipse.store.cache.types.CachingProvider");
        assertNotNull(provider);
        assertEquals("org.eclipse.store.cache.types.CachingProvider",
                provider.getClass().getName());
    }

    /**
     * api-catalogue: PathProperty() = "eclipsestore.cache.configuration.path",
     * DefaultResourceName() = "eclipsestore-cache.properties".
     */
    @Test
    void path_property_and_default_resource_name()
    {
        assertEquals("eclipsestore.cache.configuration.path",
                CacheConfiguration.PathProperty());
        assertEquals("eclipsestore-cache.properties",
                CacheConfiguration.DefaultResourceName());
    }

    /**
     * api-catalogue: CacheConfigurationPropertyNames constants.
     */
    @Test
    void property_name_constants()
    {
        assertEquals("key-type", CacheConfigurationPropertyNames.KEY_TYPE);
        assertEquals("value-type", CacheConfigurationPropertyNames.VALUE_TYPE);
        assertEquals("storage-configuration-resource-name", CacheConfigurationPropertyNames.STORAGE_CONFIGURATION_RESOURCE_NAME);
        assertEquals("read-through", CacheConfigurationPropertyNames.READ_THROUGH);
        assertEquals("write-through", CacheConfigurationPropertyNames.WRITE_THROUGH);
        assertEquals("store-by-value", CacheConfigurationPropertyNames.STORE_BY_VALUE);
        assertEquals("statistics-enabled", CacheConfigurationPropertyNames.STATISTICS_ENABLED);
        assertEquals("management-enabled", CacheConfigurationPropertyNames.MANAGEMENT_ENABLED);
        assertEquals("expiry-policy-factory", CacheConfigurationPropertyNames.EXPIRY_POLICY_FACTORY);
        assertEquals("eviction-manager-factory", CacheConfigurationPropertyNames.EVICTION_MANAGER_FACTORY);
        assertEquals("cache-loader-factory", CacheConfigurationPropertyNames.CACHE_LOADER_FACTORY);
        assertEquals("cache-writer-factory", CacheConfigurationPropertyNames.CACHE_WRITER_FACTORY);
    }

    /**
     * SKILL.md Pattern F + api-catalogue: EvictionManager static factories.
     */
    @Test
    void evictionManager_factories_exist()
    {
        final EvictionPolicy lru = EvictionPolicy.LeastRecentlyUsed(1000L);
        assertNotNull(lru);
        assertNotNull(EvictionManager.OnEntryCreation(lru));
        assertNotNull(EvictionManager.Interval(lru, 60_000L));
    }

    /**
     * SKILL.md Pattern F + api-catalogue: EvictionPolicy static factories.
     */
    @Test
    void evictionPolicy_factories_exist()
    {
        assertNotNull(EvictionPolicy.LeastRecentlyUsed(1000L));
        assertNotNull(EvictionPolicy.LeastFrequentlyUsed(1000L));
        assertNotNull(EvictionPolicy.FirstInFirstOut(4, 1000L));
        assertNotNull(EvictionPolicy.BiggestObjects(4, 1000L));
    }

    /**
     * api-catalogue: org.eclipse.store.cache.types.Cache extends javax.cache.Cache and Unwrappable;
     * adds size(), putSilent(K,V), narrows getCacheManager() / getConfiguration().
     */
    @Test
    void eclipseStore_cache_interface_shape() throws NoSuchMethodException
    {
        final Class<?> esCache = org.eclipse.store.cache.types.Cache.class;
        assertTrue(javax.cache.Cache.class.isAssignableFrom(esCache),
                "Eclipse Store Cache must extend javax.cache.Cache");
        assertTrue(Unwrappable.class.isAssignableFrom(esCache),
                "Eclipse Store Cache must extend Unwrappable");

        // Eclipse-Store-only methods declared on the narrowing interface
        final Method size = esCache.getMethod("size");
        assertEquals(long.class, size.getReturnType());

        final Method putSilent = esCache.getMethod("putSilent", Object.class, Object.class);
        assertNotNull(putSilent);
    }

    /**
     * api-catalogue: org.eclipse.store.cache.types.CacheManager extends
     * javax.cache.CacheManager and adds removeCache(String).
     */
    @Test
    void eclipseStore_cacheManager_interface_shape() throws NoSuchMethodException
    {
        final Class<?> esManager = org.eclipse.store.cache.types.CacheManager.class;
        assertTrue(javax.cache.CacheManager.class.isAssignableFrom(esManager),
                "Eclipse Store CacheManager must extend javax.cache.CacheManager");

        final Method removeCache = esManager.getMethod("removeCache", String.class);
        assertNotNull(removeCache);
    }

    /**
     * SKILL.md Mental model:
     * "A storage-backed cache automatically acts as both CacheReader and CacheWriter
     * — readThrough and writeThrough default to true."
     */
    @Test
    void storage_backed_cache_defaults_readThrough_writeThrough_to_true(@TempDir Path dir)
    {
        try (EmbeddedStorageManager storage = EmbeddedStorage.start(dir)) {
            final CacheConfiguration<String, String> cfg = CacheConfiguration
                    .Builder(String.class, String.class, "rwt", storage)
                    .build();
            assertTrue(cfg.isReadThrough(),
                    "Storage-backed cache must default readThrough=true");
            assertTrue(cfg.isWriteThrough(),
                    "Storage-backed cache must default writeThrough=true");
        }
    }

    /**
     * SKILL.md Pattern B + Anti-pattern 1:
     * Standalone configuration loses entries on restart;
     * storage-backed configuration survives.
     */
    @Test
    void storage_backed_cache_round_trips_across_restart(@TempDir Path dir)
    {
        // First boot: put entry, shut down.
        try (EmbeddedStorageManager storage = EmbeddedStorage.start(dir)) {
            final CachingProvider provider = Caching.getCachingProvider(
                    "org.eclipse.store.cache.types.CachingProvider");
            try (CacheManager cm = provider.getCacheManager()) {
                final CacheConfiguration<String, String> cfg = CacheConfiguration
                        .Builder(String.class, String.class, "persisted-cache", storage)
                        .expiryPolicyFactory(CreatedExpiryPolicy.factoryOf(
                                new Duration(TimeUnit.HOURS, 1)))
                        .build();
                final Cache<String, String> cache = cm.createCache("persisted-cache", cfg);
                cache.put("k1", "v1");
                assertEquals("v1", cache.get("k1"));
            }
        }

        // Second boot: open storage + cache again, expect entry present.
        try (EmbeddedStorageManager storage = EmbeddedStorage.start(dir)) {
            final CachingProvider provider = Caching.getCachingProvider(
                    "org.eclipse.store.cache.types.CachingProvider");
            try (CacheManager cm = provider.getCacheManager()) {
                final CacheConfiguration<String, String> cfg = CacheConfiguration
                        .Builder(String.class, String.class, "persisted-cache", storage)
                        .expiryPolicyFactory(CreatedExpiryPolicy.factoryOf(
                                new Duration(TimeUnit.HOURS, 1)))
                        .build();
                final Cache<String, String> cache = cm.createCache("persisted-cache", cfg);
                assertEquals("v1", cache.get("k1"),
                        "Storage-backed cache entry must survive restart");
            }
        }
    }

    /**
     * SKILL.md "Key advantage over Ehcache / Caffeine":
     * "Cache values are not required to be Serializable when storeByValue(false) is used."
     */
    @Test
    void non_serializable_value_works_with_storeByValue_false()
    {
        final CachingProvider provider = Caching.getCachingProvider(
                "org.eclipse.store.cache.types.CachingProvider");
        try (CacheManager cm = provider.getCacheManager()) {
            final MutableConfiguration<String, NonSerializable> cfg =
                    new MutableConfiguration<String, NonSerializable>()
                            .setTypes(String.class, NonSerializable.class)
                            .setStoreByValue(false);

            final Cache<String, NonSerializable> cache =
                    cm.createCache("non-ser-cache", cfg);

            final NonSerializable value = new NonSerializable("hello");
            assertDoesNotThrow(() -> cache.put("k", value));
            assertEquals("hello", cache.get("k").label);

            cm.destroyCache("non-ser-cache");
        }
    }

    /**
     * A class that does NOT implement Serializable — to verify storeByReference cache path.
     */
    public static class NonSerializable
    {
        public final String label;

        public NonSerializable(String label)
        {
            this.label = label;
        }
        // not Serializable — verifies the skill claim
    }

    /**
     * SKILL.md Pattern G:
     * javax.cache.Caching.getCachingProvider() works only with one provider on classpath.
     * With Eclipse Store as the sole provider here, the no-arg variant must return it.
     */
    @Test
    void no_arg_getCachingProvider_works_with_single_provider()
    {
        final CachingProvider provider = Caching.getCachingProvider();
        assertNotNull(provider);
        // In this test environment Eclipse Store is the only provider on classpath
        assertEquals("org.eclipse.store.cache.types.CachingProvider",
                provider.getClass().getName(),
                "With Eclipse Store as the only provider, the no-arg lookup must resolve to it");
    }

    /**
     * Sanity: javax.cache.spi.CachingProvider FQN, asserted by the skill, is real.
     */
    @Test
    void javax_cache_provider_fqn()
    {
        // documented to ensure the class name string in the skill matches
        assertEquals("javax.cache.spi.CachingProvider",
                CachingProvider.class.getName());
    }

    /**
     * verify that Cache values in a storage-backed config with non-Serializable
     * types still don't require Serializable.
     */
    @Test
    void storage_backed_cache_does_not_require_Serializable(@TempDir Path dir)
    {
        try (EmbeddedStorageManager storage = EmbeddedStorage.start(dir)) {
            final CachingProvider provider = Caching.getCachingProvider(
                    "org.eclipse.store.cache.types.CachingProvider");
            try (CacheManager cm = provider.getCacheManager()) {
                final CacheConfiguration<String, NonSerializable> cfg = CacheConfiguration
                        .Builder(String.class, NonSerializable.class, "ns-storage", storage)
                        .storeByReference()
                        .build();
                final Cache<String, NonSerializable> cache =
                        cm.createCache("ns-storage", cfg);
                assertDoesNotThrow(() -> cache.put("k", new NonSerializable("ok")));
                assertEquals("ok", cache.get("k").label);

                // Sanity: NonSerializable is really not Serializable
                assertTrue(!Serializable.class.isAssignableFrom(NonSerializable.class));

                cm.destroyCache("ns-storage");
            }
        }
    }
}
