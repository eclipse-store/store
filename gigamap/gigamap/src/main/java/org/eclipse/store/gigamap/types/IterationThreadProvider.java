package org.eclipse.store.gigamap.types;

/*-
 * #%L
 * EclipseStore GigaMap
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

import org.eclipse.serializer.collections.BulkList;
import org.eclipse.serializer.collections.LimitList;
import org.eclipse.serializer.collections.types.XGettingCollection;

import static org.eclipse.serializer.math.XMath.notNegative;
import static org.eclipse.serializer.util.X.notNull;


/**
 * Interface that provides methods for managing and executing threads designed specifically
 * for iteration tasks. It extends the {@link ThreadCountProvider} interface,
 * allowing for dynamic thread resource allocation during complex computational operations.
 * <p>
 * Implementations of this interface may provide different strategies for thread creation
 * and pooling, ranging from simple thread instantiation to resource-optimized pooling mechanisms.
 * <p>
 * A query hands its work over as one logic per thread, see {@link IterationLogicProvider#provideIterationLogic()}.
 * {@link #executeThreaded(GigaMap, int, IterationLogicProvider) executeThreaded} must request {@code threadCount}
 * logics and run every one of them, or throw: if it returns with fewer logics requested or run, the query fails
 * with an {@link IllegalStateException}, since a consumer did not receive its entities. A logic that is run after
 * its query has ended does nothing.
 * <p>
 * {@link #disposeIterationThreads(XGettingCollection) disposeIterationThreads} may be called while the threads
 * still run their logics, e.g. for an iterator that is closed early. Such a thread must not be handed to another
 * query before its logic is done.
 */
public interface IterationThreadProvider extends ThreadCountProvider
{
	public default void prepareIteration()
	{
		// no-op by default
	}
	
	public XGettingCollection<? extends Thread> startIterationThreads(
		GigaMap<?>             parent       ,
		int                    threadCount  ,
		IterationLogicProvider logicProvider
	);
	
	public void executeThreaded(
		GigaMap<?>             parent       ,
		int                    threadCount  ,
		IterationLogicProvider logicProvider
	);
	
	public default void disposeIterationThreads(final XGettingCollection<? extends Thread> threads)
	{
		// no-op by default
	}
	
	public default void completeIteration()
	{
		// no-op by default
	}
	
	public default void shutdown()
	{
		// no-op by default
	}
	
	
	public static IterationThreadProvider None()
	{
		return new None();
	}
	
	public static IterationThreadProvider Creating(final ThreadCountProvider threadCountProvider)
	{
		return new Creating(
			notNull(threadCountProvider)
		);
	}
	
	public static IterationThreadProvider Pooling(
		final int                 reservedThreadCount,
		final ThreadCountProvider threadCountProvider
	)
	{
		return new Pooling(
			notNegative(reservedThreadCount),
			    notNull(threadCountProvider)
		);
	}
	
	public abstract class Abstract implements IterationThreadProvider
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////
		
		private final ThreadCountProvider threadCountProvider;
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////
		
		Abstract(final ThreadCountProvider threadCountProvider)
		{
			super();
			this.threadCountProvider = threadCountProvider;
		}
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////
		
