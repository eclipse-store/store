package org.eclipse.store.gigamap.indexer;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers mutation of entities through the entity-locator path (update / replace / remove by entity)
 * after a {@link GigaMap} has been reloaded from storage, using indexes that were registered with an
 * <em>anonymous</em> indexer (the common {@code new IndexerString.Abstract<>(){...}} idiom).
 * <p>
 * Regression test: an anonymous {@link IndexerString.Abstract} loses its (transient) derived name on
 * reload. The mutation locator builds its lookup condition from the indexer
 * (see {@code HashingBitmapIndex#like}), so a recomputed, divergent fallback name used to make index
 * resolution fail with {@code IllegalArgumentException: "... does not have an index defined with name ..."}.
 * The reloaded indexer must report the name the index is registered/persisted under.
 */
public class AnonymousIndexerReloadMutationTest
{
	static class Person
	{
		String name;

		Person()
		{
			// required for deserialization
		}

		Person(final String name)
		{
			this.name = name;
		}
	}

	static final IndexerString<Person> NAME_INDEX = new IndexerString.Abstract<>()
	{
		@Override
		protected String getString(final Person e)
		{
			return e.name;
		}
	};

	private static GigaMap<Person> newStoredMap(final Path dir, final String... names)
	{
		final GigaMap<Person> map = GigaMap.New();
		map.index().bitmap().add(NAME_INDEX);
		for(final String name : names)
		{
			map.add(new Person(name));
		}
		try(final EmbeddedStorageManager m = EmbeddedStorage.start(map, dir))
		{
			// initial state stored on close
		}
		return map;
	}

	@SuppressWarnings("unchecked")
	private static GigaMap<Person> reload(final EmbeddedStorageManager m)
	{
		return (GigaMap<Person>)m.root();
	}

	@Test
	void queryAfterReloadResolvesAnonymousIndex(@TempDir final Path dir)
	{
		newStoredMap(dir, "alice");

		try(final EmbeddedStorageManager m = EmbeddedStorage.start(dir))
		{
			final GigaMap<Person> reloaded = reload(m);

			assertEquals(1, reloaded.query(NAME_INDEX.is("alice")).count());
			assertEquals(0, reloaded.query(NAME_INDEX.is("bob")).count());
		}
	}

	@Test
	void updateAfterReloadResolvesAnonymousIndex(@TempDir final Path dir)
	{
		newStoredMap(dir, "alice");

		try(final EmbeddedStorageManager m = EmbeddedStorage.start(dir))
		{
			final GigaMap<Person> reloaded = reload(m);
			final Person alice = reloaded.query(NAME_INDEX.is("alice")).toList().get(0);

			reloaded.update(alice, p -> p.name = "bob");

			assertEquals(1, reloaded.query(NAME_INDEX.is("bob")).count());
			assertEquals(0, reloaded.query(NAME_INDEX.is("alice")).count());
		}
	}

	@Test
	void updateAfterReloadPersistsAcrossSecondReload(@TempDir final Path dir)
	{
		newStoredMap(dir, "alice");

		try(final EmbeddedStorageManager m = EmbeddedStorage.start(dir))
		{
			final GigaMap<Person> reloaded = reload(m);
			final Person alice = reloaded.query(NAME_INDEX.is("alice")).toList().get(0);

			reloaded.update(alice, p -> p.name = "bob");
			reloaded.store();
		}

		try(final EmbeddedStorageManager m = EmbeddedStorage.start(dir))
		{
			final GigaMap<Person> reloaded = reload(m);

			assertEquals(1, reloaded.query(NAME_INDEX.is("bob")).count());
			assertEquals(0, reloaded.query(NAME_INDEX.is("alice")).count());
		}
	}

	@Test
	void replaceAfterReloadResolvesAnonymousIndex(@TempDir final Path dir)
	{
		newStoredMap(dir, "alice");

		try(final EmbeddedStorageManager m = EmbeddedStorage.start(dir))
		{
			final GigaMap<Person> reloaded = reload(m);
			final Person alice = reloaded.query(NAME_INDEX.is("alice")).toList().get(0);

			reloaded.replace(alice, new Person("bob"));

			assertEquals(1, reloaded.query(NAME_INDEX.is("bob")).count());
			assertEquals(0, reloaded.query(NAME_INDEX.is("alice")).count());
		}
	}

	@Test
	void removeByEntityAfterReloadResolvesAnonymousIndex(@TempDir final Path dir)
	{
		newStoredMap(dir, "alice", "bob");

		try(final EmbeddedStorageManager m = EmbeddedStorage.start(dir))
		{
			final GigaMap<Person> reloaded = reload(m);
			final Person alice = reloaded.query(NAME_INDEX.is("alice")).toList().get(0);

			final long removedId = reloaded.remove(alice);

			assertEquals(0, reloaded.query(NAME_INDEX.is("alice")).count());
			assertEquals(1, reloaded.query(NAME_INDEX.is("bob")).count());
			assertEquals(1, reloaded.size());
			assertTrue(removedId >= 0);
		}
	}
}
