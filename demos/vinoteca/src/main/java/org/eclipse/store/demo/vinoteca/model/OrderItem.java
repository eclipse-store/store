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

import javax.money.MonetaryAmount;

public class OrderItem
{
	private Wine           wine;
	private int            quantity;
	private MonetaryAmount priceAtPurchase;

	public OrderItem()
	{
	}

	public OrderItem(
		final Wine           wine,
		final int            quantity,
		final MonetaryAmount priceAtPurchase
	)
	{
		this.wine            = wine;
		this.quantity        = quantity;
		this.priceAtPurchase = priceAtPurchase;
	}

	public Wine getWine()
	{
		return this.wine;
	}

	public int getQuantity()
	{
		return this.quantity;
	}

	public MonetaryAmount getPriceAtPurchase()
	{
		return this.priceAtPurchase;
	}

	public MonetaryAmount getSubtotal()
	{
		return this.priceAtPurchase.multiply(this.quantity);
	}

	@Override
	public String toString()
	{
		return "OrderItem[" + this.wine.getName() + " x" + this.quantity + "]";
	}
}
