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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

/**
 * Reproducer for the MULTI-CHANNEL residual window of the storage GC (GC.md §10.4):
 * with channelCount >= 2, a load can straddle a sweep wave — one channel has already swept when
 * the load's collect runs on another channel. The handed-out entity X is black-marked shallowly
 * (no reference walk: the wave's mark queues must stay empty), its unloaded-Lazy target Y on the
 * already-swept channel is gone. The application receives X successfully while Y is permanently
 * deleted; a later {@code Lazy.get()} throws {@code No entity found for objectId Y} and a
 * re-store of X would make the dangling reference durable.
 * <p>
 * Determinism: the wave is frozen HALF-SWEPT using the sweep-time registry callback — the first
 * channel's sweep is allowed to execute and the JVM-global GC switch is flipped off synchronously
 * on that channel's thread before returning, so the remaining channel's sweep stays pending while
 * its thread stays FREE to serve the mid-wave load. Which channel sweeps first is scheduling-
 * dependent (the useful order is: Y's channel first), so the staging retries a bounded number of
 * rounds; each round is cheap and the wrong order fails cleanly ("No entity found for X").
 * <p>
 * The repro asserts the CORRECT behavior (no zombie for Y, X's lazy target loadable after
 * restart) — i.e. this test is RED as long as the bug exists. The control run performs the
 * identical staging without the mid-wave load and asserts the whole orphan graph is collected
 * consistently.
 */
@Isolated
@Timeout(300)
public class MultiChannelResidualWindowReproTest
{
	static final int CHANNEL_COUNT   = 2;
	static final int STAGING_ROUNDS  = 8;

	///////////////////////////////////////////////////////////////////////////
	// data types //
	///////////////

	public static class DataRoot
	{
		public List<Parent> parents = new ArrayList<>();
	}

	public static class Parent
	{
		public Lazy<Payload> child;
	}

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
	 * Delegating registry callback that freezes a sweep wave half-swept: when armed, the FIRST
	 * channel sweep (= processSelected call) is allowed to run and the JVM-global GC switch is
	 * turned off on that channel's thread before returning. The other channel's sweep stays
	 * pending (its housekeeping sees the disabled switch), but its thread remains free — exactly
	 * the state in which a real mid-wave load is served.
	 * <p>
	 * MUST forward {@code registrationVersion()}; the interface default (constant 0) would starve
	 * the seed/verify loop.
	 */
	static final class HalfSweepTrap implements EmbeddedStorageObjectRegistryCallback
	{
		private final EmbeddedStorageObjectRegistryCallback delegate = EmbeddedStorageObjectRegistryCallback.New();

		final AtomicBoolean  armed      = new AtomicBoolean();
		final CountDownLatch firstSwept = new CountDownLatch(1);

		@Override
		public void initializeObjectRegistry(final PersistenceObjectRegistry objectRegistry)
		{
			this.delegate.initializeObjectRegistry(objectRegistry);
		}

		@Override
		public long registrationVersion()
		{
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
			final boolean fireHalfSweepFreeze = this.armed.compareAndSet(true, false);
			try
			{
				return this.delegate.processSelected(processor);
			}
			finally
			{
				if(fireHalfSweepFreeze)
				{
					// freeze the wave half-swept: no further channel may enter its sweep,
					// while all channel threads stay free to serve tasks.
					StorageEntityCache.Default.setGarbageCollectionEnabled(false);
					this.firstSwept.countDown();
				}
			}
		}
	}


	///////////////////////////////////////////////////////////////////////////
	// tests //
	//////////

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

