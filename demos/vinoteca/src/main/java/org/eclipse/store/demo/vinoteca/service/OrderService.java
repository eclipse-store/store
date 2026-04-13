package org.eclipse.store.demo.vinoteca.service;

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

import org.eclipse.store.demo.vinoteca.dto.OrderInput;
import org.eclipse.store.demo.vinoteca.dto.PageResult;
import org.eclipse.store.demo.vinoteca.model.*;
import org.eclipse.store.integrations.spring.boot.types.concurrent.Mutex;
import org.eclipse.store.integrations.spring.boot.types.concurrent.Read;
import org.eclipse.store.integrations.spring.boot.types.concurrent.Write;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Application service for placing and inspecting orders.
 * <p>
 * Like {@link CustomerService}, this service operates on a plain {@link java.util.List} of
 * {@link Order} entries on the {@link DataRoot}. New orders also end up on the placing customer's
 * own order list, mirroring the bidirectional model.
 */
@Service
@Mutex("orderStore")
public class OrderService
{
	private final DataRoot               dataRoot;
	private final EmbeddedStorageManager storageManager;

	/**
	 * @param dataRoot       the persistent root from which the orders list is taken
	 * @param storageManager the EclipseStore storage manager, used to persist the orders list
	 *                       and individual order mutations
	 */
	public OrderService(
		final DataRoot               dataRoot,
		final EmbeddedStorageManager storageManager
	)
	{
		this.dataRoot       = dataRoot;
		this.storageManager = storageManager;
	}

	/**
	 * Returns a slice of the orders list.
	 *
	 * @param page the zero-based page number
	 * @param size the page size
	 * @return a page wrapping the orders in the requested slice and the total order count
	 */
	@Read
	public PageResult<Order> list(final int page, final int size)
	{
		final List<Order> orders = this.dataRoot.getOrders();
		final int total = orders.size();
		final int from  = Math.min(page * size, total);
		final int to    = Math.min(from + size, total);
		return new PageResult<>(orders.subList(from, to), total, page, size);
	}

	/**
	 * Places a new order. Each item's price is captured as the wine's current
	 * {@link Wine#getPrice() price} so that subsequent price changes do not retroactively alter
	 * past orders. The new order is appended both to the global orders list and to the placing
	 * customer's own order list.
	 *
	 * @param input the order to place
	 * @return the newly created order, with status {@link OrderStatus#PENDING}
	 * @throws IllegalArgumentException if any referenced wine id cannot be resolved
	 * @throws IndexOutOfBoundsException if {@link OrderInput#customerIndex()} is out of range
	 */
	@Write
	public Order create(final OrderInput input)
	{
		final Customer customer = this.dataRoot.getCustomers().get(input.customerIndex());
		final List<OrderItem> items = new ArrayList<>();
		for (final OrderInput.OrderItemInput itemInput : input.items())
		{
			final Wine wine = this.dataRoot.getWines().get(itemInput.wineId());
			if (wine == null)
			{
				throw new IllegalArgumentException("Wine not found: " + itemInput.wineId());
			}
			items.add(new OrderItem(wine, itemInput.quantity(), wine.getPrice()));
		}

		final Order order = new Order(customer, LocalDateTime.now(), items, OrderStatus.PENDING);
		this.dataRoot.getOrders().add(order);
		customer.getOrders().add(order);
		this.storageManager.storeAll(this.dataRoot.getOrders(), customer.getOrders());
		return order;
	}

	/**
	 * Updates the status of an existing order, addressed by its position in the orders list.
	 *
	 * @param index  the zero-based list index of the order
	 * @param status the new status
	 * @return the updated order, or {@code null} if {@code index} is out of range
	 */
	@Write
	public Order updateStatus(final int index, final OrderStatus status)
	{
		final List<Order> orders = this.dataRoot.getOrders();
		if (index < 0 || index >= orders.size())
		{
			return null;
		}
		final Order order = orders.get(index);
		order.setStatus(status);
		this.storageManager.store(order);
		return order;
	}

	/**
	 * Returns all orders currently in the given status.
	 *
	 * @param status the status to filter by
	 * @return the matching orders, in insertion order
	 */
	@Read
	public List<Order> byStatus(final OrderStatus status)
	{
		return this.dataRoot.getOrders().stream()
			.filter(o -> o.getStatus() == status)
			.collect(Collectors.toList());
	}
}
