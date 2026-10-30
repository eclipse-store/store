package one.microstream.test.springboot;

/*-
 * #%L
 * EclipseStore Integration Tests - Skills - spring-boot
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@DirtiesContext
class MutexNamedLockTest
{
    @DynamicPropertySource
    static void props(DynamicPropertyRegistry reg) throws IOException
    {
        Path dir = Files.createTempDirectory("es-spring-mutex");
        reg.add("org.eclipse.store.storage-directory", dir::toString);
    }

    @Autowired
    OrderService orders;

    @Test
    void place_then_find_returns_same_order_under_named_mutex()
    {
        Order o = new Order("ord-1", "alice@example.com");
        orders.place(o);

        Order found = orders.find("ord-1");
        assertNotNull(found, "order must be findable after @Write @Mutex(\"orders\") place");
        assertEquals(o, found);
    }
}
