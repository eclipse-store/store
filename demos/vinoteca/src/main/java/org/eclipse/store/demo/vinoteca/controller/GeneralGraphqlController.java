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

/**
 * GraphQL controller bundling the customer, order and data-generator queries and mutations
 * declared in {@code schema.graphqls}.
 * <p>
 * The {@code customers} and {@code orders} resolvers return a plain {@link Map} shaped like the
 * GraphQL {@code CustomerPage} / {@code OrderPage} types; all other resolvers return domain
 * objects directly.
 */
@Controller
public class GeneralGraphqlController
{
	private final CustomerService      customerService;
	private final OrderService         orderService;
	private final DataGeneratorService dataGeneratorService;

	/**
	 * @param customerService      the customer application service
	 * @param orderService         the order application service
	 * @param dataGeneratorService the synthetic-data generator service
	 */
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

	/**
	 * Resolves the {@code customers} query — a paged listing of customers.
	 *
	 * @param page zero-based page number
	 * @param size page size
	 * @return a map shaped like the GraphQL {@code CustomerPage} type
	 */
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

	/**
	 * Resolves the {@code orders} query — a paged listing of orders.
	 *
	 * @param page zero-based page number
	 * @param size page size
	 * @return a map shaped like the GraphQL {@code OrderPage} type
	 */
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

	/**
	 * Resolves the {@code dataMetrics} query — top-level entity counts of the persisted graph.
	 *
	 * @return aggregate dataset metrics
	 */
	@QueryMapping
	public DataMetrics dataMetrics()
	{
		return this.dataGeneratorService.getMetrics();
	}

	/**
	 * Resolves the {@code createCustomer} mutation.
	 *
	 * @param input the customer to create
	 * @return the created customer
	 */
	@MutationMapping
	public Customer createCustomer(@Argument final CustomerInput input)
	{
		return this.customerService.create(input);
	}

	/**
	 * Resolves the {@code createOrder} mutation.
	 *
	 * @param input the order to place
	 * @return the newly created order
	 */
	@MutationMapping
	public Order createOrder(@Argument final OrderInput input)
	{
		return this.orderService.create(input);
	}

	/**
	 * Resolves the {@code updateOrderStatus} mutation.
	 *
	 * @param index  the zero-based order index
	 * @param status the new status
	 * @return the updated order, or {@code null} if {@code index} is out of range
	 */
	@MutationMapping
	public Order updateOrderStatus(@Argument final int index, @Argument final OrderStatus status)
	{
		return this.orderService.updateStatus(index, status);
	}

	/**
	 * Resolves the {@code generateData} mutation — append a fresh batch of generated data.
	 *
	 * @param count the target number of wines to add
	 * @return aggregate counts of the entire persisted graph after the generation
	 */
	@MutationMapping
	public DataMetrics generateData(@Argument final int count)
	{
		return this.dataGeneratorService.generate(count);
	}
}
