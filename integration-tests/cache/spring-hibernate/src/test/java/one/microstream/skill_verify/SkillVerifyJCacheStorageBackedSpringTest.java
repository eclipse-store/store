package one.microstream.skill_verify;

/*-
 * #%L
 * EclipseStore Integration Tests - Cache - Spring Hibernate
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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.expiry.CreatedExpiryPolicy;
import javax.cache.expiry.Duration;

import org.eclipse.store.cache.types.CacheConfiguration;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.cache.JCacheManagerCustomizer;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Verifies the storage-backed Spring + JCache wiring claimed by the cache-jcache PR
 * (Pattern G storage variant, Pitfall 8 — `JCacheManagerCustomizer` ordering).
 *
 * <ul>
 *   <li>Pattern G storage-backed — `JCacheManagerCustomizer` builds the cache from
 *       `CacheConfiguration.Builder(K, V, name, EmbeddedStorageManager)`.</li>
 *   <li>Pitfall 8 — constructor injection of the `EmbeddedStorageManager` resolves the
 *       storage bean *before* `customize(...)` runs (no NPE, no race).</li>
 *   <li>Storage-backed cache `readThrough` / `writeThrough` default to `true`
 *       (mental-model claim verified through `cache.getConfiguration(...)`).</li>
 *   <li>Round-trip: a `put` is observable through `get` against the same Spring
 *       `CacheManager`, with the entry physically backed by Eclipse Store storage.</li>
 * </ul>
 *
 * No JPA / MySQL — the storage-backed cache test is independent of the relational
 * datasource, so we exclude `DataSourceAutoConfiguration` to keep this fast.
 */
@SpringBootTest(classes = SkillVerifyJCacheStorageBackedSpringTest.TestApp.class)
class SkillVerifyJCacheStorageBackedSpringTest {

    @TempDir
    static Path STORAGE_DIR;

    /**
     * Storage path is exposed via a system property so the {@code @Bean} method
     * below can pick it up before the Spring context starts.
     */
    @BeforeAll
    static void exportStorageDir() {
        System.setProperty("skill-verify.storage.dir", STORAGE_DIR.toString());
    }

    @Autowired CacheManager           cacheManager;
    @Autowired EmbeddedStorageManager storage;
    @Autowired StorageBackedCachingSetup setup;

    /**
     * Pattern G storage-backed + Pitfall 8 — constructor injection of
     * `EmbeddedStorageManager` resolves the storage bean *before*
     * `customize(...)` is invoked, and the customizer's create call wires a
     * storage-backed `CacheConfiguration`.
     */
    @Test
    void pattern_G_storage_backed_cache_wired_via_customizer_with_constructor_injection() {
        // Storage bean exists.
        assertNotNull(storage,           "EmbeddedStorageManager bean must be present");
        assertTrue(storage.isRunning(),  "EmbeddedStorageManager must already be started");

        // Constructor-injected storage was non-null at customize() time.
        assertSame(storage, setup.injectedStorage,
            "JCacheManagerCustomizer received the same storage bean via constructor "
                + "injection — the Pitfall 8 NPE pattern is avoided.");

        // The cache the customizer built is reachable through the Spring CacheManager.
        final Cache<String, String> cache = cacheManager.getCache(
            "storage_cache", String.class, String.class);
        assertNotNull(cache, "storage_cache must have been created by the customizer");
    }

    /**
     * Storage-backed cache: `readThrough` / `writeThrough` default to `true`
     * (mental-model claim from the SKILL.md).
     */
    @Test
    void storage_backed_cache_readThrough_writeThrough_default_to_true() {
        final Cache<String, String> cache = cacheManager.getCache(
            "storage_cache", String.class, String.class);
        @SuppressWarnings("unchecked")
        final CacheConfiguration<String, String> cfg =
            cache.getConfiguration(CacheConfiguration.class);

        assertTrue(cfg.isReadThrough(),
            "storage-backed cache must default readThrough=true");
        assertTrue(cfg.isWriteThrough(),
            "storage-backed cache must default writeThrough=true");
    }

    /**
     * End-to-end smoke: put / get against the Spring-managed cache that was
     * created via `CacheConfiguration.Builder(..., storage)`.
     */
    @Test
    void put_then_get_round_trips_through_storage_backed_cache() {
        final Cache<String, String> cache = cacheManager.getCache(
            "storage_cache", String.class, String.class);

        cache.put("hello", "world");
        assertEquals("world", cache.get("hello"));
    }

    /* ────────────────────────── test app ────────────────────────── */

    /**
     * Self-contained Spring Boot app: no JPA / DB. Wires:
     *  - an {@code EmbeddedStorageManager} bean from the temp dir,
     *  - a {@code StorageBackedCachingSetup} customizer that constructor-injects it.
     */
    @SpringBootApplication(exclude = {
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class
    })
    @EnableCaching
    @Import(StorageBackedCachingSetup.class)
    static class TestApp {
        public static void main(String[] args) {
            SpringApplication.run(TestApp.class, args);
        }

        @Bean(destroyMethod = "shutdown")
        EmbeddedStorageManager embeddedStorageManager() {
            final String dir = System.getProperty("skill-verify.storage.dir");
            return EmbeddedStorage.start(java.nio.file.Paths.get(dir));
        }
    }

    /**
     * The PR's Pattern G storage-backed customizer. Constructor-injects
     * {@code EmbeddedStorageManager}; idempotent {@code customize()}.
     */
    @Configuration
    static class StorageBackedCachingSetup implements JCacheManagerCustomizer {

        final EmbeddedStorageManager injectedStorage;

        StorageBackedCachingSetup(EmbeddedStorageManager storage) {
            this.injectedStorage = storage;
        }

        @Override
        public void customize(CacheManager cacheManager) {
            if (cacheManager.getCache("storage_cache") != null) {
                return;
            }
            cacheManager.createCache("storage_cache", CacheConfiguration
                .Builder(String.class, String.class, "storage_cache", injectedStorage)
                .expiryPolicyFactory(CreatedExpiryPolicy.factoryOf(
                    new Duration(TimeUnit.HOURS, 1)))
                .build());
        }
    }
}
