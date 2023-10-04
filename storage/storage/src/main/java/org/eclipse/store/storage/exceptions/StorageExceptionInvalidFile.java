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

public final class StorageExceptionInvalidFile extends StorageException
{
	private final long fileLength;

	public StorageExceptionInvalidFile(final long fileLength)
	{
		super();
		this.fileLength = fileLength;
	}

	public StorageExceptionInvalidFile(final long fileLength, final Throwable cause)
	{
		super(cause);
		this.fileLength = fileLength;
	}

	public final long fileLength()
	{
		return this.fileLength;
	}

}
