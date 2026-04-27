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

import org.eclipse.store.gigamap.data.Entity;
import org.eclipse.store.gigamap.types.GigaMap;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ReadOnlyTest
{
	@Test
	void add_throwsWhenReadOnly()
	{
		final GigaMap<Entity> map = GigaMap.New();
		map.index().bitmap().ensure(Entity.wordIndex);
		map.add(Entity.Random().setWord("a"));

		map.markReadOnly();
		assertTrue(map.isReadOnly());

		assertThrows(
			IllegalStateException.class,
			() -> map.add(Entity.Random().setWord("b"))
		);
		assertEquals(1, map.size());
	}

	@Test
	void set_throwsWhenReadOnly()
	{
		final GigaMap<Entity> map = GigaMap.New();
		map.index().bitmap().ensure(Entity.wordIndex);
		final long id = map.add(Entity.Random().setWord("a"));

		map.markReadOnly();

		assertThrows(
			IllegalStateException.class,
			() -> map.set(id, Entity.Random().setWord("b"))
		);
		assertEquals("a", map.get(id).getWord());
	}

	@Test
	void remove_throwsWhenReadOnly()
	{
		final GigaMap<Entity> map = GigaMap.New();
		map.index().bitmap().ensure(Entity.wordIndex);
		final Entity entity = Entity.Random().setWord("a");
		map.add(entity);

		map.markReadOnly();

		assertThrows(
			IllegalStateException.class,
			() -> map.remove(entity)
		);
		assertEquals(1, map.size());
	}

	@Test
	void unmarkReadOnly_allowsWriteAgain()
	{
		final GigaMap<Entity> map = GigaMap.New();
		map.index().bitmap().ensure(Entity.wordIndex);
		map.add(Entity.Random().setWord("a"));

		map.markReadOnly();
		assertTrue(map.isReadOnly());

		map.unmarkReadOnly();
		assertFalse(map.isReadOnly());

		map.add(Entity.Random().setWord("b"));
		assertEquals(2, map.size());
	}

	@Test
	void query_worksWhileReadOnly()
	{
		final GigaMap<Entity> map = GigaMap.New();
		map.index().bitmap().ensure(Entity.wordIndex);
		map.add(Entity.Random().setWord("alpha"));
		map.add(Entity.Random().setWord("beta"));
		map.add(Entity.Random().setWord("alpha"));

		map.markReadOnly();

		final List<Entity> results = map.query(Entity.wordIndex.is("alpha")).toList();
		assertEquals(2, results.size());
		assertEquals(3, map.size());
		assertTrue(map.isReadOnly(), "map must still be read-only after query");
	}

	@Test
	void get_worksWhileReadOnly()
	{
		final GigaMap<Entity> map = GigaMap.New();
		map.index().bitmap().ensure(Entity.wordIndex);
		final long id = map.add(Entity.Random().setWord("hello"));

		map.markReadOnly();

		final Entity entity = map.get(id);
		assertNotNull(entity);
		assertEquals("hello", entity.getWord());
	}

	@Test
	void count_worksWhileReadOnly()
	{
		final GigaMap<Entity> map = GigaMap.New();
		map.index().bitmap().ensure(Entity.wordIndex);
		map.add(Entity.Random().setWord("x"));
		map.add(Entity.Random().setWord("x"));
		map.add(Entity.Random().setWord("y"));

		map.markReadOnly();

		assertEquals(2, map.query(Entity.wordIndex.is("x")).count());
		assertEquals(1, map.query(Entity.wordIndex.is("y")).count());
		assertEquals(0, map.query(Entity.wordIndex.is("z")).count());
	}

	@Test
	void nestedMarkReadOnly_requiresMatchingUnmark()
	{
		final GigaMap<Entity> map = GigaMap.New();
		map.index().bitmap().ensure(Entity.wordIndex);

		map.markReadOnly();
		map.markReadOnly();
		assertTrue(map.isReadOnly());

		map.unmarkReadOnly();
		assertTrue(map.isReadOnly(), "still read-only after first unmark");

		map.unmarkReadOnly();
		assertFalse(map.isReadOnly());

		map.add(Entity.Random().setWord("x"));
		assertEquals(1, map.size());
	}
}
