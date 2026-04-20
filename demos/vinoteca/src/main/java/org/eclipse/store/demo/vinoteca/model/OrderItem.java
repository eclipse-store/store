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
 * A single line item inside an {@link Order}.
 * <p>
 * An {@code OrderItem} captures the {@link Wine} that was ordered, the quantity, and the
 * price at the time of purchase. The price is stored on the item itself (rather than read from
 * the wine) so that subsequent price changes on the wine do not retroactively alter past orders.
 *
 * @see Order
 * @see Wine
 */
public class OrderItem
{
	private Wine           wine;
	private int            quantity;
	private double priceAtPurchase;

	/**
	 * No-arg constructor required by EclipseStore for object reconstruction during loading.
	 */
	public OrderItem()
	{
	}

	/**
	 * Creates a new line item.
	 *
	 * @param wine            the wine being ordered
	 * @param quantity        the number of bottles
	 * @param priceAtPurchase the unit price captured at order time (so future price changes do not
	 *                        affect this order)
	 */
	public OrderItem(
		final Wine           wine,
		final int            quantity,
		final double         priceAtPurchase
	)
	{
		this.wine            = wine;
		this.quantity        = quantity;
		this.priceAtPurchase = priceAtPurchase;
	}

	/** @return the wine being ordered */
	public Wine getWine()
	{
		return this.wine;
	}

	/** @return the number of bottles */
	public int getQuantity()
	{
		return this.quantity;
	}

	/** @return the unit price at the time of purchase */
	public double getPriceAtPurchase()
	{
		return this.priceAtPurchase;
	}

	/**
	 * Computes the subtotal as {@code priceAtPurchase × quantity}.
	 *
	 * @return the line subtotal
	 */
	public double getSubtotal()
	{
		return this.priceAtPurchase * this.quantity;
	}

	@Override
	public String toString()
	{
		return "OrderItem[" + this.wine.getName() + " x" + this.quantity + "]";
	}
}
