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
// Java

import org.eclipse.store.storage.restadapter.types.StorageViewDataConverter;
import org.eclipse.store.storage.restadapter.types.StorageViewDataConverterProvider;
import org.eclipse.store.storage.restservice.javalin.exceptions.InvalidRouteParametersException;

import io.javalin.http.Context;

public abstract class JavalinRouteBaseConvertable<T extends StorageViewDataConverterProvider> extends JavalinRouteBase<T> {
	protected JavalinRouteBaseConvertable(final T apiAdapter) {
		super(apiAdapter);
	}

	protected String toRequestedFormat(final Object value, final String requestedFormat, final Context ctx) {
		if (requestedFormat != null) {
			final StorageViewDataConverter converter = this.apiAdapter.getConverter(requestedFormat);
			if (converter != null) {
				final String ct = converter.getHtmlResponseContentType();
				if (ct != null) {
					ctx.contentType(ct);
				}
				return converter.convert(value);
			}
			throw new InvalidRouteParametersException("format");
		}

		ctx.contentType("application/json");
		return this.apiAdapter.getConverter("application/json").convert(value);
	}
}
