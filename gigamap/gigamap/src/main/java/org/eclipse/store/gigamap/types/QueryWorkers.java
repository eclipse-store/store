package org.eclipse.store.gigamap.types;

/*-
 * #%L
 * EclipseStore GigaMap
 * %%
 * Copyright (C) 2023 - 2026 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import org.eclipse.serializer.collections.BulkList;


/**
 * The gate for the logics a threaded query hands to an {@link IterationThreadProvider}. The logics read the map's
 * bitmap segments on threads the query does not control: they may start late, outlive the query's reader, or
 * never run at all.
 * <p>
 * A logic passes the gate only while it is open, which the query guarantees to be only while it holds the map
 * read-only. Once {@link #close() closed}, no further logic starts, and the map waits with mutations until the
 * running ones are done. Nobody has to wait for the threads otherwise.
 * <p>
 * Passing and leaving the gate only takes this instance's monitor, never the map's.
 */
final class QueryWorkers
{
	///////////////////////////////////////////////////////////////////////////
	// instance fields //
	////////////////////

	// guarded by this
	private final BulkList<Thread> running = BulkList.New();
	private       int              guarded;
	private       int              started;

	private volatile boolean   closed ;
	private volatile Throwable failure;



	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////

	/**
	 * Wraps a logic to be handed to a thread provider: it runs at most once and only while this gate is open,
	 * and its failure is kept for {@link #throwFailure()} instead of ending the (possibly pooled) thread.
	 */
	final synchronized Runnable guard(final Runnable logic)
	{
		this.guarded++;

		return new Guarded(logic);
	}

	private synchronized boolean start(final Guarded logic)
	{
		if(this.closed || logic.started)
		{
			return false;
		}
		logic.started = true;
		this.started++;
		this.running.add(Thread.currentThread());

		return true;
	}

	private synchronized void completed(final Throwable failure)
	{
		if(failure != null && this.failure == null)
		{
			this.failure = failure;
		}
		this.running.removeOne(Thread.currentThread());
		this.notifyAll();
	}

	/**
	 * Keeps logics that did not start yet from ever reading anything.
	 *
	 * @return whether logics are still running.
	 */
	final synchronized boolean close()
	{
		this.closed = true;

		return !this.running.isEmpty();
	}

	/**
	 * Whether running logics should stop at their next unit of work: nobody reads their results anymore.
	 */
	final boolean isClosed()
	{
		return this.closed;
	}

	/**
	 * Whether no logic of this gate will ever read again: it is closed and nothing is running.
	 */
	final synchronized boolean isDone()
	{
		return this.closed && this.running.isEmpty();
	}

	final synchronized boolean isRunningOn(final Thread thread)
	{
		return this.running.contains(thread);
	}

	/**
	 * Whether exactly {@code logicCount} logics have been guarded and every one of them has been started.
	 * {@code false} after the provider is done means it did not ask for or did not run all the logics the query
	 * needs.
	 */
	final synchronized boolean isComplete(final int logicCount)
	{
		return this.guarded == logicCount && this.started == logicCount;
	}

	/**
	 * Waits until no logic is running. Logics that were not started yet are not waited for.
	 */
	final synchronized void awaitRunning() throws InterruptedException
	{
		while(!this.running.isEmpty())
		{
			this.wait();
		}
	}

	final void throwFailure()
	{
		final Throwable failure = this.failure;
		if(failure instanceof Error)
		{
			throw (Error)failure;
		}
		if(failure != null)
		{
			// a Runnable cannot throw anything else
			throw (RuntimeException)failure;
		}
	}



	private final class Guarded implements Runnable
	{
		private final Runnable logic  ;
		private       boolean  started; // guarded by the outer instance

		Guarded(final Runnable logic)
		{
			super();
			this.logic = logic;
		}

		@Override
		public void run()
		{
			if(!QueryWorkers.this.start(this))
			{
				return;
			}

			Throwable failure = null;
			try
			{
				this.logic.run();
			}
			catch(final Throwable t)
			{
				failure = t;
			}
			finally
			{
				QueryWorkers.this.completed(failure);
			}
		}

	}

}
