package org.eclipse.store.integrations.cdi.exceptions;

/*-
 * #%L
 * EclipseStore Integrations CDI 4
 * %%
 * Copyright (C) 2023 - 2024 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */


import org.eclipse.serializer.exceptions.BaseException;

public class CDIException extends BaseException
{
	public CDIException()
	{
		super();
	}

	public CDIException(final String message, final Throwable cause, final boolean enableSuppression, final boolean writableStackTrace)
	{
		super(message, cause, enableSuppression, writableStackTrace);
	}

	public CDIException(final String message, final Throwable cause)
	{
		super(message, cause);
	}

	public CDIException(final String message)
	{
		super(message);
	}

	public CDIException(final Throwable cause)
	{
		super(cause);
	}
	
}
