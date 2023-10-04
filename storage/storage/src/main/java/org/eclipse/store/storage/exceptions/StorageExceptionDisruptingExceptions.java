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

import org.eclipse.serializer.chars.VarString;
import org.eclipse.serializer.collections.types.XGettingSequence;

public class StorageExceptionDisruptingExceptions extends StorageException
{
	///////////////////////////////////////////////////////////////////////////
	// instance fields //
	////////////////////
	
	private final XGettingSequence<Throwable> disruptions;
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////

	public StorageExceptionDisruptingExceptions(
		final XGettingSequence<Throwable> disruptions
	)
	{
		super();
		this.disruptions = disruptions;
	}

	public StorageExceptionDisruptingExceptions(
		final XGettingSequence<Throwable> disruptions,
		final String                      message
	)
	{
		super(message);
		this.disruptions = disruptions;
	}

	public StorageExceptionDisruptingExceptions(
		final XGettingSequence<Throwable> disruptions,
		final Throwable                   cause
	)
	{
		super(cause);
		this.disruptions = disruptions;
	}

	public StorageExceptionDisruptingExceptions(
		final XGettingSequence<Throwable> disruptions,
		final String                      message    ,
		final Throwable                   cause
	)
	{
		super(message, cause);
		this.disruptions = disruptions;
	}

	public StorageExceptionDisruptingExceptions(
		final XGettingSequence<Throwable> disruptions       ,
		final String                      message           ,
		final Throwable                   cause             ,
		final boolean                     enableSuppression ,
		final boolean                     writableStackTrace
	)
	{
		super(message, cause, enableSuppression, writableStackTrace);
		this.disruptions = disruptions;
	}
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////
	
	public final XGettingSequence<Throwable> disruptions()
	{
		return this.disruptions;
	}
	
	@Override
	public String assembleOutputString()
	{
		final VarString vs = VarString.New("Disruptions: {");
		for(final Throwable d : this.disruptions)
		{
			vs.add(d.getClass().getName()).add(':').add(d.getMessage()).add(',').blank();
		}
		vs.deleteLast().add('}');
		
		return vs.toString();
	}
	
}
