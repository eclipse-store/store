package org.eclipse.store.cache.hibernate.types;

/*-
 * #%L
 * EclipseStore Cache for Hibernate
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

import java.util.Collections;

import org.hibernate.boot.registry.selector.SimpleStrategyRegistrationImpl;
import org.hibernate.boot.registry.selector.StrategyRegistration;
import org.hibernate.boot.registry.selector.StrategyRegistrationProvider;
import org.hibernate.cache.spi.RegionFactory;


public class CacheStrategyRegistrationProvider implements StrategyRegistrationProvider
{
	public CacheStrategyRegistrationProvider()
	{
		super();
	}

	@SuppressWarnings("rawtypes")
	@Override
	public Iterable<StrategyRegistration> getStrategyRegistrations()
	{
		SimpleStrategyRegistrationImpl<RegionFactory> registration = new SimpleStrategyRegistrationImpl<>(
			RegionFactory.class,
			CacheRegionFactory.class,
			"jcache",
			CacheRegionFactory.class.getName(),
			CacheRegionFactory.class.getSimpleName()
		);
		return Collections.singleton(registration);
	}
}
