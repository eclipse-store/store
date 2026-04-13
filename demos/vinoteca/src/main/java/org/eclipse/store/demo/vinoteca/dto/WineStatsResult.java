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

import java.util.Map;

/**
 * Aggregated catalog statistics shown on the analytics screen.
 *
 * @param totalCount          the total number of wines
 * @param averageRating       the average customer rating across all wines
 * @param averagePrice        the average bottle price across all wines (numeric, no currency)
 * @param typeDistribution    the number of wines per {@link org.eclipse.store.demo.vinoteca.model.WineType WineType} (key is the enum name)
 * @param countryDistribution the number of wines per producing country
 */
public record WineStatsResult(
	long               totalCount,
	double             averageRating,
	double             averagePrice,
	Map<String, Long>  typeDistribution,
	Map<String, Long>  countryDistribution
)
{
}
