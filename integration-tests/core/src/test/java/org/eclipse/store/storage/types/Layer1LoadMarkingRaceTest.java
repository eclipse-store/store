package org.eclipse.store.storage.types;

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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.eclipse.serializer.functional._longProcedure;
import org.eclipse.serializer.persistence.binary.types.ChunksBuffer;
import org.eclipse.serializer.persistence.types.PersistenceIdSet;
import org.eclipse.serializer.persistence.types.PersistenceObjectRegistry;
import org.eclipse.serializer.reference.Lazy;
import org.eclipse.serializer.util.BufferSizeProviderIncremental;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageFoundation;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

/**
 * White-box regression test for <b>Layer 1</b> of the GC mid-cycle registration race fix
 * (eclipse-store/store#736): the <i>load-side</i> protection. When an entity is handed out to the
 * application by a load, {@link StorageChannel.Default#collectLoadByOids}/{@code collectLoadByTids}
 * open a pending-load gate ({@link StorageEntityCache.Default#registerPendingLoad()} &rarr;
 * {@code pendingLoadCount} blocks sweep initiation) and every collected entity is gray-marked and
 * enqueued via {@link StorageEntityCache.Default#markEntityForLoadedData} so its references are
 * walked transitively before any sweep.
 * <p>
 * The sibling E2E test {@code MidCycleRegistrationRaceTest} covers only <b>Layer 2</b> (the
 * registration-version re-seed in {@link StorageEntityMarkMonitor}): its Phase 5 registers objects
 * into the {@link PersistenceObjectRegistry} directly, which bypasses the real load path
 * ({@code collectLoadByOids}/{@code collectLoadByTids}). Consequently {@code registerPendingLoad},
 * {@code markEntityForLoadedData} and the three {@link StorageEntityCollector} call sites were never
 * executed by any test — yet Layer 1 is the branch a normal application load hits in production.
 * This test closes that gap.
 * <p>
 * <b>Why this package / this module.</b> The asserted members are package-private in
 * {@code org.eclipse.store.storage.types}, so the test is declared in that package (a deliberate
 * "friend test"). It lives in {@code integration-tests} rather than {@code storage/storage} because
 * building a real object graph needs {@code storage-embedded}, which depends on {@code storage} — a
 * test dependency the other way around would create a reactor cycle. {@code integration-tests} runs
 * with {@code useModulePath=false}, so the split package resolves on the classpath.
 * <p>
 * <b>Why this is independent of Layer 2.</b> {@code Y} (the {@link Payload}, an unloaded Lazy target)
 * is never registered in the object registry, so Layer 2's registry re-seed can never enqueue it —
 * only the load-side walk starting from the loaded {@code X} protects it. The mark queue is drained
 * via the incremental mark step (which does <i>not</i> re-seed) and the sweep is invoked directly, so
 * {@code callToSweepRequired}'s Layer-2 re-seed loop never runs during the assertion window.
 * If {@code markEntityForLoadedData} is neutered, {@code X} stays white, the drain never reaches
 * {@code Y}, and the sweep collects {@code Y} — the test then fails.
 * <p>
 * {@code @Isolated} + flag restoration because
 * {@link StorageEntityCache.Default#setGarbageCollectionEnabled(boolean)} is a static, JVM-global
 * switch. Determinism: everything is driven synchronously on the test thread with background GC
 * halted, so there is no timing window and no {@code assumeTrue}-based skip; {@code @Timeout} is only
 * a backstop.
 */
@Isolated
public class Layer1LoadMarkingRaceTest
{
	private static final String PAYLOAD_DATA = "layer1 load-marking victim";


	///////////////////////////////////////////////////////////////////////////
	// data types //
	///////////////

	public static class DataRoot
	{
		public Object ref;
	}

	/**
	 * X — the loaded parent whose Lazy child is never materialized.
	 */
	public static class Parent
	{
		public Lazy<Payload> child;
	}

