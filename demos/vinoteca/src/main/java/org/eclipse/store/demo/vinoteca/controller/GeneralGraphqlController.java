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

import java.util.Map;

import org.eclipse.store.demo.vinoteca.dto.DataMetrics;
import org.eclipse.store.demo.vinoteca.dto.OrderInput;
import org.eclipse.store.demo.vinoteca.dto.CustomerInput;
import org.eclipse.store.demo.vinoteca.dto.PageResult;
import org.eclipse.store.demo.vinoteca.model.Customer;
import org.eclipse.store.demo.vinoteca.model.Order;
import org.eclipse.store.demo.vinoteca.model.OrderStatus;
import org.eclipse.store.demo.vinoteca.service.CustomerService;
import org.eclipse.store.demo.vinoteca.service.DataGeneratorService;
import org.eclipse.store.demo.vinoteca.service.OrderService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

@Controller
public class GeneralGraphqlController
{
	private final CustomerService      customerService;
	private final OrderService         orderService;
	private final DataGeneratorService dataGeneratorService;

	public GeneralGraphqlController(
		final CustomerService      customerService,
		final OrderService         orderService,
		final DataGeneratorService dataGeneratorService
	)
	{
		this.customerService      = customerService;
		this.orderService         = orderService;
		this.dataGeneratorService = dataGeneratorService;
	}

	@QueryMapping
	public Map<String, Object> customers(@Argument final int page, @Argument final int size)
	{
		final PageResult<Customer> result = this.customerService.list(page, size);
		return Map.of(
			"content", result.content(),
			"total",   result.total(),
			"page",    result.page(),
			"size",    result.size()
		);
	}

	@QueryMapping
	public Map<String, Object> orders(@Argument final int page, @Argument final int size)
	{
		final PageResult<Order> result = this.orderService.list(page, size);
		return Map.of(
			"content", result.content(),
			"total",   result.total(),
			"page",    result.page(),
			"size",    result.size()
		);
	}

	@QueryMapping
	public DataMetrics dataMetrics()
	{
		return this.dataGeneratorService.getMetrics();
	}

	@MutationMapping
	public Customer createCustomer(@Argument final CustomerInput input)
	{
		return this.customerService.create(input);
	}

	@MutationMapping
	public Order createOrder(@Argument final OrderInput input)
	{
		return this.orderService.create(input);
	}

	@MutationMapping
	public Order updateOrderStatus(@Argument final int index, @Argument final OrderStatus status)
	{
		return this.orderService.updateStatus(index, status);
	}

	@MutationMapping
	public DataMetrics generateData(@Argument final int count)
	{
		return this.dataGeneratorService.generate(count);
	}
}
