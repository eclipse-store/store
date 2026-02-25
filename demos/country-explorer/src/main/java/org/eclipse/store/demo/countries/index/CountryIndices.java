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

public final class CountryIndices
{
	public static final LocationIndex LOCATION = new LocationIndex();

	public static final IndexerString<Country> CONTINENT = new IndexerString.Abstract<>()
	{
		@Override
		protected String getString(final Country country)
		{
			return country.continent();
		}
	};

	public static final IndexerString<Country> NAME = new IndexerString.Abstract<>()
	{
		@Override
		protected String getString(final Country country)
		{
			return country.name();
		}
	};

	public static final IndexerString<Country> ALPHA2 = new IndexerString.Abstract<>()
	{
		@Override
		protected String getString(final Country country)
		{
			return country.alpha2();
		}
	};

	private CountryIndices()
	{
		// no instances
	}
}