	/**
	 * Y — the never-built lazy target that only Layer 1's transitive walk can protect.
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
	 * Records zombie OIDs reported by the storage GC.
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
	 * The three load entry points whose collector {@code accept} call sites invoke
	 * {@link StorageEntityCache.Default#markEntityForLoadedData}.
	 */
	enum LoadPath
	{
		/** {@code collectLoadByOids} with the default (checked) collector. */
		OIDS_CHECKED,
		/** {@code collectLoadByOids}' unchecked collector variant. */
		OIDS_UNCHECKED,
		/** {@code collectLoadByTids}. */
		TIDS
	}


	///////////////////////////////////////////////////////////////////////////
	// test //
	/////////

	@TempDir
	Path tempDir;

	EmbeddedStorageManager storage;

	@AfterEach
	public void afterTest()
	{
		// the static, JVM-global GC switch must never leak into other tests.
		StorageEntityCache.Default.setGarbageCollectionEnabled(true);

		if(this.storage != null && this.storage.isRunning())
		{
			try
			{
				this.storage.shutdown();
			}
			catch(final Exception ignored)
			{
				// best effort
			}
		}
	}

	@Test
	@Timeout(value = 120, unit = TimeUnit.SECONDS)
	void layer1ProtectsLoadedGraphViaCollectLoadByOidsChecked() throws Exception
	{
		this.run(LoadPath.OIDS_CHECKED);
	}

	@Test
	@Timeout(value = 120, unit = TimeUnit.SECONDS)
	void layer1ProtectsLoadedGraphViaCollectLoadByOidsUnchecked() throws Exception
	{
		this.run(LoadPath.OIDS_UNCHECKED);
	}

	@Test
	@Timeout(value = 120, unit = TimeUnit.SECONDS)
	void layer1ProtectsLoadedGraphViaCollectLoadByTids() throws Exception
	{
		this.run(LoadPath.TIDS);
	}

