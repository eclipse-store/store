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
