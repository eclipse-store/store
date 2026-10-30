package one.microstream.test.configuration.notes;

/*-
 * #%L
 * EclipseStore Integration Tests - Skills - configuration
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

import java.nio.file.Path;
import java.time.Duration;

import org.eclipse.store.storage.embedded.configuration.types.EmbeddedStorageConfiguration;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.types.StorageWriteControllerReadOnlyMode;

/**
 * Three ways to configure the same Notes storage. All three start a manager and
 * leave it ready for {@link NotesService} to take over.
 */
public final class NotesStorageFactories
{
    private NotesStorageFactories() {}

    /** Pattern A in SKILL.md — fluent builder. */
    public static EmbeddedStorageManager fluent(Path storageDir, Path backupDir)
    {
        EmbeddedStorageManager storage = EmbeddedStorageConfiguration.Builder()
            .setStorageDirectory(storageDir.toString())
            .setBackupDirectory(backupDir.toString())
            .setChannelCount(2)
            .setHousekeepingInterval(Duration.ofSeconds(1))
            .setHousekeepingTimeBudget(Duration.ofMillis(10))
            .createEmbeddedStorageFoundation()
            .createEmbeddedStorageManager();
        storage.start();
        return storage;
    }

    /** Pattern B in SKILL.md — INI file from the classpath. */
    public static EmbeddedStorageManager ini()
    {
        EmbeddedStorageManager storage = EmbeddedStorageConfiguration
            .load("META-INF/eclipsestore/storage.ini")
            .createEmbeddedStorageFoundation()
            .createEmbeddedStorageManager();
        storage.start();
        return storage;
    }

    /** Pattern E in SKILL.md — open an existing directory read-only. */
    public static EmbeddedStorageManager readOnly(Path storageDir)
    {
        var foundation = EmbeddedStorageConfiguration.Builder()
            .setStorageDirectory(storageDir.toString())
            .setChannelCount(2)
            .createEmbeddedStorageFoundation();

        var roCtl = new StorageWriteControllerReadOnlyMode(foundation.getWriteController());
        foundation.setWriteController(roCtl);

        EmbeddedStorageManager storage = foundation.createEmbeddedStorageManager();
        storage.start();
        return storage;
    }
}
