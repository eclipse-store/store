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

import org.eclipse.store.storage.restadapter.types.StorageRestAdapterStorageInfo;

import spark.Request;
import spark.Response;

public class RouteStorageFilesStatistics extends RouteBaseConvertable<StorageRestAdapterStorageInfo>
{
	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////

	public RouteStorageFilesStatistics(final StorageRestAdapterStorageInfo apiAdapter)
	{
		super(apiAdapter);
	}

	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////

	@Override
	public Object handle(final Request request, final Response response)
	{
		final String requestedFormat = this.getStringParameter(request, "format");

		return this.toRequestedFormat(this.apiAdapter.getStorageFilesStatistics(),
			requestedFormat,
			response);
	}

}
