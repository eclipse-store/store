package org.eclipse.store.storage.exceptions;

/*-
 * #%L
 * EclipseStore Storage
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

/**
 * A transaction-log compaction failed and could not be reconciled: the live transactions file is
 * in an undefined state and the retained swap file is its only heal source. The channel must not
 * process further writes - any appended entry would be silently discarded by the swap restore on
 * the next initialization.
 */
public class StorageExceptionTransactionsFileCompaction extends StorageException
{
	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////

	public StorageExceptionTransactionsFileCompaction()
	{
		super();
	}

	public StorageExceptionTransactionsFileCompaction(final String message)
	{
		super(message);
	}

	public StorageExceptionTransactionsFileCompaction(final Throwable cause)
	{
		super(cause);
	}

	public StorageExceptionTransactionsFileCompaction(final String message, final Throwable cause)
	{
		super(message, cause);
	}

	public StorageExceptionTransactionsFileCompaction(
		final String    message           ,
		final Throwable cause             ,
		final boolean   enableSuppression ,
		final boolean   writableStackTrace
	)
	{
		super(message, cause, enableSuppression, writableStackTrace);
	}

}
