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



import io.smallrye.config.inject.ConfigExtension;
import org.eclipse.store.integrations.cdi4.types.config.StorageManagerProducer;
import org.eclipse.store.integrations.cdi4.types.extension.StorageExtension;
import org.eclipse.store.integrations.cdi4.types.cache.StorageCache;
import org.eclipse.store.integrations.cdi4.types.cache.StorageCacheProducer;
import org.jboss.weld.junit5.auto.AddBeanClasses;
import org.jboss.weld.junit5.auto.AddExtensions;
import org.jboss.weld.junit5.auto.EnableAutoWeld;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.cache.CacheManager;
import javax.cache.spi.CachingProvider;
import jakarta.inject.Inject;


@EnableAutoWeld
@AddBeanClasses({StorageCacheProducer.class, StorageManagerProducer.class})  // For @StorageCache
@AddExtensions({StorageExtension.class, ConfigExtension.class})
// SmallRye Config extension And MicroStream extension for StorageManager
public class CacheProducerTest
{
	@Inject
	@StorageCache
	private CachingProvider provider;
	
	@Inject
	@StorageCache
	private CacheManager    cacheManager;
	
	@Test
	public void shouldNotBeNullProvider()
	{
		Assertions.assertNotNull(this.provider);
	}
	
	@Test
	public void shouldNotBeNullManager()
	{
		Assertions.assertNotNull(this.cacheManager);
	}
	
}
