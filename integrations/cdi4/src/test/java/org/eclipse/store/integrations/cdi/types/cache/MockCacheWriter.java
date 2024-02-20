package org.eclipse.store.integrations.cdi.types.cache;

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

import javax.cache.configuration.Factory;
import javax.cache.integration.CacheWriter;


public class MockCacheWriter<V, K> implements Factory<CacheWriter<K, V>>
{
	@Override
	public CacheWriter<K, V> create()
	{
		return null;
	}
}
