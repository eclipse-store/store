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

import org.eclipse.serializer.persistence.types.PersistenceObjectIdAcceptor;
import org.eclipse.serializer.persistence.types.PersistenceObjectRegistry;
import org.eclipse.serializer.persistence.types.ObjectIdsProcessor;
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
 * Regression test for the <b>mid-cycle registration race</b> (found 2026-07-03): the live-OID
 * mark seed used to be armed once per GC cycle and never re-armed when the application registered
 * an object into the {@link PersistenceObjectRegistry} <i>after</i> the seed ran but <i>before</i>
 * the cycle's sweep. A load of an orphaned parent X (with an unloaded Lazy L whose target Y is
 * never built) landing in that window let the shallow id-only sweep predicate rescue X+L while
 * sweeping Y — leaving L's persisted binary dangling and the next reload throwing
 * {@code StorageExceptionConsistency: No entity found for objectId Y}.
 * <p>
 * The fix (GC.md §10.4) snapshots the registry's registration version at every live-OID seed and
 * re-arms the seed before initiating a sweep if the version moved, so mid-cycle registrations get
 * their references walked transitively before anything is deleted.
 * <p>
 * The racy timing is made deterministic: a delegating registry callback ({@link SeedTrap}) blocks
 * the GC (channel thread) right after the first seed that no longer contains X's OID; the main
 * thread then performs the registrations a real {@code getObject(xOid)} load would perform, and
 * releases the GC. A control run performs the identical steps without the mid-cycle registration
 * and asserts the whole orphan graph is collected consistently.
 * <p>
 * {@code @Isolated} + flag restoration because {@link StorageEntityCache.Default#setGarbageCollectionEnabled}
 * is a static, JVM-global switch.
 */
@Isolated
public class MidCycleRegistrationRaceTest
{
	///////////////////////////////////////////////////////////////////////////
	// data types //
	///////////////

	public static class DataRoot
	{
		public Object ref;
	}

	/**
	 * X — survives the sweep via the id-only registry predicate.
	 */
	public static class Parent
	{
		public Lazy<Payload> child;
	}

	/**
	 * Y — the never-built lazy target that used to get swept.
	 */
	public static class Payload
	{
		public final String data;

		public Payload(final String data)
		{
			super();
			this.data = data;
		}
	}

	/**
	 * Counts zombie OIDs reported by the storage GC.
	 */
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
	 * Delegating registry callback that fires once on the first live-OID mark seed which no longer
	 * emits the watched OID, then blocks the GC (channel thread) until the main thread has simulated
	 * the mid-cycle load.
	 * <p>
	 * MUST forward {@code registrationVersion()} to the delegate: the interface default is a
	 * constant 0, which would pin the version and silently disable the very fix under test.
	 */
	static final class SeedTrap implements EmbeddedStorageObjectRegistryCallback
	{
		private final EmbeddedStorageObjectRegistryCallback delegate = EmbeddedStorageObjectRegistryCallback.New();

		volatile long           watchedOid ;
		final    AtomicBoolean  armed      = new AtomicBoolean();
		final    CountDownLatch seedMissed = new CountDownLatch(1);
		final    CountDownLatch proceed    = new CountDownLatch(1);

		@Override
		public void initializeObjectRegistry(final PersistenceObjectRegistry objectRegistry)
		{
			this.delegate.initializeObjectRegistry(objectRegistry);
		}

		@Override
		public boolean processSelected(final ObjectIdsProcessor processor)
		{
			return this.delegate.processSelected(processor);
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
			final long watched = this.watchedOid;
			final boolean[] sawWatched = {false};
			this.delegate.iterateLiveObjectIds(objectId ->
			{
				if(objectId == watched)
				{
					sawWatched[0] = true;
				}
				acceptor.acceptObjectId(objectId);
			});

			// Fire on the first seed that misses the watched OID: from this moment on,
			// a registration lands in the seed->sweep window of the current GC cycle.
			if(!sawWatched[0] && this.armed.compareAndSet(true, false))
			{
				this.seedMissed.countDown();
				try
				{
					if(!this.proceed.await(20, TimeUnit.SECONDS))
					{
						// releasing the GC on timeout keeps the storage shutdownable
					}
				}
				catch(final InterruptedException e)
				{
					Thread.currentThread().interrupt();
				}
			}
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
		// the static, JVM-global GC switch must never leak into other tests.
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
	void midCycleRegistrationDoesNotCausePartialSweep() throws Exception
	{
		final Outcome outcome = this.run(true);

		// the fixed engine re-seeds after the mid-cycle registration: X survives AND its
		// transitively referenced Y survives — no partial sweep, no zombie, graph loadable.
		assertTrue(outcome.parentLoadable, "X must survive the sweep (registry safety net)");
		assertNotNull(outcome.payloadData, "Y must survive too: the re-seeded mark walks L's binary");
		assertEquals("mid-cycle race victim", outcome.payloadData);
		assertFalse(outcome.zombieOids.contains(outcome.payloadOid),
			"no zombie OID may be reported for Y, but got: " + outcome.zombieOids);
	}

	@Test
	void controlRunCollectsWholeOrphanGraphConsistently() throws Exception
	{
		final Outcome outcome = this.run(false);

		// without the mid-cycle registration the whole orphan graph is legitimately
		// unreachable and must be collected consistently — no partial survival. The load
		// of X by its retained OID fails with the expected consistency exception (the
		// channel logs that failure at ERROR level before the test observes it — that
		// log line is part of the expected control-run output, not a defect).
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
		Throwable slow = root, fast = root;
		while(fast != null)
		{
			if(type.isInstance(fast))
			{
				return type.cast(fast);
			}
			fast = fast.getCause();
			if(type.isInstance(fast)) // isInstance(null) is false
			{
				return type.cast(fast);
			}
			fast = fast == null ? null : fast.getCause();
			slow = slow.getCause();
			if(fast != null && fast == slow)
			{
				break; // cause cycle
			}
		}
		return null;
	}

	private Outcome run(final boolean simulateMidCycleLoad) throws Exception
	{
		final Path workDir = this.tempDir.resolve(simulateMidCycleLoad ? "race" : "control");

		final SeedTrap                 trap          = new SeedTrap();
		final CountingZombieOidHandler zombieHandler = new CountingZombieOidHandler();

		// Halt GC during setup so no seed/sweep runs before the trap is armed.
		StorageEntityCache.Default.setGarbageCollectionEnabled(false);

		this.storage = EmbeddedStorage.Foundation(
				Storage.ConfigurationBuilder()
					.setChannelCountProvider(Storage.ChannelCountProvider(1))
					.setHousekeepingController(Storage.HousekeepingController(100, 1_000_000_000))
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
		Payload payload = new Payload("mid-cycle race victim");
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
		// Phase 3a - consume the store-time gray marks of X/L/Y: run one full
		// GC completion while the instances are still registry-protected.
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

		for(int i = 0; i < 50 && (parentProbe.get() != null || lazyProbe.get() != null || payloadProbe.get() != null); i++)
		{
			System.gc();
			Thread.sleep(100);
		}
		Assumptions.assumeTrue(
			parentProbe.get() == null && lazyProbe.get() == null && payloadProbe.get() == null,
			"JVM did not garbage-collect the probe instances - cannot proceed deterministically"
		);

		// ---------------------------------------------------------------
		// Phase 4 - arm the trap, reap the registry entries, kick + re-enable GC
		// ---------------------------------------------------------------
		trap.watchedOid = xOid;
		trap.armed.set(true);
		registry.cleanUp();

		// GC is cold-complete after Phase 3a; a store resets the completion
		// state so a fresh cycle (with a fresh seed) runs once GC is enabled.
		this.storage.store("kick-gc");

		StorageEntityCache.Default.setGarbageCollectionEnabled(true);

		// ---------------------------------------------------------------
		// Phase 5 - wait for the seed that misses X, then register mid-cycle
		// ---------------------------------------------------------------
		Assumptions.assumeTrue(trap.seedMissed.await(15, TimeUnit.SECONDS),
			"seed trap never fired - cannot exercise the race window");

		// Strong stand-ins must outlive the GC cycles below.
		final Object standInX = new Object();
		final Object standInL = new Object();
		if(simulateMidCycleLoad)
		{
			// What a real persistenceManager().getObject(xOid) load does to the registry at
			// exactly this point: register X's instance and its eagerly built - but unloaded -
			// Lazy instance L. Y is NOT registered: an unloaded Lazy never builds its target.
			registry.registerObject(xOid, standInX);
			registry.registerObject(lOid, standInL);
		}
		trap.proceed.countDown();

		// ---------------------------------------------------------------
		// Phase 6 - let the (re-seeded) mark and the sweep run to quiescence
		// ---------------------------------------------------------------
		/*
		 * The wait loop is load-bearing for the DEFECT direction: the race needs the CURRENT
		 * housekeeping cycle - whose seed missed X - to finish its mark and sweep naturally.
		 * Driving GC explicitly here instead would start a FRESH cycle whose new seed includes
		 * the just-registered X and L, masking the bug. The loop exits early once a zombie
		 * surfaces (pre-fix outcome) or after the generous bound (fixed outcome).
		 */
		for(int i = 0; i < 30 && zombieHandler.zombieOids.isEmpty(); i++)
		{
			Thread.sleep(100);
		}

		/*
		 * Deterministic quiescence AFTER the natural window has played out: a full issued GC
		 * runs synchronously to cold completion (at least one full mark+sweep). This guarantees
		 * the frozen-state checks below never pass vacuously because no sweep happened yet on a
		 * slow machine - in the fixed case the graph must survive an actually-completed GC, in
		 * the control case the orphan graph must actually be gone.
		 */
		this.storage.issueFullGarbageCollection();

		// keep the stand-ins strongly referenced until here
		assertNotNull(standInX);
		assertNotNull(standInL);

		// ---------------------------------------------------------------
		// Phase 7 - freeze, restart, check the persisted graph
		// ---------------------------------------------------------------
		StorageEntityCache.Default.setGarbageCollectionEnabled(false);
		this.storage.shutdown();

		final Outcome outcome = new Outcome();
		outcome.payloadOid = yOid;
		outcome.zombieOids = new ArrayList<>(zombieHandler.zombieOids);

		this.reloaded = EmbeddedStorage.Foundation(
				Storage.ConfigurationBuilder()
					.setChannelCountProvider(Storage.ChannelCountProvider(1))
					.setStorageFileProvider(Storage.FileProvider(workDir))
					.createConfiguration()
			)
			.start();
		try
		{
			final Object x;
			try
			{
				// NOTE: if X was swept (control run), the load task fails and the channel logs
				// the StorageExceptionConsistency at ERROR level - expected output there.
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
		}
	}
}
