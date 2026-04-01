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

import javax.money.MonetaryAmount;

public class Order
{
	private Customer      customer;
	private LocalDateTime orderDate;
	private List<OrderItem> items;
	private OrderStatus   status;

	public Order()
	{
	}

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

	public Customer getCustomer()
	{
		return this.customer;
	}

	public LocalDateTime getOrderDate()
	{
		return this.orderDate;
	}

	public List<OrderItem> getItems()
	{
		return this.items;
	}

	public OrderStatus getStatus()
	{
		return this.status;
	}

	public void setStatus(final OrderStatus status)
	{
		this.status = status;
	}

	public MonetaryAmount getTotal()
	{
		return this.items.stream()
			.map(OrderItem::getSubtotal)
			.reduce(MonetaryAmount::add)
			.orElse(null);
	}

	@Override
	public String toString()
	{
		return "Order[" + this.customer.getFullName() + ", " + this.orderDate + ", " + this.status + "]";
	}
}
