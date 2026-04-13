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

/**
 * Application service for the customer master data.
 * <p>
 * Customers are stored in a plain {@link java.util.List} on the {@link DataRoot} (rather than in
 * a GigaMap) — addressed by their position in that list. This makes the demo's customer model
 * intentionally simple at the cost of losing the GigaMap indexing facilities, but it is enough to
 * power the customers view and to back the {@code customerIndex} fields used by
 * {@link org.eclipse.store.demo.vinoteca.dto.OrderInput OrderInput} and
 * {@link org.eclipse.store.demo.vinoteca.dto.ReviewInput ReviewInput}.
 */
@Service
@Mutex("customerStore")
public class CustomerService
{
	private final DataRoot               dataRoot;
	private final EmbeddedStorageManager storageManager;

	/**
	 * @param dataRoot       the persistent root from which the customers list is taken
	 * @param storageManager the EclipseStore storage manager, used to persist the customers list
	 *                       after structural changes
	 */
	public CustomerService(final DataRoot dataRoot, final EmbeddedStorageManager storageManager)
	{
		this.dataRoot       = dataRoot;
		this.storageManager = storageManager;
	}

	/**
	 * Returns a slice of the customers list.
	 *
	 * @param page the zero-based page number
	 * @param size the page size
	 * @return a page wrapping the customers in the requested slice and the total customer count
	 */
	@Read
	public PageResult<Customer> list(final int page, final int size)
	{
		final List<Customer> customers = this.dataRoot.getCustomers();
		final int total = customers.size();
		final int from  = Math.min(page * size, total);
		final int to    = Math.min(from + size, total);
		return new PageResult<>(customers.subList(from, to), total, page, size);
	}

	/**
	 * Looks up a single customer by its position in the customers list.
	 *
	 * @param index the zero-based list index
	 * @return the customer, or {@code null} if {@code index} is out of range
	 */
	@Read
	public Customer findByIndex(final int index)
	{
		final List<Customer> customers = this.dataRoot.getCustomers();
		return index >= 0 && index < customers.size() ? customers.get(index) : null;
	}

	/**
	 * Creates and persists a new customer with an empty order history.
	 *
	 * @param input the customer to create
	 * @return the created customer
	 */
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

	/**
	 * Returns the order history of a single customer.
	 *
	 * @param customerIndex the zero-based list index of the customer
	 * @return the (possibly empty) order list; an empty list if {@code customerIndex} is out of
	 *         range
	 */
	@Read
	public List<Order> getOrders(final int customerIndex)
	{
		final Customer customer = this.findByIndex(customerIndex);
		return customer != null && customer.getOrders() != null
			? customer.getOrders()
			: List.of();
	}
}
