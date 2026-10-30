package one.microstream.test.housekeeping;

/*-
 * #%L
 * EclipseStore Integration Tests - Skills - housekeeping
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
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;

/**
 * Bootstrap helper for the housekeeping-and-deletion skill review. Mirrors
 * the canonical two-argument start pattern from the {@code getting-started}
 * skill.
 */
public final class OrdersStorage
{
    private OrdersStorage()
    {
        // factory holder
    }

    public static EmbeddedStorageManager start(final OrdersRoot root, final Path directory)
    {
        return EmbeddedStorage.start(root, directory);
    }
}
