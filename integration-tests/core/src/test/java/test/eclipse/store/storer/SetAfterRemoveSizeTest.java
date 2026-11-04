package test.eclipse.store.storer;

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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import java.util.function.ToLongFunction;

import org.eclipse.store.gigamap.types.BinaryIndexerLong;
import org.eclipse.store.gigamap.types.BinaryIndexerUUID;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.Indexer;
import org.eclipse.store.gigamap.types.IndexerInstant;
import org.eclipse.store.gigamap.types.IndexerString;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Putting an entity back at an id that was removed has to restore the size too.
 *
 * <p>{@link GigaMap#removeById(long)} decrements the size and empties the slot;
 * {@link GigaMap#set(long, Object)} fills a slot again. Filling a slot that a removal emptied left the size
 * one too low, permanently and persistently - the entity was present and queryable, so the map disagreed with
 * itself about how much it held, and a restart preserved the disagreement.
 *
 * <p>This is the shape an undo log needs: restoring an entity a rolled-back operation removed is
 * {@code set(id, snapshot)}, because it is the only id-addressed write.
 */
public class SetAfterRemoveSizeTest
{
	static class Item
	{
		String  name     ;
		long    code     ;
		UUID    uuid     ;
		Instant timestamp;

		Item(final String name)
		{
			this(name, 0L, new UUID(0L, 0L), Instant.EPOCH);
		}

		Item(
			final String  name     ,
			final long    code     ,
			final UUID    uuid     ,
			final Instant timestamp
		)
		{
			this.name      = name     ;
			this.code      = code     ;
			this.uuid      = uuid     ;
			this.timestamp = timestamp;
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

	/** Plain binary index: one bitmap entry per bit position of the indexed long. */
	private static final BinaryIndexerLong<Item> CODE = new BinaryIndexerLong.Abstract<>()
	{
		@Override
		protected Long getLong(final Item entity)
		{
			return entity.code;
		}
	};

	/** Composite binary index: a UUID does not fit into one long, so it is split across sub indices. */
	private static final BinaryIndexerUUID<Item> UUID_CODE = new BinaryIndexerUUID.Abstract<>()
	{
		@Override
		protected UUID getUUID(final Item entity)
		{
			return entity.uuid;
		}
	};

	/** Composite hashing index: an Instant is decomposed into year, month, day, ... sub indices. */
	private static final IndexerInstant<Item> TIMESTAMP = new IndexerInstant.Abstract<>()
	{
		@Override
		protected Instant getInstant(final Item entity)
		{
			return entity.timestamp;
		}
	};

	private static final UUID    UUID_A      = new UUID(1L, 1L);
	private static final Instant TIMESTAMP_A = Instant.parse("2026-01-01T10:15:30Z");

	private static Item itemA()
	{
		return new Item("a", 1L, UUID_A, TIMESTAMP_A);
	}

	private static Item itemB()
	{
		return new Item("b", 2L, new UUID(2L, 2L), Instant.parse("2026-02-02T20:25:35Z"));
	}

	@Test
	public void settingAnEntityBackIntoARemovedSlotRestoresTheSize(@TempDir final Path dir)
	{
		final EmbeddedStorageManager storage = EmbeddedStorage.start(dir);
		final GigaMap<Item> map = storage.ensureRoot(
			() -> GigaMap.<Item>Builder().withBitmapIndex(NAME).build());

		final long a = map.add(new Item("a"));
		map.add(new Item("b"));
		map.store();
		assertEquals(2L, map.size(), "two entities to start with");

		map.removeById(a);
		assertEquals(1L, map.size(), "the removal is accounted for");
		assertNull(map.get(a), "and the slot is empty");

		map.set(a, new Item("a"));
		assertEquals(2L, map.size(), "filling the removed slot again must restore the size");
		assertNotNull(map.get(a), "the entity is back at its own id");
		assertEquals(1L, map.query(NAME, "a").count(), "and is indexed");

		map.store();
		storage.shutdown();

		final EmbeddedStorageManager reopened = EmbeddedStorage.start(dir);
		final GigaMap<Item> fromDisk = reopened.root();
		assertEquals(2L, fromDisk.size(), "the size is persisted correctly, not one too low");
		assertNotNull(fromDisk.get(a), "the restored entity survived");
		assertEquals(1L, fromDisk.query(NAME, "a").count(), "and is still indexed");
		reopened.shutdown();
	}

	/**
	 * A failed restore must not count. {@code internalSet} orders its work so that a throw leaves the map
	 * observably unchanged, and the size is part of that: incrementing it before the index update would leave
	 * the count raised while the slot and the indices were untouched - trading a size that was too low for one
	 * that is too high, on a path that already reports failure.
	 *
	 * <p>The failure comes from an indexer that throws while deriving the new entity's keys, which is the last
	 * user code {@code internalSet} runs before it commits to the change.
	 */
	@Test
	public void aRestoreThatThrowsDoesNotChangeTheSize(@TempDir final Path dir)
	{
		final EmbeddedStorageManager storage = EmbeddedStorage.start(dir);
		final GigaMap<Item> map = storage.ensureRoot(
			() -> GigaMap.<Item>Builder().withBitmapIndex(new ThrowingNameIndexer()).build());

		final long a = map.add(new Item("a"));
		map.add(new Item("b"));
		map.store();
		assertEquals(2L, map.size(), "two entities to start with");

		map.removeById(a);
		assertEquals(1L, map.size(), "the removal is accounted for");

		assertThrows(RuntimeException.class, () -> map.set(a, new Item(POISON)),
			"the indexer refuses this entity");

		assertEquals(1L, map.size(), "a failed restore must leave the size as it was");
		assertNull(map.get(a), "and the slot still empty");

		// The map is still usable, and a successful restore still counts.
		map.set(a, new Item("a"));
		assertEquals(2L, map.size(), "a subsequent successful restore counts");

		storage.shutdown();
	}

	private static final String POISON = "poison";

	/** Refuses one particular value, to make the index update throw at the point that matters. */
	static class ThrowingNameIndexer extends IndexerString.Abstract<Item>
	{
		@Override
		protected String getString(final Item entity)
		{
			if (POISON.equals(entity.name))
			{
				throw new IllegalStateException("indexer refuses " + POISON);
			}
			return entity.name;
		}
	}

	/**
	 * The same restore on a map with a <em>binary</em> index. Its change handler read the previous entity's
	 * keys straight off the handler it was handed, casting it without a check - and the handler for an empty
	 * slot is the no-op one every other implementation tolerates, so the restore threw a
	 * {@code ClassCastException} instead of indexing the entity.
	 */
	@Test
	public void settingAnEntityBackIntoARemovedSlotWorksWithABinaryIndex(@TempDir final Path dir)
	{
		this.assertRestoreIntoEmptiedSlot(dir, CODE, map -> map.query(CODE.is(1L)).count());
	}

	/** Composite binary index (a UUID spread over sub indices): same unguarded cast, same throw. */
	@Test
	public void settingAnEntityBackIntoARemovedSlotWorksWithACompositeBinaryIndex(@TempDir final Path dir)
	{
		this.assertRestoreIntoEmptiedSlot(dir, UUID_CODE, map -> map.query(UUID_CODE.is(UUID_A)).count());
	}

	/** Composite hashing index (an Instant decomposed into date/time parts): likewise. */
	@Test
	public void settingAnEntityBackIntoARemovedSlotWorksWithACompositeHashingIndex(@TempDir final Path dir)
	{
		this.assertRestoreIntoEmptiedSlot(dir, TIMESTAMP, map -> map.query(TIMESTAMP.is(TIMESTAMP_A)).count());
	}

	/**
	 * Removes the entity at one id and puts an equal one back at that same id, then checks that the map is
	 * coherent about it: counted, reachable by id, findable through the index - before and after a restart.
	 *
	 * @param dir the storage directory for this test
	 * @param indexer the single index the map is built with
	 * @param restoredCount counts the entities the index reports for the restored entity's key
	 */
	private void assertRestoreIntoEmptiedSlot(
		final Path                          dir          ,
		final Indexer<? super Item, ?>      indexer      ,
		final ToLongFunction<GigaMap<Item>> restoredCount
	)
	{
		final EmbeddedStorageManager storage = EmbeddedStorage.start(dir);
		final GigaMap<Item> map = storage.ensureRoot(
			() -> GigaMap.<Item>Builder().withBitmapIndex(indexer).build());

		final long a = map.add(itemA());
		map.add(itemB());
		map.store();
		assertEquals(1L, restoredCount.applyAsLong(map), "the entity is indexed to start with");

		map.removeById(a);
		assertEquals(1L, map.size(), "the removal is accounted for");
		assertEquals(0L, restoredCount.applyAsLong(map), "and de-indexed the entity");

		map.set(a, itemA());
		assertEquals(2L, map.size(), "filling the removed slot again must restore the size");
		assertNotNull(map.get(a), "the entity is back at its own id");
		assertEquals(1L, restoredCount.applyAsLong(map), "and is indexed under its key again");

		map.store();
		storage.shutdown();

		final EmbeddedStorageManager reopened = EmbeddedStorage.start(dir);
		final GigaMap<Item> fromDisk = reopened.root();
		assertEquals(2L, fromDisk.size(), "the size is persisted correctly, not one too low");
		assertNotNull(fromDisk.get(a), "the restored entity survived");
		assertEquals(1L, restoredCount.applyAsLong(fromDisk), "and is still indexed");
		reopened.shutdown();
	}

	/** A plain replacement must not change the size - the slot was occupied, so nothing was restored. */
	@Test
	public void replacingAnOccupiedSlotLeavesTheSizeAlone(@TempDir final Path dir)
	{
		final EmbeddedStorageManager storage = EmbeddedStorage.start(dir);
		final GigaMap<Item> map = storage.ensureRoot(
			() -> GigaMap.<Item>Builder().withBitmapIndex(NAME).build());

		final long a = map.add(new Item("a"));
		map.add(new Item("b"));
		map.store();

		map.set(a, new Item("a2"));
		assertEquals(2L, map.size(), "a replacement is not an addition");
		assertEquals(1L, map.query(NAME, "a2").count(), "the replacement is indexed");
		assertEquals(0L, map.query(NAME, "a" ).count(), "the replaced key is gone");

		storage.shutdown();
	}
}
