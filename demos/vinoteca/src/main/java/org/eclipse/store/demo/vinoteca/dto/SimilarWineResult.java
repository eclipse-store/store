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

import org.eclipse.store.demo.vinoteca.model.Wine;

/**
 * One entry of a vector similarity search result against the wine embedding index.
 * <p>
 * Produced by {@link org.eclipse.store.demo.vinoteca.service.WineService#findSimilar
 * WineService.findSimilar(...)}, which queries the JVector vector index registered on the wines
 * GigaMap. The {@link #score()} is the cosine similarity in {@code [0.0, 1.0]} where higher means
 * more similar.
 *
 * @param wine  the matching wine
 * @param score the cosine similarity score
 */
public record SimilarWineResult(
	Wine  wine,
	float score
)
{
}
