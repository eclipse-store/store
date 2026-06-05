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
import org.eclipse.store.gigamap.types.CustomConstraint;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.IndexerString;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Covers the idempotent {@code ensure*} APIs for unique and custom constraints
 * ({@link org.eclipse.store.gigamap.types.UniqueConstraints#ensureUniqueConstraint},
 * {@link org.eclipse.store.gigamap.types.CustomConstraints#ensureConstraint}) — the get-or-create
 * behaviour that lets startup schema declaration run unchanged on every boot.
 */
public class ConstraintEnsureTest
{
	static class Person
	{
		String name;

		Person()
		{
			// for deserialization
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

	// a plain (non-unique) hashing index and a unique binary index that share the name "x"
	static final IndexerString<Person> PLAIN_X = new IndexerString.Abstract<>()
	{
		@Override
		public String name()
		{
			return "x";
		}

		@Override
		protected String getString(final Person e)
		{
			return e.name;
		}
	};

	static final BinaryIndexerString<Person> UNIQUE_X = new BinaryIndexerString.Abstract<>()
	{
		@Override
		public String name()
		{
			return "x";
		}

		@Override
		protected String getString(final Person e)
		{
			return e.name;
		}
	};

	// CustomConstraint.AbstractBase#name() is final -> registered under "NoBad"
	static class NoBad extends CustomConstraint.AbstractSimple<Person>
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
	void ensureUniqueConstraint_createsThenNoOps()
	{
		final GigaMap<Person> map = GigaMap.New();
		map.constraints().unique().ensureUniqueConstraint(UNAME);
		map.add(new Person("alice"));

		assertThrows(ConstraintViolationException.class, () -> map.add(new Person("alice")));

		// second ensure is a no-op: must not throw, constraint stays in force
		assertDoesNotThrow(() -> map.constraints().unique().ensureUniqueConstraint(UNAME));
		assertThrows(ConstraintViolationException.class, () -> map.add(new Person("alice")));
		assertEquals(1, map.size());
	}

	@Test
	void ensureConstraint_createsThenNoOps()
	{
		final GigaMap<Person> map = GigaMap.New();
		map.constraints().custom().ensureConstraint(new NoBad());

		assertThrows(ConstraintViolationException.class, () -> map.add(new Person("xBADx")));

		assertDoesNotThrow(() -> map.constraints().custom().ensureConstraint(new NoBad()));
		assertThrows(ConstraintViolationException.class, () -> map.add(new Person("xBADx")));
	}

	/**
	 * The headline restart-safety guarantee: declaring the same schema via {@code ensure*} after a
	 * reload (where the constraints already exist) must not throw, and the constraints stay enforced.
	 */
	@Test
	void ensure_afterReload_doesNotThrow(@TempDir final Path dir)
	{
		try(final EmbeddedStorageManager m = EmbeddedStorage.start(dir))
		{
			final GigaMap<Person> map = GigaMap.New();
			m.setRoot(map);
			map.constraints().unique().addUniqueConstraint(UNAME);
			map.constraints().custom().addConstraint(new NoBad());
			map.add(new Person("alice"));
			m.storeRoot();
		}

		try(final EmbeddedStorageManager m = EmbeddedStorage.start(dir))
		{
			final GigaMap<Person> map = reload(m);

			// unconditional startup declaration against an existing storage -> idempotent, no throw
			assertDoesNotThrow(() ->
			{
				map.constraints().unique().ensureUniqueConstraint(UNAME);
				map.constraints().custom().ensureConstraint(new NoBad());
			});

			// both constraints still enforced
			assertThrows(ConstraintViolationException.class, () -> map.add(new Person("alice")));
			assertThrows(ConstraintViolationException.class, () -> map.add(new Person("xBADx")));
		}
	}

	@Test
	void ensure_newConstraintOnReloadedPopulatedMap(@TempDir final Path dir)
	{
		try(final EmbeddedStorageManager m = EmbeddedStorage.start(dir))
		{
			final GigaMap<Person> map = GigaMap.New();
			m.setRoot(map);
			map.add(new Person("alice"));
			map.add(new Person("bob"));
			m.storeRoot();
		}

		try(final EmbeddedStorageManager m = EmbeddedStorage.start(dir))
		{
			final GigaMap<Person> map = reload(m);

			// constraint did not exist before: ensure creates it, validating + indexing existing data
			map.constraints().unique().ensureUniqueConstraint(UNAME);

			assertThrows(ConstraintViolationException.class, () -> map.add(new Person("alice")));
			assertEquals(2, map.size());
		}
	}

	@Test
	void ensureUniqueConstraint_existingPlainIndexSameName_throws()
	{
		final GigaMap<Person> map = GigaMap.New();
		map.index().bitmap().add(PLAIN_X); // plain (non-unique) index named "x"

		// ensuring a unique constraint under a name already taken by a non-unique index is a real conflict
		assertThrows(RuntimeException.class, () -> map.constraints().unique().ensureUniqueConstraint(UNIQUE_X));
	}

	@Test
	void ensure_isIdempotentAcrossManyCalls()
	{
		final GigaMap<Person> map = GigaMap.New();
		for(int i = 0; i < 3; i++)
		{
			map.constraints().unique().ensureUniqueConstraint(UNAME);
			map.constraints().custom().ensureConstraint(new NoBad());
		}

		// exactly one unique constraint registered
		final long[] uniqueCount = {0};
		map.index().bitmap().accessUniqueConstraints(e -> uniqueCount[0] = e.size());
		assertEquals(1L, uniqueCount[0]);

		// custom constraint enforced exactly once (no duplicate-name failure occurred above)
		assertThrows(ConstraintViolationException.class, () -> map.add(new Person("xBADx")));
	}
}
