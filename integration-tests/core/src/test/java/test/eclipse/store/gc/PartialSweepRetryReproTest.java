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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.ref.WeakReference;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.serializer.collections.Set_long;
import org.eclipse.serializer.functional._longPredicate;
import org.eclipse.serializer.persistence.types.ObjectIdsProcessor;
import org.eclipse.serializer.persistence.types.PersistenceObjectIdAcceptor;
import org.eclipse.serializer.persistence.types.PersistenceObjectRegistry;
import org.eclipse.serializer.reference.Lazy;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageObjectRegistryCallback;
import org.eclipse.store.storage.types.Storage;
import org.eclipse.store.storage.types.StorageEntityCache;
import org.eclipse.store.storage.types.StorageGCZombieOidHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

/**
 * Reproducer for the <b>partial-sweep re-execution hazard</b>: the sweep whitens
 * surviving (marked) entities AS IT GOES ({@code StorageEntityCache.Default.sweep}, whiten-as-you-go)
 * and clears the channel's sweep flag only at the very end. A TRANSIENT exception thrown partway
 * through the sweep (e.g. by a custom {@code LiveObjectIdsHandler} selector, or an OOM) leaves the
 * sweep flagged while the first K reachable entities are already white. Any re-execution of that
 * flagged sweep - housekeeping tick, import quiesce, or (since the task-chain repair) the next
 * issued GC - then deletes those K entities unless they happen to be application-registered:
 * reachable entities gone, dangling references on disk. Permanent data loss.
 * <p>
 * Determinism: the transient failure is injected through the sweep-time application-registry
 * predicate. The predicate is consulted ONLY for white (unmarked) entities - the marked ones are
 * whitened without a predicate call - so arming the trap guarantees the throw happens exactly
 * when the sweep reaches the planted white orphan W, strictly AFTER the reachable victim R
 * (same entity type, registered into the type chain before W) has already been whitened.
 * <p>
 * The victim R sits behind a Lazy whose Java instance is collected before the race: the mark
 * phase reaches R transitively through persisted binary references (root -> L -> R), so R is
 * properly marked, yet at re-execution time nothing application-side protects it.
 * <p>
 * The repro asserts the CORRECT behavior (no zombie for R, R loadable after restart), i.e. this
 * test is RED as long as the bug exists - the sweep-retry loss shows up as a zombie OID for R
 * during the second issued GC's marking and as a missing entity after restart.
 * <p>
 * {@code @Isolated} + flag restoration because {@code setGarbageCollectionEnabled} is a static,
 * JVM-global switch.
 */
@Isolated
@Timeout(120)
public class PartialSweepRetryReproTest
{
	///////////////////////////////////////////////////////////////////////////
	// data types //
	///////////////

	public static class DataRoot
	{
		public Lazy<Payload> keeper; // -> R, the reachable victim
		public Payload       orphan; // -> W, detached later: the white item that triggers the throw
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
	 * Delegating registry callback that injects a ONE-SHOT transient failure into the sweep-time
	 * live-object-ids predicate. The predicate is consulted only for white entities, so the first
	 * consult after arming is the sweep reaching the planted orphan W - by then every marked
	 * entity iterated before W (including the victim R) has already been whitened.
	 * <p>
	 * MUST forward {@code registrationVersion()}: the interface default is a constant 0, which
	 * would starve the seed/verify loop of version movement it expects.
	 */
	static final class SweepTrap implements EmbeddedStorageObjectRegistryCallback
	{
		private final EmbeddedStorageObjectRegistryCallback delegate = EmbeddedStorageObjectRegistryCallback.New();

		final AtomicBoolean armed      = new AtomicBoolean();
		final AtomicInteger throwCount = new AtomicInteger();

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
			// wrap the engine's processor so the registry's real predicate gets a throwing shell.
			return this.delegate.processSelected(new ObjectIdsProcessor()
			{
				@Override
				public void processObjectIdsByFilter(final _longPredicate registryPredicate)
				{
					processor.processObjectIdsByFilter(objectId ->
					{
						if(SweepTrap.this.armed.compareAndSet(true, false))
						{
							SweepTrap.this.throwCount.incrementAndGet();
							throw new RuntimeException(
								"transient sweep-selector failure (planted by PartialSweepRetryReproTest)"
							);
						}
						return registryPredicate.test(objectId);
					});
				}

				@Override
				public Set_long provideObjectIdsBaseSet()
				{
					return processor.provideObjectIdsBaseSet();
				}
			});
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

