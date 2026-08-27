package org.eclipse.store.gigamap.lucene;

/*-
 * #%L
 * EclipseStore GigaMap Lucene
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

import org.apache.lucene.document.Document;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.eclipse.store.gigamap.types.GigaMap;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that querying a {@link LuceneIndex} whose {@link GigaMap} is currently empty returns an
 * empty result instead of throwing.
 * <p>
 * The convenience overloads without an explicit {@code maxResults} derive that limit from the map
 * size, which is 0 for an empty map, while Lucene's {@code IndexSearcher.search(query, numHits)}
 * rejects {@code numHits <= 0} with {@code IllegalArgumentException: numHits must be > 0}. All
 * search entry points must therefore treat a non-positive limit as "no results wanted".
 */
public class LuceneEmptyMapQueryTest
{
	// ── shared entity ─────────────────────────────────────────────────────────

	private static class Article
	{
		final String title;
		final String content;

		Article(final String title, final String content)
		{
			this.title   = title;
			this.content = content;
		}
	}

	private static class ArticlePopulator extends DocumentPopulator<Article>
	{
		@Override
		public void populate(final Document document, final Article entity)
		{
			document.add(createTextField("title",     entity.title));
			document.add(createStringField("exact",   entity.title));
			document.add(createTextField("content",   entity.content));
		}
	}

	private static LuceneContext<Article> standardContext()
	{
		return LuceneContext.New(DirectoryCreator.ByteBuffers(), new ArticlePopulator());
	}


	// ── freshly created, still empty map ──────────────────────────────────────

	@Test
	void queryOnEmptyMapReturnsEmpty()
	{
		final GigaMap<Article> map = GigaMap.New();
		try(final LuceneIndex<Article> idx = map.index().register(LuceneIndex.Category(standardContext())))
		{
			final Query query = new TermQuery(new Term("exact", "nothing"));

			assertTrue(idx.query("title:anything").isEmpty(),
				"query(String) on an empty map must return empty");
			assertTrue(idx.query(query).isEmpty(),
				"query(Query) on an empty map must return empty, not throw \"numHits must be > 0\"");
			assertTrue(idx.search("title:anything").isEmpty(),
				"search(String) on an empty map must return empty");
			assertTrue(idx.search(query).isEmpty(),
				"search(Query) on an empty map must return empty");
		}
	}

	@Test
	void queryWithAcceptorOnEmptyMapReturnsUnmodifiedAcceptor()
	{
		final GigaMap<Article> map = GigaMap.New();
		try(final LuceneIndex<Article> idx = map.index().register(LuceneIndex.Category(standardContext())))
		{
			final List<Article> collected = new ArrayList<>();

			final LuceneIndex.SearchResultAcceptor<Article> acceptor =
				(entityId, entity, score) -> collected.add(entity);

			final LuceneIndex.SearchResultAcceptor<Article> returned =
				idx.query(new TermQuery(new Term("exact", "nothing")), acceptor);

			assertSame(acceptor, returned, "the very acceptor that was passed in must be returned");
			assertTrue(collected.isEmpty(), "the acceptor must not be called for an empty map");
		}
	}

	@Test
	void nonPositiveMaxResultsReturnsEmpty()
	{
		final GigaMap<Article> map = GigaMap.New();
		try(final LuceneIndex<Article> idx = map.index().register(LuceneIndex.Category(standardContext())))
		{
			map.add(new Article("eclipse store", "content"));

			final Query query = new TermQuery(new Term("exact", "eclipse store"));
			assertEquals(1, idx.query(query).size(), "sanity: findable with the default maxResults");

			// an explicitly non-positive limit means "no results wanted" on every overload
			assertTrue(idx.query(query, 0).isEmpty(),  "query(Query, 0) must return empty");
			assertTrue(idx.query(query, -1).isEmpty(), "query(Query, -1) must return empty");
			assertTrue(idx.query("title:eclipse", 0).isEmpty(), "query(String, 0) must return empty");
			assertTrue(idx.search(query, 0).isEmpty(), "search(Query, 0) must return empty");
			assertTrue(idx.search("title:eclipse", 0).isEmpty(), "search(String, 0) must return empty");

			// including the two acceptor overloads that carry the limit - the actual defect site
			final List<Article> collected = new ArrayList<>();
			final LuceneIndex.SearchResultAcceptor<Article> acceptor =
				(entityId, entity, score) -> collected.add(entity);

			assertSame(acceptor, idx.query(query, 0, acceptor),
				"query(Query, 0, acceptor) must return the acceptor it was passed");
			assertSame(acceptor, idx.query(query, -1, acceptor),
				"query(Query, -1, acceptor) must return the acceptor it was passed");
			assertSame(acceptor, idx.query("title:eclipse", 0, acceptor),
				"query(String, 0, acceptor) must return the acceptor it was passed");

			assertTrue(collected.isEmpty(), "no acceptor call may happen for a non-positive limit");
		}
	}


	// ── map emptied again after having held entities ──────────────────────────

	@Test
	void queryAfterRemoveByIdEmptiedTheMapReturnsEmpty()
	{
		final GigaMap<Article> map = GigaMap.New();
		try(final LuceneIndex<Article> idx = map.index().register(LuceneIndex.Category(standardContext())))
		{
			final long entityId = map.add(new Article("solo", "content"));
			assertEquals(1, idx.query("title:solo").size(), "sanity: findable while present");

			map.removeById(entityId);

			// the map size is back to 0, so the default maxResults is derived from 0 again
			assertTrue(idx.query("title:solo").isEmpty(),
				"query(String) after the map emptied must return empty");
			assertTrue(idx.query(new TermQuery(new Term("exact", "solo"))).isEmpty(),
				"query(Query) after the map emptied must return empty, not throw");
		}
	}

	@Test
	void queryAfterRemoveAllReturnsEmpty()
	{
		final GigaMap<Article> map = GigaMap.New();
		try(final LuceneIndex<Article> idx = map.index().register(LuceneIndex.Category(standardContext())))
		{
			map.add(new Article("first",  "content"));
			map.add(new Article("second", "content"));
			assertEquals(2, idx.query("content:content").size(), "sanity: findable while present");

			map.removeAll();

			assertTrue(idx.query(new TermQuery(new Term("exact", "first"))).isEmpty(),
				"query(Query) after removeAll must return empty, not throw");
		}
	}
}
