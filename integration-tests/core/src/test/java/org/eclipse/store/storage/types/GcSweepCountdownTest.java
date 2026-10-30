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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.eclipse.serializer.persistence.types.PersistenceObjectRegistry;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageFoundation;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

/**
 * White-box tests for the garbage-collection <b>unmarked-sweep countdown</b> (internal#109): an
 * unreachable entity is not deleted the first sweep it is found unmarked, but only after it has
 * stayed unmarked for a configurable number of consecutive sweeps
 * ({@link StorageHousekeepingController#garbageCollectionSweepThreshold()}, default {@code 3}).
 * Every successful marking resets the countdown. This is a probabilistic safety net against rare,
 * transient GC concurrency races (see {@link StorageEntity.Default#ageUnmarkedAndIsCondemned(int)}).
 * <p>
 * The countdown reuses the free negative range of {@link StorageEntity.Default}'s {@code gcState}
 * byte, so it costs no additional per-entity memory.
 * <p>
 * <b>Why this package / module.</b> The asserted members ({@code sweep}, {@code getEntry},
 * {@code ageUnmarkedAndIsCondemned}, the {@code mark*} helpers) are package-private in
 * {@code org.eclipse.store.storage.types}, so this is a deliberate "friend test" declared in that
 * package. It lives in {@code integration-tests} because building a real object graph needs
 * {@code storage-embedded} (which depends on {@code storage}); the reverse test dependency would
 * create a reactor cycle. {@code integration-tests} runs with {@code useModulePath=false}, so the
 * split package resolves on the classpath. This mirrors {@code Layer1LoadMarkingRaceTest}.
 * <p>
 * {@code @Isolated} because {@link StorageEntityCache.Default#setGarbageCollectionEnabled(boolean)}
 * is a static, JVM-global switch.
 */
@Isolated
public class GcSweepCountdownTest
{
	///////////////////////////////////////////////////////////////////////////
	// data types //
	///////////////

	public static class DataRoot
	{
		public List<Object> entries = new ArrayList<>();
	}

	public static class Entry
	{
		public final String data;

		public Entry(final String data)
		{
			super();
			this.data = data;
		}
	}


	///////////////////////////////////////////////////////////////////////////
	// fixture //
	////////////

	@TempDir
	Path tempDir;

	EmbeddedStorageManager storage;

	StorageSystem.Default  storageSystem;

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


	///////////////////////////////////////////////////////////////////////////
	// unit test of the countdown logic //
	/////////////////////////////////////

	@Test
	void ageUnmarkedAndIsCondemned_thresholdOne_deletesOnFirstUnmarkedSweep()
	{
		final StorageEntity.Default entity = StorageEntity.Default.createDummy();
		entity.markWhite();

		// threshold 1 is the classic, countdown-less behavior: condemned on the very first unmarked sweep.
		assertTrue(entity.ageUnmarkedAndIsCondemned(1), "threshold 1 must condemn on the first unmarked sweep");
	}

	@Test
	void ageUnmarkedAndIsCondemned_thresholdThree_condemnsOnlyOnThirdConsecutiveUnmarkedSweep()
	{
		final StorageEntity.Default entity = StorageEntity.Default.createDummy();
		entity.markWhite();

		assertFalse(entity.ageUnmarkedAndIsCondemned(3), "1st unmarked sweep must keep the entity");
		assertFalse(entity.ageUnmarkedAndIsCondemned(3), "2nd unmarked sweep must keep the entity");
		assertTrue (entity.ageUnmarkedAndIsCondemned(3), "3rd consecutive unmarked sweep must condemn the entity");
	}

	@Test
	void ageUnmarkedAndIsCondemned_markWhiteResetsTheCountdown()
	{
		final StorageEntity.Default entity = StorageEntity.Default.createDummy();
		entity.markWhite();

		// age it partway towards condemnation ...
		assertFalse(entity.ageUnmarkedAndIsCondemned(3));
		assertFalse(entity.ageUnmarkedAndIsCondemned(3));

		// ... a keep-alive sweep resets the countdown ...
		entity.markWhite();

		// ... so it again takes the full threshold of consecutive unmarked sweeps.
		assertFalse(entity.ageUnmarkedAndIsCondemned(3), "countdown must restart after markWhite()");
		assertFalse(entity.ageUnmarkedAndIsCondemned(3));
		assertTrue (entity.ageUnmarkedAndIsCondemned(3));
	}

