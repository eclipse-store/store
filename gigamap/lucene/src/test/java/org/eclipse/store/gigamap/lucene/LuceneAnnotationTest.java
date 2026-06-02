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

import org.eclipse.store.gigamap.lucene.annotations.FullText;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.IndexerGenerator;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class LuceneAnnotationTest
{
	@TempDir
	Path storagePath;

	static class Article
	{
		@FullText
		String title;

		@FullText
		String content;

		Article(final String title, final String content)
		{
			this.title   = title;
			this.content = content;
		}
	}

	static class ArticleCombo
	{
		@org.eclipse.store.gigamap.annotations.Index
		String category;

		@FullText
		String body;

		ArticleCombo(final String category, final String body)
		{
			this.category = category;
			this.body     = body;
		}
	}

	static class Plain
	{
		@org.eclipse.store.gigamap.annotations.Index
		String name;

		Plain(final String name)
		{
			this.name = name;
		}
	}

	record Doc(@FullText String title)
	{
	}

	@Test
	void recordComponentIsIndexedAndQueryable()
	{
		final GigaMap<Doc> map = GigaMap.New();
		IndexerGenerator.AnnotationBased(Doc.class)
			.register(LuceneAnnotationHandler.New())
			.generateIndices(map);

		map.add(new Doc("hello world"));
		map.add(new Doc("another text"));

		final LuceneIndex<Doc> luceneIndex = map.index().get(LuceneIndex.class);
		final List<Doc> hits = luceneIndex.query("title:hello");
		assertEquals(1, hits.size());
		assertEquals("hello world", hits.get(0).title());
	}

	@Test
	void fullTextAnnotationBuildsQueryableLuceneIndex()
	{
		final GigaMap<Article> map = GigaMap.New();
		IndexerGenerator.AnnotationBased(Article.class)
			.register(LuceneAnnotationHandler.New())
			.generateIndices(map);

		map.add(new Article("Title_1", "This is a first longer content text."));
		map.add(new Article("Title_2", "This is a second longer Text"));
		map.add(new Article("Title_3", "This is a third longer Text"));

		final LuceneIndex<Article> luceneIndex = map.index().get(LuceneIndex.class);

		final List<Article> byTitle = luceneIndex.query("title:Title_1");
		assertEquals(1, byTitle.size());
		assertEquals("Title_1", byTitle.get(0).title);

		final List<Article> byContent = luceneIndex.query("content:second");
		assertEquals(1, byContent.size());
		assertEquals("Title_2", byContent.get(0).title);
	}

	@Test
	void noFullTextMemberMeansNoLuceneIndex()
	{
		final GigaMap<Plain> map = GigaMap.New();
		IndexerGenerator.AnnotationBased(Plain.class)
			.register(LuceneAnnotationHandler.New())
			.generateIndices(map);

		assertNull(map.index().get(LuceneIndex.class));
		// the bitmap index for @Index is still generated
		map.add(new Plain("a"));
		assertEquals(1, map.query(map.index().bitmap().getIndexerString("name").is("a")).toList().size());
	}

	@Test
	void bitmapAndFullTextCoexist()
	{
		final GigaMap<ArticleCombo> map = GigaMap.New();
		IndexerGenerator.AnnotationBased(ArticleCombo.class)
			.register(LuceneAnnotationHandler.New())
			.generateIndices(map);

		map.add(new ArticleCombo("news", "breaking story about indexing"));
		map.add(new ArticleCombo("blog", "a quiet post about gardening"));

		assertEquals(1, map.query(map.index().bitmap().getIndexerString("category").is("news")).toList().size());

		final LuceneIndex<ArticleCombo> luceneIndex = map.index().get(LuceneIndex.class);
		final List<ArticleCombo> hits = luceneIndex.query("body:indexing");
		assertEquals(1, hits.size());
		assertEquals("news", hits.get(0).category);
	}

	@Test
	void annotationBuiltIndexSurvivesStoreAndReload()
	{
		final GigaMap<Article> map = GigaMap.New();
		IndexerGenerator.AnnotationBased(Article.class)
			.register(LuceneAnnotationHandler.New())
			.generateIndices(map);

		map.add(new Article("Title_1", "This is a first longer content text."));
		map.add(new Article("Title_2", "This is a second longer Text"));

		try(EmbeddedStorageManager storageManager = EmbeddedStorage.start(map, this.storagePath))
		{
		}

		try(EmbeddedStorageManager storageManager = EmbeddedStorage.start(this.storagePath))
		{
			@SuppressWarnings("unchecked")
			final GigaMap<Article>     map2        = (GigaMap<Article>)storageManager.root();
			final LuceneIndex<Article> luceneIndex = map2.index().get(LuceneIndex.class);

			final List<Article> result = luceneIndex.query("title:Title_1");
			assertEquals(1, result.size());
			assertEquals("Title_1", result.get(0).title);
		}
	}
}
