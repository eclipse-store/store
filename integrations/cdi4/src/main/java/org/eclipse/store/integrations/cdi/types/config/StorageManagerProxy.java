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


import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.function.Predicate;

import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.serializer.afs.types.AFile;
import org.eclipse.serializer.collections.types.XGettingEnum;
import org.eclipse.serializer.configuration.types.Configuration;
import org.eclipse.serializer.persistence.binary.types.Binary;
import org.eclipse.serializer.persistence.types.PersistenceManager;
import org.eclipse.serializer.persistence.types.PersistenceRootsView;
import org.eclipse.serializer.persistence.types.PersistenceTypeDictionaryExporter;
import org.eclipse.serializer.reference.UsageMarkable;
import org.eclipse.store.integrations.cdi.ConfigurationCoreProperties;
import org.eclipse.store.storage.embedded.configuration.types.EmbeddedStorageConfiguration;
import org.eclipse.store.storage.embedded.configuration.types.EmbeddedStorageConfigurationBuilder;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageFoundation;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.types.Database;
import org.eclipse.store.storage.types.StorageConfiguration;
import org.eclipse.store.storage.types.StorageConnection;
import org.eclipse.store.storage.types.StorageEntityCacheEvaluator;
import org.eclipse.store.storage.types.StorageEntityTypeExportFileProvider;
import org.eclipse.store.storage.types.StorageEntityTypeExportStatistics;
import org.eclipse.store.storage.types.StorageEntityTypeHandler;
import org.eclipse.store.storage.types.StorageLiveFileProvider;
import org.eclipse.store.storage.types.StorageManager;
import org.eclipse.store.storage.types.StorageRawFileStatistics;
import org.eclipse.store.storage.types.StorageTypeDictionary;

import jakarta.enterprise.inject.spi.CDI;


/**
 * For MicroProfile Config, at deployment time, we need to validate if @ConfigProperty is valid by
 * creating the StorageManager.  Since we need to look up the beans for {@link EmbeddedStorageFoundationCustomizer}
 * and {@link StorageManagerInitializer} we need a fully initialised bean Manager which we do not have.
 * And to avoid the creating of the StorageManager at deployment time, we have this proxy that
 * delays the creation of the StorageManager until first use.
 */
public class StorageManagerProxy extends UsageMarkable.Default implements StorageManager
{

    private static final Object LOCK = new Object();

    private final EmbeddedStorageFoundation<?> foundation;

    private StorageManager realStorageManager;

    public StorageManagerProxy(final String value)
    {
        // Already create the EmbeddedStorageFoundation to check if the config value points to a valid
        // location.
        final EmbeddedStorageConfigurationBuilder configurationBuilder = EmbeddedStorageConfiguration.load(value);

        final Configuration configuration = configurationBuilder.buildConfiguration();
        final String name = Optional.ofNullable(configuration.get("database-name")).orElse(value);

        this.foundation = configurationBuilder
                .createEmbeddedStorageFoundation();

        this.foundation.setDataBaseName(name);

    }

    private StorageManager getStorageManager()
    {
        synchronized (LOCK)
        {
            // Make sure this is not executed multi-threaded

            if (this.realStorageManager == null)
            {
                CDI.current()
                        .select(EmbeddedStorageFoundationCustomizer.class)
                        .stream()
                        .forEach(customizer -> customizer.customize(this.foundation));

                final EmbeddedStorageManager storageManager = this.foundation
                        .createEmbeddedStorageManager();

                if (this.isAutoStart())
                {
                    storageManager.start();
                }

                CDI.current()
                        .select(StorageManagerInitializer.class)
                        .stream()
                        .forEach(initializer -> initializer.initialize(storageManager));

                this.realStorageManager = storageManager;
            }
        }

        return this.realStorageManager;
    }

    private boolean isAutoStart()
    {
        return ConfigProvider.getConfig()
                .getOptionalValue(ConfigurationCoreProperties.Constants.PREFIX + "autoStart", Boolean.class)
                .orElse(Boolean.TRUE);
    }

