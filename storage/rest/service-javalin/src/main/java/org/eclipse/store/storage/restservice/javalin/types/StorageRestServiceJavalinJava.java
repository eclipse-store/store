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

import org.eclipse.store.storage.restadapter.exceptions.StorageRestAdapterException;
import org.eclipse.store.storage.restadapter.types.StorageRestAdapter;
import org.eclipse.store.storage.restservice.javalin.exceptions.InvalidRouteParametersException;
import org.eclipse.store.storage.restservice.types.StorageRestService;

import io.javalin.Javalin;
import io.javalin.http.Handler;

public class StorageRestServiceJavalinJava implements StorageRestService
{

	public static StorageRestServiceJavalinJava New(final StorageRestAdapter storageRestAdapter)
	{
		return new StorageRestServiceJavalinJava(storageRestAdapter);
	}

	private final StorageRestAdapter 	storageRestAdapter;
	private Javalin 					javalin;
	private final String                storageName = "store-data";


	public StorageRestServiceJavalinJava(StorageRestAdapter storageRestAdapter)
	{
		this.storageRestAdapter = storageRestAdapter;
	}

	@Override
	public void start()
	{
		if (this.javalin == null) {
			javalin = Javalin.create();
		}
		this.setupRoutes();
		this.javalin.start(4567);


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
}
