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

import java.util.List;

import org.eclipse.store.demo.vinoteca.dto.CustomerInput;
import org.eclipse.store.demo.vinoteca.dto.PageResult;
import org.eclipse.store.demo.vinoteca.model.Customer;
import org.eclipse.store.demo.vinoteca.model.DataRoot;
import org.eclipse.store.demo.vinoteca.model.Order;
import org.eclipse.store.integrations.spring.boot.types.concurrent.Mutex;
import org.eclipse.store.integrations.spring.boot.types.concurrent.Read;
import org.eclipse.store.integrations.spring.boot.types.concurrent.Write;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.springframework.stereotype.Service;

@Service
@Mutex("customerStore")
public class CustomerService
{
	private final DataRoot               dataRoot;
	private final EmbeddedStorageManager storageManager;

	public CustomerService(final DataRoot dataRoot, final EmbeddedStorageManager storageManager)
	{
		this.dataRoot       = dataRoot;
		this.storageManager = storageManager;
	}

	@Read
	public PageResult<Customer> list(final int page, final int size)
	{
		final List<Customer> customers = this.dataRoot.getCustomers();
		final int total = customers.size();
		final int from  = Math.min(page * size, total);
		final int to    = Math.min(from + size, total);
		return new PageResult<>(customers.subList(from, to), total, page, size);
	}

	@Read
	public Customer findByIndex(final int index)
	{
		final List<Customer> customers = this.dataRoot.getCustomers();
		return index >= 0 && index < customers.size() ? customers.get(index) : null;
	}

	@Write
	public Customer create(final CustomerInput input)
	{
		final Customer customer = new Customer(
			input.firstName(),
			input.lastName(),
			input.email(),
			input.city(),
			input.country()
		);
		this.dataRoot.getCustomers().add(customer);
		this.storageManager.store(this.dataRoot.getCustomers());
		return customer;
	}

	@Read
	public List<Order> getOrders(final int customerIndex)
	{
		final Customer customer = this.findByIndex(customerIndex);
		return customer != null && customer.getOrders() != null
			? customer.getOrders()
			: List.of();
	}
}
