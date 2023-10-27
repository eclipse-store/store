package org.eclipse.store.integrations.spring.boot.types.converter;

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

import org.eclipse.store.storage.embedded.configuration.types.EmbeddedStorageConfigurationPropertyNames;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConfigFieldsTest
{

    /**
     * Failed, when the new configuration key is present in Eclipse Store Configuration
     * but not present in Spring Integration
     */
    @Test
    void check_all_fields_eclipse_store_in_spring_integration_present()
    {
        Class<?> embeddedStorageConfigurationPropertyNames = EmbeddedStorageConfigurationPropertyNames.class;
        Field[] originalFields = embeddedStorageConfigurationPropertyNames.getDeclaredFields();

        EclipseStoreConfigConverter converter = new EclipseStoreConfigConverter();
        Field[] converterFields = converter.getClass()
                .getDeclaredFields();

        List<String> originalFieldNames = Arrays.stream(originalFields)
                .map(Field::getName)
                .toList();

        List<String> converterFieldsNames = Arrays.stream(converterFields)
                .map(Field::getName)
                .toList();

        assertTrue(converterFieldsNames.containsAll(originalFieldNames));
    }
}
