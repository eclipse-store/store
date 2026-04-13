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
 * Categorisation of a {@link Wine} by style.
 * <p>
 * The enum values mirror the {@code WineType} enum exposed by the GraphQL schema and serve as the
 * value domain for the {@link org.eclipse.store.demo.vinoteca.index.WineIndices#TYPE TYPE} bitmap
 * index registered on the wines {@link org.eclipse.store.gigamap.types.GigaMap GigaMap}.
 */
public enum WineType
{
	/** Red wine (still). */
	RED,
	/** White wine (still). */
	WHITE,
	/** Rosé wine. */
	ROSE,
	/** Sparkling wine (e.g. champagne, prosecco, cava). */
	SPARKLING,
	/** Sweet dessert wine. */
	DESSERT,
	/** Orange (skin-contact) wine. */
	ORANGE
}
