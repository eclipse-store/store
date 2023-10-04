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

/**
 * Service Provider Interface for server implementations using the api
 * provided by StorageRestAdapter interface.
 * <p>
 * Usage:
 * 1. create an own implementation of this interface
 * 2. load it by using the methods the the class RestServiceResolver
 *
 */
public interface StorageRestService
{
	/**
	 * Start the service
	 */
	public void start();

	/**
	 * stop the service
	 */
	public void stop();
}