	@Test
	void transientMidSweepFailureMustNotLoseReachableEntities() throws Exception
	{
		final Outcome outcome = this.run(true);

		// primary: the reachable graph must be fully intact after restart.
		assertEquals("keep", outcome.victimData,
			"DATA LOSS: reachable data whitened by the failed sweep attempt was deleted by the"
			+ " re-executed sweep (partial-sweep retry) - zombie OIDs: " + outcome.zombieOids
			+ (outcome.restartFailure == null ? "" : ", restart failed: " + outcome.restartFailure));
		// secondary: the surviving cycle must not have encountered any zombie ids either.
		assertTrue(outcome.zombieOids.isEmpty(),
			"no zombie OIDs may be reported, got: " + outcome.zombieOids);
	}

	@Test
	void controlRunWithoutFailureCollectsOnlyTheOrphan() throws Exception
	{
		final Outcome outcome = this.run(false);

		assertTrue(outcome.zombieOids.isEmpty(), "no zombies expected, got: " + outcome.zombieOids);
		assertEquals("keep", outcome.victimData, "the reachable victim must survive untouched");
		assertTrue(outcome.orphanCollected, "the detached orphan must have been collected");
	}

	static final class Outcome
	{
		long             victimOid      ;
		String           victimData     ;
		boolean          orphanCollected;
		List<Long>       zombieOids     ;
		RuntimeException restartFailure ;
	}

