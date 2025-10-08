package org.eclipse.store.gigamap.indexer.comparable;

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

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.IndexerComparing;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class CustomComparableTest
{
	@TempDir
	Path tempDir;

	@Test
	void customComareGigaMapTest()
	{

		GigaMap<PersonCustomComparable> map = GigaMap.New();
		PersonIndexerCompare indexer = new PersonIndexerCompare();
		map.index().bitmap().add(indexer);

		PersonCustomComparable p1 = new PersonCustomComparable("Aaa", 30);
		PersonCustomComparable p2 = new PersonCustomComparable("Aab", 35);
		PersonCustomComparable p3 = new PersonCustomComparable("Aac", 40);

		map.addAll(p1, p2, p3);

		List<PersonCustomComparable> list = map.query(indexer.between(new PersonCustomComparable("", 31), new PersonCustomComparable("", 39))).toList();
		assertEquals(1, list.size());

		list = map.query(indexer.greaterThan(new PersonCustomComparable("", 30))).toList();
		assertIterableEquals(List.of(p2, p3), list);

		list = map.query(indexer.lessThan(new PersonCustomComparable("", 40))).toList();
		assertIterableEquals(List.of(p1, p2), list);

		list = map.query(indexer.is(new PersonCustomComparable("", 35))).toList();
		assertIterableEquals(List.of(p2), list);

		try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(map, tempDir)) {
			// nothing to do
		}

		try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(tempDir)) {
			GigaMap<PersonCustomComparable> loadedMap = (GigaMap<PersonCustomComparable>) storageManager.root();

			IndexerComparing loadedIndexer = loadedMap.index().bitmap().getIndexer(IndexerComparing.class, "org.eclipse.store.gigamap.indexer.comparable.CustomComparableTest.PersonIndexerCompare");
			Assertions.assertNotNull(loadedIndexer);

			list = loadedMap.query(loadedIndexer.between(new PersonCustomComparable("", 31), new PersonCustomComparable("", 39))).toList();
			assertEquals(1, list.size());

			list = loadedMap.query(loadedIndexer.greaterThan(new PersonCustomComparable("", 30))).toList();
			assertIterableEquals(List.of(p2, p3), list);

			list = loadedMap.query(loadedIndexer.lessThan(new PersonCustomComparable("", 40))).toList();
			assertIterableEquals(List.of(p1, p2), list);

			list = loadedMap.query(loadedIndexer.is(new PersonCustomComparable("", 35))).toList();
			assertIterableEquals(List.of(p2), list);
		}

	}

	@Test
	void customComparableTest()
	{
		PersonCustomComparable p1 = new PersonCustomComparable("Aaa", 30);
		PersonCustomComparable p2 = new PersonCustomComparable("Aab", 35);

		PersonCustomComparable p3 = new PersonCustomComparable("AaC", 40);

		assert p1.compareTo(p2) < 0;
		assert p2.compareTo(p1) > 0;
		assert p2.compareTo(p3) < 0;
		assert p3.compareTo(p2) > 0;
		assert p1.compareTo(p3) < 0;
		assert p3.compareTo(p1) > 0;
		assert p1.compareTo(p1) == 0;
		assert p2.compareTo(p2) == 0;
		assert p3.compareTo(p3) == 0;
	}


	private class PersonIndexerCompare extends IndexerComparing.Abstract<PersonCustomComparable, PersonCustomComparable> {

		@Override
		public Class<PersonCustomComparable> keyType()
		{
			return PersonCustomComparable.class;
		}

		@Override
		public PersonCustomComparable index(PersonCustomComparable entity)
		{
			return entity;
		}
	}

	private class PersonCustomComparable implements Comparable<PersonCustomComparable>
	{

		private String name;
		private Integer age;

		public PersonCustomComparable(String name, Integer age)
		{
			this.name = name;
			this.age = age;
		}

		@Override
		public int compareTo(PersonCustomComparable o)
		{
			return age.compareTo(o.age);
		}

		@Override
		public boolean equals(Object o)
		{
			if (o == null || getClass() != o.getClass()) return false;
			PersonCustomComparable that = (PersonCustomComparable) o;
			return Objects.equals(age, that.age);
		}

		@Override
		public int hashCode()
		{
			return Objects.hashCode(age);
		}
	}
}
