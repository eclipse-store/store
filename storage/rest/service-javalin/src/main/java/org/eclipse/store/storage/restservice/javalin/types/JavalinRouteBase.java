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



import org.eclipse.store.storage.restservice.javalin.exceptions.InvalidRouteParametersException;

import io.javalin.http.Context;

public abstract class JavalinRouteBase<T> {
	protected final T apiAdapter;

	protected JavalinRouteBase(final T apiAdapter) {
		this.apiAdapter = apiAdapter;
	}

	protected boolean getBooleanParam(final Context ctx, final String name, final boolean defaultValue) {
		final String param = ctx.queryParam(name);
		if (param == null) return defaultValue;

		final String v = param.toLowerCase();
		if ("true".equals(v) || "false".equals(v)) {
			return Boolean.parseBoolean(v);
		}
		throw new InvalidRouteParametersException(name);
	}

	protected long getLongParam(final Context ctx, final String name, final long defaultValue) {
		final String param = ctx.queryParam(name);
		if (param == null) return defaultValue;
		try {
			return Long.parseLong(param);
		} catch (NumberFormatException e) {
			throw new InvalidRouteParametersException(name);
		}
	}

	protected String getStringParam(final Context ctx, final String name) {
		return ctx.queryParam(name);
	}

	protected long validateObjectId(final Context ctx) {
		try {
			return Long.parseLong(ctx.pathParam("oid"));
		} catch (Exception e) {
			throw new InvalidRouteParametersException("ObjectId");
		}
	}
}
