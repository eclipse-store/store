package org.eclipse.store.demo.vinoteca.model;

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
 * The dominant grape variety used to make a {@link Wine}.
 * <p>
 * Only a curated subset of internationally recognized varieties is modelled — enough to make the
 * demo's filtering and analytics screens meaningful without exhaustively cataloguing every variety
 * known to ampelography. The enum values back the
 * {@link org.eclipse.store.demo.vinoteca.index.WineIndices#GRAPE_VARIETY GRAPE_VARIETY} bitmap
 * index on the wines {@link org.eclipse.store.gigamap.types.GigaMap GigaMap}.
 */
public enum GrapeVariety
{
	/** Cabernet Sauvignon (red). */
	CABERNET_SAUVIGNON,
	/** Merlot (red). */
	MERLOT,
	/** Pinot Noir (red). */
	PINOT_NOIR,
	/** Chardonnay (white). */
	CHARDONNAY,
	/** Sauvignon Blanc (white). */
	SAUVIGNON_BLANC,
	/** Riesling (white). */
	RIESLING,
	/** Syrah / Shiraz (red). */
	SYRAH,
	/** Tempranillo (red). */
	TEMPRANILLO,
	/** Sangiovese (red). */
	SANGIOVESE,
	/** Nebbiolo (red). */
	NEBBIOLO,
	/** Malbec (red). */
	MALBEC,
	/** Pinot Grigio / Pinot Gris (white). */
	PINOT_GRIGIO,
	/** Gewürztraminer (aromatic white). */
	GEWURZTRAMINER,
	/** Grenache / Garnacha (red). */
	GRENACHE,
	/** Zinfandel / Primitivo (red). */
	ZINFANDEL
}
