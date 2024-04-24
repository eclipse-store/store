package org.eclipse.store.integrations.spring.boot.types.suppliers;

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

import org.eclipse.store.storage.embedded.types.EmbeddedStorageFoundation;

@FunctionalInterface
public interface EmbeddedStorageFoundationSupplier<T extends EmbeddedStorageFoundation<?>> extends java.util.function.Supplier<T>
{
}
