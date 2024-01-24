package org.eclipse.store.examples.reloader;

/*-
 * #%L
 * EclipseStore Example Reloader
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

import org.eclipse.store.storage.embedded.configuration.types.EmbeddedStorageConfiguration;
import org.eclipse.store.storage.types.StorageManager;

public class StorageProvider
{

    public static StorageManager createStorageManager(final String storageDirectory, final Object root) {

        return EmbeddedStorageConfiguration.Builder()
                .setStorageDirectory(storageDirectory)

                .createEmbeddedStorageFoundation()
                .setRoot(root)

                .createEmbeddedStorageManager();
    }
}