	@Test
	void ageUnmarkedAndIsCondemned_markBlackResetsTheCountdown()
	{
		final StorageEntity.Default entity = StorageEntity.Default.createDummy();
		entity.markWhite();
		assertFalse(entity.ageUnmarkedAndIsCondemned(3));

		// a successful marking makes the entity gc-marked again; the next keep-alive sweep resets it to white.
		entity.markBlack();
		assertTrue(entity.isGcMarked(), "markBlack must make the entity gc-marked");
		entity.markWhite();

		assertFalse(entity.ageUnmarkedAndIsCondemned(3), "countdown must restart after a successful marking");
		assertFalse(entity.ageUnmarkedAndIsCondemned(3));
		assertTrue (entity.ageUnmarkedAndIsCondemned(3));
	}


	///////////////////////////////////////////////////////////////////////////
	// configuration wiring / validation //
	//////////////////////////////////////

	@Test
	void housekeepingController_defaultSweepThreshold_isThree()
	{
		assertEquals(3, StorageHousekeepingController.New().garbageCollectionSweepThreshold());
		assertEquals(3, StorageHousekeepingController.Defaults.defaultGarbageCollectionSweepThreshold());
	}

	@Test
	void housekeepingController_explicitSweepThreshold_isReturned()
	{
		assertEquals(5, Storage.HousekeepingController(1_000, 10_000_000, 5).garbageCollectionSweepThreshold());
	}

	@Test
	void housekeepingController_sweepThresholdOutOfRange_throws()
	{
		assertThrows(IllegalArgumentException.class,
			() -> Storage.HousekeepingController(1_000, 10_000_000, 0),
			"a sweep threshold below the minimum (1) must be rejected");
		assertThrows(IllegalArgumentException.class,
			() -> Storage.HousekeepingController(1_000, 10_000_000, 128),
			"a sweep threshold above the maximum (127) must be rejected");
	}

	@Test
	@Timeout(value = 120, unit = TimeUnit.SECONDS)
	void startup_customControllerWithOutOfRangeSweepThreshold_isRejected()
	{
		// a custom StorageHousekeepingController bypasses the builder validation, so the entity cache must
		// enforce the upper bound itself: an out-of-range value would underflow the gcState byte otherwise.
		final StorageHousekeepingController badController = new StorageHousekeepingController()
		{
			private final StorageHousekeepingController delegate = StorageHousekeepingController.New();

			@Override
			public long housekeepingIntervalMs()
			{
				return this.delegate.housekeepingIntervalMs();
			}

			@Override
			public long housekeepingTimeBudgetNs()
			{
				return this.delegate.housekeepingTimeBudgetNs();
			}

			@Override
			public long garbageCollectionTimeBudgetNs()
			{
				return this.delegate.garbageCollectionTimeBudgetNs();
			}

			@Override
			public long liveCheckTimeBudgetNs()
			{
				return this.delegate.liveCheckTimeBudgetNs();
			}

			@Override
			public long fileCheckTimeBudgetNs()
			{
				return this.delegate.fileCheckTimeBudgetNs();
			}

			@Override
			public int garbageCollectionSweepThreshold()
			{
				return 200; // out of range: would underflow the gcState byte
			}
		};

		final EmbeddedStorageFoundation<?> foundation = EmbeddedStorage.Foundation(
			Storage.ConfigurationBuilder()
				.setChannelCountProvider(Storage.ChannelCountProvider(1))
				.setHousekeepingController(badController)
				.setStorageFileProvider(Storage.FileProvider(this.tempDir.resolve("db-bad")))
				.createConfiguration()
		);

		assertThrows(RuntimeException.class, foundation::start,
			"an out-of-range sweep threshold from a custom controller must be rejected at startup");
	}


	///////////////////////////////////////////////////////////////////////////
	// white-box sweep progression //
	////////////////////////////////

