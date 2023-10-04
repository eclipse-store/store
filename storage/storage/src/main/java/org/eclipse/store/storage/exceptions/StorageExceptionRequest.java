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

public class StorageExceptionRequest extends StorageException
{
	///////////////////////////////////////////////////////////////////////////
	// instance fields //
	////////////////////

	private final Throwable[] problems;



	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////

	public StorageExceptionRequest(final Throwable[] problems)
	{
		super();
		this.problems = problems;
	}



	///////////////////////////////////////////////////////////////////////////
	// getters //
	////////////

	public Throwable[] problems()
	{
		return this.problems;
	}



}
