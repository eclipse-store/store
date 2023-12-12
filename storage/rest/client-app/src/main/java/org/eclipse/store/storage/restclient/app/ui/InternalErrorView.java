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

import java.io.PrintWriter;
import java.io.StringWriter;

import org.eclipse.store.storage.restclient.app.types.ApplicationErrorHandler;
import org.eclipse.store.storage.restclient.app.types.SessionData;

import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.NativeLabel;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.ErrorParameter;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.HasErrorParameter;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;

import jakarta.servlet.http.HttpServletResponse;


@Route(value = "error", layout = RootLayout.class)
public class InternalErrorView extends VerticalLayout
	implements HasDynamicTitle, HasErrorParameter<Exception>, BeforeEnterObserver
{
	public InternalErrorView()
	{
		super();

		this.setSizeFull();
	}
	
	@Override
	public String getPageTitle()
	{
		return this.getTranslation("ERROR") + " - " + RootLayout.PAGE_TITLE;
	}
	
	@Override
	public int setErrorParameter(
		final BeforeEnterEvent event,
		final ErrorParameter<Exception> parameter
	)
	{
		final VaadinSession session = event.getUI().getSession();
		session.setAttribute(
			ApplicationErrorHandler.THROWABLE_ATTRIBUTE,
			parameter.getCaughtException()
		);
		
		return HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
	}

	@Override
	public void beforeEnter(
		final BeforeEnterEvent event
	)
	{
		final H3 header = new H3(this.getTranslation("INTERNAL_ERROR_TITLE"));
		header.addClassName(ClassNames.ERROR);
		this.add(header);

		final VaadinSession session = event.getUI().getSession();
		final SessionData sessionData = session.getAttribute(SessionData.class);
		if(sessionData != null)
		{
			this.add(new NativeLabel(this.getTranslation("INTERNAL_ERROR_HINT", sessionData.baseUrl())));
		}
		
		final Throwable t = (Throwable)session.getAttribute(
			ApplicationErrorHandler.THROWABLE_ATTRIBUTE
		);
		if(t != null)
		{
			this.add(new Hr());
			
			final StringWriter stringWriter = new StringWriter();
			try(final PrintWriter writer = new PrintWriter(stringWriter))
			{
				t.printStackTrace(writer);
			}
			
			final TextArea stackTrace = new TextArea();
			stackTrace.setValue(stringWriter.toString());
			stackTrace.setReadOnly(true);
			stackTrace.setWidth("100%");
			
			final Details details = new Details(this.getTranslation("DETAILS"), stackTrace);
			details.setOpened(false);
			this.add(details);
			this.setHorizontalComponentAlignment(Alignment.STRETCH, details);
			this.setFlexGrow(1, details);
		}
		
	}
	
}