    @Override
    public StorageConfiguration configuration()
    {
        return this.getStorageManager().configuration();
    }

    @Override
    public StorageTypeDictionary typeDictionary()
    {
        return this.getStorageManager().typeDictionary();
    }

    @Override
    public StorageManager start()
    {
        return this.getStorageManager().start();
    }

    @Override
    public boolean shutdown()
    {
        return this.getStorageManager().shutdown();
    }

    @Override
    public boolean isAcceptingTasks()
    {
        return this.getStorageManager().isAcceptingTasks();
    }

    @Override
    public boolean isRunning()
    {
        return this.getStorageManager().isRunning();
    }

    @Override
    public boolean isStartingUp()
    {
        return this.getStorageManager().isStartingUp();
    }

    @Override
    public boolean isShuttingDown()
    {
        return this.getStorageManager().isShuttingDown();
    }

    @Override
    public void checkAcceptingTasks()
    {
        this.getStorageManager().checkAcceptingTasks();
    }

    @Override
    public long initializationTime()
    {
        return this.getStorageManager().initializationTime();
    }

    @Override
    public long operationModeTime()
    {
        return this.getStorageManager().operationModeTime();
    }

    @Override
    public StorageConnection createConnection()
    {
        return this.getStorageManager().createConnection();
    }

    @Override
    public Object root()
    {
        return this.getStorageManager().root();
    }

    @Override
    public Object setRoot(final Object newRoot)
    {
        return this.getStorageManager().setRoot(newRoot);
    }

    @Override
    public long storeRoot()
    {
        return this.getStorageManager().storeRoot();
    }

    @Override
    public PersistenceRootsView viewRoots()
    {
        return this.getStorageManager().viewRoots();
    }

    @Override
    public Database database()
    {
        return this.getStorageManager().database();
    }

    @Override
    public boolean issueGarbageCollection(final long nanoTimeBudget)
    {
        return this.getStorageManager().issueGarbageCollection(nanoTimeBudget);
    }

    @Override
    public boolean issueFileCheck(final long nanoTimeBudget)
    {
        return this.getStorageManager().issueFileCheck(nanoTimeBudget);
    }

    @Override
    public boolean issueCacheCheck(final long nanoTimeBudget, final StorageEntityCacheEvaluator entityEvaluator)
    {
        return this.getStorageManager().issueCacheCheck(nanoTimeBudget, entityEvaluator);
    }

    @Override
    public void issueFullBackup(final StorageLiveFileProvider targetFileProvider, final PersistenceTypeDictionaryExporter typeDictionaryExporter)
    {
        this.getStorageManager().issueFullBackup(targetFileProvider, typeDictionaryExporter);
    }

    @Override
    public void issueTransactionsLogCleanup()
    {
        this.getStorageManager().issueTransactionsLogCleanup();
    }

    @Override
    public StorageRawFileStatistics createStorageStatistics()
    {
        return this.getStorageManager().createStorageStatistics();
    }

    @Override
    public void exportChannels(final StorageLiveFileProvider fileProvider, final boolean performGarbageCollection)
    {
        this.getStorageManager().exportChannels(fileProvider, performGarbageCollection);
    }

    @Override
    public StorageEntityTypeExportStatistics exportTypes(final StorageEntityTypeExportFileProvider exportFileProvider, final Predicate<? super StorageEntityTypeHandler> isExportType)
    {
        return this.getStorageManager().exportTypes(exportFileProvider, isExportType);
    }

    @Override
    public void importFiles(final XGettingEnum<AFile> importFiles)
    {
        this.getStorageManager().importFiles(importFiles);
    }

    @Override
    public void importData(final XGettingEnum<ByteBuffer> importData)
    {
        this.getStorageManager().importData(importData);
    }

    @Override
    public PersistenceManager<Binary> persistenceManager()
    {
        return this.getStorageManager().persistenceManager();
    }

    @Override
    public boolean isActive()
    {
        return this.getStorageManager().isActive();
    }
}
