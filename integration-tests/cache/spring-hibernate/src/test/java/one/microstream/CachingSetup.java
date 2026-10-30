package one.microstream;

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

import org.springframework.boot.autoconfigure.cache.JCacheManagerCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import javax.cache.CacheManager;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.expiry.CreatedExpiryPolicy;
import javax.cache.expiry.Duration;

import static java.util.concurrent.TimeUnit.SECONDS;

@Configuration
@EnableCaching
public class CachingSetup implements JCacheManagerCustomizer {

    @Override
    public void customize(CacheManager cacheManager) {
        // Idempotent — JCache CacheManager is JVM-singleton, so two SpringBootTest
        // classes that both register this customizer would otherwise collide on the
        // second run with "A cache named employee_cache already exists".
        if (cacheManager.getCache("employee_cache") != null) {
            return;
        }
        cacheManager.createCache("employee_cache", new MutableConfiguration<>()
                .setExpiryPolicyFactory(CreatedExpiryPolicy.factoryOf(new Duration(SECONDS, 10000)))
                .setStoreByValue(true)
                .setStatisticsEnabled(true));
    }
}
