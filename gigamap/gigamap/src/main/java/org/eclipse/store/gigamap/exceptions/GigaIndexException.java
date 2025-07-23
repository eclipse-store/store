package org.eclipse.store.gigamap.exceptions;

/*-
 * #%L
 * EclipseStore GigaMap
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

import org.eclipse.store.gigamap.types.GigaIndex;
import org.eclipse.serializer.exceptions.BaseException;


/**
 * Represents an exception related to operations or issues with a GigaIndex.
 * This exception is used to indicate errors associated with a specific GigaIndex instance
 * during various operations within the GigaMap ecosystem.
 */
public class GigaIndexException extends BaseException
{
	private final GigaIndex<?> index;
	
	/**
	 * Constructs a new GigaIndexException associated with a specific GigaIndex.
	 * This exception indicates an error related to the provided GigaIndex instance.
	 *
	 * @param index the GigaIndex instance associated with the exception
	 */
	public GigaIndexException(final GigaIndex<?> index)
	{
		super();
		this.index = index;
	}
	
	/**
	 * Constructs a new GigaIndexException with the specified cause and associated GigaIndex.
	 * This constructor allows associating the exception with a specific GigaIndex instance
	 * and provides a throwable cause that led to the exception.
	 *
	 * @param cause the throwable that caused the exception
	 * @param index the GigaIndex instance associated with the exception
	 */
	public GigaIndexException(final Throwable cause, final GigaIndex<?> index)
	{
		super(cause);
		this.index = index;
	}
	
	/**
	 * Constructs a new GigaIndexException with the specified detail message and associated GigaIndex.
	 * This constructor allows associating the exception with a specific GigaIndex instance
	 * and provides a descriptive error message indicating the cause of the exception.
	 *
	 * @param message the detail message explaining the cause of the exception
	 * @param index the GigaIndex instance associated with the exception
	 */
	public GigaIndexException(final String message, final GigaIndex<?> index)
	{
		super(message);
		this.index = index;
	}
	
	/**
	 * Constructs a new GigaIndexException with the specified detail message, cause,
	 * and associated GigaIndex. This constructor allows providing a descriptive
	 * error message, a throwable cause, and associating the exception with a specific
	 * GigaIndex instance.
	 *
	 * @param message the detail message explaining the cause of the exception
	 * @param cause the throwable that caused the exception
	 * @param index the GigaIndex instance associated with the exception
	 */
	public GigaIndexException(final String message, final Throwable cause, final GigaIndex<?> index)
	{
		super(message, cause);
		this.index = index;
	}
	
	/**
	 * Constructs a new GigaIndexException with the specified detail message, cause,
	 * suppression enabled/disabled, writable stack trace, and associated GigaIndex.
	 * This constructor provides detailed configuration for the exception properties
	 * while associating the exception with a specific GigaIndex instance.
	 *
	 * @param message the detail message explaining the cause of the exception
	 * @param cause the throwable that caused the exception
	 * @param enableSuppression whether suppression is enabled or disabled
	 * @param writableStackTrace whether the stack trace should be writable
	 * @param index the GigaIndex instance associated with the exception
	 */
	public GigaIndexException(final String message, final Throwable cause, final boolean enableSuppression, final boolean writableStackTrace, final GigaIndex<?> index)
	{
		super(message, cause, enableSuppression, writableStackTrace);
		this.index = index;
	}
	
	/**
	 * Retrieves the associated GigaIndex instance.
	 * This method provides access to the GigaIndex that is associated with
	 * this exception, typically indicating the source of the error.
	 *
	 * @return the associated GigaIndex instance
	 */
	public GigaIndex<?> index()
	{
		return this.index;
	}
	
}
