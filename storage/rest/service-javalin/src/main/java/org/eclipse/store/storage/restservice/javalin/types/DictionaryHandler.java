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

import org.eclipse.store.storage.restadapter.types.StorageRestAdapter;

import io.javalin.http.Context;
import io.javalin.http.Handler;

public class DictionaryHandler implements Handler
{
	private final StorageRestAdapter storageRestAdapter;

	public DictionaryHandler(final StorageRestAdapter storageRestAdapter) {
		this.storageRestAdapter = Objects.requireNonNull(storageRestAdapter, "storageRestAdapter");
	}

	@Override
	public void handle(final Context ctx) {
		final String dictionary = this.storageRestAdapter.getTypeDictionary();
		ctx.contentType("text/plain").result(dictionary);
	}
}
