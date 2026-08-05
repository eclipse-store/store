package org.eclipse.store.gigamap.issues;

/*-
 * #%L
 * EclipseStore GigaMap
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.eclipse.serializer.reference.Lazy;
import org.eclipse.serializer.reference.LazyReferenceManager;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.IndexerString;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Regression test for the entity locator eviction miss (internal issue #138): {@code remove(E)},
 * {@code update(E, ...)}, {@code apply(E, ...)} and {@code replace(E, E)} resolve an entity's id via
 * {@code lookupEntityIdPeeking}, which - for the default referential-equality equalator - compared
 * candidates using {@code isEntityPeeking}/{@code peek(long)}. {@code peek} walks the segment tree
 * with {@code Lazy.peek()} and returns {@code null} without loading whenever the owning segment is
 * not resident (evicted by {@code release()} or the default ~15-minute {@code LazyReferenceManager}
 * idle timeout) - even though the caller's instance is provably still in the map (the object
 * registry would resolve it to the identical instance on load, i.e. {@code get(id) == entity}).
 * <p>
 * The fix makes {@code isEntityPeeking} fall back to the already-existing loading comparison
 * ({@code isEntity}/{@code get(long)}, previously only used for value-equality equalators via
 * {@code EntityIdResolverLoading}) whenever {@code peek()} finds nothing, so a peek-miss caused by
 * eviction is no longer mistaken for "entity not in the map".
 */
public class EntityLocatorEvictionMissTest
{
	static final class Rec
	{
		String key;
		String data;

		Rec(final String key, final String data)
		{
			this.key  = key;
			this.data = data;
		}
	}

	static final class KeyIdx extends IndexerString.Abstract<Rec>
	{
		@Override
		public String name()
		{
			return "key";
		}

		@Override
		protected String getString(final Rec entity)
		{
			return entity.key;
		}
	}

	/**
	 * Documents the precondition the rest of this test class relies on: after a segment is evicted,
	 * loading it again resolves the SAME instance via the object registry, as long as the caller still
	 * strongly references it. This is checked in isolation (own map, nothing removed) because forcing
	 * a load via {@code get(id)} on the very entity under test would reload its segment and defeat the
	 * "segment not resident" precondition the other tests below rely on.
	 */
	@Test
	@Timeout(120)
	void objectRegistryReturnsIdenticalInstanceAfterEviction(@TempDir final Path dir)
	{
		final KeyIdx idx = new KeyIdx();
		try(final EmbeddedStorageManager storage = EmbeddedStorage.start(dir))
		{
			final GigaMap<Rec> map = GigaMap.New();
			storage.setRoot(map);
			map.index().bitmap().add(idx);

			final Rec b = new Rec("b", "2");
			final long idB = map.add(b);
			storage.storeRoot();

			map.release(); // evict the clean, unused segment

			assertSame(b, map.get(idB),
				"object registry must return the identical, still strongly-held instance after eviction");
		}
	}

	@Test
	@Timeout(120)
	void removeOfIdenticalInstanceMustNotDependOnSegmentEviction(@TempDir final Path dir)
	{
		final KeyIdx idx = new KeyIdx();
		try(final EmbeddedStorageManager storage = EmbeddedStorage.start(dir))
		{
			final GigaMap<Rec> map = GigaMap.New();
			storage.setRoot(map);
			map.index().bitmap().add(idx);

			map.add(new Rec("a", "1"));
			final Rec b = new Rec("b", "2");
			final long idB = map.add(b);
			map.add(new Rec("c", "3"));
			storage.storeRoot();

			map.release(); // evict clean, unused segments

			final long removedId = map.remove(b);

			assertEquals(idB, removedId,
				"remove(b) must find and remove b although its segment was evicted");
			assertEquals(2, map.size());
		}
	}

	@Test
	@Timeout(120)
	void removeAfterReloadSucceedsProvingTheMissIsEvictionDependent(@TempDir final Path dir)
	{
		final KeyIdx idx = new KeyIdx();
		try(final EmbeddedStorageManager storage = EmbeddedStorage.start(dir))
		{
			final GigaMap<Rec> map = GigaMap.New();
			storage.setRoot(map);
			map.index().bitmap().add(idx);

			final Rec b = new Rec("b", "2");
			final long idB = map.add(b);
			storage.storeRoot();

			map.release();
			map.get(idB); // reload the segment

			assertEquals(idB, map.remove(b),
				"with the segment loaded, remove(b) must succeed - control case");
			assertEquals(0, map.size());
		}
	}

