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

import static org.eclipse.serializer.math.XMath.positive;
import static org.eclipse.serializer.util.X.notNull;

import java.util.function.Supplier;

import org.eclipse.serializer.chars.VarString;
import org.eclipse.serializer.util.logging.Logging;
import org.slf4j.Logger;

/**
 * Configures how much time each {@link StorageChannel} spends on incremental housekeeping work
 * (garbage collection, file cleanup, entity-cache eviction, transaction-log cleanup) per
 * housekeeping cycle.
 * <p>
 * A controller is built from two primary values: an interval in milliseconds at which housekeeping
 * cycles are scheduled, and a per-cycle time budget in nanoseconds. The ratio of these two values
 * determines, very roughly, the percentage of CPU time the storage spends on housekeeping: a 10ms
 * budget per 1000ms interval is approximately 1% of one channel thread. The controller can also
 * report individual budgets per task category so that, for example, garbage collection can run on
 * a different budget than file cleanup.
 * <p>
 * In addition to the {@link #New() framework default} controller (constant interval and budget),
 * this interface offers an {@link #Adaptive(long, long, long, StorageFoundation) adaptive} variant
 * that wraps another controller and progressively increases the budgets while the garbage collector
 * is unable to reach the sweeping phase, and shrinks them back to the wrapped values once GC
 * completes. Use the {@link #AdaptiveBuilder(StorageFoundation) AdaptiveBuilder} for fluent
 * configuration of the adaptive variant.
 *
 * @see Storage#HousekeepingController()
 * @see StorageChannel
 */
public interface StorageHousekeepingController
{
	/**
	 * @return The housekeeping interval in milliseconds.
	 */
	public long housekeepingIntervalMs();

	/**
	 * @return The general housekeeping time budget per interval in nanoseconds.
	 */
	public long housekeepingTimeBudgetNs();

	/**
	 * @return The garbage collection housekeeping time budget per interval in nanoseconds.
	 */
	public long garbageCollectionTimeBudgetNs();

	/**
	 * @return The live/cache check housekeeping time budget per interval in nanoseconds.
	 */
	public long liveCheckTimeBudgetNs();

	/**
	 * @return The file cleanup housekeeping time budget per interval in nanoseconds.
	 */
	public long fileCheckTimeBudgetNs();

	/**
	 * The number of consecutive garbage-collection sweeps an entity must remain unmarked before it is
	 * actually deleted. A value of {@code 1} deletes an unmarked entity on the first sweep (the classic
	 * behavior); higher values keep unreachable entities for that many sweeps as a probabilistic safety
	 * net against rare, transient GC concurrency races, at the cost of reclaiming garbage slightly later.
	 *
	 * @return the number of consecutive unmarked sweeps before an entity is collected, in range {@code [1, 127]}.
	 */
	public int garbageCollectionSweepThreshold();



	/**
	 * Static helpers exposing the lower bounds for {@link StorageHousekeepingController}
	 * configuration values and a range-check that throws {@link IllegalArgumentException} on
	 * violation.
	 */
	public interface Validation
	{
		public static long minimumHousekeepingIntervalMs()
		{
			return 1;
		}

		public static long minimumHousekeepingTimeBudgetNs()
		{
			return 0;
		}

		public static int minimumGarbageCollectionSweepThreshold()
		{
			return 1;
		}

		public static int maximumGarbageCollectionSweepThreshold()
		{
			// stored in the negative range of a single signed byte (see StorageEntity.gcState), so bounded by 127.
			return 127;
		}

		public static void validateParameters(
			final long housekeepingIntervalMs  ,
			final long housekeepingTimeBudgetNs
		)
			throws IllegalArgumentException
		{
			validateParameters(
				housekeepingIntervalMs                              ,
				housekeepingTimeBudgetNs                            ,
				Defaults.defaultGarbageCollectionSweepThreshold()
			);
		}

