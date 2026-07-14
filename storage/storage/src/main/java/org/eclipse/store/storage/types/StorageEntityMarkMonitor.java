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

import java.util.function.Supplier;

import org.eclipse.serializer.chars.VarString;
import org.eclipse.serializer.math.XMath;
import org.eclipse.serializer.persistence.types.PersistenceLiveStorerRegistry;
import org.eclipse.serializer.persistence.types.PersistenceObjectIdAcceptor;
import org.eclipse.serializer.reference.Referencing;
import org.eclipse.serializer.reference.Swizzling;
import org.eclipse.serializer.util.logging.Logging;
import org.eclipse.store.storage.exceptions.StorageException;
import org.slf4j.Logger;


/**
 * Central instance serving as a locking instance (concurrency monitor) for concurrently marking entities.
 * Via the indirection over a pure OID (long primitives) mark queue, the actual marking, sweeping and concurrency
 * management associated with it is strictly thread local, like the rest of the storage implementation is.
 * Without that centralization and indirection, absolute concurrency correctness is hard to achieve and much more
 * coding effort.
 *
 */
public interface StorageEntityMarkMonitor extends PersistenceObjectIdAcceptor
{
	public void signalPendingStoreUpdate(StorageEntityCache<?> channel);

	public void resetCompletion();

	public void advanceMarking(StorageObjectIdMarkQueue objectIdMarkQueue, int amount);

	public void clearPendingStoreUpdate(StorageEntityCache<?> channel);

	/**
	 * Signals that the passed channel is currently collecting entity data for a load request.
	 * While any channel has a pending load, {@link #isMarkingComplete()} reports {@code false},
	 * preventing a sweep from being initiated in the middle of a load task.
	 * <p>
	 * This is the load-side counterpart to {@link #signalPendingStoreUpdate(StorageEntityCache)}:
	 * entity data handed out by a load will be registered in the application's object registry
	 * and thus be kept alive by the sweep-time safety net even if it is not reachable from the
	 * persisted root. If that happens after the live object id seed of the current GC cycle ran,
	 * the entity itself survives the sweep, but its references are never traversed, so entities
	 * reachable only through it (e.g. the target of a not yet resolved lazy reference) get swept
	 * and the surviving entity's persisted record dangles ("mid-cycle registration race").
	 * Blocking sweep initiation for the duration of the load task gives the entity cache a stable
	 * window to gc-protect and enqueue the collected entities
	 * (see StorageEntityCache.Default#markEntityForLoadedData), guaranteeing their references are
	 * marked before the sweep decides what to delete.
	 */
	public void signalPendingLoad(StorageEntityCache<?> channel);

	/**
	 * Clears the pending load state signaled via {@link #signalPendingLoad(StorageEntityCache)}
	 * for the passed channel.
	 */
	public void clearPendingLoad(StorageEntityCache<?> channel);

	/**
	 * Signals that a load task is in flight, covering ALL channels for its entire lifetime.
	 * While the count of pending load tasks is greater than zero, {@link #isMarkingComplete()}
	 * reports {@code false}, so no sweep can be initiated on any channel while any load task is
	 * being processed anywhere.
	 * <p>
	 * This is the task-scoped counterpart to the per-channel
	 * {@link #signalPendingLoad(StorageEntityCache)}: the latter is signaled inside each channel's
	 * own collect and is skipped entirely for a channel whose oid subset of the load is empty, so
	 * an in-flight load is invisible to a sibling channel's sweep-initiation check (internal#85).
	 * Signaling this task-scoped gate once at load-task enqueue (before any channel is notified)
	 * and clearing it once when the task has been processed on all channels closes that
	 * cross-channel visibility gap: no wave can initiate while a load is in flight, so the loaded
	 * graph is fully gc-protected (its references gray-marked and traversed) before any sweep runs.
	 * Reference-counted so concurrent load tasks are handled correctly.
	 */
	public void signalPendingLoadTask();

	/**
	 * Clears the task-scoped pending load state signaled via {@link #signalPendingLoadTask()}.
	 * Called exactly once per load task, when the task has completed on all channels.
	 */
	public void clearPendingLoadTask();

	public boolean isComplete();

	public boolean isComplete(StorageEntityCache<?> channel);

	public boolean needsSweep(StorageEntityCache<?> channel);

	public boolean isPendingSweep(StorageEntityCache<?> channel);

	public void completeSweep(
		StorageEntityCache<?>  channel                ,
		StorageRootOidSelector rootObjectIdSelector   ,
		long                   channelRootObjectId    ,
		LiveObjectIdsIterator  liveObjectIdsIterator
	);

	public boolean isMarkingComplete();

	/**
	 * Reports whether the current sweep wave's live-OID seed has become stale relative to the passed
	 * registration version, i.e. whether the application registry gained a new association after this
	 * wave snapshotted its seed but before the calling channel executes its sweep (internal#85,
	 * "Window B" / GC.md §10.4).
	 * <p>
	 * A channel calls this at the very start of its sweep, from inside the registry mutex (so the
	 * version cannot change under it for the duration of that channel's sweep). If it returns
	 * {@code true}, the seed no longer reflects what the application holds: a just-loaded orphan graph
	 * (e.g. a reheated parent whose unloaded {@code Lazy} target is neither loaded nor marked) could be
	 * partially swept. The channel then defers its collection with a keep-all pass instead of deleting,
	 * so the wave's post-sweep re-seed can mark the newly-reachable graph transitively before the next
	 * sweep decides what to delete.
	 * <p>
	 * The {@code currentRegistrationVersion} must be read by the caller (a lock-free volatile read via
	 * {@link LiveObjectIdsIterator#registrationVersion()}); implementations must not acquire their own
	 * monitor here, as the caller already holds the registry mutex and the seed path takes the monitor
	 * before the registry (acquiring the monitor here would invert that order).
	 * <p>
	 * Default is {@code false} (never stale): correct for implementations that do not participate in
	 * the registration-version protocol; they keep the pre-existing sweep behavior.
	 */
	public default boolean isSeedRegistrationStale(final long currentRegistrationVersion)
	{
		return false;
	}

