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
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.MMapDirectory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

/**
 * A {@link GigaMap#reindex()} whose {@link DocumentPopulator} keeps failing for one entity. The reindex populates
 * every entity's document before it drops anything, so it fails without changing the index: queries keep returning
 * the documents from before the call, and the map can still be changed and stored while the failure persists. The
 * readers of the documents it populated are closed, also those of the document whose population failed, and so are
 * those of an add, a set or an addAll whose population fails.
 */
class LuceneReindexPopulatorFailureTest
{
	private static final int    COUNT   = 20;
	private static final int    POISON  = 10;
	private static final String FAILURE = "simulated populator failure for entity " + POISON;
	// readers the populator opens per document: one in a reader-valued field, one behind a token-stream field
	private static final int    READERS = 2;

	@TempDir
	Path temp;

	@AfterEach
	void disarm()
	{
		PoisonPopulator.FAIL.set(false);
		PoisonPopulator.POISON_CALLS.set(0);
	}

	@Test
	void failingPopulatorLeavesTheIndexUsable()
	{
		final GigaMap<Article> map = populatedMap();
		try(final LuceneIndex<Article> index = map.index().register(LuceneIndex.Category(
			LuceneContext.New(DirectoryCreator.ByteBuffers(), new PoisonPopulator())
		)))
		{
			PoisonPopulator.FAIL.set(true);
			assertReindexFails(map);

			// still failing: every entity is found, the failing one by its document from before the reindex
			assertEquals(COUNT, index.query("kind:article").size(), "the failed reindex changed the index");
			assertEquals(1, index.query("word:w" + POISON).size());

			map.add(new Article(COUNT));
			map.set(3, new Article(3, "changed"));
			map.removeById(4);
			assertEquals(COUNT, index.query("kind:article").size());
			assertEquals(1, index.query("word:w" + COUNT).size(), "add");
			assertEquals(1, index.query("word:changed").size(), "set");
			assertEquals(0, index.query("word:w4").size(), "remove");

			PoisonPopulator.FAIL.set(false);
			map.reindex();
			assertEquals(COUNT, index.query("kind:article").size(), "after a reindex that succeeds");
			assertEquals(1, index.query("word:changed").size(), "after a reindex that succeeds");
			assertEquals(0, index.query("word:w4").size(), "after a reindex that succeeds");
		}
	}

	@Test
	void manualCommitKeepsUncommittedChangesAndStoresWhileTheFailurePersists() throws IOException
	{
		final Path storage = this.temp.resolve("storage");
		final Path lucene  = this.temp.resolve("lucene");
		try(final EmbeddedStorageManager manager = EmbeddedStorage.start(storage))
		{
			final GigaMap<Article> map = populatedMap();
			manager.setRoot(map);
			final LuceneIndex<Article> index = map.index().register(LuceneIndex.Category(LuceneContext.New(
				DirectoryCreator.MMap(lucene),
				AnalyzerCreator.Standard(),
				new PoisonPopulator(),
				false
			)));
			manager.storeRoot();
			map.add(new Article(COUNT)); // not committed

			PoisonPopulator.FAIL.set(true);
			assertReindexFails(map);
			assertEquals(COUNT + 1, index.query("kind:article").size(), "the uncommitted change was lost");

			// still failing: unrelated changes, not committed either
			map.add(new Article(COUNT + 1));
			map.set(3, new Article(3, "changed"));
			assertEquals(COUNT + 2, index.query("kind:article").size(), "add");
			assertEquals(1, index.query("word:changed").size(), "set");

			final int callsBeforeStore = PoisonPopulator.POISON_CALLS.get();
			manager.storeRoot();
			assertEquals(callsBeforeStore, PoisonPopulator.POISON_CALLS.get(),
				"the store populated the failing entity again");
			assertEquals(COUNT + 2, committedDocuments(lucene), "the store did not commit the changes");
		}

		// still failing: the restarted index must serve its commit without populating anything
		final int callsBeforeRestart = PoisonPopulator.POISON_CALLS.get();
		try(final EmbeddedStorageManager manager = EmbeddedStorage.start(storage))
		{
			final GigaMap<Article>     map   = manager.root();
			final LuceneIndex<Article> index = map.index().get(LuceneIndex.class);
			assertEquals(COUNT + 2, index.query("kind:article").size(), "after a restart");
			assertEquals(1, index.query("word:w" + COUNT).size(), "after a restart");
			assertEquals(1, index.query("word:w" + (COUNT + 1)).size(), "after a restart");
			assertEquals(1, index.query("word:changed").size(), "after a restart");
			assertEquals(callsBeforeRestart, PoisonPopulator.POISON_CALLS.get(),
				"the restarted index populated the failing entity again");
		}
	}