		public static void validateParameters(
			final long housekeepingIntervalMs        ,
			final long housekeepingTimeBudgetNs      ,
			final int  garbageCollectionSweepThreshold
		)
			throws IllegalArgumentException
		{
			if(housekeepingIntervalMs < minimumHousekeepingIntervalMs())
			{
				throw new IllegalArgumentException(
					"Specified housekeeping millisecond interval of "
					+ housekeepingIntervalMs
					+ " is lower than the minimum value "
					+ minimumHousekeepingIntervalMs()+ "."
				);
			}
			if(housekeepingTimeBudgetNs < minimumHousekeepingTimeBudgetNs())
			{
				throw new IllegalArgumentException(
					"Specified housekeeping nanosecond time budget of "
					+ housekeepingTimeBudgetNs
					+ " is lower than the minimum value "
					+ minimumHousekeepingTimeBudgetNs()+ "."
				);
			}
			if(garbageCollectionSweepThreshold < minimumGarbageCollectionSweepThreshold()
			|| garbageCollectionSweepThreshold > maximumGarbageCollectionSweepThreshold()
			)
			{
				throw new IllegalArgumentException(
					"Specified garbage collection sweep threshold of "
					+ garbageCollectionSweepThreshold
					+ " is outside the valid range ["
					+ minimumGarbageCollectionSweepThreshold() + ", "
					+ maximumGarbageCollectionSweepThreshold() + "]."
				);
			}
		}
	}

	/**
	 * Pseudo-constructor method to create a new {@link StorageHousekeepingController} instance
	 * using default values defined by {@link StorageHousekeepingController.Defaults}.
	 * 
	 * @return a new {@link StorageHousekeepingController} instance.
	 * 
	 * @see StorageHousekeepingController#New(long, long)
	 * @see Storage#HousekeepingController()
	 * @see StorageHousekeepingController.Defaults
	 */
	public static StorageHousekeepingController New()
	{
		/*
		 * Validates its own default values, but the cost is negligible and it is a
		 * good defense against accidentally erroneous changes of the default values.
		 */
		return new StorageHousekeepingController.Default(
			Defaults.defaultHousekeepingIntervalMs(),
			Defaults.defaultHousekeepingTimeBudgetNs()
		);
	}
	
	/**
	 * Pseudo-constructor method to create a new {@link StorageHousekeepingController} instance
	 * using the passed values.<p>
	 * The combination of these two values can be used to define how much percentage of the system's computing power
	 * shall be used for storage housekeeping.<br>
	 * Example:<br>
	 * 10 Million ns (= 10 ms) housekeeping budget every 1000 ms
	 * means (roughly) 1% of the computing power will be used for storage housekeeping.<p>
	 * Note that in an application where no store occurs over a longer period of time, all housekeeping tasks
	 * will eventually be completed, reducing the required computing power to 0. When the next store occurs, the
	 * housekeeping starts anew.<br>
	 * How long the housekeeping requires to complete depends on the computing power it is granted by the
	 * {@link StorageHousekeepingController}, other configurations (like entity data cache timeouts)
	 * and the amount of data that has to be managed.
	 * <p>
	 * See all "issue~" methods in {@link StorageConnection} for a way to call housekeeping actions explicitly
	 * and causing them to be executed completely.
	 * 
	 * @param housekeepingIntervalMs the interval in milliseconds that the storage threads shall
	 *        execute their various housekeeping actions (like cache clearing checks, file consolidation, etc.).
	 *        Must be greater than zero.
	 * 
	 * @param housekeepingTimeBudgetNs the time budget in nanoseconds that each storage thread will use to perform
	 *        a housekeeping action. This is a best effort value, not a strictly reliable border value. This means
	 *        a housekeeping action can occasionally take slightly longer than specified here.
	 *        Must be greater than zero.
	 * 
	 * @return a new {@link StorageHousekeepingController} instance.
	 * 
	 * @see StorageHousekeepingController#New()
	 * @see Storage#HousekeepingController(long, long)
	 */
	public static StorageHousekeepingController New(
		final long housekeepingIntervalMs  ,
		final long housekeepingTimeBudgetNs
	)
	{
		Validation.validateParameters(housekeepingIntervalMs, housekeepingTimeBudgetNs);

		return new StorageHousekeepingController.Default(
			housekeepingIntervalMs  ,
			housekeepingTimeBudgetNs
		);
	}

