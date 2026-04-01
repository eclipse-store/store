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

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerRestController
{
	private final CustomerService customerService;

	public CustomerRestController(final CustomerService customerService)
	{
		this.customerService = customerService;
	}

	@GetMapping
	public PageResult<Customer> list(
		@RequestParam(defaultValue = "0") final int page,
		@RequestParam(defaultValue = "20") final int size
	)
	{
		return this.customerService.list(page, size);
	}

	@GetMapping("/{index}")
	public ResponseEntity<Customer> findByIndex(@PathVariable final int index)
	{
		final Customer customer = this.customerService.findByIndex(index);
		return customer != null ? ResponseEntity.ok(customer) : ResponseEntity.notFound().build();
	}

	@PostMapping
	public Customer create(@RequestBody final CustomerInput input)
	{
		return this.customerService.create(input);
	}

	@GetMapping("/{index}/orders")
	public List<Order> getOrders(@PathVariable final int index)
	{
		return this.customerService.getOrders(index);
	}
}
