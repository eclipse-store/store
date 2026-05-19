package org.eclipse.store.gigamap.indexer.byte_;

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

import org.eclipse.store.gigamap.types.ByteIndexerInstant;
import org.eclipse.store.gigamap.types.Condition;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.IndexerString;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ByteIndexerInstantTest
{
    @TempDir
    Path tempDir;

    static final Instant EPOCH   = Instant.EPOCH;                                       // 1970-01-01T00:00:00Z
    static final Instant BEFORE  = Instant.parse("1969-12-31T23:59:59Z");                // negative epochSecond
    static final Instant RECENT  = Instant.parse("2024-06-15T12:30:45Z");
    static final Instant NANO_A  = Instant.parse("2024-06-15T12:30:45.000000001Z");      // 1 nano after recent
    static final Instant NANO_B  = Instant.parse("2024-06-15T12:30:45.999999999Z");      // same second, max nanos
    static final Instant FUTURE  = Instant.parse("2030-01-01T00:00:00Z");

    @Test
    void equalityQueries()
    {
        final EventTimestampIndexer indexer = new EventTimestampIndexer();

        final GigaMap<Event> map = GigaMap.<Event>Builder()
            .withBitmapIdentityIndex(indexer)
            .build();

        map.add(new Event(EPOCH));
        map.add(new Event(BEFORE));
        map.add(new Event(RECENT));
        map.add(new Event(NANO_A));
        map.add(new Event(NANO_B));
        map.add(new Event(FUTURE));

        assertEquals(1, map.query(indexer.is(EPOCH)).toList().size());
        assertEquals(1, map.query(indexer.is(BEFORE)).toList().size());
        assertEquals(1, map.query(indexer.is(RECENT)).toList().size());
        assertEquals(1, map.query(indexer.is(NANO_A)).toList().size());
        assertEquals(1, map.query(indexer.is(NANO_B)).toList().size());
        assertEquals(1, map.query(indexer.is(FUTURE)).toList().size());
    }

    @Test
    void equalityWithNull()
    {
        final EventTimestampIndexer indexer = new EventTimestampIndexer();

        final GigaMap<Event> map = GigaMap.<Event>Builder()
            .withBitmapIdentityIndex(indexer)
            .build();

        map.add(new Event(EPOCH));
        map.add(new Event(null));

        assertEquals(1, map.query(indexer.is((Instant)null)).toList().size());
        assertTrue(map.query(indexer.is((Instant)null)).toList().get(0).getTimestamp() == null);
    }

    @Test
    void afterQuery()
    {
        final EventTimestampIndexer indexer = new EventTimestampIndexer();

        final GigaMap<Event> map = GigaMap.<Event>Builder()
            .withBitmapIdentityIndex(indexer)
            .build();

        map.add(new Event(EPOCH));
        map.add(new Event(BEFORE));
        map.add(new Event(RECENT));
        map.add(new Event(NANO_A));
        map.add(new Event(NANO_B));
        map.add(new Event(FUTURE));

        // after(EPOCH) should exclude BEFORE and EPOCH
        assertEquals(4, map.query(indexer.after(EPOCH)).toList().size());

        // after(FUTURE) should return nothing
        assertEquals(0, map.query(indexer.after(FUTURE)).toList().size());
    }

    @Test
    void beforeQuery()
    {
        final EventTimestampIndexer indexer = new EventTimestampIndexer();

        final GigaMap<Event> map = GigaMap.<Event>Builder()
            .withBitmapIdentityIndex(indexer)
            .build();

        map.add(new Event(EPOCH));
        map.add(new Event(BEFORE));
        map.add(new Event(RECENT));
        map.add(new Event(NANO_A));
        map.add(new Event(NANO_B));
        map.add(new Event(FUTURE));

        // before(EPOCH) should include only BEFORE
        assertEquals(1, map.query(indexer.before(EPOCH)).toList().size());

        // before(BEFORE) should return nothing
        assertEquals(0, map.query(indexer.before(BEFORE)).toList().size());
    }

    @Test
    void afterEqualQuery()
    {
        final EventTimestampIndexer indexer = new EventTimestampIndexer();

        final GigaMap<Event> map = GigaMap.<Event>Builder()
            .withBitmapIdentityIndex(indexer)
            .build();

        map.add(new Event(EPOCH));
        map.add(new Event(BEFORE));
        map.add(new Event(RECENT));
        map.add(new Event(NANO_A));
        map.add(new Event(NANO_B));
        map.add(new Event(FUTURE));

        // afterEqual(EPOCH) should include EPOCH + everything after
        assertEquals(5, map.query(indexer.afterEqual(EPOCH)).toList().size());

        // afterEqual(FUTURE) should include only FUTURE
        assertEquals(1, map.query(indexer.afterEqual(FUTURE)).toList().size());
    }

    @Test
    void beforeEqualQuery()
    {
        final EventTimestampIndexer indexer = new EventTimestampIndexer();

        final GigaMap<Event> map = GigaMap.<Event>Builder()
            .withBitmapIdentityIndex(indexer)
            .build();

        map.add(new Event(EPOCH));
        map.add(new Event(BEFORE));
        map.add(new Event(RECENT));
        map.add(new Event(NANO_A));
        map.add(new Event(NANO_B));
        map.add(new Event(FUTURE));

        // beforeEqual(EPOCH) should include BEFORE and EPOCH
        assertEquals(2, map.query(indexer.beforeEqual(EPOCH)).toList().size());

        // beforeEqual(BEFORE) should include only BEFORE
        assertEquals(1, map.query(indexer.beforeEqual(BEFORE)).toList().size());
    }

    @Test
    void betweenQuery()
    {
        final EventTimestampIndexer indexer = new EventTimestampIndexer();

        final GigaMap<Event> map = GigaMap.<Event>Builder()
            .withBitmapIdentityIndex(indexer)
            .build();

        map.add(new Event(EPOCH));
        map.add(new Event(BEFORE));
        map.add(new Event(RECENT));
        map.add(new Event(NANO_A));
        map.add(new Event(NANO_B));
        map.add(new Event(FUTURE));

        // between(EPOCH, RECENT) should include EPOCH, RECENT
        assertEquals(2, map.query(indexer.between(EPOCH, RECENT)).toList().size());

        // between(BEFORE, FUTURE) should include all 6
        assertEquals(6, map.query(indexer.between(BEFORE, FUTURE)).toList().size());
    }

    @Test
    void subSecondPrecision()
    {
        final EventTimestampIndexer indexer = new EventTimestampIndexer();

        final GigaMap<Event> map = GigaMap.<Event>Builder()
            .withBitmapIdentityIndex(indexer)
            .build();

        map.add(new Event(RECENT));
        map.add(new Event(NANO_A));
        map.add(new Event(NANO_B));

        // after(RECENT) should return NANO_A and NANO_B but not RECENT itself
        final List<Event> afterRecent = map.query(indexer.after(RECENT)).toList();
        assertEquals(2, afterRecent.size());

        // between(NANO_A, NANO_B) should return exactly the two nano-precision entries
        final List<Event> betweenNanos = map.query(indexer.between(NANO_A, NANO_B)).toList();
        assertEquals(2, betweenNanos.size());
    }

    @Test
    void negativeEpochSeconds()
    {
        final EventTimestampIndexer indexer = new EventTimestampIndexer();

        final GigaMap<Event> map = GigaMap.<Event>Builder()
            .withBitmapIdentityIndex(indexer)
            .build();

        map.add(new Event(BEFORE));
        map.add(new Event(EPOCH));
        map.add(new Event(RECENT));

        // BEFORE (1969) should sort before EPOCH (1970)
        assertEquals(1, map.query(indexer.before(EPOCH)).toList().size());
        assertEquals(BEFORE, map.query(indexer.before(EPOCH)).toList().get(0).getTimestamp());

        // after(BEFORE) should return EPOCH and RECENT
        assertEquals(2, map.query(indexer.after(BEFORE)).toList().size());
    }

    @Test
    void inSecondQuery()
    {
        final EventTimestampIndexer indexer = new EventTimestampIndexer();

        final GigaMap<Event> map = GigaMap.<Event>Builder()
            .withBitmapIdentityIndex(indexer)
            .build();

        map.add(new Event(EPOCH));
        map.add(new Event(BEFORE));
        map.add(new Event(RECENT));
        map.add(new Event(NANO_A));
        map.add(new Event(NANO_B));
        map.add(new Event(FUTURE));

        // inSecond(RECENT) should return RECENT, NANO_A, and NANO_B (all in same second)
        final List<Event> inSecond = map.query(indexer.inSecond(RECENT)).toList();
        assertEquals(3, inSecond.size());

        // inSecond(EPOCH) should return only EPOCH
        assertEquals(1, map.query(indexer.inSecond(EPOCH)).toList().size());

        // inSecond(FUTURE) should return only FUTURE
        assertEquals(1, map.query(indexer.inSecond(FUTURE)).toList().size());
    }

    @Test
    void persistenceRoundTrip()
    {
        final EventTimestampIndexer indexer = new EventTimestampIndexer();

        final GigaMap<Event> map = GigaMap.<Event>Builder()
            .withBitmapIdentityIndex(indexer)
            .build();

        map.add(new Event(EPOCH));
        map.add(new Event(BEFORE));
        map.add(new Event(RECENT));
        map.add(new Event(NANO_A));
        map.add(new Event(NANO_B));
        map.add(new Event(FUTURE));

        try (EmbeddedStorageManager manager = EmbeddedStorage.start(map, this.tempDir))
        {
            assertEquals(6, map.size());
        }

        final GigaMap<Event> newMap = GigaMap.New();
        try (EmbeddedStorageManager manager = EmbeddedStorage.start(newMap, this.tempDir))
        {
            assertEquals(6, newMap.size());

            final List<Event> result = newMap.query(indexer.is(RECENT)).toList();
            assertEquals(1, result.size());
            assertEquals(RECENT, result.get(0).getTimestamp());

            assertEquals(4, newMap.query(indexer.after(EPOCH)).toList().size());
            assertEquals(1, newMap.query(indexer.before(EPOCH)).toList().size());
            assertEquals(3, newMap.query(indexer.inSecond(RECENT)).toList().size());
        }
    }


    /**
     * Regression test for issue #653 / #654: composing a {@link ByteIndexerInstant} range
     * condition via {@link Condition#and(Condition)} (i) with another range condition and
     * (ii) with a condition from a different indexer type must produce the same result as
     * composing via {@link org.eclipse.store.gigamap.types.GigaQuery#and(Condition)}.
     */
    @Test
    void rangeCompositionViaConditionAnd()
    {
        final NamedEventTimestampIndexer tsIdx   = new NamedEventTimestampIndexer();
        final NameIndex                  nameIdx = new NameIndex();

        final GigaMap<NamedEvent> map = GigaMap.<NamedEvent>Builder()
            .withBitmapIndex(tsIdx)
            .withBitmapIndex(nameIdx)
            .build();
        map.add(new NamedEvent("Alice",   Instant.parse("2021-01-01T12:00:00Z")));
        map.add(new NamedEvent("Bob",     Instant.parse("2021-01-01T13:00:00Z")));
        map.add(new NamedEvent("Charlie", Instant.parse("2021-01-01T14:00:00Z")));
        map.add(new NamedEvent("Dave",    Instant.parse("2021-01-01T15:00:00Z")));

        final Instant lower = Instant.parse("2021-01-01T12:30:00Z"); // exclusive
        final Instant upper = Instant.parse("2021-01-01T14:30:00Z"); // exclusive

        // (i) range AND range
        final Condition<NamedEvent> after  = tsIdx.after(lower);
        final Condition<NamedEvent> before = tsIdx.before(upper);

        final List<NamedEvent> rangeAnd = map.query(after.and(before)).toList();
        final List<NamedEvent> rangeQ   = map.query().and(after).and(before).toList();

        assertEquals(2, rangeAnd.size(), "Condition.and() must respect both bounds");
        assertEquals(rangeQ.size(), rangeAnd.size(), "Condition.and() and GigaQuery.and() must agree");
        rangeAnd.forEach(e -> assertNotEquals("Alice", e.name()));
        rangeAnd.forEach(e -> assertNotEquals("Dave",  e.name()));

        // (ii) range AND condition from a different indexer
        final Condition<NamedEvent> bobMatch = nameIdx.is("Bob");

        final List<NamedEvent> mixedAnd = map.query(after.and(bobMatch)).toList();
        final List<NamedEvent> mixedQ   = map.query().and(after).and(bobMatch).toList();

        assertEquals(1, mixedAnd.size(), "range AND non-range condition must intersect correctly");
        assertEquals("Bob", mixedAnd.get(0).name());
        assertEquals(mixedQ.size(), mixedAnd.size());
    }


    static class EventTimestampIndexer extends ByteIndexerInstant.Abstract<Event>
    {
        @Override
        protected Instant getInstant(final Event entity)
        {
            return entity.getTimestamp();
        }
    }

    static class Event
    {
        private final Instant timestamp;

        Event(final Instant timestamp)
        {
            this.timestamp = timestamp;
        }

        public Instant getTimestamp()
        {
            return this.timestamp;
        }
    }

    static class NamedEventTimestampIndexer extends ByteIndexerInstant.Abstract<NamedEvent>
    {
        @Override
        protected Instant getInstant(final NamedEvent entity)
        {
            return entity.timestamp();
        }
    }

    static class NameIndex extends IndexerString.Abstract<NamedEvent>
    {
        @Override
        protected String getString(final NamedEvent entity)
        {
            return entity.name();
        }
    }

    static class NamedEvent
    {
        private final String  name;
        private final Instant timestamp;

        NamedEvent(final String name, final Instant timestamp)
        {
            this.name      = name;
            this.timestamp = timestamp;
        }

        String name()
        {
            return this.name;
        }

        Instant timestamp()
        {
            return this.timestamp;
        }
    }

}
