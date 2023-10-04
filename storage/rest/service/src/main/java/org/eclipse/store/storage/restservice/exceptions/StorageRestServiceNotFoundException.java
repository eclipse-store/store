
package org.eclipse.store.storage.restservice.exceptions;

/*-
 * #%L
 * EclipseStore Storage REST Service
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

import org.eclipse.serializer.exceptions.BaseException;

/**
 * Exception thrown by RestServiceResolver.
 *
 */
public class StorageRestServiceNotFoundException extends BaseException
{
	public StorageRestServiceNotFoundException(
		final String message
	)
	{
		super(message);
	}
	
	public StorageRestServiceNotFoundException()
	{
		super();
	}
	
	public StorageRestServiceNotFoundException(
		final String message,
		final Throwable cause,
		final boolean enableSuppression,
		final boolean writableStackTrace
	)
	{
		super(message, cause, enableSuppression, writableStackTrace);
	}
	
	public StorageRestServiceNotFoundException(
		final String message,
		final Throwable cause
	)
	{
		super(message, cause);
	}
	
	public StorageRestServiceNotFoundException(
		final Throwable cause
	)
	{
		super(cause);
	}
	
}
