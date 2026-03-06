package org.eclipse.store.storage.restservice.javalin.types;

/*-
 * #%L
 * EclipseStore Storage REST Service Javalin
 * %%
 * Copyright (C) 2023 - 2025 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import java.util.ArrayList;
import java.util.stream.Stream;

import io.javalin.http.Context;
import io.javalin.http.Handler;

public final class AllRoutesHandler implements Handler
{

	private final String storageName;

	public AllRoutesHandler(final String storageName)
	{
		this.storageName = storageName;
	}

	@Override
	public void handle(final Context ctx)
	{
		ctx.contentType("application/json");

		var routes = new ArrayList<>(Stream.of(
				new RouteWithMethodsDto("/" + storageName + "/", "get"),
				new RouteWithMethodsDto("/" + storageName + "/root", "get"),
				new RouteWithMethodsDto("/" + storageName + "/dictionary", "get"),
				new RouteWithMethodsDto("/" + storageName + "/object/:oid", "get"),
				new RouteWithMethodsDto("/" + storageName + "/maintenance/filesStatistics", "get")
		).toList());

		ctx.contentType("application/json").result(routes.toString());
	}
}
