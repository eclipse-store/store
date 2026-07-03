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
import org.eclipse.store.integrations.spring.boot.types.configuration.redis.Redis;
import org.junit.jupiter.api.Test;

/**
 * Regression test: the Redis URI must be emitted under the composed filesystem
 * key ({@code storage-filesystem.redis.uri} / {@code backup-filesystem.redis.uri})
 * like every other backend. It used to be emitted under the bare {@code redis.uri}
 * key, which the storage configuration silently ignores — the storage then
 * started on the default NIO backend instead of Redis, and a storage- and a
 * backup-filesystem Redis configuration collided on the single bare key.
 */
class RedisFilesystemKeyTest
{
    private final EclipseStoreConfigConverter converter = new EclipseStoreConfigConverter();

    @Test
    void storageFilesystemRedisUriIsPrefixed()
    {
        final EclipseStoreProperties properties = new EclipseStoreProperties();
        final StorageFilesystem filesystem = new StorageFilesystem();
        final Redis redis = new Redis();
        redis.setUri("redis://storage-host:6379");
        filesystem.setRedis(redis);
        properties.setStorageFilesystem(filesystem);

        final Map<String, String> map = this.converter.convertConfigurationToMap(properties);

        assertNull(map.get("redis.uri"),
                "Redis URI must not be emitted under the bare, prefix-less key");
        assertEquals("redis://storage-host:6379", map.get("storage-filesystem.redis.uri"));
    }

    @Test
    void backupFilesystemRedisUriIsPrefixedAndDoesNotCollide()
    {
        final EclipseStoreProperties properties = new EclipseStoreProperties();

        final StorageFilesystem storageFs = new StorageFilesystem();
        final Redis storageRedis = new Redis();
        storageRedis.setUri("redis://storage-host:6379");
        storageFs.setRedis(storageRedis);
        properties.setStorageFilesystem(storageFs);

        final StorageFilesystem backupFs = new StorageFilesystem();
        final Redis backupRedis = new Redis();
        backupRedis.setUri("redis://backup-host:6379");
        backupFs.setRedis(backupRedis);
        properties.setBackupFilesystem(backupFs);

        final Map<String, String> map = this.converter.convertConfigurationToMap(properties);

        assertEquals("redis://storage-host:6379", map.get("storage-filesystem.redis.uri"));
        assertEquals("redis://backup-host:6379", map.get("backup-filesystem.redis.uri"),
                "storage and backup Redis configurations must not collide on one key");
    }
}
