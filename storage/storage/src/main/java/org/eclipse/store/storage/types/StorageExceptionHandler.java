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

/**
 * Pluggable last-resort handler invoked when a storage worker thread encounters an unrecoverable
 * {@link Throwable} while processing tasks or housekeeping.
 * <p>
 * The handler is the single point at which custom recovery, alerting, or controlled shutdown logic
 * can be hooked in: by the time {@link #handleException(Throwable, StorageChannel)} is invoked, the
 * affected channel is about to stop working. Implementations are expected to either rethrow (which
 * tears the channel down) or to perform side-effects (logging, alerting, escalation) and rethrow,
 * since silently swallowing the exception will leave the channel in an inconsistent state.
 *
 * @see StorageException
 * @see StorageChannel
 */
@FunctionalInterface
public interface StorageExceptionHandler
{
	/**
	 * Handles an unrecoverable {@link Throwable} encountered while running the passed channel.
	 * <p>
	 * Implementations should treat this as a final notification before the channel disposes its
	 * resources and stops; rethrowing is the correct default behavior.
	 *
	 * @param exception the encountered throwable.
	 * @param channel   the {@link StorageChannel} on which the throwable was raised.
	 */
	public void handleException(Throwable exception, StorageChannel channel);



	/**
	 * Reusable default behavior: rethrows the passed throwable as a {@link StorageException},
	 * wrapping it if it is not already one.
	 * <p>
	 * Custom {@link StorageExceptionHandler} implementations can call this from their own
	 * {@link #handleException(Throwable, StorageChannel)} after performing additional reporting.
	 *
	 * @param exception the throwable to rethrow.
	 * @param channel   the {@link StorageChannel} on which the throwable was raised; currently
	 *                  unused, but accepted for symmetry with {@link #handleException}.
	 *
	 * @throws StorageException always, wrapping {@code exception} if it is not already one.
	 */
	public static void defaultHandleException(final Throwable exception, final StorageChannel channel)
	{
		// logic encapsulated in static method to be reusable by other implementors.
		if(exception instanceof StorageException)
		{
			throw (StorageException)exception;
		}
		throw new StorageException(exception);
	}



	/**
	 * Pseudo-constructor method to create a new {@link StorageExceptionHandler} with the framework
	 * default behavior: log the exception with channel context, then rethrow via
	 * {@link #defaultHandleException(Throwable, StorageChannel)}.
	 *
	 * @return a new default {@link StorageExceptionHandler}.
	 */
	public static StorageExceptionHandler New()
	{
		return new StorageExceptionHandler.Default();
	}

	/**
	 * Default {@link StorageExceptionHandler} implementation: emits an error-level log entry that
	 * includes the channel index, then delegates to
	 * {@link StorageExceptionHandler#defaultHandleException(Throwable, StorageChannel)} to rethrow
	 * the exception as a {@link StorageException}.
	 */
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
