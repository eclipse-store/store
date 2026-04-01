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
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import org.eclipse.store.demo.vinoteca.dto.NearbyWineryResult;
import org.eclipse.store.demo.vinoteca.model.Winery;
import org.eclipse.store.demo.vinoteca.service.WineryService;
import org.eclipse.store.demo.vinoteca.ui.MainLayout;

@Route(value = "wineries", layout = MainLayout.class)
@PageTitle("Winery Explorer | Vinoteca")
public class WineryExplorerView extends VerticalLayout
{
	private final WineryService wineryService;
	private final Grid<Winery>  wineryGrid;
	private final Grid<NearbyWineryResult> nearbyGrid;

	public WineryExplorerView(final WineryService wineryService)
	{
		this.wineryService = wineryService;
		this.wineryGrid = new Grid<>(Winery.class, false);
		this.nearbyGrid = new Grid<>(NearbyWineryResult.class, false);
		setSizeFull();

		// Spatial search panel
		final NumberField latField    = new NumberField("Latitude");
		final NumberField lonField    = new NumberField("Longitude");
		final NumberField radiusField = new NumberField("Radius (km)");
		radiusField.setValue(100.0);

		final Button nearbyBtn = new Button("Find Nearby", e -> {
			if (latField.getValue() != null && lonField.getValue() != null)
			{
				final List<NearbyWineryResult> results = this.wineryService.nearby(
					latField.getValue(), lonField.getValue(), radiusField.getValue()
				);
				this.nearbyGrid.setItems(results);
				this.nearbyGrid.setVisible(true);
				Notification.show("Found " + results.size() + " nearby wineries");
			}
		});

		final ComboBox<String> hemisphereFilter = new ComboBox<>("Hemisphere");
		hemisphereFilter.setItems("north", "south", "east", "west");

		final Button hemisphereBtn = new Button("Filter", e -> {
			if (hemisphereFilter.getValue() != null)
			{
				this.wineryGrid.setItems(this.wineryService.hemisphere(hemisphereFilter.getValue()));
			}
		});

		final TextField countryFilter = new TextField("Country");
		final Button countryBtn = new Button("Filter by Country", e -> {
			if (!countryFilter.isEmpty())
			{
				this.wineryGrid.setItems(this.wineryService.byCountry(countryFilter.getValue()));
			}
		});

		final Button resetBtn = new Button("Show All", e -> this.loadAll());

		final HorizontalLayout spatialPanel = new HorizontalLayout(
			latField, lonField, radiusField, nearbyBtn
		);
		spatialPanel.setDefaultVerticalComponentAlignment(Alignment.END);

		final HorizontalLayout filterPanel = new HorizontalLayout(
			hemisphereFilter, hemisphereBtn, countryFilter, countryBtn, resetBtn
		);
		filterPanel.setDefaultVerticalComponentAlignment(Alignment.END);

		this.wineryGrid.addColumn(Winery::getName).setHeader("Name").setSortable(true).setAutoWidth(true);
		this.wineryGrid.addColumn(Winery::getRegion).setHeader("Region").setSortable(true);
		this.wineryGrid.addColumn(Winery::getCountry).setHeader("Country").setSortable(true);
		this.wineryGrid.addColumn(Winery::getLatitude).setHeader("Lat");
		this.wineryGrid.addColumn(Winery::getLongitude).setHeader("Lon");
		this.wineryGrid.addColumn(Winery::getFoundedYear).setHeader("Founded").setSortable(true);
		this.wineryGrid.addColumn(w -> w.getWines() != null ? w.getWines().size() : 0).setHeader("Wines");
		this.wineryGrid.setSizeFull();

		this.nearbyGrid.addColumn(r -> r.winery().getName()).setHeader("Winery");
		this.nearbyGrid.addColumn(r -> r.winery().getRegion()).setHeader("Region");
		this.nearbyGrid.addColumn(r -> r.winery().getCountry()).setHeader("Country");
		this.nearbyGrid.addColumn(NearbyWineryResult::distanceKm).setHeader("Distance (km)");
		this.nearbyGrid.setVisible(false);
		this.nearbyGrid.setHeight("300px");

		add(spatialPanel, filterPanel, this.wineryGrid, this.nearbyGrid);
		this.loadAll();
	}

	private void loadAll()
	{
		this.wineryGrid.setItems(this.wineryService.list(0, 1000).content());
		this.nearbyGrid.setVisible(false);
	}
}
