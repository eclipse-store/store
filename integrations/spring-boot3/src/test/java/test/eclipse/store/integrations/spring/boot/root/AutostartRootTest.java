package test.eclipse.store.integrations.spring.boot.root;

/*-
 * #%L
 * spring-boot3
 * %%
 * Copyright (C) 2023 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestPropertySource("classpath:application-autostart-root.properties")
@SpringBootTest
public class AutostartRootTest
{

    @Autowired
    private EmbeddedStorageManager storage;

    @Test
    void autostarts_and_provides_root()
    {
        assertTrue(storage.isRunning() || storage.isStartingUp());

        Object o = storage.root();
        assertInstanceOf(Root.class, o);
    }
}
