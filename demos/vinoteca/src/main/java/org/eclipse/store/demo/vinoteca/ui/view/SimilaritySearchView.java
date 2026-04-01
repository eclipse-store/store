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
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import org.eclipse.store.demo.vinoteca.dto.SimilarWineResult;
import org.eclipse.store.demo.vinoteca.service.WineService;
import org.eclipse.store.demo.vinoteca.ui.MainLayout;

@Route(value = "similarity", layout = MainLayout.class)
@PageTitle("Similarity Search | Vinoteca")
public class SimilaritySearchView extends VerticalLayout
{
	public SimilaritySearchView(final WineService wineService)
	{
		setSizeFull();

		final TextField queryField = new TextField("Describe a wine you're looking for");
		queryField.setWidthFull();
		queryField.setPlaceholder("e.g., fruity red wine with cherry and spice notes");

		final IntegerField kField = new IntegerField("Number of results");
		kField.setValue(5);
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

		add(controls, grid);
	}
}
