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

import org.eclipse.store.demo.vinoteca.model.GrapeVariety;
import org.eclipse.store.demo.vinoteca.model.WineType;

/**
 * Inbound DTO used by the REST and GraphQL APIs to create or update a
 * {@link org.eclipse.store.demo.vinoteca.model.Wine Wine}.
 * <p>
 * The {@link #wineryId()} references the producing winery by its position inside the wineries
 * {@link org.eclipse.store.gigamap.types.GigaMap GigaMap}; price is transmitted as a plain
 * {@code double} (in EUR).
 *
 * @param name           the commercial name of the wine
 * @param wineryId       the GigaMap entity id of the producing winery
 * @param grapeVariety   the dominant grape variety
 * @param type           the wine type
 * @param vintage        the year of harvest
 * @param price          the bottle price (numeric value)
 * @param currency       the ISO 4217 currency code (e.g. {@code "EUR"}); may be {@code null}
 * @param tastingNotes   free-form tasting notes (optional)
 * @param aroma          aroma description (optional)
 * @param foodPairing    suggested food pairing (optional)
 * @param alcoholContent alcohol content in percent by volume
 * @param bottlesInStock current bottle inventory
 * @param available      whether the wine is currently offered for sale
 */
public record WineInput(
	String       name,
	long         wineryId,
	GrapeVariety grapeVariety,
	WineType     type,
	int          vintage,
	double       price,
	String       currency,
	String       tastingNotes,
	String       aroma,
	String       foodPairing,
	double       alcoholContent,
	int          bottlesInStock,
	boolean      available
)
{
}
