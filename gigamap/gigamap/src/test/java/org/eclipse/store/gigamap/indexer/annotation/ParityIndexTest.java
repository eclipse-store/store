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
import org.eclipse.store.gigamap.types.BinaryIndexerDouble;
import org.eclipse.store.gigamap.types.BinaryIndexerFloat;
import org.eclipse.store.gigamap.types.ByteIndexerInstant;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.IndexerComparing;
import org.eclipse.store.gigamap.types.IndexerGenerator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Parity between manual and annotation-based index creation for cases that previously failed fast or
 * silently downgraded: binary float/double, bit-sliced Instant, and Comparable key types (range).
 */
public class ParityIndexTest
{
	static class Measurement
	{
		@Index(binary = true)               float   weight;
		@Index(binary = true)               double  ratio;
		@Index(kind = IndexKind.BIT_SLICED) Instant timestamp;

		Measurement(final float weight, final double ratio, final Instant timestamp)
		{
			this.weight    = weight;
			this.ratio     = ratio;
			this.timestamp = timestamp;
		}
	}

	static final class Version implements Comparable<Version>
	{
		final int value;

		Version(final int value)
		{
			this.value = value;
		}

		@Override
		public int compareTo(final Version o)
		{
			return Integer.compare(this.value, o.value);
		}

		@Override
		public boolean equals(final Object o)
		{
			return o instanceof Version && ((Version)o).value == this.value;
		}

		@Override
		public int hashCode()
		{
			return Objects.hashCode(this.value);
		}
	}

	static class Doc
	{
		@Index Date    date;
		@Index Version version;

		Doc(final Date date, final Version version)
		{
			this.date    = date;
			this.version = version;
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	void binaryFloatAndDoubleAreSupported()
	{
		final GigaMap<Measurement> map = GigaMap.New();
		IndexerGenerator.AnnotationBased(Measurement.class).generateIndices(map);

		map.add(new Measurement(1.5f, 2.5, Instant.parse("2024-01-01T00:00:00Z")));
		map.add(new Measurement(9.0f, 9.0, Instant.parse("2025-01-01T00:00:00Z")));

		final BinaryIndexerFloat<Measurement> weight =
			map.index().bitmap().getIndexer(BinaryIndexerFloat.class, "weight");
		assertInstanceOf(BinaryIndexerFloat.class, weight);
		assertEquals(1, map.query(weight.is(1.5f)).toList().size());

		final BinaryIndexerDouble<Measurement> ratio =
			map.index().bitmap().getIndexer(BinaryIndexerDouble.class, "ratio");
		assertInstanceOf(BinaryIndexerDouble.class, ratio);
		assertEquals(1, map.query(ratio.is(2.5)).toList().size());
	}

	@SuppressWarnings("unchecked")
	@Test
	void bitSlicedInstantSupportsRange()
	{
		final GigaMap<Measurement> map = GigaMap.New();
		IndexerGenerator.AnnotationBased(Measurement.class).generateIndices(map);

		map.add(new Measurement(1.5f, 2.5, Instant.parse("2024-01-01T00:00:00Z")));
		map.add(new Measurement(9.0f, 9.0, Instant.parse("2025-06-01T00:00:00Z")));

		final ByteIndexerInstant<Measurement> ts =
			map.index().bitmap().getIndexer(ByteIndexerInstant.class, "timestamp");
		assertInstanceOf(ByteIndexerInstant.class, ts);

		final List<Measurement> recent = map.query(ts.after(Instant.parse("2025-01-01T00:00:00Z"))).toList();
		assertEquals(1, recent.size());
		assertEquals(9.0f, recent.get(0).weight);
	}

	@SuppressWarnings("unchecked")
	@Test
	void comparableKeyTypesSupportRange()
	{
		final GigaMap<Doc> map = GigaMap.New();
		IndexerGenerator.AnnotationBased(Doc.class).generateIndices(map);

		final Date d1 = new Date(1_000L);
		final Date d2 = new Date(2_000L);
		final Date d3 = new Date(3_000L);
		map.add(new Doc(d1, new Version(1)));
		map.add(new Doc(d2, new Version(2)));
		map.add(new Doc(d3, new Version(3)));

		// java.util.Date is Comparable -> comparing index with range support (was equality-only before)
		final IndexerComparing<Doc, Date> date =
			map.index().bitmap().getIndexer(IndexerComparing.class, "date");
		assertInstanceOf(IndexerComparing.class, date);
		assertEquals(2, map.query(date.greaterThan(d1)).toList().size());
		assertEquals(1, map.query(date.is(d2)).toList().size());

		// custom Comparable key type -> range too
		final IndexerComparing<Doc, Version> version =
			map.index().bitmap().getIndexer(IndexerComparing.class, "version");
		assertInstanceOf(IndexerComparing.class, version);
		assertEquals(1, map.query(version.between(new Version(2), new Version(2))).toList().size());
		assertEquals(2, map.query(version.greaterThanEqual(new Version(2))).toList().size());
	}
}