	/**
	 * Signals that the marking of the currently issued garbage collection cannot complete
	 * (a channel failed mid-marking and will never deliver its pending marks). Channels waiting
	 * inside an issued garbage collection for other channels' marks must exit promptly instead
	 * of waiting forever. Marking state itself (mark queues, pending marks count) is left
	 * untouched; the abandoned marks are drained by the failed channel's subsequent
	 * (housekeeping) garbage collection runs.
	 * <p>
	 * Default is a no-op for custom implementations (issued garbage collections then keep the
	 * pre-existing behavior of waiting for the time budget).
	 */
	public default void signalGcMarkingAbort()
	{
		// no-op by default
	}

	/**
	 * Clears an abort signaled via {@link #signalGcMarkingAbort()}. Called once per issued
	 * garbage collection task before any channel starts processing it, arming the new attempt.
	 */
	public default void clearGcMarkingAbort()
	{
		// no-op by default
	}

	/**
	 * @return whether {@link #signalGcMarkingAbort()} has been signaled for the current issued
	 * garbage collection attempt.
	 */
	public default boolean isGcMarkingAborted()
	{
		return false;
	}

	public StorageReferenceMarker provideReferenceMarker(StorageEntityCache<?> channel);

	public void enqueue(StorageObjectIdMarkQueue objectIdMarkQueue, long objectId);


	/**
	 * Reset to a clean initial state, ready to be used.
	 */
	public void reset();


	public static StorageEntityMarkMonitor.Creator Creator()
	{
		return Creator(StorageEntityMarkMonitor.Creator.Defaults.defaultReferenceCacheLength());
	}
	
	public static StorageEntityMarkMonitor.Creator Creator(final int referenceCacheLength)
	{
		return new StorageEntityMarkMonitor.Creator.Default(
				XMath.positive(referenceCacheLength)
		);
	}
	
	public interface ObjectIds
	{
		public long[] objectIds();
		
		public int size();
	}

	public interface Creator
	{
		public StorageEntityMarkMonitor createEntityMarkMonitor(
			StorageObjectIdMarkQueue[]                 oidMarkQueues         ,
			StorageEventLogger                         eventLogger           ,
			Referencing<PersistenceLiveStorerRegistry> refStorerRegistry     ,
			LiveObjectIdsIterator                      liveObjectIdsIterator
		);
		
		public StorageEntityMarkMonitor cachedInstance();
		
		
		
		public interface Defaults
		{
			public static int defaultReferenceCacheLength()
			{
				/*
				 * Since every channel allocates a reference cache array for every other channel,
				 * the total amount of reference caches is channelCount^2.
				 * This means that the reference cache length should not be to big, otherwise the
				 * occupied memory increases dramatically with the number of channel.
				 * E.g:
				 * Length 10_000:
				 * 32 channel occupy 32*32*10_000*8 = 80 MB just for reference caches
				 * 64 channel occupy 64*64*10_000*8 = 300 MB just for reference caches
				 * 
				 * Since this is just a cache to prevent inter-thread-communication for single objectIds,
				 * it doesn't have to be very big in the first place. It just defines how big the batch
				 * will be that is communicated between channels. 100 should be fine. Numbers up to 1000 are
				 * conceivable. Everything beyond that should be more or less overkill or even crazy.
				 */
				return 100;
			}
		}



		public final class Default implements StorageEntityMarkMonitor.Creator
		{
			///////////////////////////////////////////////////////////////////////////
			// instance fields //
			////////////////////
			
			private final int                referenceCacheLength;
			private StorageEntityMarkMonitor cachedInstance      ;
			
			
			///////////////////////////////////////////////////////////////////////////
			// constructors //
			/////////////////
			
			Default(final int referenceCacheLength)
			{
				super();
				this.referenceCacheLength = referenceCacheLength;
			}
			
			
			
			///////////////////////////////////////////////////////////////////////////
			// methods //
			////////////
			
			@Override
			public StorageEntityMarkMonitor createEntityMarkMonitor(
				final StorageObjectIdMarkQueue[]                 objectIdMarkQueues   ,
				final StorageEventLogger                         eventLogger          ,
				final Referencing<PersistenceLiveStorerRegistry> refStorerRegistry    ,
				final LiveObjectIdsIterator                      liveObjectIdsIterator
			)
			{
				return this.cachedInstance = new StorageEntityMarkMonitor.Default(
					objectIdMarkQueues.clone(),
					eventLogger,
					refStorerRegistry,
					this.referenceCacheLength,
					liveObjectIdsIterator
				);
			}
			
			@Override
			public StorageEntityMarkMonitor cachedInstance()
			{
				return this.cachedInstance;
			}

		}

	}


	final class Default implements StorageEntityMarkMonitor, StorageReferenceMarker
	{
		private final static Logger logger = Logging.getLogger(StorageEntityMarkMonitor.class);
		
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////
		
		// state 1.0: immutable or stateless (as far as this implementation is concerned)

		private final Referencing<PersistenceLiveStorerRegistry> refStorerRegistry;
		
