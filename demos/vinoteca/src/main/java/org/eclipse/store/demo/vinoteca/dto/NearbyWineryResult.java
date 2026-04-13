package org.eclipse.store.demo.vinoteca.dto;

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

/**
 * One entry of a "wineries near a point" query result.
 * <p>
 * Produced by {@link org.eclipse.store.demo.vinoteca.service.WineryService#findNearby
 * WineryService.findNearby(...)}, which uses the spatial index registered on the wineries
 * GigaMap and computes the great-circle distance via the Haversine formula.
 *
 * @param winery     the matching winery
 * @param distanceKm the distance from the query point in kilometres
 */
public record NearbyWineryResult(
	Winery winery,
	double distanceKm
)
{
}
