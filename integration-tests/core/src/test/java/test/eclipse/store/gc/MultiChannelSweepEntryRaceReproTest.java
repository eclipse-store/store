package test.eclipse.store.gc;

/*-
 * #%L
 * EclipseStore Integration Tests
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.ref.WeakReference;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.serializer.persistence.types.ObjectIdsProcessor;
import org.eclipse.serializer.persistence.types.PersistenceObjectIdAcceptor;
import org.eclipse.serializer.persistence.types.PersistenceObjectRegistry;
import org.eclipse.serializer.reference.Lazy;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageObjectRegistryCallback;
import org.eclipse.store.storage.exceptions.StorageExceptionConsistency;
import org.eclipse.store.storage.types.Storage;
import org.eclipse.store.storage.types.StorageEntityCache;
import org.eclipse.store.storage.types.StorageGCZombieOidHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

/**
 * Regression test for the <b>sweep-entry registration race</b> ("Window B", the
 * residual documented in GC.md §10.4). This is the multi-channel continuation of
 * {@link MidCycleRegistrationRaceTest}: there the mid-cycle registration lands <i>before</i> the
 * sweep is initiated (caught by the pre-sweep re-seed of store#736); here it lands <i>after</i>
 * {@code initiateSweep} took the wave's seed snapshot but <i>before</i> a channel's destructive
 * sweep executes. With {@code channelCount >= 2} the wave sweeps channel by channel, so a load that
 * reheats an orphaned parent X (registering X and its eagerly built, unloaded Lazy L, never the
 * Lazy's cross-channel target Y) in that window let the shallow id-only sweep predicate rescue X+L
 * while a sibling channel swept the still-unmarked, unregistered Y — leaving L's persisted binary
 * dangling and the next reload throwing {@code No entity found for objectId Y}.
 * <p>
 * The fix (GC.md §10.4): each channel, at the very start of its sweep and inside the registry mutex,
 * compares the registry's current registration version against the wave's seed snapshot
 * ({@code StorageEntityMarkMonitor#isSeedRegistrationStale}). On a mismatch the channel defers its
 * collection with a keep-all pass instead of deleting; the wave's post-sweep re-seed then marks the
 * newly-reachable graph (X → L → Y) transitively before the next sweep decides what to delete, so
 * the whole graph survives and GC is merely deferred one cycle.
 * <p>
 * Determinism: a delegating registry callback ({@link SweepEntryRegisterTrap}) performs the
 * registration <i>on the first {@code processSelected} call</i> — i.e. after the wave has been
 * initiated (seed snapshot taken) but before any channel runs its delete loop — exactly what a
 * cross-channel mid-wave load would do to the registry at that point. No channel has swept yet when
 * the trap fires, so the placement of Y across channels is irrelevant to the outcome. A control run
 * performs the identical staging without the registration and asserts the whole orphan graph is
 * collected consistently.
 * <p>
 * {@code @Isolated} + flag restoration because
 * {@link StorageEntityCache.Default#setGarbageCollectionEnabled} is a static, JVM-global switch.
 */
@Isolated
public class MultiChannelSweepEntryRaceReproTest
{
	static final int CHANNEL_COUNT = 2;

	///////////////////////////////////////////////////////////////////////////
	// data types //
	///////////////

	public static class DataRoot
	{
		public Object ref;
	}

	/** X — survives the sweep via the id-only registry predicate. */
	public static class Parent
	{
		public Lazy<Payload> child;
	}

	/** Y — the never-built lazy target, on a (potentially) different channel than X. */
	public static class Payload
	{
		public final String data;

		public Payload(final String data)
		{
			super();
			this.data = data;
		}
	}

	static final class CountingZombieOidHandler implements StorageGCZombieOidHandler
	{
		final List<Long> zombieOids = Collections.synchronizedList(new ArrayList<>());

		@Override
		public boolean handleZombieOid(final long objectId)
		{
			this.zombieOids.add(objectId);
			return true;
		}
	}