		private final StorageEventLogger eventLogger         ;
		private final int                channelCount        ;
		private final int                channelHash         ;
		private final int                referenceCacheLength;
		
		
		// state 2.0: final references to mutable instances, i.e. content must be cleared on reset
		
		private final StorageObjectIdMarkQueue[] oidMarkQueues   ;
		private final long[]                     channelRootOids ;
		private final StorageReferenceMarker[]   referenceMarkers;

		
		// state 3.0: mutable fields. Must be cleared on reset.
		
		private       long      pendingMarksCount      ;
		private final boolean[] pendingStoreUpdates    ;
		private       int       pendingStoreUpdateCount;
		private final boolean[] pendingLoads           ;
		private       int       pendingLoadCount       ;
		private       int       pendingLoadTaskCount   ;

		/*
		 * Set when a channel fails mid-marking during an ISSUED garbage collection: its pending
		 * marks will never be delivered, so sibling channels waiting for them must exit instead
		 * of waiting forever (the issued GC's time budget is effectively unbounded). Cleared
		 * once per issued GC task before processing starts. Deliberately does NOT touch
		 * pendingMarksCount or the mark queues: the abandoned marks are durable channel state
		 * that the failed channel's subsequent (housekeeping) GC runs drain consistently.
		 */
		private boolean gcMarkingAborted;
		
		private final boolean[] needsSweep             ;
		private       int       sweepingChannelCount   ;
		
		private long sweepGeneration     ;
		private long lastSweepStart      ;
		private long lastSweepEnd        ;
		private long gcHotGeneration     ;
		private long gcColdGeneration    ;
		private long lastGcHotCompletion ;
		private long lastGcColdCompletion;
		
		/*
		 * Indicates that no new data (store) has been received since the last sweep.
		 * This basically means that no more gc marking or sweeping is necessary, however as stored entities
		 * (both newly created and updated) are forced gray, potentially any number of entities can be
		 * virtually doomed but still be kept alive. Those will only be found in a second mark and sweep since the
		 * last store.
		 * This flag can be seen as "no new data level 1".
		 */
		private boolean gcHotPhaseComplete;
		
		/*
		 * Indicates that not only no new data has been received since the last sweep, but also that a second sweep
		 * has already been executed since then, removing all unreachable entities and effectively establishing
		 * a clean / optimized / stable state.
		 * This flag can be seen as "no new data level 2".
		 * It will shut off all GC activity until the next store resets the flags.
		 */
		private boolean gcColdPhaseComplete;

		/*
		 * Application-side iterator used to seed live object ids into the mark queue
		 * around sweep boundaries. Constructor-injected, may be null in non-embedded
		 * (e.g. test or REST viewer) scenarios where there is no application registry.
		 *
		 * Used by two complementary mechanisms:
		 *  1. Pre-sweep gate in callToSweepRequired() - guards the very first sweep
		 *     of a GC cycle so registry-only-kept entities are transitively marked
		 *     before any sweep ever happens (covers cycle 0 where no prior post-sweep
		 *     seed exists).
		 *  2. Post-sweep seed in completeSweep() - re-establishes application-state
		 *     mark roots after every sweep completion (both hot and cold) so the
		 *     next mark cycle traverses everything reachable from app-held entities.
		 */
		private final LiveObjectIdsIterator liveObjectIdsIterator;

		/*
		 * Flag indicating whether live application OIDs have been seeded into the
		 * mark queue at least once for the current GC cycle (pre-sweep gate).
		 * Reset on resetCompletion() (which is invoked on every store, arming the
		 * gate again for the next cycle). Note that "at least once" is not "exactly
		 * once": every sweep initiation additionally compares the application
		 * registry's registration version against the snapshot taken at the last
		 * seed (seedRegistrationVersion) and re-runs the seed if registrations
		 * happened in between — see the seed/verify loop in callToSweepRequired().
		 */
		private boolean liveOidsSeededForCurrentCycle;

		/*
		 * Registration version of the application object registry observed when the
		 * live-OID seed last ran (see LiveObjectIdsIterator#registrationVersion).
		 * Compared against the current version before every sweep initiation: a
		 * mismatch means the registry gained entries AFTER the seed (mid-cycle
		 * registration race) — e.g. a load of an orphaned entity whose unloaded
		 * lazy reference's target is neither loaded nor registered. Sweeping with
		 * the stale seed would rescue the registered entities only shallowly and
		 * delete their binary-referenced children, so the seed must be re-run and
		 * transitively marked first. See GC.md §10.4.
		 * <p>
		 * Volatile: written under the monitor lock (callToSweepRequired / completeSweep) but read
		 * lock-free by a sweeping channel in {@link #isSeedRegistrationStale(long)} (that read cannot
		 * take the monitor lock — it runs while the channel holds the registry mutex, internal#85).
		 */
		private volatile long seedRegistrationVersion;



		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		Default(
			final StorageObjectIdMarkQueue[]                 oidMarkQueues        ,
			final StorageEventLogger                         eventLogger          ,
			final Referencing<PersistenceLiveStorerRegistry> refStorerRegistry    ,
			final int                                        referenceCacheLength ,
			final LiveObjectIdsIterator                      liveObjectIdsIterator
		)
		{
			super();
			this.eventLogger             = eventLogger                   ;
			this.refStorerRegistry       = refStorerRegistry             ;
			this.oidMarkQueues           = oidMarkQueues                 ;
			this.referenceCacheLength    = referenceCacheLength          ;
			this.channelCount            = oidMarkQueues.length          ;
			this.channelHash             = this.channelCount - 1         ;
			this.pendingStoreUpdates     = new boolean[this.channelCount];
			this.pendingLoads            = new boolean[this.channelCount];
			this.needsSweep              = new boolean[this.channelCount];
			this.channelRootOids         = new long   [this.channelCount];
			this.liveObjectIdsIterator   = liveObjectIdsIterator         ;

			this.referenceMarkers = new StorageReferenceMarker[this.channelCount];
			
			// mostly redundant for instance initialization, but consistency is important.
			this.initialize();
		}
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////
		
