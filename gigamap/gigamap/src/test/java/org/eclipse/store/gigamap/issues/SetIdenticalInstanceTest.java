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

import org.eclipse.serializer.hashing.XHashing;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.IndexerString;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for internal issue #131, symptom A: {@code set(id, theInstanceAlreadyMappedThere)}
 * used to be a complete silent no-op.
 * <p>
 * The ordinary "load the entity, mutate an indexed field, write it back" idiom - which the class-level
 * documentation of {@link GigaMap} and the Javadoc of {@link GigaMap#reindex()} both named as a
 * sanctioned write path - hit an equality gate in {@code internalSet} that skipped the index update,
 * the change marking, and any entry in the pending entity stores. The indices kept answering under the
 * old key, {@code store()} persisted nothing, and a restart silently reverted the mutation - while
 * {@code set} returned normally with no signal whatsoever.
 * <p>
 * A functional fix is impossible: an entity's pre-mutation index keys are no longer derivable once it
 * has been mutated in place, which is exactly why {@link GigaMap#update(long, java.util.function.Consumer)}
 * and {@link GigaMap#apply(long, java.util.function.Function)} capture them <em>before</em> running the
 * caller's logic. So the call is now rejected with an {@link IllegalArgumentException} that names those
 * two methods - symmetric with {@link GigaMap#replace(Object, Object)}, which has always rejected
 * {@code current == replacement}.
 * <p>
 * Note the no-op was useless on <em>every</em> map configuration, indexed or not: the change marking it
 * skipped would only have re-stored the owning segment, and that segment holds an already-persisted
 * object id, so the storer never re-serializes the entity's changed content either.
 *
 * @see SetValueEqualInstanceTest the sibling symptom - a value-equal but distinct replacement was
 *      discarded by the same gate
 */
public class SetIdenticalInstanceTest
{
	static final KeyIndexer KEY = new KeyIndexer();

	/**
	 * The call must be rejected rather than quietly doing nothing, and the map must be untouched.
	 */
	@Test
	void setWithTheInstanceAlreadyMappedThrows()
	{
		final GigaMap<Item> map = GigaMap.New();
		map.index().bitmap().add(KEY);

		final Item item = new Item("a");
		final long id   = map.add(item);
		map.add(new Item("b"));

		final IllegalArgumentException e = assertThrows(
			IllegalArgumentException.class,
			() -> map.set(id, item)
		);

		// The message has to send the caller somewhere useful, since there is nothing set() can do.
		assertTrue(e.getMessage().contains("update"),
			"the message must point at update(long, Consumer) / apply(long, Function), was: " + e.getMessage());

		assertEquals(2L, map.size()          , "a rejected set must not change the size");
		assertSame  (item, map.get(id)       , "the entity must still be mapped to its id");
		assertEquals(1L, map.query(KEY.is("a")).count(), "and must still be indexed exactly once");
	}

	/**
	 * The exact idiom from the issue report: mutate an indexed field in place, then write the instance
	 * back. Pre-fix this returned normally and lost both the index update and the mutation itself.
	 * Post-fix it throws, and the documented remedies repair the state.
	 */
	@Test
	void setAfterDirectMutationThrowsInsteadOfSilentlyDoingNothing(@TempDir final Path dir)
	{
		final GigaMap<Item> map = GigaMap.New();
		map.index().bitmap().add(KEY);

		final Item item = new Item("old");
		final long id   = map.add(item);

		item.key = "new";

		assertThrows(
			IllegalArgumentException.class,
			() -> map.set(id, item),
			"the direct mutation cannot be re-indexed by set(), so it must be rejected, not ignored"
		);

		// The rejection does not repair the staleness the direct mutation caused - it only stops the
		// caller from believing it was repaired. The index still answers under the old key:
		assertEquals(1L, map.query(KEY.is("old")).count(), "the index is stale, as documented");
		assertEquals(0L, map.query(KEY.is("new")).count(), "and does not know the new key yet");

		// reindex() is the documented repair for a direct mutation, and it must actually work here.
		map.reindex();
		assertEquals(0L, map.query(KEY.is("old")).count(), "reindex() must drop the stale key");
		assertEquals(1L, map.query(KEY.is("new")).count(), "and index the current one");

		try(final EmbeddedStorageManager storage = EmbeddedStorage.start(map, dir))
		{
			storage.storeRoot();
		}
		try(final EmbeddedStorageManager storage = EmbeddedStorage.start(dir))
		{
			@SuppressWarnings("unchecked")
			final GigaMap<Item> loaded = (GigaMap<Item>)storage.root();

			assertEquals("new", loaded.get(id).key            , "the repaired mutation must be persisted");
			assertEquals(1L, loaded.query(KEY.is("new")).count(), "and the repaired index with it");
		}
	}

	/**
	 * The interaction guard for the sibling fix (internal issue #130, {@code 6b7fb2fa}): restoring an
	 * entity into a slot that {@code removeById} emptied is {@code set(id, theSameInstance)} - the only
	 * id-addressed write there is - and must keep working.
	 * <p>
	 * It can never hit the new guard, because an emptied slot holds {@code null} while the entity passed
	 * in is non-null; this pins that reasoning so the guard cannot later be widened into breaking the
	 * restore.
	 */
	@Test
	void setIntoAnEmptiedSlotWithTheSameInstanceIsAllowed(@TempDir final Path dir)
	{
		final GigaMap<Item> map = GigaMap.New();
		map.index().bitmap().add(KEY);

		final long id = map.add(new Item("a"));
		map.add(new Item("b"));

		final Item removed = map.get(id);
		map.removeById(id);
		assertEquals(1L, map.size(), "the removal is accounted for");

		assertNull(map.set(id, removed), "nothing was replaced, so null is returned");

		assertEquals(2L, map.size()                    , "the restore must count again");
		assertSame  (removed, map.get(id)              , "and be reachable by its original id");
		assertEquals(1L, map.query(KEY.is("a")).count(), "and be indexed again");

		try(final EmbeddedStorageManager storage = EmbeddedStorage.start(map, dir))
		{
			storage.storeRoot();
		}
		try(final EmbeddedStorageManager storage = EmbeddedStorage.start(dir))
		{
			@SuppressWarnings("unchecked")
			final GigaMap<Item> loaded = (GigaMap<Item>)storage.root();

			assertEquals(2L, loaded.size()                    , "the restored size is persisted");
			assertNotNull(loaded.get(id)                      , "and the restored entity with it");
			assertEquals(1L, loaded.query(KEY.is("a")).count(), "and it is still indexed");
		}
	}

	/**
	 * The guard lives in {@code internalSet} rather than in {@code set} for this case: on a value-equality
	 * map holding two value-equal instances, {@code replace} resolves {@code current} to the lowest
	 * matching id, which can be the <em>replacement's own</em> id. Guarding {@code set} alone would leave
	 * that silently doing nothing while returning a plausible id.
	 */
	@Test
	void replaceResolvingToTheReplacementsOwnIdThrows()
	{
		final GigaMap<Item> map = GigaMap.New(XHashing.hashEqualityValue());
		map.index().bitmap().add(KEY);

		// Value-equal per Item#equals (which compares the key only), distinct instances.
		final Item first  = new Item("a");
		final Item second = new Item("a");
		map.add(second); // lower id, so the lookup below resolves to this one
		map.add(first);

		assertThrows(
			IllegalArgumentException.class,
			() -> map.replace(first, second),
			"the lookup resolves to second's own id, so this is set(id, theInstanceAlreadyThere)"
		);

		assertEquals(2L, map.size(), "a rejected replace must not change the size");
	}

	/**
	 * The contract must not depend on the index configuration: with no index at all there is nothing to
	 * re-derive keys for, but the mutation would still go unpersisted, so the rejection stands.
	 */
	@Test
	void setWithTheSameInstanceThrowsOnAMapWithoutIndices()
	{
		final GigaMap<Item> map = GigaMap.New();

		final Item item = new Item("a");
		final long id   = map.add(item);

		assertThrows(
			IllegalArgumentException.class,
			() -> map.set(id, item)
		);
	}

	static final class KeyIndexer extends IndexerString.Abstract<Item>
	{
		@Override
		protected String getString(final Item entity)
		{
			return entity.key;
		}
	}

	static final class Item
	{
		String key; // intentionally mutable: the tests mutate it in place

		Item(final String key)
		{
			this.key = key;
		}

		@Override
		public boolean equals(final Object other)
		{
			return other instanceof Item item && item.key.equals(this.key);
		}

		@Override
		public int hashCode()
		{
			return this.key.hashCode();
		}

		@Override
		public String toString()
		{
			return "Item[" + this.key + "]";
		}
	}
}
