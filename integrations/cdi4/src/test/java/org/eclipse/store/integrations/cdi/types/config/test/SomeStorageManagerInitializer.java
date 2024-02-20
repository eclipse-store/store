package org.eclipse.store.integrations.cdi.types.config.test;

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


import org.eclipse.store.integrations.cdi.types.config.StorageManagerInitializer;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.store.storage.types.StorageManager;

@ApplicationScoped
public class SomeStorageManagerInitializer implements StorageManagerInitializer
{

    private boolean initializerCalled;

    private boolean managerRunning;

    @Override
    public void initialize(final StorageManager storageManager)
    {
        this.initializerCalled = true;
        this.managerRunning = storageManager.isRunning();
    }

    public boolean isInitializerCalled()
    {
        return initializerCalled;
    }

    public boolean isManagerRunning()
    {
        return managerRunning;
    }
}
