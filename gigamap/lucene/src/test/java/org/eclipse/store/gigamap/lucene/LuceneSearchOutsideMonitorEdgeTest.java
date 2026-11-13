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

import static org.eclipse.store.gigamap.lucene.LuceneSearchOutsideMonitorTest.TIMEOUT_SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.eclipse.store.gigamap.lucene.LuceneSearchOutsideMonitorTest.Article;
import org.eclipse.store.gigamap.lucene.LuceneSearchOutsideMonitorTest.HeldQuery;
import org.eclipse.store.gigamap.lucene.LuceneSearchOutsideMonitorTest.TitlePopulator;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.ScoredSearchResult;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.NIOFSDirectory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A search held outside the map's monitor while the index is closed on file directories (MMap, NIO),
 * reindexed, fails to reindex, or is committed and stored into a storage that is then restarted; and searches
 * and acceptor queries whose hits change meanwhile. The held search must finish on the reader it took and
 * release it, its result and the acceptor's entities must match the index as it is when the search returns,
 * and the index must stay complete.
 */
class LuceneSearchOutsideMonitorEdgeTest
{
	// the number of populations that still succeed before one fails, -1 for none failing
	static final AtomicInteger POPULATIONS_BEFORE_FAILURE = new AtomicInteger(-1);

	static final class FailingTitlePopulator extends DocumentPopulator<Article>
	{
		@Override
		public void populate(final Document document, final Article entity)
		{
			if(POPULATIONS_BEFORE_FAILURE.get() >= 0 && POPULATIONS_BEFORE_FAILURE.getAndDecrement() == 0)
			{
				throw new IllegalStateException("populate failed");
			}
			document.add(createTextField("title", entity.title));
		}
	}

	// a plain NIO file directory, which the index has no factory for
	static final class NioCreator extends DirectoryCreator
	{
		final String path;

		NioCreator(final Path path)
		{
			this.path = path.toString();
		}

		@Override
		public Directory createDirectory()
		{
			try
			{
				return new NIOFSDirectory(Path.of(this.path));
			}
			catch(final IOException e)
			{
				throw new UncheckedIOException(e);
			}
		}
	}

	final ExecutorService executor = Executors.newCachedThreadPool();

	@AfterEach
	void shutdownExecutor()
	{
		this.executor.shutdownNow();
		POPULATIONS_BEFORE_FAILURE.set(-1);
	}

	static <T> T get(final Future<T> future) throws Exception
	{
		return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
	}

	static List<Long> ids(final ScoredSearchResult<Article> result)
	{
		final List<Long> ids = new ArrayList<>();
		for(final ScoredSearchResult.Entry<Article> entry : result)
		{
			ids.add(entry.entityId());
		}
		ids.sort(null);
		return ids;
	}

	@Test
	void aSearchFinishesAfterAnMMapIndexWasRefreshedAndClosed(@TempDir final Path dir) throws Exception
	{
		this.searchAcrossRefreshAndClose(LuceneContext.New(DirectoryCreator.MMap(dir), new TitlePopulator()));
	}

	@Test
	void aSearchFinishesAfterAnNioIndexWasRefreshedAndClosed(@TempDir final Path dir) throws Exception
	{
		this.searchAcrossRefreshAndClose(LuceneContext.New(new NioCreator(dir), new TitlePopulator()));
	}

	private void searchAcrossRefreshAndClose(final LuceneContext<Article> context) throws Exception
	{
		final GigaMap<Article>     map   = GigaMap.New();
		final LuceneIndex<Article> index = map.index().register(LuceneIndex.Category(context));
		try
		{
			final long first  = map.add(new Article("alpha one"));
			final long second = map.add(new Article("alpha two"));

			final HeldQuery                           held   = new HeldQuery("alpha");
			final Future<LuceneSearchResult<Article>> search = this.executor.submit(() -> index.search(held, 10));
			held.awaitEntered();
			final DirectoryReader heldReader = ((LuceneIndex.Default<Article>)index).reader;
			assertEquals(2, heldReader.getRefCount(), "held by the index and the search");

			// the removal and the search after it make the index give up the held search's reader, the close
			// closes writer, reader and the file directory
			map.removeById(first);
			assertEquals(List.of(second), ids(index.search("title:alpha", 10)));
			index.close();
			assertEquals(1, heldReader.getRefCount(), "only the held search still holds its reader");

			held.released.countDown();
			// the held search finished on its reader, then ran again on the index opened from its files
			assertEquals(List.of(second), ids(get(search)), "the hits of the index as it is when the search returns");
			assertEquals(0, heldReader.getRefCount(), "the finished search released its reader, which is closed");

			assertEquals(List.of(second), ids(index.search("title:alpha", 10)));
		}
		finally
		{
			index.close();
		}
	}

