package org.eclipse.store.storage.restservice.javalin.types;

/*-
 * #%L
 * service-javalin
 * %%
 * Copyright (C) 2023 - 2025 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import org.eclipse.store.storage.restadapter.types.StorageRestAdapter;
import org.eclipse.store.storage.restservice.types.StorageRestService;
import org.eclipse.store.storage.restservice.types.StorageRestServiceProvider;

public class StorageRestServiceJavalinProvider implements StorageRestServiceProvider
{

	@Override
	public StorageRestService provideService(StorageRestAdapter adapter)
	{
		return StorageRestServiceJavalinJava.New(adapter);
	}
}
