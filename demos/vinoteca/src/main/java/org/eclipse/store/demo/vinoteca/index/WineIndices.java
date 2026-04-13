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

import org.eclipse.store.demo.vinoteca.model.Wine;
import org.eclipse.store.gigamap.types.IndexerString;

/**
 * Bitmap index definitions for the wines {@link org.eclipse.store.gigamap.types.GigaMap GigaMap}.
 * <p>
 * Each {@code public static final} field exposes a single
 * {@link org.eclipse.store.gigamap.types.IndexerString IndexerString} that GigaMap uses to maintain
 * a bitmap index over a derived string attribute of {@link Wine}. The indices are registered in
 * {@link org.eclipse.store.demo.vinoteca.model.DataRoot#DataRoot()} and underpin the equality and
 * containment filters offered by the wine REST/GraphQL endpoints and the catalog UI.
 * <p>
 * This is a utility class — the constructor is private and the class is {@code final}.
 */
public final class WineIndices
{
	/** Indexer over the wine name (used for exact-match lookups by name). */
	public static final IndexerString<Wine> NAME = new IndexerString.Abstract<>()
	{
		@Override
		public String name()
		{
			return "name";
		}

		@Override
		protected String getString(final Wine wine)
		{
			return wine.getName();
		}
	};

	/** Indexer over the wine type (e.g. {@code "RED"}, {@code "WHITE"}). */
	public static final IndexerString<Wine> TYPE = new IndexerString.Abstract<>()
	{
		@Override
		public String name()
		{
			return "type";
		}

		@Override
		protected String getString(final Wine wine)
		{
			return wine.getType().name();
		}
	};

	/** Indexer over the grape variety enum constant name. */
	public static final IndexerString<Wine> GRAPE_VARIETY = new IndexerString.Abstract<>()
	{
		@Override
		public String name()
		{
			return "grape_variety";
		}

		@Override
		protected String getString(final Wine wine)
		{
			return wine.getGrapeVariety().name();
		}
	};

	/** Indexer over the producing winery's name (de-references the {@link Wine#getWinery()} association). */
	public static final IndexerString<Wine> WINERY_NAME = new IndexerString.Abstract<>()
	{
		@Override
		public String name()
		{
			return "winery_name";
		}

		@Override
		protected String getString(final Wine wine)
		{
			return wine.getWinery().getName();
		}
	};

	/** Indexer over the producing winery's country. */
	public static final IndexerString<Wine> COUNTRY = new IndexerString.Abstract<>()
	{
		@Override
		public String name()
		{
			return "country";
		}

		@Override
		protected String getString(final Wine wine)
		{
			return wine.getWinery().getCountry();
		}
	};

	/** Indexer over the producing winery's region. */
	public static final IndexerString<Wine> REGION = new IndexerString.Abstract<>()
	{
		@Override
		public String name()
		{
			return "region";
		}

		@Override
		protected String getString(final Wine wine)
		{
			return wine.getWinery().getRegion();
		}
	};

	private WineIndices()
	{
	}
}
