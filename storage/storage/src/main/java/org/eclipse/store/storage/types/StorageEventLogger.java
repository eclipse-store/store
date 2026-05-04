package org.eclipse.store.storage.types;

/*-
 * #%L
 * EclipseStore Storage
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

import static org.eclipse.serializer.util.X.notNull;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.function.Consumer;

/**
 * Pluggable target for storage lifecycle and housekeeping events.
 * <p>
 * The storage layer invokes the {@code log~} methods at well-defined points (channel
 * shutdown, garbage-collector phase boundaries, disruptions, etc.); each hook has an empty default
 * implementation, so an implementation only needs to override the events it cares about. The
 * built-in implementations cover the common cases:
 * <ul>
 *   <li>{@link #NoOp()} — discards every event.</li>
 *   <li>{@link #Default()} / {@link #Default(Consumer)} — logs disruptions only.</li>
 *   <li>{@link #Debug()} / {@link #Debug(Consumer)} — logs every event for diagnostic use.</li>
 *   <li>{@link #Chain(StorageEventLogger, StorageEventLogger)} — fans events out to two loggers.</li>
 * </ul>
 *
 * @see StorageEventLogger.Default
 * @see StorageEventLogger.Debug
 */
public interface StorageEventLogger
{
	/**
	 * Called when a channel transitions into the disabled state and stops accepting new tasks.
	 *
	 * @param channel the affected {@link StorageChannel}.
	 */
	public default void logChannelProcessingDisabled(final StorageChannel channel)
	{
		// no-op by default
	}

	/**
	 * Called when a channel's worker thread finishes its run loop and stops working.
	 *
	 * @param channel the affected {@link StorageChannel}.
	 */
	public default void logChannelStoppedWorking(final StorageChannel channel)
	{
		// no-op by default
	}

	/**
	 * Note that not all Throwables are Exceptions. There are also Errors.
	 * And not all exceptions are problems. There are also program execution control vehicles like
	 * {@link InterruptedException}. The actually fitting common term is "Disruption".
	 * Throwable is a very low-level technical, compiler-oriented expression.
	 *
	 * @param channel the affected channel
	 * @param t the reason for the disruption
	 */
	public default void logDisruption(final StorageChannel channel, final Throwable t)
	{
		// no-op by default
	}

	/**
	 * Called when an entity cache has finished an incremental live-check pass.
	 *
	 * @param entityCache the entity cache that completed the pass.
	 */
	public default void logLiveCheckComplete(final StorageEntityCache<?> entityCache)
	{
		// no-op by default
	}

	/**
	 * Called when the garbage collector finishes a sweep on the passed entity cache.
	 *
	 * @param entityCache the entity cache whose sweep just completed.
	 */
	public default void logGarbageCollectorSweepingComplete(final StorageEntityCache<?> entityCache)
	{
		// no-op by default
	}

	/**
	 * Called when the garbage collector determines that no further work is currently required.
	 */
	public default void logGarbageCollectorNotNeeded()
	{
		// no-op by default
	}

	/**
	 * Called when the garbage collector has completed a hot-phase generation.
	 *
	 * @param gcHotGeneration     the just-completed hot-phase generation number.
	 * @param lastGcHotCompletion the timestamp at which the hot-phase generation was completed.
	 */
	public default void logGarbageCollectorCompletedHotPhase(final long gcHotGeneration, final long lastGcHotCompletion)
	{
		// no-op by default
	}

	/**
	 * Called when the garbage collector has completed a full (cold-phase) generation.
	 *
	 * @param gcColdGeneration     the just-completed cold-phase generation number.
	 * @param lastGcColdCompletion the timestamp at which the cold-phase generation was completed.
	 */
	public default void logGarbageCollectorCompleted(final long gcColdGeneration, final long lastGcColdCompletion)
	{
		// no-op by default
	}

	/**
	 * Called when the garbage collector encounters a reference to an object id that no longer
	 * resolves to a live entity ("zombie" reference).
	 *
	 * @param objectId the unresolvable object id.
	 */
	public default void logGarbageCollectorEncounteredZombieObjectId(final long objectId)
	{
		// no-op by default
	}
	
	
	/**
	 * Creates a NoOp StorageEventLogger that does really nothing.
	 * 
	 * @return a StorageEventLogger.NoOp instance
	 */
	public static StorageEventLogger NoOp()
	{
		return new StorageEventLogger.NoOp();
	}
	
