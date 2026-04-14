
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


import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.IndexerInstant;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for GitHub issue: ArrayIndexOutOfBoundsException in SubBitmapIndexHashing
 * when updating entity from null to non-null composite key value.
 * <p>
 * Root cause: {@code AbstractCompositeBitmapIndex.internalHandleChanged()} called
 * {@code ensureSubIndices(newKeys)} which grew the sub-index array (e.g. from 1 to 6),
 * then iterated <strong>all</strong> sub-indices calling
 * {@code subIndex.internalHandleChanged(oldKeys, ...)}.
 * Each sub-index called {@code internalLookupEntry(oldKeys)} → {@code indexEntity(oldKeys)} →
 * {@code index(oldKeys)} → {@code oldKeys[this.position]}.
 * When {@code oldKeys} was the {@code NULL()} sentinel (length 1) but
 * {@code this.position >= 1}, it threw {@code ArrayIndexOutOfBoundsException}.
 * <p>
 * Fix: {@code internalHandleChanged} now checks {@code isEmpty(oldKeys, i)} and
 * {@code isEmpty(newKeys, i)} per sub-index, dispatching to {@code internalAddToEntry}
 * or {@code internalRemove} instead of unconditionally calling {@code internalHandleChanged}.
 */
public class CompositeIndexNullHandlingTest
{

	@TempDir
	Path tempDir;

	/**
	 * Entity with an optional {@link Instant} field — mirrors real-world scenario
	 * like Booking with nullable {@code expiresAt}, {@code billingReportedAt}, etc.
	 */
	static final class Event
	{
		final long id;
		Instant timestamp; // initially null, set on update

		Event(final long id)
		{
			this.id = id;
			this.timestamp = null;
		}
	}

	static final class EventIndexer extends IndexerInstant.Abstract<Event>
	{
		@Override
		protected Instant getInstant(final Event entity)
		{
			return entity.timestamp;
		}
	}

	@Test
	void nullToNonNullCompositeKeyUpdateShouldWork()
	{
		final GigaMap<Event> map = GigaMap.New();
		final EventIndexer indexer = new EventIndexer();
		map.index().bitmap().ensure(indexer);

		// Add entity with null timestamp → composite key = NULL() = Object[1]
		final Event event = new Event(1L);
		map.add(event);
		assertEquals(1, map.size());

		// Update to set non-null timestamp → composite key = Object[6]
		// Before fix: ArrayIndexOutOfBoundsException (Index 1 out of bounds for length 1)
		assertDoesNotThrow(() ->
			map.update(event, e -> e.timestamp = Instant.parse("2025-06-15T10:30:00Z"))
		);

		assertEquals(1, map.size());
	}

	@Test
	void nonNullToNullCompositeKeyUpdateShouldWork()
	{
		final GigaMap<Event> map = GigaMap.New();
		final EventIndexer indexer = new EventIndexer();
		map.index().bitmap().ensure(indexer);

		// Add entity with non-null timestamp → composite key = Object[6]
		final Event event = new Event(1L);
		event.timestamp = Instant.parse("2025-06-15T10:30:00Z");
		map.add(event);

		// Update to null timestamp → composite key = NULL() = Object[1]
		assertDoesNotThrow(() ->
			map.update(event, e -> e.timestamp = null)
		);

		assertEquals(1, map.size());
	}

	@Test
	void nullToNonNullCompositeKeyUpdateWithPersistenceShouldWork()
	{
		final GigaMap<Event> map = GigaMap.New();
		final EventIndexer indexer = new EventIndexer();
		map.index().bitmap().ensure(indexer);

		// Add entity with null timestamp
		final Event event = new Event(1L);
		map.add(event);

		try (final EmbeddedStorageManager manager = EmbeddedStorage.start(map, this.tempDir))
		{
			// Update to set non-null timestamp
			assertDoesNotThrow(() ->
				map.update(event, e -> e.timestamp = Instant.parse("2025-06-15T10:30:00Z"))
			);

			assertEquals(1, map.size());
			map.store();
		}

		// Verify persistence
		try (final EmbeddedStorageManager manager = EmbeddedStorage.start(this.tempDir))
		{
			final GigaMap<Event> loadedMap = (GigaMap<Event>) manager.root();
			assertEquals(1, loadedMap.size());

			final Event loadedEvent = loadedMap.query().findFirst().get();
			assertNotNull(loadedEvent.timestamp);
			assertEquals(Instant.parse("2025-06-15T10:30:00Z"), loadedEvent.timestamp);
		}
	}

	@Test
	void multipleNullToNonNullUpdatesShouldWork()
	{
		final GigaMap<Event> map = GigaMap.New();
		final EventIndexer indexer = new EventIndexer();
		map.index().bitmap().ensure(indexer);

		// Add multiple entities with null timestamps
		final Event event1 = new Event(1L);
		final Event event2 = new Event(2L);
		final Event event3 = new Event(3L);
		map.addAll(event1, event2, event3);
		assertEquals(3, map.size());

		// Update all to non-null timestamps
		assertDoesNotThrow(() ->
		{
			map.update(event1, e -> e.timestamp = Instant.parse("2025-06-15T10:30:00Z"));
			map.update(event2, e -> e.timestamp = Instant.parse("2025-06-15T11:30:00Z"));
			map.update(event3, e -> e.timestamp = Instant.parse("2025-06-15T12:30:00Z"));
			map.update(event1, e -> {});
			map.update(event2, e -> {});
			map.update(event3, e -> {});
		});

		assertEquals(3, map.size());
	}

	@Test
	void nullToNonNullQueryShouldWork()
	{
		final GigaMap<Event> map = GigaMap.New();
		final EventIndexer indexer = new EventIndexer();
		map.index().bitmap().ensure(indexer);

		// Add entities with mixed null/non-null timestamps
		final Event event1 = new Event(1L);
		final Event event2 = new Event(2L);
		event2.timestamp = Instant.parse("2025-06-15T10:30:00Z");
		map.addAll(event1, event2);

		// Query for null timestamps
		assertEquals(1, map.query(indexer.is((Instant) null)).count());

		// Query for non-null timestamps
		assertEquals(1, map.query(indexer.is(Instant.parse("2025-06-15T10:30:00Z"))).count());

		// Update null to non-null
		map.update(event1, e -> e.timestamp = Instant.parse("2025-06-15T11:30:00Z"));

		// Verify queries after update
		assertEquals(0, map.query(indexer.is((Instant) null)).count());
		assertEquals(1, map.query(indexer.is((Instant) null).or(indexer.is(Instant.parse("2025-06-15T10:30:00Z")))).count());
	}
}
