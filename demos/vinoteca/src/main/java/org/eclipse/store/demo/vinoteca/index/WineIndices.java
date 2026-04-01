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

public final class WineIndices
{
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
