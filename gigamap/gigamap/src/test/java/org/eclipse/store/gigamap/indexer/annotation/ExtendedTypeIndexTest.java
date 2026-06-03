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
import org.eclipse.store.gigamap.types.Indexer;
import org.eclipse.store.gigamap.types.IndexerGenerator;
import org.eclipse.store.gigamap.types.IndexerInstant;
import org.eclipse.store.gigamap.types.IndexerZonedDateTime;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ExtendedTypeIndexTest
{
	static class Event
	{
		@Index Instant       ts;
		@Index ZonedDateTime zdt;
		@Index BigDecimal    amount;

		Event(final Instant ts, final ZonedDateTime zdt, final BigDecimal amount)
		{
			this.ts     = ts;
			this.zdt    = zdt;
			this.amount = amount;
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	void instantZonedAndBigDecimalAreMappedAndQueryable()
	{
		final GigaMap<Event> map = GigaMap.New();
		IndexerGenerator.AnnotationBased(Event.class).generateIndices(map.index().bitmap());

		final Instant       t1 = Instant.parse("2024-01-01T10:15:30Z");
		final Instant       t2 = Instant.parse("2025-06-02T08:00:00Z");
		final ZonedDateTime z1 = t1.atZone(ZoneOffset.UTC);
		final ZonedDateTime z2 = t2.atZone(ZoneOffset.UTC);

		map.add(new Event(t1, z1, new BigDecimal("19.99")));
		map.add(new Event(t2, z2, new BigDecimal("5.00")));

		final IndexerInstant<Event> instant = map.index().bitmap().getIndexer(IndexerInstant.class, "ts");
		List<Event> result = map.query(instant.is(t1)).toList();
		assertEquals(1, result.size());
		assertEquals(t1, result.get(0).ts);

		final IndexerZonedDateTime<Event> zoned = map.index().bitmap().getIndexer(IndexerZonedDateTime.class, "zdt");
		result = map.query(zoned.is(z2)).toList();
		assertEquals(1, result.size());

		final Indexer<Event, BigDecimal> amount = map.index().bitmap().getIndexerForKey(BigDecimal.class, "amount");
		assertEquals(BigDecimal.class, amount.keyType());
		result = map.query(amount.is(new BigDecimal("19.99"))).toList();
		assertEquals(1, result.size());
		assertEquals(0, new BigDecimal("19.99").compareTo(result.get(0).amount));
	}

	@SuppressWarnings("unchecked")
	@Test
	void instantRangeQuery()
	{
		final GigaMap<Event> map = GigaMap.New();
		IndexerGenerator.AnnotationBased(Event.class).generateIndices(map.index().bitmap());

		final Instant t1 = Instant.parse("2024-01-01T10:15:30Z");
		final Instant t2 = Instant.parse("2025-06-02T08:00:00Z");
		map.add(new Event(t1, t1.atZone(ZoneOffset.UTC), BigDecimal.ONE));
		map.add(new Event(t2, t2.atZone(ZoneOffset.UTC), BigDecimal.TEN));

		final IndexerInstant<Event> instant = map.index().bitmap().getIndexer(IndexerInstant.class, "ts");
		final List<Event> result = map.query(instant.after(Instant.parse("2025-01-01T00:00:00Z"))).toList();
		assertEquals(1, result.size());
		assertEquals(t2, result.get(0).ts);
	}
}
