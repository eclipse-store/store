
package org.eclipse.store.storage.restclient.app.types;

import org.eclipse.store.storage.restclient.app.ui.ConnectView;

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

import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.VaadinServiceInitListener;


public class ApplicationServiceInitListener implements VaadinServiceInitListener
{
	public ApplicationServiceInitListener()
	{
		super();
	}
	
	@Override
	public void serviceInit(
		final ServiceInitEvent serviceInitEvent
	)
	{		
		final VaadinService service = serviceInitEvent.getSource();
		
		service.addSessionInitListener(sessionInitEvent -> 
			sessionInitEvent.getSession().setErrorHandler(new ApplicationErrorHandler())
		);
		
		service.addUIInitListener(
			uiInitEvent -> uiInitEvent.getUI().addBeforeEnterListener(enterEvent -> {
				if(!ConnectView.class.equals(enterEvent.getNavigationTarget()) &&
					enterEvent.getUI().getSession().getAttribute(SessionData.class) == null)
				{
					enterEvent.rerouteTo(ConnectView.class);
				}
			}
		));
	}
	
}
