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
import org.apache.lucene.index.DirectoryReader;
import org.eclipse.store.gigamap.types.GigaMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Verifies that the write path of {@link LuceneIndex} never (re)opens the near-real-time reader, and that the
 * deferred reopen on the next search still provides read-your-writes semantics.
 * <p>
 * These assertions are white-box on purpose: the per-mutation reader reopen was functionally invisible, which is
 * why it could dominate ingest cost unnoticed. The reader field is package-private so this test can observe it
 * without reflection.
 */
public class LuceneWritePathReaderTest
{
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
			document.add(createTextField("title",   entity.title));
			document.add(createTextField("content", entity.content));
		}
	}

	private static LuceneContext<Article> standardContext()
	{
		return LuceneContext.New(DirectoryCreator.ByteBuffers(), new ArticlePopulator());
	}

	private static LuceneContext<Article> manualCommitContext()
	{
		return LuceneContext.New(
			DirectoryCreator.ByteBuffers(),
			AnalyzerCreator.Standard()    ,
			new ArticlePopulator()        ,
			false
		);
	}

	private static DirectoryReader readerOf(final LuceneIndex<Article> index)
	{
		return ((LuceneIndex.Default<Article>)index).reader;
	}


	@Test
	void writePathDoesNotOpenReader()
	{
		final GigaMap<Article> map = GigaMap.New();
		try(final LuceneIndex<Article> idx = map.index().register(LuceneIndex.Category(standardContext())))
		{
			for(int i = 0; i < 10; i++)
			{
				map.add(new Article("write", "content " + i));
			}

			assertNull(readerOf(idx), "Index mutations must not open a near-real-time reader");

			assertEquals(10, idx.query("title:write", 100).size(),
				"The first search must open the reader and see all uncommitted documents");
			assertNotNull(readerOf(idx), "The first search must open the near-real-time reader");
		}
	}

	@Test
	void writesAfterFirstSearchDoNotReopenReader()
	{
		final GigaMap<Article> map = GigaMap.New();
		try(final LuceneIndex<Article> idx = map.index().register(LuceneIndex.Category(standardContext())))
		{
			final long id = map.add(new Article("first", "content"));
			assertEquals(1, idx.query("title:first").size());

			final DirectoryReader readerAfterFirstSearch = readerOf(idx);
			assertNotNull(readerAfterFirstSearch);

			for(int i = 0; i < 20; i++)
			{
				map.add(new Article("later", "content " + i));
			}
			map.removeById(id);

			assertSame(readerAfterFirstSearch, readerOf(idx),
				"Index mutations must not reopen the near-real-time reader");

			// read-your-writes: the deferred reopen happens now
			assertEquals(20, idx.query("title:later", 100).size(),
				"The next search must see all documents added since the previous search");
			assertEquals(0, idx.query("title:first").size(),
				"The next search must not see the removed document");
			assertNotSame(readerAfterFirstSearch, readerOf(idx),
				"The next search must pick up a refreshed reader");
		}
	}

	@Test
	void commitAndRepeatedSearchDoNotReopenReader()
	{
		final GigaMap<Article> map = GigaMap.New();
		try(final LuceneIndex<Article> idx = map.index().register(LuceneIndex.Category(manualCommitContext())))
		{
			map.add(new Article("eclipse", "content"));
			assertEquals(1, idx.query("title:eclipse").size());

			final DirectoryReader reader = readerOf(idx);
			assertNotNull(reader);

			idx.commit();
			assertSame(reader, readerOf(idx), "A commit alone must not reopen the reader");

			assertEquals(1, idx.query("title:eclipse").size(), "Committed documents must stay visible");
			assertSame(reader, readerOf(idx),
				"A search without a preceding mutation must reuse the existing searcher");
		}
	}
}
