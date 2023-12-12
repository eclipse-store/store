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

import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.ErrorParameter;
import com.vaadin.flow.router.NotFoundException;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteNotFoundError;

import jakarta.servlet.http.HttpServletResponse;

@Route(value = "404", layout = RootLayout.class)
@PageTitle("404 - " + RootLayout.PAGE_TITLE)
public class RouteNotFoundView extends RouteNotFoundError
{
	public RouteNotFoundView()
	{
		super();
	}
	
	@Override
	public int setErrorParameter(BeforeEnterEvent event, ErrorParameter<NotFoundException> parameter)
	{
		this.getElement().appendChild(new Span("404 - not found").getElement());
		
        return HttpServletResponse.SC_NOT_FOUND;
	}
}
