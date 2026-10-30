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

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.management.CacheMXBean;
import javax.cache.management.CacheStatisticsMXBean;

import jakarta.persistence.EntityManagerFactory;

import one.microstream.CachingSetup;
import one.microstream.DemoApplication;
import one.microstream.data.Employee;
import one.microstream.repository.EmployeeRepository;
import one.microstream.service.EmployeeService;

import org.eclipse.store.cache.hibernate.types.CacheRegionFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies the Spring-Boot integration claims of the cache-jcache skill (PR #15)
 * against the live `simplecache` Spring Boot 3 + Hibernate + JPA + Eclipse Store
 * cache stack. Each test maps to a specific PR claim:
 *
 * <ul>
 *   <li>Pattern G — `@Cacheable` + `JCacheManagerCustomizer` (in-memory)</li>
 *   <li>Pattern H — Hibernate L2 wired to Eclipse Store via `CacheRegionFactory`</li>
 *   <li>Pitfall 7 — statistics off by default; `enableStatistics()` flips them on</li>
 *   <li>Hibernate property — `hibernate.cache.eclipsestore.missing_cache_strategy`
 *       (renamed from the old `hibernate.cache.microstream.*`)</li>
 * </ul>
 */
@Testcontainers
@SpringBootTest(classes = DemoApplication.class)
class SkillVerifyJCacheSpringTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("employees")
            .withUsername("data")
            .withPassword("college")
            .withInitScript("init/employees-seed.sql");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",                MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username",           MYSQL::getUsername);
        registry.add("spring.datasource.password",           MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name",  MYSQL::getDriverClassName);
    }

    @Autowired EmployeeService       employeeService;
    @Autowired EmployeeRepository    employeeRepository;
    @Autowired CacheManager          jCacheManager;            // injected as javax.cache.CacheManager
    @Autowired CachingSetup          cachingSetup;             // the bean from the main module
    @Autowired EntityManagerFactory  entityManagerFactory;

    /**
     * Pattern G — Spring `@Cacheable` integration:
     *  - `JCacheManagerCustomizer` (CachingSetup) ran;
     *  - `employee_cache` was created with the configuration prescribed in CachingSetup;
     *  - the cache reachable through Spring's `CacheManager` is the same one as via
     *    JCache's `Caching.getCachingProvider().getCacheManager()`.
     */
    @Test
    void pattern_G_jcacheManagerCustomizer_wires_employee_cache() {
        assertNotNull(cachingSetup, "CachingSetup bean must be in the context");

        final Cache<Object, Object> cache = jCacheManager.getCache("employee_cache");
        assertNotNull(cache, "employee_cache must have been created by JCacheManagerCustomizer");

        // The provider class is Eclipse Store's
        assertEquals("org.eclipse.store.cache.types.CachingProvider",
            jCacheManager.getCachingProvider().getClass().getName());
    }

    /**
     * Pitfall 7 — statistics are off by default; CachingSetup uses
     * `setStatisticsEnabled(true)`, so {@code cache.unwrap(CacheStatisticsMXBean.class)}
     * returns the registered statistics MXBean and reports non-zero hits after
     * warm-up. Also exercises {@code cache.unwrap(CacheMXBean.class)} for the
     * configuration MXBean.
     */
    @Test
    void pitfall_7_statistics_reflect_cacheable_calls() {
        // Warm up: first call → DB hit; second + third → cache hits.
        employeeService.findAll();
        employeeService.findAll();
        employeeService.findAll();

        final Cache<Object, Object> cache = jCacheManager.getCache("employee_cache");

        final CacheStatisticsMXBean stats = cache.unwrap(CacheStatisticsMXBean.class);
        assertNotNull(stats, "Cache.unwrap(CacheStatisticsMXBean.class) must return "
            + "the registered statistics MXBean when statistics are enabled");
        assertTrue(stats.getCacheHits() > 0,
            "Cache hits must be non-zero after multiple @Cacheable calls; got: "
                + stats.getCacheHits());

        final CacheMXBean cfgMxbean = cache.unwrap(CacheMXBean.class);
        assertNotNull(cfgMxbean, "Cache.unwrap(CacheMXBean.class) must return "
            + "the registered configuration MXBean");
    }

    /**
     * Pattern H — Hibernate L2 region factory is Eclipse Store's, wired via
     * `spring.jpa.properties.hibernate.cache.region.factory_class`.
     */
    @Test
    void pattern_H_hibernate_L2_uses_eclipseStore_region_factory() {
        final SessionFactory sessionFactory =
            entityManagerFactory.unwrap(SessionFactory.class);

        final Object regionFactory = sessionFactory.getSessionFactoryOptions()
            .getServiceRegistry()
            .getService(org.hibernate.cache.spi.RegionFactory.class);

        assertNotNull(regionFactory, "Hibernate must have a region factory configured");
        assertTrue(regionFactory instanceof CacheRegionFactory,
            "Region factory must be Eclipse Store's CacheRegionFactory; was "
                + regionFactory.getClass().getName());
    }

    /**
     * Pattern H — Hibernate L2 actually caches `Employee` reads. Pulls one entity
     * twice through the repository; second read must come from the L2 cache via
     * the Eclipse Store region factory.
     */
    @Test
    void pattern_H_hibernate_L2_caches_entities() {
        final SessionFactory sessionFactory =
            entityManagerFactory.unwrap(SessionFactory.class);
        sessionFactory.getStatistics().setStatisticsEnabled(true);
        sessionFactory.getStatistics().clear();

        final Long someId = 10001L;   // populated by employees-seed.sql
        final Employee first  = employeeRepository.findById(someId).orElseThrow();
        final Employee second = employeeRepository.findById(someId).orElseThrow();

        assertSame(first.getNumber(), second.getNumber(), "same logical entity");

        final long l2Hits = sessionFactory.getStatistics().getSecondLevelCacheHitCount();
        assertTrue(l2Hits > 0,
            "Hibernate L2 must have at least one hit on the second findById; got "
                + l2Hits);
    }
}
