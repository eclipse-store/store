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

import org.eclipse.store.storage.restadapter.types.StorageViewDataConverter;
import org.eclipse.store.storage.restadapter.types.StorageViewDataConverterProvider;
import org.eclipse.store.storage.restservice.sparkjava.exceptions.InvalidRouteParametersException;

import spark.Response;

public abstract class RouteBaseConvertable<T extends StorageViewDataConverterProvider> extends RouteBase<T>
{
	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////

	public RouteBaseConvertable(final T apiAdapter)
	{
		super(apiAdapter);
	}


	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////

	public String toRequestedFormat(final Object object, final String requestedFormat, final Response response)
	{
		if(requestedFormat != null)
		{
			final StorageViewDataConverter converter = this.apiAdapter.getConverter(requestedFormat);
			if(converter != null)
			{
				final String responseContentType = converter.getHtmlResponseContentType();

				if(responseContentType != null)
				{
					response.type(responseContentType);
				}

				return converter.convert(object);
			}
			throw new InvalidRouteParametersException("format");
		}

		response.type("application/json");
		return this.apiAdapter.getConverter("application/json").convert(object);
	}
}
