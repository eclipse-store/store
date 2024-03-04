package org.eclipse.store.integrations.spring.boot.types.factories;

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

import org.eclipse.store.storage.embedded.types.EmbeddedStorageFoundation;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;

/**
 * The {@code EmbeddedStorageManagerFactory} is responsible for the creation of the EmbeddedStorageManager instances.
 * It provides methods for creating the EmbeddedStorageManager instances based on the provided foundation.
 *
 * @since 1.2.0
 */
public class EmbeddedStorageManagerFactory
{

    /**
     * Creates an {@code EmbeddedStorageManager} using a pre-configured foundation. This method is beneficial when additional configuration for the foundation is required.
     *
     * @param foundation The {@code EmbeddedStorageFoundation} to be configured before calling this method.
     * @param autoStart  Determines whether the newly created {@code EmbeddedStorageManager} should start directly after creation.
     * @return A new {@code EmbeddedStorageManager} instance based on the provided {@code EmbeddedStorageFoundation}.
     */
    public EmbeddedStorageManager createStorage(final EmbeddedStorageFoundation<?> foundation, final boolean autoStart)
    {
        final EmbeddedStorageManager storageManager = foundation.createEmbeddedStorageManager();
        if (autoStart)
        {
            storageManager.start();
        }
        return storageManager;
    }

}
