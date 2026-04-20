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

import java.time.LocalDateTime;
import java.util.List;

/**
 * Domain entity representing a customer order placed in the Vinoteca shop.
 * <p>
 * An {@code Order} bundles a {@link Customer}, a timestamp, a snapshot list of
 * {@link OrderItem order items} (each capturing the price at the time of purchase) and the current
 * {@link OrderStatus}. The contained item list is immutable — it is captured via {@link List#copyOf}
 * in the constructor — so once an order is placed its line items cannot be mutated.
 *
 * @see OrderItem
 * @see OrderStatus
 */
public class Order
{
	private Customer      customer;
	private LocalDateTime orderDate;
	private List<OrderItem> items;
	private OrderStatus   status;

	/**
	 * No-arg constructor required by EclipseStore for object reconstruction during loading.
	 */
	public Order()
	{
	}

	/**
	 * Creates a new {@code Order}. The provided item list is defensively copied via
	 * {@link List#copyOf}; the resulting {@code Order} therefore exposes an unmodifiable view.
	 *
	 * @param customer  the customer placing the order
	 * @param orderDate the timestamp at which the order was placed
	 * @param items     the line items (must not be {@code null}; copied)
	 * @param status    the initial order status
	 */
	public Order(
		final Customer        customer,
		final LocalDateTime   orderDate,
		final List<OrderItem> items,
		final OrderStatus     status
	)
	{
		this.customer  = customer;
		this.orderDate = orderDate;
		this.items     = List.copyOf(items);
		this.status    = status;
	}

	/** @return the customer who placed the order */
	public Customer getCustomer()
	{
		return this.customer;
	}

	/** @return the timestamp at which the order was placed */
	public LocalDateTime getOrderDate()
	{
		return this.orderDate;
	}

	/** @return the unmodifiable list of line items */
	public List<OrderItem> getItems()
	{
		return this.items;
	}

	/** @return the current order status */
	public OrderStatus getStatus()
	{
		return this.status;
	}

	/** @param status the new order status */
	public void setStatus(final OrderStatus status)
	{
		this.status = status;
	}

	/**
	 * Computes the total monetary value of the order by summing the subtotals of all line items.
	 *
	 * @return the order total, or {@code null} if the order has no items
	 */
	public double getTotal()
	{
		return this.items.stream()
			.mapToDouble(OrderItem::getSubtotal)
			.sum();
	}

	@Override
	public String toString()
	{
		return "Order[" + this.customer.getFullName() + ", " + this.orderDate + ", " + this.status + "]";
	}
}
