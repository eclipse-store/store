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

import static org.eclipse.serializer.math.XMath.positive;


/**
 * A functional interface to provide a thread count for parallel operations in a {@link GigaMap}.
 * This interface defines methods and implementations to either fix the thread count or
 * adapt it dynamically based on certain parameters such as the available processor count or
 * the size of the task.
 * <p>
 * The primary purpose of this interface is to determine the optimal number of threads based
 * on input parameters, allowing for flexibility in the execution of multi-threaded tasks.
 */
@FunctionalInterface
public interface ThreadCountProvider
{
	/**
	 * Provides the number of threads to be used for parallel operations in a {@link GigaMap}.
	 * The thread count can be either fixed or dynamically determined based on input
	 * parameters such as the size of the results array or other contextual information.
	 *
	 * @param parent the GigaMap instance for which the thread count is being determined
	 * @param results an array of BitmapResult objects that may influence the thread count calculation
	 * @return the number of threads to be used for the operation
	 */
	public int provideThreadCount(GigaMap<?> parent, BitmapResult[] results);
	
	/**
	 * Creates a {@link ThreadCountProvider} with a fixed number of threads.
	 * The provided thread count is validated to ensure it is positive, and the resulting provider
	 * will always return the specified number of threads when used.
	 *
	 * @param threadCount the fixed number of threads to be used; must be a positive integer
	 * @return a {@link ThreadCountProvider} instance that always provides the specified number of threads
	 */
	public static ThreadCountProvider Fixed(final int threadCount)
	{
		return new Fixed(
			positive(threadCount)
		);
	}
	
	/**
	 * Creates an adaptive {@link ThreadCountProvider} that dynamically calculates the
	 * optimal number of threads based on the available processors on the system.
	 * The resulting provider adjusts the thread count to balance resource utilization
	 * and task execution efficiency.
	 *
	 * @return a {@link ThreadCountProvider} instance that adapts the thread count dynamically
	 *         using the number of available processors as the maximum thread count
	 */
	public static ThreadCountProvider Adaptive()
	{
		return new Adaptive(
			Runtime.getRuntime().availableProcessors()
		);
	}
	
	/**
	 * Creates an adaptive {@link ThreadCountProvider} that dynamically calculates the optimal number
	 * of threads based on the provided maximum thread count. The resulting provider determines the
	 * number of threads to be used for execution, taking into account both the maximum thread count
	 * and any relevant contextual factors such as the size of input arrays.
	 *
	 * @param maxThreadCount the maximum number of threads that can be used; must be a positive integer
	 * @return an adaptive {@link ThreadCountProvider} instance that adjusts the thread count
	 *         dynamically, constrained by the specified maximum thread count
	 */
	public static ThreadCountProvider Adaptive(final int maxThreadCount)
	{
		return new Adaptive(
			positive(maxThreadCount)
		);
	}
	
	
	
	public static class Fixed implements ThreadCountProvider
	{
		private final int threadCount;

		Fixed(final int threadCount)
		{
			super();
			this.threadCount = threadCount;
		}
	
		@Override
		public int provideThreadCount(final GigaMap<?> parent, final BitmapResult[] results)
		{
			return this.threadCount;
		}
		
	}
	
	public static class Adaptive implements ThreadCountProvider
	{
		private final int maxThreadCount;

		Adaptive(final int maxThreadCount)
		{
			super();
			this.maxThreadCount = maxThreadCount;
		}
	
		@Override
		public int provideThreadCount(final GigaMap<?> parent, final BitmapResult[] results)
		{
			return Math.min(results.length, this.maxThreadCount);
		}
		
	}
	
}
