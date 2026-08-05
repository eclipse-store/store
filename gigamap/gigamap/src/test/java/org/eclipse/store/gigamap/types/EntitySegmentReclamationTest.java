package org.eclipse.store.gigamap.types;

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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.serializer.reference.Lazy;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Emptying an entity segment has to release it (internal issue #120).
 * <p>
 * {@code GigaMap.Default#internalRemove} used to null only the entity slot, leaving the emptied
 * {@link GigaLevel1} referenced from its {@link GigaLevel2} and that one from the {@link GigaLevel3}
 * forever. Since ids are never recycled, an add/remove churn workload accumulated one dead segment per
 * exhausted id range - not collectable (still reachable through the segment tree), not freed by a
 * restart (persisted and reloaded), and not fixable by the application, because there is no
 * {@code compact()} and {@code removeAll()} would discard the live data as well. The bitmap index tree
 * has always self-cleaned ({@code BitmapLevel3#clearLevel2Segment}); the entity tree was the one place
 * that skipped it.
 * <p>
 * This test lives in the {@code types} package so it can read {@link GigaLevel3#segments} and
 * {@link GigaLevel2#segments} directly instead of reflectively.
 * <p>
 * Most cases use deliberately tiny segment dimensions: {@code GigaMap.New(1)} puts 2 entities into a
 * level1 segment, and {@code GigaMap.New(0, 8)} puts 1 entity into a level1 segment and 256 level1
 * segments into a level2 segment, so 256 ids empty a whole level2 - with 1 entity per level1 the adding
 * state is always already folded when a removal arrives, which isolates the level3 drop from the
 * adding-state handling.
 */
public class EntitySegmentReclamationTest
{
	static class Item
	{
		final String name;

		Item(final String name)
		{
			super();
			this.name = name;
		}
	}

	static class NameIndexer extends IndexerString.Abstract<Item>
	{
		@Override
		protected String getString(final Item entity)
		{
			return entity.name;
		}
	}

	private static final NameIndexer NAME = new NameIndexer();

	private static final String POISON = "poison";

	/** Refuses one particular value, to make an index update throw at the point that matters. */
	static class ThrowingNameIndexer extends IndexerString.Abstract<Item>
	{
		@Override
		protected String getString(final Item entity)
		{
			if(POISON.equals(entity.name))
			{
				throw new IllegalStateException("indexer refuses " + POISON);
			}
			return entity.name;
		}
	}

	/**
	 * Refuses one particular value exactly once, so that the rollback triggered by the failure can still
	 * clean the indices up (it re-runs the indexers to locate the entries to remove).
	 */
	static class FailOnceNameIndexer extends IndexerString.Abstract<Item>
	{
		private boolean refused;

		@Override
		protected String getString(final Item entity)
		{
			if(POISON.equals(entity.name) && !this.refused)
			{
				this.refused = true;
				throw new IllegalStateException("indexer refuses " + POISON + " once");
			}
			return entity.name;
		}
	}



	///////////////////////////////////////////////////////////////////////////
	// structural reclamation //
	///////////////////////////

	@Test
	public void emptyingALevel1SegmentReleasesIt()
	{
		// 2 entities per level1 segment: ids 0+1 in segment 0, ids 2+3 in segment 1.
		final GigaMap<Item> map = GigaMap.New(1);
		for(int i = 0; i < 4; i++)
		{
			map.add(new Item("item" + i));
		}
		assertEquals(2, nonNullLevel1Count(map), "two level1 segments to start with");

		map.removeById(0);
		assertNotNull(level1EntryOf(map, 0, 0), "one entity left, so the segment is still in use");

		map.removeById(1);
		assertNull(level1EntryOf(map, 0, 0), "the emptied level1 segment must be released");
		assertNotNull(level2EntryOf(map, 0), "the level2 segment still holds the second level1 segment");
		assertNotNull(level1EntryOf(map, 0, 1), "the untouched level1 segment stays");

		assertEquals(2L, map.size(), "the size only counts the surviving entities");
		assertEquals(3L, map.highestUsedId(), "ids are not recycled");
		assertNull(map.get(0), "the released ids read as empty slots");
		assertNull(map.get(1), "the released ids read as empty slots");
		assertNotNull(map.get(2), "the surviving entities are untouched");
		assertNotNull(map.get(3), "the surviving entities are untouched");
		assertEquals(2, countIterated(map), "iteration agrees with the size");
	}

	@Test
	public void emptyingTheLastLevel1ReleasesTheLevel2()
	{
		// 1 entity per level1 segment, 256 level1 segments per level2 segment: ids 0-255 in level2
		// segment 0, id 256 in level2 segment 1.
		final GigaMap<Item> map = GigaMap.New(0, 8);
		for(int i = 0; i < 257; i++)
		{
			map.add(new Item("item" + i));
		}
		assertEquals(2, nonNullLevel2Count(map), "two level2 segments to start with");

		for(long id = 0; id < 255; id++)
		{
			map.removeById(id);
		}
		assertNotNull(level2EntryOf(map, 0), "one entity left, so the level2 segment is still in use");

		map.removeById(255);
		assertNull(level2EntryOf(map, 0), "the emptied level2 segment must be released as well");
		assertNotNull(level2EntryOf(map, 1), "the untouched level2 segment stays");

		assertEquals(1L, map.size(), "the size only counts the surviving entity");
		assertEquals(256L, map.highestUsedId(), "ids are not recycled");
		assertNotNull(map.get(256), "the surviving entity is untouched");
		assertEquals(1, countIterated(map), "iteration agrees with the size");
	}

	/**
	 * The segment the next add writes into is cached in the map's adding state. Releasing it without
	 * folding that state first would make the next add write into a {@link GigaLevel1} no longer
	 * reachable from its level2 - a silently lost entity - and fail on the now-null slot while marking
	 * the segment changed. This is the ordinary case, not an exotic one: a map that has not filled its
	 * first segment yet removes entities with the adding state pointing right at them.
	 */
	@Test
	public void drainingTheAddingSegmentKeepsAddingConsistent()
	{
		final GigaMap<Item> map = GigaMap.New();
		map.add(new Item("a"));
		map.add(new Item("b"));
		map.add(new Item("c"));

		map.removeById(0);
		map.removeById(1);
		map.removeById(2);

		assertEquals(0L, map.size(), "everything was removed");
		assertEquals(0, nonNullLevel1Count(map), "the drained segment must be released");
		assertEquals(0, nonNullLevel2Count(map), "and its now empty level2 segment too");

		final Item d  = new Item("d");
		final long id = map.add(d);

		assertEquals(3L, id, "the next add gets a fresh id, the released ids are not recycled");
		assertEquals(1L, map.size(), "the new entity is counted");
		assertEquals(d, map.get(3), "the new entity is readable at its id, so the segment was re-created");
		assertNull(map.get(0), "the released ids stay empty");
		assertNull(map.get(1), "the released ids stay empty");
		assertNull(map.get(2), "the released ids stay empty");
		assertEquals(1, countIterated(map), "iteration agrees with the size");
	}

	/**
	 * The counterpart: draining a segment that is not the one being added to must leave the adding state
	 * alone, or every removal would cost a needless adding-state setup.
	 */
	@Test
	public void drainingANonAddingSegmentLeavesTheAddingStateAlone()
	{
		// 4 entities per level1 segment: ids 0-3 in segment 0, id 4 in segment 1 (the adding one).
		final GigaMap<Item> map = GigaMap.New(2);
		for(int i = 0; i < 5; i++)
		{
			map.add(new Item("item" + i));
		}

		for(long id = 0; id < 4; id++)
		{
			map.removeById(id);
		}

		assertNull(level1EntryOf(map, 0, 0), "the drained segment is released");
		assertNotNull(level1EntryOf(map, 0, 1), "the adding segment is untouched");
		assertEquals(1L, map.size(), "one entity left");
		assertEquals(4L, map.highestUsedId(), "the adding state was not disturbed");

		assertEquals(5L, map.add(new Item("item5")), "adding continues in the still live adding segment");
		assertEquals(2L, map.size(), "both remaining entities are counted");
	}

	/**
	 * The workload from the issue in its harshest form: a queue of depth one. Every removal empties the
	 * segment the following add then resumes in, so nothing may accumulate at all.
	 */
	@Test
	public void interleavedAddRemoveDoesNotAccumulateSegments()
	{
		this.assertInterleavedAddRemoveKeepsNothing(GigaMap.New(1) , 2000);
		this.assertInterleavedAddRemoveKeepsNothing(GigaMap.New()  , 2000);
		this.assertInterleavedAddRemoveKeepsNothing(GigaMap.New(0, 8), 2000);
	}

	private void assertInterleavedAddRemoveKeepsNothing(final GigaMap<Item> map, final int cycles)
	{
		for(int i = 0; i < cycles; i++)
		{
			map.removeById(map.add(new Item("item" + i)));
		}

		assertEquals(0L, map.size(), "nothing is left alive");
		assertEquals(0, countIterated(map), "iteration agrees with the size");
		assertTrue(nonNullLevel1Count(map) <= 1,
			"emptied level1 segments must not accumulate, found " + nonNullLevel1Count(map));
		assertTrue(nonNullLevel2Count(map) <= 1,
			"emptied level2 segments must not accumulate, found " + nonNullLevel2Count(map));
		assertEquals(cycles - 1, map.highestUsedId(), "ids are still not recycled");

		final Item last = new Item("last");
		final long id   = map.add(last);
		assertEquals(cycles, id, "the map is still usable");
		assertEquals(last, map.get(id), "and the entity is readable");
	}



	///////////////////////////////////////////////////////////////////////////
	// persistence //
	////////////////

	/**
	 * The released slot has to reach the disk. A level2 slot is covered by the change marking a removal
	 * already did, but the level3's own array is only rewritten when the level3 instance itself is
	 * flagged - without that flag the drop would be in-memory only and a restart would resurrect a whole
	 * subtree of removed entities, while the persisted size and id counter would know nothing about them.
	 */
	@Test
	public void releasedLevel1StaysReleasedAcrossRestart(@TempDir final Path dir)
	{
		{
			final GigaMap<Item> map = GigaMap.<Item>New(1);
			map.index().bitmap().add(NAME);
			final EmbeddedStorageManager storage = EmbeddedStorage.start(map, dir);
			for(int i = 0; i < 4; i++)
			{
				map.add(new Item("item" + i));
			}
			storage.storeRoot();

			map.removeById(0);
			map.removeById(1);
			assertNull(level1EntryOf(map, 0, 0), "the emptied level1 segment is released in memory");

			map.store();
			storage.shutdown();
		}

		final EmbeddedStorageManager storage = EmbeddedStorage.start(dir);
		try
		{
			final GigaMap<Item> map = storage.root();

			assertNull(level1EntryOf(map, 0, 0), "the released level1 segment must not come back");
			assertEquals(2L, map.size(), "the size survived");
			assertEquals(2, countIterated(map), "iteration agrees with the size");
			assertNull(map.get(0), "the removed entities stay removed");
			assertNull(map.get(1), "the removed entities stay removed");
			assertNotNull(map.get(2), "the surviving entities survived");
			assertEquals(0L, map.query(NAME, "item0").count(), "and nothing resurrected into the index");
			assertEquals(1L, map.query(NAME, "item2").count(), "the surviving entities are still indexed");
			assertEquals(4L, map.add(new Item("item4")), "adding continues at the next fresh id");
		}
		finally
		{
			storage.shutdown();
		}
	}

	@Test
	public void releasedLevel2StaysReleasedAcrossRestart(@TempDir final Path dir)
	{
		{
			final GigaMap<Item> map = GigaMap.<Item>New(0, 8);
			map.index().bitmap().add(NAME);
			final EmbeddedStorageManager storage = EmbeddedStorage.start(map, dir);
			for(int i = 0; i < 257; i++)
			{
				map.add(new Item("item" + i));
			}
			storage.storeRoot();

			for(long id = 0; id < 256; id++)
			{
				map.removeById(id);
			}
			assertNull(level2EntryOf(map, 0), "the emptied level2 segment is released in memory");

			map.store();
			storage.shutdown();
		}

		final EmbeddedStorageManager storage = EmbeddedStorage.start(dir);
		try
		{
			final GigaMap<Item> map = storage.root();

			assertNull(level2EntryOf(map, 0), "the released level2 segment must not come back");
			assertEquals(1L, map.size(), "the size survived");
			assertEquals(1, countIterated(map), "iteration agrees with the size");
			assertNull(map.get(0), "the removed entities stay removed");
			assertNotNull(map.get(256), "the surviving entity survived");
			assertEquals(0L, map.query(NAME, "item0").count(), "and nothing resurrected into the index");
			assertEquals(1L, map.query(NAME, "item256").count(), "the surviving entity is still indexed");
			assertEquals(257L, map.add(new Item("item257")), "adding continues at the next fresh id");
		}
		finally
		{
			storage.shutdown();
		}
	}

	/**
	 * A segment release is a structural change that is not stored yet, so the segment carrying it must
	 * not be evicted before the store - otherwise the store would silently skip it while the index and
	 * size updates commit anyway. Same guarantee as
	 * {@code org.eclipse.store.gigamap.issues.DirtySegmentEvictionLostUpdateTest}, for the release.
	 */
	@Test
	public void releasedSegmentSurvivesReleaseBeforeStore(@TempDir final Path dir)
	{
		{
			final GigaMap<Item> map = GigaMap.<Item>New(1);
			map.index().bitmap().add(NAME);
			final EmbeddedStorageManager storage = EmbeddedStorage.start(map, dir);
			for(int i = 0; i < 4; i++)
			{
				map.add(new Item("item" + i));
			}
			storage.storeRoot();

			map.removeById(0);
			map.removeById(1);

			map.release();
			map.store();
			storage.shutdown();
		}

		final EmbeddedStorageManager storage = EmbeddedStorage.start(dir);
		try
		{
			final GigaMap<Item> map = storage.root();

			assertNull(level1EntryOf(map, 0, 0), "the release must have been stored, not evicted");
			assertEquals(2L, map.size(), "the size survived");
			assertEquals(2, countIterated(map), "iteration agrees with the size");
			assertNull(map.get(0), "no resurrection");
			assertEquals(0L, map.query(NAME, "item0").count(), "and no index divergence");
		}
		finally
		{
			storage.shutdown();
		}
	}

	/**
	 * The scenario reported in the issue: monotonic fill-one-segment / empty-it cycles with a store per
	 * cycle. It used to leave one dead level1 segment per cycle behind, persisted, reloaded and immune
	 * to a full garbage collection because the segments stayed reachable.
	 */
	@Test
	public void monotonicFillEmptyCyclesDoNotRetainSegments(@TempDir final Path dir)
	{
		final int perSegment = 256; // exactly one level1 segment with the default dimensions
		final int cycles     = 30 ;

		{
			final GigaMap<Item> map = GigaMap.New();
			map.index().bitmap().add(NAME);
			final EmbeddedStorageManager storage = EmbeddedStorage.start(map, dir);
			storage.storeRoot();

			for(int c = 0; c < cycles; c++)
			{
				final long[] ids = new long[perSegment];
				for(int i = 0; i < perSegment; i++)
				{
					ids[i] = map.add(new Item("item" + (c * perSegment + i)));
				}
				for(final long id : ids)
				{
					map.removeById(id);
				}
				map.store();
			}
			storage.issueFullGarbageCollection();
			storage.shutdown();
		}

		final EmbeddedStorageManager storage = EmbeddedStorage.start(dir);
		try
		{
			final GigaMap<Item> map = storage.root();

			assertEquals(0L, map.size(), "the workload leaves nothing alive");
			assertEquals(0, countIterated(map), "iteration agrees with the size");
			assertTrue(nonNullLevel1Count(map) <= 1,
				"emptied level1 segments must not be persisted and reloaded, found "
				+ nonNullLevel1Count(map) + " with 0 live entities");
			assertEquals(cycles * perSegment - 1, map.highestUsedId(), "ids are still not recycled");
		}
		finally
		{
			storage.shutdown();
		}
	}



	///////////////////////////////////////////////////////////////////////////
	// writing back into a released id //
	////////////////////////////////////

	/**
	 * {@code set(id, entity)} into a slot a removal emptied is the supported way to undo a removal at its
	 * own id (see {@code test.eclipse.store.storer.SetAfterRemoveSizeTest}). It has to keep working when
	 * the removals released the whole segment, which means re-creating it.
	 */
	@Test
	public void setIntoAReleasedLevel1RematerializesIt()
	{
		final GigaMap<Item> map = GigaMap.<Item>New(1);
		map.index().bitmap().add(NAME);
		for(int i = 0; i < 4; i++)
		{
			map.add(new Item("item" + i));
		}

		map.removeById(0);
		map.removeById(1);
		assertNull(level1EntryOf(map, 0, 0), "the segment is released");

		final Item restored = new Item("item0");
		assertNull(map.set(0, restored), "nothing was replaced, the slot was empty");

		assertNotNull(level1EntryOf(map, 0, 0), "the segment was re-created");
		assertEquals(3L, map.size(), "filling the released slot restores the size");
		assertEquals(restored, map.get(0), "the entity is back at its own id");
		assertEquals(1L, map.query(NAME, "item0").count(), "and is indexed");
		assertEquals(4L, map.add(new Item("item4")), "adding still gets the next fresh id");
	}

	@Test
	public void setIntoAReleasedLevel2RematerializesIt()
	{
		final GigaMap<Item> map = GigaMap.<Item>New(0, 8);
		map.index().bitmap().add(NAME);
		for(int i = 0; i < 257; i++)
		{
			map.add(new Item("item" + i));
		}

		for(long id = 0; id < 256; id++)
		{
			map.removeById(id);
		}
		assertNull(level2EntryOf(map, 0), "the level2 segment is released");

		final Item restored = new Item("item0");
		assertNull(map.set(0, restored), "nothing was replaced, the slot was empty");

		assertNotNull(level2EntryOf(map, 0), "the level2 segment was re-created");
		assertNotNull(level1EntryOf(map, 0, 0), "and so was the level1 segment");
		assertEquals(2L, map.size(), "filling the released slot restores the size");
		assertEquals(restored, map.get(0), "the entity is back at its own id");
		assertEquals(1L, map.query(NAME, "item0").count(), "and is indexed");
		assertEquals(257L, map.add(new Item("item257")), "adding still gets the next fresh id");
	}

	/**
	 * A failed {@code set} must leave the map observably unchanged, which includes not leaving a freshly
	 * created, empty segment (and a pending re-store for it) behind. So the segment is materialized only
	 * after the last thing that can throw.
	 */
	@Test
	public void aFailedSetIntoAReleasedSegmentCreatesNoSegment()
	{
		final GigaMap<Item> map = GigaMap.<Item>New(1);
		map.index().bitmap().add(new ThrowingNameIndexer());
		for(int i = 0; i < 4; i++)
		{
			map.add(new Item("item" + i));
		}

		map.removeById(0);
		map.removeById(1);
		assertNull(level1EntryOf(map, 0, 0), "the segment is released");

		assertThrows(RuntimeException.class, () -> map.set(0, new Item(POISON)),
			"the indexer refuses this entity");

		assertNull(level1EntryOf(map, 0, 0), "a failed set must not re-create the segment");
		assertEquals(2L, map.size(), "a failed set must leave the size as it was");
		assertNull(map.get(0), "and the slot still empty");

		// The map is still usable, and a successful restore still works.
		final Item restored = new Item("item0");
		assertNull(map.set(0, restored), "the slot was still empty");
		assertEquals(3L, map.size(), "a subsequent successful restore counts");
		assertEquals(restored, map.get(0), "and is readable");
	}

	/**
	 * A rolled-back add releases the segment it had just created for its entity, and since the rollback
	 * reclaims the id as well, the following add has to re-create that very segment.
	 * <p>
	 * The indexer fails only on its first call, because the rollback re-runs the indexers to locate the
	 * entries to clean up: an indexer that keeps throwing aborts the cleanup, on which the rollback
	 * deliberately keeps both the id and - since the reclamation is the last step of a removal - the
	 * segment, so that the stale index entries can never alias a future entity.
	 */
	@Test
	public void rollbackAfterAFailedAddReleasesTheFreshSegment()
	{
		final GigaMap<Item> map = GigaMap.<Item>New(1);
		map.index().bitmap().add(new FailOnceNameIndexer());
		map.add(new Item("item0"));
		map.add(new Item("item1"));
		assertEquals(1, nonNullLevel1Count(map), "both entities are in the first level1 segment");

		assertThrows(RuntimeException.class, () -> map.add(new Item(POISON)),
			"the indexer refuses this entity once");

		assertEquals(1, nonNullLevel1Count(map), "the segment the rolled back add created is released");
		assertNull(level1EntryOf(map, 0, 1), "specifically the one for the rolled back id");
		assertEquals(2L, map.size(), "the failed add is not counted");

		final Item item2 = new Item("item2");
		assertEquals(2L, map.add(item2), "the rolled back id is handed out again");
		assertNotNull(level1EntryOf(map, 0, 1), "and its segment was re-created");
		assertEquals(item2, map.get(2), "the entity is readable");
		assertEquals(3L, map.size(), "and counted");
	}

	/**
	 * An indexer that keeps throwing aborts the rollback's index cleanup, and the rollback then keeps
	 * the id unreclaimed on purpose. The segment goes with it: a removal reclaims its segment only after
	 * the index removal succeeded, so a half-failed removal leaves the segment tree untouched.
	 */
	@Test
	public void rollbackWithAFailingCleanupKeepsTheFreshSegment()
	{
		final GigaMap<Item> map = GigaMap.<Item>New(1);
		map.index().bitmap().add(new ThrowingNameIndexer());
		map.add(new Item("item0"));
		map.add(new Item("item1"));

		assertThrows(RuntimeException.class, () -> map.add(new Item(POISON)),
			"the indexer refuses this entity");

		assertNotNull(level1EntryOf(map, 0, 1), "the segment of the uncleaned id is kept");
		assertEquals(2L, map.size(), "the failed add is not counted");
		assertNull(map.get(2), "but its slot is empty");
		assertEquals(3L, map.add(new Item("item3")), "and its id is not handed out again");
	}



	///////////////////////////////////////////////////////////////////////////
	// released ids on the other entity paths //
	///////////////////////////////////////////

	@Test
	public void applyOnAReleasedIdFailsCleanly()
	{
		final GigaMap<Item> map = GigaMap.<Item>New(1);
		map.index().bitmap().add(NAME);
		for(int i = 0; i < 4; i++)
		{
			map.add(new Item("item" + i));
		}

		map.removeById(0);
		map.removeById(1);

		assertThrows(IllegalArgumentException.class, () -> map.apply(0L, e -> e),
			"there is no entity at a released id");
		assertNull(map.removeById(0), "removing a released id again is a no-op");
		assertNull(map.peek(0), "peeking a released id yields nothing");
		assertEquals(2L, map.size(), "none of that changed the size");
	}

	/**
	 * The update logic runs while this map's (reentrant) monitor is held, so it can remove the very
	 * entity it is applied to - and with a segment of 2 that removal can release the segment underneath
	 * the running apply. Whatever else that reports, it must not be a {@link NullPointerException} from
	 * the persistence bookkeeping dereferencing a segment that is gone.
	 */
	@Test
	public void reentrantRemovalFromApplyLogicDoesNotHitAGoneSegment()
	{
		final GigaMap<Item> map = GigaMap.<Item>New(1);
		map.index().bitmap().add(NAME);
		final long a = map.add(new Item("a"));
		map.add(new Item("b"));
		map.add(new Item("c"));
		map.removeById(1); // leaves only id 0 in the first level1 segment

		try
		{
			map.apply(a, e ->
			{
				map.removeById(a);
				return e;
			});
		}
		catch(final NullPointerException e)
		{
			fail("the released segment must not cause a NullPointerException", e);
		}
		catch(final RuntimeException e)
		{
			// Reentrant mutation from update logic is not a supported use, so any other report is fine.
		}

		assertNull(level1EntryOf(map, 0, 0), "the segment was released");
		assertNull(map.get(a), "the entity is gone");
		assertEquals(1L, map.size(), "only the untouched entity is left");
		assertEquals(1, countIterated(map), "iteration agrees with the size");
	}

	@Test
	public void removeAllAndReleaseTolerateReleasedSegments()
	{
		final GigaMap<Item> map = GigaMap.<Item>New(0, 8);
		map.index().bitmap().add(NAME);
		for(int i = 0; i < 257; i++)
		{
			map.add(new Item("item" + i));
		}
		for(long id = 0; id < 256; id++)
		{
			map.removeById(id);
		}
		assertNull(level2EntryOf(map, 0), "the level2 segment is released");

		map.release();
		map.removeAll();

		assertEquals(0L, map.size(), "removeAll cleared the rest");
		assertEquals(0, countIterated(map), "iteration agrees with the size");
		assertEquals(0L, map.add(new Item("fresh")), "removeAll reset the id counter");
		assertEquals(1L, map.size(), "and the map is usable again");
	}



	///////////////////////////////////////////////////////////////////////////
	// helpers //
	////////////

	private static <E> GigaLevel3<E> level3(final GigaMap<E> map)
	{
		return ((GigaMap.Default<E>)map).level3();
	}

	private static <E> Lazy<GigaLevel2<E>> level2EntryOf(final GigaMap<E> map, final int level3Index)
	{
		return level3(map).segments[level3Index];
	}

	private static <E> Lazy<GigaLevel1<E>> level1EntryOf(
		final GigaMap<E> map        ,
		final int        level3Index,
		final int        level2Index
	)
	{
		final Lazy<GigaLevel2<E>> level2Entry = level2EntryOf(map, level3Index);

		return level2Entry == null
			? null
			: level2Entry.get().segments[level2Index]
		;
	}

	/**
	 * Counts the level1 segments the segment tree still holds. Deliberately counts non-null
	 * {@link Lazy} slots, not loaded instances, so the count measures retention and is unaffected by
	 * lazy eviction.
	 */
	private static <E> int nonNullLevel1Count(final GigaMap<E> map)
	{
		int count = 0;
		for(final Lazy<GigaLevel2<E>> level2Entry : level3(map).segments)
		{
			if(level2Entry == null)
			{
				continue;
			}
			for(final Lazy<GigaLevel1<E>> level1Entry : level2Entry.get().segments)
			{
				if(level1Entry != null)
				{
					count++;
				}
			}
		}

		return count;
	}

	private static <E> int nonNullLevel2Count(final GigaMap<E> map)
	{
		int count = 0;
		for(final Lazy<GigaLevel2<E>> level2Entry : level3(map).segments)
		{
			if(level2Entry != null)
			{
				count++;
			}
		}

		return count;
	}

	private static <E> int countIterated(final GigaMap<E> map)
	{
		final AtomicLong count = new AtomicLong();
		map.iterate(e -> count.incrementAndGet());

		return (int)count.get();
	}

}
