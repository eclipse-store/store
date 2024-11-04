package org.microstream.spring.boot.example.simple.initializer;

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

import org.eclipse.serializer.reference.Lazy;
import org.eclipse.serializer.reference.LazyReferenceManager;
import org.eclipse.store.integrations.spring.boot.types.initializers.StorageContextInitializer;
import org.springframework.stereotype.Component;

/**
 * This class is for demonstration purposes only. It shows how to execute code before storage is initialized.
 * <a href="https://docs.eclipsestore.io/manual/storage/loading-data/lazy-loading/clearing-lazy-references.html">...</a>
 */
@Component
public class StorageContextInitializerImpl implements StorageContextInitializer
{
    @Override
    public void initialize()
    {
        LazyReferenceManager.set(LazyReferenceManager.New(
                Lazy.Checker(
                        1_000_000, // timeout of lazy access
                        0.75                       // memory quota
                )));
    }
}