	private void run(final LoadPath loadPath) throws Exception
	{
		final Path                     workDir       = this.tempDir.resolve(loadPath.name());
		final CountingZombieOidHandler zombieHandler = new CountingZombieOidHandler();

		// Halt background GC so no channel-thread mark/sweep runs concurrently with the synchronous
		// white-box driving below. A very long housekeeping interval keeps the channel thread from
		// doing cache/file checks during the sub-second test window.
		StorageEntityCache.Default.setGarbageCollectionEnabled(false);

		final EmbeddedStorageFoundation<?> foundation = EmbeddedStorage.Foundation(
			Storage.ConfigurationBuilder()
				.setChannelCountProvider(Storage.ChannelCountProvider(1))
				.setHousekeepingController(Storage.HousekeepingController(3_600_000, 1_000_000))
				.setStorageFileProvider(Storage.FileProvider(workDir))
				.createConfiguration()
		);
		foundation.setGCZombieOidHandler(zombieHandler);
		this.storage = foundation.start();

		final PersistenceObjectRegistry registry = this.storage.persistenceManager().objectRegistry();

		// ---------------------------------------------------------------
		// Phase 1 - store root -> X(Parent) -> L(Lazy) -> Y(Payload)
		// ---------------------------------------------------------------
		final DataRoot      root         = new DataRoot();
		final Parent        parent       = new Parent();
		final Payload       payload      = new Payload(PAYLOAD_DATA);
		parent.child = Lazy.Reference(payload);
		final Lazy<Payload> lazyInstance = parent.child;
		root.ref = parent;

		this.storage.setRoot(root);
		this.storage.storeRoot();

		final long xOid = registry.lookupObjectId(parent);
		final long lOid = registry.lookupObjectId(lazyInstance);
		final long yOid = registry.lookupObjectId(payload);
		assertTrue(xOid > 0 && lOid > 0 && yOid > 0, "could not resolve the OIDs of the stored graph");

		// reach the single channel's entity cache (white-box).
		final StorageSystem.Default      system      =
			(StorageSystem.Default)foundation.getConnectionFoundation().getStorageSystem();
		final StorageChannel.Default     channel     = channelZero(system);
		final StorageEntityCache.Default entityCache = entityCacheOf(channel);
		final StorageEntityMarkMonitor   markMonitor = entityCache.markMonitor();

		// ---------------------------------------------------------------
		// Phase 2 - one full GC while the graph is reachable+registered: marks everything (incl.
		// X/L/Y) black and cold-completes the cycle. Then halt background GC again.
		// ---------------------------------------------------------------
		StorageEntityCache.Default.setGarbageCollectionEnabled(true);
		this.storage.issueFullGarbageCollection();
		StorageEntityCache.Default.setGarbageCollectionEnabled(false);

		final StorageEntity.Default entryX = entityCache.getEntry(xOid);
		final StorageEntity.Default entryL = entityCache.getEntry(lOid);
		final StorageEntity.Default entryY = entityCache.getEntry(yOid);
		assertNotNull(entryX, "X entry missing after store");
		assertNotNull(entryL, "L entry missing after store");
		assertNotNull(entryY, "Y entry missing after store");

		// ---------------------------------------------------------------
		// Phase 3 - model an in-progress GC cycle whose live-OID seed MISSED X/L/Y: force those three
		// entries white (every other entity stays black) and re-open the cycle so loadMarkingRequired
		// becomes true.
		// ---------------------------------------------------------------
		entryX.markWhite();
		entryL.markWhite();
		entryY.markWhite();
		markMonitor.resetCompletion(); // gcColdPhaseComplete=false => a cycle is "in progress"

		// ---------------------------------------------------------------
		// Layer-1 assertion A: the pending-load gate defers sweep initiation.
		// ---------------------------------------------------------------
		entityCache.registerPendingLoad();
		assertFalse(markMonitor.isMarkingComplete(),
			"registerPendingLoad() must keep marking 'incomplete' so no sweep can initiate mid-load");
		entityCache.clearPendingLoad();

		// ---------------------------------------------------------------
		// Layer-1 assertion B: the real load path gray-marks the handed-out entity X and enqueues it
		// (this is what markEntityForLoadedData does; a no-op there leaves X white and the assertion
		// fails).
		// ---------------------------------------------------------------
		driveLoad(loadPath, channel, entityCache, xOid, entryX.typeId());
		assertTrue(entryX.isGcAlreadyHandled(),
			"the load path must gray-mark X via markEntityForLoadedData (Layer 1)");
		assertFalse(entryX.isGcBlack(), "X should be gray (enqueued), not yet walked");

		// ---------------------------------------------------------------
		// Layer-1 assertion C: draining the mark queue walks X -> L -> Y transitively. Y is never in
		// the registry, so only this load-side walk can protect it.
		// ---------------------------------------------------------------
		drainMarkQueue(entityCache);
		assertTrue(entryL.isGcBlack(), "L must be marked black by the transitive walk from X");
		assertTrue(entryY.isGcBlack(),
			"Y must be reached transitively via L's binary and marked black (Layer-1 transitive protection)");

		// ---------------------------------------------------------------
		// Layer-1 assertion D: a subsequent sweep spares X/L/Y and reports no zombie. Only X/L/Y are
		// white candidates (every other entity is still black from Phase 2), so the oid->false
		// predicate ("nothing live in the application") collects exactly the entities Layer 1 failed
		// to protect - none, in the fixed engine.
		// ---------------------------------------------------------------
		alignSeedRegistrationVersion(entityCache, markMonitor); // avoid the stale-seed keep-all shortcut
		entityCache.sweep(oid -> false);
		assertNotNull(entityCache.getEntry(xOid), "X must survive the sweep");
		assertNotNull(entityCache.getEntry(lOid), "L must survive the sweep");
		assertNotNull(entityCache.getEntry(yOid), "Y must survive the sweep");
		assertFalse(zombieHandler.zombieOids.contains(yOid),
			"no zombie OID may be reported for Y, but got: " + zombieHandler.zombieOids);
	}


	///////////////////////////////////////////////////////////////////////////
	// load driving //
	/////////////////

