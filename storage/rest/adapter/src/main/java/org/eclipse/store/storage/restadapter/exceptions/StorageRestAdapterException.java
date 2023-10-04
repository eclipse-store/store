
package org.eclipse.store.storage.restadapter.exceptions;

/*-
 * #%L
 * EclipseStore Storage REST Adapter
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

public class StorageRestAdapterException extends BaseException
{
	public StorageRestAdapterException(
		final String message
	)
	{
		super(message);
	}
	
	public StorageRestAdapterException()
	{
		super();
	}
	
	public StorageRestAdapterException(
		final String message,
		final Throwable cause,
		final boolean enableSuppression,
		final boolean writableStackTrace
	)
	{
		super(message, cause, enableSuppression, writableStackTrace);
	}
	
	public StorageRestAdapterException(
		final String message,
		final Throwable cause
	)
	{
		super(message, cause);
	}
	
	public StorageRestAdapterException(
		final Throwable cause
	)
	{
		super(cause);
	}
	
}