		private void initializeMarkQueues()
		{
			// this differs from resetMarkQueues() in that here are no consistency checks
			for(int i = 0; i < this.oidMarkQueues.length; i++)
			{
				this.oidMarkQueues[i].reset();
			}
		}
		
		private void initializeChannelRootIds()
		{
			for(int i = 0; i < this.channelRootOids.length; i++)
			{
				this.channelRootOids[i] = Swizzling.nullId();
			}
		}
		
		private void initializePendingStoreUpdates()
		{
			for(int i = 0; i < this.pendingStoreUpdates.length; i++)
			{
				this.pendingStoreUpdates[i] = false;
			}
			for(int i = 0; i < this.pendingLoads.length; i++)
			{
				this.pendingLoads[i] = false;
			}

			this.pendingMarksCount = 0;
			this.pendingStoreUpdateCount = 0;
			this.pendingLoadCount = 0;
			this.pendingLoadTaskCount = 0;
		}
		
		private void initializeSweepingState()
		{
			for(int i = 0; i < this.needsSweep.length; i++)
			{
				this.needsSweep[i] = false;
			}
			
			this.sweepingChannelCount = 0;
		}
		
		private void initializeCompletionState()
		{
			// GC is initially completed because there is no data at all. Initialization and stores will flip them.
			this.gcHotPhaseComplete            = true ;
			this.gcColdPhaseComplete           = true ;
			this.liveOidsSeededForCurrentCycle  = false;
			this.seedRegistrationVersion        = 0L   ;
			this.gcMarkingAborted               = false;
		}

		@Override
		public final synchronized void signalGcMarkingAbort()
		{
			this.gcMarkingAborted = true;

			// wake all channels potentially waiting on their mark queue for other channels' marks.
			for(final StorageObjectIdMarkQueue queue : this.oidMarkQueues)
			{
				synchronized(queue)
				{
					queue.notifyAll();
				}
			}
		}

		@Override
		public final synchronized void clearGcMarkingAbort()
		{
			this.gcMarkingAborted = false;
		}

		@Override
		public final synchronized boolean isGcMarkingAborted()
		{
			return this.gcMarkingAborted;
		}
		
		private void initializeGenerationalState()
		{
			this.sweepGeneration      = 0;
			this.lastSweepStart       = 0;
			this.lastSweepEnd         = 0;
			this.gcHotGeneration      = 0;
			this.gcColdGeneration     = 0;
			this.lastGcHotCompletion  = 0;
			this.lastGcColdCompletion = 0;
		}
		
		private final void initialize()
		{
			// this first block basically just sets everything to 0.
			this.initializeMarkQueues();
			this.initializeChannelRootIds();
			this.initializePendingStoreUpdates();
			this.initializeSweepingState();
			this.initializeGenerationalState();
			
			// referenceMarkers may NOT be cleared! They are initialized once with a linking instance that must be kept!
			
			// sets completion state to true, not false!
			this.initializeCompletionState();
		}
		
		@Override
		public final synchronized void reset()
		{
			/* Note:
			 * The methods for "resetting" mark queues and completion state refer to
			 * the operating state and are not applicable here.
			 * The actual resetting is not different from (re)initializing everything.
			 */
			this.initialize();
			
			// this is the only actually exclusive resetting method
			this.synchResetReferenceMarkers();
		}


		private void synchResetReferenceMarkers()
		{
			for(int i = 0; i < this.referenceMarkers.length; i++)
			{
				if(this.referenceMarkers[i] == null)
				{
					continue;
				}
				this.referenceMarkers[i].reset();
			}
		}

		private synchronized void incrementPendingMarksCount()
		{
			this.pendingMarksCount++;
		}

		@Override
		public final synchronized boolean isMarkingComplete()
		{
			// pendingLoadCount > 0 defers sweep initiation while a channel is collecting entities for
			// a load, and pendingLoadTaskCount > 0 defers it for the entire lifetime of any in-flight
			// load task (from enqueue until processed on all channels, closing the cross-channel
			// visibility gap of internal#85), so that entities handed out to the application are
			// gc-protected and their references are marked before any sweep (see #signalPendingLoad
			// and #signalPendingLoadTask).
			return this.pendingMarksCount == 0 && this.pendingStoreUpdateCount == 0
				&& this.pendingLoadCount == 0 && this.pendingLoadTaskCount == 0;
		}

		@Override
		public final boolean isSeedRegistrationStale(final long currentRegistrationVersion)
		{
			/*
			 * Deliberately NOT synchronized: the calling channel holds the registry mutex during its
			 * sweep, and the seed path takes this monitor's lock BEFORE the registry lock; acquiring
			 * the monitor lock here would invert that order and risk deadlock. A lock-free read is
			 * sufficient and correct: seedRegistrationVersion is volatile and liveObjectIdsIterator is
			 * final, and the sweep-initiation that set the snapshot happened-before this sweep runs.
			 * currentRegistrationVersion is read by the caller (a volatile read via the iterator).
			 */
			return this.liveObjectIdsIterator != null
				&& currentRegistrationVersion != this.seedRegistrationVersion;
		}

