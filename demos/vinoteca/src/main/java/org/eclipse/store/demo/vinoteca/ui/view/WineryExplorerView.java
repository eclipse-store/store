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
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import org.eclipse.store.demo.vinoteca.dto.NearbyWineryResult;
import org.eclipse.store.demo.vinoteca.model.Winery;
import org.eclipse.store.demo.vinoteca.service.WineryService;
import org.eclipse.store.demo.vinoteca.ui.LeafletMap;
import org.eclipse.store.demo.vinoteca.ui.MainLayout;

/**
 * Geographic explorer of the wineries collection.
 * <p>
 * Combines a {@link LeafletMap} with a {@link Grid} of wineries plus filter controls that
 * exercise the spatial and bitmap indices on the wineries GigaMap: a "find nearby" panel
 * (latitude/longitude/radius), hemisphere and country drop-downs, and a click-to-pick interaction
 * on the map and grid that keeps both views in sync.
 */
@Route(value = "wineries", layout = MainLayout.class)
@PageTitle("Winery Explorer | Vinoteca")
public class WineryExplorerView extends VerticalLayout
{
	private final WineryService wineryService;
	private final Grid<Winery>  wineryGrid;
	private final LeafletMap    map;

	/**
	 * @param wineryService the winery application service that backs all queries on this view
	 */
	public WineryExplorerView(final WineryService wineryService)
	{
		this.wineryService = wineryService;
		this.wineryGrid = new Grid<>(Winery.class, false);
		this.map        = new LeafletMap();
		setSizeFull();

		// Spatial search panel
		final NumberField latField    = new NumberField("Latitude");
		final NumberField lonField    = new NumberField("Longitude");
		final NumberField radiusField = new NumberField("Radius (km)");
		radiusField.setValue(100.0);

		final Button nearbyBtn = new Button("Find Nearby", e -> {
			if (latField.getValue() != null && lonField.getValue() != null)
			{
				final List<Winery> results = this.wineryService.nearby(
					latField.getValue(), lonField.getValue(), radiusField.getValue()
				).stream().map(NearbyWineryResult::winery).toList();
				this.wineryGrid.setItems(results);
				this.updateMap(results);
				Notification.show("Found " + results.size() + " nearby wineries");
			}
		});

		final ComboBox<String> hemisphereFilter = new ComboBox<>("Hemisphere");
		hemisphereFilter.setItems("north", "south", "east", "west");
		final ComboBox<String> countryFilter = new ComboBox<>("Country");
		countryFilter.setItems(this.wineryService.countries().stream().sorted().toList());

		hemisphereFilter.addValueChangeListener(e -> {
			if (e.getValue() != null)
			{
				countryFilter.clear();
				final List<Winery> results = this.wineryService.hemisphere(e.getValue());
				this.wineryGrid.setItems(results);
				this.updateMap(results);
			}
		});

		countryFilter.addValueChangeListener(e -> {
			if (e.getValue() != null)
			{
				hemisphereFilter.clear();
				final List<Winery> results = this.wineryService.byCountry(e.getValue());
				this.wineryGrid.setItems(results);
				this.updateMap(results);
			}
		});

		final Button resetBtn = new Button("Show All", e -> {
			hemisphereFilter.clear();
			countryFilter.clear();
			this.loadAll();
		});

		final HorizontalLayout spatialPanel = new HorizontalLayout(
			latField, lonField, radiusField, nearbyBtn
		);
		spatialPanel.setDefaultVerticalComponentAlignment(Alignment.END);

		final HorizontalLayout filterPanel = new HorizontalLayout(
			hemisphereFilter, countryFilter, resetBtn
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
		this.wineryGrid.addItemClickListener(e -> {
			final Winery w = e.getItem();
			latField.setValue(w.getLatitude());
			lonField.setValue(w.getLongitude());
			this.map.selectMarker(w.getLatitude(), w.getLongitude());
		});

		this.map.setHeight("400px");
		this.map.setWidthFull();
		this.map.addMapClickListener((lat, lon) -> {
			latField.setValue(Math.round(lat * 10000.0) / 10000.0);
			lonField.setValue(Math.round(lon * 10000.0) / 10000.0);
		});

		add(spatialPanel, filterPanel, this.map, this.wineryGrid);
		this.loadAll();
	}

	private void loadAll()
	{
		final List<Winery> wineries = this.wineryService.list(0, 1000).content();
		this.wineryGrid.setItems(wineries);
		this.updateMap(wineries);
	}

	private void updateMap(final List<Winery> wineries)
	{
		this.map.setMarkers(
			wineries.stream()
				.map(w -> new LeafletMap.Marker(
					w.getLatitude(),
					w.getLongitude(),
					"<b>" + w.getName() + "</b><br>" + w.getRegion() + ", " + w.getCountry()
				))
				.toList()
		);
	}
}