	/**
	 * Pseudo-constructor method to create a new {@link StorageHousekeepingController} instance
	 * using the passed values, including the garbage collection sweep threshold.
	 *
	 * @param housekeepingIntervalMs the interval in milliseconds that the storage threads shall
	 *        execute their various housekeeping actions. Must be greater than zero.
	 * @param housekeepingTimeBudgetNs the time budget in nanoseconds that each storage thread will use
	 *        to perform a housekeeping action. Must not be negative.
	 * @param garbageCollectionSweepThreshold the number of consecutive garbage-collection sweeps an
	 *        entity must remain unmarked before it is deleted, in range {@code [1, 127]}.
	 * @return a new {@link StorageHousekeepingController} instance.
	 *
	 * @see StorageHousekeepingController#New(long, long)
	 */
	public static StorageHousekeepingController New(
		final long housekeepingIntervalMs        ,
		final long housekeepingTimeBudgetNs      ,
		final int  garbageCollectionSweepThreshold
	)
	{
		Validation.validateParameters(
			housekeepingIntervalMs         ,
			housekeepingTimeBudgetNs       ,
			garbageCollectionSweepThreshold
		);

		return new StorageHousekeepingController.Default(
			housekeepingIntervalMs         ,
			housekeepingTimeBudgetNs       ,
			garbageCollectionSweepThreshold
		);
	}

	/**
	 * Static factory for the framework default housekeeping interval and time budget used by
	 * {@link StorageHousekeepingController#New()}.
	 */
	public interface Defaults
	{
		public static long defaultHousekeepingIntervalMs()
		{
			return 1_000; // ms
		}

		public static long defaultHousekeepingTimeBudgetNs()
		{
			return 10_000_000; // ns
		}

		public static int defaultGarbageCollectionSweepThreshold()
		{
			// safe-by-default: an unreachable entity must be unmarked on 3 consecutive sweeps before deletion.
			return 3;
		}
	}


	/**
	 * Default {@link StorageHousekeepingController} implementation: returns the configured interval
	 * and time budget verbatim, applying the same general budget to garbage collection, live check
	 * and file check.
	 */
	public final class Default implements StorageHousekeepingController
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////

		private final long intervalMs, nanoTimeBudget;
		private final int  gcSweepThreshold;



		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		Default(final long intervalMs, final long nanoTimeBudget)
		{
			this(intervalMs, nanoTimeBudget, Defaults.defaultGarbageCollectionSweepThreshold());
		}

		Default(final long intervalMs, final long nanoTimeBudget, final int gcSweepThreshold)
		{
			super();
			this.intervalMs       = intervalMs      ;
			this.nanoTimeBudget   = nanoTimeBudget  ;
			this.gcSweepThreshold = gcSweepThreshold;
		}



		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////

		@Override
		public final long housekeepingIntervalMs()
		{
			return this.intervalMs;
		}

		@Override
		public final long housekeepingTimeBudgetNs()
		{
			return this.nanoTimeBudget;
		}

		@Override
		public final long garbageCollectionTimeBudgetNs()
		{
			// no special treatment in generic base implementation
			return this.housekeepingTimeBudgetNs();
		}

		@Override
		public final long liveCheckTimeBudgetNs()
		{
			// no special treatment in generic base implementation
			return this.housekeepingTimeBudgetNs();
		}

		@Override
		public final long fileCheckTimeBudgetNs()
		{
			// no special treatment in generic base implementation
			return this.housekeepingTimeBudgetNs();
		}

		@Override
		public final int garbageCollectionSweepThreshold()
		{
			return this.gcSweepThreshold;
		}

