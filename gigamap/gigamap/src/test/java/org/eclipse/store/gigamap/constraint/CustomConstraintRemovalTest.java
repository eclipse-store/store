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
import org.eclipse.store.gigamap.types.CustomConstraint;
import org.eclipse.store.gigamap.types.GigaMap;
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
 * Covers {@link org.eclipse.store.gigamap.types.CustomConstraints#removeConstraint(String)} and, via the
 * reload tests, the durability of custom-constraint structural changes (the {@code elements} table must be
 * stored eagerly so post-first-store mutations persist).
 */
public class CustomConstraintRemovalTest
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

	// CustomConstraint.AbstractBase#name() is final and returns getClass().getSimpleName(),
	// so these constraints are registered under "NoBad" / "NoUgly".
	static class NoBad extends CustomConstraint.AbstractSimple<Person>
	{
		@Override
		public boolean isViolated(final Person entity)
		{
			return entity.name.contains("BAD");
		}
	}

	static class NoUgly extends CustomConstraint.AbstractSimple<Person>
	{
		@Override
		public boolean isViolated(final Person entity)
		{
			return entity.name.contains("UGLY");
		}
	}

	@SuppressWarnings("unchecked")
	private static GigaMap<Person> reload(final EmbeddedStorageManager m)
	{
		return (GigaMap<Person>)m.root();
	}

	@Test
	void removeConstraint_previouslyRejectedNowAccepted()
	{
		final GigaMap<Person> map = GigaMap.New();
		map.constraints().custom().addConstraint(new NoBad());

		assertThrows(ConstraintViolationException.class, () -> map.add(new Person("xBADx")));

		assertTrue(map.constraints().custom().removeConstraint("NoBad"));

		map.add(new Person("xBADx")); // now accepted
		assertEquals(1, map.size());
	}

	@Test
	void removeConstraint_unknownName_returnsFalse()
	{
		final GigaMap<Person> map = GigaMap.New();
		map.constraints().custom().addConstraint(new NoBad());

		assertFalse(map.constraints().custom().removeConstraint("does-not-exist"));
	}

	@Test
	void removeConstraint_isIdempotent()
	{
		final GigaMap<Person> map = GigaMap.New();
		map.constraints().custom().addConstraint(new NoBad());

		assertTrue(map.constraints().custom().removeConstraint("NoBad"));
		assertFalse(map.constraints().custom().removeConstraint("NoBad"));
	}

	@Test
	void removeConstraint_oneOfMany_othersStillEnforced()
	{
		final GigaMap<Person> map = GigaMap.New();
		map.constraints().custom().addConstraints(new NoBad(), new NoUgly());

		map.constraints().custom().removeConstraint("NoBad");

		map.add(new Person("xBADx")); // no longer rejected
		assertThrows(ConstraintViolationException.class, () -> map.add(new Person("xUGLYx")));
	}

	/**
	 * Primary regression guard for the eager-store fix: a constraint added before a store and then
	 * removed after a reload must stay removed across a second reload.
	 */
	@Test
	void removeConstraint_afterReload(@TempDir final Path dir)
	{
		final GigaMap<Person> map = GigaMap.New();
		map.constraints().custom().addConstraint(new NoBad());
		map.add(new Person("ok"));
		try(final EmbeddedStorageManager m = EmbeddedStorage.start(map, dir))
		{
			// stored on close
		}

		try(final EmbeddedStorageManager m = EmbeddedStorage.start(dir))
		{
			final GigaMap<Person> reloaded = reload(m);
			assertTrue(reloaded.constraints().custom().removeConstraint("NoBad"));
			reloaded.store();
		}

		try(final EmbeddedStorageManager m = EmbeddedStorage.start(dir))
		{
			final GigaMap<Person> reloaded = reload(m);
			reloaded.add(new Person("xBADx")); // constraint must stay removed
			assertEquals(2, reloaded.size());
		}
	}

	/**
	 * Independent guard on the add path: a constraint added <em>after</em> the first store must be
	 * persisted (this relies on the {@code elements} table being stored eagerly).
	 */
	@Test
	void addConstraint_afterInitialStore_persists(@TempDir final Path dir)
	{
		final GigaMap<Person> map = GigaMap.New();
		try(final EmbeddedStorageManager m = EmbeddedStorage.start(map, dir))
		{
			// empty map stored on close
		}

		try(final EmbeddedStorageManager m = EmbeddedStorage.start(dir))
		{
			final GigaMap<Person> reloaded = reload(m);
			reloaded.constraints().custom().addConstraint(new NoBad());
			reloaded.store();
		}

		try(final EmbeddedStorageManager m = EmbeddedStorage.start(dir))
		{
			final GigaMap<Person> reloaded = reload(m);
			assertThrows(ConstraintViolationException.class, () -> reloaded.add(new Person("xBADx")));
		}
	}
}
