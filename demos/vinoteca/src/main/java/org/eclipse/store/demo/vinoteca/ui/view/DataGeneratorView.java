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

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import org.eclipse.store.demo.vinoteca.dto.DataMetrics;
import org.eclipse.store.demo.vinoteca.service.DataGeneratorService;
import org.eclipse.store.demo.vinoteca.ui.MainLayout;

@Route(value = "generate", layout = MainLayout.class)
@PageTitle("Data Generator | Vinoteca")
public class DataGeneratorView extends VerticalLayout
{
	private final DataGeneratorService generatorService;
	private final Span wineriesCount  = new Span();
	private final Span winesCount     = new Span();
	private final Span customersCount = new Span();
	private final Span ordersCount    = new Span();
	private final Span reviewsCount   = new Span();

	public DataGeneratorView(final DataGeneratorService generatorService)
	{
		this.generatorService = generatorService;

		final IntegerField countField = new IntegerField("Number of wines to generate");
		countField.setValue(50);
		countField.setMin(1);
		countField.setMax(5000);

		final Button generateBtn = new Button("Generate Data", e -> {
			final DataMetrics metrics = this.generatorService.generate(countField.getValue());
			this.updateCounts(metrics);
			Notification.show("Generated data successfully!");
		});

		final HorizontalLayout controls = new HorizontalLayout(countField, generateBtn);
		controls.setDefaultVerticalComponentAlignment(Alignment.END);

		add(
			new H3("Data Generator"),
			controls,
			new H3("Current Data Counts"),
			createCountRow("Wineries:", this.wineriesCount),
			createCountRow("Wines:", this.winesCount),
			createCountRow("Customers:", this.customersCount),
			createCountRow("Orders:", this.ordersCount),
			createCountRow("Reviews:", this.reviewsCount)
		);

		this.updateCounts(this.generatorService.getMetrics());
	}

	private void updateCounts(final DataMetrics metrics)
	{
		this.wineriesCount.setText(String.valueOf(metrics.wineries()));
		this.winesCount.setText(String.valueOf(metrics.wines()));
		this.customersCount.setText(String.valueOf(metrics.customers()));
		this.ordersCount.setText(String.valueOf(metrics.orders()));
		this.reviewsCount.setText(String.valueOf(metrics.reviews()));
	}

	private HorizontalLayout createCountRow(final String label, final Span value)
	{
		final Span labelSpan = new Span(label);
		labelSpan.getStyle().set("font-weight", "bold").set("min-width", "100px");
		value.getStyle().set("font-size", "var(--lumo-font-size-l)");
		final HorizontalLayout row = new HorizontalLayout(labelSpan, value);
		row.setDefaultVerticalComponentAlignment(Alignment.CENTER);
		return row;
	}
}