	/**
	 * The populator opens readers for every document, the failing one included, before it throws. The documents the
	 * reindex populates without indexing them are never handed to Lucene, which would close their readers, so the
	 * reindex has to close them, also those of the document whose population failed, and also on a reindex that
	 * succeeds.
	 */
	@Test
	void failedReindexClosesTheReadersOfTheDocumentsItPopulated()
	{
		final GigaMap<Article> map = populatedMap();
		try(final LuceneIndex<Article> index = map.index().register(LuceneIndex.Category(
			LuceneContext.New(DirectoryCreator.ByteBuffers(), new PoisonPopulator())
		)))
		{
			final int opened = TrackedReader.OPENED.get();
			final int closed = TrackedReader.CLOSED.get();

			PoisonPopulator.FAIL.set(true);
			assertReindexFails(map);
			assertReindexFails(map);
			// the entities are populated in id order, up to and including the failing one
			assertEquals(2 * (POISON + 1) * READERS, TrackedReader.OPENED.get() - opened,
				"precondition: readers opened by the two reindexes");
			assertEquals(TrackedReader.OPENED.get() - opened, TrackedReader.CLOSED.get() - closed,
				"readers left open or closed twice by the failed reindexes");

			PoisonPopulator.FAIL.set(false);
			map.reindex();
			assertEquals(TrackedReader.OPENED.get() - opened, TrackedReader.CLOSED.get() - closed,
				"readers left open or closed twice by the reindex that succeeded");
			assertEquals(COUNT, index.query("kind:article").size());
		}
	}

	/**
	 * The same for the other operations that populate documents: an add, a set and an addAll whose population fails
	 * close the readers of the documents they built, in the case of addAll also those built before the failing one.
	 */
	@Test
	void failedAddSetAndAddAllCloseTheReadersOfTheDocumentsTheyPopulated()
	{
		final GigaMap<Article> map = populatedMap();
		try(final LuceneIndex<Article> index = map.index().register(LuceneIndex.Category(
			LuceneContext.New(DirectoryCreator.ByteBuffers(), new PoisonPopulator())
		)))
		{
			final int opened = TrackedReader.OPENED.get();
			final int closed = TrackedReader.CLOSED.get();

			PoisonPopulator.FAIL.set(true);
			assertFails(() -> map.add(new Article(POISON)));
			assertFails(() -> map.set(3, new Article(POISON)));
			assertFails(() -> map.addAll(List.of(
				new Article(COUNT    ),
				new Article(COUNT + 1),
				new Article(POISON   ),
				new Article(COUNT + 2)
			)));
			// one document each for add and set, three for addAll, which fails on its third entity
			assertEquals(5 * READERS, TrackedReader.OPENED.get() - opened,
				"precondition: readers opened by the operations");
			assertEquals(TrackedReader.OPENED.get() - opened, TrackedReader.CLOSED.get() - closed,
				"readers left open or closed twice by the failed operations");
			// none of them took effect, in the map or in the index
			assertEquals(COUNT, map.size());
			assertEquals(COUNT, index.query("kind:article").size());
			assertEquals("w3", map.get(3).word, "the failed set changed the map");
			assertEquals(1, index.query("word:w3").size(), "the failed set changed the index");
		}
	}


	///////////////////////////////////////////////////////////////////////////
	// fixtures //
	/////////////

	private static void assertReindexFails(final GigaMap<Article> map)
	{
		assertFails(map::reindex);
	}

	private static void assertFails(final Executable operation)
	{
		assertEquals(FAILURE, assertThrows(IllegalStateException.class, operation).getMessage());
	}

	private static int committedDocuments(final Path lucene) throws IOException
	{
		try(final Directory       directory = new MMapDirectory(lucene);
			final DirectoryReader reader    = DirectoryReader.open(directory))
		{
			return reader.numDocs();
		}
	}

	private static GigaMap<Article> populatedMap()
	{
		final GigaMap<Article> map = GigaMap.New();
		for(int i = 0; i < COUNT; i++)
		{
			map.add(new Article(i));
		}
		return map;
	}

	static final class Article
	{
		final int    no  ;
		final String word;

		Article(final int no)
		{
			this(no, "w" + no);
		}

		Article(final int no, final String word)
		{
			super();
			this.no   = no;
			this.word = word;
		}
	}

	/**
	 * Adds two fields over a reader to every document, like a populator indexing file contents: a reader-valued field
	 * and a token-stream field whose tokenizer reads the reader. Throws for entity {@link #POISON} while armed, after
	 * those fields were added, counting those calls. Static state, because the populator is part of the stored context
	 * and is re-created by the load.
	 */
	static final class PoisonPopulator extends DocumentPopulator<Article>
	{
		static final AtomicBoolean FAIL         = new AtomicBoolean();
		static final AtomicInteger POISON_CALLS = new AtomicInteger();

		@Override
		public void populate(final Document document, final Article entity)
		{
			document.add(createStringField("kind", "article"  ));
			document.add(createStringField("word", entity.word));
			document.add(new TextField("body", new TrackedReader(entity.word)));
			final Tokenizer tokenizer = new StandardTokenizer();
			tokenizer.setReader(new TrackedReader(entity.word));
			document.add(new TextField("tokens", tokenizer));
			if(FAIL.get() && entity.no == POISON)
			{
				POISON_CALLS.incrementAndGet();
				throw new IllegalStateException(FAILURE);
			}
		}
	}

	/**
	 * Counts how many of its instances were opened, and every call of {@link #close()}: as many closes as readers
	 * opened means none was left open and none was closed twice.
	 */
	static final class TrackedReader extends StringReader
	{
		static final AtomicInteger OPENED = new AtomicInteger();
		static final AtomicInteger CLOSED = new AtomicInteger();

		TrackedReader(final String content)
		{
			super(content);
			OPENED.incrementAndGet();
		}

		@Override
		public void close()
		{
			super.close();
			CLOSED.incrementAndGet();
		}
	}
}
