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

import org.eclipse.serializer.chars.XChars;
import org.eclipse.serializer.collections.BulkList;


/**
 * Everything that keeps a {@link GigaMap} from being structurally modified at the moment: open readers,
 * iterations and query executions in progress, workers of threaded queries that are still reading and the
 * explicit read-only mode.
 * <p>
 * This is runtime state of the map instance, not part of its loaded state: it is never reset.
 * <p>
 * Not thread-safe: every method must be called while holding the map's monitor.
 */
final class ReadHolds
{
	///////////////////////////////////////////////////////////////////////////
	// instance fields //
	////////////////////

	private final GigaMap<?> map;

	// readers the application has to close (iterators, id matchers)
	private final BulkList<GigaMap.Reading> readers = BulkList.New();

	// one entry per iterate() / execute() call in progress: the thread that runs it
	private final BulkList<Thread> iterations = BulkList.New();

	// gates of threaded queries whose logics may run: of executions in progress, to recognize their threads,
	// and of queries that ended while logics were still reading
	private final BulkList<QueryWorkers> workers = BulkList.New();

	// controlled solely by GigaMap#markReadOnly / #unmarkReadOnly
	private int explicitCount;



	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////

	ReadHolds(final GigaMap<?> map)
	{
		super();
		this.map = map;
	}



	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////

	final void openReader(final GigaMap.Reading reader)
	{
		this.readers.add(reader);
	}

	/**
	 * @return whether the reader was open.
	 */
	final boolean closeReader(final GigaMap.Reading reader)
	{
		return this.readers.removeOne(reader);
	}

	final void enterIteration()
	{
		this.iterations.add(Thread.currentThread());
	}

	final void exitIteration()
	{
		this.iterations.removeOne(Thread.currentThread());
	}

	/**
	 * Makes the threads of a threaded execution known, so a mutation attempted by one of its consumers fails
	 * instead of waiting for its own execution.
	 */
	final void registerWorkers(final QueryWorkers workers)
	{
		this.workers.add(workers);
	}

	/**
	 * Ends a threaded query's use of its workers. Must be called before the query's reader or iteration is
	 * released: logics that did not start yet never will, and mutations wait for the ones still running.
	 *
	 * @return whether logics are still running.
	 */
	final boolean releaseWorkers(final QueryWorkers workers)
	{
		final boolean running = workers.close();
		if(running && !this.workers.contains(workers))
		{
			this.workers.add(workers);
		}

		// also here: a map that is only read never gets to the check for mutations
		this.pruneWorkers();

		return running;
	}

	private void pruneWorkers()
	{
		if(!this.workers.isEmpty())
		{
			this.workers.removeBy(QueryWorkers::isDone);
		}
	}

	final void markExplicit()
	{
		this.explicitCount++;
	}

	final void unmarkExplicit()
	{
		if(this.explicitCount <= 0)
		{
			// would otherwise lift the mark of another caller, or leave the map read-only for good
			throw new IllegalStateException(
				XChars.systemString(this.map) + " unmarkReadOnly() called without a matching markReadOnly()."
			);
		}
		this.explicitCount--;
	}

	final boolean isReadOnly()
	{
		// including logics that still read after their query ended: mutations wait for them, too
		this.pruneWorkers();

		return this.explicitCount != 0
			|| !this.readers.isEmpty()
			|| !this.iterations.isEmpty()
			|| !this.workers.isEmpty()
		;
	}

	/**
	 * Decides for a mutation attempted by the current thread: {@code false} if it may proceed, {@code true} if
	 * it has to wait for threads that still read, an exception if waiting would never end or is not wanted
	 * (explicit read-only mode, the current thread is one of the readers itself).
	 */
	final boolean mustWaitToMutate()
	{
		if(this.explicitCount != 0)
		{
			throw new IllegalStateException(XChars.systemString(this.map) + " is in read only mode.");
		}

		this.pruneWorkers();
		if(this.readers.isEmpty() && this.iterations.isEmpty() && this.workers.isEmpty())
		{
			return false;
		}

		// the current thread would wait for itself
		final Thread current = Thread.currentThread();
		if(this.iterations.contains(current))
		{
			throw this.modifiedDuringIteration();
		}
		for(final QueryWorkers w : this.workers)
		{
			if(w.isRunningOn(current))
			{
				throw this.modifiedDuringIteration();
			}
		}
		for(final GigaMap.Reading reader : this.readers)
		{
			if(reader.owningThread() == current)
			{
				throw new IllegalStateException(
					"Self-deadlock detected: the current thread holds an open read iterator on this "
					+ GigaMap.class.getSimpleName() + " and cannot mutate it until that iterator is closed. "
					+ "Close the iterator (e.g. via try-with-resources) before mutating, or iterate and "
					+ "mutate on separate threads."
				);
			}
		}

		// Readers and iterations of other threads: wait until they are done. An iteration is waited for like a
		// reader, so a writing thread does not fail because of a query running elsewhere.
		// If failing fast is preferred for iterations (a mutation that an iteration's consumer waits for can
		// never get through): throw #modifiedDuringIteration here if this.iterations is not empty.
		return true;
	}

	private IllegalStateException modifiedDuringIteration()
	{
		return new IllegalStateException(
			XChars.systemString(this.map) + " must not be structurally modified during iteration."
		);
	}

	/**
	 * How long a waiting mutation may sleep on the map's monitor before it has to check again, 0 for "until
	 * notified". Readers and iterations notify the monitor when they end, so do executions, whose gates are
	 * registered while they run. Logics that still run after their query ended (a closed gate) do not notify: for
	 * them the monitor is polled.
	 * <p>
	 * Every closed gate counts, also one that is done meanwhile: the caller decided to wait on an earlier state
	 * ({@link #mustWaitToMutate()}), and the logics may have ended since without notifying anybody. A done gate is
	 * pruned at the next check.
	 */
	final long waitTimeout()
	{
		for(final QueryWorkers w : this.workers)
		{
			if(w.isClosed())
			{
				return 1;
			}
		}
		return 0;
	}

	@Override
	public String toString()
	{
		return "readers " + this.readers.size()
			+ ", iterations " + this.iterations.size()
			+ ", query workers " + this.workers.size()
			+ ", explicit " + this.explicitCount
		;
	}

}
