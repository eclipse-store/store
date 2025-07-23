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

import org.eclipse.store.gigamap.types.BitmapIndices;


/**
 * Represents an exception that occurs specifically for {@code BitmapIndices} operations.
 * This exception extends {@code IndexGroupException} to provide additional context
 * for exceptions related to {@code BitmapIndices}.
 */
public class BitmapIndicesException extends IndexGroupException
{
	/**
	 * Constructs a new {@code BitmapIndicesException} with the specified {@code BitmapIndices} instance.
	 *
	 * @param indexGroup the {@code BitmapIndices} instance associated with the exception,
	 *                   providing context for the error.
	 */
	public BitmapIndicesException(final BitmapIndices<?> indexGroup)
	{
		super(indexGroup);
	}
	
	/**
	 * Constructs a new {@code BitmapIndicesException} with the specified cause
	 * and {@code BitmapIndices} instance.
	 *
	 * @param cause the throwable that caused this exception, providing the underlying reason for the error.
	 * @param indexGroup the {@code BitmapIndices} instance associated with this exception,
	 *                   providing context for the error.
	 */
	public BitmapIndicesException(final Throwable cause, final BitmapIndices<?> indexGroup)
	{
		super(cause, indexGroup);
	}
	
	/**
	 * Constructs a new {@code BitmapIndicesException} with the specified detail message
	 * and {@code BitmapIndices} instance.
	 *
	 * @param message the detail message providing additional context about the exception.
	 * @param indexGroup the {@code BitmapIndices} instance associated with this exception,
	 *                   providing context for the error.
	 */
	public BitmapIndicesException(final String message, final BitmapIndices<?> indexGroup)
	{
		super(message, indexGroup);
	}
	
	/**
	 * Constructs a new {@code BitmapIndicesException} with the specified detail message,
	 * cause, and {@code BitmapIndices} instance.
	 *
	 * @param message the detail message providing additional information about the exception.
	 * @param cause the throwable that caused this exception, providing the underlying reason.
	 * @param indexGroup the {@code BitmapIndices} instance associated with this exception,
	 *                   providing context for the error.
	 */
	public BitmapIndicesException(final String message, final Throwable cause, final BitmapIndices<?> indexGroup)
	{
		super(message, cause, indexGroup);
	}
	
	/**
	 * Constructs a new {@code BitmapIndicesException} with the specified detail message,
	 * cause, suppression enabled or disabled, writable stack trace enabled or disabled,
	 * and {@code BitmapIndices} instance.
	 *
	 * @param message the detail message providing additional context about the exception.
	 * @param cause the throwable that caused this exception, providing the underlying reason.
	 * @param enableSuppression whether or not suppression is enabled or disabled.
	 * @param writableStackTrace whether or not the stack trace should be writable.
	 * @param indexGroup the {@code BitmapIndices} instance associated with this exception,
	 *                   providing context for the error.
	 */
	public BitmapIndicesException(final String message, final Throwable cause, final boolean enableSuppression, final boolean writableStackTrace, final BitmapIndices<?> indexGroup)
	{
		super(message, cause, enableSuppression, writableStackTrace, indexGroup);
	}
	
	/**
	 * Retrieves the {@code BitmapIndices} instance associated with this exception,
	 * providing context for the error that occurred.
	 *
	 * @return the {@code BitmapIndices} instance representing the group of indices associated
	 *         with this exception, cast from the superclass method's return value.
	 */
	@Override
	public BitmapIndices<?> indexGroup()
	{
		// cast safety ensured by constructors
		return (BitmapIndices<?>)super.indexGroup();
	}
	
}