	/**
	 * Delegating registry callback that makes the sweep-entry race deterministic: on the wave's HOT
	 * sweep it parks EVERY channel at the very start of {@code processSelected} (a barrier, before any
	 * channel's delete loop), then exactly one channel registers X and its unloaded Lazy L and
	 * releases the barrier. This reproduces a cross-channel mid-wave load landing after the wave's seed
	 * snapshot but before any channel deletes — with no reliance on thread scheduling (registering on a
	 * single channel is racy: a sibling channel can run its delete loop before the registration lands).
	 * The barrier is single-use: the subsequent cold sweep passes straight through (latches already
	 * tripped, injector already claimed), so the cold cycle marks X → L → Y and keeps them.
	 * <p>
	 * MUST forward {@code registrationVersion()} to the delegate: the interface default is a constant
	 * 0, which would pin the version and silently disable the very fix under test.
	 */
	static final class SweepEntryRegisterTrap implements EmbeddedStorageObjectRegistryCallback
	{
		private final EmbeddedStorageObjectRegistryCallback delegate = EmbeddedStorageObjectRegistryCallback.New();

		private PersistenceObjectRegistry registry;

		final boolean         blockAndInject;
		volatile boolean       armed;          // engaged only for the target wave (Phase 5), not setup GCs
		final CountDownLatch   allParked      = new CountDownLatch(CHANNEL_COUNT);
		final CountDownLatch   proceed        = new CountDownLatch(1);
		final AtomicBoolean    injectorClaim  = new AtomicBoolean();
		volatile long xOid;
		volatile long lOid;

		// strong stand-ins kept alive by this trap for the duration of the GC cycles.
		final Object standInX = new Object();
		final Object standInL = new Object();

		SweepEntryRegisterTrap(final boolean blockAndInject)
		{
			super();
			this.blockAndInject = blockAndInject;
		}

		@Override
		public void initializeObjectRegistry(final PersistenceObjectRegistry objectRegistry)
		{
			this.registry = objectRegistry;
			this.delegate.initializeObjectRegistry(objectRegistry);
		}

		@Override
		public long registrationVersion()
		{
			// forwarding is load-bearing, see class javadoc.
			return this.delegate.registrationVersion();
		}

		@Override
		public void iterateLiveObjectIds(final PersistenceObjectIdAcceptor acceptor)
		{
			this.delegate.iterateLiveObjectIds(acceptor);
		}

		@Override
		public boolean processSelected(final ObjectIdsProcessor processor)
		{
			if(this.blockAndInject && this.armed)
			{
				// Park this channel at sweep entry (BEFORE the delete loop and before the registry
				// mutex is taken by the delegate). Exactly one channel becomes the injector.
				final boolean injector = this.injectorClaim.compareAndSet(false, true);
				this.allParked.countDown();
				try
				{
					if(injector)
					{
						// wait until every channel is parked at its sweep entry, so no channel can run
						// its delete loop before the registration below is visible.
						if(this.allParked.await(20, TimeUnit.SECONDS))
						{
							// what a real cross-channel getObject(xOid) load registers at this point:
							// X and its eagerly built - but unloaded - Lazy L, never Y. Bumps the
							// registration version past the wave's seed snapshot.
							this.registry.registerObject(this.xOid, this.standInX);
							this.registry.registerObject(this.lOid, this.standInL);
						}
						this.proceed.countDown();
					}
					else
					{
						// releasing on timeout keeps the storage shutdownable
						this.proceed.await(20, TimeUnit.SECONDS);
					}
				}
				catch(final InterruptedException e)
				{
					Thread.currentThread().interrupt();
				}
			}
			return this.delegate.processSelected(processor);
		}
	}


	///////////////////////////////////////////////////////////////////////////
	// test //
	/////////

	@TempDir
	Path tempDir;

	EmbeddedStorageManager storage ;
	EmbeddedStorageManager reloaded;

	@AfterEach
	public void afterTest()
	{
		StorageEntityCache.Default.setGarbageCollectionEnabled(true);

		for(final EmbeddedStorageManager manager : new EmbeddedStorageManager[]{this.reloaded, this.storage})
		{
			if(manager != null && manager.isRunning())
			{
				try
				{
					manager.shutdown();
				}
				catch(final Exception ignored)
				{
					// best effort
				}
			}
		}
	}

