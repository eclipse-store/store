package org.eclipse.store.gigamap.restart;

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

import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.IndexerString;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Restart round-trips for the GigaMap failure semantics when an indexer throws mid-mutation:
 * the state persisted by a {@code store()} after a failed mutation must be internally consistent
 * (dirty-flag correctness), i.e. entity segments, indices and size must agree after a reload.
 */
public class ThrowingIndexerRestartTest
{
	static final class Item
	{
		final String name;

		Item(final String name)
		{
			this.name = name;
		}
	}

	static final IndexerString<Item> NAME = new IndexerString.Abstract<>()
	{
		@Override
		protected String getString(final Item entity)
		{
			return entity.name;
		}
	};

	/** Throws for entities named "poison", healthy for all others. */
	static final IndexerString<Item> POISON = new IndexerString.Abstract<>()
	{
		@Override
		protected String getString(final Item entity)
		{
			if("poison".equals(entity.name))
			{
				throw new RuntimeException("poison name");
			}
			return entity.name;
		}
	};

	/** Throws whenever {@link #toggleArmed} is set, regardless of the entity's value. */
	static volatile boolean toggleArmed = false;

	static final IndexerString<Item> TOGGLE = new IndexerString.Abstract<>()
	{
		@Override
		protected String getString(final Item entity)
		{
			if(toggleArmed)
			{
				throw new RuntimeException("toggle boom");
			}
			return entity.name;
		}
	};

	@BeforeEach
	@AfterEach
	void disarm()
	{
		toggleArmed = false;
	}

	@Test
	void failedAddSurvivesRestartConsistently(@TempDir final Path dir)
	{
		// ---- Session 1: store a valid entity, fail an add, store again ----
		{
			final GigaMap<Item> map = GigaMap.New();
			map.index().bitmap().add(NAME);
			map.index().bitmap().add(POISON);

			try(final EmbeddedStorageManager storage = EmbeddedStorage.start(map, dir))
			{
				map.add(new Item("alice"));
				storage.storeRoot();

				assertThrows(RuntimeException.class, () -> map.add(new Item("poison")));
				map.store();
			}
		}

		// ---- Session 2: everything the failed add touched must have been rolled back ----
		{
			try(final EmbeddedStorageManager storage = EmbeddedStorage.start(dir))
			{
				@SuppressWarnings("unchecked")
				final GigaMap<Item> loaded = (GigaMap<Item>)storage.root();

				assertEquals(1, loaded.size());
				assertNull(loaded.get(1L));
				assertEquals(1, loaded.query(NAME.is("alice")).count());
				assertEquals(0, loaded.query(NAME.is("poison")).count());

				// the poison entity's failed cleanup burned its id; it must not be reused after reload
				final long bobId = loaded.add(new Item("bob"));
				assertEquals(2L, bobId);
				assertEquals(2, loaded.size());
				assertEquals(1, loaded.query(NAME.is("bob")).count());
			}
		}
	}

	@Test
	void failedRemoveSurvivesRestartConsistently(@TempDir final Path dir)
	{
		final long aliceId;

		// ---- Session 1: store two entities, fail a remove, store again ----
		{
			final GigaMap<Item> map = GigaMap.New();
			map.index().bitmap().add(NAME);
			map.index().bitmap().add(TOGGLE);

			try(final EmbeddedStorageManager storage = EmbeddedStorage.start(map, dir))
			{
				aliceId = map.add(new Item("alice"));
				map.add(new Item("bob"));
				storage.storeRoot();

				toggleArmed = true;
				assertThrows(RuntimeException.class, () -> map.removeById(aliceId));
				toggleArmed = false;

				map.store();
			}
		}

		// ---- Session 2: the removal must be durable and consistent ----
		{
			try(final EmbeddedStorageManager storage = EmbeddedStorage.start(dir))
			{
				@SuppressWarnings("unchecked")
				final GigaMap<Item> loaded = (GigaMap<Item>)storage.root();

				assertNull(loaded.get(aliceId));
				assertEquals(1, loaded.size());
				assertEquals(0, loaded.query(NAME.is("alice")).count());
				assertEquals(1, loaded.query(NAME.is("bob")).count());
				// residue in the broken index refers to an empty id and is query-invisible
				assertEquals(0, loaded.query(TOGGLE.is("alice")).count());

				// reindex() repairs the residue; the map is fully usable afterwards
				loaded.reindex();
				loaded.add(new Item("carol"));
				assertEquals(2, loaded.size());
				assertEquals(1, loaded.query(TOGGLE.is("carol")).count());
				assertEquals(1, loaded.query(NAME.is("carol")).count());

				loaded.store();
			}
		}

		// ---- Session 3: the reindexed state must also be durable ----
		{
			try(final EmbeddedStorageManager storage = EmbeddedStorage.start(dir))
			{
				@SuppressWarnings("unchecked")
				final GigaMap<Item> loaded = (GigaMap<Item>)storage.root();

				assertEquals(2, loaded.size());
				assertEquals(1, loaded.query(NAME.is("bob")).count());
				assertEquals(1, loaded.query(NAME.is("carol")).count());
				assertEquals(0, loaded.query(TOGGLE.is("alice")).count());
			}
		}
	}
}
