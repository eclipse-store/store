package org.eclipse.store.demo.countries.config;

/*-
 * #%L
 * EclipseStore Demo Country Explorer
 * %%
 * Copyright (C) 2023 - 2026 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import org.eclipse.store.demo.countries.index.LocationIndex;
import org.eclipse.store.demo.countries.index.CountryIndices;
import org.eclipse.store.demo.countries.model.Country;
import org.eclipse.store.gigamap.types.GigaMap;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GigaMapConfig
{
	@Bean
	public GigaMap<Country> countryGigaMap()
	{
		final GigaMap<Country> gigaMap = GigaMap.New();
		gigaMap.index().bitmap().addAll(
			CountryIndices.LOCATION,
			CountryIndices.CONTINENT,
			CountryIndices.NAME,
			CountryIndices.ALPHA2
		);
		return gigaMap;
	}

	@Bean
	public LocationIndex locationIndex()
	{
		return CountryIndices.LOCATION;
	}
}
