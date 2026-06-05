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

import org.eclipse.store.gigamap.exceptions.ConstraintViolationException;
import org.eclipse.store.gigamap.types.BinaryIndexerString;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.IndexerString;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the structural removal of a registered bitmap index via {@link org.eclipse.store.gigamap.types.BitmapIndices#removeIndex(String)}.
 */
public class IndexRemovalTest
{
	static class Person
	{
		String name;
		String city;

		Person()
		{
			// required for deserialization
		}

		Person(final String name, final String city)
		{
			this.name = name;
			this.city = city;
		}
	}

	static final IndexerString<Person> NAME = new IndexerString.Abstract<>()
	{
		@Override
		public String name()
		{
			return "name";
		}

		@Override
		protected String getString(final Person e)
		{
			return e.name;
		}
	};

	static final IndexerString<Person> CITY = new IndexerString.Abstract<>()
	{
		@Override
		public String name()
		{
			return "city";
		}

		@Override
		protected String getString(final Person e)
		{
			return e.city;
		}
	};

	// binary string index -> backing index is suitable as a unique constraint
	static final BinaryIndexerString<Person> UNAME = new BinaryIndexerString.Abstract<>()
	{
		@Override
		public String name()
		{
			return "uname";
		}

		@Override
		protected String getString(final Person e)
		{
			return e.name;
		}
	};

	@SuppressWarnings("unchecked")
	private static GigaMap<Person> reload(final EmbeddedStorageManager m)
	{
		return (GigaMap<Person>)m.root();
	}

	@Test
	void removeIndex_returnsTrue_andIndexGone()
	{
		final GigaMap<Person> map = GigaMap.New();
		map.index().bitmap().add(NAME);
		map.add(new Person("alice", "NYC"));

		assertNotNull(map.index().bitmap().get("name"));
		assertTrue(map.index().bitmap().removeIndex("name"));
		assertNull(map.index().bitmap().get("name"));
	}

	@Test
	void removeIndex_unknownName_returnsFalse()
	{
		final GigaMap<Person> map = GigaMap.New();
		map.index().bitmap().add(NAME);

		assertFalse(map.index().bitmap().removeIndex("does-not-exist"));
	}

	@Test
	void removeIndex_isIdempotent()
	{
		final GigaMap<Person> map = GigaMap.New();
		map.index().bitmap().add(NAME);

		assertTrue(map.index().bitmap().removeIndex("name"));
		assertFalse(map.index().bitmap().removeIndex("name"));
	}

	@Test
	void removeIndex_siblingIndexesUnaffected()
	{
		final GigaMap<Person> map = GigaMap.New();
		map.index().bitmap().add(NAME);
		map.index().bitmap().add(CITY);
		map.add(new Person("alice", "NYC"));
		map.add(new Person("bob", "NYC"));

		map.index().bitmap().removeIndex("name");

		assertNull(map.index().bitmap().get("name"));
		assertNotNull(map.index().bitmap().get("city"));
		assertEquals(2, map.query(CITY.is("NYC")).count());
	}

	@Test
	void removeIndex_entitiesIntact()
	{
		final GigaMap<Person> map = GigaMap.New();
		map.index().bitmap().add(NAME);
		map.add(new Person("alice", "NYC"));
		map.add(new Person("bob", "LA"));

		map.index().bitmap().removeIndex("name");

		assertEquals(2, map.size());
	}

	@Test
	void removeIndex_identityIndex_throws()
	{
		final GigaMap<Person> map = GigaMap.New();
		map.index().bitmap().add(NAME);
		map.index().bitmap().setIdentityIndices(NAME);
		map.add(new Person("alice", "NYC"));

		assertThrows(RuntimeException.class, () -> map.index().bitmap().removeIndex("name"));
		assertNotNull(map.index().bitmap().get("name"));
	}

	@Test
	void removeIndex_uniqueIndex_alsoLiftsUniqueness()
	{
		final GigaMap<Person> map = GigaMap.New();
		map.index().bitmap().addUniqueConstraint(UNAME);
		map.add(new Person("alice", "NYC"));

		assertThrows(ConstraintViolationException.class, () -> map.add(new Person("alice", "LA")));

		assertTrue(map.index().bitmap().removeIndex("uname"));
		assertTrue(map.index().bitmap().uniqueConstraints() == null || map.index().bitmap().uniqueConstraints().isEmpty());

		// duplicate is now allowed
		map.add(new Person("alice", "LA"));
		assertEquals(2, map.size());
	}

	@Test
	void removeIndex_thenReAddSameName_backfillsExisting()
	{
		final GigaMap<Person> map = GigaMap.New();
		map.index().bitmap().add(NAME);
		map.add(new Person("alice", "NYC"));

		map.index().bitmap().removeIndex("name");
		map.index().bitmap().add(NAME); // name freed; re-add must back-fill existing entities

		assertEquals(1, map.query(NAME.is("alice")).count());
	}

	@Test
	void removeIndex_readOnly_throws()
	{
		final GigaMap<Person> map = GigaMap.New();
		map.index().bitmap().add(NAME);
		map.add(new Person("alice", "NYC"));
		map.markReadOnly();

		assertThrows(RuntimeException.class, () -> map.index().bitmap().removeIndex("name"));
		assertNotNull(map.index().bitmap().get("name"));

		map.unmarkReadOnly();
	}

	@Test
	void removeIndex_afterReload(@TempDir final Path dir)
	{
		final GigaMap<Person> map = GigaMap.New();
		map.index().bitmap().add(NAME);
		map.index().bitmap().add(CITY);
		map.add(new Person("alice", "NYC"));
		map.add(new Person("bob", "NYC"));
		try(final EmbeddedStorageManager m = EmbeddedStorage.start(map, dir))
		{
			// initial state stored on close
		}

		try(final EmbeddedStorageManager m = EmbeddedStorage.start(dir))
		{
			final GigaMap<Person> reloaded = reload(m);
			assertTrue(reloaded.index().bitmap().removeIndex("name"));
			reloaded.store();
		}

		try(final EmbeddedStorageManager m = EmbeddedStorage.start(dir))
		{
			final GigaMap<Person> reloaded = reload(m);
			assertNull(reloaded.index().bitmap().get("name"));
			assertNotNull(reloaded.index().bitmap().get("city"));
			assertEquals(2, reloaded.query(CITY.is("NYC")).count());
			assertEquals(2, reloaded.size());
		}
	}
}
