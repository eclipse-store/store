package org.eclipse.store.storage.restservice.sparkjava.types;

/*-
 * #%L
 * EclipseStore Storage REST Service Sparkjava
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

import spark.Request;
import spark.Response;

public class RouteDocumentation extends RouteBase<DocumentationManager>
{
	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////

	public RouteDocumentation(final DocumentationManager apiAdapter)
	{
		super(apiAdapter);
	}

	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////

	@Override
	public Object handle(final Request request, final Response response)
	{
		final String paramMethod = this.getStringParameter(request, "method");

		response.type("application/json");

		if(paramMethod != null)
		{
			return this.apiAdapter.getDocumentation(request.uri(), paramMethod);
		}
		return this.apiAdapter.getDocumentation(request.uri());
	}
}
