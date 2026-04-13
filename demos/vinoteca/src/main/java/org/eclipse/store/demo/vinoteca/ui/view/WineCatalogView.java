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
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;
import org.eclipse.store.demo.vinoteca.model.Wine;
import org.eclipse.store.demo.vinoteca.model.WineType;
import org.eclipse.store.demo.vinoteca.service.CustomerService;
import org.eclipse.store.demo.vinoteca.service.WineService;
import org.eclipse.store.demo.vinoteca.ui.MainLayout;

/**
 * Default landing view of the Vinoteca UI. Lists all wines in a sortable {@link Grid} together
 * with text/type/country/region filters wired to the corresponding bitmap-index queries on
 * {@link WineService}. Clicking a row opens a {@link WineDetailDialog} for inspection and
 * review-adding.
 */
@Route(value = "wines", layout = MainLayout.class)
@RouteAlias(value = "", layout = MainLayout.class)
@PageTitle("Wine Catalog | Vinoteca")
public class WineCatalogView extends VerticalLayout
{
	private final WineService     wineService;
	private final CustomerService customerService;
	private final Grid<Wine>      grid;

	/**
	 * @param wineService     the wine application service (provides listing and filter queries)
	 * @param customerService the customer service (forwarded to the detail dialog so users can
	 *                        attach reviews)
	 */
	public WineCatalogView(final WineService wineService, final CustomerService customerService)
	{
		this.wineService     = wineService;
		this.customerService = customerService;

		setSizeFull();

		final TextField nameFilter = new TextField("Search by name");
		nameFilter.setPlaceholder("Type to filter...");
		nameFilter.setClearButtonVisible(true);

		final ComboBox<WineType> typeFilter = new ComboBox<>("Wine Type");
		typeFilter.setItems(WineType.values());
		typeFilter.setClearButtonVisible(true);

		final TextField countryFilter = new TextField("Country");
		countryFilter.setClearButtonVisible(true);

		final TextField regionFilter = new TextField("Region");
		regionFilter.setClearButtonVisible(true);

		final Button searchBtn = new Button("Search", e -> this.applyFilters(
			nameFilter.getValue(), typeFilter.getValue(),
			countryFilter.getValue(), regionFilter.getValue()
		));

		final Button resetBtn = new Button("Reset", e -> {
			nameFilter.clear();
			typeFilter.clear();
			countryFilter.clear();
			regionFilter.clear();
			this.loadData();
		});

		final HorizontalLayout filters = new HorizontalLayout(
			nameFilter, typeFilter, countryFilter, regionFilter, searchBtn, resetBtn
		);
		filters.setDefaultVerticalComponentAlignment(Alignment.END);

		this.grid = new Grid<>(Wine.class, false);
		this.grid.addColumn(Wine::getName).setHeader("Name").setSortable(true).setAutoWidth(true);
		this.grid.addColumn(w -> w.getType().name()).setHeader("Type").setSortable(true);
		this.grid.addColumn(w -> w.getGrapeVariety().name().replace('_', ' ')).setHeader("Grape").setSortable(true);
		this.grid.addColumn(Wine::getVintage).setHeader("Vintage").setSortable(true);
		this.grid.addColumn(w -> w.getWinery().getName()).setHeader("Winery").setSortable(true);
		this.grid.addColumn(w -> w.getWinery().getCountry()).setHeader("Country").setSortable(true);
		this.grid.addColumn(w -> w.getWinery().getRegion()).setHeader("Region").setSortable(true);
		this.grid.addColumn(w -> String.format("%.2f EUR", w.getPriceAsDouble())).setHeader("Price").setSortable(true);
		this.grid.addColumn(Wine::getRating).setHeader("Rating").setSortable(true);
		this.grid.addColumn(Wine::getBottlesInStock).setHeader("Stock").setSortable(true);
		this.grid.setSizeFull();
		this.grid.addItemClickListener(e -> {
			final WineDetailDialog dialog = new WineDetailDialog(
				e.getItem(), this.wineService, this.customerService,
				() -> this.grid.getDataProvider().refreshItem(e.getItem())
			);
			dialog.open();
		});

		add(filters, this.grid);
		this.loadData();
	}

	private void loadData()
	{
		this.grid.setItems(this.wineService.list(0, 1000).content());
	}

	private void applyFilters(
		final String name, final WineType type,
		final String country, final String region
	)
	{
		if (type != null)
		{
			this.grid.setItems(this.wineService.byType(type.name()));
		}
		else if (country != null && !country.isBlank())
		{
			this.grid.setItems(this.wineService.byCountry(country));
		}
		else if (region != null && !region.isBlank())
		{
			this.grid.setItems(this.wineService.byRegion(region));
		}
		else
		{
			this.loadData();
		}

		Notification.show("Filter applied");
	}
}
