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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.store.demo.vinoteca.dto.OrderInput;
import org.eclipse.store.demo.vinoteca.dto.PageResult;
import org.eclipse.store.demo.vinoteca.model.Customer;
import org.eclipse.store.demo.vinoteca.model.DataRoot;
import org.eclipse.store.demo.vinoteca.model.Order;
import org.eclipse.store.demo.vinoteca.model.OrderItem;
import org.eclipse.store.demo.vinoteca.model.OrderStatus;
import org.eclipse.store.demo.vinoteca.model.Wine;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.integrations.spring.boot.types.concurrent.Mutex;
import org.eclipse.store.integrations.spring.boot.types.concurrent.Read;
import org.eclipse.store.integrations.spring.boot.types.concurrent.Write;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.springframework.stereotype.Service;

@Service
@Mutex("orderStore")
public class OrderService
{
	private final DataRoot               dataRoot;
	private final GigaMap<Wine>          wineGigaMap;
	private final EmbeddedStorageManager storageManager;

	public OrderService(
		final DataRoot               dataRoot,
		final GigaMap<Wine>          wineGigaMap,
		final EmbeddedStorageManager storageManager
	)
	{
		this.dataRoot       = dataRoot;
		this.wineGigaMap    = wineGigaMap;
		this.storageManager = storageManager;
	}

	@Read
	public PageResult<Order> list(final int page, final int size)
	{
		final List<Order> orders = this.dataRoot.getOrders();
		final int total = orders.size();
		final int from  = Math.min(page * size, total);
		final int to    = Math.min(from + size, total);
		return new PageResult<>(orders.subList(from, to), total, page, size);
	}

	@Write
	public Order create(final OrderInput input)
	{
		final Customer customer = this.dataRoot.getCustomers().get(input.customerIndex());
		final List<OrderItem> items = new ArrayList<>();
		for (final OrderInput.OrderItemInput itemInput : input.items())
		{
			final Wine wine = this.wineGigaMap.get(itemInput.wineId());
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

	@Read
	public List<Order> byStatus(final OrderStatus status)
	{
		return this.dataRoot.getOrders().stream()
			.filter(o -> o.getStatus() == status)
			.collect(Collectors.toList());
	}
}
