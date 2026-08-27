package org.eclipse.store.gigamap.query;

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

import org.eclipse.store.gigamap.types.EntityIdMatcher;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.GigaQuery;
import org.eclipse.store.gigamap.types.IndexerLocalDateTime;
import org.eclipse.store.gigamap.types.IndexerLong;
import org.eclipse.store.gigamap.types.IndexerString;
import org.eclipse.store.gigamap.types.IterationThreadProvider;
import org.eclipse.store.gigamap.types.ThreadCountProvider;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for sub-query composition correctness:
 * <ul>
 *     <li>{@link EntityIdMatcher.Multiple} honors AND (shortcircuits on any delegate returning -1).</li>
 *     <li>{@link GigaQuery.Default}'s internal {@code buildEntityIdMatcher} populates the matcher
 *     array correctly when two or more sub-queries are combined.</li>
 *     <li>{@code GigaQuery.idStart(...)} / {@code idBound(...)} are honored by iterator-based
 *     execution (e.g. {@code count()}, {@code toList()}).</li>
 *     <li>Threaded iteration and multi-consumer execution remain correct in the presence of a
 *     non-trivial idMatcher (should transparently fall back to single-threaded).</li>
 *     <li>Executing a query with a sub-query releases the read lock afterwards (no reader leak).</li>
 * </ul>
 */
public class SubQueryCompositionTest
{
	private static class Entity
	{
		final String category;
		final long   value   ;

		Entity(final String category, final long value)
		{
			this.category = category;
			this.value    = value   ;
		}

		@Override
		public String toString()
		{
			return "Entity{" +
					"category='" + category + '\'' +
					", value=" + value +
					'}';
		}
	}

	private static class CategoryIndexer extends IndexerString.Abstract<Entity>
	{
		@Override
		protected String getString(final Entity entity)
		{
			return entity.category;
		}
	}

	private static class ValueIndexer extends IndexerLong.Abstract<Entity>
	{
		@Override
		protected Long getLong(final Entity entity)
		{
			return entity.value;
		}
	}


	@Test
	void multipleSubQueriesAreAllApplied()
	{
		final GigaMap<Entity>  map           = GigaMap.New();
		final CategoryIndexer  categoryIndex = new CategoryIndexer();
		final ValueIndexer     valueIndex    = new ValueIndexer();
		map.index().bitmap().add(categoryIndex);
		map.index().bitmap().add(valueIndex);

		final long idMatch = map.add(new Entity("A", 10L));   // matches all three
		map.add(new Entity("A", 20L));   // matches category + range, fails value
		map.add(new Entity("B", 10L));   // matches value + range, fails category
		map.add(new Entity("A", 99L));   // matches category + value, fails range

		// Two sub-queries on top of a primary bitmap condition force Multiple's AND path.
		final GigaQuery<Entity> subB = map.query(valueIndex).is(10L);
		final GigaQuery<Entity> subC = map.query(valueIndex.between(0L, 15L));

		final Set<Long> ids = new HashSet<>();
		map.query(categoryIndex).is("A").and(subB).and(subC).iterateIndexed((id, e) -> ids.add(id));

		assertEquals(Set.of(idMatch), ids, "AND of a condition + two sub-queries must intersect all three");
	}

	@Test
	void multipleShortcircuitsOnRejection()
	{
		final GigaMap<Entity> map           = GigaMap.New();
		final CategoryIndexer categoryIndex = new CategoryIndexer();
		final ValueIndexer    valueIndex    = new ValueIndexer();
		map.index().bitmap().add(categoryIndex);
		map.index().bitmap().add(valueIndex);

		map.add(new Entity("A", 10L));
		map.add(new Entity("A", 20L));
		map.add(new Entity("A", 30L));

		// Two sub-queries where one rejects everything. Without the AND shortcircuit on -1,
		// Multiple's max(>=0, -1) would falsely report matches when the primary condition holds.
		final GigaQuery<Entity> subB = map.query(valueIndex).is(10L);   // matches id=0
		final GigaQuery<Entity> subC = map.query(valueIndex).is(999L);  // matches nothing

		final long count = map.query(categoryIndex).is("A").and(subB).and(subC).count();
		assertEquals(0, count, "AND must produce no matches when any sub-query rejects");
	}

