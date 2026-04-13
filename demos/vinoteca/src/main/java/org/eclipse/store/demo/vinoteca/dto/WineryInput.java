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

/**
 * Inbound DTO used by the REST and GraphQL APIs to create or update a
 * {@link org.eclipse.store.demo.vinoteca.model.Winery Winery}.
 *
 * @param name        the winery name
 * @param region      the wine-growing region
 * @param country     the country of the winery
 * @param latitude    the geographic latitude in decimal degrees ([-90, 90])
 * @param longitude   the geographic longitude in decimal degrees ([-180, 180])
 * @param description a free-form description (may be {@code null})
 * @param foundedYear the year the winery was founded
 */
public record WineryInput(
	String name,
	String region,
	String country,
	double latitude,
	double longitude,
	String description,
	int    foundedYear
)
{
}
