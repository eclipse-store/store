package org.eclipse.store.integrations.spring.boot.types.factories.legacy;

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

import org.eclipse.serializer.reflect.ClassLoaderProvider;
import org.eclipse.store.integrations.spring.boot.types.EclipseStoreProvider;
import org.eclipse.store.integrations.spring.boot.types.configuration.ConfigurationPair;
import org.eclipse.store.integrations.spring.boot.types.configuration.EclipseStoreProperties;
import org.eclipse.store.integrations.spring.boot.types.converter.EclipseStoreConfigConverter;
import org.eclipse.store.integrations.spring.boot.types.factories.EmbeddedStorageFoundationFactory;
import org.eclipse.store.integrations.spring.boot.types.factories.EmbeddedStorageManagerFactory;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageFoundation;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;


@SuppressWarnings("removal")
@Deprecated
public class EclipseStoreProviderImpl implements EclipseStoreProvider
{

    private final EmbeddedStorageFoundationFactory embeddedStorageFoundationFactory;
    private final EmbeddedStorageManagerFactory embeddedStorageManagerFactory;

    public EclipseStoreProviderImpl(final EclipseStoreConfigConverter converter, final ClassLoaderProvider classLoaderProvider)
    {
        this.embeddedStorageFoundationFactory = new EmbeddedStorageFoundationFactory(converter, classLoaderProvider);
        this.embeddedStorageManagerFactory = new EmbeddedStorageManagerFactory();
    }

    @Override
    public EmbeddedStorageManager createStorage(final EclipseStoreProperties eclipseStoreProperties, final ConfigurationPair... additionalConfiguration)
    {
        final EmbeddedStorageFoundation<?> storageFoundation = this.createStorageFoundation(eclipseStoreProperties, additionalConfiguration);
        return this.createStorage(storageFoundation, eclipseStoreProperties.isAutoStart());
    }

    @Override
    public EmbeddedStorageManager createStorage(final EmbeddedStorageFoundation<?> foundation, final boolean autoStart)
    {
        return embeddedStorageManagerFactory.createStorage(foundation, autoStart);
    }

    @Override
    public EmbeddedStorageFoundation<?> createStorageFoundation(final EclipseStoreProperties eclipseStoreProperties, final ConfigurationPair... additionalConfiguration)
    {
        return this.embeddedStorageFoundationFactory.createStorageFoundation(eclipseStoreProperties, additionalConfiguration);
    }

}
