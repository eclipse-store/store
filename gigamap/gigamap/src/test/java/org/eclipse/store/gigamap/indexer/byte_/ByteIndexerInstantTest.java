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
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

}