	@Test
	void idStartIdBoundAreHonoredWithSubQuery()
	{
		final GigaMap<Entity> map           = GigaMap.New();
		final CategoryIndexer categoryIndex = new CategoryIndexer();
		map.index().bitmap().add(categoryIndex);

		final long id0 = map.add(new Entity("A", 0L));
		final long id1 = map.add(new Entity("A", 1L));
		final long id2 = map.add(new Entity("A", 2L));
		final long id3 = map.add(new Entity("A", 3L));
		final long id4 = map.add(new Entity("A", 4L));

		final GigaQuery<Entity> everyoneInA = map.query(categoryIndex).is("A");

		// id range restricted to [id1, id3] (inclusive both ends).
		final Set<Long> ids = new HashSet<>();
		map.query(categoryIndex).is("A")
			.idStart(id1)
			.idBound(id3 + 1)
			.and(everyoneInA)
			.iterateIndexed((id, e) -> ids.add(id));

		assertEquals(Set.of(id1, id2, id3), ids);
		assertFalse(ids.contains(id0));
		assertFalse(ids.contains(id4));
	}

	@Test
	void threadedIterationWithSubQueryIsStillCorrect()
	{
		final GigaMap<Entity> map           = GigaMap.New();
		final CategoryIndexer categoryIndex = new CategoryIndexer();
		final ValueIndexer    valueIndex    = new ValueIndexer();
		map.index().bitmap().add(categoryIndex);
		map.index().bitmap().add(valueIndex);

		final Set<Long> expected = new HashSet<>();
		for(int i = 0; i < 1000; i++)
		{
			final boolean isA = i % 3 == 0;
			final long    id  = map.add(new Entity(isA ? "A" : "B", i));
			if(isA && i % 2 == 0)
			{
				expected.add(id);
			}
		}

		// Sub-query: only entities with an even value. Combined with threaded execution over
		// category "A", the result must be the intersection.
		final GigaQuery<Entity> evenValues = map.query(valueIndex).is(v -> v % 2 == 0);

		final IterationThreadProvider threadProvider = IterationThreadProvider.Creating(
			ThreadCountProvider.Fixed(4)
		);

		final Set<Long> actual = new HashSet<>();
		map.query(threadProvider).and(categoryIndex.is("A")).and(evenValues)
			.iterateIndexed((id, e) -> {
				synchronized(actual) { actual.add(id); }
			});

		assertEquals(expected, actual,
			"threaded execution must still apply the sub-query's id matcher");
	}

	@Test
	void threadedMultiConsumerExecuteWithSubQueryIsCorrect()
	{
		final GigaMap<Entity> map           = GigaMap.New();
		final CategoryIndexer categoryIndex = new CategoryIndexer();
		final ValueIndexer    valueIndex    = new ValueIndexer();
		map.index().bitmap().add(categoryIndex);
		map.index().bitmap().add(valueIndex);

		int expected = 0;
		for(int i = 0; i < 200; i++)
		{
			final boolean isA = i % 3 == 0;
			map.add(new Entity(isA ? "A" : "B", i));
			if(isA && i % 2 == 0)
			{
				expected++;
			}
		}

		final GigaQuery<Entity> evenValues = map.query(valueIndex).is(v -> v % 2 == 0);

		final IterationThreadProvider threadProvider = IterationThreadProvider.Creating(
			ThreadCountProvider.Fixed(4)
		);

		// Collect into a shared set — each consumer sees its own id-partition of the result;
		// the union across consumers must equal the expected filtered set.
		final Set<Entity> union = new HashSet<>();
		@SuppressWarnings("unchecked")
		final Consumer<? super Entity>[] consumers = new Consumer[] {
			(Consumer<Entity>) e -> { synchronized(union) { union.add(e); } },
			(Consumer<Entity>) e -> { synchronized(union) { union.add(e); } }
		};
		map.query(threadProvider).and(categoryIndex.is("A")).and(evenValues)
			.execute(consumers);

		assertEquals(expected, union.size(),
			"multi-consumer execution with a sub-query must produce the full filtered set across partitions");
	}

