
package org.eclipse.store.integrations.cdi.types.config;

/*-
 * #%L
 * EclipseStore Integrations CDI 4
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



import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.Config;
import org.eclipse.store.integrations.cdi.ConfigurationCoreProperties;
import org.eclipse.store.integrations.cdi.types.extension.StorageExtension;
import org.eclipse.store.storage.embedded.configuration.types.EmbeddedStorageConfigurationBuilder;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageFoundation;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.types.StorageManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@ApplicationScoped
public class StorageManagerProducer
{
    private static final Logger LOGGER = LoggerFactory.getLogger(StorageManagerProducer.class);

    @Inject
    private Config config;

    @Inject
    private StorageExtension storageExtension;

    @Inject
    private Instance<EmbeddedStorageFoundationCustomizer> customizers;

    @Inject
    private Instance<StorageManagerInitializer> initializers;

    @Produces
    @ApplicationScoped
    public StorageManager getStorageManager()
    {

        if (this.storageExtension.getStorageManagerConfigInjectionNames()
                .isEmpty())
        {
            return this.storageManagerFromProperties();
        }

        // StorageManager through StorageManagerConverter
        final String configName = this.storageExtension.getStorageManagerConfigInjectionNames()
                .iterator()
                .next();
        LOGGER.info("Loading StorageManager from file indicated by MicroProfile Config key : " + configName);

        // This will succeed since it is already validated during deployment of the application.
        return this.config.getValue(configName, StorageManager.class);
    }

    private EmbeddedStorageManager storageManagerFromProperties()
    {
        final Map<String, String> properties = ConfigurationCoreProperties.getProperties(this.config);
        LOGGER.info("Loading default StorageManager from MicroProfile Config properties. The keys: " + properties.keySet());

        final EmbeddedStorageConfigurationBuilder builder = EmbeddedStorageConfigurationBuilder.New();
        for (final Map.Entry<String, String> entry : properties.entrySet())
        {
            builder.set(entry.getKey(), entry.getValue());
        }
        final EmbeddedStorageFoundation<?> foundation = builder.createEmbeddedStorageFoundation();
        foundation.setDataBaseName("Generic");

        this.customizers.stream()
                .forEach(customizer -> customizer.customize(foundation));

        final EmbeddedStorageManager storageManager = foundation
                .createEmbeddedStorageManager();

        if (this.isAutoStart(properties))
        {
            storageManager.start();
        }

        if (!this.storageExtension.hasStorageRoot())
        {
            // Only execute at this point when no storage root bean has defined with @Storage
            // Initializers are called from StorageBean.create if user has defined @Storage and root is read.
            this.initializers.stream()
                    .forEach(initializer -> initializer.initialize(storageManager));
        }

        return storageManager;
    }

    private boolean isAutoStart(final Map<String, String> properties)
    {
        return Boolean.parseBoolean(properties.getOrDefault("autoStart", "true"));

    }

    public void dispose(@Disposes final StorageManager manager)
    {
        LOGGER.info("Closing the default StorageManager");
        manager.close();
    }
}
