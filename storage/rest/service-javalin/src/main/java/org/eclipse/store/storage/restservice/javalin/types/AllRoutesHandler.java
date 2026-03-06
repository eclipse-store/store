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
import java.util.List;
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
		final String base = "/" + storageName;

		var routes = List.of(
				new RouteWithMethodsDto(base + "/", "get"),
				new RouteWithMethodsDto(base + "/root", "get"),
				new RouteWithMethodsDto(base + "/dictionary", "get"),
				new RouteWithMethodsDto(base + "/object/{oid}", "get"),
				new RouteWithMethodsDto(base + "/maintenance/filesStatistics", "get")
		);

		ctx.json(routes);
	}
}
