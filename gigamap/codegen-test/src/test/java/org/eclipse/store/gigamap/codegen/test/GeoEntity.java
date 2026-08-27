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

import org.eclipse.store.gigamap.annotations.SpatialIndex;

/**
 * Spatial index whose name is not a valid Java identifier; the generated constant identifier must be
 * sanitized (e.g. {@code geo-index} &rarr; {@code geo_index}).
 */
@SpatialIndex(name = "geo-index", latitude = "lat", longitude = "lon")
public class GeoEntity
{
	private double lat;
	private double lon;

	public GeoEntity()
	{
		super();
	}

	public GeoEntity(final double lat, final double lon)
	{
		this.lat = lat;
		this.lon = lon;
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