	@Test
	@Timeout(value = 120, unit = TimeUnit.SECONDS)
	void sweep_withThresholdThree_deletesAnUnmarkedEntityOnlyOnTheThirdConsecutiveSweep() throws Exception
	{
		// halt background GC so the channel thread cannot mutate gcState during the synchronous white-box driving.
		StorageEntityCache.Default.setGarbageCollectionEnabled(false);
		final long targetOid = this.startWithGarbageCandidate(3);

		final StorageEntityCache.Default entityCache = this.entityCache();

		// simulate an in-progress cycle whose marking missed the target: force it white (start of countdown).
		final StorageEntity.Default target = entityCache.getEntry(targetOid);
		assertNotNull(target, "target entry must exist before sweeping");
		target.markWhite();

		// predicate: everything is application-reachable EXCEPT the target, so only the target ages down.
		// 1st and 2nd sweep must keep it (countdown not yet exhausted) ...
		this.directSweep(entityCache, targetOid);
		assertNotNull(entityCache.getEntry(targetOid), "target must survive the 1st unmarked sweep (threshold 3)");

		this.directSweep(entityCache, targetOid);
		assertNotNull(entityCache.getEntry(targetOid), "target must survive the 2nd unmarked sweep (threshold 3)");

		// ... the 3rd consecutive unmarked sweep condemns it.
		this.directSweep(entityCache, targetOid);
		assertNull(entityCache.getEntry(targetOid), "target must be deleted on the 3rd consecutive unmarked sweep");
	}

	@Test
	@Timeout(value = 120, unit = TimeUnit.SECONDS)
	void sweep_reMarkingBetweenSweeps_resetsTheCountdownAndSparesTheEntity() throws Exception
	{
		// halt background GC so the channel thread cannot mutate gcState during the synchronous white-box driving.
		StorageEntityCache.Default.setGarbageCollectionEnabled(false);
		final long targetOid = this.startWithGarbageCandidate(3);

		final StorageEntityCache.Default entityCache = this.entityCache();
		final StorageEntity.Default      target      = entityCache.getEntry(targetOid);
		assertNotNull(target);
		target.markWhite();

		// two unmarked sweeps bring it close to condemnation ...
		this.directSweep(entityCache, targetOid);
		this.directSweep(entityCache, targetOid);
		assertNotNull(entityCache.getEntry(targetOid), "target must still survive after 2 sweeps");

		// ... a successful marking (as a correct mark cycle would do) resets the countdown ...
		target.markBlack();

		// ... so two more unmarked sweeps still do not delete it (a threshold-3 countdown restarted from scratch).
		this.directSweep(entityCache, targetOid);
		this.directSweep(entityCache, targetOid);
		assertNotNull(entityCache.getEntry(targetOid),
			"re-marking must reset the countdown, so the entity survives further sweeps");
	}


	///////////////////////////////////////////////////////////////////////////
	// full garbage collection contract //
	/////////////////////////////////////

	@Test
	@Timeout(value = 120, unit = TimeUnit.SECONDS)
	void issueFullGarbageCollection_withThresholdThree_reclaimsGarbageInASingleCall() throws Exception
	{
		this.startStorage(3);

		final PersistenceObjectRegistry  registry    = this.storage.persistenceManager().objectRegistry();
		final StorageEntityCache.Default entityCache = this.entityCache();

		final DataRoot root  = new DataRoot();
		Entry          entry = new Entry("garbage-candidate");
		root.entries.add(entry);
		this.storage.setRoot(root);
		this.storage.storeRoot();

		final long targetOid = registry.lookupObjectId(entry);
		assertTrue(targetOid > 0, "could not resolve the target entry's object id");
		assertNotNull(entityCache.getEntry(targetOid), "target must exist before it becomes garbage");

		// make the target actual garbage: drop it from the persistent graph, store, then release all Java refs.
		root.entries.clear();
		this.storage.store(root.entries);

		final WeakReference<Entry> probe = new WeakReference<>(entry);
		entry = null; // drop the last strong reference so the object registry can reap its (weak) entry

		for(int i = 0; i < 10 && probe.get() != null; i++)
		{
			System.gc();
			Thread.sleep(50);
		}
		assumeTrue(probe.get() == null,
			"JVM did not garbage-collect the target entry - test cannot proceed deterministically");

		// reap the cleared weak reference from the registry so the sweep's application-reachability
		// safety net no longer reports the target as live (same code path a store triggers; see GC.md §8).
		registry.cleanUp();

		// a single explicit full GC must reclaim it despite the sweep threshold of 3: an issued full GC
		// bypasses the countdown and deletes unmarked entities immediately (effective threshold 1).
		this.storage.issueFullGarbageCollection();

		assertNull(entityCache.getEntry(targetOid),
			"issueFullGarbageCollection must reclaim actual garbage in a single call even with threshold 3");
	}


