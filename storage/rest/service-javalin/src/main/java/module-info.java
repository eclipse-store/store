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
module org.eclipse.store.storage.restservice.javalin
{
	exports org.eclipse.store.storage.restservice.javalin.exceptions;
	exports org.eclipse.store.storage.restservice.javalin.types;

	provides org.eclipse.store.storage.restadapter.types.StorageViewDataConverter
			with org.eclipse.store.storage.restservice.javalin.types.StorageViewDataConverterJson
			;
	provides org.eclipse.store.storage.restservice.types.StorageRestServiceProvider
			with org.eclipse.store.storage.restservice.javalin.types.StorageRestServiceJavalinProvider
			;

	requires transitive org.eclipse.store.storage.restservice;
	requires transitive com.google.gson;
	requires transitive io.javalin;
}
