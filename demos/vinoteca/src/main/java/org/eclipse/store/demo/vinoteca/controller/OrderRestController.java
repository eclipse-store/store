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

@RestController
@RequestMapping("/api/v1/orders")
public class OrderRestController
{
	private final OrderService orderService;

	public OrderRestController(final OrderService orderService)
	{
		this.orderService = orderService;
	}

	@GetMapping
	public PageResult<Order> list(
		@RequestParam(defaultValue = "0") final int page,
		@RequestParam(defaultValue = "20") final int size
	)
	{
		return this.orderService.list(page, size);
	}

	@PostMapping
	public Order create(@RequestBody final OrderInput input)
	{
		return this.orderService.create(input);
	}

	@PutMapping("/{index}/status")
	public ResponseEntity<Order> updateStatus(
		@PathVariable final int index,
		@RequestParam final OrderStatus status
	)
	{
		final Order order = this.orderService.updateStatus(index, status);
		return order != null ? ResponseEntity.ok(order) : ResponseEntity.notFound().build();
	}

	@GetMapping("/by-status/{status}")
	public List<Order> byStatus(@PathVariable final OrderStatus status)
	{
		return this.orderService.byStatus(status);
	}
}
