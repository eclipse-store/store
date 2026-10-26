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
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.MMapDirectory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A {@link GigaMap#reindex()} whose {@link DocumentPopulator} keeps failing for one entity. The reindex populates
 * every entity's document before it drops anything, so it fails without changing the index: queries keep returning
 * the documents from before the call, and the map can still be changed and stored while the failure persists.
 */
class LuceneReindexPopulatorFailureTest
{
	private static final int    COUNT   = 20;
	private static final int    POISON  = 10;
	private static final String FAILURE = "simulated populator failure for entity " + POISON;

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

			final int callsBeforeStore = PoisonPopulator.POISON_CALLS.get();
			manager.storeRoot();
			assertEquals(callsBeforeStore, PoisonPopulator.POISON_CALLS.get(),
				"the store populated the failing entity again");
			assertEquals(COUNT + 1, committedDocuments(lucene), "the store did not commit the change");
		}

		// still failing: the restarted index must serve its commit without populating anything
		final int callsBeforeRestart = PoisonPopulator.POISON_CALLS.get();
		try(final EmbeddedStorageManager manager = EmbeddedStorage.start(storage))
		{
			final GigaMap<Article>     map   = manager.root();
			final LuceneIndex<Article> index = map.index().get(LuceneIndex.class);
			assertEquals(COUNT + 1, index.query("kind:article").size(), "after a restart");
			assertEquals(1, index.query("word:w" + COUNT).size(), "after a restart");
			assertEquals(callsBeforeRestart, PoisonPopulator.POISON_CALLS.get(),
				"the restarted index populated the failing entity again");
		}
	}


	///////////////////////////////////////////////////////////////////////////
	// fixtures //
	/////////////

	private static void assertReindexFails(final GigaMap<Article> map)
	{
		assertEquals(FAILURE, assertThrows(IllegalStateException.class, map::reindex).getMessage());
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
	 * Throws for entity {@link #POISON} while armed, counting those calls. Static state, because the populator is
	 * part of the stored context and is re-created by the load.
	 */
	static final class PoisonPopulator extends DocumentPopulator<Article>
	{
		static final AtomicBoolean FAIL         = new AtomicBoolean();
		static final AtomicInteger POISON_CALLS = new AtomicInteger();

		@Override
		public void populate(final Document document, final Article entity)
		{
			if(FAIL.get() && entity.no == POISON)
			{
				POISON_CALLS.incrementAndGet();
				throw new IllegalStateException(FAILURE);
			}
			document.add(createStringField("kind", "article"  ));
			document.add(createStringField("word", entity.word));
		}
	}
}
