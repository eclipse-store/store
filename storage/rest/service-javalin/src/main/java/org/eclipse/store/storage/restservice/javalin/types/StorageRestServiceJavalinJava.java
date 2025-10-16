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


import org.eclipse.store.storage.restadapter.exceptions.StorageRestAdapterException;
import org.eclipse.store.storage.restadapter.types.StorageRestAdapter;
import org.eclipse.store.storage.restservice.javalin.exceptions.InvalidRouteParametersException;
import org.eclipse.store.storage.restservice.types.StorageRestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.javalin.Javalin;
import io.javalin.http.Handler;

public class StorageRestServiceJavalinJava implements StorageRestService
{

	// Environment variable names
	private static final String ENV_PORT = "eclipse_store_rest_port";
	private static final String ENV_STORAGE_NAME = "eclipse_store_rest_storage_name";

	// Default values
	private static final int DEFAULT_PORT = 4567;
	private static final String DEFAULT_STORAGE_NAME = "store-data";

	Logger logger = LoggerFactory.getLogger(StorageRestServiceJavalinJava.class);

	public static StorageRestServiceJavalinJava New(final StorageRestAdapter storageRestAdapter)
	{
		return new StorageRestServiceJavalinJava(storageRestAdapter);
	}

	private final StorageRestAdapter 	storageRestAdapter;
	private Javalin 					javalin;
	private final String                storageName;
	private final int                   port;


	public StorageRestServiceJavalinJava(StorageRestAdapter storageRestAdapter)
	{
		this.storageRestAdapter = storageRestAdapter;
		this.port = resolvePort();
		this.storageName = resolveStorageName();
	}

	@Override
	public void start()
	{
		if (this.javalin == null) {
			javalin = Javalin.create();
		}
		this.setupRoutes();
		this.javalin.start(this.port);
	}


	private void setupRoutes()
	{
		final AllRoutesHandler allRoutesHandler = new AllRoutesHandler(this.storageName);
		final Handler rootHandler = new RootHandler(storageRestAdapter);
		final Handler dictionaryHandler = new DictionaryHandler(storageRestAdapter);
		final Handler getObjectHandler = new GetObjectHandler(storageRestAdapter);
		final Handler storageFilesStatisticsHandler = new StorageFilesStatisticsHandler(storageRestAdapter);

		this.javalin.get("/" + this.storageName + "/", allRoutesHandler);
		this.javalin.get("/" + this.storageName + "/root", rootHandler);
		this.javalin.get("/" + this.storageName + "/dictionary", dictionaryHandler);
		this.javalin.get("/" + this.storageName + "/object/{oid}", getObjectHandler);
		this.javalin.get("/" + this.storageName + "/maintenance/filesStatistics", storageFilesStatisticsHandler);

		this.javalin.exception(InvalidRouteParametersException.class, (e, ctx) -> {
			ctx.status(404).result(e.getMessage());
		});

		this.javalin.exception(StorageRestAdapterException.class, (e, ctx) -> {
			ctx.status(404).result(e.getMessage());
		});
	}

	@Override
	public void stop()
	{
		if (this.javalin != null)
		{
			this.javalin.stop();
		}
	}

	private int resolvePort()
	{
		final String raw = System.getenv(ENV_PORT);
		if (raw != null) {
			try {
				final int p = Integer.parseInt(raw.trim());
				if (p >= 1 && p <= 65535) {
					return p;
				}
			} catch (NumberFormatException ignored) {
				logger.error("Invalid port number in environment variable {}: {}, use default {}", ENV_PORT, raw, DEFAULT_PORT);
			}
		}
		return DEFAULT_PORT;
	}

	private String resolveStorageName()
	{
		final String raw = System.getenv(ENV_STORAGE_NAME);
		if (raw == null || raw.trim().isEmpty()) {
			return DEFAULT_STORAGE_NAME;
		}
		return raw.trim();
	}

}
