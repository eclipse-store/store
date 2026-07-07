package org.eclipse.store.gigamap.issues;

/*-
 * #%L
 * EclipseStore GigaMap
 * %%
 * Copyright (C) 2023 - 2025 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.IndexerString;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Regression test for the dirty-segment eviction lost update (review finding GM-B): a
 * mutated-but-not-yet-stored GigaMap segment could be evicted, after which
 * {@code storeChangedChildren} silently skipped it — the mutation was lost while the (non-lazy)
 * bitmap-index and size updates WERE persisted, leaving index and entity segments permanently
 * divergent on disk, with no exception ever thrown.
 * <p>
 * Mechanism:
 * <ul>
 *   <li>{@code GigaMap.internalRemove} nulls the entity slot in the in-memory {@code GigaLevel1}
 *       and calls {@code markChanged} — the change flags live on the {@code GigaLevel1}/
 *       {@code GigaLevel2} <i>instances behind the Lazy references</i>.</li>
 *   <li>{@code markChanged} pins the child via {@code Lazy.markUsedFor}, but before the fix
 *       {@code isUsed()} had zero callers in any clearing path — the pin was write-only.
 *       {@code Lazy.Default.clear} gated only on {@code isStored()}.</li>
 *   <li>{@code GigaMap.release()} cleared every stored level-2 Lazy with no usage-mark check,
 *       discarding the changed instances and their flags.</li>
 *   <li>{@code storeChangedChildren} then {@code peek()}ed null for the evicted child and
 *       silently continued.</li>
 * </ul>
 * The fix consults the usage mark: {@code release()} retains used-marked (dirty) segments, and
 * the serializer-side {@code Lazy.Default.clear(ClearingEvaluator)} guard exempts them from
 * {@code LazyReferenceManager} eviction. Everything here uses only public GigaMap API:
 * add, store, remove, release, store.
 * <p>
 * Adopted from the reproducer {@code DirtySegmentEvictionLostUpdate_GMB} by hg-ms
 * (hg-ms/EclipseStoreDev, branch missingObjectReproducers).
 */
public class DirtySegmentEvictionLostUpdateTest
{
	static final IndexerString<Person> NAME_INDEXER = new IndexerString.Abstract<>()
	{
		@Override
		protected String getString(final Person entity)
		{
			return entity.name;
		}
	};

	@Test
	void removalSurvivesReleaseBeforeStore(@TempDir final Path dir)
	{
		final long bobId;

		// ---- Session 1: create, store, remove, evict, store ----
		{
			final GigaMap<Person> map = GigaMap.New();
			map.index().bitmap().add(NAME_INDEXER);

			map.add(new Person("alice"));
			final Person bob = new Person("bob");
			bobId = map.add(bob);
			map.add(new Person("carol"));

			try(final EmbeddedStorageManager storage = EmbeddedStorage.start(map, dir))
			{
				storage.storeRoot();     // everything persisted, change flags cleared

				map.remove(bob);         // in-memory: slot nulled, segment marked changed + pinned
				map.release();           // pre-fix: evicted the dirty segment along with the clean ones

				/*
				 * CAUTION - Heisenbug: any map access here (get/iterate/query) re-resolves an
				 * evicted Lazy via the object registry's weak-reference fast path, handing the
				 * SAME dirty instance back to the Lazy and accidentally healing the subsequent
				 * store. The window must stay untouched for the pre-fix bug to manifest.
				 */

				map.store();             // pre-fix: storeChangedChildren peek()ed null -> silent skip
			}
		}

		// ---- Session 2: restart and inspect what was actually persisted ----
		{
			try(final EmbeddedStorageManager storage = EmbeddedStorage.start(dir))
			{
				@SuppressWarnings("unchecked")
				final GigaMap<Person> loaded = (GigaMap<Person>)storage.root();

				final long[] iterated = {0L};
				loaded.iterate(p -> iterated[0]++);

				// pre-fix divergence: get(bobId) != null while the index query already found 0 hits
				assertNull(loaded.get(bobId), "removed entity must not survive in the segment");
				assertEquals(0, loaded.query(NAME_INDEXER.is("bob")).count(), "removed entity must not be indexed");
				assertEquals(2, loaded.size(), "size must reflect the removal");
				assertEquals(2, iterated[0], "iteration must agree with size and index");
			}
		}
	}

	public static class Person
	{
		final String name;

		public Person(final String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return "Person[" + this.name + "]";
		}
	}
}
