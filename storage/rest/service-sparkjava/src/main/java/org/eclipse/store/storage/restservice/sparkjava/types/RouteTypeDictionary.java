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

import org.eclipse.store.storage.restadapter.types.StorageRestAdapterTypeDictionary;

import spark.Request;
import spark.Response;

public class RouteTypeDictionary extends RouteBase<StorageRestAdapterTypeDictionary>
{
	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////

	public RouteTypeDictionary(final StorageRestAdapterTypeDictionary apiAdapter)
	{
		super(apiAdapter);
	}


	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////

	@Override
	public String handle(final Request request, final Response response)
	{
		final String string = this.apiAdapter.getTypeDictionary();
		response.type("application/text");
		return string;
	}

}
