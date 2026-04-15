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
import org.eclipse.store.gigamap.types.GigaQuery;
import org.eclipse.store.gigamap.types.IndexerString;
import org.eclipse.store.gigamap.types.ScoredSearchResult;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link LuceneSearchResult} can be used as a {@link GigaMap.SubQuery} and
 * composed with a bitmap-backed {@link GigaQuery}.
 */
public class LuceneSearchSubQueryTest
{
	private static class Article
	{
		final String status ;
		final String title  ;
		final String content;

		Article(final String status, final String title, final String content)
		{
			this.status  = status ;
			this.title   = title  ;
			this.content = content;
		}
	}

	private static class StatusIndexer extends IndexerString.Abstract<Article>
	{
		@Override
		protected String getString(final Article entity)
		{
			return entity.status;
		}
	}

	private static class ArticlePopulator extends DocumentPopulator<Article>
	{
		@Override
		public void populate(final Document document, final Article entity)
		{
			document.add(createTextField("title"  , entity.title  ));
			document.add(createTextField("content", entity.content));
		}
	}


	private static LuceneContext<Article> newContext()
	{
		return LuceneContext.New(DirectoryCreator.ByteBuffers(), new ArticlePopulator());
	}


	@Test
	void subQueryIntersectsWithBitmapQuery()
	{
		final GigaMap<Article> map           = GigaMap.New();
		final StatusIndexer    statusIndexer = new StatusIndexer();
		map.index().bitmap().add(statusIndexer);
		try(final LuceneIndex<Article> luceneIndex = map.index().register(LuceneIndex.Category(newContext())))
		{
			final long idPub1 = map.add(new Article("PUBLISHED", "Title_1", "eclipse store gigamap"));
			final long idPub2 = map.add(new Article("PUBLISHED", "Title_2", "eclipse foundation"));
			final long idDrf1 = map.add(new Article("DRAFT"    , "Title_3", "eclipse foundation"));
			map.add(new Article("PUBLISHED", "Title_4", "unrelated content"));

			final LuceneSearchResult<Article> hits = luceneIndex.search("content:eclipse", 10);
			assertEquals(3, hits.size());

			// Only PUBLISHED articles whose content matches: idPub1, idPub2
			final Set<Long> combinedIds = new HashSet<>();
			map.query(statusIndexer).is("PUBLISHED").and(hits).iterateIndexed((id, article) -> combinedIds.add(id));

			assertEquals(Set.of(idPub1, idPub2), combinedIds);
			assertFalse(combinedIds.contains(idDrf1));
		}
	}

	@Test
	void subQueryWithQueryObject()
	{
		final GigaMap<Article> map           = GigaMap.New();
		final StatusIndexer    statusIndexer = new StatusIndexer();
		map.index().bitmap().add(statusIndexer);
		try(final LuceneIndex<Article> luceneIndex = map.index().register(LuceneIndex.Category(newContext())))
		{
			final long idPub = map.add(new Article("PUBLISHED", "Title_1", "some text"));
			map.add(new Article("DRAFT", "Title_1", "some text"));
			map.add(new Article("PUBLISHED", "Title_2", "some text"));

			final Query                       query = new TermQuery(new Term("title", "title_1"));
			final LuceneSearchResult<Article> hits  = luceneIndex.search(query, 10);
			assertEquals(2, hits.size());

			final long count = map.query(statusIndexer).is("PUBLISHED").and(hits).count();
			assertEquals(1, count);

			final Set<Long> combinedIds = new HashSet<>();
			map.query(statusIndexer).is("PUBLISHED").and(hits).iterateIndexed((id, article) -> combinedIds.add(id));
			assertEquals(Set.of(idPub), combinedIds);
		}
	}

	@Test
	void maxResultsIsHonored()
	{
		final GigaMap<Article> map = GigaMap.New();
		try(final LuceneIndex<Article> luceneIndex = map.index().register(LuceneIndex.Category(newContext())))
		{
			for(int i = 0; i < 10; i++)
			{
				map.add(new Article("PUBLISHED", "Title_" + i, "lucene indexing"));
			}

			final LuceneSearchResult<Article> hits = luceneIndex.search("content:lucene", 3);
			assertEquals(3, hits.size());
		}
	}

	@Test
	void defaultLimitMatchesMapSize()
	{
		final GigaMap<Article> map = GigaMap.New();
		try(final LuceneIndex<Article> luceneIndex = map.index().register(LuceneIndex.Category(newContext())))
		{
			for(int i = 0; i < 5; i++)
			{
				map.add(new Article("PUBLISHED", "Title_" + i, "matching word"));
			}

			// no maxResults arg -> defaults to gigaMap.size() == 5
			final LuceneSearchResult<Article> hits = luceneIndex.search("content:matching");
			assertEquals(5, hits.size());
		}
	}

	@Test
	void emptySubQueryYieldsEmptyResult()
	{
		final GigaMap<Article> map           = GigaMap.New();
		final StatusIndexer    statusIndexer = new StatusIndexer();
		map.index().bitmap().add(statusIndexer);
		try(final LuceneIndex<Article> luceneIndex = map.index().register(LuceneIndex.Category(newContext())))
		{
			map.add(new Article("PUBLISHED", "Title_1", "one"));
			map.add(new Article("PUBLISHED", "Title_2", "two"));

			final LuceneSearchResult<Article> hits = luceneIndex.search("content:nonexistentterm");
			assertTrue(hits.isEmpty());

			final long count = map.query(statusIndexer).is("PUBLISHED").and(hits).count();
			assertEquals(0, count);
		}
	}

	@Test
	void entryExposesIdScoreAndEntity()
	{
		final GigaMap<Article> map = GigaMap.New();
		try(final LuceneIndex<Article> luceneIndex = map.index().register(LuceneIndex.Category(newContext())))
		{
			map.add(new Article("PUBLISHED", "Title_1", "eclipse"));

			final LuceneSearchResult<Article> hits = luceneIndex.search("content:eclipse");
			final List<ScoredSearchResult.Entry<Article>> entries = hits.toList();
			assertEquals(1, entries.size());

			final ScoredSearchResult.Entry<Article> entry = entries.get(0);
			assertEquals(0, entry.entityId());
			assertTrue(entry.score() > 0f);
			assertNotNull(entry.entity());
			assertEquals("Title_1", entry.entity().title);
		}
	}
}
