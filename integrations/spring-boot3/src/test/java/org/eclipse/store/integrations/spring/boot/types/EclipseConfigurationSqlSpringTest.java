package org.eclipse.store.integrations.spring.boot.types;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

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

import org.eclipse.store.integrations.spring.boot.types.configuration.EclipseStoreProperties;
import org.eclipse.store.integrations.spring.boot.types.converter.EclipseStoreConfigConverter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource("classpath:application-sql.properties")
@SpringBootTest(classes = {EclipseStoreSpringBoot.class})
public class EclipseConfigurationSqlSpringTest
{

    @Qualifier("eclipseStoreProperties")
    @Autowired
    EclipseStoreProperties values;

    @Autowired
    EclipseStoreConfigConverter converter;

    @Test
    void checkStorageDirectoryValue()
    {
        assertNotNull(this.values.getStorageFilesystem());
    }

    @Test
    void converterBasicTest()
    {
        final Map<String, String> valueMap = this.converter.convertConfigurationToMap(this.values);
        assertTrue(valueMap.containsKey("storage-filesystem.sql.postgres.user"));
    }
}
