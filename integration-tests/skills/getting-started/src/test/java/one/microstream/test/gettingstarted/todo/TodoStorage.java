package one.microstream.test.gettingstarted.todo;

/*-
 * #%L
 * EclipseStore Integration Tests - Skills - getting-started
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

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageFoundation;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.types.Storage;
import org.eclipse.store.storage.types.StorageConfiguration;

/**
 * Three bootstrap patterns for the todo store, each producing a started
 * {@link EmbeddedStorageManager}. Mirrors the patterns in the
 * {@code getting-started} skill (SKILL.md).
 */
public final class TodoStorage
{
    private TodoStorage()
    {
        // factory holder
    }

    /**
     * Pattern A — canonical two-argument {@code start(root, dir)}.
     */
    public static EmbeddedStorageManager startTwoArg(final TodoRoot root, final Path directory)
    {
        return EmbeddedStorage.start(root, directory);
    }

    /**
     * Pattern B — Foundation pattern with channel count = 2.
     */
    public static EmbeddedStorageManager startFoundation(final TodoRoot root, final Path directory)
    {
        final StorageConfiguration config = StorageConfiguration.Builder()
            .setStorageFileProvider(Storage.FileProvider(directory))
            .setChannelCountProvider(Storage.ChannelCountProvider(2))
            .createConfiguration();

        final EmbeddedStorageFoundation<?> foundation = EmbeddedStorage.Foundation(config);
        return foundation.start(root);
    }

    /**
     * Pattern C — start with no root, then {@code ensureRoot(supplier)}.
     * Supplier is invoked only on a fresh DB; on a populated DB the loaded
     * root is returned unchanged.
     */
    public static EmbeddedStorageManager startEnsureRoot(
        final java.util.function.Supplier<TodoRoot> supplier,
        final Path directory)
    {
        final EmbeddedStorageManager manager = EmbeddedStorage.start(directory);
        manager.ensureRoot(supplier);
        return manager;
    }
}
