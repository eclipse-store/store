package org.eclipse.store.demo.vinoteca.index;

/*-
 * #%L
 * EclipseStore Demo Vinoteca
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

import org.eclipse.store.demo.vinoteca.model.Winery;
import org.eclipse.store.gigamap.types.IndexerString;

/**
 * Index definitions for the wineries {@link org.eclipse.store.gigamap.types.GigaMap GigaMap}.
 * <p>
 * Combines the {@link #LOCATION} spatial index — which is what powers proximity queries such as
 * "wineries within 100 km of this point" — with bitmap indices on name, region and country for
 * exact-match filtering. Registered in
 * {@link org.eclipse.store.demo.vinoteca.model.DataRoot#DataRoot()}.
 * <p>
 * This is a utility class — the constructor is private and the class is {@code final}.
 */
public final class WineryIndices
{
	/** Spatial index over winery latitude/longitude — drives {@code nearbyWineries} queries. */
	public static final WineryLocationIndex LOCATION = new WineryLocationIndex();

	/** Indexer over the winery name. */
	public static final IndexerString<Winery> NAME = new IndexerString.Abstract<>()
	{
		@Override
		public String name()
		{
			return "name";
		}

		@Override
		protected String getString(final Winery winery)
		{
			return winery.getName();
		}
	};

	/** Indexer over the winery's region. */
	public static final IndexerString<Winery> REGION = new IndexerString.Abstract<>()
	{
		@Override
		public String name()
		{
			return "region";
		}

		@Override
		protected String getString(final Winery winery)
		{
			return winery.getRegion();
		}
	};

	/** Indexer over the winery's country. */
	public static final IndexerString<Winery> COUNTRY = new IndexerString.Abstract<>()
	{
		@Override
		public String name()
		{
			return "country";
		}

		@Override
		protected String getString(final Winery winery)
		{
			return winery.getCountry();
		}
	};

	private WineryIndices()
	{
	}
}
