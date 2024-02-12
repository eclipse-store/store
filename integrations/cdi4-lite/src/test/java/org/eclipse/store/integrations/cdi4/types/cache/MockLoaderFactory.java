package org.eclipse.store.integrations.cdi4.types.cache;

/*-
 * #%L
 * Eclipse Store Integrations CDI 4 - lite
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



import javax.cache.configuration.Factory;
import javax.cache.integration.CacheLoader;


public class MockLoaderFactory<V, K> implements Factory<CacheLoader<K, V>>
{
	@Override
	public CacheLoader<K, V> create()
	{
		return null;
	}
}
