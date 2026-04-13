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
 * Top-level entity counts for the entire dataset, used by the data generator and the analytics
 * dashboard to summarise the size of the persisted graph.
 *
 * @param wineries  total number of wineries
 * @param wines     total number of wines
 * @param customers total number of customers
 * @param orders    total number of orders
 * @param reviews   total number of reviews (summed across all wines)
 */
public record DataMetrics(
	long wineries,
	long wines,
	long customers,
	long orders,
	long reviews
)
{
}