		@Override
		public final synchronized void advanceMarking(final StorageObjectIdMarkQueue oidMarkQueue, final int amount)
		{
//			DEBUGStorage.println(System.identityHashCode(oidMarkQueue) + " >-  " + this.pendingMarksCount + " " + oidMarkQueue.size());

			if(this.pendingMarksCount < amount)
			{
				throw new StorageException(
					"pending marks count (" + this.pendingMarksCount +
					") is smaller than the number to be advanced (" + amount + ")."
				);
			}

			/*
			 * Advance the oidMarkQueue not before the mark monitor has been locked and the amount has been validated.
			 * AND while the lock is held. Hence, the channel must pass and update its queue instance in here, not outside.
			 */
			oidMarkQueue.advanceTail(amount);
			this.pendingMarksCount -= amount;

		}

		@Override
		public final synchronized void signalPendingStoreUpdate(final StorageEntityCache<?> channel)
		{
			// check array to ensure idempotence
			if(!this.pendingStoreUpdates[channel.channelIndex()])
			{
				this.pendingStoreUpdates[channel.channelIndex()] = true;
				this.pendingStoreUpdateCount++;
			}
		}

		@Override
		public final synchronized void clearPendingStoreUpdate(final StorageEntityCache<?> channel)
		{
			// check array to ensure idempotence
			if(this.pendingStoreUpdates[channel.channelIndex()])
			{
				this.pendingStoreUpdates[channel.channelIndex()] = false;
				this.pendingStoreUpdateCount--;
			}
		}

		@Override
		public final synchronized void signalPendingLoad(final StorageEntityCache<?> channel)
		{
			// check array to ensure idempotence
			if(!this.pendingLoads[channel.channelIndex()])
			{
				this.pendingLoads[channel.channelIndex()] = true;
				this.pendingLoadCount++;
			}
		}

		@Override
		public final synchronized void clearPendingLoad(final StorageEntityCache<?> channel)
		{
			// check array to ensure idempotence
			if(this.pendingLoads[channel.channelIndex()])
			{
				this.pendingLoads[channel.channelIndex()] = false;
				this.pendingLoadCount--;
			}
		}

		@Override
		public final synchronized void signalPendingLoadTask()
		{
			this.pendingLoadTaskCount++;
		}

		@Override
		public final synchronized void clearPendingLoadTask()
		{
			// floor guard: may get called after reset() zeroed the count (see clearPendingLoad),
			// e.g. a load task completing after a storage reset. Must never go negative.
			if(this.pendingLoadTaskCount > 0)
			{
				this.pendingLoadTaskCount--;
			}
		}

		private synchronized void advanceGcCompletion()
		{
			if(this.gcColdPhaseComplete)
			{
				logger.debug("GC not needed");
				this.eventLogger.logGarbageCollectorNotNeeded();
				return;
			}

			if(this.gcHotPhaseComplete)
			{
				/*
				 * Note for debugging:
				 * For testing repeated GC runs, do NOT just deactivate the cold completion flag here.
				 * It will create a completion state inconsistency and thus a race condition in isComplete(),
				 * occasionally causing one channel to forever wait for itself while all others assumed
				 * completion via the hot phase + sweep count check.
				 * Nasty problem to find.
				 * To let the GC run repeatedly, modify the logic in isComplete() directly (always return false).
				 */
				this.gcColdPhaseComplete = true;
				this.lastGcColdCompletion = System.currentTimeMillis();
				this.gcColdGeneration++;
				logger.debug("Storage GC completed #{} @ {}", this.gcColdGeneration, this.lastGcColdCompletion);
				this.eventLogger.logGarbageCollectorCompleted(this.gcColdGeneration, this.lastGcColdCompletion);
			}
			else
			{
				this.gcHotPhaseComplete = true;
				this.lastGcHotCompletion = System.currentTimeMillis();
				this.gcHotGeneration++;
				logger.debug("Storage GC completed hot phase #{} @ {}", this.gcHotGeneration, this.lastGcHotCompletion);
				this.eventLogger.logGarbageCollectorCompletedHotPhase(this.gcHotGeneration, this.lastGcHotCompletion);
				
			}
		}

