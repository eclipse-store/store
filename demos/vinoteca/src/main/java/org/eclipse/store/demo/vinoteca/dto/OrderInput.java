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
 * Inbound DTO used by the REST and GraphQL APIs to place a new
 * {@link org.eclipse.store.demo.vinoteca.model.Order Order}.
 * <p>
 * The {@link #customerIndex()} addresses a customer by its position in
 * {@link org.eclipse.store.demo.vinoteca.model.DataRoot#getCustomers()} (rather than by entity id),
 * matching the demo's intentionally simple list-based customer storage.
 *
 * @param customerIndex the index of the placing customer in the customers list
 * @param items         the line items to include in the order
 */
public record OrderInput(
	int              customerIndex,
	List<OrderItemInput> items
)
{
	/**
	 * A single line item in an {@link OrderInput}.
	 *
	 * @param wineId   the GigaMap entity id of the wine being ordered
	 * @param quantity the number of bottles
	 */
	public record OrderItemInput(
		long wineId,
		int  quantity
	)
	{
	}
}
