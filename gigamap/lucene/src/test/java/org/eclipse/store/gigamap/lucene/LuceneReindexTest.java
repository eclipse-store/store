package org.eclipse.store.gigamap.lucene;

/*-
 * #%L
 * EclipseStore GigaMap Lucene
 * %%
 * Copyright (C) 2023 - 2025 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import org.apache.lucene.document.Document;
import org.eclipse.store.gigamap.types.GigaMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies that {@link GigaMap#reindex()} rebuilds a Lucene index from the current entity state, recovering
 * from the stale-index situation that arises when an indexed entity is mutated directly (i.e. not via
 * {@code update()} / {@code apply()}) and then stored (issue #714).
 */
public class LuceneReindexTest
{
	@Test
	void reindex_recoversStaleEmbeddedIndex()
	{
		final LuceneContext<Article> context = LuceneContext.New(
			DirectoryCreator.ByteBuffers(),
			new ArticleDocumentPopulator()
		);

		final GigaMap<Article> gigaMap = GigaMap.New();
		try(final LuceneIndex<Article> luceneIndex = gigaMap.index().register(LuceneIndex.Category(context)))
		{
			final Article article = new Article("Title_1", "SomeContent");
			gigaMap.add(article);

			// direct mutation - bypasses the indexers, so the Lucene document stays stale
			article.setTitle("Title_2");

			// stale state: the old title is still indexed, the new one is not
			assertEquals(1, query(luceneIndex, "title:Title_1").size(), "index is expected to be stale before reindex");
			assertEquals(0, query(luceneIndex, "title:Title_2").size(), "new title must not be indexed before reindex");

			// rebuild from current entity state
			gigaMap.reindex();

			assertEquals(0, query(luceneIndex, "title:Title_1").size(), "stale title must be gone after reindex");
			final List<Article> newHits = query(luceneIndex, "title:Title_2");
			assertEquals(1, newHits.size(), "updated title must be reindexed");
			assertEquals("Title_2", newHits.get(0).getTitle());
		}
	}

	@Test
	void reindex_recoversStaleExternalIndex(@TempDir final Path indexDir)
	{
		final LuceneContext<Article> context = LuceneContext.New(
			DirectoryCreator.MMap(indexDir),
			new ArticleDocumentPopulator()
		);

		final GigaMap<Article> gigaMap = GigaMap.New();
		try(final LuceneIndex<Article> luceneIndex = gigaMap.index().register(LuceneIndex.Category(context)))
		{
			final Article article = new Article("Alpha", "SomeContent");
			gigaMap.add(article);

			article.setTitle("Beta");

			assertEquals(1, query(luceneIndex, "title:Alpha").size(), "index is expected to be stale before reindex");
			assertEquals(0, query(luceneIndex, "title:Beta").size(), "new title must not be indexed before reindex");

			gigaMap.reindex();

			assertEquals(0, query(luceneIndex, "title:Alpha").size(), "stale title must be gone after reindex");
			final List<Article> newHits = query(luceneIndex, "title:Beta");
			assertEquals(1, newHits.size(), "updated title must be reindexed");
			assertEquals("Beta", newHits.get(0).getTitle());
		}
	}

	private static List<Article> query(final LuceneIndex<Article> luceneIndex, final String queryText)
	{
		final List<Article> hits = new ArrayList<>();
		luceneIndex.query(queryText, (id, entity, score) -> hits.add(entity));
		return hits;
	}

	private static class ArticleDocumentPopulator extends DocumentPopulator<Article>
	{
		@Override
		public void populate(final Document document, final Article entity)
		{
			document.add(createTextField("title", entity.getTitle()));
			document.add(createTextField("content", entity.getContent()));
		}
	}

	private static class Article
	{
		private String title;
		private String content;

		Article(final String title, final String content)
		{
			this.title   = title;
			this.content = content;
		}

		String getTitle()
		{
			return this.title;
		}

		void setTitle(final String title)
		{
			this.title = title;
		}

		String getContent()
		{
			return this.content;
		}
	}
}
