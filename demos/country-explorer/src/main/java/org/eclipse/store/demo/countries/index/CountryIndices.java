package org.eclipse.store.demo.countries.index;

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

import org.eclipse.store.demo.countries.model.Country;
import org.eclipse.store.gigamap.types.IndexerString;

/**
 * Central registry of all {@link org.eclipse.store.gigamap.types.GigaMap GigaMap}
 * indices used by the Country Explorer application.
 * <p>
 * This utility class holds singleton index instances that are shared between the
 * {@link org.eclipse.store.demo.countries.config.GigaMapConfig GigaMapConfig} (where
 * they are registered with the {@code GigaMap}) and the
 * {@link org.eclipse.store.demo.countries.service.CountryService CountryService}
 * (where they are used for querying).
 * <p>
 * Four indices are provided:
 * <ul>
 *   <li>{@link #LOCATION} &ndash; a {@link LocationIndex spatial index} for
 *       latitude/longitude-based queries (nearby, bounding box, hemisphere).</li>
 *   <li>{@link #CONTINENT} &ndash; a {@link IndexerString string index} on the
 *       continent name for filtering by continent.</li>
 *   <li>{@link #NAME} &ndash; a {@link IndexerString string index} on the country
 *       name.</li>
 *   <li>{@link #ALPHA2} &ndash; a {@link IndexerString string index} on the
 *       ISO&nbsp;3166-1 alpha-2 code for exact-match lookups.</li>
 * </ul>
 *
 * <p>This class is not instantiable.
 */
public final class CountryIndices
{
	/**
	 * Spatial index that extracts latitude and longitude from a {@link Country}
	 * instance. Used for geographic proximity and bounding-box queries.
	 */
	public static final LocationIndex LOCATION = new LocationIndex();

	/**
	 * String index on the {@link Country#continent()} field.
	 * Enables efficient filtering of countries by continent name.
	 */
	public static final IndexerString<Country> CONTINENT = new IndexerString.Abstract<>()
	{
		@Override
		protected String getString(final Country country)
		{
			return country.continent();
		}
	};

	/**
	 * String index on the {@link Country#name()} field.
	 * Enables efficient filtering and lookup of countries by their common name.
	 */
	public static final IndexerString<Country> NAME = new IndexerString.Abstract<>()
	{
		@Override
		protected String getString(final Country country)
		{
			return country.name();
		}
	};

	/**
	 * String index on the {@link Country#alpha2()} field.
	 * Enables efficient exact-match lookup of a country by its two-letter
	 * ISO&nbsp;3166-1 alpha-2 code.
	 */
	public static final IndexerString<Country> ALPHA2 = new IndexerString.Abstract<>()
	{
		@Override
		protected String getString(final Country country)
		{
			return country.alpha2();
		}
	};

	/**
	 * Private constructor to prevent instantiation.
	 */
	private CountryIndices()
	{
		// no instances
	}
}
