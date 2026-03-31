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

/**
 * Spring configuration that creates and wires the
 * {@link GigaMap} instance used throughout the application.
 * <p>
 * The {@code GigaMap} is set up with four bitmap indices defined in
 * {@link CountryIndices}: a {@link LocationIndex spatial index} for geospatial
 * queries (nearby, bounding-box, hemisphere) and three
 * {@link org.eclipse.store.gigamap.types.IndexerString string indices} for
 * continent, country name, and ISO&nbsp;3166-1 alpha-2 code lookups.
 *
 * @see CountryIndices
 * @see LocationIndex
 */
@Configuration
public class GigaMapConfig
{
	/**
	 * Creates and configures the {@link GigaMap} bean for {@link Country} entities.
	 * <p>
	 * Registers the following bitmap indices:
	 * <ul>
	 *   <li>{@link CountryIndices#LOCATION} &ndash; spatial (latitude/longitude)</li>
	 *   <li>{@link CountryIndices#CONTINENT} &ndash; continent name</li>
	 *   <li>{@link CountryIndices#NAME} &ndash; country name</li>
	 *   <li>{@link CountryIndices#ALPHA2} &ndash; ISO&nbsp;3166-1 alpha-2 code</li>
	 * </ul>
	 *
	 * @return a fully configured {@code GigaMap} ready to accept country data
	 */
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

	/**
	 * Exposes the shared {@link LocationIndex} singleton as a Spring bean so that
	 * services can use it directly for spatial queries without going through the
	 * {@code GigaMap} index registry.
	 *
	 * @return the {@link LocationIndex} instance registered with the {@code GigaMap}
	 */
	@Bean
	public LocationIndex locationIndex()
	{
		return CountryIndices.LOCATION;
	}
}
