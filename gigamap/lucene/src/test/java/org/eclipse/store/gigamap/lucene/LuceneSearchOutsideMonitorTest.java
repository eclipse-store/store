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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.ScoredSearchResult;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryVisitor;
import org.apache.lucene.search.TermQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * A search of a {@link LuceneIndex} holds the map's monitor only to parse the query and to take a searcher, not while
 * it searches and reads the entity ids of its hits: the map's other callers do not wait for a search, and a search
 * finishes on the reader it took, whatever is written, refreshed or closed meanwhile. Its result holds for the index
 * as it is when the search returns: after a write meanwhile, the search runs again under the monitor.
 * <p>
 * The searches of the tests are held inside Lucene, after they took their searcher, by a query that waits when it is
 * rewritten.
 */
public class LuceneSearchOutsideMonitorTest
{
	// only reached when a test fails: a passing test never waits for it
	static final long TIMEOUT_SECONDS = 10;

	static class Article
	{
		String title;

		Article(final String title)
		{
			this.title = title;
		}
	}

	static class TitlePopulator extends DocumentPopulator<Article>
	{
		@Override
		public void populate(
			final Document document,
			final Article  entity
		)
		{
			document.add(createTextField("title", entity.title));
		}
	}

	/*
	 * A query for a word of the title that tells when a search started on it, and holds that search until it is
	 * released. Lucene rewrites it once per search, so the count of rewrites tells whether a search ran again.
	 */
	static final class HeldQuery extends Query
	{
		final CountDownLatch entered  = new CountDownLatch(1);
		final CountDownLatch released = new CountDownLatch(1);
		final AtomicInteger  rewrites = new AtomicInteger();
		final Query          query   ;

		HeldQuery(final String word)
		{
			super();
			this.query = new TermQuery(new Term("title", word));
		}

		@Override
		public Query rewrite(final IndexSearcher searcher) throws IOException
		{
			this.rewrites.incrementAndGet();
			this.entered.countDown();
			try
			{
				if(!this.released.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
				{
					throw new IOException("The held search was never released.");
				}
			}
			catch(final InterruptedException e)
			{
				Thread.currentThread().interrupt();
				throw new IOException(e);
			}

			return this.query;
		}

		void awaitEntered() throws InterruptedException
		{
			assertTrue(this.entered.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "the search reached Lucene");
		}

		@Override
		public String toString(final String field)
		{
			return "held(" + this.query.toString(field) + ")";
		}

		@Override
		public void visit(final QueryVisitor visitor)
		{
			this.query.visit(visitor);
		}

		@Override
		public boolean equals(final Object other)
		{
			return this == other;
		}

		@Override
		public int hashCode()
		{
			return System.identityHashCode(this);
		}
	}

	private final ExecutorService executor = Executors.newCachedThreadPool();

	@AfterEach
	void shutdownExecutor()
	{
		this.executor.shutdownNow();
	}

	private static <T> T get(final Future<T> future) throws Exception
	{
		return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
	}

	private static List<Long> ids(final ScoredSearchResult<Article> result)
	{
		final List<Long> ids = new ArrayList<>();
		for(final ScoredSearchResult.Entry<Article> entry : result)
		{
			ids.add(entry.entityId());
		}
		ids.sort(null);

		return ids;
	}

	private static LuceneContext<Article> memoryContext()
	{
		return LuceneContext.New(DirectoryCreator.ByteBuffers(), new TitlePopulator());
	}


	@Test
	void theMapAndOtherSearchesDoNotWaitForASearch() throws Exception
	{
		final GigaMap<Article> map = GigaMap.New();
		try(final LuceneIndex<Article> index = map.index().register(LuceneIndex.Category(memoryContext())))
		{
			final long first  = map.add(new Article("alpha one"));
			final long second = map.add(new Article("alpha two"));

			final HeldQuery                           held   = new HeldQuery("alpha");
			final Future<LuceneSearchResult<Article>> search = this.executor.submit(() -> index.search(held, 10));
			held.awaitEntered();

			// each of these takes the map's monitor, which the held search would keep if it searched under it.
			assertEquals(2L, get(this.executor.submit(map::size)));
			assertEquals("alpha one", get(this.executor.submit(() -> map.get(first))).title);
			assertEquals(List.of(first, second), get(this.executor.submit(() -> ids(index.search("title:alpha", 10)))));
			final long third = get(this.executor.submit(() -> map.add(new Article("alpha three"))));
			assertEquals(
				List.of(first, second, third),
				get(this.executor.submit(() -> ids(index.search("title:alpha", 10)))),
				"a search started later finds the entity added meanwhile"
			);

			held.released.countDown();
			assertEquals(
				List.of(first, second, third),
				ids(get(search)),
				"the held search returns the hits of the index as it is when it returns"
			);
			assertEquals(2, held.rewrites.get(), "the add meanwhile made the held search run again");
		}
	}

