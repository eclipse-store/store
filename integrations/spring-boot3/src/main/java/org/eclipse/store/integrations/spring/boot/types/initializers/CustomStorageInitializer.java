package org.eclipse.store.integrations.spring.boot.types.initializers;

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

/**
 * Interface for custom storage initializers.
 * Implementations of this interface can provide custom initialization logic
 * that will be executed before the creation of the storage foundation.
 */
public interface CustomStorageInitializer
{
    /**
     * Method to be implemented with custom initialization logic.
     * This method will be called immediately before the storage foundation creation process.
     */
    void initialize();
}
