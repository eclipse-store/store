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
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import org.eclipse.store.demo.vinoteca.model.Wine;
import org.eclipse.store.demo.vinoteca.service.CustomerService;
import org.eclipse.store.demo.vinoteca.service.WineService;
import org.eclipse.store.demo.vinoteca.ui.MainLayout;

/**
 * Lucene full-text search screen for the wines GigaMap.
 * <p>
 * The user enters a Lucene query — leveraging tokenised text fields ({@code name},
 * {@code tastingNotes}, {@code aroma}, {@code foodPairing}) and untokenised string fields
 * ({@code type}, {@code grapeVariety}, {@code region}, {@code country}) populated by
 * {@link org.eclipse.store.demo.vinoteca.index.WineDocumentPopulator WineDocumentPopulator}.
 * Pre-defined sample queries (chips) demonstrate the supported syntax.
 */
@Route(value = "search", layout = MainLayout.class)
@PageTitle("Full-Text Search | Vinoteca")
public class FullTextSearchView extends VerticalLayout
{
	/**
	 * @param wineService     the wine application service (executes the Lucene query)
	 * @param customerService the customer service (forwarded to the detail dialog)
	 */
	public FullTextSearchView(final WineService wineService, final CustomerService customerService)
	{
		setSizeFull();

		final Paragraph help = new Paragraph(
			"Lucene query syntax — Text fields: name, tastingNotes, aroma, foodPairing. " +
			"Exact fields: type, grapeVariety, region, country. " +
			"Operators: AND, OR, NOT. Phrases: \"pinot noir\""
		);
		help.getStyle().set("color", "var(--lumo-secondary-text-color)");

		final TextField queryField = new TextField("Lucene Query");
		queryField.setWidthFull();
		queryField.setPlaceholder("e.g., tastingNotes:cherry AND type:red");

		final HorizontalLayout chips = new HorizontalLayout();
		addChip(chips, queryField, "tastingNotes:cherry AND type:red");
		addChip(chips, queryField, "aroma:pepper");
		addChip(chips, queryField, "country:france");
		addChip(chips, queryField, "foodPairing:steak");
		addChip(chips, queryField, "name:cabernet");

		final IntegerField maxResults = new IntegerField("Max Results");
		maxResults.setValue(20);
		maxResults.setMin(1);
		maxResults.setMax(100);

		final Grid<Wine> grid = new Grid<>(Wine.class, false);
		grid.addColumn(Wine::getName).setHeader("Name").setAutoWidth(true);
		grid.addColumn(w -> w.getType().name()).setHeader("Type");
		grid.addColumn(w -> w.getGrapeVariety().name().replace('_', ' ')).setHeader("Grape");
		grid.addColumn(Wine::getVintage).setHeader("Vintage");
		grid.addColumn(w -> w.getWinery().getRegion()).setHeader("Region");
		grid.addColumn(w -> w.getWinery().getCountry()).setHeader("Country");
		grid.addColumn(Wine::getTastingNotes).setHeader("Tasting Notes").setAutoWidth(true);
		grid.setSizeFull();
		grid.addItemClickListener(e -> new WineDetailDialog(
			e.getItem(), wineService, customerService,
			() -> grid.getDataProvider().refreshItem(e.getItem())
		).open());

		final Button searchBtn = new Button("Search", e -> {
			if (!queryField.isEmpty())
			{
				try
				{
					final var results = wineService.fulltextSearch(queryField.getValue(), maxResults.getValue());
					grid.setItems(results);
					Notification.show("Found " + results.size() + " results");
				}
				catch (final Exception ex)
				{
					Notification.show("Query error: " + ex.getMessage());
				}
			}
		});

		final HorizontalLayout controls = new HorizontalLayout(queryField, maxResults, searchBtn);
		controls.setDefaultVerticalComponentAlignment(Alignment.END);
		controls.setWidthFull();
		controls.setFlexGrow(1, queryField);

		add(help, chips, controls, grid);
	}

	private void addChip(final HorizontalLayout container, final TextField target, final String expression)
	{
		final Button chip = new Button(expression);
		chip.getStyle().set("font-size", "var(--lumo-font-size-xs)");
		chip.addClickListener(e -> target.setValue(expression));
		container.add(chip);
	}
}
