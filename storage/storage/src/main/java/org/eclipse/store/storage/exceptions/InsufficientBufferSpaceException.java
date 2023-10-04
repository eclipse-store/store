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

public final class InsufficientBufferSpaceException extends Exception // intentionally checked exception
{
	///////////////////////////////////////////////////////////////////////////
	// instance fields //
	////////////////////

	private final long requiredBufferSpace;



	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////

	public InsufficientBufferSpaceException(final long requiredBufferSpace)
	{
		super();
		this.requiredBufferSpace = requiredBufferSpace;
	}



	///////////////////////////////////////////////////////////////////////////
	// getters //
	////////////

	public long requiredBufferSpace()
	{
		return this.requiredBufferSpace;
	}



}
