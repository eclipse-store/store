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
import org.eclipse.store.gigamap.types.GigaMap;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the lifecycle of a {@link LuceneIndex} when used through {@link GigaMap}:
 * close/reopen semantics, update propagation, manual commit, and concurrent access.
 */
public class LuceneLifecycleTest
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

	private static class ManualCommitContext extends LuceneContext.Default<Article>
	{
		ManualCommitContext()
		{
			super(DirectoryCreator.ByteBuffers(), AnalyzerCreator.Standard(), new ArticlePopulator());
		}

		@Override
		public boolean autoCommit()
		{
			return false;
		}
	}


	// ── close / reopen ────────────────────────────────────────────────────────

	@Test
	void closeAndReopenGraphDirectoryRetainsData()
	{
		// null directoryCreator → GraphDirectory stores index data in fileEntries inside GigaMap.
		// After close(), the fileEntries map is still in memory; lazyInit re-uses it.
		final LuceneContext<Article> ctx = LuceneContext.New(new ArticlePopulator());

		final GigaMap<Article> map = GigaMap.New();
		final LuceneIndex<Article> idx = map.index().register(LuceneIndex.Category(ctx));

		map.add(new Article("eclipse", "persistent"));
		assertEquals(1, idx.query("title:eclipse").size());

		idx.close();

		assertEquals(1, idx.query("title:eclipse").size(),
			"GraphDirectory index data must survive close/reopen without EmbeddedStorage");

		idx.close();
	}


	// ── update propagation ────────────────────────────────────────────────────

	@Test
	void setReplacesDocumentInIndex()
	{
		final GigaMap<Article> map = GigaMap.New();
		try(final LuceneIndex<Article> idx = map.index().register(LuceneIndex.Category(standardContext())))
		{
			final long id = map.add(new Article("original title", "content"));

			assertEquals(1, idx.query("title:original").size());
			assertEquals(0, idx.query("title:replaced").size());

			map.set(id, new Article("replaced title", "content"));

			assertEquals(0, idx.query("title:original").size(),
				"Old document must be removed from Lucene index after set()");
			assertEquals(1, idx.query("title:replaced").size(),
				"New document must be visible in Lucene index after set()");
		}
	}


	// ── manual commit ─────────────────────────────────────────────────────────

	@Test
	void manualCommitWorksCorrectly()
	{
		final GigaMap<Article> map = GigaMap.New();
		try(final LuceneIndex<Article> idx = map.index().register(
			LuceneIndex.Category(new ManualCommitContext())))
		{
			map.add(new Article("eclipse", "manual commit test"));

			// NRT reader sees uncommitted changes immediately
			assertEquals(1, idx.query("title:eclipse").size(),
				"NRT reader must see changes before explicit commit");

			// Explicit commit must work without errors and keep data visible
			assertDoesNotThrow(idx::commit);
			assertEquals(1, idx.query("title:eclipse").size(),
				"Data must still be visible after explicit commit");
		}
	}


	// ── large corpus ──────────────────────────────────────────────────────────

	@Test
	void largeCorpus1000Docs()
	{
		final GigaMap<Article> map = GigaMap.New();
		try(final LuceneIndex<Article> idx = map.index().register(LuceneIndex.Category(standardContext())))
		{
			for(int i = 0; i < 1000; i++)
			{
				final String category = i % 2 == 0 ? "even" : "odd";
				map.add(new Article(category, "doc " + i));
			}

			final List<Article> evens = idx.query("title:even", 1000);
			final List<Article> odds  = idx.query("title:odd",  1000);

			assertEquals(500, evens.size(), "Expected 500 even-titled docs");
			assertEquals(500, odds.size(),  "Expected 500 odd-titled docs");
		}
	}


	// ── concurrent add + search ───────────────────────────────────────────────

	@Test
	void concurrentAddAndSearch() throws InterruptedException
	{
		final GigaMap<Article> map = GigaMap.New();
		try(final LuceneIndex<Article> idx = map.index().register(LuceneIndex.Category(standardContext())))
		{
			final int WRITE_THREADS   = 4;
			final int ADDS_PER_THREAD = 50;
			final int READ_THREADS    = 2;
			final int READS_PER_READER = 20;

			final CountDownLatch writeLatch = new CountDownLatch(WRITE_THREADS);
			final CountDownLatch readLatch  = new CountDownLatch(READ_THREADS);
			final AtomicInteger  errors     = new AtomicInteger();

			for(int t = 0; t < WRITE_THREADS; t++)
			{
				final int threadId = t;
				new Thread(() ->
				{
					try
					{
						for(int i = 0; i < ADDS_PER_THREAD; i++)
						{
							map.add(new Article("concurrent", "thread " + threadId + " item " + i));
						}
					}
					catch(final Exception e)
					{
						errors.incrementAndGet();
					}
					finally
					{
						writeLatch.countDown();
					}
				}).start();
			}

			for(int r = 0; r < READ_THREADS; r++)
			{
				new Thread(() ->
				{
					try
					{
						for(int i = 0; i < READS_PER_READER; i++)
						{
							idx.query("title:concurrent");
						}
					}
					catch(final Exception e)
					{
						errors.incrementAndGet();
					}
					finally
					{
						readLatch.countDown();
					}
				}).start();
			}

			assertTrue(writeLatch.await(30, TimeUnit.SECONDS), "Writers did not finish in time");
			assertTrue(readLatch.await(30, TimeUnit.SECONDS),  "Readers did not finish in time");

			assertEquals(0, errors.get(), "No concurrent errors expected");
			assertEquals(WRITE_THREADS * ADDS_PER_THREAD, (int) map.size());

			final List<Article> finalHits = idx.query("title:concurrent", WRITE_THREADS * ADDS_PER_THREAD);
			assertEquals(WRITE_THREADS * ADDS_PER_THREAD, finalHits.size(),
				"All added entities must be findable after concurrent writes complete");
		}
	}
}