	@Test
	void aReindexDoesNotWaitForASearchAndTheSearchKeepsItsReader() throws Exception
	{
		final GigaMap<Article> map = GigaMap.New();
		try(final LuceneIndex<Article> index = map.index().register(LuceneIndex.Category(
			LuceneContext.New(DirectoryCreator.ByteBuffers(), new TitlePopulator())
		)))
		{
			final long first  = map.add(new Article("alpha one"));
			final long second = map.add(new Article("alpha two"));

			final HeldQuery                           held   = new HeldQuery("alpha");
			final Future<LuceneSearchResult<Article>> search = this.executor.submit(() -> index.search(held, 10));
			held.awaitEntered();

			final DirectoryReader heldReader = ((LuceneIndex.Default<Article>)index).reader;

			get(this.executor.submit(map::reindex));
			final long third = map.add(new Article("alpha three"));
			assertEquals(List.of(first, second, third), ids(index.search("title:alpha", 10)));
			assertEquals(1, heldReader.getRefCount(), "only the held search still holds its reader");

			held.released.countDown();
			assertEquals(List.of(first, second, third), ids(get(search)));
			assertEquals(0, heldReader.getRefCount(), "the finished search released its reader, which is closed");
		}
	}

	/*
	 * A search() result holds for the index as it is when the search returns, like the acceptor's hits: a hit
	 * whose entity was replaced while the search ran outside the monitor no longer matches.
	 */
	@Test
	void aSearchReturnsNoHitWhoseEntityWasReplacedDuringTheSearch() throws Exception
	{
		final GigaMap<Article> map = GigaMap.New();
		try(final LuceneIndex<Article> index = map.index().register(LuceneIndex.Category(
			LuceneContext.New(DirectoryCreator.ByteBuffers(), new TitlePopulator())
		)))
		{
			final long first  = map.add(new Article("alpha one"));
			final long second = map.add(new Article("alpha two"));

			final HeldQuery                           held   = new HeldQuery("alpha");
			final Future<LuceneSearchResult<Article>> search = this.executor.submit(() -> index.search(held, 10));
			held.awaitEntered();

			get(this.executor.submit(() -> map.set(first, new Article("beta one"))));

			held.released.countDown();
			assertEquals(List.of(second + ":alpha two"), entries(get(search)));
		}
	}

	/*
	 * removeAll() lets the next adds reuse the ids, so a hit's id from before can stand for a new, unrelated
	 * entity: the search() result must not contain it.
	 */
	@Test
	void aSearchReturnsNoHitWhoseIdWasReused() throws Exception
	{
		final GigaMap<Article> map = GigaMap.New();
		try(final LuceneIndex<Article> index = map.index().register(LuceneIndex.Category(
			LuceneContext.New(DirectoryCreator.ByteBuffers(), new TitlePopulator())
		)))
		{
			map.add(new Article("alpha one"));
			map.add(new Article("alpha two"));

			final HeldQuery                           held   = new HeldQuery("alpha");
			final Future<LuceneSearchResult<Article>> search = this.executor.submit(() -> index.search(held, 10));
			held.awaitEntered();

			final long reused = get(this.executor.submit(() ->
			{
				map.removeAll();
				map.add(new Article("beta reused"));
				return map.add(new Article("alpha new"));
			}));

			held.released.countDown();
			assertEquals(List.of(reused + ":alpha new"), entries(get(search)));
		}
	}

	// "id:title" of each entry, in the order of the result
	static List<String> entries(final ScoredSearchResult<Article> result)
	{
		final List<String> entries = new ArrayList<>();
		for(final ScoredSearchResult.Entry<Article> entry : result)
		{
			entries.add(entry.entityId() + ":" + entry.entity().title);
		}
		return entries;
	}

	/*
	 * A reindex that fails during the back-fill, after it dropped the documents, discards the writer and the
	 * reader of the index. The held search keeps its reader until it finished, then runs again, which rebuilds
	 * the index.
	 */
	@Test
	void aFailedReindexDoesNotWaitForASearchAndTheSearchKeepsItsReader() throws Exception
	{
		final GigaMap<Article> map = GigaMap.New();
		try(final LuceneIndex<Article> index = map.index().register(LuceneIndex.Category(
			LuceneContext.New(DirectoryCreator.ByteBuffers(), new FailingTitlePopulator())
		)))
		{
			final long first  = map.add(new Article("alpha one"));
			final long second = map.add(new Article("alpha two"));

			final HeldQuery                           held   = new HeldQuery("alpha");
			final Future<LuceneSearchResult<Article>> search = this.executor.submit(() -> index.search(held, 10));
			held.awaitEntered();
			// the reader the held search took: the index's own reference and the search's
			final DirectoryReader heldReader = ((LuceneIndex.Default<Article>)index).reader;
			assertEquals(2, heldReader.getRefCount());

			// both entities pass the check before the documents are dropped, the first one of the back-fill fails
			POPULATIONS_BEFORE_FAILURE.set(2);
			final Future<?>          reindex = this.executor.submit(map::reindex);
			final ExecutionException failure = assertThrows(ExecutionException.class, () -> get(reindex));
			assertNotNull(failure.getCause());
			assertEquals(-1, POPULATIONS_BEFORE_FAILURE.get(), "the back-fill population failed");
			assertNull(((LuceneIndex.Default<Article>)index).reader, "the failed reindex discarded the reader");
			assertEquals(1, heldReader.getRefCount(), "only the held search still holds the reader");

			held.released.countDown();
			assertEquals(List.of(first, second), ids(get(search)), "the held search ran again on the rebuilt index");
			assertEquals(0, heldReader.getRefCount(), "the finished search released the reader, which is closed");
			assertNotNull(((LuceneIndex.Default<Article>)index).reader, "the index is open again");

			assertEquals(List.of(first, second), ids(index.search("title:alpha", 10)));
		}
	}