		private synchronized boolean callToSweepRequired()
		{
			// if there is already a sweep going on, no new sweep may be done
			if(this.sweepingChannelCount > 0)
			{
				return false;
			}

			// if no sweep is in progress, check if the marking is complete
			if(!this.isMarkingComplete())
			{
				return false;
			}

			/*
			 * Before initiating the sweep, ensure that all live application OIDs have been
			 * seeded into the mark queue for this cycle. This is critical: entities that are
			 * only alive via the PersistenceObjectRegistry safety net (not reachable from the
			 * persisted root) must have their transitive binary references marked before
			 * sweep, otherwise referenced entities not in the registry will be swept,
			 * producing zombie OIDs on the next mark phase.
			 *
			 * The loop covers, in one construct:
			 * - the initial pre-sweep gate of a fresh cycle (flag not yet set),
			 * - the MID-CYCLE REGISTRATION RACE: the registry gained entries after the last
			 *   seed (registration version moved past the snapshot) — e.g. a load of an
			 *   orphaned entity between seed and sweep registers the entity and its unloaded
			 *   Lazy but never the Lazy's target; sweeping with the stale seed would rescue
			 *   the registered entities only shallowly and delete the target. Re-run the
			 *   seed so the mark phase walks the new roots transitively first,
			 * - the cold cycle's verification against the post-sweep seed's snapshot
			 *   (the flag stays set, but the version compare still runs).
			 *
			 * The snapshot is taken BEFORE iterating (load-bearing): a registration racing
			 * with the seed iteration is then either included in the iteration or bumps the
			 * version past the snapshot and is caught by the next compare. Snapshotting
			 * after the seed could miss such a registration forever.
			 *
			 * Termination: a seed of a non-empty registry raises pendingMarksCount and
			 * returns false; the loop body only repeats immediately when the seed enqueued
			 * nothing, so it runs at most a couple of iterations per call. Continuous
			 * registrations defer the sweep across calls — the same trade-off as continuous
			 * stores deferring completion via resetCompletion(); proceeding with a stale
			 * seed instead would re-open the race.
			 */
			if(this.liveObjectIdsIterator != null)
			{
				while(!this.liveOidsSeededForCurrentCycle
					|| this.liveObjectIdsIterator.registrationVersion() != this.seedRegistrationVersion
				)
				{
					this.liveOidsSeededForCurrentCycle = true;
					this.seedRegistrationVersion       = this.liveObjectIdsIterator.registrationVersion();
					this.enqueueLiveApplicationOids(this.liveObjectIdsIterator);

					// If any OIDs were actually enqueued, marking is no longer complete.
					// Return false so the caller continues marking before retrying sweep.
					if(!this.isMarkingComplete())
					{
						return false;
					}
					// If no OIDs were enqueued (empty registry / all already marked),
					// the loop re-checks version stability and then falls through to sweep.
				}
			}

			this.lastSweepStart = System.currentTimeMillis();

			// reset channel root ids board because channels will update it upon ecountering the need to sweep.
			this.resetChannelRootIds();

			/*
			 * This is the (lock-secured) only time when it is guaranteed that all mark queues are empty.
			 * So reset them to free memory occupied by the last mark.
			 */
			this.resetMarkQueues();

			// no current sweep and completed marking means a new sweep has to be initiated.
			this.initiateSweep();

			return true;
		}
		
		@Override
		public final synchronized boolean needsSweep(final StorageEntityCache<?> channel)
		{
			/*
			 * If there is a pending sweep to be executed by the passed (= calling) channel, then mark as done
			 * and return that sweep is required, directly causing a sweep in the calling method.
			 *
			 * Otherwise, check if the passed/calling channel/thread is the first to recognize
			 * the current marking is complete (no more pending oids to mark) and issue that a channel-wide sweep is required.
			 * If so, again the current channel marks its own sweep to be done and returns causing a sweep.
			 *
			 * If both checks yield false, no sweep is needed.
			 *
			 * Note that the actual timing of when the sweep is done or before or after other threads already marking again is irrelevant.
			 * What is relevant is the logical order:
			 * - A required sweep is ONLY issued if the marking is safely completed (lock-secured central count == 0 check)
			 * - The sweep check itself is lock-secured and central, a sweep cannot be issues or done twice.
			 * - After the count == 0 case is detected, every channel will exactly sweep once before it can go back to marking
			 * - The mark oid queue (long[]) does not in any way interfere with the sweeping (local Entry instances) or vice versa.
			 */
			return this.isPendingSweep(channel) || this.callToSweepRequired();
		}

		@Override
		public final synchronized boolean isPendingSweep(final StorageEntityCache<?> channel)
		{
			return this.needsSweep[channel.channelIndex()];
		}

		@Override
		public final synchronized void completeSweep(
			final StorageEntityCache<?>  channel              ,
			final StorageRootOidSelector rootOidSelector      ,
			final long                   channelRootOid       ,
			final LiveObjectIdsIterator  liveObjectIdsIterator
		)
		{
			// register the channel's current valid root Oid after the performed sweep (potentially 0).
			this.channelRootOids[channel.channelIndex()] = channelRootOid;

			// mark this channel as having completed the sweep
			this.needsSweep[channel.channelIndex()] = false;

			logger.debug("StorageChannel#{} completed sweeping", channel.channelIndex());
			this.eventLogger.logGarbageCollectorSweepingComplete(channel);

			// decrement sweep channel count and execute completion logic if required.
			if(--this.sweepingChannelCount == 0)
			{
				this.lastSweepEnd = System.currentTimeMillis();
				this.incrementSweepGeneration();
				this.advanceGcCompletion();
				this.determineAndEnqueueRootOid(rootOidSelector);
				// Only seed the next mark cycle if there will actually be one. Once advanceGcCompletion()
				// has flipped gcColdPhaseComplete the housekeeping loop stops calling incrementalMark(),
				// so seeded OIDs would sit unread in the mark queues until the next store. The next cycle's
				// pre-sweep gate (after resetCompletion()) will re-seed the registry anyway.
				if(!this.gcColdPhaseComplete)
				{
					// snapshot BEFORE seeding (see callToSweepRequired): the cold cycle's sweep
					// initiation compares against this snapshot to catch registrations landing
					// in the hot-sweep -> cold-sweep window.
					if(liveObjectIdsIterator != null)
					{
						this.seedRegistrationVersion = liveObjectIdsIterator.registrationVersion();
					}
					this.enqueueLiveApplicationOids(liveObjectIdsIterator);
				}
			}
		}

		/**
		 * Seed the upcoming mark cycle with every currently live application-held object id so that
		 * entities kept alive only by the registry safety net still get their binary references
		 * transitively marked. Without this, an entity that survives sweep solely because its Java
		 * instance is registered can retain stale binary references to entities that were swept in
		 * the same cycle, producing zombie OIDs on the next mark phase and persistent data corruption.
		 */
		final synchronized void enqueueLiveApplicationOids(final LiveObjectIdsIterator liveObjectIdsIterator)
		{
			if(liveObjectIdsIterator == null)
			{
				return;
			}

			liveObjectIdsIterator.iterateLiveObjectIds(this);
		}
		
