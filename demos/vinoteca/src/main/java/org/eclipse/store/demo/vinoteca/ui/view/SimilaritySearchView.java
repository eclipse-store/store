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
import org.eclipse.store.demo.vinoteca.dto.SimilarWineResult;
import org.eclipse.store.demo.vinoteca.service.CustomerService;
import org.eclipse.store.demo.vinoteca.service.WineService;
import org.eclipse.store.demo.vinoteca.ui.MainLayout;

/**
 * Natural-language similarity-search screen powered by the JVector vector index registered on
 * the wines GigaMap.
 * <p>
 * The user types a free-form description (e.g. "fruity red wine with cherry and spice notes");
 * the query is encoded with the same Ollama embedding model used to populate the index, and the
 * top-{@code k} nearest neighbours are displayed with their cosine similarity scores. If Ollama
 * is unavailable the result list will be empty and the UI shows a hint.
 */
@Route(value = "similarity", layout = MainLayout.class)
@PageTitle("Similarity Search | Vinoteca")
public class SimilaritySearchView extends VerticalLayout
{
	/**
	 * @param wineService     the wine application service (executes the vector similarity search)
	 * @param customerService the customer service (forwarded to the detail dialog)
	 */
	public SimilaritySearchView(final WineService wineService, final CustomerService customerService)
	{
		setSizeFull();

		final Paragraph help = new Paragraph(
			"Describe a wine in natural language — the query is encoded with the same embedding " +
			"model used to index the wine catalog, and the nearest neighbours are returned with " +
			"their cosine similarity scores."
		);
		help.getStyle().set("color", "var(--lumo-secondary-text-color)");

		final TextField queryField = new TextField("Describe a wine you're looking for");
		queryField.setWidthFull();
		queryField.setPlaceholder("e.g., fruity red wine with cherry and spice notes");

		final HorizontalLayout chips = new HorizontalLayout();
		addChip(chips, queryField, "fruity red wine with cherry and spice notes");
		addChip(chips, queryField, "crisp white wine with citrus and mineral finish");
		addChip(chips, queryField, "bold full-bodied wine good with grilled meat");
		addChip(chips, queryField, "light aromatic wine with floral and honey aromas");
		addChip(chips, queryField, "dry sparkling wine for celebration");

		final IntegerField kField = new IntegerField("Number of results");
		kField.setValue(50);
		kField.setMin(1);
		kField.setMax(50);

		final Grid<SimilarWineResult> grid = new Grid<>(SimilarWineResult.class, false);
		grid.addColumn(r -> r.wine().getName()).setHeader("Wine").setAutoWidth(true);
		grid.addColumn(r -> r.wine().getType().name()).setHeader("Type");
		grid.addColumn(r -> r.wine().getGrapeVariety().name().replace('_', ' ')).setHeader("Grape");
		grid.addColumn(r -> r.wine().getVintage()).setHeader("Vintage");
		grid.addColumn(r -> r.wine().getWinery().getRegion()).setHeader("Region");
		grid.addColumn(r -> String.format("%.4f", r.score())).setHeader("Similarity Score");
		grid.setSizeFull();
		grid.addItemClickListener(e -> new WineDetailDialog(
			e.getItem().wine(), wineService, customerService, null
		).open());

		final Button searchBtn = new Button("Find Similar Wines", e -> {
			if (!queryField.isEmpty())
			{
				final var results = wineService.similar(queryField.getValue(), kField.getValue());
				grid.setItems(results);
				if (results.isEmpty())
				{
					Notification.show("No results. Is Ollama running with the all-minilm model?");
				}
			}
		});

		final HorizontalLayout controls = new HorizontalLayout(queryField, kField, searchBtn);
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