	/**
	 * NoOp StorageEventLogger
	 * <p>
	 * Doesn't log any storage events
	 *
	 */
	public final class NoOp implements StorageEventLogger
	{
		NoOp()
		{
			super();
		}
	}
	
	
	/**
	 * Creates a Default StorageEventLogger thats prints to the console.
	 * 
	 * @return a StorageEventLogger.Default instance
	 */
	public static StorageEventLogger Default()
	{
		return new StorageEventLogger.Default(Default::printString);
	}
	
	/**
	 * Creates a Default StorageEventLogger that forwards its output to the supplied Consumer
	 * 
	 * @param messageConsumer a Consumer that processes the forwarded log messages
	 * @return a StorageEventLogger.Default instance
	 */
	public static StorageEventLogger Default(final Consumer<? super String> messageConsumer)
	{
		return new StorageEventLogger.Default(
			notNull(messageConsumer)
		);
	}
	
	/**
	 * Default implementation of StorageEventLogger
	 * <p>
	 * This implementation doesn't log behavior but logs exceptions
	 *
	 */
	public class Default implements StorageEventLogger
	{
		///////////////////////////////////////////////////////////////////////////
		// static methods //
		///////////////////
		
		public static void printString(final String s)
		{
			System.out.println(s);
		}
		
		public static String toChannelIdentifier(final StorageChannel channel)
		{
			return toChannelPartIdentifier(channel);
		}
		
		public static String toChannelIdentifier(final StorageEntityCache<?> entityCache)
		{
			return toChannelPartIdentifier(entityCache);
		}
		
		public static String toChannelPartIdentifier(final StorageHashChannelPart channelPart)
		{
			return StorageChannel.class.getSimpleName()+ '#' + channelPart.channelIndex();
		}
		
		public static String stackTraceToString(final Throwable t)
		{
			final StringWriter stringWriter = new StringWriter();
			final PrintWriter printWriter = new PrintWriter(stringWriter);
			t.printStackTrace(printWriter);
			
			return printWriter.toString();
		}
		
		
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////
		
		protected final Consumer<? super String> messageConsumer;
		
			
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////
		
		Default(final Consumer<? super String> messageConsumer)
		{
			super();
			this.messageConsumer = notNull(messageConsumer);
		}
		
				
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////
		
		public void log(final String s)
		{
			this.messageConsumer.accept(s);
		}
		
		@Override
		public void logDisruption(final StorageChannel channel, final Throwable t)
		{
			this.log(toChannelIdentifier(channel) + " encountered disrupting exception " + t);
			t.printStackTrace();
		}
	}
	
	
	/**
	 * Creates a Debug StorageEventLogger that prints to the console.
	 * 
	 * @return a StorageEventLogger.Debug instance
	 */
	public static StorageEventLogger Debug()
	{
		return new StorageEventLogger.Debug(Debug.Default::printString);
	}
	
	/**
	 * Creates a Debug StorageEventLogger forwards its output to the supplied Consumer
	 * 
	 * @param messageConsumer a Consumer that processes the forwarded log messages
	 * @return a StorageEventLogger.Debug instance
	 */
	public static StorageEventLogger Debug(final Consumer<? super String> messageConsumer)
	{
		return new StorageEventLogger.Debug(
			notNull(messageConsumer)
		);
	}
	
	/**
	 * Debug implementation of StorageEventLogger
	 * <p>
	 * This implementation logs behavior and exceptions
	 *
	 */
	public class Debug extends Default
	{
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////
		
		Debug(final Consumer<? super String> messageConsumer)
		{
			super(messageConsumer);
		}
		
				
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////
			
		@Override
		public void logChannelProcessingDisabled(final StorageChannel channel)
		{
			this.log(toChannelIdentifier(channel) + " processing disabled.");
		}
		
		@Override
		public void logChannelStoppedWorking(final StorageChannel channel)
		{
			this.log(toChannelIdentifier(channel) + " stopped working.");
		}
				
		@Override
		public void logLiveCheckComplete(final StorageEntityCache<?> entityCache)
		{
			this.log(toChannelIdentifier(entityCache) + " completed live check.");
		}
		
		@Override
		public void logGarbageCollectorSweepingComplete(final StorageEntityCache<?> entityCache)
		{
			this.log(toChannelIdentifier(entityCache) + " completed sweeping.");
		}
		
