package org.eclipse.store.storage.restclient.app.ui;

/*-
 * #%L
 * EclipseStore Storage REST Client App
 * %%
 * Copyright (C) 2023 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.splitlayout.SplitLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.treegrid.TreeGrid;
import com.vaadin.flow.data.provider.hierarchy.HierarchicalDataProvider;
import com.vaadin.flow.function.SerializablePredicate;

import org.eclipse.store.storage.restclient.app.types.SessionData;
import org.eclipse.store.storage.restclient.jersey.types.StorageRestClientJersey;
import org.eclipse.store.storage.restclient.types.StorageView;
import org.eclipse.store.storage.restclient.types.StorageViewConfiguration;
import org.eclipse.store.storage.restclient.types.StorageViewElement;


public class StorageViewComponent extends SplitLayout
{
	public StorageViewComponent()
	{
		super();

		this.setOrientation(Orientation.VERTICAL);
		this.setSplitterPosition(65);
		this.setSizeFull();

		final TreeGrid<StorageViewElement> treeGrid = StorageViewTreeGridBuilder.New(this).build();
		treeGrid.setId(ElementIds.GRID_DATA);
		this.addToPrimary(treeGrid);

		final Div secondaryDiv = new Div();
		this.addToSecondary(secondaryDiv);

		treeGrid.addSelectionListener(event -> {
			final StorageViewElement element = event.getFirstSelectedItem().orElse(null);
			secondaryDiv.removeAll();
			if(element != null)
			{
				if(element.hasMembers())
				{
					final TreeGrid<StorageViewElement> detailTreeGrid = StorageViewTreeGridBuilder.New(this).build();
					detailTreeGrid.setId(ElementIds.GRID_DETAIL);
					detailTreeGrid.setDataProvider(StorageViewDataProvider.New(element));
					detailTreeGrid.expand(element);
					detailTreeGrid.setSizeFull();
					secondaryDiv.add(detailTreeGrid);
				}
				else
				{
					final String   value    = element.value();
					final TextArea textArea = new TextArea();
					textArea.setId(ElementIds.TEXT_DETAIL);
					textArea.setValue(value != null ? value : "");
					textArea.setReadOnly(true);
					textArea.setWidth("100%");
					secondaryDiv.add(textArea);
				}
			}
		});

		treeGrid.addCollapseListener(event -> {
			final HierarchicalDataProvider<StorageViewElement, SerializablePredicate<StorageViewElement>>
				dataProvider = treeGrid.getDataProvider()
			;
			event.getItems().forEach(item -> dataProvider.refreshItem(item, true));
		});

		this.addAttachListener(event -> {
			final SessionData sessionData = event.getUI().getSession().getAttribute(SessionData.class);
			final StorageView storageView = StorageView.New(
				StorageViewConfiguration.Default(),
				StorageRestClientJersey.New(sessionData.baseUrl())
			);
			treeGrid.setDataProvider(StorageViewDataProvider.New(storageView));
		});
	}
}
