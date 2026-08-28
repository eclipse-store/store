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
import io.javalin.config.JavalinConfig;
import io.javalin.http.staticfiles.Location;
import io.javalin.plugin.bundled.CorsPluginConfig;

public class StorageRestServiceJavalinJava implements StorageRestService
{

	// Environment variable names
	private static final String ENV_PORT = "eclipse_store_rest_port";
	private static final String ENV_STORAGE_NAME = "eclipse_store_rest_storage_name";
	private static final String ENV_UI_ENABLED = "eclipse_store_rest_ui_enabled";

	// Default values
	private static final int DEFAULT_PORT = 4567;
	private static final String DEFAULT_STORAGE_NAME = "store-data";
	private static final boolean DEFAULT_UI_ENABLED = false;

	// The bundled Vanilla-JS storage viewer lives in this classpath directory (deliberately NOT
	// under META-INF/resources, so it is never auto-served without the flag being enabled).
	private static final String UI_CLASSPATH_DIR = "/eclipse-store-rest-viewer";

	Logger logger = LoggerFactory.getLogger(StorageRestServiceJavalinJava.class);

	public static StorageRestServiceJavalinJava New(final StorageRestAdapter storageRestAdapter)
	{
		return new StorageRestServiceJavalinJava(storageRestAdapter);
	}

	private final StorageRestAdapter 	storageRestAdapter;
	private Javalin 					javalin;
	private final String                storageName;
	private final int                   port;
	private final boolean               uiEnabled;


	public StorageRestServiceJavalinJava(StorageRestAdapter storageRestAdapter)
	{
		this.storageRestAdapter = storageRestAdapter;
		this.port = resolvePort();
		this.storageName = resolveStorageName();
		this.uiEnabled = resolveUiEnabled();
	}

	@Override
	public void start()
	{
		if (this.javalin == null) {
			javalin = Javalin.create(config -> {
				config.bundledPlugins.enableCors(cors -> cors.addRule(CorsPluginConfig.CorsRule::anyHost));
				if (this.uiEnabled) {
					this.setupUi(config);
				}
				this.setupRoutes(config);
			});
		}
		this.javalin.start(this.port);
		if (this.uiEnabled) {
			this.logger.info("Storage REST viewer UI enabled at http://localhost:{}/", this.port);
		}
	}

	private void setupUi(final JavalinConfig config)
	{
		// Serve the bundled Vanilla-JS viewer from the classpath. The REST routes are registered
		// explicitly and take precedence; the static files are only served for otherwise-unmatched
		// GET requests (e.g. "/", "/index.html", "/js/...", "/css/...").
		config.staticFiles.add(staticFiles ->
		{
			staticFiles.hostedPath = "/";
			staticFiles.directory  = UI_CLASSPATH_DIR;
			staticFiles.location   = Location.CLASSPATH;
		});
	}


	private void setupRoutes(final JavalinConfig config)
	{
		final String base = "/" + this.storageName;

		config.routes
			.get(base + "/",                              new AllRoutesHandler(this.storageName))
			.get(base + "/root",                          new RootHandler(storageRestAdapter))
			.get(base + "/dictionary",                    new DictionaryHandler(storageRestAdapter))
			.get(base + "/object/{oid}",                  new GetObjectHandler(storageRestAdapter))
			.get(base + "/maintenance/filesStatistics",   new StorageFilesStatisticsHandler(storageRestAdapter))
			.exception(InvalidRouteParametersException.class, (e, ctx) ->
				ctx.status(404).result(e.getMessage())
			)
			.exception(StorageRestAdapterException.class, (e, ctx) ->
				ctx.status(404).result(e.getMessage())
			);
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
		final String raw = System.getProperty(ENV_PORT);
		if (raw != null) {
			try {
				final int p = Integer.parseInt(raw.trim());
				if (p >= 1 && p <= 65535) {
					return p;
				}
			} catch (NumberFormatException ignored) {
				logger.error("Invalid port number in environment variable {}: {}, use default {}", ENV_PORT, raw, DEFAULT_PORT);
			}
		} else {
			logger.trace("No environment variable {}, use default {}", ENV_PORT, DEFAULT_PORT);
		}
		return DEFAULT_PORT;
	}

	private String resolveStorageName()
	{
		final String raw = System.getProperty(ENV_STORAGE_NAME);
		if (raw == null || raw.trim().isEmpty()) {
			return DEFAULT_STORAGE_NAME;
		} else {
			logger.trace("No storage name environment variable {}, use default {}", ENV_STORAGE_NAME, DEFAULT_STORAGE_NAME);
		}
		return raw.trim();
	}

	private boolean resolveUiEnabled()
	{
		final String raw = System.getProperty(ENV_UI_ENABLED);
		if (raw == null || raw.trim().isEmpty()) {
			return DEFAULT_UI_ENABLED;
		}
		return Boolean.parseBoolean(raw.trim());
	}

}