		@Override
		public void logGarbageCollectorEncounteredZombieObjectId(final long objectId)
		{
			this.log("GC marking encountered zombie ObjectId " + objectId);
		}
		
		@Override
		public void logGarbageCollectorNotNeeded()
		{
			this.log("not needed.");
		}
		
		@Override
		public void logGarbageCollectorCompletedHotPhase(final long gcHotGeneration, final long lastGcHotCompletion)
		{
			this.log("Completed GC Hot Phase #" + gcHotGeneration + " @ " + lastGcHotCompletion);
		}
		
		@Override
		public void logGarbageCollectorCompleted(final long gcColdGeneration, final long lastGcColdCompletion)
		{
			this.log("Storage-GC completed #" + gcColdGeneration + " @ " + lastGcColdCompletion);
		}
		
	}
	
	
	/**
	 * Pseudo-constructor method that creates a {@link StorageEventLogger} fanning every event out to
	 * both of the passed loggers, in the given order.
	 *
	 * @param first  the logger invoked first for every event; must be non-{@code null}.
	 * @param second the logger invoked second for every event; must be non-{@code null}.
	 *
	 * @return a {@link StorageEventLogger} that forwards each event to {@code first} and then to
	 *         {@code second}.
	 */
	public static StorageEventLogger Chain(
		final StorageEventLogger first ,
		final StorageEventLogger second
	)
	{
		return new StorageEventLogger.Chaining(
			notNull(first ),
			notNull(second)
		);
	}


	/**
	 * {@link StorageEventLogger} implementation that forwards every event to two delegate loggers
	 * in fixed order. Returned by {@link StorageEventLogger#Chain(StorageEventLogger, StorageEventLogger)}.
	 */
	public final class Chaining implements StorageEventLogger
	{
		private final StorageEventLogger first ;
		private final StorageEventLogger second;
		
		Chaining(
			final StorageEventLogger first ,
			final StorageEventLogger second
		)
		{
			super();
			this.first  = first ;
			this.second = second;
		}
		
		@Override
		public void logChannelProcessingDisabled(final StorageChannel channel)
		{
			this.first.logChannelProcessingDisabled(channel);
			this.second.logChannelProcessingDisabled(channel);
		}
		
		@Override
		public void logChannelStoppedWorking(final StorageChannel channel)
		{
			this.first.logChannelStoppedWorking(channel);
			this.second.logChannelStoppedWorking(channel);
		}
		
		@Override
		public void logDisruption(final StorageChannel channel, final Throwable t)
		{
			this.first.logDisruption(channel, t);
			this.second.logDisruption(channel, t);
		}
		
		@Override
		public void logLiveCheckComplete(final StorageEntityCache<?> entityCache)
		{
			this.first.logLiveCheckComplete(entityCache);
			this.second.logLiveCheckComplete(entityCache);
		}
		
		@Override
		public void logGarbageCollectorSweepingComplete(final StorageEntityCache<?> entityCache)
		{
			this.first.logGarbageCollectorSweepingComplete(entityCache);
			this.second.logGarbageCollectorSweepingComplete(entityCache);
		}
		
		@Override
		public void logGarbageCollectorNotNeeded()
		{
			this.first.logGarbageCollectorNotNeeded();
			this.second.logGarbageCollectorNotNeeded();
		}
		
		@Override
		public void logGarbageCollectorCompletedHotPhase(final long gcHotGeneration, final long lastGcHotCompletion)
		{
			this.first.logGarbageCollectorCompletedHotPhase(gcHotGeneration, lastGcHotCompletion);
			this.second.logGarbageCollectorCompletedHotPhase(gcHotGeneration, lastGcHotCompletion);
		}
		
		@Override
		public void logGarbageCollectorCompleted(final long gcColdGeneration, final long lastGcColdCompletion)
		{
			this.first.logGarbageCollectorCompleted(gcColdGeneration, lastGcColdCompletion);
			this.second.logGarbageCollectorCompleted(gcColdGeneration, lastGcColdCompletion);
		}

		@Override
		public void logGarbageCollectorEncounteredZombieObjectId(final long objectId)
		{
			this.first.logGarbageCollectorEncounteredZombieObjectId(objectId);
			this.second.logGarbageCollectorEncounteredZombieObjectId(objectId);
		}
		
	}
	
}
