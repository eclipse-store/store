package org.eclipse.store.gigamap.indexer.annotation;

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

import org.eclipse.store.gigamap.annotations.Index;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.IndexerGenerator;
import org.eclipse.store.gigamap.types.IndexerInteger;
import org.eclipse.store.gigamap.types.IndexerString;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class GetterIndexTest
{
	static class Person
	{
		private final String name;
		private final int    age;

		Person(final String name, final int age)
		{
			this.name = name;
			this.age  = age;
		}

		@Index
		public String getName()
		{
			return this.name;
		}

		@Index
		public int getAge()
		{
			return this.age;
		}
	}

	record Point(@Index int x, @Index int y)
	{
	}

	@Test
	void getterAnnotationsAreIndexedByPropertyName()
	{
		final GigaMap<Person> map = GigaMap.New();
		IndexerGenerator.AnnotationBased(Person.class).generateIndices(map.index().bitmap());

		map.add(new Person("Alice", 30));
		map.add(new Person("Bob", 40));

		final IndexerString<Person> name = map.index().bitmap().getIndexerString("name");
		final List<Person> alice = map.query(name.is("Alice")).toList();
		assertEquals(1, alice.size());
		assertEquals(30, alice.get(0).getAge());

		final IndexerInteger<Person> age = map.index().bitmap().getIndexerInteger("age");
		assertEquals(1, map.query(age.is(40)).toList().size());
	}

	@Test
	void recordComponentsAreIndexedOnce()
	{
		final GigaMap<Point> map = GigaMap.New();
		IndexerGenerator.AnnotationBased(Point.class).generateIndices(map.index().bitmap());

		map.add(new Point(1, 2));
		map.add(new Point(3, 4));

		final IndexerInteger<Point> x = map.index().bitmap().getIndexerInteger("x");
		final List<Point> result = map.query(x.is(3)).toList();
		assertEquals(1, result.size());
		assertEquals(4, result.get(0).y());
	}
}