		@Override
		public String toString()
		{
			return VarString.New()
				.add(this.getClass().getName()).add(':').lf()
				.blank().add("house keeping interval"           ).tab().add('=').blank().add(this.intervalMs).lf()
				.blank().add("house keeping nano time budget"   ).tab().add('=').blank().add(this.nanoTimeBudget).lf()
				.blank().add("garbage collection sweep threshold").tab().add('=').blank().add(this.gcSweepThreshold)
				.toString()
			;
		}

	}
	
	/**
	 * Pseudo-constructor method to create a new adaptive {@link StorageHousekeepingController} instance
	 * using the passed values.
	 * <p>
	 * It will wrap a {@link StorageHousekeepingController} with default values and increase the time budgets on demand,
	 * if the garbage collector needs more time to reach the sweeping phase.
	 * 
	 * @see #New()
	 * 
	 * @param increaseThresholdMs the threshold in milliseconds of the adaption cycle to calculate new budgets for the housekeeping process
	 * @param increaseAmountNs the amount in nanoseconds the budgets will be increased each cycle
	 * @param maximumTimeBudgetNs the upper limit of the time budgets in nanoseconds
	 * @param foundation the {@link StorageFoundation} the controller is created for
	 * @return a new {@link StorageHousekeepingController} instance.
	 */
	public static StorageHousekeepingController Adaptive(
		final long                               increaseThresholdMs,
		final long                               increaseAmountNs   ,
		final long                               maximumTimeBudgetNs,
		final StorageFoundation<?>               foundation
	)
	{
		return Adaptive(
			StorageHousekeepingController.New(),
			foundation.getEntityMarkMonitorCreator()::cachedInstance,
			increaseThresholdMs                                     ,
			increaseAmountNs                                        ,
			maximumTimeBudgetNs                                     ,
			foundation
		);
	}

	
	/**
	 * Pseudo-constructor method to create a new adaptive {@link StorageHousekeepingController} instance
	 * using the passed values.
	 * <p>
	 * It will wrap a {@link StorageHousekeepingController} with default values and increase the time budgets on demand,
	 * if the garbage collector needs more time to reach the sweeping phase.
	 * 
	 * @see #New()
	 * 
	 * @param monitorSupplier mark monitor used to query GC state
	 * @param increaseThresholdMs the threshold in milliseconds of the adaption cycle to calculate new budgets for the housekeeping process
	 * @param increaseAmountNs the amount in nanoseconds the budgets will be increased each cycle
	 * @param maximumTimeBudgetNs the upper limit of the time budgets in nanoseconds
	 * @param foundation the {@link StorageFoundation} the controller is created for
	 * @return a new {@link StorageHousekeepingController} instance.
	 */
	public static StorageHousekeepingController Adaptive(
		final Supplier<StorageEntityMarkMonitor> monitorSupplier    ,
		final long                               increaseThresholdMs,
		final long                               increaseAmountNs   ,
		final long                               maximumTimeBudgetNs,
		final StorageFoundation<?>               foundation
	)
	{
		return Adaptive(
			StorageHousekeepingController.New(),
			monitorSupplier                    ,
			increaseThresholdMs                ,
			increaseAmountNs                   ,
			maximumTimeBudgetNs                ,
			foundation
		);
	}
	
	/**
	 * Pseudo-constructor method to create a new adaptive {@link StorageHousekeepingController} instance
	 * using the passed values.
	 * <p>
	 * It will wrap the given {@link StorageHousekeepingController} and increase the time budgets on demand,
	 * if the garbage collector needs more time to reach the sweeping phase.
	 * 
	 * @see #New(long, long)
	 * 
	 * @param delegate the wrapped controller delivering the original budget values
	 * @param increaseThresholdMs the threshold in milliseconds of the adaption cycle to calculate new budgets for the housekeeping process
	 * @param increaseAmountNs the amount in nanoseconds the budgets will be increased each cycle
	 * @param maximumTimeBudgetNs the upper limit of the time budgets in nanoseconds
	 * @param foundation the {@link StorageFoundation} the controller is created for
	 * @return a new {@link StorageHousekeepingController} instance.
	 */
	public static StorageHousekeepingController Adaptive(
		final StorageHousekeepingController      delegate           ,
		final long                               increaseThresholdMs,
		final long                               increaseAmountNs   ,
		final long                               maximumTimeBudgetNs,
		final StorageFoundation<?>               foundation
	)
	{
		return Adaptive(
			delegate,
			foundation.getEntityMarkMonitorCreator()::cachedInstance,
			increaseThresholdMs,
			increaseAmountNs,
			maximumTimeBudgetNs,
			foundation
		);
	}
	
	/**
	 * Pseudo-constructor method to create a new adaptive {@link StorageHousekeepingController} instance
	 * using the passed values.
	 * <p>
	 * It will wrap the given {@link StorageHousekeepingController} and increase the time budgets on demand,
	 * if the garbage collector needs more time to reach the sweeping phase.
	 * 
	 * @see #New(long, long)
	 * 
	 * @param delegate the wrapped controller delivering the original budget values
	 * @param monitorSupplier mark monitor used to query GC state
	 * @param increaseThresholdMs the threshold in milliseconds of the adaption cycle to calculate new budgets for the housekeeping process
	 * @param increaseAmountNs the amount in nanoseconds the budgets will be increased each cycle
	 * @param maximumTimeBudgetNs the upper limit of the time budgets in nanoseconds
	 * @param foundation the {@link StorageFoundation} the controller is created for
	 * @return a new {@link StorageHousekeepingController} instance.
	 */
	public static StorageHousekeepingController Adaptive(
		final StorageHousekeepingController      delegate           ,
		final Supplier<StorageEntityMarkMonitor> monitorSupplier    ,
		final long                               increaseThresholdMs,
		final long                               increaseAmountNs   ,
		final long                               maximumTimeBudgetNs,
		final StorageFoundation<?>               foundation
	)
	{
		final StorageHousekeepingController.Adaptive controller = new StorageHousekeepingController.Adaptive(
			notNull (delegate           ),
			notNull (monitorSupplier    ),
			positive(increaseThresholdMs),
			positive(increaseAmountNs   ),
			positive(maximumTimeBudgetNs)
		);
		foundation.addEventLogger(controller);
		return controller;
	}
	
	/**
	 * Pseudo-constructor method to create a new adaptive {@link StorageHousekeepingController} builder.
	 * It will wrap a {@link StorageHousekeepingController} with default values.
	 * 
	 * @param foundation foundation to create the builder parts with
	 * 
	 * @return a new {@link AdaptiveBuilder} instance
	 */
	public static AdaptiveBuilder AdaptiveBuilder(final StorageFoundation<?> foundation)
	{
		return AdaptiveBuilder(
			StorageHousekeepingController.New(),
			foundation.getEntityMarkMonitorCreator()::cachedInstance
		);
	}
	
	/**
	 * Pseudo-constructor method to create a new adaptive {@link StorageHousekeepingController} builder.
	 * It will wrap a {@link StorageHousekeepingController} with default values.
	 * 
	 * @param monitorSupplier mark monitor used to query GC state
	 * 
	 * @return a new {@link AdaptiveBuilder} instance
	 */
	public static AdaptiveBuilder AdaptiveBuilder(final Supplier<StorageEntityMarkMonitor> monitorSupplier)
	{
		return AdaptiveBuilder(
			StorageHousekeepingController.New(),
			monitorSupplier
		);
	}
	
	/**
	 * Pseudo-constructor method to create a new adaptive {@link StorageHousekeepingController} builder.
	 * It will wrap the given {@link StorageHousekeepingController}.
	 * 
	 * @param delegate the wrapped controller delivering the original budget values
	 * @param foundation foundation to create the builder parts with
	 * 
	 * @return a new {@link AdaptiveBuilder} instance
	 */
	public static AdaptiveBuilder AdaptiveBuilder(
		final StorageHousekeepingController delegate  ,
		final StorageFoundation<?>          foundation
	)
	{
		return new AdaptiveBuilder.Default(
			notNull(delegate),
			foundation.getEntityMarkMonitorCreator()::cachedInstance
		);
	}
	
	/**
	 * Pseudo-constructor method to create a new adaptive {@link StorageHousekeepingController} builder.
	 * It will wrap the given {@link StorageHousekeepingController}.
	 * 
	 * @param delegate the wrapped controller delivering the original budget values
	 * @param monitorSupplier mark monitor used to query GC state
	 * 
	 * @return a new {@link AdaptiveBuilder} instance
	 */
	public static AdaptiveBuilder AdaptiveBuilder(
		final StorageHousekeepingController      delegate       ,
		final Supplier<StorageEntityMarkMonitor> monitorSupplier
	)
	{
		return new AdaptiveBuilder.Default(
			notNull(delegate       ),
			notNull(monitorSupplier)
		);
	}
	
	/**
	 * Builder type for an adaptive {@link StorageHousekeepingController}
	 *
	 */
	public interface AdaptiveBuilder
	{
		/**
		 * @param increaseThresholdMs the threshold in milliseconds of the adaption cycle to calculate new budgets for the housekeeping process
		 * @return this builder instance
		 */
		public AdaptiveBuilder increaseThresholdMs(long increaseThresholdMs);
		
		/**
		 * 
		 * @param increaseAmountNs the amount in nanoseconds the budgets will be increased each cycle
		 * @return this builder instance
		 */
		public AdaptiveBuilder increaseAmountNs(long increaseAmountNs);
		
		/**
		 * 
		 * @param maximumTimeBudgetNs the upper limit of the time budgets in nanoseconds
		 * @return this builder instance
		 */
		public AdaptiveBuilder maximumTimeBudgetNs(long maximumTimeBudgetNs);
		
		/**
		 * Builds the {@link StorageHousekeepingController} instance given the provided values.
		 * 
		 * @param foundation the {@link StorageFoundation} the controller should be created for
		 * @return a new {@link StorageHousekeepingController} instance
		 */
		public StorageHousekeepingController buildFor(final StorageFoundation<?> foundation);
		
		
		public static class Default implements AdaptiveBuilder
		{
			private final StorageHousekeepingController      delegate            ;
			private final Supplier<StorageEntityMarkMonitor> monitorSupplier     ;
			private long                                     increaseThresholdMs = Adaptive.Defaults.defaultAdaptiveHousekeepingIncreaseThresholdMs();
			private long                                     increaseAmountNs    = Adaptive.Defaults.defaultAdaptiveHousekeepingIncreaseAmountNs   ();
			private long                                     maximumTimeBudgetNs = Adaptive.Defaults.defaultAdaptiveHousekeepingMaximumTimeBudgetNs();
			
			Default(
				final StorageHousekeepingController      delegate,
				final Supplier<StorageEntityMarkMonitor> monitorSupplier
			)
			{
				super();
				this.delegate         = delegate        ;
				this.monitorSupplier  = monitorSupplier ;
			}
		
			@Override
			public AdaptiveBuilder increaseThresholdMs(final long increaseThresholdMs)
			{
				this.increaseThresholdMs = increaseThresholdMs;
				return this;
			}
			
			@Override
			public AdaptiveBuilder increaseAmountNs(final long increaseAmountNs)
			{
				this.increaseAmountNs = increaseAmountNs;
				return this;
			}
			
			@Override
			public AdaptiveBuilder maximumTimeBudgetNs(final long maximumTimeBudgetNs)
			{
				this.maximumTimeBudgetNs = maximumTimeBudgetNs;
				return this;
			}
			
			@Override
			public StorageHousekeepingController buildFor(final StorageFoundation<?> foundation)
			{
				final StorageHousekeepingController.Adaptive controller = new StorageHousekeepingController.Adaptive(
					this.delegate           ,
					this.monitorSupplier    ,
					this.increaseThresholdMs,
					this.increaseAmountNs   ,
					this.maximumTimeBudgetNs
				);
				foundation.addEventLogger(controller);
				return controller;
			}
			
		}
		
	}
	
	
	/**
	 * Adaptive {@link StorageHousekeepingController} that wraps a base controller and progressively
	 * increases its time budgets while the garbage collector is making forward progress but has not
	 * yet reached a sweeping phase. The increases are capped at a configurable maximum and reset
	 * back to the base values once the GC reports completion or no work needed.
	 * <p>
	 * The instance also implements {@link StorageEventLogger}: it must be registered as an event
	 * logger on the foundation so that GC-completion events drive the reset back to base budgets.
	 */
	public final class Adaptive implements StorageHousekeepingController, StorageEventLogger
	{
		/**
		 * Defaults for the {@link Adaptive} controller's tuning parameters: how often to step up the
		 * budgets, by how much per step, and the upper limit they will not exceed.
		 */
		public interface Defaults
		{
			public static long defaultAdaptiveHousekeepingIncreaseThresholdMs()
			{
				return 5000; // 5 seconds
			}
			
			public static long defaultAdaptiveHousekeepingIncreaseAmountNs()
			{
				return 50_000_000; // 50 ms
			}
			
			public static long defaultAdaptiveHousekeepingMaximumTimeBudgetNs()
			{
				return 500_000_000; // half second
			}
		}
		

		private final static Logger logger = Logging.getLogger(Adaptive.class);
		
		
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////
		
		private final StorageHousekeepingController      delegate           ;
		private final Supplier<StorageEntityMarkMonitor> monitorSupplier    ;
		private final long                               increaseThresholdMs;
		private final long                               increaseAmountNs   ;
		private final long                               maximumTimeBudgetNs;
				
		// mutable adaptive state
		
		private long                                lastFinishedGCCycle = 0;
		private long                                lastIncrease        = 0;
		private long                                currentIncreaseNs   = 0;
		
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		Adaptive(
			final StorageHousekeepingController      delegate           ,
			final Supplier<StorageEntityMarkMonitor> monitorSupplier    ,
			final long                               increaseThresholdMs,
			final long                               increaseAmountNs   ,
			final long                               maximumNsTimeBudget
		)
		{
			super();
			this.delegate            = delegate           ;
			this.monitorSupplier     = monitorSupplier    ;
			this.increaseThresholdMs = increaseThresholdMs;
			this.increaseAmountNs    = increaseAmountNs   ;
			this.maximumTimeBudgetNs = maximumNsTimeBudget;
		}
		
		
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////
		
		private void reset()
		{
			this.lastFinishedGCCycle = this.lastIncrease = System.currentTimeMillis();
			if(this.currentIncreaseNs != 0)
			{
				this.currentIncreaseNs = 0;
				logger.debug("Housekeeping time budget reset to default");
			}
		}
		
		private long increaseNs()
		{
			if(this.monitorSupplier.get().isComplete())
			{
				this.reset();
			}
			else
			{
				final long now = System.currentTimeMillis();
				if( now - this.increaseThresholdMs > this.lastFinishedGCCycle
				&& (this.lastIncrease <= 0 || now - this.lastIncrease > this.increaseThresholdMs)
				)
				{
					this.lastIncrease = now;
					this.internalSetIncrease(this.currentIncreaseNs + this.increaseAmountNs);
				}
			}
			return this.currentIncreaseNs;
		}
		
		private void internalSetIncrease(final long increaseNs)
		{
			this.currentIncreaseNs = Math.min(
				this.maximumTimeBudgetNs,
				increaseNs
			);
			logger.debug("Housekeeping time budget increased by {} ns", String.format("%,d", this.currentIncreaseNs));
		}

		@Override
		public long housekeepingIntervalMs()
		{
			return this.delegate.housekeepingIntervalMs();
		}

		@Override
		public long housekeepingTimeBudgetNs()
		{
			return Math.min(
				this.maximumTimeBudgetNs,
				this.delegate.housekeepingTimeBudgetNs() + this.increaseNs()
			);
		}

		@Override
		public long garbageCollectionTimeBudgetNs()
		{
			return Math.min(
				this.maximumTimeBudgetNs,
				this.delegate.garbageCollectionTimeBudgetNs() + this.increaseNs()
			);
		}

		@Override
		public long liveCheckTimeBudgetNs()
		{
			return Math.min(
				this.maximumTimeBudgetNs,
				this.delegate.liveCheckTimeBudgetNs() + this.increaseNs()
			);
		}

		@Override
		public long fileCheckTimeBudgetNs()
		{
			return Math.min(
				this.maximumTimeBudgetNs,
				this.delegate.fileCheckTimeBudgetNs() + this.increaseNs()
			);
		}

		@Override
		public int garbageCollectionSweepThreshold()
		{
			// the sweep threshold is not time-adaptive; pass the wrapped controller's value through.
			return this.delegate.garbageCollectionSweepThreshold();
		}

		@Override
		public void logGarbageCollectorNotNeeded()
		{
			this.reset();
		}
		
		@Override
		public void logGarbageCollectorSweepingComplete(final StorageEntityCache<?> entityCache)
		{
			this.reset();
		}

	}

}
