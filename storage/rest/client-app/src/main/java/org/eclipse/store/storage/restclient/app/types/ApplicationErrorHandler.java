package org.eclipse.store.storage.restclient.app.types;

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

import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.DefaultErrorHandler;
import com.vaadin.flow.server.ErrorEvent;
import com.vaadin.flow.server.ErrorHandler;
import com.vaadin.flow.server.VaadinSession;

import org.eclipse.serializer.util.logging.Logging;
import org.eclipse.store.storage.restclient.app.ui.InternalErrorView;


public class ApplicationErrorHandler implements ErrorHandler
{
	public static final String THROWABLE_ATTRIBUTE = ApplicationErrorHandler.class.getName() + "#THROWABLE";
	
	public static void handle(final Throwable throwable)
	{
		Logging.getLogger(ApplicationErrorHandler.class)
			.error(throwable.getMessage(), throwable);
		
		VaadinSession.getCurrent().setAttribute(THROWABLE_ATTRIBUTE, throwable);
		UI.getCurrent().navigate(InternalErrorView.class);
	}
	
	
	public ApplicationErrorHandler()
	{
		super();
	}
	
	@Override
	public void error(final ErrorEvent event)
	{
		handle(DefaultErrorHandler.findRelevantThrowable(event.getThrowable()));
	}
	
}
