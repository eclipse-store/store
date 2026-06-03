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
import org.eclipse.store.gigamap.types.IndexerInteger;
import org.eclipse.store.gigamap.types.IndexerString;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Covers {@link org.eclipse.store.gigamap.types.BitmapIndices#update(org.eclipse.store.gigamap.types.Indexer)}:
 * replacing an existing index' logic and rebuilding its data from all current entities, including
 * unique-constraint and identity-index preservation.
 */
public class IndexerRedefinitionTest
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

	// two indexers registered under the SAME name "key", but different logic
	static final IndexerString<Person> FIRST_LETTER = new IndexerString.Abstract<>()
	{
		@Override
		public String name()
		{
			return "key";
		}

		@Override
		protected String getString(final Person e)
		{
			return e.name.substring(0, 1);
		}
	};

	static final IndexerString<Person> FULL_NAME = new IndexerString.Abstract<>()
	{
		@Override
		public String name()
		{
			return "key";
		}

		@Override
		protected String getString(final Person e)
		{
			return e.name;
		}
	};

	static final IndexerInteger<Person> NAME_LENGTH = new IndexerInteger.Abstract<>()
	{
		@Override
		public String name()
		{
			return "key";
		}

		@Override
		protected Integer getInteger(final Person e)
		{
			return e.name.length();
		}
	};

	// case-sensitive vs case-folding binary string indexers, both named "u" (suitable as unique)
	static final BinaryIndexerString<Person> U_CASE_SENSITIVE = new BinaryIndexerString.Abstract<>()
	{
		@Override
		public String name()
		{
			return "u";
		}

		@Override
		protected String getString(final Person e)
		{
			return e.name;
		}
	};

	static final BinaryIndexerString<Person> U_LOWERCASE = new BinaryIndexerString.Abstract<>()
	{
		@Override
		public String name()
		{
			return "u";
		}

		@Override
		protected String getString(final Person e)
		{
			return e.name.toLowerCase();
		}
	};

	static final IndexerString<Person> NAME_IDENTITY = new IndexerString.Abstract<>()
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

	static final IndexerString<Person> NAME_IDENTITY_2 = new IndexerString.Abstract<>()
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

	@SuppressWarnings("unchecked")
	private static GigaMap<Person> reload(final EmbeddedStorageManager m)
	{
		return (GigaMap<Person>)m.root();
	}

	@Test
	void update_rebuildsAllExistingEntities()
	{
		final GigaMap<Person> map = GigaMap.New();
		map.index().bitmap().add(FIRST_LETTER);
		map.add(new Person("alice"));
		map.add(new Person("anna"));
		map.add(new Person("bob"));

		assertEquals(2, map.query(FIRST_LETTER.is("a")).count());

		map.index().bitmap().update(FULL_NAME);

		// data was rebuilt under the new (full-name) logic
		assertEquals(1, map.query(FULL_NAME.is("alice")).count());
		assertEquals(1, map.query(FULL_NAME.is("anna")).count());
		assertEquals(0, map.query(FIRST_LETTER.is("a")).count()); // no entity's full name is "a"
	}

	@Test
	void update_emptyMap_swapsLogic()
	{
		final GigaMap<Person> map = GigaMap.New();
		map.index().bitmap().add(FIRST_LETTER);

		map.index().bitmap().update(FULL_NAME);
		map.add(new Person("alice"));

		assertEquals(1, map.query(FULL_NAME.is("alice")).count());
	}

	@Test
	void update_unknownName_throws()
	{
		final GigaMap<Person> map = GigaMap.New();

		assertThrows(RuntimeException.class, () -> map.index().bitmap().update(FULL_NAME));
	}

	@Test
	void update_preservesUniqueConstraint()
	{
		final GigaMap<Person> map = GigaMap.New();
		map.index().bitmap().addUniqueConstraint(U_CASE_SENSITIVE);
		map.add(new Person("alice"));
		map.add(new Person("bob"));

		map.index().bitmap().update(U_LOWERCASE);

		// uniqueness is still enforced under the new logic
		assertThrows(ConstraintViolationException.class, () -> map.add(new Person("ALICE")));
		assertEquals(1, map.index().bitmap().uniqueConstraints().size());
	}

	@Test
	void update_uniqueViolationDuringRebuild_throws_andKeepsOldIndex()
	{
		final GigaMap<Person> map = GigaMap.New();
		map.index().bitmap().addUniqueConstraint(U_CASE_SENSITIVE);
		map.add(new Person("Alice"));
		map.add(new Person("alice")); // distinct under case-sensitive logic

		// under the lowercase logic both collapse to "alice" -> rebuild must fail
		assertThrows(ConstraintViolationException.class, () -> map.index().bitmap().update(U_LOWERCASE));

		// atomic failure: entities intact and the original (case-sensitive) constraint still enforced
		assertEquals(2, map.size());
		assertThrows(ConstraintViolationException.class, () -> map.add(new Person("Alice")));
	}

	@Test
	void update_preservesIdentityIndex()
	{
		final GigaMap<Person> map = GigaMap.New();
		map.index().bitmap().add(NAME_IDENTITY);
		map.index().bitmap().setIdentityIndices(NAME_IDENTITY);
		map.add(new Person("alice"));

		map.index().bitmap().update(NAME_IDENTITY_2);

		// identity-based lookup (used by update) must resolve via the rebuilt index
		final Person alice = map.query(NAME_IDENTITY.is("alice")).toList().get(0);
		map.update(alice, p -> p.name = "alice2");

		assertEquals(1, map.query(NAME_IDENTITY.is("alice2")).count());
		assertEquals(0, map.query(NAME_IDENTITY.is("alice")).count());
	}

	@Test
	void update_changedKeyType()
	{
		final GigaMap<Person> map = GigaMap.New();
		map.index().bitmap().add(FULL_NAME);
		map.add(new Person("alice")); // length 5
		map.add(new Person("bob"));   // length 3

		map.index().bitmap().update(NAME_LENGTH);

		assertEquals(1, map.query(NAME_LENGTH.is(5)).count());
		assertEquals(1, map.query(NAME_LENGTH.is(3)).count());
	}

	@Test
	void update_readOnly_throws()
	{
		final GigaMap<Person> map = GigaMap.New();
		map.index().bitmap().add(FIRST_LETTER);
		map.add(new Person("alice"));
		map.markReadOnly();

		assertThrows(RuntimeException.class, () -> map.index().bitmap().update(FULL_NAME));

		map.unmarkReadOnly();
	}

	@Test
	void update_afterReload(@TempDir final Path dir)
	{
		final GigaMap<Person> map = GigaMap.New();
		map.index().bitmap().add(FIRST_LETTER);
		map.add(new Person("alice"));
		map.add(new Person("anna"));
		try(final EmbeddedStorageManager m = EmbeddedStorage.start(map, dir))
		{
			// stored on close
		}

		try(final EmbeddedStorageManager m = EmbeddedStorage.start(dir))
		{
			final GigaMap<Person> reloaded = reload(m);
			reloaded.index().bitmap().update(FULL_NAME);
			reloaded.store();
		}

		try(final EmbeddedStorageManager m = EmbeddedStorage.start(dir))
		{
			final GigaMap<Person> reloaded = reload(m);
			assertEquals(1, reloaded.query(FULL_NAME.is("alice")).count());
			assertEquals(1, reloaded.query(FULL_NAME.is("anna")).count());
			assertEquals(0, reloaded.query(FIRST_LETTER.is("a")).count());
		}
	}

	@Test
	void ensure_doesNotUpdateLogic()
	{
		final GigaMap<Person> map = GigaMap.New();
		map.index().bitmap().add(FIRST_LETTER);
		map.add(new Person("alice"));
		map.add(new Person("anna"));
		map.add(new Person("bob"));

		// ensure is get-or-create: it returns the existing index unchanged and ignores FULL_NAME's logic
		assertNotNull(map.index().bitmap().ensure(FULL_NAME));

		assertEquals(2, map.query(FIRST_LETTER.is("a")).count()); // still first-letter logic
		assertEquals(0, map.query(FULL_NAME.is("alice")).count()); // logic was NOT switched
	}
}
