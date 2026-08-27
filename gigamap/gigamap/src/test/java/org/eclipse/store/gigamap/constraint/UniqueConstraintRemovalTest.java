package org.eclipse.store.gigamap.constraint;

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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers {@link org.eclipse.store.gigamap.types.UniqueConstraints#removeUniqueConstraint(String)}:
 * demoting a unique index to a plain queryable index (uniqueness no longer enforced, index retained).
 */
public class UniqueConstraintRemovalTest
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

	static final IndexerString<Person> PLAIN = new IndexerString.Abstract<>()
	{
		@Override
		public String name()
		{
			return "plain";
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
	void removeUniqueConstraint_allowsDuplicatesAfterward()
	{
		final GigaMap<Person> map = GigaMap.New();
		map.constraints().unique().addUniqueConstraint(UNAME);
		map.add(new Person("alice"));

		assertThrows(ConstraintViolationException.class, () -> map.add(new Person("alice")));

		assertTrue(map.constraints().unique().removeUniqueConstraint("uname"));

		map.add(new Person("alice")); // now allowed
		assertEquals(2, map.size());
	}

	@Test
	void removeUniqueConstraint_indexRemainsQueryable()
	{
		final GigaMap<Person> map = GigaMap.New();
		map.constraints().unique().addUniqueConstraint(UNAME);
		map.add(new Person("alice"));

		map.constraints().unique().removeUniqueConstraint("uname");
		map.add(new Person("alice"));

		// the demoted index is still a regular queryable index
		assertEquals(2, map.query(UNAME.is("alice")).count());
	}

	@Test
	void removeUniqueConstraint_unknownName_returnsFalse()
	{
		final GigaMap<Person> map = GigaMap.New();
		map.constraints().unique().addUniqueConstraint(UNAME);

		assertFalse(map.constraints().unique().removeUniqueConstraint("does-not-exist"));
	}

	@Test
	void removeUniqueConstraint_nonUniqueIndex_returnsFalse()
	{
		final GigaMap<Person> map = GigaMap.New();
		map.index().bitmap().add(PLAIN); // plain index, not a unique constraint

		assertFalse(map.constraints().unique().removeUniqueConstraint("plain"));
	}

	@Test
	void removeUniqueConstraint_isIdempotent()
	{
		final GigaMap<Person> map = GigaMap.New();
		map.constraints().unique().addUniqueConstraint(UNAME);

		assertTrue(map.constraints().unique().removeUniqueConstraint("uname"));
		assertFalse(map.constraints().unique().removeUniqueConstraint("uname"));
	}

	@Test
	void removeUniqueConstraint_lastConstraint_emptiesRegistry()
	{
		final GigaMap<Person> map = GigaMap.New();
		map.constraints().unique().addUniqueConstraint(UNAME);

		map.constraints().unique().removeUniqueConstraint("uname");

		assertTrue(map.index().bitmap().uniqueConstraints() == null
			|| map.index().bitmap().uniqueConstraints().isEmpty());
	}

	@Test
	void removeIndex_thenRePromote_reEnforcesUniqueness()
	{
		final GigaMap<Person> map = GigaMap.New();
		map.constraints().unique().addUniqueConstraint(UNAME);
		map.add(new Person("alice"));

		// demote, add a duplicate (now allowed)
		map.constraints().unique().removeUniqueConstraint("uname");
		map.add(new Person("alice"));

		// re-promoting the still-registered index by the same name is rejected (name already taken);
		// the index must be removed first, then re-added as a unique constraint.
		assertThrows(RuntimeException.class, () -> map.constraints().unique().addUniqueConstraint(UNAME));

		map.index().bitmap().removeIndex("uname");
		// now there are two "alice" entities -> promotion must fail on the existing duplicate
		assertThrows(ConstraintViolationException.class, () -> map.constraints().unique().addUniqueConstraint(UNAME));
	}

	@Test
	void removeUniqueConstraint_afterReload(@TempDir final Path dir)
	{
		final GigaMap<Person> map = GigaMap.New();
		map.constraints().unique().addUniqueConstraint(UNAME);
		map.add(new Person("alice"));
		try(final EmbeddedStorageManager m = EmbeddedStorage.start(map, dir))
		{
			// stored on close
		}

		try(final EmbeddedStorageManager m = EmbeddedStorage.start(dir))
		{
			final GigaMap<Person> reloaded = reload(m);
			assertTrue(reloaded.constraints().unique().removeUniqueConstraint("uname"));
			reloaded.store();
		}

		try(final EmbeddedStorageManager m = EmbeddedStorage.start(dir))
		{
			final GigaMap<Person> reloaded = reload(m);
			// demotion persisted: duplicate now allowed, index still queryable
			reloaded.add(new Person("alice"));
			assertEquals(2, reloaded.size());
			assertEquals(2, reloaded.query(UNAME.is("alice")).count());
		}
	}
}