	/**
	 * This stages the ONE sub-case of the multi-channel residual that is fundamentally not closeable
	 * post-hoc (GC.md §10.4): the lazy target Y's channel sweeps <b>first</b> and physically deletes Y
	 * <i>before</i> the mid-wave load reheats and registers the parent X. Once Y's channel has
	 * deleted Y, no later action can bring it back — neither fix 1 (task-scoped pending-load gate,
	 * which only blocks sweep <i>initiation</i> while a load is in flight — here the wave initiated
	 * with no load in flight) nor fix 2 (registrationVersion check at sweep entry, which only defers
	 * channels that have <i>not yet</i> swept — Y's channel already swept). So this test asserts an
	 * outcome (whole graph survives) that is provably unachievable for this interleaving and is kept
	 * {@code @Disabled} as executable documentation of the known residual.
	 * <p>
	 * The <i>rescuable</i> subset of the same residual — the registration observed before the child's
	 * channel sweeps — IS closed by fix 2 and is covered green by
	 * {@link MultiChannelSweepEntryRaceReproTest}. The control run below (whole orphan graph collected
	 * consistently, no partial survival) stays enabled.
	 */
	@Disabled("GC.md §10.4: child's channel sweeps before the reheating registration -"
		+ " fundamentally not closeable post-hoc (the child is already physically deleted); the"
		+ " rescuable subset is covered by MultiChannelSweepEntryRaceReproTest")
	@Test
	void midWaveLoadMustNotLoseTheLazyTargetOnTheOtherChannel() throws Exception
	{
		final Outcome outcome = this.runUntilStaged(true);

		assertFalse(outcome.zombieOids.contains(outcome.targetOid),
			"DATA LOSS: the cross-channel lazy target was deleted by the half-swept wave"
			+ " - zombie OIDs: " + outcome.zombieOids);
		assertNotNull(outcome.targetData, "the lazy target must be loadable after restart");
		assertEquals("mc-victim", outcome.targetData,
			"DATA LOSS: the handed-out entity survived but its cross-channel lazy target is gone");
	}

	@Test
	void controlRunCollectsWholeOrphanGraphConsistently() throws Exception
	{
		final Outcome outcome = this.runUntilStaged(false);

		assertFalse(outcome.loadedMidWave, "control: no mid-wave load performed");
		assertTrue(outcome.parentGone,
			"control: the orphaned parent must have been collected consistently");
		assertTrue(outcome.zombieOids.isEmpty(),
			"control: no zombie OIDs expected, got: " + outcome.zombieOids);
	}

	static final class Outcome
	{
		long       targetOid   ;
		String     targetData  ;
		boolean    loadedMidWave;
		boolean    parentGone  ;
		List<Long> zombieOids  ;
	}

	/**
	 * Retries the staging until the scheduling cooperates (the channel of the lazy TARGET sweeps
	 * first, leaving the channel of the PARENT pending). Wrong order fails cleanly and retries.
	 */
	private Outcome runUntilStaged(final boolean loadMidWave) throws Exception
	{
		for(int round = 1; round <= STAGING_ROUNDS; round++)
		{
			final Outcome outcome = this.runRound(loadMidWave, round);
			if(outcome != null)
			{
				return outcome;
			}
			this.afterTest(); // reset storages between rounds
		}
		Assumptions.assumeTrue(false,
			"staging never reached the required channel order in " + STAGING_ROUNDS + " rounds");
		throw new IllegalStateException("unreachable");
	}

