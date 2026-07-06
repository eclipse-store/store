package org.eclipse.store.integrations.spring.boot.types.converter;

/*-
 * #%L
 * EclipseStore Integrations SpringBoot
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

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.eclipse.store.integrations.spring.boot.types.configuration.EclipseStoreProperties;
import org.eclipse.store.integrations.spring.boot.types.configuration.StorageFilesystem;
import org.eclipse.store.integrations.spring.boot.types.configuration.aws.Aws;
import org.eclipse.store.integrations.spring.boot.types.configuration.aws.S3;
import org.eclipse.store.integrations.spring.boot.types.configuration.azure.Azure;
import org.eclipse.store.integrations.spring.boot.types.configuration.googlecloud.Googlecloud;
import org.eclipse.store.integrations.spring.boot.types.configuration.oraclecloud.ObjectStorage;
import org.eclipse.store.integrations.spring.boot.types.configuration.oraclecloud.Oraclecloud;
import org.junit.jupiter.api.Test;

/**
 * Partially populated backend configurations must convert without a
 * {@code NullPointerException} — optional nested nodes (credentials, client,
 * config-file, ...) are simply absent from the resulting map. Before the null
 * guards, e.g. an S3 configuration relying on environment-provided credentials
 * (a plain endpoint-override + region setup) crashed the startup with an NPE
 * inside the converter.
 */
class ConverterPartialConfigurationTest
{
    private final EclipseStoreConfigConverter converter = new EclipseStoreConfigConverter();

    @Test
    void s3WithoutCredentialsConverts()
    {
        final S3 s3 = new S3();
        s3.setEndpointOverride("http://minio:9000");
        s3.setRegion("eu-central-1");
        final Aws aws = new Aws();
        aws.setS3(s3);

        final Map<String, String> map = this.convertWith(fs -> fs.setAws(aws));

        assertEquals("http://minio:9000", map.get("storage-filesystem.aws.s3.endpoint-override"));
        assertEquals("eu-central-1", map.get("storage-filesystem.aws.s3.region"));
        assertFalse(map.containsKey("storage-filesystem.aws.s3.credentials.type"));
    }

    @Test
    void azureWithoutCredentialsConverts()
    {
        final Azure azure = new Azure();
        final org.eclipse.store.integrations.spring.boot.types.configuration.azure.Storage storage =
                new org.eclipse.store.integrations.spring.boot.types.configuration.azure.Storage();
        storage.setConnectionString("UseDevelopmentStorage=true");
        azure.setStorage(storage);

        final Map<String, String> map = this.convertWith(fs -> fs.setAzure(azure));

        assertEquals("UseDevelopmentStorage=true", map.get("storage-filesystem.azure.storage.connection-string"));
        assertFalse(map.containsKey("storage-filesystem.azure.storage.credentials.type"));
    }

    @Test
    void azureWithoutStorageNodeConverts()
    {
        assertDoesNotThrow(() -> this.convertWith(fs -> fs.setAzure(new Azure())));
    }

    @Test
    void oracleCloudWithoutConfigFileAndClientConverts()
    {
        final ObjectStorage objectStorage = new ObjectStorage();
        objectStorage.setRegion("eu-frankfurt-1");
        objectStorage.setEndpoint("https://objectstorage.example");
        final Oraclecloud oraclecloud = new Oraclecloud();
        oraclecloud.setObjectStorage(objectStorage);

        final Map<String, String> map = this.convertWith(fs -> fs.setOraclecloud(oraclecloud));

        assertEquals("eu-frankfurt-1", map.get("storage-filesystem.oraclecloud.object-storage.region"));
        assertFalse(map.containsKey("storage-filesystem.oraclecloud.object-storage.config-file.path"));
    }

    @Test
    void oracleCloudWithoutObjectStorageNodeConverts()
    {
        assertDoesNotThrow(() -> this.convertWith(fs -> fs.setOraclecloud(new Oraclecloud())));
    }

    @Test
    void googleCloudWithoutFirestoreNodeConverts()
    {
        assertDoesNotThrow(() -> this.convertWith(fs -> fs.setGooglecloud(new Googlecloud())));
    }

    private Map<String, String> convertWith(final java.util.function.Consumer<StorageFilesystem> filesystemSetup)
    {
        final EclipseStoreProperties properties = new EclipseStoreProperties();
        final StorageFilesystem filesystem = new StorageFilesystem();
        filesystemSetup.accept(filesystem);
        properties.setStorageFilesystem(filesystem);
        return this.converter.convertConfigurationToMap(properties);
    }
}
