package org.eclipse.store.gigamap.codegen.test;

/*-
 * #%L
 * EclipseStore GigaMap Codegen
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

import org.eclipse.store.gigamap.annotations.Index;
import org.eclipse.store.gigamap.annotations.SpatialIndex;
import org.eclipse.store.gigamap.annotations.Unique;

/**
 * Type-level {@link SpatialIndex} built from two numeric coordinate members, plus a regular field
 * index.
 */
@SpatialIndex(latitude = "lat", longitude = "lon")
public class City
{
	@Unique
	private String name;

	private double lat;
	private double lon;

	public City()
	{
		super();
	}

	public City(final String name, final double lat, final double lon)
	{
		this.name = name;
		this.lat  = lat;
		this.lon  = lon;
	}

	public String getName()
	{
		return this.name;
	}

	public double getLat()
	{
		return this.lat;
	}

	public double getLon()
	{
		return this.lon;
	}
}
