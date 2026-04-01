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
import com.vaadin.flow.component.html.Pre;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import org.eclipse.store.demo.vinoteca.model.Wine;
import org.eclipse.store.demo.vinoteca.service.JexlService;
import org.eclipse.store.demo.vinoteca.ui.MainLayout;

@Route(value = "jexl", layout = MainLayout.class)
@PageTitle("JEXL Playground | Vinoteca")
public class JexlPlaygroundView extends VerticalLayout
{
	public JexlPlaygroundView(final JexlService jexlService)
	{
		setSizeFull();

		final RadioButtonGroup<String> modeGroup = new RadioButtonGroup<>("Mode");
		modeGroup.setItems("Filter Wines", "Evaluate Expression");
		modeGroup.setValue("Filter Wines");

		final TextArea expressionArea = new TextArea("JEXL Expression");
		expressionArea.setWidthFull();
		expressionArea.setHeight("100px");
		expressionArea.setPlaceholder("wine.rating > 90 && wine.type == 'RED'");

		final Paragraph examples = new Paragraph(
			"Filter examples: wine.rating > 90 && wine.type == 'RED' | " +
			"wine.vintage >= 2015 && wine.winery.country == 'France' | " +
			"wine.alcoholContent < 13. " +
			"Evaluate examples: wines.size() | " +
			"wines.stream().mapToDouble(w -> w.getRating()).average().orElse(0)"
		);
		examples.getStyle().set("color", "var(--lumo-secondary-text-color)").set("font-size", "var(--lumo-font-size-s)");

		// Example chips
		final HorizontalLayout chips = new HorizontalLayout();
		addChip(chips, expressionArea, "wine.rating > 90 && wine.type == 'RED'");
		addChip(chips, expressionArea, "wine.vintage >= 2015 && wine.winery.country == 'France'");
		addChip(chips, expressionArea, "wine.alcoholContent < 13");
		addChip(chips, expressionArea, "wines.size()");

		final Grid<Wine> wineGrid = new Grid<>(Wine.class, false);
		wineGrid.addColumn(Wine::getName).setHeader("Name").setAutoWidth(true);
		wineGrid.addColumn(w -> w.getType().name()).setHeader("Type");
		wineGrid.addColumn(Wine::getRating).setHeader("Rating");
		wineGrid.addColumn(Wine::getVintage).setHeader("Vintage");
		wineGrid.addColumn(w -> w.getWinery().getCountry()).setHeader("Country");
		wineGrid.setSizeFull();
		wineGrid.setVisible(false);

		final Pre resultArea = new Pre();
		resultArea.setWidthFull();
		resultArea.getStyle().set("background", "var(--lumo-contrast-5pct)")
			.set("padding", "var(--lumo-space-m)")
			.set("border-radius", "var(--lumo-border-radius-m)")
			.set("overflow", "auto")
			.set("max-height", "200px");
		resultArea.setVisible(false);

		final Button executeBtn = new Button("Execute", e -> {
			if (expressionArea.isEmpty())
			{
				return;
			}
			try
			{
				if ("Filter Wines".equals(modeGroup.getValue()))
				{
					final var results = jexlService.filterWines(expressionArea.getValue());
					wineGrid.setItems(results);
					wineGrid.setVisible(true);
					resultArea.setVisible(false);
					Notification.show("Found " + results.size() + " matching wines");
				}
				else
				{
					final Object result = jexlService.evaluate(expressionArea.getValue());
					resultArea.setText(String.valueOf(result));
					resultArea.setVisible(true);
					wineGrid.setVisible(false);
				}
			}
			catch (final Exception ex)
			{
				Notification.show("Error: " + ex.getMessage());
			}
		});

		final HorizontalLayout controls = new HorizontalLayout(modeGroup, executeBtn);
		controls.setDefaultVerticalComponentAlignment(Alignment.END);

		add(expressionArea, chips, examples, controls, wineGrid, resultArea);
	}

	private void addChip(final HorizontalLayout container, final TextArea target, final String expression)
	{
		final Button chip = new Button(expression.length() > 40 ? expression.substring(0, 37) + "..." : expression);
		chip.getStyle().set("font-size", "var(--lumo-font-size-xs)");
		chip.addClickListener(e -> target.setValue(expression));
		container.add(chip);
	}
}
