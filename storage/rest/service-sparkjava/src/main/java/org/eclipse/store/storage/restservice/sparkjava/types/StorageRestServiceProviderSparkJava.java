package org.eclipse.store.storage.restservice.sparkjava.types;

/*-
 * #%L
 * EclipseStore Storage REST Service Sparkjava
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
import org.eclipse.store.storage.restservice.types.StorageRestService;
import org.eclipse.store.storage.restservice.types.StorageRestServiceProvider;


public class StorageRestServiceProviderSparkJava implements StorageRestServiceProvider
{
	public StorageRestServiceProviderSparkJava()
	{
		super();
	}

	@Override
	public StorageRestService provideService(
		final StorageRestAdapter adapter
	)
	{
		return StorageRestServiceSparkJava.New(adapter);
	}
	
}