	///////////////////////////////////////////////////////////////////////////
	// helpers //
	////////////

	/**
	 * Starts a single-channel storage (background GC effectively disabled via a very long housekeeping
	 * interval), stores a root holding one {@link Entry}, and returns that entry's object id.
	 */
	private void startStorage(final int sweepThreshold) throws Exception
	{
		final EmbeddedStorageFoundation<?> foundation = EmbeddedStorage.Foundation(
			Storage.ConfigurationBuilder()
				.setChannelCountProvider(Storage.ChannelCountProvider(1))
				.setHousekeepingController(Storage.HousekeepingController(3_600_000, 1_000_000, sweepThreshold))
				.setStorageFileProvider(Storage.FileProvider(this.tempDir.resolve("db")))
				.createConfiguration()
		);
		this.storage       = foundation.start();
		this.storageSystem = (StorageSystem.Default)foundation.getConnectionFoundation().getStorageSystem();

		assertEquals(sweepThreshold,
			gcSweepThresholdOf(this.entityCache()),
			"the configured sweep threshold must be threaded into the entity cache");
	}

	private long startWithGarbageCandidate(final int sweepThreshold) throws Exception
	{
		this.startStorage(sweepThreshold);

		final DataRoot root  = new DataRoot();
		final Entry    entry = new Entry("garbage-candidate");
		root.entries.add(entry);
		this.storage.setRoot(root);
		this.storage.storeRoot();

		final long targetOid = this.storage.persistenceManager().objectRegistry().lookupObjectId(entry);
		assertTrue(targetOid > 0, "could not resolve the target entry's object id");

		return targetOid;
	}

	/**
	 * Executes one direct sweep in which every object id is treated as application-reachable except
	 * {@code targetOid}. Only the target can therefore age towards condemnation; every other entity is
	 * kept and reset to white. The mark-monitor seed version is aligned first so the sweep does not take
	 * the stale-seed keep-all shortcut.
	 */
	private void directSweep(final StorageEntityCache.Default entityCache, final long targetOid) throws Exception
	{
		alignSeedRegistrationVersion(entityCache);
		entityCache.sweep(oid -> oid != targetOid);
	}

	private StorageEntityCache.Default entityCache() throws Exception
	{
		return entityCacheOf(channelZero(this.storageSystem));
	}


	///////////////////////////////////////////////////////////////////////////
	// white-box reflection helpers (mirrors Layer1LoadMarkingRaceTest) //
	////////////////////////////////////////////////////////////////////

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

	private static int gcSweepThresholdOf(final StorageEntityCache.Default entityCache) throws Exception
	{
		final Field field = StorageEntityCache.Default.class.getDeclaredField("gcSweepThreshold");
		field.setAccessible(true);
		return field.getInt(entityCache);
	}

	private static void alignSeedRegistrationVersion(final StorageEntityCache.Default entityCache) throws Exception
	{
		final StorageEntityMarkMonitor markMonitor = entityCache.markMonitor();

		final Field handlerField = StorageEntityCache.Default.class.getDeclaredField("liveObjectIdsHandler");
		handlerField.setAccessible(true);
		final LiveObjectIdsHandler handler = (LiveObjectIdsHandler)handlerField.get(entityCache);

		final Field versionField = markMonitor.getClass().getDeclaredField("seedRegistrationVersion");
		versionField.setAccessible(true);
		versionField.setLong(markMonitor, handler.registrationVersion());
	}
}
