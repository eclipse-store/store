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
import org.eclipse.store.gigamap.types.IndexerInteger;
import org.eclipse.store.gigamap.types.IndexerString;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * End-to-end coherence check: a single map carrying several index families, a unique constraint, an
 * identity index and a custom constraint is mutated structurally (remove / redefine / demote /
 * removeConstraint) with a store+reload between every step, asserting the surviving structure and
 * entity data stay correct.
 */
public class StructuralLifecycleIntegrationTest
{
	static class Person
	{
		String name;
		String email;
		int    age;

		Person()
		{
			// required for deserialization
		}

		Person(final String name, final String email, final int age)
		{
			this.name  = name;
			this.email = email;
			this.age   = age;
		}
	}

	static final IndexerString<Person> NAME_FIRST_LETTER = new IndexerString.Abstract<>()
	{
		@Override
		public String name()
		{
			return "name";
		}

		@Override
		protected String getString(final Person e)
		{
			return e.name.substring(0, 1);
		}
	};

	static final IndexerString<Person> NAME_FULL = new IndexerString.Abstract<>()
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

	static final IndexerInteger<Person> AGE = new IndexerInteger.Abstract<>()
	{
		@Override
		public String name()
		{
			return "age";
		}

		@Override
		protected Integer getInteger(final Person e)
		{
			return e.age;
		}
	};

	static final BinaryIndexerString<Person> EMAIL = new BinaryIndexerString.Abstract<>()
	{
		@Override
		public String name()
		{
			return "email";
		}

		@Override
		protected String getString(final Person e)
		{
			return e.email;
		}
	};

	static class NoBad extends org.eclipse.store.gigamap.types.CustomConstraint.AbstractSimple<Person>
	{
		@Override
		public boolean isViolated(final Person entity)
		{
			return entity.name.contains("BAD");
		}
	}

	@SuppressWarnings("unchecked")
	private static GigaMap<Person> reload(final EmbeddedStorageManager m)
	{
		return (GigaMap<Person>)m.root();
	}

	@Test
	void fullStructuralLifecycleAcrossReloads(@TempDir final Path dir)
	{
		// 0) initial structure: name (first-letter) + age indexes, unique+identity email, NoBad constraint
		final GigaMap<Person> map = GigaMap.New();
		map.index().bitmap().add(NAME_FIRST_LETTER);
		map.index().bitmap().add(AGE);
		map.index().bitmap().addUniqueConstraint(EMAIL);
		map.index().bitmap().setIdentityIndices(EMAIL);
		map.constraints().custom().addConstraint(new NoBad());
		map.add(new Person("alice", "alice@x.com", 30));
		map.add(new Person("anna", "anna@x.com", 30));
		try(final EmbeddedStorageManager m = EmbeddedStorage.start(map, dir))
		{
			// stored on close
		}

		// constraints are active right away
		assertThrows(ConstraintViolationException.class, () -> map.add(new Person("bob", "alice@x.com", 1))); // dup email
		assertThrows(ConstraintViolationException.class, () -> map.add(new Person("xBADx", "bad@x.com", 1))); // NoBad

		// 1) remove the age index
		try(final EmbeddedStorageManager m = EmbeddedStorage.start(dir))
		{
			final GigaMap<Person> r = reload(m);
			r.index().bitmap().removeIndex("age");
			r.store();
		}
		try(final EmbeddedStorageManager m = EmbeddedStorage.start(dir))
		{
			final GigaMap<Person> r = reload(m);
			assertNull(r.index().bitmap().get(Integer.class, "age"));
			assertNotNull(r.index().bitmap().get("name"));
			assertEquals(2, r.size());
			assertEquals(2, r.query(NAME_FIRST_LETTER.is("a")).count());
		}

		// 2) redefine the name index from first-letter to full-name
		try(final EmbeddedStorageManager m = EmbeddedStorage.start(dir))
		{
			final GigaMap<Person> r = reload(m);
			r.index().bitmap().update(NAME_FULL);
			r.store();
		}
		try(final EmbeddedStorageManager m = EmbeddedStorage.start(dir))
		{
			final GigaMap<Person> r = reload(m);
			assertEquals(1, r.query(NAME_FULL.is("alice")).count());
			assertEquals(0, r.query(NAME_FIRST_LETTER.is("a")).count());
			// email unique constraint still enforced after the unrelated redefine
			assertThrows(ConstraintViolationException.class, () -> r.add(new Person("zoe", "alice@x.com", 9)));
		}

		// 3) demote the email unique constraint (still an identity index, still queryable)
		try(final EmbeddedStorageManager m = EmbeddedStorage.start(dir))
		{
			final GigaMap<Person> r = reload(m);
			r.constraints().unique().removeUniqueConstraint("email");
			r.store();
		}
		try(final EmbeddedStorageManager m = EmbeddedStorage.start(dir))
		{
			final GigaMap<Person> r = reload(m);
			r.add(new Person("zoe", "alice@x.com", 9)); // duplicate email now allowed
			assertEquals(3, r.size());
			assertEquals(2, r.query(EMAIL.is("alice@x.com")).count());
			r.store();
		}

		// 4) remove the custom constraint
		try(final EmbeddedStorageManager m = EmbeddedStorage.start(dir))
		{
			final GigaMap<Person> r = reload(m);
			r.constraints().custom().removeConstraint("NoBad");
			r.store();
		}
		try(final EmbeddedStorageManager m = EmbeddedStorage.start(dir))
		{
			final GigaMap<Person> r = reload(m);
			r.add(new Person("xBADx", "bad@x.com", 1)); // NoBad no longer enforced
			assertEquals(4, r.size());
		}
	}
}