	/**
	 * Same as the {@code release()} variant, but eviction happens through the PRODUCTION path:
	 * {@code LazyReferenceManager.cleanUp} with a timeout checker - the exact mechanism the default
	 * 15-minute idle timeout ({@code Lazy.Checker.Defaults.defaultTimeout()}) applies to any segment
	 * untouched for that long.
	 */
	@Test
	@Timeout(120)
	void removeMissAlsoTriggersViaLazyReferenceManagerTimeout(@TempDir final Path dir)
		throws InterruptedException
	{
		final KeyIdx idx = new KeyIdx();
		try(final EmbeddedStorageManager storage = EmbeddedStorage.start(dir))
		{
			final GigaMap<Rec> map = GigaMap.New();
			storage.setRoot(map);
			map.index().bitmap().add(idx);

			final Rec b = new Rec("b", "2");
			final long idB = map.add(b);
			storage.storeRoot();

			Thread.sleep(50);
			// production eviction path: timeout checker, threshold far below the elapsed idle time.
			LazyReferenceManager.get().cleanUp(Lazy.Checker(1L));

			final long removedId = map.remove(b);

			assertEquals(idB, removedId,
				"remove(b) after LazyReferenceManager timeout eviction must find and remove b");
		}
	}

	@Test
	@Timeout(120)
	void updateAndReplaceNoLongerMissAfterEviction(@TempDir final Path dir)
	{
		final KeyIdx idx = new KeyIdx();
		try(final EmbeddedStorageManager storage = EmbeddedStorage.start(dir))
		{
			final GigaMap<Rec> map = GigaMap.New();
			storage.setRoot(map);
			map.index().bitmap().add(idx);

			final Rec b = new Rec("b", "2");
			map.add(b);
			storage.storeRoot();
			map.release();

			map.update(b, e -> e.data = "updated");
			assertEquals("updated", b.data, "update(b) must succeed although b's segment was evicted");

			// release() retains unstored changes (see GigaMap javadoc), so the just-mutated segment
			// must be persisted again before a second release() can actually evict it.
			storage.storeRoot();
			map.release();
			final Rec replacement = new Rec("b", "replaced");
			final long replacedId = map.replace(b, replacement);
			assertTrue(replacedId >= 0, "replace(b, b') must succeed although b's segment was evicted");
			assertSame(replacement, map.get(replacedId));
		}
	}

	@Test
	@Timeout(60)
	void nonUniqueHintRemovesOnlyTheIdentityMatch()
	{
		final KeyIdx idx = new KeyIdx();
		final GigaMap<Rec> map = GigaMap.New();
		map.index().bitmap().add(idx);

		final Rec e1 = new Rec("dup", "first");
		final Rec e2 = new Rec("dup", "second");
		final long id1 = map.add(e1);
		final long id2 = map.add(e2);

		final long removedId = map.remove(e2, idx); // hint matches BOTH candidates
		assertEquals(id2, removedId, "must resolve the identity match among same-key candidates");
		assertEquals(1, map.size());
		assertSame(e1, map.get(id1), "the other same-key entity must stay untouched");
	}

	@Test
	@Timeout(60)
	void foreignMapHintMustNotSilentlyMisbehave()
	{
		final KeyIdx idxA = new KeyIdx();
		final KeyIdx idxB = new KeyIdx(); // same name, registered in ANOTHER map
		final GigaMap<Rec> mapA = GigaMap.New();
		final GigaMap<Rec> mapB = GigaMap.New();
		mapA.index().bitmap().add(idxA);
		mapB.index().bitmap().add(idxB);

		final Rec a = new Rec("x", "inA");
		final long idA = mapA.add(a);
		mapB.add(new Rec("x", "inB"));

		final long removedId;
		try
		{
			removedId = mapA.remove(a, idxB); // hint from the foreign map
		}
		catch(final RuntimeException loud)
		{
			return; // loud rejection is acceptable
		}
		// silent acceptance is only OK if it did exactly the right thing
		assertEquals(idA, removedId, "foreign-map hint accepted silently - then it must still resolve correctly");
		assertEquals(0, mapA.size(), "entity removed from mapA");
		assertEquals(1, mapB.size(), "mapB untouched");
	}

	@Test
	@Timeout(60)
	void staleKeyAfterExternalMutationPinnedBehavior()
	{
		final KeyIdx idx = new KeyIdx();
		final GigaMap<Rec> map = GigaMap.New();
		map.index().bitmap().add(idx);

		final Rec e = new Rec("old", "1");
		final long id = map.add(e);

		e.key = "new"; // mutation WITHOUT update() - the index still holds "old"

		final long removedId = map.remove(e); // like(e) derives "new" -> no candidate
		assertEquals(-1, removedId, "pinned: stale-key entity is unlocatable by remove(E)");
		assertEquals(1, map.size(), "entity silently stays");
		// the escape hatch works:
		assertNotNull(map.removeById(id), "removeById is the reliable removal for mutated entities");
		assertEquals(0, map.size());
	}
}