	@Test
	void sweepEntryRegistrationDoesNotLoseTheCrossChannelLazyTarget() throws Exception
	{
		final Outcome outcome = this.run(true);

		// the fixed engine defers the stale sweep (keep-all) and re-marks X -> L -> Y before deleting:
		// X survives AND its transitively referenced Y survives - no partial sweep, no zombie.
		assertTrue(outcome.parentLoadable, "X must survive the sweep (registry safety net)");
		assertNotNull(outcome.payloadData,
			"Y must survive too: the deferred sweep + re-seed marks L's binary target");
		assertEquals("sweep-entry race victim", outcome.payloadData);
		assertFalse(outcome.zombieOids.contains(outcome.payloadOid),
			"no zombie OID may be reported for Y, but got: " + outcome.zombieOids);
	}

	@Test
	void controlRunCollectsWholeOrphanGraphConsistently() throws Exception
	{
		final Outcome outcome = this.run(false);

		// without the mid-wave registration the whole orphan graph is legitimately unreachable and
		// must be collected consistently - no partial survival. The load of X by its retained OID
		// fails with the expected consistency exception (the channel logs that failure at ERROR level
		// before the test observes it - that log line is part of the expected control-run output).
		assertFalse(outcome.parentLoadable, "X must have been swept consistently");
		assertNotNull(outcome.parentLoadFailure, "the load of the swept X must have failed");
		assertTrue(outcome.zombieOids.isEmpty(),
			"no zombie OIDs expected in the control run, but got: " + outcome.zombieOids);
	}

	static final class Outcome
	{
		boolean    parentLoadable   ;
		Throwable  parentLoadFailure;
		String     payloadData      ;
		long       payloadOid       ;
		List<Long> zombieOids       ;
	}

	private static <T extends Throwable> T findInCauseChain(final Throwable root, final Class<T> type)
	{
		for(Throwable t = root; t != null; t = t.getCause())
		{
			if(type.isInstance(t))
			{
				return type.cast(t);
			}
			if(t.getCause() == t)
			{
				break;
			}
		}
		return null;
	}

