package org.eclipse.store.demo.vinoteca.ui.view;

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

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import org.eclipse.store.demo.vinoteca.model.Customer;
import org.eclipse.store.demo.vinoteca.model.Order;
import org.eclipse.store.demo.vinoteca.service.CustomerService;
import org.eclipse.store.demo.vinoteca.ui.MainLayout;

/**
 * Master/detail customer screen: a top grid lists all customers; selecting a row populates a
 * second grid below with that customer's order history.
 */
@Route(value = "customers", layout = MainLayout.class)
@PageTitle("Customers | Vinoteca")
public class CustomersView extends VerticalLayout
{
	/**
	 * @param customerService the customer application service that provides the listing
	 */
	public CustomersView(final CustomerService customerService)
	{
		setSizeFull();

		final Grid<Customer> customerGrid = new Grid<>(Customer.class, false);
		customerGrid.addColumn(Customer::getFirstName).setHeader("First Name").setSortable(true);
		customerGrid.addColumn(Customer::getLastName).setHeader("Last Name").setSortable(true);
		customerGrid.addColumn(Customer::getEmail).setHeader("Email").setSortable(true);
		customerGrid.addColumn(Customer::getCity).setHeader("City");
		customerGrid.addColumn(Customer::getCountry).setHeader("Country");
		customerGrid.addColumn(c -> c.getOrders() != null ? c.getOrders().size() : 0).setHeader("Orders");
		customerGrid.setHeight("50%");

		final Grid<Order> orderGrid = new Grid<>(Order.class, false);
		orderGrid.addColumn(o -> o.getCustomer().getFullName()).setHeader("Customer");
		orderGrid.addColumn(o -> o.getOrderDate().toString()).setHeader("Date").setSortable(true);
		orderGrid.addColumn(o -> o.getStatus().name()).setHeader("Status");
		orderGrid.addColumn(o -> o.getItems().size()).setHeader("Items");
		orderGrid.addColumn(o -> {
			final var total = o.getTotal();
			return total != null ? String.format("%.2f EUR", total.getNumber().doubleValue()) : "N/A";
		}).setHeader("Total");
		orderGrid.setHeight("50%");

		customerGrid.addSelectionListener(event ->
			event.getFirstSelectedItem().ifPresent(customer ->
				orderGrid.setItems(customer.getOrders() != null ? customer.getOrders() : java.util.List.of())
			)
		);

		customerGrid.setItems(customerService.list(0, 1000).content());

		add(customerGrid, orderGrid);
	}
}
