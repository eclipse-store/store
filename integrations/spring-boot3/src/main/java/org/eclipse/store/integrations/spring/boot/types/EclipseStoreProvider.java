package org.eclipse.store.integrations.spring.boot.types;

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

import org.eclipse.store.integrations.spring.boot.types.configuration.ConfigurationPair;
import org.eclipse.store.integrations.spring.boot.types.configuration.EclipseStoreProperties;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageFoundation;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;

/**
 * The {@code EclipseStoreProvider} interface is responsible for the creation of the EmbeddedStorageManager and EmbeddedStorageFoundation instances.
 * It provides methods for creating the EmbeddedStorageManager and EmbeddedStorageFoundation instances based on the provided configuration.
 *
 * @deprecated this class is deprecated in favour of {@link org.eclipse.store.integrations.spring.boot.types.factories.EmbeddedStorageFoundationFactory}
 * and {@link org.eclipse.store.integrations.spring.boot.types.factories.EmbeddedStorageManagerFactory} and will be removed in future releases.
 */
@Deprecated(since = "1.2.0", forRemoval = true)
public interface EclipseStoreProvider
{
    /**
     * Creates an {@code EmbeddedStorageManager} using the provided configuration.
     *
     * @param eclipseStoreProperties  Configuration file structure representing configuration elements mapped by Spring Configuration.
     * @param additionalConfiguration Optional additional parameters that allow the inclusion of configuration keys not present in {@code EclipseStoreProperties}.
     * @return A new {@code EmbeddedStorageManager} instance based on the provided configuration.
     * @deprecated please use {@link org.eclipse.store.integrations.spring.boot.types.factories.EmbeddedStorageManagerFactory}
     */
    @Deprecated(since = "1.2.0", forRemoval = true)
    EmbeddedStorageManager createStorage(EclipseStoreProperties eclipseStoreProperties, ConfigurationPair... additionalConfiguration);

    /**
     * Creates an {@code EmbeddedStorageManager} using a pre-configured foundation. This method is beneficial when additional configuration for the foundation is required.
     *
     * @param foundation The {@code EmbeddedStorageFoundation} to be configured before calling this method.
     * @param autoStart  Determines whether the newly created {@code EmbeddedStorageManager} should start directly after creation.
     * @return A new {@code EmbeddedStorageManager} instance based on the provided {@code EmbeddedStorageFoundation}.
     * @deprecated please use {@link org.eclipse.store.integrations.spring.boot.types.factories.EmbeddedStorageManagerFactory}
     */
    @Deprecated(since = "1.2.0", forRemoval = true)
    EmbeddedStorageManager createStorage(EmbeddedStorageFoundation<?> foundation, boolean autoStart);


    /**
     * Creates an {@code EmbeddedStorageFoundation} using the provided configuration. This method should be called when the additional configuration for the foundation is required.
     *
     * @param eclipseStoreProperties  Configuration file structure representing configuration elements mapped by Spring Configuration.
     * @param additionalConfiguration Optional additional parameters that allow the inclusion of configuration keys not present in {@code EclipseStoreProperties}.
     * @return A new {@code EmbeddedStorageFoundation} instance based on the provided configuration.
     * @deprecated please use {@link org.eclipse.store.integrations.spring.boot.types.factories.EmbeddedStorageFoundationFactory}
     */
    @Deprecated(since = "1.2.0", forRemoval = true)
    EmbeddedStorageFoundation<?> createStorageFoundation(EclipseStoreProperties eclipseStoreProperties, ConfigurationPair... additionalConfiguration);


}
