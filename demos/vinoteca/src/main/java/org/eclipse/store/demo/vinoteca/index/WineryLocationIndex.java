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
import org.eclipse.store.gigamap.types.SpatialIndexer;

/**
 * GigaMap spatial indexer that maps each {@link Winery} to its
 * {@link Winery#getLatitude() latitude}/{@link Winery#getLongitude() longitude} so that the
 * wineries GigaMap can answer proximity, bounding-box and Haversine-distance queries.
 * <p>
 * A single instance is exposed as {@link WineryIndices#LOCATION} and registered on the wineries
 * GigaMap by {@link org.eclipse.store.demo.vinoteca.model.DataRoot#DataRoot()}.
 */
public class WineryLocationIndex extends SpatialIndexer.Abstract<Winery>
{
	/** @return the index identifier ({@code "location"}). */
	@Override
	public String name()
	{
		return "location";
	}

	/**
	 * @param winery the winery to index
	 * @return the winery's latitude in decimal degrees
	 */
	@Override
	protected Double getLatitude(final Winery winery)
	{
		return winery.getLatitude();
	}

	/**
	 * @param winery the winery to index
	 * @return the winery's longitude in decimal degrees
	 */
	@Override
	protected Double getLongitude(final Winery winery)
	{
		return winery.getLongitude();
	}
}