	private Outcome run(final boolean simulateMidWaveRegistration) throws Exception
	{
		final Path workDir = this.tempDir.resolve(simulateMidWaveRegistration ? "race" : "control");

		final SweepEntryRegisterTrap   trap          = new SweepEntryRegisterTrap(simulateMidWaveRegistration);
		final CountingZombieOidHandler zombieHandler = new CountingZombieOidHandler();

		// Halt GC during setup so no seed/sweep runs before the trap is armed.
		StorageEntityCache.Default.setGarbageCollectionEnabled(false);

		this.storage = EmbeddedStorage.Foundation(
				Storage.ConfigurationBuilder()
					.setChannelCountProvider(Storage.ChannelCountProvider(CHANNEL_COUNT))
					.setHousekeepingController(Storage.HousekeepingController(3_600_000, 1_000_000))
					.setDataFileEvaluator(Storage.DataFileEvaluator(1024, 2048, 1.0))
					.setStorageFileProvider(Storage.FileProvider(workDir))
					.createConfiguration()
			)
			.setGCZombieOidHandler(zombieHandler)
			.onConnectionFoundation(cf -> cf.setObjectRegistryCallback(trap))
			.start();

		final PersistenceObjectRegistry registry = this.storage.persistenceManager().objectRegistry();

		// ---------------------------------------------------------------
		// Phase 1 - store root -> X(Parent) -> L(Lazy) -> Y(Payload)
		// ---------------------------------------------------------------
		final DataRoot root = new DataRoot();
		Parent  parent  = new Parent();
		Payload payload = new Payload("sweep-entry race victim");
		parent.child = Lazy.Reference(payload);
		Lazy<Payload> lazyInstance = parent.child;
		root.ref = parent;

		this.storage.setRoot(root);
		this.storage.storeRoot();

		final long xOid = registry.lookupObjectId(parent);
		final long lOid = registry.lookupObjectId(lazyInstance);
		final long yOid = registry.lookupObjectId(payload);
		Assumptions.assumeTrue(xOid > 0 && lOid > 0 && yOid > 0,
			"could not resolve the OIDs of the stored graph");

		final WeakReference<Parent>  parentProbe  = new WeakReference<>(parent);
		final WeakReference<Lazy<?>> lazyProbe    = new WeakReference<>(lazyInstance);
		final WeakReference<Payload> payloadProbe = new WeakReference<>(payload);

		// ---------------------------------------------------------------
		// Phase 2 - detach X from root on disk
		// ---------------------------------------------------------------
		root.ref = null;
		this.storage.storeRoot();

		// ---------------------------------------------------------------
		// Phase 3a - consume the store-time gray marks of X/L/Y while the
		// instances are still registry-protected (two full GC completions).
		// ---------------------------------------------------------------
		StorageEntityCache.Default.setGarbageCollectionEnabled(true);
		this.storage.issueFullGarbageCollection();
		this.storage.issueFullGarbageCollection();
		StorageEntityCache.Default.setGarbageCollectionEnabled(false);

		// ---------------------------------------------------------------
		// Phase 3b - drop the Java instances and let the JVM collect them
		// ---------------------------------------------------------------
		parent       = null;
		lazyInstance = null;
		payload      = null;

		for(int i = 0; i < 50
			&& (parentProbe.get() != null || lazyProbe.get() != null || payloadProbe.get() != null); i++)
		{
			System.gc();
			Thread.sleep(100);
		}
		Assumptions.assumeTrue(
			parentProbe.get() == null && lazyProbe.get() == null && payloadProbe.get() == null,
			"JVM did not garbage-collect the probe instances - cannot proceed deterministically"
		);

		// ---------------------------------------------------------------
		// Phase 4 - reap the registry entries; prime the trap (which injects
		// during Phase 5's sweep iff this is the race run)
		// ---------------------------------------------------------------
		registry.cleanUp();
		trap.xOid = xOid;
		trap.lOid = lOid;

		// ---------------------------------------------------------------
		// Phase 5 - run a full GC. The trap injects the registration on the first
		// sweep of the wave (after initiation, before any delete loop). Fixed engine:
		// every channel defers (keep-all), then the cold cycle marks X -> L -> Y and
		// keeps them. Pre-fix: X/L are kept (registered), Y is swept -> zombie.
		// ---------------------------------------------------------------
		trap.armed = true; // engage the barrier only for this wave (setup GCs above must pass through)
		StorageEntityCache.Default.setGarbageCollectionEnabled(true);
		this.storage.issueFullGarbageCollection();
		this.storage.issueFullFileCheck(); // make deletions physically durable across the restart

		// keep the stand-ins strongly referenced until here (the trap holds them)
		assertNotNull(trap.standInX);
		assertNotNull(trap.standInL);

		// ---------------------------------------------------------------
		// Phase 6 - freeze, restart, check the persisted graph
		// ---------------------------------------------------------------
		StorageEntityCache.Default.setGarbageCollectionEnabled(false);
		this.storage.shutdown();

		final Outcome outcome = new Outcome();
		outcome.payloadOid = yOid;
		outcome.zombieOids = new ArrayList<>(zombieHandler.zombieOids);

		this.reloaded = EmbeddedStorage.Foundation(
				Storage.ConfigurationBuilder()
					.setChannelCountProvider(Storage.ChannelCountProvider(CHANNEL_COUNT))
					.setStorageFileProvider(Storage.FileProvider(workDir))
					.createConfiguration()
			)
			.start();
		try
		{
			final Object x;
			try
			{
				x = this.reloaded.persistenceManager().getObject(xOid);
			}
			catch(final Exception e)
			{
				// only the precise "X itself is gone" failure counts as consistent whole-graph
				// collection; anything else is a genuine defect and must fail the test.
				final StorageExceptionConsistency consistency =
					findInCauseChain(e, StorageExceptionConsistency.class);
				if(consistency == null
					|| !String.valueOf(consistency.getMessage()).contains("No entity found for objectId " + xOid)
				)
				{
					throw e;
				}
				outcome.parentLoadable    = false;
				outcome.parentLoadFailure = consistency;
				return outcome;
			}

			outcome.parentLoadable = true;
			final Parent reloadedParent = (Parent)x;
			assertNotNull(reloadedParent.child, "X survived but its Lazy L is null");

			// the decisive call: pre-fix this threw "No entity found for objectId Y".
			final Payload reloadedPayload = reloadedParent.child.get();
			outcome.payloadData = reloadedPayload == null ? null : reloadedPayload.data;
			return outcome;
		}
		finally
		{
			this.reloaded.shutdown();
			this.reloaded = null;
		}
	}
}
