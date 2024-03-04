package org.eclipse.store.integrations.spring.boot.types.factories;

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

import org.eclipse.serializer.persistence.binary.jdk17.types.BinaryHandlersJDK17;
import org.eclipse.serializer.persistence.binary.jdk8.types.BinaryHandlersJDK8;
import org.eclipse.serializer.reflect.ClassLoaderProvider;
import org.eclipse.serializer.util.logging.Logging;
import org.eclipse.store.integrations.spring.boot.types.configuration.ConfigurationPair;
import org.eclipse.store.integrations.spring.boot.types.configuration.EclipseStoreProperties;
import org.eclipse.store.integrations.spring.boot.types.converter.EclipseStoreConfigConverter;
import org.eclipse.store.storage.embedded.configuration.types.EmbeddedStorageConfigurationBuilder;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageFoundation;
import org.slf4j.Logger;

import java.util.Map;


/**
 * The {@code EmbeddedStorageFoundationFactory} is responsible for the creation of the EmbeddedStorageFoundation instances.
 * It provides methods for creating EmbeddedStorageFoundation instances based on the provided configuration.
 *
 * @since 1.2.0
 */
public class EmbeddedStorageFoundationFactory
{
    private final EclipseStoreConfigConverter converter;
    private final ClassLoaderProvider classLoaderProvider;

    private final Logger logger = Logging.getLogger(EmbeddedStorageFoundationFactory.class);

    public EmbeddedStorageFoundationFactory(final EclipseStoreConfigConverter converter, final ClassLoaderProvider classLoaderProvider)
    {
        this.converter = converter;
        this.classLoaderProvider = classLoaderProvider;
    }

    /**
     * Creates an {@code EmbeddedStorageFoundation} using the provided configuration. This method should be called when the additional configuration for the foundation is required.
     *
     * @param eclipseStoreProperties  Configuration file structure representing configuration elements mapped by Spring Configuration.
     * @param additionalConfiguration Optional additional parameters that allow the inclusion of configuration keys not present in {@code EclipseStoreProperties}.
     * @return A new {@code EmbeddedStorageFoundation} instance based on the provided configuration.
     */
    public EmbeddedStorageFoundation<?> createStorageFoundation(final EclipseStoreProperties eclipseStoreProperties, final ConfigurationPair... additionalConfiguration)
    {
        final EmbeddedStorageConfigurationBuilder builder = EmbeddedStorageConfigurationBuilder.New();
        final Map<String, String> valueMap = this.converter.convertConfigurationToMap(eclipseStoreProperties);
        for (final ConfigurationPair pair : additionalConfiguration)
        {
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

        storageFoundation.getConnectionFoundation().setClassLoaderProvider(classLoaderProvider);

        final Object root = this.createNewRootInstance(eclipseStoreProperties);
        if (root != null)
        {
            this.logger.debug("Root object: " + root.getClass().getName());
            storageFoundation.setRoot(root);
        }

        if (eclipseStoreProperties.isRegisterJdk8Handlers())
        {
            this.logger.debug("Register JDK8 handlers. ");
            storageFoundation.onConnectionFoundation(BinaryHandlersJDK8::registerJDK8TypeHandlers);
        }

        if (eclipseStoreProperties.isRegisterJdk17Handlers())
        {
            this.logger.debug("Register JDK17 handlers. ");
            storageFoundation.onConnectionFoundation(BinaryHandlersJDK17::registerJDK17TypeHandlers);
        }

        return storageFoundation;
    }

    protected Object createNewRootInstance(final EclipseStoreProperties properties)
    {
        var rootClass = properties.getRoot();
        if (rootClass == null)
        {
            return null;
        }
        try
        {
            return rootClass.getDeclaredConstructor().newInstance();
        } catch (final Exception e)
        {
            throw new RuntimeException("Failed to instantiate storage root: " + rootClass, e);
        }
    }

}
