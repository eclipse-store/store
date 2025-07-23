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

import org.eclipse.store.gigamap.types.IndexGroup;
import org.eclipse.serializer.exceptions.BaseException;


/**
 * Represents an exception occurring in the context of an {@code IndexGroup},
 * providing additional information about the group of indices associated with the error.
 * This exception can encapsulate context-specific messages, causes, and a reference
 * to the relevant {@code IndexGroup} instance.
 */
public class IndexGroupException extends BaseException
{
	private final IndexGroup<?> indexGroup;
	
	/**
	 * Constructs a new IndexGroupException associated with a specific IndexGroup.
	 * This exception indicates an error related to the provided IndexGroup instance.
	 *
	 * @param indexGroup the IndexGroup instance associated with the exception
	 */
	public IndexGroupException(final IndexGroup<?> indexGroup)
	{
		super();
		this.indexGroup = indexGroup;
	}
	
	/**
	 * Constructs a new IndexGroupException with the specified cause and associated IndexGroup.
	 * This constructor allows associating the exception with a specific IndexGroup instance
	 * and provides a throwable cause that led to the exception.
	 *
	 * @param cause the throwable that caused the exception
	 * @param indexGroup the IndexGroup instance associated with the exception
	 */
	public IndexGroupException(final Throwable cause, final IndexGroup<?> indexGroup)
	{
		super(cause);
		this.indexGroup = indexGroup;
	}
	
	/**
	 * Constructs a new IndexGroupException with the specified detail message and associated IndexGroup.
	 * This exception indicates an error related to the provided IndexGroup instance and provides
	 * a descriptive error message explaining the cause of the exception.
	 *
	 * @param message the detail message explaining the cause of the exception
	 * @param indexGroup the IndexGroup instance associated with the exception
	 */
	public IndexGroupException(final String message, final IndexGroup<?> indexGroup)
	{
		super(message);
		this.indexGroup = indexGroup;
	}
	
	/**
	 * Constructs a new IndexGroupException with the specified detail message, cause,
	 * and associated IndexGroup. This constructor allows specifying a descriptive error
	 * message, a throwable cause that led to the exception, and the relevant IndexGroup
	 * instance associated with the error.
	 *
	 * @param message the detail message explaining the cause of the exception
	 * @param cause the throwable that caused the exception
	 * @param indexGroup the IndexGroup instance associated with the exception
	 */
	public IndexGroupException(final String message, final Throwable cause, final IndexGroup<?> indexGroup)
	{
		super(message, cause);
		this.indexGroup = indexGroup;
	}
	
	/**
	 * Constructs a new IndexGroupException with the specified detail message, cause,
	 * suppression enabled or disabled, writable stack trace flag, and associated IndexGroup.
	 * This constructor provides a way to define all properties of the exception along
	 * with an IndexGroup instance associated with the error.
	 *
	 * @param message the detail message explaining the cause of the exception
	 * @param cause the throwable that caused the exception
	 * @param enableSuppression whether suppression is enabled or disabled
	 * @param writableStackTrace whether the stack trace should be writable
	 * @param indexGroup the IndexGroup instance associated with the exception
	 */
	public IndexGroupException(final String message, final Throwable cause, final boolean enableSuppression, final boolean writableStackTrace, final IndexGroup<?> indexGroup)
	{
		super(message, cause, enableSuppression, writableStackTrace);
		this.indexGroup = indexGroup;
	}
	
	/**
	 * Retrieves the associated IndexGroup instance.
	 * This method provides access to the IndexGroup that is associated with
	 * this exception, typically indicating the source of the error.
	 *
	 * @return the associated IndexGroup instance
	 */
	public IndexGroup<?> indexGroup()
	{
		return this.indexGroup;
	}
	
}
