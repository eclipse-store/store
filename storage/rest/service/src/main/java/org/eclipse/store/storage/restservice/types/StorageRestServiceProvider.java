package org.eclipse.store.storage.restservice.types;

/*-
 * #%L
 * EclipseStore Storage REST Service
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

import org.eclipse.store.storage.restadapter.types.StorageRestAdapter;


@FunctionalInterface
public interface StorageRestServiceProvider
{
	/**
	 * Return a StorageRestService instance initialized with the provided StorageRestAdapter.
	 * This method is required for the RestServiceResolver.
	 *
	 * @param adapter the adapter to initialize the service with
	 * @return StorageRestService instance
	 */
	public StorageRestService provideService(final StorageRestAdapter adapter);
}
