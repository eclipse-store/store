package org.eclipse.store.storage.restservice.javalin.types;

import java.util.function.Function;

import org.eclipse.store.storage.restadapter.types.StorageRestAdapter;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.Handler;

public final class AllRoutesHandler implements Handler {

	private final StorageRestAdapter storageRestAdapter;

	public AllRoutesHandler(final StorageRestAdapter storageRestAdapter) {
		this.storageRestAdapter = storageRestAdapter;
	}

	@Override
	public void handle(final Context ctx) {
		ctx.contentType("application/json");

		String host = ctx.host(); // e.g. localhost:8080
		final String contextPath = ctx.req().getContextPath(); // e.g. /app
		if (contextPath != null && !contextPath.isEmpty()) {
			host += contextPath;
		}

		final String payload = storageRestAdapter.getAllRoutes(host);
		ctx.result(payload);
	}
}