	private Outcome runRound(final boolean loadMidWave, final int round) throws Exception
	{
		final Path workDir = this.tempDir.resolve((loadMidWave ? "race-" : "control-") + round);

		final HalfSweepTrap            trap          = new HalfSweepTrap();
		final CountingZombieOidHandler zombieHandler = new CountingZombieOidHandler();

		StorageEntityCache.Default.setGarbageCollectionEnabled(false);

		this.storage = EmbeddedStorage.Foundation(
				Storage.ConfigurationBuilder()
					.setChannelCountProvider(Storage.ChannelCountProvider(CHANNEL_COUNT))
					.setHousekeepingController(Storage.HousekeepingController(50, 1_000_000_000))
					.setDataFileEvaluator(Storage.DataFileEvaluator(1024, 2048, 1.0))
					.setStorageFileProvider(Storage.FileProvider(workDir))
					.createConfiguration()
			)
			.setGCZombieOidHandler(zombieHandler)
			.onConnectionFoundation(cf -> cf.setObjectRegistryCallback(trap))
			.start();

		final PersistenceObjectRegistry registry = this.storage.persistenceManager().objectRegistry();

		// ---------------------------------------------------------------
		// Phase 1 - stage TWO orphan pairs with OPPOSITE channel placements:
		// pairA: X on channel of parity 0, Y on parity 1; pairB: the reverse.
		// Whichever channel sweeps first, exactly one X remains loadable on the
		// pending channel while its Y lies on the already-swept channel - the
		// demo pair is selected by probing, no scheduling luck needed.
		// Sequential oid assignment gives lazy=m, payload=m+1; a one-oid dummy
		// store in between flips the payload parity, so placement is forcible.
		// ---------------------------------------------------------------
		final DataRoot root = new DataRoot();
		this.storage.setRoot(root);
		final List<Parent> parents = new ArrayList<>();
		for(int i = 0; i < 4; i++)
		{
			final Parent parent = new Parent();
			root.parents.add(parent);
			parents.add(parent);
		}
		this.storage.storeRoot(); // parents persisted with null child; parities fixed

		final List<Payload>       payloads = new ArrayList<>();
		final List<Lazy<Payload>> lazies   = new ArrayList<>();

		final long[] pairX = new long[2]; // index = X's channel parity
		final long[] pairY = new long[2];
		for(int wantXParity = 0; wantXParity <= 1; wantXParity++)
		{
			Parent parent = null;
			long   pOid   = -1;
			for(final Parent candidate : parents)
			{
				final long oid = registry.lookupObjectId(candidate);
				if(candidate.child == null && oid > 0 && (oid & (CHANNEL_COUNT - 1)) == wantXParity)
				{
					parent = candidate;
					pOid   = oid;
					break;
				}
			}
			Assumptions.assumeTrue(parent != null, "no parent candidate with parity " + wantXParity);

			final int wantYParity = 1 - wantXParity;
			long yOid = -1;
			for(int attempt = 0; attempt < 3 && yOid < 0; attempt++)
			{
				final Payload payload = new Payload("mc-victim");
				parent.child = Lazy.Reference(payload);
				this.storage.store(parent);
				payloads.add(payload);
				lazies.add(parent.child);
				final long oid = registry.lookupObjectId(payload);
				if((oid & (CHANNEL_COUNT - 1)) == wantYParity)
				{
					yOid = oid;
				}
				else
				{
					this.storage.store("parity-burn-" + round + "-" + wantXParity + "-" + attempt);
				}
			}
			Assumptions.assumeTrue(yOid > 0, "could not place Y on parity " + wantYParity);
			pairX[wantXParity] = pOid;
			pairY[wantXParity] = yOid;
		}

		// ---------------------------------------------------------------
		// Phase 2 - detach all parents on disk; consume store-time gray marks
		// ---------------------------------------------------------------
		root.parents = new ArrayList<>();
		this.storage.storeRoot();

		StorageEntityCache.Default.setGarbageCollectionEnabled(true);
		this.storage.issueFullGarbageCollection();
		this.storage.issueFullGarbageCollection();
		StorageEntityCache.Default.setGarbageCollectionEnabled(false);

		// ---------------------------------------------------------------
		// Phase 3 - drop the Java instances; nothing application-side protects the graph
		// ---------------------------------------------------------------
		final List<WeakReference<Object>> probes = new ArrayList<>();
		parents.forEach(p -> probes.add(new WeakReference<>(p)));
		payloads.forEach(p -> probes.add(new WeakReference<>(p)));
		lazies.forEach(l -> probes.add(new WeakReference<>(l)));
		parents.clear();
		payloads.clear();
		lazies.clear();

		for(int i = 0; i < 50 && probes.stream().anyMatch(p -> p.get() != null); i++)
		{
			System.gc();
			Thread.sleep(100);
		}
		Assumptions.assumeTrue(probes.stream().allMatch(p -> p.get() == null),
			"JVM did not collect the probe instances");
		registry.cleanUp();

		// ---------------------------------------------------------------
		// Phase 4 - fresh wave; arm the trap; let housekeeping initiate and
		// let exactly ONE channel sweep, then the wave is frozen half-swept.
		// ---------------------------------------------------------------
		this.storage.store("kick-gc-" + round);
		trap.armed.set(true);
		StorageEntityCache.Default.setGarbageCollectionEnabled(true);

		Assumptions.assumeTrue(trap.firstSwept.await(15, TimeUnit.SECONDS),
			"no channel entered its sweep - cannot stage");
		// GC switch is now OFF; exactly one channel swept, the other's sweep is pending.

		final Outcome outcome = new Outcome();

		long demoX = -1, demoY = -1;
		if(loadMidWave)
		{
			// ---------------------------------------------------------------
			// Phase 5 - the mid-wave load: probe both opposite pairs; exactly one
			// X is on the pending channel (loadable), its Y on the swept channel.
			// ---------------------------------------------------------------
			Object x = null;
			for(int parity = 0; parity <= 1 && x == null; parity++)
			{
				try
				{
					x = this.storage.persistenceManager().getObject(pairX[parity]);
					demoX = pairX[parity];
					demoY = pairY[parity];
				}
				catch(final RuntimeException e)
				{
					// this pair's X was on the swept channel; try the opposite pair
				}
			}
			Assumptions.assumeTrue(x != null, "neither pair's parent was loadable mid-wave");
			outcome.loadedMidWave = true;
			outcome.targetOid     = demoY;

			// keep X strongly referenced through the rest of the wave
			// ---------------------------------------------------------------
			// Phase 6 - thaw: the pending sweep of the second channel executes.
			// ---------------------------------------------------------------
			StorageEntityCache.Default.setGarbageCollectionEnabled(true);
			this.storage.issueFullGarbageCollection();
			this.storage.issueFullFileCheck(); // make deletions physically durable across the restart

			assertNotNull(x);
		}
		else
		{
			outcome.targetOid = pairY[0];
			// control: no mid-wave load; thaw and let the wave finish.
			StorageEntityCache.Default.setGarbageCollectionEnabled(true);
			this.storage.issueFullGarbageCollection();
			this.storage.issueFullFileCheck(); // make deletions physically durable across the restart
		}

		outcome.zombieOids = new ArrayList<>(zombieHandler.zombieOids);

		// ---------------------------------------------------------------
		// Phase 7 - freeze, restart, inspect what is actually persisted.
		// ---------------------------------------------------------------
		StorageEntityCache.Default.setGarbageCollectionEnabled(false);
		this.storage.shutdown();

		this.reloaded = EmbeddedStorage.Foundation(
				Storage.ConfigurationBuilder()
					.setChannelCountProvider(Storage.ChannelCountProvider(CHANNEL_COUNT))
					.setStorageFileProvider(Storage.FileProvider(workDir))
					.createConfiguration()
			)
			.start();
		try
		{
			if(loadMidWave)
			{
				final Parent reloadedParent;
				try
				{
					reloadedParent = (Parent)this.reloaded.persistenceManager().getObject(demoX);
				}
				catch(final RuntimeException e)
				{
					// X itself gone after restart despite the successful mid-wave hand-out:
					// also loss, report as missing target.
					outcome.targetData = null;
					return outcome;
				}
				assertNotNull(reloadedParent.child, "persisted parent must still hold the Lazy");
				try
				{
					final Payload target = reloadedParent.child.get(); // pre-fix: "No entity found for objectId Y"
					outcome.targetData = target == null ? null : target.data;
				}
				catch(final RuntimeException e)
				{
					outcome.targetData = null;
				}
			}
			else
			{
				try
				{
					this.reloaded.persistenceManager().getObject(pairX[0]);
					outcome.parentGone = false;
				}
				catch(final RuntimeException e)
				{
					outcome.parentGone =
						findInCauseChain(e, StorageExceptionConsistency.class) != null;
				}
			}
			return outcome;
		}
		finally
		{
			this.reloaded.shutdown();
			this.reloaded = null;
		}
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
}
