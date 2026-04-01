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

import java.util.Map;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import org.eclipse.store.demo.vinoteca.dto.DataMetrics;
import org.eclipse.store.demo.vinoteca.dto.WineStatsResult;
import org.eclipse.store.demo.vinoteca.model.Wine;
import org.eclipse.store.demo.vinoteca.service.CustomerService;
import org.eclipse.store.demo.vinoteca.service.DataGeneratorService;
import org.eclipse.store.demo.vinoteca.service.WineService;
import org.eclipse.store.demo.vinoteca.ui.MainLayout;

@Route(value = "analytics", layout = MainLayout.class)
@PageTitle("Analytics | Vinoteca")
public class AnalyticsView extends VerticalLayout
{
	public AnalyticsView(
		final WineService          wineService,
		final CustomerService      customerService,
		final DataGeneratorService dataGeneratorService
	)
	{
		setSizeFull();

		final DataMetrics metrics     = dataGeneratorService.getMetrics();
		final WineStatsResult stats   = wineService.getStats();

		// Summary cards
		final HorizontalLayout summary = new HorizontalLayout(
			createCard("Wineries", String.valueOf(metrics.wineries())),
			createCard("Wines", String.valueOf(metrics.wines())),
			createCard("Customers", String.valueOf(metrics.customers())),
			createCard("Orders", String.valueOf(metrics.orders())),
			createCard("Reviews", String.valueOf(metrics.reviews())),
			createCard("Avg Rating", String.format("%.1f", stats.averageRating())),
			createCard("Avg Price", String.format("%.2f EUR", stats.averagePrice()))
		);

		// Type distribution
		add(new H3("Overview"), summary);

		add(new H3("Wine Type Distribution"));
		final Grid<Map.Entry<String, Long>> typeGrid = new Grid<>();
		typeGrid.addColumn(Map.Entry::getKey).setHeader("Type");
		typeGrid.addColumn(Map.Entry::getValue).setHeader("Count");
		typeGrid.setItems(stats.typeDistribution().entrySet());
		typeGrid.setHeight("200px");
		add(typeGrid);

		// Country distribution
		add(new H3("Country Distribution"));
		final Grid<Map.Entry<String, Long>> countryGrid = new Grid<>();
		countryGrid.addColumn(Map.Entry::getKey).setHeader("Country");
		countryGrid.addColumn(Map.Entry::getValue).setHeader("Count");
		countryGrid.setItems(stats.countryDistribution().entrySet());
		countryGrid.setHeight("200px");
		add(countryGrid);

		// Top rated wines
		add(new H3("Top 10 Rated Wines"));
		final Grid<Wine> topGrid = new Grid<>(Wine.class, false);
		topGrid.addColumn(Wine::getName).setHeader("Name").setAutoWidth(true);
		topGrid.addColumn(Wine::getRating).setHeader("Rating");
		topGrid.addColumn(w -> w.getType().name()).setHeader("Type");
		topGrid.addColumn(w -> w.getWinery().getRegion()).setHeader("Region");
		topGrid.addColumn(Wine::getVintage).setHeader("Vintage");
		topGrid.setItems(wineService.topRated(10));
		topGrid.setHeight("300px");
		topGrid.addItemClickListener(e -> new WineDetailDialog(
			e.getItem(), wineService, customerService,
			() -> topGrid.getDataProvider().refreshItem(e.getItem())
		).open());
		add(topGrid);
	}

	private VerticalLayout createCard(final String label, final String value)
	{
		final VerticalLayout card = new VerticalLayout();
		card.setWidth("120px");
		card.setPadding(true);
		card.getStyle()
			.set("background", "var(--lumo-contrast-5pct)")
			.set("border-radius", "var(--lumo-border-radius-m)")
			.set("text-align", "center");

		final Span valueSpan = new Span(value);
		valueSpan.getStyle().set("font-size", "var(--lumo-font-size-xl)").set("font-weight", "bold");

		final Span labelSpan = new Span(label);
		labelSpan.getStyle().set("color", "var(--lumo-secondary-text-color)").set("font-size", "var(--lumo-font-size-s)");

		card.add(valueSpan, labelSpan);
		return card;
	}
}
