package org.eclipse.store.gigamap.indexer.edge;

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
import org.eclipse.store.gigamap.types.GigaQuery;
import org.eclipse.store.gigamap.types.IndexerString;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for cyclic sub-query registration via {@link GigaQuery#and(GigaMap.SubQuery)}.
 * <p>
 * Root cause: {@code GigaQuery} implements {@code GigaMap.SubQuery} itself, and
 * {@code and(GigaMap.SubQuery)} registered the passed sub-query without any identity check. A query
 * registered as its own sub-query therefore made {@code buildEntityIdMatcher} re-enter
 * {@code provideEntityIdMatcher()} on the very same query, recursing until the stack was exhausted -
 * before a single entity was tested.
 * <p>
 * Fix: registering the query itself is skipped (a query AND itself is the query), and an indirect
 * cycle is reported while resolving the matchers instead of overflowing the stack.
 */
public class SubQuerySelfReferenceTest
{
	static final class Rec
	{
		final String cat;

		Rec(final String cat)
		{
			this.cat = cat;
		}
	}

	static final class CatIdx extends IndexerString.Abstract<Rec>
	{
		@Override
		protected String getString(final Rec entity)
		{
			return entity.cat;
		}
	}

	private final CatIdx cat = new CatIdx();

	private GigaMap<Rec> newMap()
	{
		final GigaMap<Rec> map = GigaMap.New();
		map.index().bitmap().add(this.cat);
		map.add(new Rec("a"));
		map.add(new Rec("b"));
		map.add(new Rec("a"));

		return map;
	}

	@Test
	@Timeout(60)
	void selfSubQueryIsIdempotent()
	{
		final GigaMap<Rec> map = this.newMap();

		final long plain = map.query(this.cat.is("a")).count();
		assertEquals(2, plain);

		final GigaQuery<Rec> query = map.query(this.cat.is("a"));
		assertEquals(plain, query.and(query).count(), "q AND q == q");
	}

	@Test
	@Timeout(60)
	void indirectSubQueryCycleIsReported()
	{
		final GigaMap<Rec> map = this.newMap();

		final GigaQuery<Rec> q1 = map.query(this.cat.is("a"));
		final GigaQuery<Rec> q2 = map.query(this.cat.is("b"));

		q1.and(q2);
		q2.and(q1);

		assertThrows(IllegalStateException.class, q1::count, "cyclic sub-query registration");
	}

	@Test
	@Timeout(60)
	void acyclicSubQueryStillRestrictsTheResult()
	{
		final GigaMap<Rec> map = this.newMap();

		final GigaQuery<Rec> subQuery = map.query(this.cat.is("b"));

		assertEquals(0, map.query(this.cat.is("a")).and(subQuery).count(), "cat=a AND cat=b");
		assertEquals(2, map.query(this.cat.is("a")).and(map.query(this.cat.is("a"))).count(), "cat=a AND cat=a");
	}
}
