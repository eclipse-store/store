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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@DirtiesContext
class AutowiringTest
{
    @DynamicPropertySource
    static void props(DynamicPropertyRegistry reg) throws IOException
    {
        Path dir = Files.createTempDirectory("es-spring-autowire");
        reg.add("org.eclipse.store.storage-directory", dir::toString);
    }

    @Autowired
    EmbeddedStorageManager storage;

    @Test
    void storage_manager_is_autowired_and_root_is_typed()
    {
        assertNotNull(storage, "EmbeddedStorageManager must be autowired");
        assertNotNull(storage.root(), "root must not be null");
        assertTrue(storage.root() instanceof AppRoot,
            "root must be the configured AppRoot type, was: "
                + storage.root().getClass().getName());
    }
}
