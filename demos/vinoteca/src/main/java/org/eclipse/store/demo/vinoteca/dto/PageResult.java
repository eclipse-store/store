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

import java.util.List;

/**
 * A generic, immutable page of results returned by the paged list endpoints.
 * <p>
 * {@code PageResult} is the API surface used both by the REST controllers and by the GraphQL
 * {@code WinePage} / {@code WineryPage} / {@code CustomerPage} / {@code OrderPage} types.
 *
 * @param <T>     the entity type carried in this page
 * @param content the entities that fall in the requested page slice
 * @param total   the total number of entities (across all pages) matching the query
 * @param page    the zero-based page number that this result represents
 * @param size    the requested page size
 */
public record PageResult<T>(
	List<T> content,
	long    total,
	int     page,
	int     size
)
{
}
