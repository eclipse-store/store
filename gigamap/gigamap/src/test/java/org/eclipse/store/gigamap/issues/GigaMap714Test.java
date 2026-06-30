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

import org.eclipse.store.gigamap.data.Entity;
import org.eclipse.store.gigamap.exceptions.ConstraintViolationException;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.IndexerString;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies that {@link GigaMap#reindex()} rebuilds a bitmap index from the current entity state, recovering
 * from the stale-index situation that arises when an indexed field is mutated directly (i.e. not via
 * {@code update()} / {@code apply()}) - issue #714.
 */
public class GigaMap714Test
{
	@Test
	void reindex_recoversStaleBitmapIndex()
	{
		final GigaMap<Entity> map = GigaMap.New();
		map.index().bitmap().add(Entity.wordIndex);

		final Entity entity = Entity.Random().setWord("alpha");
		map.add(entity);
		map.add(Entity.Random().setWord("gamma"));

		// direct mutation - bypasses the indexers, so the bitmap index stays stale
		entity.setWord("beta");

		assertEquals(1, map.query(Entity.wordIndex.is("alpha")).toList().size(), "index is expected to be stale before reindex");
		assertEquals(0, map.query(Entity.wordIndex.is("beta")).toList().size(), "new key must not be indexed before reindex");

		map.reindex();

		assertEquals(0, map.query(Entity.wordIndex.is("alpha")).toList().size(), "stale key must be gone after reindex");
		assertEquals(1, map.query(Entity.wordIndex.is("beta")).toList().size(), "updated key must be reindexed");
	}

	@Test
	void reindex_preservesUniqueConstraint()
	{
		final GigaMap<Entity> map = GigaMap.New();
		map.index().bitmap().addUniqueConstraint(Entity.uniqueWordIndex);

		map.add(Entity.Random().setWord("a"));
		map.add(Entity.Random().setWord("b"));

		// rebuilding valid (still unique) data must not fail
		map.reindex();

		assertEquals(1, map.query(Entity.uniqueWordIndex.is("a")).toList().size());
		assertEquals(1, map.query(Entity.uniqueWordIndex.is("b")).toList().size());

		// the unique constraint must still be enforced after the rebuild
		assertThrows(
			ConstraintViolationException.class,
			() -> map.add(Entity.Random().setWord("a"))
		);
	}

	@Test
	void reindex_persistsRebuiltIndex(@TempDir final Path dir)
	{
		final NameIndexer nameIndexer = new NameIndexer();
		final Customer    alice       = new Customer("Alice");

		final GigaMap<Customer> map = GigaMap.New();
		map.index().bitmap().add(nameIndexer);
		map.add(alice);
		map.add(new Customer("Bob"));

		try(final EmbeddedStorageManager manager = EmbeddedStorage.start(map, dir))
		{
			// initial graph is persisted by start(); now mutate an indexed field directly (index goes stale).
			// A direct mutation is not tracked by GigaMap, so the entity must be stored explicitly; reindex()
			// then rebuilds the index structures, which gigaMap.store() persists.
			alice.setName("Carol");
			manager.store(alice);
			map.reindex();
			map.store();
		}

		// reopen and confirm the rebuilt index AND the entity state were persisted consistently
		try(final EmbeddedStorageManager manager = EmbeddedStorage.start(dir))
		{
			final GigaMap<Customer> root = manager.root();
			assertEquals(0, root.query(nameIndexer.is("Alice")).count(), "stale key must not survive a reload");

			final List<Customer> carol = root.query(nameIndexer.is("Carol")).toList();
			assertEquals(1, carol.size(), "rebuilt key must be persisted");
			assertEquals("Carol", carol.get(0).getName(), "entity state and index must be consistent after reload");

			assertEquals(1, root.query(nameIndexer.is("Bob")).count());
		}
	}

	static class NameIndexer extends IndexerString.Abstract<Customer>
	{
		@Override
		protected String getString(final Customer entity)
		{
			return entity.getName();
		}
	}

	static class Customer
	{
		private String name;

		Customer(final String name)
		{
			this.name = name;
		}

		String getName()
		{
			return this.name;
		}

		void setName(final String name)
		{
			this.name = name;
		}
	}
}
