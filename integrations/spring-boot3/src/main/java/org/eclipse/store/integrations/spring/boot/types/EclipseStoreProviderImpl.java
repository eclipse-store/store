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

import java.util.Map;

import org.eclipse.serializer.persistence.binary.jdk17.types.BinaryHandlersJDK17;
import org.eclipse.serializer.persistence.binary.jdk8.types.BinaryHandlersJDK8;
import org.eclipse.serializer.util.logging.Logging;
import org.eclipse.store.integrations.spring.boot.types.configuration.ConfigurationPair;
import org.eclipse.store.integrations.spring.boot.types.configuration.EclipseStoreProperties;
import org.eclipse.store.integrations.spring.boot.types.converter.EclipseStoreConfigConverter;
import org.eclipse.store.storage.embedded.configuration.types.EmbeddedStorageConfigurationBuilder;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageFoundation;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.slf4j.Logger;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class EclipseStoreProviderImpl implements EclipseStoreProvider
{
    private final EclipseStoreConfigConverter converter;
    private final ApplicationContext applicationContext;

    private final Logger logger = Logging.getLogger(EclipseStoreProviderImpl.class);

    public EclipseStoreProviderImpl(final EclipseStoreConfigConverter converter, final ApplicationContext applicationContext)
    {
        this.converter = converter;
        this.applicationContext = applicationContext;
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
        final EmbeddedStorageManager storageManager = foundation.createEmbeddedStorageManager();
        if (autoStart)
        {
            storageManager.start();
        }
        return storageManager;
    }

    @Override
    public EmbeddedStorageFoundation<?> createStorageFoundation(final EclipseStoreProperties eclipseStoreProperties, final ConfigurationPair... additionalConfiguration)
    {

        final EmbeddedStorageConfigurationBuilder builder = EmbeddedStorageConfigurationBuilder.New();
        final Map<String, String> valueMap = this.converter.convertConfigurationToMap(eclipseStoreProperties);
        for (final ConfigurationPair pair : additionalConfiguration) {
            valueMap.put(pair.key(), pair.value());
        }

        this.logger.debug("EclipseStore configuration items: ");
        valueMap.forEach((key, value) ->
        {
            if (value != null)
            {
                final String logValue = key.contains("password") ? "xxxxxx" : value;
                this.logger.debug(key + " : " + logValue);
                builder.set(key, value);
            }
        });

        final EmbeddedStorageFoundation<?> storageFoundation = builder.createEmbeddedStorageFoundation();

        final Object root = this.provideRoot(eclipseStoreProperties);
        if (root != null)
        {
            this.logger.debug("Root object: " + root.getClass().getName());
            storageFoundation.setRoot(root);
        }

        storageFoundation.getConnectionFoundation()
                .setClassLoaderProvider(typeName -> this.applicationContext.getClassLoader());

        if (eclipseStoreProperties.isRegisterJdk8Handlers()) {
            this.logger.debug("Register JDK8 handlers. ");
            storageFoundation.onConnectionFoundation(BinaryHandlersJDK8::registerJDK8TypeHandlers);
        }

        if (eclipseStoreProperties.isRegisterJdk17Handlers()) {
            this.logger.debug("Register JDK17 handlers. ");
            storageFoundation.onConnectionFoundation(BinaryHandlersJDK17::registerJDK17TypeHandlers);
        }

        return storageFoundation;
    }

    private Object provideRoot(final EclipseStoreProperties properties)
    {
        final Class<?> rootClass = properties.getRoot();
        if (rootClass == null)
        {
            return null;
        }
        try
        {
            return rootClass.getDeclaredConstructor().newInstance();
        } catch (final Exception e)
        {
            throw new RuntimeException("Failed to instantiate class: " + rootClass, e);
        }
    }

}
