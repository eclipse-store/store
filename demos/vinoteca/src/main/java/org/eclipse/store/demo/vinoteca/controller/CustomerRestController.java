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

import org.eclipse.store.demo.vinoteca.dto.CustomerInput;
import org.eclipse.store.demo.vinoteca.dto.PageResult;
import org.eclipse.store.demo.vinoteca.model.Customer;
import org.eclipse.store.demo.vinoteca.model.Order;
import org.eclipse.store.demo.vinoteca.service.CustomerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller exposing customer master data at {@code /api/v1/customers}.
 * <p>
 * Customers are addressed by their position in the customers list (the {@code index} path
 * variable), matching the demo's intentionally simple list-based customer storage.
 */
@RestController
@RequestMapping("/api/v1/customers")
public class CustomerRestController
{
	private final CustomerService customerService;

	/**
	 * @param customerService the underlying customer application service
	 */
	public CustomerRestController(final CustomerService customerService)
	{
		this.customerService = customerService;
	}

	/**
	 * {@code GET /api/v1/customers} — paged listing of all customers.
	 *
	 * @param page zero-based page number (default {@code 0})
	 * @param size page size (default {@code 20})
	 * @return a page of customers
	 */
	@GetMapping
	public PageResult<Customer> list(
		@RequestParam(defaultValue = "0") final int page,
		@RequestParam(defaultValue = "20") final int size
	)
	{
		return this.customerService.list(page, size);
	}

	/**
	 * {@code GET /api/v1/customers/{index}} — single-customer lookup by list index.
	 *
	 * @param index zero-based customer index
	 * @return 200 with the customer, or 404 if {@code index} is out of range
	 */
	@GetMapping("/{index}")
	public ResponseEntity<Customer> findByIndex(@PathVariable final int index)
	{
		final Customer customer = this.customerService.findByIndex(index);
		return customer != null ? ResponseEntity.ok(customer) : ResponseEntity.notFound().build();
	}

	/**
	 * {@code POST /api/v1/customers} — create a new customer.
	 *
	 * @param input the customer to create
	 * @return the created customer
	 */
	@PostMapping
	public Customer create(@RequestBody final CustomerInput input)
	{
		return this.customerService.create(input);
	}

	/**
	 * {@code GET /api/v1/customers/{index}/orders} — order history of a single customer.
	 *
	 * @param index zero-based customer index
	 * @return the (possibly empty) order list; an empty list if {@code index} is out of range
	 */
	@GetMapping("/{index}/orders")
	public List<Order> getOrders(@PathVariable final int index)
	{
		return this.customerService.getOrders(index);
	}
}
