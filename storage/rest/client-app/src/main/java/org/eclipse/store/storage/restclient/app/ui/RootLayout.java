package org.eclipse.store.storage.restclient.app.ui;

import org.eclipse.store.storage.restclient.app.types.SessionData;

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

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.NativeLabel;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.RouterLayout;
import com.vaadin.flow.theme.Theme;
import com.vaadin.flow.theme.lumo.Lumo;


@Push
@Theme(themeClass = Lumo.class, variant = Lumo.DARK)
@CssImport("./styles/shared-styles.css")
public class RootLayout extends VerticalLayout
	implements RouterLayout, BeforeEnterObserver, AppShellConfigurator
{
	public final static String PAGE_TITLE = "Eclipse Store Client";
	
	private Component   toolBar;
	private NativeLabel headerLabel;
	
	public RootLayout()
	{
		super();

		this.add(this.createHeader());
		this.setDefaultHorizontalComponentAlignment(Alignment.STRETCH);
		this.setMargin(false);
		this.setPadding(false);
		this.setSizeFull();
	}
	
	private Component createHeader()
	{
		this.headerLabel = new NativeLabel();
		
		final Button cmdDisconnect = new Button(this.getTranslation("DISCONNECT"), event -> {
			this.getUI().ifPresent(ui -> {
				ui.getSession().setAttribute(SessionData.class, null);
				ui.navigate(ConnectView.class);
			});
		});
		cmdDisconnect.setId(ElementIds.BUTTON_DISCONNECT);
		cmdDisconnect.setIcon(new Image(UIUtils.imagePath("logout.svg"), ""));
		cmdDisconnect.addThemeVariants(ButtonVariant.LUMO_SMALL);
		
		final HorizontalLayout toolBar = new HorizontalLayout(cmdDisconnect);
		toolBar.setJustifyContentMode(JustifyContentMode.END);
		this.toolBar = toolBar;
		
		final HorizontalLayout header = new HorizontalLayout(
			new Image(UIUtils.imagePath("logo.png"), "Logo"),
			this.headerLabel,
			UIUtils.compact(toolBar));
		header.setDefaultVerticalComponentAlignment(Alignment.CENTER);
		header.setFlexGrow(1, toolBar);
		
		header.addClassName(ClassNames.HEADER);
		
		return UIUtils.compact(header);
	}
	
	@Override
	public void beforeEnter(
		final BeforeEnterEvent event
	)
	{
		final SessionData sessionData = event.getUI().getSession().getAttribute(SessionData.class);
		this.headerLabel.setText(
			sessionData != null
				? this.getTranslation("CLIENT") + " - " + sessionData.baseUrl()
				: this.getTranslation("CLIENT")
		);
		this.toolBar.setVisible(
			   sessionData != null
			&& !event.getNavigationTarget().equals(ConnectView.class)
		);
	}
	
}
