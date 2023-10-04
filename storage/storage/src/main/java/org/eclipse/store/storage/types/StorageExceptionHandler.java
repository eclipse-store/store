package org.eclipse.store.storage.types;

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

import org.eclipse.serializer.util.logging.Logging;
import org.eclipse.store.storage.exceptions.StorageException;
import org.slf4j.Logger;

@FunctionalInterface
public interface StorageExceptionHandler
{
	public void handleException(Throwable exception, StorageChannel channel);
	
	
	
	public static void defaultHandleException(final Throwable exception, final StorageChannel channel)
	{
		// logic encapsulated in static method to be reusable by other implementors.
		if(exception instanceof StorageException)
		{
			throw (StorageException)exception;
		}
		throw new StorageException(exception);
	}
	
	
	
	public static StorageExceptionHandler New()
	{
		return new StorageExceptionHandler.Default();
	}
	
	public final class Default implements StorageExceptionHandler
	{
		private final static Logger logger = Logging.getLogger(StorageExceptionHandler.class);
		
		Default()
		{
			super();
		}
		
		@Override
		public void handleException(final Throwable exception, final StorageChannel channel)
		{
			logger.error("Exception occurred in storage channel#{}", channel.channelIndex(), exception);
			
			StorageExceptionHandler.defaultHandleException(exception, channel);
		}
		
	}
	
}
