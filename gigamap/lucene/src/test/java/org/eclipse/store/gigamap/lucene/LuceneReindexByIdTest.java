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

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies that a GigaMap with only a (non-bitmap) Lucene index can trigger reindexing of a mutated
 * entity through the entityId-based {@link GigaMap#apply(long, java.util.function.Function)} /
 * {@link GigaMap#update(long, java.util.function.Consumer)}, without a dummy bitmap index (issue #713).
 * The entity-instance based update()/apply() would throw IllegalStateException on such a map.
 */
public class LuceneReindexByIdTest
{
	@Test
	void updateById_reindexesLuceneOnlyMap()
	{
		final LuceneContext<Article> luceneContext = LuceneContext.New(
			DirectoryCreator.ByteBuffers(),
			new ArticleDocumentPopulator()
		);

		final GigaMap<Article> gigaMap = GigaMap.New(); // no bitmap index
		try(final LuceneIndex<Article> luceneIndex = gigaMap.index().register(LuceneIndex.Category(luceneContext)))
		{
			final long id = gigaMap.add(new Article("Title_1", "SomeContent"));

			// in-place mutation + reindex via the entityId-based overload
			gigaMap.update(id, article -> article.setTitle("Title_2"));

			// the old title is no longer found ...
			final List<Article> oldHits = new ArrayList<>();
			luceneIndex.query("title:Title_1", (i, entity, score) -> oldHits.add(entity));
			assertEquals(0, oldHits.size(), "stale title must not be indexed any more");

			// ... and the new title is found.
			final List<Article> newHits = new ArrayList<>();
			luceneIndex.query("title:Title_2", (i, entity, score) -> newHits.add(entity));
			assertEquals(1, newHits.size(), "updated title must be reindexed");
			assertEquals("Title_2", newHits.get(0).getTitle());
		}
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
