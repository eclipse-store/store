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

import org.eclipse.store.integrations.spring.boot.types.configuration.EclipseStoreProperties;
import org.eclipse.store.integrations.spring.boot.types.configuration.StorageFilesystem;
import org.eclipse.store.integrations.spring.boot.types.configuration.sql.Mariadb;
import org.eclipse.store.integrations.spring.boot.types.configuration.sql.Sql;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EclipseStoreConfigConverterTest
{

    private final EclipseStoreConfigConverter converter = new EclipseStoreConfigConverter();

    @Test
    void testBasicConversion()
    {
        EclipseStoreProperties properties = new EclipseStoreProperties();
        properties.setChannelCount("4");

        EclipseStoreConfigConverter converter = new EclipseStoreConfigConverter();
        Map<String, String> stringStringMap = converter.convertConfigurationToMap(properties);

        assertNotNull(stringStringMap);
        assertEquals(1, stringStringMap.size());
    }

    @Test
    void testSQLConfiguration()
    {
        final String CATALOG = "super_catalog";

        Sql sql = new Sql();
        Mariadb mariadb = new Mariadb();
        mariadb.setCatalog(CATALOG);
        mariadb.setPassword("myPssw");
        sql.setMariadb(mariadb);
        StorageFilesystem storageFilesystem = new StorageFilesystem();
        storageFilesystem.setSql(sql);
        EclipseStoreProperties values = new EclipseStoreProperties();
        values.setStorageFilesystem(storageFilesystem);

        EclipseStoreConfigConverter converter = new EclipseStoreConfigConverter();
        Map<String, String> valueMap = converter.convertConfigurationToMap(values);

        assertTrue(valueMap.containsKey("storage-filesystem.sql.mariadb.catalog"));
        assertEquals(CATALOG, valueMap.get("storage-filesystem.sql.mariadb.catalog"));
    }

    @Test
    void testConvertConfigurationToMap() {
        EclipseStoreProperties configValues = new EclipseStoreProperties();
        Map<String, String> result = converter.convertConfigurationToMap(configValues);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testConvertConfigurationToMapWithStorageDirectory() {
        EclipseStoreProperties configValues = new EclipseStoreProperties();
        configValues.setStorageDirectory("storage/dir");
        Map<String, String> result = converter.convertConfigurationToMap(configValues);
        assertNotNull(result);
        assertEquals("storage/dir", result.get(EclipseStoreConfigConverter.STORAGE_DIRECTORY));
    }

    @Test
    void testNullValuesAreRemoved() {
        EclipseStoreProperties configValues = new EclipseStoreProperties();
        configValues.setStorageDirectory(null);
        configValues.setStorageFilesystem(new StorageFilesystem());
        configValues.setBackupDirectory("backup/dir");

        Map<String, String> result = converter.convertConfigurationToMap(configValues);

        assertNull(result.get(EclipseStoreConfigConverter.STORAGE_DIRECTORY));
        assertNotNull(result.get(EclipseStoreConfigConverter.BACKUP_DIRECTORY));
    }

    @Test
    void testComposeKey() {
        String result = converter.composeKey("prefix", "suffix");
        assertEquals("prefix.suffix", result);
    }
}
