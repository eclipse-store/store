package org.eclipse.store.gigamap.codegen.test;

/*-
 * #%L
 * EclipseStore GigaMap Codegen
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

import org.eclipse.store.gigamap.types.BinaryIndexer;
import org.eclipse.store.gigamap.types.ByteIndexerInteger;
import org.eclipse.store.gigamap.types.GeneratedIndices;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.Indexer;
import org.eclipse.store.gigamap.types.IndexerGenerator;
import org.eclipse.store.gigamap.types.IndexerInteger;
import org.eclipse.store.gigamap.types.IndexerMultiValue;
import org.eclipse.store.gigamap.types.IndexerString;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies that the compile-time generated {@code <Entity>_} metamodels (produced by the
 * {@code IndexMetamodelProcessor} during this module's test compilation) yield working indexer
 * constants and a reload-safe {@code registerIndices}, and that their indexer flavors match the
 * runtime {@code IndexerGenerator}.
 */
public class GeneratedMetamodelTest
{
	@TempDir
	Path tempDir;

	private static GigaMap<Person> people()
	{
		final GigaMap<Person> map = GigaMap.New();
		Person_.registerIndices(map);
		map.add(new Person("John", 30, 111L, 15, Color.RED , List.of("a", "b"), "john@x", "Johnny", "Berlin"));
		map.add(new Person("Jane", 25, 222L,  5, Color.BLUE, List.of("b", "c"), "jane@x", "Janie" , "Hamburg"));
		map.add(new Person("Bob" , 40, 333L, 50, Color.RED , List.of("c")     , "bob@x" , "Bobby" , "Berlin"));
		return map;
	}

	@Test
	void queryViaGeneratedConstants()
	{
		final GigaMap<Person> map = people();

		assertEquals(1, map.query(Person_.firstName.startsWith("Jo")).toList().size());
		assertEquals(1, map.query(Person_.age.is(30)).toList().size());
		assertEquals(1, map.query(Person_.ssn.is(222L)).toList().size());
		assertEquals(2, map.query(Person_.score.between(0, 20)).toList().size());
		assertEquals(2, map.query(Person_.color.is(Color.RED)).toList().size());
		assertEquals(2, map.query(Person_.tags.is("b")).toList().size());
		assertEquals(1, map.query(Person_.email.is("bob@x")).toList().size());
		assertEquals(1, map.query(Person_.nickname.is("Janie")).toList().size());
		assertEquals(2, map.query(Person_.city.is("Berlin")).toList().size());
	}

	@Test
	void recordMetamodel()
	{
		final GigaMap<Article> map = GigaMap.New();
		Article_.registerIndices(map);
		map.add(new Article("Hello", 100));
		map.add(new Article("World", 200));

		assertEquals(1, map.query(Article_.title.is("Hello")).toList().size());
		assertEquals(1, map.query(Article_.views.is(200)).toList().size());
	}

	@Test
	void spatialMetamodel()
	{
		final GigaMap<City> map = GigaMap.New();
		City_.registerIndices(map);
		map.add(new City("Berlin", 52.520, 13.405));
		map.add(new City("Munich", 48.137, 11.575));

		assertEquals(1, map.query(City_.spatial.near(52.5, 13.4, 50)).toList().size());
		assertEquals(1, map.query(City_.name.is("Munich")).toList().size());
	}

	@Test
	void registerIndicesIsIdempotentAndReloadSafe()
	{
		final GigaMap<Person> map = GigaMap.New();
		Person_.registerIndices(map);
		Person_.registerIndices(map); // second call must be a no-op, not fail on the unique constraint
		map.add(new Person("John", 30, 111L, 15, Color.RED, List.of("a"), "john@x", "Johnny", "Berlin"));

		try(final EmbeddedStorageManager sm = EmbeddedStorage.start(map, this.tempDir))
		{
			// store
		}

		try(final EmbeddedStorageManager sm = EmbeddedStorage.start(this.tempDir))
		{
			final GigaMap<Person> reloaded = sm.root();
			Person_.registerIndices(reloaded); // idempotent on the reloaded, already-indexed map

			assertEquals(1, reloaded.query(Person_.firstName.is("John")).toList().size());
			assertEquals(1, reloaded.query(Person_.ssn.is(111L)).toList().size());
			assertEquals(1, reloaded.query(Person_.score.between(10, 20)).toList().size());
		}
	}

	@Test
	void flavorsMatchRuntimeGenerator()
	{
		final GigaMap<Person> runtimeMap = GigaMap.New();
		final GeneratedIndices<Person> gi =
			IndexerGenerator.AnnotationBased(Person.class).generateIndices(runtimeMap);

		assertEquals(flavor(gi.get("firstName")), flavor(Person_.firstName));
		assertEquals(flavor(gi.get("age"))      , flavor(Person_.age));
		assertEquals(flavor(gi.get("ssn"))      , flavor(Person_.ssn));
		assertEquals(flavor(gi.get("score"))    , flavor(Person_.score));
		assertEquals(flavor(gi.get("color"))    , flavor(Person_.color));
		assertEquals(flavor(gi.get("tags"))     , flavor(Person_.tags));
		assertEquals(flavor(gi.get("email"))    , flavor(Person_.email));
		assertEquals(flavor(gi.get("nickname")) , flavor(Person_.nickname));
		assertEquals(flavor(gi.get("city"))     , flavor(Person_.city));
	}

	/** Classifies an indexer by its most-specific GigaMap indexer interface (for parity comparison). */
	private static String flavor(final Indexer<?, ?> indexer)
	{
		if(indexer instanceof ByteIndexerInteger)
		{
			return "ByteIndexerInteger";
		}
		if(indexer instanceof BinaryIndexer)
		{
			return "BinaryIndexer";
		}
		if(indexer instanceof IndexerMultiValue)
		{
			return "IndexerMultiValue:" + indexer.keyType().getName();
		}
		if(indexer instanceof IndexerString)
		{
			return "IndexerString";
		}
		if(indexer instanceof IndexerInteger)
		{
			return "IndexerInteger";
		}
		return "Indexer:" + indexer.keyType().getName();
	}
}
