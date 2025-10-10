package org.eclipse.store.storage.restservice.javalin.types;

/*-
 * #%L
 * service-javalin
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

import java.util.Objects;

import org.eclipse.store.storage.restadapter.types.StorageRestAdapterRoot;
import org.eclipse.store.storage.restadapter.types.ViewerRootDescription;

import io.javalin.http.Context;
import io.javalin.http.Handler;

public final class RootHandler implements Handler {
	private final StorageRestAdapterRoot apiAdapter;

	public RootHandler(final StorageRestAdapterRoot apiAdapter) {
		this.apiAdapter = Objects.requireNonNull(apiAdapter, "apiAdapter");
	}

	@Override
	public void handle(final Context ctx) {
		final String requestedFormat = ctx.queryParam("format");
		final ViewerRootDescription rootDescription = this.apiAdapter.getUserRoot();

		if (requestedFormat == null || requestedFormat.isEmpty() || "json".equalsIgnoreCase(requestedFormat)) {
			ctx.json(rootDescription);
			return;
		}

		if ("text".equalsIgnoreCase(requestedFormat) || "plain".equalsIgnoreCase(requestedFormat)) {
			ctx.contentType("text/plain").result(String.valueOf(rootDescription));
			return;
		}

		ctx.status(400).result("Unsupported format: " + requestedFormat);
	}
}