	/**
	 * Adds 50 000 entities, each with a category (A/B/C), a value, and a LocalDateTime
	 * derived from the entity index. The test uses a composite index (IndexerLocalDateTime,
	 * which is backed by a HashingCompositeIndexer) combined with a bitmap sub-query on
	 * category to verify that the composite index returns exactly the expected set of IDs.
	 */
	@Test
	void largeDatasetCompositeIndexFindsAllRelevantEntities()
	{
		final GigaMap<Entity>   map           = GigaMap.New();
		final CategoryIndexer   categoryIndex = new CategoryIndexer();
		final ValueIndexer      valueIndex    = new ValueIndexer();
		final DateTimeIndexer   dateTimeIndex = new DateTimeIndexer();
		map.index().bitmap().add(categoryIndex);
		map.index().bitmap().add(valueIndex);
		map.index().bitmap().add(dateTimeIndex);

		// Base timestamp used to generate per-entity dates.
		final LocalDateTime base = LocalDateTime.of(2000, 1, 1, 0, 0, 0);

		final int TOTAL         = 5_000;
		// Categories cycle A -> B -> C -> A ...
		final String[] cats     = {"A", "B", "C"};
		// IDs of entities in category "A" whose value is divisible by 7 — the target subset.
		final Set<Long> expectedIds = new HashSet<>();

		for(int i = 0; i < TOTAL; i++)
		{
			final String  cat   = cats[i % 3];
			final long    value = i;
			final long    id    = map.add(new Entity(cat, value));
			if("A".equals(cat) && value % 7 == 0)
			{
				expectedIds.add(id);
			}
		}

		// Sub-query 1: category == "A"  (bitmap index)
		final GigaQuery<Entity> catAQuery = map.query(categoryIndex).is("A");
		// Sub-query 2: value divisible by 7  (bitmap predicate)
		final GigaQuery<Entity> mod7Query = map.query(valueIndex).is(v -> v % 7 == 0);

		// Primary condition: dateTime after the base (all entities satisfy this because
		// each entity's minute = i % 60 and hour grows beyond midnight very quickly,
		// so we simply match anything after the epoch-like base).
		// We anchor the primary on the composite (datetime) index so we exercise the
		// HashingCompositeIndexer code path; the sub-queries narrow it via AND.
		final Set<Long> actualIds = new HashSet<>();

		map.query(dateTimeIndex.after(base.minusSeconds(1)))
			.and(catAQuery)
			.and(mod7Query)
			.iterateIndexed((id, e) -> actualIds.add(id));

		assertEquals(expectedIds.size(), actualIds.size(),
			"composite index + two sub-queries must find exactly the expected number of entities");
		assertEquals(expectedIds, actualIds,
			"composite index + two sub-queries must return exactly the expected entity IDs");
	}

	// -------------------------------------------------------------------------
	// Helper indexer used only by largeDatasetCompositeIndexFindsAllRelevantEntities
	// -------------------------------------------------------------------------

	private static class DateTimeIndexer extends IndexerLocalDateTime.Abstract<Entity>
	{
		// Each entity gets a unique minute derived from its value so dates are spread
		// across many distinct LocalDateTime values, exercising the composite index
		// at high cardinality.
		@Override
		protected LocalDateTime getLocalDateTime(final Entity entity)
		{
			final long minutesFromEpoch = entity.value;
			// Spread over ~34 years worth of minutes (50 000 minutes ≈ 34 days but
			// that is fine — we just need a diverse set of composite key entries).
			return LocalDateTime.of(2000, 1, 1, 0, 0, 0).plusMinutes(minutesFromEpoch);
		}
	}

	@Test
	void subQueryExecutionReleasesReaderLock()
	{
		final GigaMap<Entity> map           = GigaMap.New();
		final CategoryIndexer categoryIndex = new CategoryIndexer();
		final ValueIndexer    valueIndex    = new ValueIndexer();
		map.index().bitmap().add(categoryIndex);
		map.index().bitmap().add(valueIndex);

		map.add(new Entity("A", 10L));
		map.add(new Entity("A", 20L));
		map.add(new Entity("B", 10L));

		final GigaQuery<Entity> tens = map.query(valueIndex).is(10L);

		// Execute a composed query multiple times; each composition materializes a matcher that
		// must not leak a reader registration.
		for(int i = 0; i < 5; i++)
		{
			final List<Entity> hits = map.query(categoryIndex).is("A").and(tens).toList();
			assertEquals(1, hits.size());
		}

		assertFalse(map.isReadOnly(), "no reader registration must outlive a completed query");

		// Sanity: a mutation must succeed — the map is not stuck in read-only mode.
		map.add(new Entity("A", 10L));
		assertEquals(2, map.query(categoryIndex).is("A").and(tens).count());
	}
}
