package test.eclipse.store.behavior;

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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.serializer.persistence.types.PersistenceManager;
import org.eclipse.serializer.persistence.types.PersistenceObjectRegistry;
import org.eclipse.serializer.reference.Lazy;
import org.eclipse.serializer.reference.Swizzling;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.types.Storage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Regression test for internal issue #72: {@code BinaryLoader} used to register
 * a freshly created instance in the global object registry <b>before</b>
 * initializing its state ({@code getEffectiveInstance} →
 * {@code optionalRegisterObject}), while {@code PersistenceManager.lookupObject}
 * and {@code DefaultObjectRegistry.lookupObject} read the registry without the
 * build monitor. A concurrent reader could therefore observe (and capture) a
 * blank, partially-initialized instance — and, by deriving new state from it
 * and storing, overwrite the entity's persisted content with the blank state.
 * <p>
 * Scenario (made wide instead of racy): the shared entity is a {@link HashMap}
 * with many entries. JDK hash collections are populated in the loader's
 * {@code complete} phase, so under the defect the map was registered blank at
 * the start of the build and filled only at its very end — the
 * "registered but not initialized" window spanned nearly the whole load.
 * Thread A resolves the graph via {@code Lazy.get()}; thread B concurrently
 * polls the OID exactly like the {@code getObject} fast path does and captures
 * what it sees first.
 * <p>
 * Correct behavior, asserted here: the first instance state observable by a
 * concurrent reader must be the fully initialized one — the loader may publish
 * instances to the object registry only after {@code complete}.
 */
public class BlankInstanceRegistrationRaceReproTest
{
	/**
	 * Entries in the shared map. Large enough that the loader's build takes
	 * long enough for the reader thread to reliably observe the instance the
	 * moment it becomes visible in the registry.
	 */
	private static final int MAP_SIZE = 200_000;

	@TempDir
	Path tempDir;

	EmbeddedStorageManager storage;

	@AfterEach
	public void afterTest()
	{
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
	@Timeout(300)
	void concurrentFirstLoadMustNeverExposeBlankInstance() throws Exception
	{
		// Phase 1: seed the storage: root -> Lazy -> HashMap(MAP_SIZE), commit,
		// capture the map's OID, shut down (so the next session starts with an
		// empty object registry and must load the map from disk).
		final long mapOid;
		{
			this.storage = EmbeddedStorage.Foundation(
				Storage.ConfigurationBuilder()
					.setChannelCountProvider(Storage.ChannelCountProvider(1))
					.setStorageFileProvider(Storage.FileProvider(this.tempDir))
					.createConfiguration()
			)
			.start();

			final DataRoot root = new DataRoot();
			final HashMap<String, String> map = new HashMap<>();
			for(int i = 0; i < MAP_SIZE; i++)
			{
				map.put("key-" + i, "value-" + i);
			}
			root.lazyMap = Lazy.Reference(map);
			this.storage.setRoot(root);
			this.storage.storeRoot();

			mapOid = this.storage.persistenceManager().objectRegistry().lookupObjectId(map);
			assertNotEquals(Swizzling.notFoundId(), mapOid, "map must be registered after initial store");

			this.storage.shutdown();
		}

		// Phase 2: fresh session; the map exists only on disk.
		this.storage = EmbeddedStorage.Foundation(
			Storage.ConfigurationBuilder()
				.setChannelCountProvider(Storage.ChannelCountProvider(1))
				.setStorageFileProvider(Storage.FileProvider(this.tempDir))
				.createConfiguration()
		)
		.start();

		final PersistenceManager<?>     pm       = this.storage.persistenceManager();
		final PersistenceObjectRegistry registry = pm.objectRegistry();
		final DataRoot                  root     = (DataRoot)this.storage.root();

		assertNotNull(root.lazyMap, "reloaded root must hold the Lazy");
		assertNull(registry.lookupObject(mapOid), "map must not be loaded/registered yet");

		// Thread B: the concurrent reader, using only public PersistenceManager
		// API. It spins on PersistenceManager.lookupObject, which reads the
		// registry without the build monitor and thus sees the instance the
		// moment it is registered.
		final AtomicReference<Integer>   firstObservedSize = new AtomicReference<>();
		final AtomicReference<Integer>   publicApiSize     = new AtomicReference<>();
		final List<String>               observations      = new ArrayList<>();
		final CountDownLatch             observed          = new CountDownLatch(1);
		final AtomicReference<Throwable> readerFailure     = new AtomicReference<>();

		final Thread reader = new Thread(() ->
		{
			try
			{
				Object o;
				while((o = pm.lookupObject(mapOid)) == null)
				{
					Thread.onSpinWait();
				}
				final long tRegistered = System.nanoTime();
				@SuppressWarnings("unchecked")
				final Map<String, String> viaRegistry = (Map<String, String>)o;
				final int sizeAtFirstSight = viaRegistry.size();
				firstObservedSize.set(sizeAtFirstSight);

				// getObject blocks on the build monitor and returns only after
				// the build completed; it must resolve to the same instance.
				final Object viaGetObject = pm.getObject(mapOid);
				publicApiSize.set(((Map<?, ?>)viaGetObject).size());
				assertSame(o, viaGetObject, "getObject must resolve to the registered instance");

				// Document how long a partially-filled state stays observable
				// (empty list of transitions once the defect is fixed).
				int lastSize = sizeAtFirstSight;
				observations.add("t+0ms size=" + sizeAtFirstSight);
				final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
				while(lastSize < MAP_SIZE && System.nanoTime() < deadline)
				{
					final int s = viaRegistry.size();
					if(s != lastSize)
					{
						observations.add("t+" + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - tRegistered)
							+ "ms size=" + s);
						lastSize = s;
					}
					Thread.onSpinWait();
				}
				observed.countDown();
			}
			catch(final Throwable t)
			{
				readerFailure.set(t);
				observed.countDown();
			}
		}, "concurrent-reader");
		reader.start();

		// Thread A (main): the legitimate first load of the shared subgraph.
		final Map<String, String> viaLazy = root.lazyMap.get();
		assertEquals(MAP_SIZE, viaLazy.size(), "loader thread itself must see the complete map");

		assertTrue(observed.await(90, TimeUnit.SECONDS), "reader thread must observe the registered instance");
		reader.join(TimeUnit.SECONDS.toMillis(10));
		if(readerFailure.get() != null)
		{
			fail("reader thread failed", readerFailure.get());
		}

		// Correctness assertion: a concurrently visible instance must be fully
		// initialized. Under the defect, the reader saw the blank map
		// (typically size=0) through PersistenceManager.lookupObject long
		// before the build completed.
		assertEquals(MAP_SIZE, firstObservedSize.get().intValue(),
			"BLANK INSTANCE EXPOSED: concurrent reader observed a partially-initialized map"
				+ " (first sight via PersistenceManager.lookupObject: " + firstObservedSize.get()
				+ ", via blocking getObject afterwards: " + publicApiSize.get()
				+ ", expected " + MAP_SIZE + "); fill-in timeline: " + observations);
	}

	///////////////////////////////////////////////////////////////////////////
	// data types //
	////////////////

	public static class DataRoot
	{
		public Lazy<HashMap<String, String>> lazyMap;
	}
}
