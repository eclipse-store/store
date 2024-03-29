package org.eclipse.store.storage.restclient.exceptions;

/*-
 * #%L
 * EclipseStore Storage REST Client
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

public class StorageViewException extends BaseException
{
	public StorageViewException()
	{
		super();
	}

	public StorageViewException(
		final String message,
		final Throwable cause,
		final boolean enableSuppression,
		final boolean writableStackTrace
	)
	{
		super(message, cause, enableSuppression, writableStackTrace);
	}

	public StorageViewException(
		final String message,
		final Throwable cause
	)
	{
		super(message, cause);
	}

	public StorageViewException(
		final String message
	)
	{
		super(message);
	}

	public StorageViewException(
		final Throwable cause
	)
	{
		super(cause);
	}
	
}