	private Outcome run(final boolean injectTransientSweepFailure) throws Exception
	{
		final Path workDir = this.tempDir.resolve(injectTransientSweepFailure ? "race" : "control");

		final SweepTrap                trap          = new SweepTrap();
		final CountingZombieOidHandler zombieHandler = new CountingZombieOidHandler();

		// freeze the storage GC during staging; huge housekeeping interval keeps the cycle under
		// the test's control (issued GCs only), aggressive data file evaluator for physical reclaim.
		StorageEntityCache.Default.setGarbageCollectionEnabled(false);

		this.storage = EmbeddedStorage.Foundation(
				Storage.ConfigurationBuilder()
					.setChannelCountProvider(Storage.ChannelCountProvider(1))
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
		// Phase 1 - store root -> Lazy L -> R ("keep"); then orphan W in a SECOND
		// store so W registers into the Payload type chain strictly after R.
		// ---------------------------------------------------------------
		final DataRoot root = new DataRoot();
		Payload victim = new Payload("keep");
		root.keeper = Lazy.Reference(victim);
		Lazy<Payload> lazyInstance = root.keeper;

		this.storage.setRoot(root);
		this.storage.storeRoot();

		Payload orphan = new Payload("white trigger");
		root.orphan = orphan;
		this.storage.storeRoot();

		final long rOid = registry.lookupObjectId(victim);
		final long wOid = registry.lookupObjectId(orphan);
		final long lOid = registry.lookupObjectId(lazyInstance);
		Assumptions.assumeTrue(rOid > 0 && wOid > 0 && lOid > 0, "could not resolve the staged OIDs");

		// ---------------------------------------------------------------
		// Phase 2 - detach W on disk; R stays reachable via root -> L
		// ---------------------------------------------------------------
		root.orphan = null;
		this.storage.storeRoot();

		// ---------------------------------------------------------------
		// Phase 3a - consume store-time gray marks while everything is registry-protected
		// ---------------------------------------------------------------
		StorageEntityCache.Default.setGarbageCollectionEnabled(true);
		this.storage.issueFullGarbageCollection();
		this.storage.issueFullGarbageCollection();
		StorageEntityCache.Default.setGarbageCollectionEnabled(false);

		// ---------------------------------------------------------------
		// Phase 3b - drop the Java instances of R, W and L so nothing application-side
		// protects them; R stays reachable ONLY through persisted binary references.
		// ---------------------------------------------------------------
		final WeakReference<Payload> victimProbe = new WeakReference<>(victim);
		final WeakReference<Payload> orphanProbe = new WeakReference<>(orphan);
		final WeakReference<Lazy<?>> lazyProbe   = new WeakReference<>(lazyInstance);
		victim       = null;
		orphan       = null;
		lazyInstance = null;
		root.keeper  = null; // the persisted root record still references L; only the heap lets go

		for(int i = 0; i < 50
			&& (victimProbe.get() != null || orphanProbe.get() != null || lazyProbe.get() != null); i++)
		{
			System.gc();
			Thread.sleep(100);
		}
		Assumptions.assumeTrue(
			victimProbe.get() == null && orphanProbe.get() == null && lazyProbe.get() == null,
			"JVM did not collect the probe instances - cannot stage deterministically"
		);
		registry.cleanUp();

		// ---------------------------------------------------------------
		// Phase 4 - fresh cycle; arm the trap; the issued GC's hot sweep whitens the marked
		// entities up to W (including R) and then hits W -> predicate -> transient throw.
		// ---------------------------------------------------------------
		this.storage.store("kick-gc"); // resetCompletion: a fresh wave will run

		StorageEntityCache.Default.setGarbageCollectionEnabled(true);

		if(injectTransientSweepFailure)
		{
			trap.armed.set(true);
			assertThrows(RuntimeException.class,
				() -> this.storage.issueFullGarbageCollection(),
				"the planted transient sweep failure must fail the issued GC");
			assertEquals(1, trap.throwCount.get(), "the transient failure must have fired exactly once");
		}

		/*
		 * Re-execution: the sweep flagged by the failed attempt is still pending; this issued GC
		 * executes it (now without any failure), then runs its own wave whose marking walks
		 * root -> L -> R. Pre-fix, R was whitened by the failed attempt, is no longer registered
		 * anywhere, and gets DELETED by the re-executed sweep - the subsequent marking then
		 * reports R's oid as a zombie.
		 */
		this.storage.issueFullGarbageCollection();
		this.storage.issueFullFileCheck();

		/*
		 * The orphan W (actual garbage) must have been collected by now. Checked on the RUNNING
		 * storage, where the entity cache is authoritative - after a restart the startup scanner
		 * may resurrect logically-deleted-but-not-yet-compacted entities, which would make a
		 * post-restart check nondeterministic. Loading a collected oid fails on the storage side.
		 */
		boolean orphanCollected;
		try
		{
			orphanCollected = this.storage.persistenceManager().getObject(wOid) == null;
		}
		catch(final RuntimeException e)
		{
			// "No entity found for objectId W" - the entity is gone, which is what we assert
			orphanCollected = true;
		}

		// ---------------------------------------------------------------
		// Phase 5 - freeze, restart, inspect what physically survived
		// ---------------------------------------------------------------
		StorageEntityCache.Default.setGarbageCollectionEnabled(false);
		this.storage.shutdown();

		final Outcome outcome = new Outcome();
		outcome.victimOid       = rOid;
		outcome.orphanCollected = orphanCollected;
		outcome.zombieOids      = new ArrayList<>(zombieHandler.zombieOids);

		/*
		 * Pre-fix, the loss can be broader than the single victim: the failed sweep attempt
		 * whitened EVERY marked entity iterated before the trigger - including the Lazy L and
		 * potentially the root records - and the re-executed sweep deletes all of them. The
		 * eager root-graph load at restart then fails outright. Any failure in this phase is
		 * therefore recorded as total loss (victimData = null) instead of erroring the test,
		 * so the caller's assertions report it cleanly.
		 */
		try
		{
			this.reloaded = EmbeddedStorage.Foundation(
					Storage.ConfigurationBuilder()
						.setChannelCountProvider(Storage.ChannelCountProvider(1))
						.setStorageFileProvider(Storage.FileProvider(workDir))
						.createConfiguration()
				)
				.start();
			final DataRoot reloadedRoot = (DataRoot)this.reloaded.root();
			final Payload  reloadedVictim = reloadedRoot == null || reloadedRoot.keeper == null
				? null
				: reloadedRoot.keeper.get(); // pre-fix: "No entity found for objectId R"
			outcome.victimData = reloadedVictim == null ? null : reloadedVictim.data;
		}
		catch(final RuntimeException e)
		{
			outcome.victimData     = null;
			outcome.restartFailure = e;
		}
		finally
		{
			if(this.reloaded != null && this.reloaded.isRunning())
			{
				this.reloaded.shutdown();
			}
		}
		return outcome;
	}
}
