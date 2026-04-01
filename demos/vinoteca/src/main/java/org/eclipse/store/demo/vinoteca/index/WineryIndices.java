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

public final class WineryIndices
{
	public static final WineryLocationIndex LOCATION = new WineryLocationIndex();

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
