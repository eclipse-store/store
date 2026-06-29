
package org.eclipse.store.gigamap.crud;

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

import org.eclipse.store.gigamap.exceptions.UniqueConstraintViolationException;
import org.eclipse.store.gigamap.types.BinaryIndexerString;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for the entityId-based {@link GigaMap#apply(long, java.util.function.Function)} and
 * {@link GigaMap#update(long, java.util.function.Consumer)} overloads, which trigger reindexing
 * without requiring a bitmap index (issue #713).
 */
public class ApplyByIdTest
{
	@TempDir
	Path tempDir;

	/**
	 * A map with no bitmap index at all can still be updated in place via the entityId-based
	 * methods. The entity-instance based update()/apply() would throw IllegalStateException here.
	 */
	@Test
	void applyById_worksWithoutBitmapIndex()
	{
		final GigaMap<Item> gigaMap = GigaMap.New(); // no index registered

		final long id1 = gigaMap.add(new Item("item1", 1));
		gigaMap.add(new Item("item2", 2));

		final Integer result = gigaMap.apply(id1, item ->
		{
			item.setName("renamed");
			return item.getOrder();
		});

		assertEquals(1, result);
		assertEquals("renamed", gigaMap.get(id1).getName());
	}

	@Test
	void updateById_worksWithoutBitmapIndex()
	{
		final GigaMap<Item> gigaMap = GigaMap.New(); // no index registered

		final long id1 = gigaMap.add(new Item("item1", 1));

		final Item updated = gigaMap.update(id1, item -> item.setOrder(42));

		assertSame(gigaMap.get(id1), updated);
		assertEquals(42, gigaMap.get(id1).getOrder());
	}

	/**
	 * The in-place mutation must be re-persisted by store() (covered by the pendingEntityStores
	 * tracking) and survive a reload.
	 */
	@Test
	void applyById_persistsAcrossReload()
	{
		final GigaMap<Item> gigaMap = GigaMap.New();
		final long id = gigaMap.add(new Item("original", 1));

		try(final EmbeddedStorageManager storageManager = EmbeddedStorage.start(gigaMap, this.tempDir))
		{
			gigaMap.update(id, item -> item.setName("changed"));
			gigaMap.store();
		}

		try(final EmbeddedStorageManager storageManager = EmbeddedStorage.start(this.tempDir))
		{
			@SuppressWarnings("unchecked")
			final GigaMap<Item> loaded = (GigaMap<Item>)storageManager.root();
			assertEquals("changed", loaded.get(id).getName());
		}
	}

	@Test
	void applyById_invalidIdThrows()
	{
		final GigaMap<Item> gigaMap = GigaMap.New();
		gigaMap.add(new Item("item1", 1));

		assertThrows(IllegalArgumentException.class, () -> gigaMap.apply(-1L, item -> null));
		assertThrows(IllegalArgumentException.class, () -> gigaMap.apply(999L, item -> null));
	}

	/**
	 * Constraint-violation semantics must match the entity-instance based apply(): the offending
	 * entity is ejected from the map and the exception carries its id.
	 */
	@Test
	void applyById_constraintViolationEjectsEntity()
	{
		final BinaryIndexerString<Item> nameIndex = new BinaryIndexerString.Abstract<>()
		{
			@Override
			protected String getString(final Item item)
			{
				return item.getName();
			}
		};

		final GigaMap<Item> gigaMap = GigaMap.<Item>Builder()
			.withBitmapUniqueIndex(nameIndex)
			.build();

		final long aliceId = gigaMap.add(new Item("alice", 1));
		gigaMap.add(new Item("bob", 2));

		final UniqueConstraintViolationException ex = assertThrows(
			UniqueConstraintViolationException.class,
			() -> gigaMap.apply(aliceId, item -> { item.setName("bob"); return null; })
		);

		assertEquals(aliceId, ex.getEntityId());
		assertNull(gigaMap.peek(aliceId), "the offending entity must be ejected");
		assertEquals(1, gigaMap.size());
	}

	static class Item
	{
		private String  name;
		private Integer order;

		Item(final String name, final Integer order)
		{
			this.name  = name;
			this.order = order;
		}

		String getName()
		{
			return this.name;
		}

		void setName(final String name)
		{
			this.name = name;
		}

		Integer getOrder()
		{
			return this.order;
		}

		void setOrder(final Integer order)
		{
			this.order = order;
		}
	}
}
