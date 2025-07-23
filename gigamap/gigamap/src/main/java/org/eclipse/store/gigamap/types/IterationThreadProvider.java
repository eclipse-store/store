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
						// interrupted while waiting for the thread to complete. So don't wait for it and move on.
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
				
		private final BulkList<PoolThread>            reservedThreads ;
		private final BulkList<LimitList<PoolThread>> allocatedThreads;
			
			
		
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////
		
		Pooling(final int reservedThreadCount, final ThreadCountProvider threadCountProvider)
		{
			super(threadCountProvider);
			this.reservedThreads  = BulkList.New(reservedThreadCount);
			this.allocatedThreads = BulkList.New();
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
			this.ensureReservedThreadCount(threadCount);
			final LimitList<PoolThread> threads = LimitList.New(threadCount);
			for(int t = 0; t < threadCount; t++)
			{
				final PoolThread poolThread = this.reservedThreads.pop();
				final Runnable logic = logicProvider.provideIterationLogic();
				synchronized(poolThread)
				{
					poolThread.setLogic(logic);
					threads.add(poolThread);
					poolThread.notifyAll();
				}
			}
			this.allocatedThreads.add(threads);

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
				synchronized(t)
				{
					try
					{
						t.waitOnCompletion();
					}
					catch(final InterruptedException e)
					{
						// interrupted while waiting for the thread to complete. So don't wait for it and move on.
					}
				}
			}
			
			this.disposeIterationThreads(threads);
		}
		
		private void ensureReservedThreadCount(final int threadCount)
		{
			if(this.reservedThreads.size() >= threadCount)
			{
				return;
			}
			
			final int requiredThreadCount = threadCount - this.reservedThreads.intSize();
			for(int i = 0; i < requiredThreadCount; i++)
			{
				final PoolThread poolThread = new PoolThread();
				poolThread.start();
				this.reservedThreads.add(poolThread);
			}
		}
		
		@Override
		public synchronized void disposeIterationThreads(final XGettingCollection<? extends Thread> threads)
		{
			final long index = this.allocatedThreads.indexBy(t -> t == threads);
			if(index < 0)
			{
				return;
			}
			
			final LimitList<PoolThread> allocatedThreads = this.allocatedThreads.at(index);
			this.reservedThreads.addAll(allocatedThreads);
			this.allocatedThreads.removeAt(index);
		}
		
		@Override
		public void shutdown()
		{
			this.internalShutdown();
		}
		
		private void internalShutdown()
		{
			this.reservedThreads.iterate(t ->
			{
				synchronized(t)
				{
					t.deactivate();
					t.notifyAll(); // must notify to wake up inactive thread from waiting for work.
				}
			});
			this.reservedThreads.truncate();
		}
		
		@SuppressWarnings("deprecation")
		@Override
		protected void finalize() throws Throwable
		{
			this.internalShutdown();
		}
		
	}
	
	
	static final class PoolThread extends Thread
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////
		
		volatile Runnable logic    ;
		volatile boolean  isRunning = true;
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////
		
		PoolThread()
		{
			super();
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
				this.synchronizedWaitForWork();
			}
		}
		
		private synchronized void synchronizedWaitForWork()
		{
			try
			{
				while(this.logic == null)
				{
					this.wait();
					if(!this.isRunning)
					{
						return;
					}
				}
			}
			catch(final InterruptedException e)
			{
				// thread has been interrupted from waiting
				return;
			}
			
			this.logic.run();
			this.logic = null;
			this.notifyAll();
		}
		
		final synchronized void deactivate()
		{
			this.isRunning = false;
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
