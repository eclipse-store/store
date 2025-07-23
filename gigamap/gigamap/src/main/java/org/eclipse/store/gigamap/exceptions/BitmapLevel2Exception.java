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

import org.eclipse.serializer.exceptions.BaseException;


/**
 * Represents an exception specifically related to level 2 bitmap operations.
 * This exception extends the {@code BaseException}.
 */
public class BitmapLevel2Exception extends BaseException
{
	/**
	 * Constructs a new {@code BitmapLevel2Exception} with no detail message or cause.
	 * This constructor initializes the exception to represent a generic error related
	 * to level 2 bitmap operations.
	 */
	public BitmapLevel2Exception()
	{
		super();
	}
	
	/**
	 * Constructs a new {@code BitmapLevel2Exception} with the specified cause.
	 * This constructor is used to create an instance of this exception with a throwable
	 * that caused the error, allowing exception chaining for better error analysis.
	 *
	 * @param cause the throwable that caused this exception
	 */
	public BitmapLevel2Exception(final Throwable cause)
	{
		super(cause);
	}
	
	/**
	 * Constructs a new {@code BitmapLevel2Exception} with the specified detail message.
	 * This constructor provides information about the error using a descriptive message.
	 *
	 * @param message the detail message explaining the cause of the exception
	 */
	public BitmapLevel2Exception(final String message)
	{
		super(message);
	}
	
	/**
	 * Constructs a new BitmapLevel2Exception with the specified detail message and cause.
	 * This constructor allows providing a descriptive error message and the throwable root cause,
	 * offering detailed context about the exception occurrence.
	 *
	 * @param message the detail message explaining the cause of the exception
	 * @param cause the throwable that caused the exception
	 */
	public BitmapLevel2Exception(final String message, final Throwable cause)
	{
		super(message, cause);
	}
	
	/**
	 * Constructs a new BitmapLevel2Exception with the specified detail message,
	 * cause, suppression enabled or disabled, and writable stack trace enabled or disabled.
	 * This constructor provides detailed error information and allows configuring
	 * exception behavior by enabling or disabling suppression and stack trace writability.
	 *
	 * @param message the detail message explaining the cause of the exception
	 * @param cause the throwable that caused the exception
	 * @param enableSuppression whether suppression is enabled or disabled
	 * @param writableStackTrace whether the stack trace should be writable
	 */
	public BitmapLevel2Exception(final String message, final Throwable cause, final boolean enableSuppression, final boolean writableStackTrace)
	{
		super(message, cause, enableSuppression, writableStackTrace);
	}
	
}