		@Override
		public final int provideThreadCount(final GigaMap<?> parent, final BitmapResult[] results)
		{
			return this.threadCountProvider.provideThreadCount(parent, results);
		}
		
	}
	
	public final class None implements IterationThreadProvider
	{
		@Override
		public int provideThreadCount(final GigaMap<?> parent, final BitmapResult[] results)
		{
			return 0;
		}

		@Override
		public XGettingCollection<? extends Thread> startIterationThreads(
			final GigaMap<?>             parent       ,
			final int                    threadCount  ,
			final IterationLogicProvider logicProvider
		)
		{
			throw new UnsupportedOperationException();
		}

		@Override
		public void executeThreaded(
			final GigaMap<?>             parent       ,
			final int                    threadCount  ,
			final IterationLogicProvider logicProvider
		)
		{
			throw new UnsupportedOperationException();
		}

	}
	
	public final class Creating extends Abstract
	{
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////
		
		Creating(final ThreadCountProvider threadCountProvider)
		{
			super(threadCountProvider);
		}
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////

		@Override
		public final XGettingCollection<? extends Thread> startIterationThreads(
			final GigaMap<?>             parent       ,
			final int                    threadCount  ,
			final IterationLogicProvider logicProvider
		)
		{
			final LimitList<Thread> threads = LimitList.New(threadCount);
			for(int t = 0; t < threadCount; t++)
			{
				final Runnable logic = logicProvider.provideIterationLogic();
				final Thread  thread = new Thread(logic);
				thread.setDaemon(true);
				threads.add(thread);
			}
			
			threads.iterate(Thread::start);

			return threads;
		}
		
		@Override
		public void executeThreaded(
			final GigaMap<?>             parent       ,
			final int                    threadCount  ,
			final IterationLogicProvider logicProvider
		)
		{
			final XGettingCollection<? extends Thread> threads = this.startIterationThreads(parent, threadCount, logicProvider);
			
			for(final Thread t : threads)
			{
				synchronized(t)
				{
					try
					{
						t.join();
					}
					catch(final InterruptedException e)
					{
						// interrupted while waiting for the thread to complete. So don't wait for it and move on,
						// but keep the interrupt status for the caller.
						Thread.currentThread().interrupt();
					}
				}
			}
		}
		
	}
	
	/* (19.01.2024 TM)TODO: smarter thread pooling
	 * This implementation is very basic and could be made a lot smarter:
	 * - reduce allocated threads over time
	 * - configurable minimum of threads to hold reserved.
	 * - maybe give the threads a weak reference back to this instance so they terminate implicitly
	 */
	public final class Pooling extends Abstract
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////

		private final ThreadReserve reserve;



		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		Pooling(final int reservedThreadCount, final ThreadCountProvider threadCountProvider)
		{
			super(threadCountProvider);
			this.reserve = new ThreadReserve(reservedThreadCount);
			Cleaners.SHARED.register(this, new Cleanup(this.reserve));
		}
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////

		@Override
		public final synchronized LimitList<PoolThread> startIterationThreads(
			final GigaMap<?>             parent       ,
			final int                    threadCount  ,
			final IterationLogicProvider logicProvider
		)
		{
			final LimitList<PoolThread> threads = LimitList.New(threadCount);
			try
			{
				for(int t = 0; t < threadCount; t++)
				{
					final PoolThread poolThread = this.allocateThread();
					threads.add(poolThread);
					final Runnable logic = logicProvider.provideIterationLogic();
					synchronized(poolThread)
					{
						poolThread.setLogic(logic);
						poolThread.notifyAll();
					}
				}
			}
			catch(final Throwable t)
			{
				// the threads allocated so far must not be lost to the pool
				this.disposeIterationThreads(threads);
				throw t;
			}

			return threads;
		}
		
		@Override
		public void executeThreaded(
			final GigaMap<?>             parent       ,
			final int                    threadCount  ,
			final IterationLogicProvider logicProvider
		)
		{
			final LimitList<PoolThread> threads = this.startIterationThreads(parent, threadCount, logicProvider);
			
			for(final PoolThread t : threads)
			{
				try
				{
					t.waitOnCompletion();
				}
				catch(final InterruptedException e)
				{
					// interrupted while waiting for the thread to complete. So don't wait for it and move on,
					// but keep the interrupt status for the caller.
					Thread.currentThread().interrupt();
				}
			}
			
			this.disposeIterationThreads(threads);
		}
		
		private PoolThread allocateThread()
		{
			return this.reserve.allocate();
		}
		
		@Override
		public synchronized void disposeIterationThreads(final XGettingCollection<? extends Thread> threads)
		{
			for(final Thread t : threads)
			{
				if(t instanceof PoolThread)
				{
					((PoolThread)t).release();
				}
			}
		}
		
		@Override
		public synchronized void shutdown()
		{
			// Synchronous deactivation. Does NOT cancel the registered Cleaner —
			// if the pool is reused after shutdown, threads added later will
			// still be deactivated on GC of this Pooling instance. Threads that
			// are busy now are deactivated when they come back.
			// `synchronized` matches startIterationThreads/disposeIterationThreads.
			this.reserve.shutdown();
		}

		/**
		 * The idle threads of a pool and the count of its shutdowns, shared with its threads and its cleanup.
		 * Its monitor guards both and the flags of its threads. A thread still running a logic comes back by itself
		 * when the logic is done, so a busy thread is never handed out and nobody has to wait for it.
		 * <p>
		 * Lock order: this monitor before a thread's monitor, never the other way around.
		 */
		static final class ThreadReserve
		{
			///////////////////////////////////////////////////////////////////////////
			// instance fields //
			////////////////////

			final BulkList<PoolThread> idle     ;
			private int                shutdowns;



			///////////////////////////////////////////////////////////////////////////
			// constructors //
			/////////////////

			ThreadReserve(final int capacity)
			{
				super();
				this.idle = BulkList.New(capacity);
			}



			///////////////////////////////////////////////////////////////////////////
			// methods //
			////////////

			final synchronized PoolThread allocate()
			{
				final PoolThread thread;
				if(this.idle.isEmpty())
				{
					thread = new PoolThread(this);
					thread.start();
				}
				else
				{
					thread = this.idle.pop();
				}
				thread.allocated = true;
				thread.shutdowns = this.shutdowns;

				return thread;
			}

			// The caller holds this monitor. A thread handed out before a shutdown would otherwise outlive it.
			final void takeBack(final PoolThread thread)
			{
				if(thread.shutdowns == this.shutdowns)
				{
					this.idle.add(thread);
				}
				else
				{
					thread.deactivate();
				}
			}

			final synchronized void shutdown()
			{
				this.shutdowns++;
				this.idle.iterate(PoolThread::deactivate);
				this.idle.truncate();
			}

		}

		// Holds the thread reserve for Cleaner-based shutdown.
		//
		// MUST be a static nested class — capturing the enclosing Pooling
		// would keep it strongly reachable and prevent the Cleaner from firing.
		// PoolThread is `static final` and holds no back-reference to Pooling,
		// so Pooling can be reclaimed independently of its threads.
		private static final class Cleanup implements Runnable
		{
			private final ThreadReserve reserve;

			Cleanup(final ThreadReserve reserve)
			{
				this.reserve = reserve;
			}

			@Override
			public void run()
			{
				this.reserve.shutdown();
			}
		}

	}
	
	
	static final class PoolThread extends Thread
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////
		
		volatile Runnable logic    ;
		volatile boolean  isRunning = true;

		// the owning pool's reserve. Its monitor guards the three fields below.
		private final Pooling.ThreadReserve reserve;
		boolean allocated      ; // handed out by the pool, not released yet
		boolean returnWhenDone ; // released while its logic was not done yet
		int     shutdowns      ; // the reserve's shutdown count when handed out
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////
		
		PoolThread(final Pooling.ThreadReserve reserve)
		{
			super();
			this.reserve = reserve;
			this.setDaemon(true);
		}
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////
		
		@Override
		public void run()
		{
			while(this.isRunning)
			{
				final Runnable logic = this.waitForWork();
				if(logic == null)
				{
					continue;
				}

				// Not under this thread's monitor: a caller waiting for completion must be able to stop waiting.
				try
				{
					logic.run();
				}
				finally
				{
					this.done();
				}
			}
		}
		
		private synchronized Runnable waitForWork()
		{
			try
			{
				while(this.logic == null)
				{
					if(!this.isRunning)
					{
						return null;
					}
					this.wait();
				}
			}
			catch(final InterruptedException e)
			{
				// thread has been interrupted from waiting
				return null;
			}

			return this.logic;
		}
		
		private void done()
		{
			synchronized(this.reserve)
			{
				this.logic = null;
				if(this.returnWhenDone)
				{
					this.returnWhenDone = false;
					this.reserve.takeBack(this);
				}
			}
			synchronized(this)
			{
				this.notifyAll();
			}
		}
		
		/**
		 * Gives the thread back to its pool: right away if it is idle, otherwise as soon as its logic is done.
		 * Does not wait for the logic.
		 */
		final void release()
		{
			synchronized(this.reserve)
			{
				if(!this.allocated)
				{
					return;
				}
				this.allocated = false;
				if(this.logic == null)
				{
					this.reserve.takeBack(this);
				}
				else
				{
					this.returnWhenDone = true;
				}
			}
		}
		
		final synchronized void deactivate()
		{
			this.isRunning = false;
			this.notifyAll(); // wakes the thread up from waiting for work
		}
		
		final synchronized void setLogic(final Runnable logic)
		{
			this.logic = logic;
		}
		
		final synchronized void waitOnCompletion() throws InterruptedException
		{
			while(this.logic != null)
			{
				this.wait();
			}
		}
		
	}
	
	
	
	@FunctionalInterface
	public interface IterationLogicProvider
	{
		public Runnable provideIterationLogic();
	}
	
}
