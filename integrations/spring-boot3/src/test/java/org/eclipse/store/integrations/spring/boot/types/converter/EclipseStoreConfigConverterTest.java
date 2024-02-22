package org.eclipse.store.integrations.spring.boot.types.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import org.eclipse.store.integrations.spring.boot.types.configuration.StorageFilesystem;
import org.eclipse.store.integrations.spring.boot.types.configuration.sql.Mariadb;
import org.eclipse.store.integrations.spring.boot.types.configuration.sql.Sql;
import org.junit.jupiter.api.Test;

class EclipseStoreConfigConverterTest
{

    private final EclipseStoreConfigConverter converter = new EclipseStoreConfigConverter();

    @Test
    void testBasicConversion()
    {
        final EclipseStoreProperties properties = new EclipseStoreProperties();
        properties.setChannelCount("4");

        final EclipseStoreConfigConverter converter = new EclipseStoreConfigConverter();
        final Map<String, String> stringStringMap = converter.convertConfigurationToMap(properties);

        assertNotNull(stringStringMap);
        assertEquals(1, stringStringMap.size());
    }

    @Test
    void testSQLConfiguration()
    {
        final String CATALOG = "super_catalog";

        final Sql sql = new Sql();
        final Mariadb mariadb = new Mariadb();
        mariadb.setCatalog(CATALOG);
        mariadb.setPassword("myPssw");
        sql.setMariadb(mariadb);
        final StorageFilesystem storageFilesystem = new StorageFilesystem();
        storageFilesystem.setSql(sql);
        final EclipseStoreProperties values = new EclipseStoreProperties();
        values.setStorageFilesystem(storageFilesystem);

        final EclipseStoreConfigConverter converter = new EclipseStoreConfigConverter();
        final Map<String, String> valueMap = converter.convertConfigurationToMap(values);

        assertTrue(valueMap.containsKey("storage-filesystem.sql.mariadb.catalog"));
        assertEquals(CATALOG, valueMap.get("storage-filesystem.sql.mariadb.catalog"));
    }

    @Test
    void testConvertConfigurationToMap()
    {
        final EclipseStoreProperties configValues = new EclipseStoreProperties();
        final Map<String, String> result = this.converter.convertConfigurationToMap(configValues);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testConvertConfigurationToMapWithStorageDirectory()
    {
        final EclipseStoreProperties configValues = new EclipseStoreProperties();
        configValues.setStorageDirectory("storage/dir");
        final Map<String, String> result = this.converter.convertConfigurationToMap(configValues);
        assertNotNull(result);
        assertEquals("storage/dir", result.get(EclipseStoreConfigConverter.STORAGE_DIRECTORY));
    }

    @Test
    void testNullValuesAreRemoved()
    {
        final EclipseStoreProperties configValues = new EclipseStoreProperties();
        configValues.setStorageDirectory(null);
        configValues.setStorageFilesystem(new StorageFilesystem());
        configValues.setBackupDirectory("backup/dir");

        final Map<String, String> result = this.converter.convertConfigurationToMap(configValues);

        assertNull(result.get(EclipseStoreConfigConverter.STORAGE_DIRECTORY));
        assertNotNull(result.get(EclipseStoreConfigConverter.BACKUP_DIRECTORY));
    }

    @Test
    void testComposeKey()
    {
        final String result = this.converter.composeKey("prefix", "suffix");
        assertEquals("prefix.suffix", result);
    }
}
