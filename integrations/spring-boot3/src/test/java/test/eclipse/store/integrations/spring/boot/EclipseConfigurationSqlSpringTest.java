package test.eclipse.store.integrations.spring.boot;

/*-
 * #%L
 * EclipseStore Integrations SpringBoot
 * %%
 * Copyright (C) 2023 - 2024 MicroStream Software
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@TestPropertySource("classpath:application-sql.properties")
public class EclipseConfigurationSqlSpringTest
{

    @Autowired
    private EclipseStoreProperties values;

    @Autowired
    private EclipseStoreConfigConverter converter;

    @Test
    void checks_storage_directory_value()
    {
        assertNotNull(this.values.getStorageFilesystem());
    }

    @Test
    void converts_value_from_properties()
    {
        final Map<String, String> valueMap = this.converter.convertConfigurationToMap(this.values);
        assertTrue(valueMap.containsKey("storage-filesystem.sql.postgres.user"));
    }
}