		private void incrementSweepGeneration()
		{
			final PersistenceLiveStorerRegistry storerRegistry = this.refStorerRegistry.get();
			if(storerRegistry != null)
			{
				// storerRegistry might be null if there is no connected application, yet.
				storerRegistry.clearGroupAndAdvance(this.sweepGeneration, this.sweepGeneration + 1);
			}

			this.sweepGeneration++;
		}

		final synchronized void resetChannelRootIds()
		{
			// no difference to reinitializing
			this.initializeChannelRootIds();
		}

		final synchronized void resetMarkQueues()
		{
			for(int i = 0; i < this.oidMarkQueues.length; i++)
			{
				if(this.oidMarkQueues[i].hasElements())
				{
					throw new StorageException("ObjectId mark queue for channel " + i + " still has elements.");
				}
				this.oidMarkQueues[i].reset();
			}
		}

		final synchronized void initiateSweep()
		{
			for(int i = 0; i < this.needsSweep.length; i++)
			{
				this.needsSweep[i] = true;
			}
			this.sweepingChannelCount = this.needsSweep.length;
		}

		final synchronized void determineAndEnqueueRootOid(final StorageRootOidSelector rootObjectIdSelector)
		{
			/*
			 * note that no lock on the selector instance is required because every channel thread
			 * brings his own exclusive instance and only uses it "in here" by itself.
			 */
			rootObjectIdSelector.resetGlobal();
			for(int i = 0; i < this.channelRootOids.length; i++)
			{
				rootObjectIdSelector.acceptGlobal(this.channelRootOids[i]);
			}

			// at least one channel MUST have a non-null root oid, otherwise the whole database would be wiped.
			final long currentMaxRootObjectId = rootObjectIdSelector.yieldGlobal();

			if(currentMaxRootObjectId == Swizzling.nullId())
			{
				/*
				 * no error here. Strictly seen, an empty or cleared database is valid.
				 * Should the need for an error arise, StorageRootOidSelector#yieldGlobal
				 * is the right place to do it in a customized way.
				 */
				return;
			}

			/*
			 * this initializes the next marking.
			 * From here on, pendingMarksCount can only be 0 again if marking is complete.
			 */
			this.acceptObjectId(currentMaxRootObjectId);
		}

		@Override
		public final void acceptObjectId(final long objectId)
		{
			// do not enqueue null oids, not even get the lock
			if(objectId == Swizzling.nullId())
			{
				return;
			}

			this.enqueue(this.oidMarkQueues[(int)(objectId & this.channelHash)], objectId);
		}

		@Override
		public final void enqueue(final StorageObjectIdMarkQueue objectIdMarkQueue, final long objectId)
		{
			this.incrementPendingMarksCount();
			// no need to keep the lock longer than necessary or nested with the queue lock.
			objectIdMarkQueue.enqueue(objectId);
		}

		@Override
		public final boolean tryFlush()
		{
			// nothing to flush in a single-oid-enqueing implementation.
			return false;
		}

		@Override
		public final StorageReferenceMarker provideReferenceMarker(final StorageEntityCache<?> channel)
		{
			if(this.referenceMarkers[channel.channelIndex()] != null)
			{
				throw new StorageException(
					StorageReferenceMarker.class.getSimpleName()
					+ " for channel #" + channel.channelIndex()
					+ " already exists."
				);
			}
			
			return this.referenceMarkers[channel.channelIndex()] =
				new CachingReferenceMarker(this, this.channelCount, this.referenceCacheLength)
			;
		}

		final void enqueueBulk(final ObjectIds[] oidsPerChannel)
		{
			long totalSize = 0;
			
			/* (24.02.2020 TM)FIXME: priv#72: how is this size-adding loop concurrency-safe? Research and comment!
			 * The size might get concurrently modified by other channel threads while the loop runs.
			 */
			for(final ObjectIds e : oidsPerChannel)
			{
				totalSize += e.size();
			}

			synchronized(this)
			{
				this.pendingMarksCount += totalSize;
			}

			final StorageObjectIdMarkQueue[] oidMarkQueues = this.oidMarkQueues;

			// lock for every queue is only acquired once and all oids are enqueued efficiently
			for(int i = 0; i < oidsPerChannel.length; i++)
			{
				if(oidsPerChannel[i].size() == 0)
				{
					// avoid unnecessary locking and execution overhead
					continue;
				}
				oidMarkQueues[i].enqueueBulk(oidsPerChannel[i].objectIds(), oidsPerChannel[i].size());
			}
		}

		@Override
		public final synchronized void resetCompletion()
		{
			this.gcHotPhaseComplete  = this.gcColdPhaseComplete = false;
			this.liveOidsSeededForCurrentCycle = false;
		}
		
		@Override
		public final synchronized boolean isComplete()
		{
			return this.gcColdPhaseComplete;
		}

		@Override
		public final synchronized boolean isComplete(final StorageEntityCache<?> channel)
		{
			/*
			 * GC is effectively complete if either:
			 * - the cold phase is complete (meaning nothing will/can change until the next store)
			 * - the hot phase (first sweep) is complete and the cold phase has only sweeps pending from other channels
			 * ! NOT if hot phase is completed and sweepingChannelCount is 0, because that applies to marking, too.
			 */
			return this.gcColdPhaseComplete
				|| this.gcHotPhaseComplete && this.sweepingChannelCount > 0 && !this.needsSweep[channel.channelIndex()]
			;
		}

