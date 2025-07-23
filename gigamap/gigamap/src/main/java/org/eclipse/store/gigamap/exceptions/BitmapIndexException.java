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

import org.eclipse.store.gigamap.types.BitmapIndex;


/**
 * Represents an exception specifically related to operations on a BitmapIndex.
 * This exception extends the functionality of {@code GigaIndexException} and provides
 * access to the associated {@code BitmapIndex} instance that caused the error.
 */
public class BitmapIndexException extends GigaIndexException
{
	/**
	 * Constructs a new BitmapIndexException with the specified BitmapIndex.
	 * This constructor allows associating the exception with a specific BitmapIndex
	 * instance that caused the error.
	 *
	 * @param index the {@code BitmapIndex} associated with the exception
	 */
	public BitmapIndexException(final BitmapIndex<?, ?> index)
	{
		super(index);
	}
	
	/**
	 * Constructs a new BitmapIndexException with the specified cause and BitmapIndex.
	 * This constructor allows associating the exception with a specific BitmapIndex
	 * instance and a throwable cause that led to the error.
	 *
	 * @param cause the throwable that caused the exception
	 * @param index the BitmapIndex associated with the exception
	 */
	public BitmapIndexException(final Throwable cause, final BitmapIndex<?, ?> index)
	{
		super(cause, index);
	}
	
	/**
	 * Constructs a new BitmapIndexException with the specified detail message and associated BitmapIndex.
	 * This constructor provides information about the error using a descriptive message and the specific
	 * BitmapIndex instance that caused the exception.
	 *
	 * @param message the detail message explaining the cause of the exception
	 * @param index the BitmapIndex associated with the exception
	 */
	public BitmapIndexException(final String message, final BitmapIndex<?, ?> index)
	{
		super(message, index);
	}
	
	/**
	 * Constructs a new BitmapIndexException with the specified detail message, cause,
	 * and associated BitmapIndex. This constructor provides a descriptive error message,
	 * the root cause of the exception, and the specific BitmapIndex instance tied to the error.
	 *
	 * @param message the detail message explaining the cause of the exception
	 * @param cause the throwable that caused the exception
	 * @param index the BitmapIndex associated with the exception
	 */
	public BitmapIndexException(final String message, final Throwable cause, final BitmapIndex<?, ?> index)
	{
		super(message, cause, index);
	}
	
	/**
	 * Constructs a new BitmapIndexException with the specified detail message, cause,
	 * indicator for suppression, writable stack trace, and associated BitmapIndex.
	 * This constructor allows providing a detailed error message, a throwable cause,
	 * and configuration of exception properties while associating it with a specific BitmapIndex.
	 *
	 * @param message the detail message explaining the cause of the exception
	 * @param cause the throwable that caused the exception
	 * @param enableSuppression whether suppression is enabled or disabled
	 * @param writableStackTrace whether the stack trace should be writable
	 * @param index the BitmapIndex associated with the exception
	 */
	public BitmapIndexException(final String message, final Throwable cause, final boolean enableSuppression, final boolean writableStackTrace, final BitmapIndex<?, ?> index)
	{
		super(message, cause, enableSuppression, writableStackTrace, index);
	}
	
	/**
	 * Retrieves the associated {@code BitmapIndex} instance.
	 * This method overrides the {@code index} method in the superclass.
	 * The cast functionality is ensured by the constructors of this class.
	 *
	 * @return the associated {@code BitmapIndex} instance
	 */
	@Override
	public BitmapIndex<?, ?> index()
	{
		// cast safety ensured by constructors
		return (BitmapIndex<?, ?>)super.index();
	}
	
}
