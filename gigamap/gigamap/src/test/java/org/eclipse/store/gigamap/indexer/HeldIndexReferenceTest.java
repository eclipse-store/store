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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.store.gigamap.exceptions.BitmapIndexException;
import org.eclipse.store.gigamap.types.BitmapIndex;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.IndexerInteger;
import org.eclipse.store.gigamap.types.IndexerString;
import org.junit.jupiter.api.Test;

/**
 * A {@link BitmapIndex} obtained from {@code add} / {@code get} is a handle, not the registered index
 * itself: {@code update(Indexer)} and a rebuild replace the registered instance, {@code removeIndex}
 * drops it. The replacement keeps the same parent, so validating the parent back-reference cannot tell a
 * detached instance from the live one - and a detached one has had its off-heap released, so answering
 * from it would silently return nothing.
 * <p>
 * These tests pin that a held reference resolves to whatever is registered under its name at query time,
 * and that a name which is gone is reported instead of answering empty.
 */
public class HeldIndexReferenceTest
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
			super();
			this.name = name;
		}
	}

	static final IndexerString<Person> FULL_NAME = new IndexerString.Abstract<>()
	{
		@Override
		public String name()
		{
			return "key";
		}

		@Override
		protected String getString(final Person entity)
		{
			return entity.name;
		}
	};

	/** same name "key", new logic */
	static final IndexerString<Person> FIRST_LETTER = new IndexerString.Abstract<>()
	{
		@Override
		public String name()
		{
			return "key";
		}

		@Override
		protected String getString(final Person entity)
		{
			return entity.name.substring(0, 1);
		}
	};

	/** same name "key", but a different key type */
	static final IndexerInteger<Person> NAME_LENGTH = new IndexerInteger.Abstract<>()
	{
		@Override
		public String name()
		{
			return "key";
		}

		@Override
		protected Integer getInteger(final Person entity)
		{
			return entity.name.length();
		}
	};

	private static GigaMap<Person> populatedMap()
	{
		final GigaMap<Person> map = GigaMap.New();
		map.index().bitmap().add(FULL_NAME);
		map.add(new Person("alice"));
		map.add(new Person("bob"));

		return map;
	}

	@Test
	void heldReferenceAnswersFromTheRebuiltIndexAfterUpdate()
	{
		final GigaMap<Person>          map  = populatedMap();
		final BitmapIndex<Person, String> held = map.index().bitmap().get(String.class, "key");

		map.index().bitmap().update(FIRST_LETTER);

		// the held handle must answer for the NEW logic, not from its own released data
		assertEquals(1, map.query(held.is("a")).count(), "held reference did not follow the redefinition");
		assertEquals(1, map.query(held.is("b")).count(), "held reference did not follow the redefinition");
		assertEquals(0, map.query(held.is("alice")).count(), "held reference still answers from stale data");
	}

	@Test
	void heldReferenceResolvesToTheCurrentlyRegisteredInstance()
	{
		final GigaMap<Person>             map  = populatedMap();
		final BitmapIndex<Person, String> held = map.index().bitmap().get(String.class, "key");

		map.index().bitmap().update(FIRST_LETTER);

		assertSame(
			map.index().bitmap().get(String.class, "key"),
			map.index().bitmap().get(String.class, "key"),
			"precondition: get() returns the registered instance"
		);
		// the handle is stale, but querying through it reaches the registered index
		assertEquals(1, map.query(held.is("a")).count());
	}

	@Test
	void heldReferenceToARemovedIndexIsReportedInsteadOfAnsweringEmpty()
	{
		final GigaMap<Person>             map  = populatedMap();
		final BitmapIndex<Person, String> held = map.index().bitmap().get(String.class, "key");

		map.index().bitmap().removeIndex("key");

		final BitmapIndexException e = assertThrows(
			BitmapIndexException.class,
			() -> map.query(held.is("alice")).count(),
			"a removed index must be reported, not answered as empty"
		);
		assertTrue(
			e.getMessage().contains("no longer registered"),
			"the message must say the name is gone, not merely that something failed: " + e.getMessage()
		);
	}

	@Test
	void heldReferenceOfAChangedKeyTypeIsReportedInsteadOfAnsweringEmpty()
	{
		final GigaMap<Person>             map  = populatedMap();
		final BitmapIndex<Person, String> held = map.index().bitmap().get(String.class, "key");

		// same name, different key type - the String index is gone
		map.index().bitmap().update(NAME_LENGTH);

		assertThrows(
			BitmapIndexException.class,
			() -> map.query(held.is("alice")).count(),
			"a replaced key type must be reported, not answered as empty"
		);
		// the map itself is fine under the new logic
		assertEquals(1, map.query(NAME_LENGTH.is(5)).count());
	}

	@Test
	void aForeignParentIsStillRejected()
	{
		final GigaMap<Person>             map   = populatedMap();
		final BitmapIndex<Person, String> held  = map.index().bitmap().get(String.class, "key");
		final GigaMap<Person>             other = populatedMap();

		assertThrows(
			BitmapIndexException.class,
			() -> other.query(held.is("alice")).count(),
			"an index of another map must not resolve"
		);
	}
}
