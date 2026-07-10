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
 * A transactions file ends inside an entry (torn tail), typically caused by a crash during an
 * append. {@link #position()} is the offset of the incomplete trailing entry: the content before
 * it is intact, so truncating to that position heals the file.
 */
public class StorageExceptionIncompleteTransactionsEntry extends StorageExceptionConsistency
{
	///////////////////////////////////////////////////////////////////////////
	// instance fields //
	////////////////////

	private final long position;

	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////

	public StorageExceptionIncompleteTransactionsEntry(final long position, final String message)
	{
		super(message);
		this.position = position;
	}

	///////////////////////////////////////////////////////////////////////////
	// declared methods //
	/////////////////////

	public final long position()
	{
		return this.position;
	}

}