	@Test
	void aSearchWithoutAWriteMeanwhileRunsOnce() throws Exception
	{
		final GigaMap<Article> map = GigaMap.New();
		try(final LuceneIndex<Article> index = map.index().register(LuceneIndex.Category(memoryContext())))
		{
			final long first  = map.add(new Article("alpha one"));
			final long second = map.add(new Article("alpha two"));

			final HeldQuery                           held   = new HeldQuery("alpha");
			final Future<LuceneSearchResult<Article>> search = this.executor.submit(() -> index.search(held, 10));
			held.awaitEntered();

			// reads only, and another search, which finds the reader up to date and keeps it
			assertEquals(2L, get(this.executor.submit(map::size)));
			assertEquals("alpha two", get(this.executor.submit(() -> map.get(second))).title);
			assertEquals(List.of(first, second), get(this.executor.submit(() -> ids(index.search("title:alpha", 10)))));

			held.released.countDown();
			assertEquals(List.of(first, second), ids(get(search)));
			assertEquals(1, held.rewrites.get(), "the search's hits were still exact, so it did not run again");

			final HeldQuery  heldQuery = new HeldQuery("alpha");
			final List<Long> accepted  = new ArrayList<>();
			heldQuery.released.countDown();
			index.query(heldQuery, 10, (id, entity, score) -> accepted.add(id));
			accepted.sort(null);
			assertEquals(List.of(first, second), accepted);
			assertEquals(1, heldQuery.rewrites.get(), "neither did the acceptor's search");
		}
	}

	@Test
	void aSearchFinishesAfterTheIndexWasRefreshedAndClosed() throws Exception
	{
		final GigaMap<Article>     map   = GigaMap.New();
		final LuceneIndex<Article> index = map.index().register(LuceneIndex.Category(memoryContext()));
		try
		{
			final long first  = map.add(new Article("alpha one"));
			final long second = map.add(new Article("alpha two"));

			final HeldQuery                           held   = new HeldQuery("alpha");
			final Future<LuceneSearchResult<Article>> search = this.executor.submit(() -> index.search(held, 10));
			held.awaitEntered();
			// the reader the held search took: the index's own reference and the search's
			final DirectoryReader heldReader = ((LuceneIndex.Default<Article>)index).reader;
			assertEquals(2, heldReader.getRefCount());

			// a removal and a search after it: the index reopens its reader and gives up the one of the held search.
			map.removeById(first);
			assertEquals(List.of(second), ids(index.search("title:alpha", 10)));
			// and a close, which closes writer, reader and directory.
			index.close();
			assertEquals(1, heldReader.getRefCount(), "only the held search still holds its reader");

			held.released.countDown();
			final LuceneSearchResult<Article> result = get(search);
			assertEquals(0, heldReader.getRefCount(), "the finished search released its reader, which is closed");
			// the search ran again on the reopened index, as the removal came after it took its reader
			assertEquals(List.of(second), ids(result), "the hits of the index as it is when the search returns");
			final List<String> titles = new ArrayList<>();
			for(final ScoredSearchResult.Entry<Article> entry : result)
			{
				final Article article = entry.entity();
				if(article != null)
				{
					titles.add(article.title);
				}
			}
			assertEquals(List.of("alpha two"), titles);

			// the index opens again and holds what the map holds.
			assertEquals(List.of(second), ids(index.search("title:alpha", 10)));
		}
		finally
		{
			index.close();
		}
	}

	@Test
	void theAcceptorGetsItsHitsUnderTheMapsMonitor() throws Exception
	{
		final GigaMap<Article> map = GigaMap.New();
		try(final LuceneIndex<Article> index = map.index().register(LuceneIndex.Category(memoryContext())))
		{
			final long first  = map.add(new Article("alpha one"));
			final long second = map.add(new Article("alpha two"));

			final HeldQuery       held     = new HeldQuery("alpha");
			final List<Long>      accepted = new ArrayList<>();
			final List<Boolean>   monitor  = new ArrayList<>();
			final Future<Object>  query    = this.executor.submit(() -> index.query(held, 10, (id, entity, score) ->
			{
				assertNotNull(entity);
				accepted.add(id);
				monitor.add(Thread.holdsLock(map));
			}));
			held.awaitEntered();

			// removed while the search runs: its hit is left out, as its entity is gone when the hits are passed.
			assertEquals("alpha one", get(this.executor.submit(() -> map.removeById(first))).title);

			held.released.countDown();
			get(query);
			assertEquals(List.of(second), accepted);
			assertEquals(List.of(true), monitor);
		}
	}

	@Test
	void aSearchOnAThreadHoldingTheMonitorStillWorks()
	{
		final GigaMap<Article> map = GigaMap.New();
		try(final LuceneIndex<Article> index = map.index().register(LuceneIndex.Category(memoryContext())))
		{
			final long first = map.add(new Article("alpha one"));
			synchronized(map)
			{
				map.add(new Article("beta"));
				assertEquals(List.of(first), ids(index.search("title:alpha", 10)));
			}
		}
	}
}
