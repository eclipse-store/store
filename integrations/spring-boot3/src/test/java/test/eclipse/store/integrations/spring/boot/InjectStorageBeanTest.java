package test.eclipse.store.integrations.spring.boot;

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

import static org.assertj.core.api.Assertions.assertThat;


@SpringBootTest
@TestPropertySource("classpath:application-inject-test.properties")
public class InjectStorageBeanTest
{

    @Autowired
    private EmbeddedStorageManager manager;


    @Test
    void injects_storage_manager()
    {
        assertThat(manager.isRunning()).isFalse();
        manager.start();
        assertThat(manager.isRunning()).isTrue();
        manager.setRoot("hello");
        manager.storeRoot();
        manager.shutdown();
        assertThat(manager.isRunning()).isFalse();
    }

}
