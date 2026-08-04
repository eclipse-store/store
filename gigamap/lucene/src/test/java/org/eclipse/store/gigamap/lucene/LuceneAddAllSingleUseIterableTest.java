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
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link GigaMap#addAll(Iterable)} used to hand the caller's {@link Iterable} to the index fan-out, which
 * traverses it once per index. An iterable that cannot be traversed repeatedly therefore left the entities
 * unindexed (or, in the worst case, indexed at ids holding no entity).
 * <p>
 * Pins that a single-use iterable is fully indexed by the Lucene index, which receives the batch via
 * {@link LuceneIndex.Internal#internalAddAll(long, Iterable)} and turns it into one
 * {@code IndexWriter#addDocuments} call.
 */
public class LuceneAddAllSingleUseIterableTest
{
	/**
	 * An {@link Iterable} that hands out the <b>same</b> iterator every time, i.e. it is exhausted after the
	 * first traversal.
	 */
	static <T> Iterable<T> sameIteratorEachTime(final List<T> elements)
	{
		final Iterator<T> shared = elements.iterator();

		return () -> shared;
	}

	@Test
	void addAll_singleUseIterable_indexesEveryEntity()
	{
		final LuceneContext<Article> luceneContext = LuceneContext.New(
			DirectoryCreator.ByteBuffers(),
			new ArticleDocumentPopulator()
		);

		final GigaMap<Article> gigaMap = GigaMap.New();
		try(final LuceneIndex<Article> luceneIndex = gigaMap.index().register(LuceneIndex.Category(luceneContext)))
		{
			final List<Article> articles = List.of(
				new Article("Title_1", "This is a first longer content text."),
				new Article("Title_2", "This is a second longer Text"),
				new Article("Title_3", "This is a third longer Text")
			);

			gigaMap.addAll(sameIteratorEachTime(articles));

			assertEquals(3, gigaMap.size(), "all entities of the batch must be added");

			// every entity must be findable, i.e. the whole batch reached the index
			for(final Article article : articles)
			{
				final List<Article> result = new ArrayList<>();
				luceneIndex.query("title:" + article.getTitle(), (id, entity, score) -> result.add(entity));

				assertEquals(1, result.size(), "entity \"" + article.getTitle() + "\" must be indexed");
				assertEquals(article.getTitle(), result.get(0).getTitle());
			}

			// and no phantom document may alias an entity added afterwards
			final List<Article> result = new ArrayList<>();
			gigaMap.add(new Article("Title_4", "This is a fourth longer Text"));
			luceneIndex.query("title:Title_4", (id, entity, score) -> result.add(entity));

			assertEquals(1, result.size());
			assertEquals("Title_4", result.get(0).getTitle());
		}
	}



	///////////////////////////////////////////////////////////////////////////
	// test domain //
	////////////////

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
		private final String title;
		private final String content;

		Article(final String title, final String content)
		{
			super();
			this.title   = title;
			this.content = content;
		}

		String getTitle()
		{
			return this.title;
		}

		String getContent()
		{
			return this.content;
		}
	}
}
