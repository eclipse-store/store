package org.eclipse.store.integrations.spring.boot.types.storages;

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

import org.eclipse.store.integrations.spring.boot.types.EclipseStoreSpringBoot;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource("classpath:application-two-storages.properties")
@Import(TwoBeanConfiguration.class)
@SpringBootTest(classes = {EclipseStoreSpringBoot.class, TwoStoragesTest.class})
public class TwoStoragesTest
{

    @Autowired
    @Qualifier("first_storage")
    EmbeddedStorageManager firstStorage;

    @Autowired
    @Qualifier("second_storage")
    EmbeddedStorageManager secondStorage;


    @Test
    void name()
    {
        Assertions.assertEquals("FirstRoot{value='First root value'}", firstStorage.root().toString());
        Assertions.assertEquals("SecondRoot{intValue=50, c=c}", secondStorage.root().toString());
    }

}