	/*
	 * The acceptor must only get entities that match the query: a hit whose entity was replaced while the
	 * search ran outside the monitor no longer matches.
	 */
	@Test
	void theAcceptorGetsNoEntityThatWasReplacedDuringTheSearch() throws Exception
	{
		final GigaMap<Article> map = GigaMap.New();
		try(final LuceneIndex<Article> index = map.index().register(LuceneIndex.Category(
			LuceneContext.New(DirectoryCreator.ByteBuffers(), new TitlePopulator())
		)))
		{
			final long first  = map.add(new Article("alpha one"));
			final long second = map.add(new Article("alpha two"));

			final HeldQuery      held     = new HeldQuery("alpha");
			final List<String>   accepted = new ArrayList<>();
			final Future<Object> query    = this.executor.submit(() -> index.query(held, 10, (id, entity, score) ->
				accepted.add(id + ":" + entity.title)
			));
			held.awaitEntered();

			get(this.executor.submit(() -> map.set(first, new Article("beta one"))));

			held.released.countDown();
			get(query);
			assertEquals(List.of(second + ":alpha two"), accepted);
		}
	}

	/*
	 * removeAll() lets the next adds reuse the ids, so an old hit's id can stand for a new, unrelated entity.
	 */
	@Test
	void theAcceptorGetsNoEntityThatReusedTheIdOfAHit() throws Exception
	{
		final GigaMap<Article> map = GigaMap.New();
		try(final LuceneIndex<Article> index = map.index().register(LuceneIndex.Category(
			LuceneContext.New(DirectoryCreator.ByteBuffers(), new TitlePopulator())
		)))
		{
			map.add(new Article("alpha one"));
			map.add(new Article("alpha two"));

			final HeldQuery      held     = new HeldQuery("alpha");
			final List<String>   accepted = new ArrayList<>();
			final Future<Object> query    = this.executor.submit(() -> index.query(held, 10, (id, entity, score) ->
				accepted.add(entity.title)
			));
			held.awaitEntered();

			get(this.executor.submit(() ->
			{
				map.removeAll();
				map.add(new Article("beta reused"));
				map.add(new Article("alpha new"));
				return null;
			}));

			held.released.countDown();
			get(query);
			assertEquals(List.of("alpha new"), accepted);
		}
	}

	/*
	 * Graph directory with manual commits: the store during the held search commits the index and stores its
	 * files while the held search still reads the superseded segments. Releasing the search lets the writer drop
	 * those, under the map's monitor. After a restart the index must hold exactly the stored state.
	 */
	@Test
	void aStoreDuringASearchAndARestartKeepTheIndexComplete(@TempDir final Path dir) throws Exception
	{
		final Path storage = dir.resolve("storage");
		final long first;
		final long second;
		final long third;
		try(final EmbeddedStorageManager manager = EmbeddedStorage.start(storage))
		{
			final GigaMap<Article> map = GigaMap.New();
			manager.setRoot(map);
			final LuceneIndex<Article> index = map.index().register(LuceneIndex.Category(
				LuceneContext.New(null, AnalyzerCreator.Standard(), new TitlePopulator(), false)
			));
			first  = map.add(new Article("alpha one"));
			second = map.add(new Article("alpha two"));
			manager.storeRoot();

			final HeldQuery                           held   = new HeldQuery("alpha");
			final Future<LuceneSearchResult<Article>> search = this.executor.submit(() -> index.search(held, 10));
			held.awaitEntered();
			final DirectoryReader heldReader = ((LuceneIndex.Default<Article>)index).reader;

			map.removeById(first);
			third = map.add(new Article("alpha three"));
			assertEquals(List.of(second, third), ids(index.search("title:alpha", 10)));
			// takes the map's monitor, so it would wait for a search that held it
			get(this.executor.submit(() -> map.store()));
			assertEquals(1, heldReader.getRefCount(), "only the held search still holds its reader");

			held.released.countDown();
			assertEquals(List.of(second, third), ids(get(search)));
			assertEquals(0, heldReader.getRefCount(), "the finished search released its reader, which is closed");

			// after the release: a further write and store on top of the dropped segments
			final long fourth = map.add(new Article("alpha four"));
			map.removeById(fourth);
			map.store();
		}

		try(final EmbeddedStorageManager manager = EmbeddedStorage.start(storage))
		{
			@SuppressWarnings("unchecked")
			final GigaMap<Article>     map   = (GigaMap<Article>)manager.root();
			@SuppressWarnings("unchecked")
			final LuceneIndex<Article> index = map.index().get(LuceneIndex.class);
			assertEquals(List.of(second, third), ids(index.search("title:alpha", 10)));
			assertEquals(List.of(third), ids(index.search("title:three", 10)));
		}
	}
}
