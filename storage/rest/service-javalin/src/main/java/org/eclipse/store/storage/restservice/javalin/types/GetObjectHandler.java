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

import org.eclipse.store.storage.restadapter.types.StorageRestAdapterObject;
import org.eclipse.store.storage.restadapter.types.ViewerObjectDescription;

import io.javalin.http.Context;
import io.javalin.http.Handler;

public final class GetObjectHandler extends JavalinRouteBaseConvertable<StorageRestAdapterObject> implements Handler {

	public GetObjectHandler(final StorageRestAdapterObject storageRestAdapter) {
		super(storageRestAdapter);
	}

	@Override
	public void handle(final Context ctx) {
		final long fixedOffset       = this.getLongParam(ctx, "fixedOffset", 0L);
		final long fixedLength       = this.getLongParam(ctx, "fixedLength", Long.MAX_VALUE);
		final long variableOffset    = this.getLongParam(ctx, "variableOffset", 0L);
		final long variableLength    = this.getLongParam(ctx, "variableLength", Long.MAX_VALUE);
		final long valueLength       = this.getLongParam(ctx, "valueLength", this.apiAdapter.getDefaultValueLength());
		final boolean resolveRefs    = this.getBooleanParam(ctx, "references", false);
		final String requestedFormat = this.getStringParam(ctx, "format");

		final long objectId = this.validateObjectId(ctx);

		final ViewerObjectDescription obj = this.apiAdapter.getObject(
				objectId,
				fixedOffset,
				fixedLength,
				variableOffset,
				variableLength,
				valueLength,
				resolveRefs
		);

		final String payload = this.toRequestedFormat(obj, requestedFormat, ctx);
		ctx.result(payload);
	}
}
