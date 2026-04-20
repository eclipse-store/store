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

import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import org.eclipse.store.demo.vinoteca.model.Order;
import org.eclipse.store.demo.vinoteca.model.OrderItem;
import org.eclipse.store.demo.vinoteca.model.OrderStatus;
import org.eclipse.store.demo.vinoteca.service.OrderService;
import org.eclipse.store.demo.vinoteca.ui.MainLayout;

/**
 * Master/detail orders screen with a status filter. The top grid lists orders; selecting a row
 * shows the line items of that order in a second grid below. The status drop-down filters the
 * top grid via {@link OrderService#byStatus}.
 */
@Route(value = "orders", layout = MainLayout.class)
@PageTitle("Orders | Vinoteca")
public class OrdersView extends VerticalLayout
{
	private final OrderService orderService;
	private final Grid<Order>  orderGrid;

	/**
	 * @param orderService the order application service that provides the listings and filters
	 */
	public OrdersView(final OrderService orderService)
	{
		this.orderService = orderService;
		setSizeFull();

		this.orderGrid = new Grid<>(Order.class, false);

		final ComboBox<OrderStatus> statusFilter = new ComboBox<>("Filter by Status");
		statusFilter.setItems(OrderStatus.values());
		statusFilter.setClearButtonVisible(true);

		final Button filterBtn = new Button("Filter", e -> {
			if (statusFilter.getValue() != null)
			{
				this.orderGrid.setItems(this.orderService.byStatus(statusFilter.getValue()));
			}
			else
			{
				this.loadAll();
			}
		});

		final Button resetBtn = new Button("Show All", e -> {
			statusFilter.clear();
			this.loadAll();
		});

		final HorizontalLayout filters = new HorizontalLayout(statusFilter, filterBtn, resetBtn);
		filters.setDefaultVerticalComponentAlignment(Alignment.END);

		this.orderGrid.addColumn(o -> o.getCustomer().getFullName()).setHeader("Customer").setSortable(true);
		this.orderGrid.addColumn(o -> o.getOrderDate().toString()).setHeader("Date").setSortable(true);
		this.orderGrid.addColumn(o -> o.getStatus().name()).setHeader("Status").setSortable(true);
		this.orderGrid.addColumn(o -> o.getItems().size()).setHeader("Items");
		this.orderGrid.addColumn(o -> String.format("%.2f EUR", o.getTotal())).setHeader("Total");
		this.orderGrid.setHeight("50%");

		final Grid<OrderItem> itemGrid = new Grid<>(OrderItem.class, false);
		itemGrid.addColumn(i -> i.getWine().getName()).setHeader("Wine").setAutoWidth(true);
		itemGrid.addColumn(OrderItem::getQuantity).setHeader("Qty");
		itemGrid.addColumn(i -> String.format("%.2f EUR", i.getPriceAtPurchase()))
			.setHeader("Price");
		itemGrid.addColumn(i -> String.format("%.2f EUR", i.getSubtotal()))
			.setHeader("Subtotal");
		itemGrid.setHeight("50%");

		this.orderGrid.addSelectionListener(event ->
			event.getFirstSelectedItem().ifPresent(order -> itemGrid.setItems(order.getItems()))
		);

		add(filters, this.orderGrid, itemGrid);
		this.loadAll();
	}

	private void loadAll()
	{
		this.orderGrid.setItems(this.orderService.list(0, 1000).content());
	}
}
