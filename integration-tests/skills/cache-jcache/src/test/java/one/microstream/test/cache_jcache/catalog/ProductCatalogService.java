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

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.expiry.Duration;
import javax.cache.expiry.ModifiedExpiryPolicy;
import javax.cache.spi.CachingProvider;

import org.eclipse.store.cache.types.CacheConfiguration;
import org.eclipse.store.cache.types.CacheStatisticsMXBean;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;

/**
 * Cache-aside product catalog backed by a JCache (JSR-107) cache that is itself
 * backed by an Eclipse Store {@link EmbeddedStorageManager} so cache entries
 * survive JVM restarts.
 */
public class ProductCatalogService implements AutoCloseable
{
    public static final String CACHE_NAME = "products";

    private final Map<String, Product>     database;
    private final AtomicLong               dbLookups = new AtomicLong();
    private final EmbeddedStorageManager   storage;
    private final CacheManager             cacheManager;
    private final Cache<String, Product>   cache;

    public ProductCatalogService(final Path storageDir, final Map<String, Product> database)
    {
        this.database = database;

        this.storage = EmbeddedStorage.start(storageDir);

        final CachingProvider provider = Caching.getCachingProvider(
            "org.eclipse.store.cache.types.CachingProvider");
        this.cacheManager = provider.getCacheManager();

        final CacheConfiguration<String, Product> cfg = CacheConfiguration
            .Builder(String.class, Product.class, CACHE_NAME, this.storage)
            .expiryPolicyFactory(ModifiedExpiryPolicy.factoryOf(
                new Duration(TimeUnit.HOURS, 1)))
            .enableStatistics()
            .build();

        this.cache = this.cacheManager.createCache(CACHE_NAME, cfg);
    }

    public Product findBySku(final String sku)
    {
        Product cached = this.cache.get(sku);
        if (cached != null)
        {
            return cached;
        }
        this.dbLookups.incrementAndGet();
        final Product fromDb = this.database.get(sku);
        if (fromDb != null)
        {
            this.cache.put(sku, fromDb);
        }
        return fromDb;
    }

    public void evict(final String sku)
    {
        this.cache.remove(sku);
    }

    public long cacheHits()
    {
        return this.statsBean().getCacheHits();
    }

    public long cacheMisses()
    {
        return this.statsBean().getCacheMisses();
    }

    public long dbLookups()
    {
        return this.dbLookups.get();
    }

    private CacheStatisticsMXBean statsBean()
    {
        return this.cache.unwrap(CacheStatisticsMXBean.class);
    }

    @Override
    public void close()
    {
        if (this.cacheManager != null && !this.cacheManager.isClosed())
        {
            this.cacheManager.close();
        }
        if (this.storage != null && this.storage.isRunning())
        {
            this.storage.shutdown();
        }
    }

    public static Map<String, Product> seedDatabase()
    {
        final Map<String, Product> db = new HashMap<>();
        db.put("SKU-1", new Product("SKU-1", "Widget",     new java.math.BigDecimal("9.99"),   "Standard widget"));
        db.put("SKU-2", new Product("SKU-2", "Sprocket",   new java.math.BigDecimal("14.50"),  "Premium sprocket"));
        db.put("SKU-3", new Product("SKU-3", "Gizmo",      new java.math.BigDecimal("29.00"),  "Gizmo with USB"));
        db.put("SKU-4", new Product("SKU-4", "Thingamajig", new java.math.BigDecimal("3.25"),  "Tiny thing"));
        db.put("SKU-5", new Product("SKU-5", "Doohickey",  new java.math.BigDecimal("199.99"), "Industrial doohickey"));
        return db;
    }
}