		static final class CachingReferenceMarker implements StorageReferenceMarker
		{
			///////////////////////////////////////////////////////////////////////////
			// instance fields //
			////////////////////
			
			// state 1.0: immutable or stateless (as far as this implementation is concerned)
			
			private final StorageEntityMarkMonitor.Default markMonitor   ;
			private final int                              channelHash   ;
			private final int                              bufferLength  ;
			

			// state 2.0: final references to mutable instances, i.e. content must be cleared on reset
			
			private final ChannelItem[] oidsPerChannel;
			
					
			
			///////////////////////////////////////////////////////////////////////////
			// constructors //
			/////////////////

			CachingReferenceMarker(
				final StorageEntityMarkMonitor.Default markMonitor ,
				final int                              channelCount,
				final int                              bufferLength
			)
			{
				super();
				this.markMonitor  = markMonitor     ;
				this.bufferLength = bufferLength    ;
				this.channelHash  = channelCount - 1;
				
				this.oidsPerChannel = new ChannelItem[channelCount];
				for(int i = 0; i < channelCount; i++)
				{
					this.oidsPerChannel[i] = new ChannelItem(bufferLength);
				}
			}
			
			static final class ChannelItem implements ObjectIds
			{
				final long[] oids;
				      int    size;
				
				ChannelItem(final int capacity)
				{
					super();
					this.oids = new long[capacity];
				}
				
				/**
				 * Add the passed oid and returns the resulting size.
				 */
				final int add(final long oid)
				{
					this.oids[this.size] = oid;
					
					return ++this.size;
				}
				
				final boolean isEmpty()
				{
					return this.size == 0;
				}
				
				final void reset()
				{
					// this is sufficient. Old oid data in the array is irrelevant.
					this.size = 0;
				}

				@Override
				public final long[] objectIds()
				{
					return this.oids;
				}

				@Override
				public final int size()
				{
					return this.size;
				}
				
			}
			
			
			
			///////////////////////////////////////////////////////////////////////////
			// methods //
			////////////

			@Override
			public final void acceptObjectId(final long objectId)
			{
				// do not enqueue null oids
				if(objectId == Swizzling.nullId())
				{
					return;
				}

				final int i = (int)(objectId & this.channelHash);

				if(this.oidsPerChannel[i].add(objectId) == this.bufferLength)
				{
					this.flush();
				}
			}

			// (24.02.2020 TM)FIXME: how are the calls to this method concurrency-safe?
			final void flush()
			{
				this.markMonitor.enqueueBulk(this.oidsPerChannel);
				this.resetOidsPerChannel();
			}
			
			final void resetOidsPerChannel()
			{
				for(int i = 0; i < this.oidsPerChannel.length; i++)
				{
					this.oidsPerChannel[i].reset();
				}
			}

			@Override
			public final boolean tryFlush()
			{
				for(int i = 0; i < this.oidsPerChannel.length; i++)
				{
					if(!this.oidsPerChannel[i].isEmpty())
					{
						this.flush();
						return true;
					}
				}

				return false;
			}
			
			@Override
			public final void reset()
			{
				this.resetOidsPerChannel();
			}
		}


		private synchronized <T> T lockAllMarkQueues(final int currentIndex, final Supplier<T> logic)
		{
			// dynamic locking via trivial recursion
			if(currentIndex >= 0)
			{
				synchronized(this.oidMarkQueues[currentIndex])
				{
					return this.lockAllMarkQueues(currentIndex - 1, logic);
				}
			}

			return logic.get();
		}


		public synchronized String DEBUG_state()
		{
			return this.lockAllMarkQueues(this.channelHash, () ->
			{
				final VarString vs = VarString.New("GC state");

				vs
				.lf().padLeft(Long.toString(this.pendingMarksCount), 10, ' ').add(" pending marks count")
				;
				for(int i = 0; i < this.oidMarkQueues.length; i++)
				{
					vs.lf().padLeft(Long.toString(this.oidMarkQueues[i].size()), 10, ' ').add(" in channel #" + i);
				}

				vs
				.lf()
				.lf().add("Hot  complete: ").add(this.gcHotPhaseComplete)
				.lf().add("Cold complete: ").add(this.gcColdPhaseComplete)
				.lf()
				.lf().add("sweepGeneration     : ").add(this.sweepGeneration     )
				.lf().add("lastSweepEnd        : ").add(this.lastSweepEnd        )
				.lf().add("lastSweepStart      : ").add(this.lastSweepStart      )
				.lf().add("gcHotGeneration     : ").add(this.gcHotGeneration     )
				.lf().add("gcColdGeneration    : ").add(this.gcColdGeneration    )
				.lf().add("lastGcColdCompletion: ").add(this.lastGcColdCompletion)
				.lf().add("lastGcHotCompletion : ").add(this.lastGcHotCompletion )
				.lf()
				.lf().add("Needs sweep (").add(this.sweepingChannelCount).add("):")
				;
				for(int i = 0; i < this.needsSweep.length; i++)
				{
					vs.lf().blank().add(i).add(": ").add(this.needsSweep[i]);
				}

				vs
				.lf().padLeft(Long.toString(this.pendingStoreUpdateCount), 10, ' ').blank().add("pending store updates")
				;
				for(int i = 0; i < this.pendingStoreUpdates.length; i++)
				{
					vs.lf().blank().add(i).add(": ").add(this.pendingStoreUpdates[i]);
				}

				return vs.toString();
			});
		}

	}

}
