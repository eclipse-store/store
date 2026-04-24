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

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.eclipse.store.gigamap.types.GigaMap;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that various Lucene query types work correctly through the GigaMap-Lucene integration.
 * These tests exercise the integration layer, not Lucene internals.
 */
public class LuceneQueryTypesTest
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
			document.add(createTextField("title",   entity.title));
			document.add(createTextField("content", entity.content));
		}
	}

	private static LuceneContext<Article> standardContext()
	{
		return LuceneContext.New(DirectoryCreator.ByteBuffers(), new ArticlePopulator());
	}


	// ── wildcard ──────────────────────────────────────────────────────────────

	@Test
	void wildcardSuffixQuery()
	{
		final GigaMap<Article> map = GigaMap.New();
		try(final LuceneIndex<Article> idx = map.index().register(LuceneIndex.Category(standardContext())))
		{
			map.add(new Article("eclipse store", "x"));
			map.add(new Article("java runtime",  "x"));

			final List<Article> hits = idx.query("title:ecl*");

			assertEquals(1, hits.size());
			assertEquals("eclipse store", hits.get(0).title);
		}
	}

	@Test
	void wildcardLeadingQuery()
	{
		final GigaMap<Article> map = GigaMap.New();
		try(final LuceneIndex<Article> idx = map.index().register(LuceneIndex.Category(standardContext())))
		{
			map.add(new Article("eclipse", "x"));
			map.add(new Article("java",    "x"));

			// Leading wildcards are enabled via setAllowLeadingWildcard(true) in LuceneIndex
			final List<Article> hits = idx.query("title:*lipse");

			assertEquals(1, hits.size());
			assertEquals("eclipse", hits.get(0).title);
		}
	}


	// ── phrase ────────────────────────────────────────────────────────────────

	@Test
	void phraseQuery()
	{
		final GigaMap<Article> map = GigaMap.New();
		try(final LuceneIndex<Article> idx = map.index().register(LuceneIndex.Category(standardContext())))
		{
			map.add(new Article("T1", "eclipse store gigamap"));   // adjacent — matches
			map.add(new Article("T2", "eclipse foundation store")); // non-adjacent — no match
			map.add(new Article("T3", "java runtime"));             // unrelated

			final List<Article> hits = idx.query("content:\"eclipse store\"");

			assertEquals(1, hits.size());
			assertEquals("T1", hits.get(0).title);
		}
	}


	// ── boolean ───────────────────────────────────────────────────────────────

	@Test
	void booleanOrQuery()
	{
		final GigaMap<Article> map = GigaMap.New();
		try(final LuceneIndex<Article> idx = map.index().register(LuceneIndex.Category(standardContext())))
		{
			map.add(new Article("alpha", "x"));
			map.add(new Article("beta",  "x"));
			map.add(new Article("gamma", "x"));

			final Query orQuery = new BooleanQuery.Builder()
				.add(new TermQuery(new Term("title", "alpha")), BooleanClause.Occur.SHOULD)
				.add(new TermQuery(new Term("title", "beta")),  BooleanClause.Occur.SHOULD)
				.build();

			final List<Article> hits = idx.query(orQuery);
			assertEquals(2, hits.size());
		}
	}

	@Test
	void booleanMustNotQuery()
	{
		final GigaMap<Article> map = GigaMap.New();
		try(final LuceneIndex<Article> idx = map.index().register(LuceneIndex.Category(standardContext())))
		{
			map.add(new Article("eclipse java",  "x")); // has both "eclipse" and "java"
			map.add(new Article("eclipse store", "x")); // has "eclipse", NOT "java"

			final Query query = new BooleanQuery.Builder()
				.add(new TermQuery(new Term("title", "eclipse")), BooleanClause.Occur.MUST)
				.add(new TermQuery(new Term("title", "java")),    BooleanClause.Occur.MUST_NOT)
				.build();

			final List<Article> hits = idx.query(query);
			assertEquals(1, hits.size());
			assertEquals("eclipse store", hits.get(0).title);
		}
	}


	// ── fuzzy ─────────────────────────────────────────────────────────────────

	@Test
	void fuzzyQuery()
	{
		final GigaMap<Article> map = GigaMap.New();
		try(final LuceneIndex<Article> idx = map.index().register(LuceneIndex.Category(standardContext())))
		{
			map.add(new Article("eclipse", "x"));
			map.add(new Article("java",    "x"));

			// "ecllipse" has Levenshtein distance 1 from "eclipse" (one extra 'l')
			final List<Article> hits = idx.query("title:ecllipse~1");

			assertEquals(1, hits.size());
			assertEquals("eclipse", hits.get(0).title);
		}
	}


	// ── score ordering ────────────────────────────────────────────────────────

	@Test
	void scoreOrderingByTermFrequency()
	{
		final GigaMap<Article> map = GigaMap.New();
		try(final LuceneIndex<Article> idx = map.index().register(LuceneIndex.Category(standardContext())))
		{
			final long idHigh = map.add(new Article("eclipse eclipse eclipse", "x"));
			final long idLow  = map.add(new Article("eclipse",                 "x"));

			final LuceneSearchResult<Article> result = idx.search("title:eclipse", 10);
			final var entries = result.toList();

			assertEquals(2, entries.size());
			assertTrue(
				entries.get(0).score() >= entries.get(1).score(),
				"Entry with higher term frequency must score at least as high"
			);
			assertEquals(idHigh, entries.get(0).entityId(),
				"Higher-frequency entry must appear first in score order");
		}
	}


	// ── custom AnalyzerCreator ────────────────────────────────────────────────

	@Test
	void customAnalyzerCreatorAffectsTokenization()
	{
		// StandardAnalyzer with "eclipse" as a custom stop word.
		// The term "eclipse" is stripped at both index and query time.
		final class CustomStopWordCreator extends AnalyzerCreator
		{
			@Override
			public Analyzer createAnalyzer()
			{
				return new StandardAnalyzer(new CharArraySet(List.of("eclipse"), false));
			}
		}

		final LuceneContext<Article> ctx = LuceneContext.New(
			DirectoryCreator.ByteBuffers(),
			new CustomStopWordCreator(),
			new ArticlePopulator()
		);

		final GigaMap<Article> map = GigaMap.New();
		try(final LuceneIndex<Article> idx = map.index().register(LuceneIndex.Category(ctx)))
		{
			map.add(new Article("eclipse store", "x"));

			assertTrue(idx.query("title:eclipse").isEmpty(),
				"Custom stop word 'eclipse' must not be indexed");
			assertEquals(1, idx.query("title:store").size(),
				"Non-stop-word term 'store' must still be indexed");
		}
	}


	// ── custom DirectoryCreator ───────────────────────────────────────────────

	@Test
	void customDirectoryCreatorIsInvoked()
	{
		final boolean[] invoked = {false};

		final DirectoryCreator trackingCreator = new DirectoryCreator()
		{
			@Override
			public Directory createDirectory()
			{
				invoked[0] = true;
				return new ByteBuffersDirectory();
			}
		};

		final LuceneContext<Article> ctx = LuceneContext.New(trackingCreator, new ArticlePopulator());

		final GigaMap<Article> map = GigaMap.New();
		try(final LuceneIndex<Article> idx = map.index().register(LuceneIndex.Category(ctx)))
		{
			map.add(new Article("test", "custom directory"));
			assertTrue(invoked[0], "Custom DirectoryCreator must be invoked on first index access");
		}
	}


	// ── numeric range ─────────────────────────────────────────────────────────

	@Test
	void numericRangeQuery()
	{
		// content field is (ab)used to carry the year as a string for test simplicity
		final class YearPopulator extends DocumentPopulator<Article>
		{
			@Override
			public void populate(final Document document, final Article entity)
			{
				document.add(createTextField("title", entity.title));
				document.add(createIntField("year",   Integer.parseInt(entity.content)));
			}
		}

		final LuceneContext<Article> ctx = LuceneContext.New(
			DirectoryCreator.ByteBuffers(),
			new YearPopulator()
		);

		final GigaMap<Article> map = GigaMap.New();
		try(final LuceneIndex<Article> idx = map.index().register(LuceneIndex.Category(ctx)))
		{
			map.add(new Article("alpha", "2020"));
			map.add(new Article("beta",  "2021"));
			map.add(new Article("gamma", "2022"));
			map.add(new Article("delta", "2023"));

			final Query rangeQuery = IntPoint.newRangeQuery("year", 2021, 2022);
			final List<Article> hits = idx.query(rangeQuery);

			assertEquals(2, hits.size());
		}
	}
}
