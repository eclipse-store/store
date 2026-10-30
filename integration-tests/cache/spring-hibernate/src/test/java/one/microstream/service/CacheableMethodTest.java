package one.microstream.service;

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

import one.microstream.DemoApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.Assert;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(classes = {DemoApplication.class})
class CacheableMethodTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("employees")
            .withUsername("data")
            .withPassword("college")
            .withInitScript("init/employees-seed.sql");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    }

    @Autowired
    private EmployeeService employeeService;

    @Test
    void loadDataWithCacheableTest() {
        long start;
        long stop;
        long first;
        long second;
        long third;

        start = System.currentTimeMillis();
        employeeService.findAll();
        stop = System.currentTimeMillis();
        first = stop - start;

        start = System.currentTimeMillis();
        employeeService.findAll();
        stop = System.currentTimeMillis();
        second = stop - start;

        start = System.currentTimeMillis();
        employeeService.findAll();
        stop = System.currentTimeMillis();
        third = stop - start;

        // Cached calls must be much faster than the first DB hit.
        Assert.isTrue(first > ((second + third) * 5), "With cache is slower.");
    }
}
