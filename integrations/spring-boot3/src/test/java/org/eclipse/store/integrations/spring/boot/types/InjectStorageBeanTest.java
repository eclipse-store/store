package org.eclipse.store.integrations.spring.boot.types;

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

@TestPropertySource("classpath:application-run.properties")
@SpringBootTest(classes = {EclipseStoreSpringBoot.class})
public class InjectStorageBeanTest
{

    @Autowired
    EmbeddedStorageManager manager;


    @Test
    void storeSomething()
    {
        manager.start();
        manager.setRoot("hello");
        manager.storeRoot();
        manager.shutdown();
    }
}
