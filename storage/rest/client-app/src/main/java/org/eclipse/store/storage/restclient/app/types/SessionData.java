
package org.eclipse.store.storage.restclient.app.types;

/*-
 * #%L
 * EclipseStore Storage REST Client App
 * %%
 * Copyright (C) 2023 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

public class SessionData
{
	private final String baseUrl;
	
	public SessionData(
		final String baseUrl
	)
	{
		super();
		this.baseUrl = baseUrl;
	}
	
	public String baseUrl()
	{
		return this.baseUrl;
	}
}
