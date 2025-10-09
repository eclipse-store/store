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

import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import org.eclipse.store.gigamap.types.BitmapIndex;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.Indexer;
import org.eclipse.store.gigamap.types.IndexerComparing;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class StringComparableTest
{

	@TempDir
	Path tempDir;

	@Test
	void comparableIndexerTest()
	{
		PersonAgeIndexer indexer = new PersonAgeIndexer();

		GigaMap<PersonCompare> map = GigaMap.New();
		map.index().bitmap().add(indexer);

		PersonCompare e1 = new PersonCompare("AAa", 30);
		PersonCompare e2 = new PersonCompare("ABa", 35);
		PersonCompare e3 = new PersonCompare("ACa", 40);

		map.addAll(e1, e2, e3);

		List<PersonCompare> result = map.query(indexer.between(31, 39)).toList();
		assertIterableEquals(List.of(e2), result);

		result = map.query(indexer.greaterThan(30)).toList();
		assertIterableEquals(List.of(e2, e3), result);

		result = map.query(indexer.lessThan(40)).toList();
		assertIterableEquals(List.of(e1, e2), result);

		result = map.query(indexer.is(35)).toList();
		assertIterableEquals(List.of(e2), result);

		try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, tempDir)) {
			// nothing to do
		}

		try (EmbeddedStorageManager manager = EmbeddedStorage.start(tempDir)) {
			GigaMap<PersonCompare> root = (GigaMap<PersonCompare>) manager.root();

			IndexerComparing loadedIndexer = root.index().bitmap().getIndexer(IndexerComparing.class, "org.eclipse.store.gigamap.indexer.comparable.StringComparableTest.PersonAgeIndexer");
			assertNotNull(loadedIndexer);

			result = root.query(loadedIndexer.between(31, 39)).toList();
			assertIterableEquals(List.of(e2), result);

			result = root.query(loadedIndexer.greaterThan(30)).toList();
			assertIterableEquals(List.of(e2, e3), result);

			result = root.query(loadedIndexer.lessThan(40)).toList();
			assertIterableEquals(List.of(e1, e2), result);

			result = root.query(loadedIndexer.is(35)).toList();
			assertIterableEquals(List.of(e2), result);

		}



	}

	private static class PersonCompare
	{
		private final String name;
		private final Integer age;

		public PersonCompare(String name, Integer age)
		{
			this.name = name;
			this.age = age;
		}

		public String getName()
		{
			return name;
		}

		public Integer getAge()
		{
			return age;
		}

		@Override
		public String toString()
		{
			return "PersonCompare{" +
					"name='" + name + '\'' +
					", age=" + age +
					'}';
		}

		@Override
		public boolean equals(Object o)
		{
			if (o == null || getClass() != o.getClass()) return false;
			PersonCompare that = (PersonCompare) o;
			return Objects.equals(name, that.name) && Objects.equals(age, that.age);
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(name, age);
		}
	}

	static class PersonAgeIndexer extends IndexerComparing.Abstract<PersonCompare, Integer>
	{

		@Override
		public Class<Integer> keyType()
		{
			return Integer.class;
		}

		@Override
		public Integer index(PersonCompare entity)
		{
			return entity.getAge();
		}
	}
}
