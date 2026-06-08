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
import org.eclipse.store.gigamap.annotations.IndexKind;
import org.eclipse.store.gigamap.annotations.SpatialIndex;
import org.eclipse.store.gigamap.types.BinaryIndexer;
import org.eclipse.store.gigamap.types.BinaryIndexerString;
import org.eclipse.store.gigamap.types.BitmapIndices;
import org.eclipse.store.gigamap.types.ByteIndexerNumber;
import org.eclipse.store.gigamap.types.GeneratedIndices;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.IndexerComparing;
import org.eclipse.store.gigamap.types.IndexerGenerator;
import org.eclipse.store.gigamap.types.IndexerInstant;
import org.eclipse.store.gigamap.types.IndexerString;
import org.eclipse.store.gigamap.types.IndexerZonedDateTime;
import org.eclipse.store.gigamap.types.SpatialIndexer;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class IndexerRetrievalTest
{
	@TempDir
	Path tempDir;

	static class Bean
	{
		@Index                              String        name;
		@Index                              Instant       ts;
		@Index                              ZonedDateTime zdt;
		@Index(binary = true)               long          id;
		@Index(binary = true)               String        code;
		@Index(kind = IndexKind.BIT_SLICED) int           score;
		@Index                              Date          date;

		Bean(final String name, final Instant ts, final long id, final String code, final int score, final Date date)
		{
			this.name  = name;
			this.ts    = ts;
			this.zdt   = ts.atZone(ZoneOffset.UTC);
			this.id    = id;
			this.code  = code;
			this.score = score;
			this.date  = date;
		}
	}

	@SpatialIndex(latitude = "lat", longitude = "lon")
	static class City
	{
		String name;
		double lat;
		double lon;

		City(final String name, final double lat, final double lon)
		{
			this.name = name;
			this.lat  = lat;
			this.lon  = lon;
		}
	}

	private static GigaMap<Bean> newBeanMap()
	{
		final GigaMap<Bean> map = GigaMap.New();
		IndexerGenerator.AnnotationBased(Bean.class).generateIndices(map);
		map.add(new Bean("alice", Instant.parse("2024-01-01T00:00:00Z"), 100L, "ax-1", 10, new Date(1_000L)));
		map.add(new Bean("bob",   Instant.parse("2025-06-01T00:00:00Z"), 200L, "bx-9", 90, new Date(9_000L)));
		return map;
	}

	@Test
	void newTypedGettersResolveEachKind()
	{
		final GigaMap<Bean>       map = newBeanMap();
		final BitmapIndices<Bean> b   = map.index().bitmap();

		assertEquals(1, map.query(b.getIndexerInstant("ts").is(Instant.parse("2024-01-01T00:00:00Z"))).toList().size());
		assertEquals(1, map.query(b.getIndexerZonedDateTime("zdt").is(Instant.parse("2025-06-01T00:00:00Z").atZone(ZoneOffset.UTC))).toList().size());

		final BinaryIndexer<Bean> id = b.getBinaryIndexer("id");
		assertEquals(1, map.query(id.is(200L)).toList().size());

		// a binary String index is a BinaryIndexerString (BinaryCompositeIndexer), so neither the
		// String getter nor the binary getter can resolve it - getBinaryIndexerString must be used.
		final BinaryIndexerString<Bean> code = b.getBinaryIndexerString("code");
		assertEquals(1, map.query(code.is("ax-1")).toList().size());
		assertThrows(ClassCastException.class, () -> b.getIndexerString("code"));
		assertThrows(ClassCastException.class, () -> b.getBinaryIndexer("code"));

		final ByteIndexerNumber<Bean, Integer> score = b.getByteIndexerNumber(Integer.class, "score");
		assertEquals(1, map.query(score.greaterThan(50)).toList().size());

		final IndexerComparing<Bean, Date> date = b.getIndexerComparing(Date.class, "date");
		assertEquals(1, map.query(date.greaterThan(new Date(5_000L))).toList().size());
		assertEquals(1, map.query(date.is(new Date(1_000L))).toList().size());
	}

	@Test
	void spatialGetter()
	{
		final GigaMap<City> map = GigaMap.New();
		IndexerGenerator.AnnotationBased(City.class).generateIndices(map);
		map.add(new City("Berlin", 52.520, 13.405));
		map.add(new City("New York", 40.713, -74.006));

		final SpatialIndexer<City> spatial = map.index().bitmap().getSpatialIndexer("spatial");
		assertNotNull(spatial);
		assertEquals(1, map.query(spatial.near(52.520, 13.405, 100)).toList().size());
	}

	@Test
	void generatedIndicesRegistry()
	{
		final GigaMap<Bean> map = GigaMap.New();
		final GeneratedIndices<Bean> idx =
			IndexerGenerator.AnnotationBased(Bean.class).generateIndices(map);

		map.add(new Bean("alice", Instant.parse("2024-01-01T00:00:00Z"), 100L, "ax-1", 10, new Date(1_000L)));
		map.add(new Bean("bob",   Instant.parse("2025-06-01T00:00:00Z"), 200L, "bx-9", 90, new Date(9_000L)));

		assertEquals(7, idx.all().size());
		assertNotNull(idx.get("name"));
		assertNull(idx.get("does-not-exist"));

		// typed convenience + raw typed get
		assertEquals(1, map.query(idx.getIndexerString("name").is("alice")).toList().size());
		assertEquals(1, map.query(idx.get(IndexerString.class, "name").startsWith("al")).toList().size());
		assertEquals(1, map.query(idx.getIndexerInstant("ts").after(Instant.parse("2025-01-01T00:00:00Z"))).toList().size());
		assertEquals(1, map.query(idx.getBinaryIndexer("id").is(100L)).toList().size());
		assertEquals(1, map.query(idx.getBinaryIndexerString("code").is("bx-9")).toList().size());
		assertEquals(1, map.query(idx.getByteIndexerNumber(Integer.class, "score").greaterThan(50)).toList().size());
		assertEquals(1, map.query(idx.getIndexerComparing(Date.class, "date").greaterThan(new Date(5_000L))).toList().size());
	}

	@Test
	void registryUsableAfterReload()
	{
		final GigaMap<Bean> map = newBeanMap();
		try(EmbeddedStorageManager storage = EmbeddedStorage.start(map, this.tempDir))
		{
		}

		try(EmbeddedStorageManager storage = EmbeddedStorage.start(this.tempDir))
		{
			@SuppressWarnings("unchecked")
			final GigaMap<Bean> map2 = (GigaMap<Bean>)storage.root();

			// re-run the idempotent generation on the reloaded map to obtain a fresh handle
			final GeneratedIndices<Bean> idx =
				IndexerGenerator.AnnotationBased(Bean.class).generateIndices(map2);

			assertEquals(7, idx.all().size());
			assertEquals(1, map2.query(idx.getIndexerString("name").is("bob")).toList().size());
			assertEquals(1, map2.query(idx.getBinaryIndexer("id").is(100L)).toList().size());
			assertEquals(1, map2.query(idx.getBinaryIndexerString("code").is("ax-1")).toList().size());
			assertEquals(1, map2.query(idx.getByteIndexerNumber(Integer.class, "score").greaterThan(50)).toList().size());
		}
	}
}
