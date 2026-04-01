package org.eclipse.store.demo.vinoteca.ui;

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

import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.theme.lumo.LumoUtility;
import org.eclipse.store.demo.vinoteca.ui.view.AnalyticsView;
import org.eclipse.store.demo.vinoteca.ui.view.CustomersView;
import org.eclipse.store.demo.vinoteca.ui.view.DataGeneratorView;
import org.eclipse.store.demo.vinoteca.ui.view.FullTextSearchView;
import org.eclipse.store.demo.vinoteca.ui.view.OrdersView;
import org.eclipse.store.demo.vinoteca.ui.view.SimilaritySearchView;
import org.eclipse.store.demo.vinoteca.ui.view.WineCatalogView;
import org.eclipse.store.demo.vinoteca.ui.view.WineryExplorerView;

public class MainLayout extends AppLayout
{
	public MainLayout()
	{
		final DrawerToggle toggle = new DrawerToggle();
		final H1 title = new H1("Vinoteca");
		title.getStyle().set("font-size", "var(--lumo-font-size-l)").set("margin", "0");

		final HorizontalLayout header = new HorizontalLayout(toggle, title);
		header.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
		header.setWidthFull();
		header.addClassNames(LumoUtility.Padding.Horizontal.MEDIUM);

		addToNavbar(header);

		final SideNav nav = new SideNav();
		nav.addItem(new SideNavItem("Wine Catalog",       WineCatalogView.class));
		nav.addItem(new SideNavItem("Wineries",            WineryExplorerView.class));
		nav.addItem(new SideNavItem("Similarity Search",   SimilaritySearchView.class));
		nav.addItem(new SideNavItem("Full-Text Search",    FullTextSearchView.class));
		nav.addItem(new SideNavItem("Customers",           CustomersView.class));
		nav.addItem(new SideNavItem("Orders",              OrdersView.class));
		nav.addItem(new SideNavItem("Analytics",           AnalyticsView.class));
		nav.addItem(new SideNavItem("Data Generator",      DataGeneratorView.class));

		addToDrawer(nav);
	}
}
