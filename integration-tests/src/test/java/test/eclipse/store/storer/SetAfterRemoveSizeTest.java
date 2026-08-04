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

import java.nio.file.Path;

import org.eclipse.store.gigamap.types.GigaMap;
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
		String name;

		Item(final String name)
		{
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