	private static void driveLoad(
		final LoadPath                   loadPath   ,
		final StorageChannel.Default     channel    ,
		final StorageEntityCache.Default entityCache,
		final long                       xOid       ,
		final long                       parentTid
	)
	{
		switch(loadPath)
		{
			case OIDS_CHECKED:
				// real entry point; default collector is the checked EntityCollectorByOid.
				channel.collectLoadByOids(new ChunksBuffer[1], idSet(xOid));
				break;

			case OIDS_UNCHECKED:
				// The channel's configured collector is the checked one; drive the unchecked collector
				// directly (as collectLoadByOids would with Creator.Unchecked()) inside the same
				// pending-load bracket, to cover EntityCollectorByOidUnchecked#accept.
				final ChunksBuffer chunks =
					ChunksBuffer.New(new ChunksBuffer[1], BufferSizeProviderIncremental.New());
				entityCache.registerPendingLoad();
				try
				{
					idSet(xOid).iterate(
						new StorageEntityCollector.EntityCollectorByOidUnchecked(entityCache, chunks));
				}
				finally
				{
					entityCache.clearPendingLoad();
				}
				chunks.complete();
				break;

			case TIDS:
				// real entry point; drives EntityCollectorByTid over the Parent type (only X).
				channel.collectLoadByTids(new ChunksBuffer[1], idSet(parentTid));
				break;

			default:
				throw new IllegalArgumentException(String.valueOf(loadPath));
		}
	}

	private static PersistenceIdSet idSet(final long... ids)
	{
		return new PersistenceIdSet()
		{
			@Override
			public long size()
			{
				return ids.length;
			}

			@Override
			public boolean isEmpty()
			{
				return ids.length == 0;
			}

			@Override
			public void iterate(final _longProcedure iterator)
			{
				for(final long id : ids)
				{
					iterator.accept(id);
				}
			}
		};
	}


	///////////////////////////////////////////////////////////////////////////
	// white-box reflection helpers //
	/////////////////////////////////

	private static StorageChannel.Default channelZero(final StorageSystem.Default system) throws Exception
	{
		final Field keepersField = StorageSystem.Default.class.getDeclaredField("channelKeepers");
		keepersField.setAccessible(true);
		final Object[] keepers = (Object[])keepersField.get(system);

		final Object keeper       = keepers[0];
		final Field  channelField = keeper.getClass().getDeclaredField("channel");
		channelField.setAccessible(true);
		return (StorageChannel.Default)channelField.get(keeper);
	}

	private static StorageEntityCache.Default entityCacheOf(final StorageChannel.Default channel) throws Exception
	{
		final Field cacheField = StorageChannel.Default.class.getDeclaredField("entityCache");
		cacheField.setAccessible(true);
		return (StorageEntityCache.Default)cacheField.get(channel);
	}

	/**
	 * Drains the channel's mark queue via the private incremental mark step - which walks references
	 * transitively but, unlike {@code issuedGarbageCollection}, does NOT reset completion or re-seed
	 * from the registry (so Layer 2 stays out of the picture).
	 */
	private static void drainMarkQueue(final StorageEntityCache.Default entityCache) throws Exception
	{
		final Method incrementalMark =
			StorageEntityCache.Default.class.getDeclaredMethod("incrementalMark", long.class);
		incrementalMark.setAccessible(true);

		final long deadline = System.nanoTime() + 10_000_000_000L; // 10s backstop
		boolean    drained  = false;
		while(!drained && System.nanoTime() < deadline)
		{
			// returns true once the queue ran out of work (rather than out of time).
			drained = (Boolean)incrementalMark.invoke(entityCache, System.nanoTime() + 1_000_000_000L);
		}
		assertTrue(drained, "mark queue did not drain within the backstop");
	}

	/**
	 * Aligns the mark monitor's seed registration version with the current registry version so the
	 * sweep does not take the "stale seed -> keep everything" shortcut (which would make the survival
	 * assertions pass vacuously). No registration happens after this point, so the versions stay equal
	 * through the sweep.
	 */
	private static void alignSeedRegistrationVersion(
		final StorageEntityCache.Default entityCache,
		final StorageEntityMarkMonitor   markMonitor
	) throws Exception
	{
		final Field handlerField = StorageEntityCache.Default.class.getDeclaredField("liveObjectIdsHandler");
		handlerField.setAccessible(true);
		final LiveObjectIdsHandler handler = (LiveObjectIdsHandler)handlerField.get(entityCache);

		final Field versionField = markMonitor.getClass().getDeclaredField("seedRegistrationVersion");
		versionField.setAccessible(true);
		versionField.setLong(markMonitor, handler.registrationVersion());
	}
}
