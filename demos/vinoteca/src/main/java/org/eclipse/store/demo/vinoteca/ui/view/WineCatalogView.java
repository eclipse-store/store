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

import java.util.List;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;
import org.eclipse.store.demo.vinoteca.model.GrapeVariety;
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
	private final WineService              wineService;
	private final CustomerService          customerService;
	private final Grid<Wine>               grid;
	private final TextField                nameFilter;
	private final ComboBox<WineType>       typeFilter;
	private final ComboBox<GrapeVariety>   grapeFilter;
	private final ComboBox<Integer>        vintageFilter;
	private final ComboBox<String>         countryFilter;
	private final ComboBox<String>         regionFilter;

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

		this.nameFilter = new TextField("Search by name");
		this.nameFilter.setPlaceholder("Type to filter...");
		this.nameFilter.setClearButtonVisible(true);
		this.nameFilter.setValueChangeMode(ValueChangeMode.LAZY);
		this.nameFilter.addValueChangeListener(e -> this.applyFilters());

		this.typeFilter = new ComboBox<>("Wine Type");
		this.typeFilter.setItems(WineType.values());
		this.typeFilter.setClearButtonVisible(true);
		this.typeFilter.addValueChangeListener(e -> this.applyFilters());

		this.grapeFilter = new ComboBox<>("Grape Variety");
		this.grapeFilter.setItems(GrapeVariety.values());
		this.grapeFilter.setItemLabelGenerator(g -> g.name().replace('_', ' '));
		this.grapeFilter.setClearButtonVisible(true);
		this.grapeFilter.addValueChangeListener(e -> this.applyFilters());

		this.vintageFilter = new ComboBox<>("Vintage");
		this.vintageFilter.setItems(wineService.vintages().stream().sorted().toList());
		this.vintageFilter.setClearButtonVisible(true);
		this.vintageFilter.addValueChangeListener(e -> this.applyFilters());

		this.countryFilter = new ComboBox<>("Country");
		this.countryFilter.setItems(wineService.countries().stream().sorted().toList());
		this.countryFilter.setClearButtonVisible(true);
		this.countryFilter.addValueChangeListener(e -> this.applyFilters());

		this.regionFilter = new ComboBox<>("Region");
		this.regionFilter.setItems(wineService.regions().stream().sorted().toList());
		this.regionFilter.setClearButtonVisible(true);
		this.regionFilter.addValueChangeListener(e -> this.applyFilters());

		final Button resetBtn = new Button("Reset", e -> {
			this.nameFilter.clear();
			this.typeFilter.clear();
			this.grapeFilter.clear();
			this.vintageFilter.clear();
			this.countryFilter.clear();
			this.regionFilter.clear();
		});

		final HorizontalLayout filters = new HorizontalLayout(
			this.nameFilter, this.typeFilter, this.grapeFilter, this.vintageFilter,
			this.countryFilter, this.regionFilter, resetBtn
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
		this.grid.addColumn(w -> String.format("%.2f EUR", w.getPrice())).setHeader("Price").setSortable(true);
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
		this.applyFilters();
	}

	private void applyFilters()
	{
		final List<Wine> filtered = this.wineService.filter(
			this.nameFilter.getValue(),
			this.typeFilter.getValue(),
			this.grapeFilter.getValue(),
			this.vintageFilter.getValue(),
			this.countryFilter.getValue(),
			this.regionFilter.getValue()
		);
		this.grid.setItems(filtered);
	}
}
