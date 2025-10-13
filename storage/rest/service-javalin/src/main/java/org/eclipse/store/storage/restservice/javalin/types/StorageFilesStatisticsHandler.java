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


import org.eclipse.store.storage.restadapter.types.StorageRestAdapterStorageInfo;

import io.javalin.http.Context;
import io.javalin.http.Handler;

public final class StorageFilesStatisticsHandler extends JavalinRouteBaseConvertable<StorageRestAdapterStorageInfo>
		implements Handler {

	public StorageFilesStatisticsHandler(final StorageRestAdapterStorageInfo apiAdapter) {
		super(apiAdapter);
	}

	@Override
	public void handle(final Context ctx) {
		final String requestedFormat = this.getStringParam(ctx, "format");
		final Object stats = this.apiAdapter.getStorageFilesStatistics();
		final String payload = this.toRequestedFormat(stats, requestedFormat, ctx);
		ctx.result(payload);
	}
}
