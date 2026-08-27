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

import java.lang.ref.WeakReference;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression test for the {@code apply()}/{@code update()} lost-update (internal issue #90): a
 * mutated-in-place entity was tracked for the next {@code store()} only via a {@code WeakReference}
 * in {@code pendingEntityStores}, <b>without pinning the level1 segment that owns it</b>. If the
 * segment was evicted ({@code release()} or {@code LazyReferenceManager} timeout) and the JVM GC
 * collected the mutated entity, {@code store()} silently skipped the dead reference — while the
 * (non-lazy) bitmap-index deltas committed anyway, leaving the persisted index and entity
 * permanently divergent on disk, with no exception ever thrown.
 * <p>
 * Mechanism (before the fix):
 * <ul>
 *   <li>{@code internalApply} mutated the entity in place and set the index change flags, but never
 *       called {@code markChanged} on the owning segment — so, unlike add/set/remove, the segment
 *       was not usage-pinned.</li>
 *   <li>{@code release()} (or LRM timeout) evicted the unpinned, already-stored segment; the entity's
 *       only strong root was gone and GC collected it.</li>
 *   <li>{@code internalRegisterChangeStores} then found {@code WeakReference.get() == null} and
 *       silently skipped the entity store, while the index deltas committed.</li>
 * </ul>
 * The fix marks the owning segment changed in {@code internalApply} (pinning it via
 * {@code Lazy.markUsedFor} exactly as the other mutation paths do), so the segment — and thus the
 * mutated entity — survives eviction and GC until it is stored. Everything here uses only public
 * GigaMap API.
 *
 * @see DirtySegmentEvictionLostUpdateTest the analogous remove-path regression (review finding GM-B)
 */
public class ApplyReleaseLostUpdateTest
{
	static final IndexerString<Item> VALUE_INDEXER = new IndexerString.Abstract<>()
	{
		@Override
		protected String getString(final Item entity)
		{
			return entity.value;
		}
	};

	@Test
	void applyMutationSurvivesReleaseAndGcBeforeStore(@TempDir final Path dir)
	{
		final long id;

		// ---- Session 1: create, store, apply-mutate, release, GC, store ----
		{
			final GigaMap<Item> map = GigaMap.New();
			map.index().bitmap().add(VALUE_INDEXER);

			id = map.add(new Item("OLD"));

			try(final EmbeddedStorageManager storage = EmbeddedStorage.start(map, dir))
			{
				storage.storeRoot(); // entity + index persisted, change flags cleared

				// In-place mutation via the public apply API.
				map.apply(id, e ->
				{
					e.value = "NEW";
					return null;
				});

				// Probe the mutated entity without keeping a strong reference to it, so the only thing
				// that can keep it alive is the (post-fix) usage-pinned segment.
				final WeakReference<Item> probe = new WeakReference<>(map.get(id));

				// release() promises unstored changes are retained (GigaMap.java javadoc). Pre-fix it
				// evicted the unpinned dirty segment.
				map.release();

				// Give the pre-fix path a chance to collect the now-unreferenced mutated entity. Post-fix
				// the pinned segment keeps it alive, so this returns as soon as its short budget is spent
				// (it must not busy-wait for the full timeout on the fixed code). Either way the
				// post-restart invariant below must hold, so a non-collecting GC does not make this flaky.
				forceGcUntilCollected(probe, 10, 10);

				map.store(); // pre-fix: silently skipped the dead WeakReference; index delta committed anyway
			}
		}

		// ---- Session 2: restart and verify index and entity agree ----
		{
			try(final EmbeddedStorageManager storage = EmbeddedStorage.start(dir))
			{
				@SuppressWarnings("unchecked")
				final GigaMap<Item> loaded = (GigaMap<Item>)storage.root();

				final Item item = loaded.get(id);

				// The committed index deltas say "NEW"; the entity must too (else: lost update + divergence).
				assertEquals("NEW", item.value, "apply() mutation must be persisted by store()");
				assertEquals(1, loaded.query(VALUE_INDEXER.is("NEW")).count(),
					"index must find the entity under its NEW value");
				assertEquals(0, loaded.query(VALUE_INDEXER.is("OLD")).count(),
					"index must no longer find the entity under its OLD value");
			}
		}
	}

	private static void forceGcUntilCollected(final WeakReference<?> ref, final int maxCycles, final long sleepMs)
	{
		// A single unreferenced small object is collected within the first cycle or two; a small bounded
		// budget therefore reproduces the pre-fix collection without penalizing the fixed (pinned) path,
		// which can never collect and would otherwise busy-wait for the whole budget.
		for(int i = 0; i < maxCycles && ref.get() != null; i++)
		{
			System.gc();
			try
			{
				Thread.sleep(sleepMs);
			}
			catch(final InterruptedException e)
			{
				Thread.currentThread().interrupt();
				return;
			}
		}
	}

	static final class Item
	{
		String value; // intentionally mutable: apply() mutates in place

		Item(final String value)
		{
			this.value = value;
		}

		@Override
		public String toString()
		{
			return "Item[" + this.value + "]";
		}
	}
}
