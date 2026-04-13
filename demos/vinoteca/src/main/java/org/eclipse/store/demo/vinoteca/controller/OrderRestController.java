package org.eclipse.store.demo.vinoteca.controller;

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

import org.eclipse.store.demo.vinoteca.dto.OrderInput;
import org.eclipse.store.demo.vinoteca.dto.PageResult;
import org.eclipse.store.demo.vinoteca.model.Order;
import org.eclipse.store.demo.vinoteca.model.OrderStatus;
import org.eclipse.store.demo.vinoteca.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller exposing order operations at {@code /api/v1/orders}.
 * <p>
 * Orders are addressed by their position in the orders list (the {@code index} path variable),
 * matching the demo's intentionally simple list-based order storage.
 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderRestController
{
	private final OrderService orderService;

	/**
	 * @param orderService the underlying order application service
	 */
	public OrderRestController(final OrderService orderService)
	{
		this.orderService = orderService;
	}

	/**
	 * {@code GET /api/v1/orders} — paged listing of all orders.
	 *
	 * @param page zero-based page number (default {@code 0})
	 * @param size page size (default {@code 20})
	 * @return a page of orders
	 */
	@GetMapping
	public PageResult<Order> list(
		@RequestParam(defaultValue = "0") final int page,
		@RequestParam(defaultValue = "20") final int size
	)
	{
		return this.orderService.list(page, size);
	}

	/**
	 * {@code POST /api/v1/orders} — place a new order.
	 *
	 * @param input the order to place
	 * @return the newly created order, with status {@link OrderStatus#PENDING}
	 */
	@PostMapping
	public Order create(@RequestBody final OrderInput input)
	{
		return this.orderService.create(input);
	}

	/**
	 * {@code PUT /api/v1/orders/{index}/status} — change the status of an existing order.
	 *
	 * @param index  the zero-based order index
	 * @param status the new status (passed as a query parameter)
	 * @return 200 with the updated order, or 404 if {@code index} is out of range
	 */
	@PutMapping("/{index}/status")
	public ResponseEntity<Order> updateStatus(
		@PathVariable final int index,
		@RequestParam final OrderStatus status
	)
	{
		final Order order = this.orderService.updateStatus(index, status);
		return order != null ? ResponseEntity.ok(order) : ResponseEntity.notFound().build();
	}

	/**
	 * {@code GET /api/v1/orders/by-status/{status}} — orders currently in the given status.
	 *
	 * @param status the status to filter by
	 * @return matching orders, in insertion order
	 */
	@GetMapping("/by-status/{status}")
	public List<Order> byStatus(@PathVariable final OrderStatus status)
	{
		return this.orderService.byStatus(status);
	}
}
