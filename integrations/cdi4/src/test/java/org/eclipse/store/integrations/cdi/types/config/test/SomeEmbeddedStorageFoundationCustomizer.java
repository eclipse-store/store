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


import org.eclipse.store.integrations.cdi.types.config.EmbeddedStorageFoundationCustomizer;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageFoundation;

@ApplicationScoped
public class SomeEmbeddedStorageFoundationCustomizer implements EmbeddedStorageFoundationCustomizer
{
    private boolean customizeCalled;

    @Override
    public void customize(final EmbeddedStorageFoundation<?> embeddedStorageFoundation)
    {
        this.customizeCalled = true;
    }

    public boolean isCustomizeCalled()
    {
        return customizeCalled;
    }
}
