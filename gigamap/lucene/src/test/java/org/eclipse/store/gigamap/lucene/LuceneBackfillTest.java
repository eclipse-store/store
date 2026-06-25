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
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies that registering a {@link LuceneIndex} on a {@link GigaMap} that already contains
 * entities back-fills those pre-existing entities into the index (matching the bitmap and vector
 * index behavior), rather than only indexing entities added after registration.
 */
public class LuceneBackfillTest
{
	@TempDir
	Path storagePath;

	// ── back-fill basic ─────────────────────────────────────────────────────────

	@Test
	void existingEntitiesAreIndexedOnRegistration()
	{
		final GigaMap<Article> gigaMap = GigaMap.New();

		// entities added BEFORE the Lucene index exists
		gigaMap.add(new Article("Title_1", "eclipse store"));
		gigaMap.add(new Article("Title_2", "eclipse foundation"));
		gigaMap.add(new Article("Title_3", "java runtime"));

		try(final LuceneIndex<Article> luceneIndex = gigaMap.index().register(LuceneIndex.Category(standardContext())))
		{
			assertEquals(2, luceneIndex.query("content:eclipse").size(),
				"Pre-existing entities must be back-filled into a freshly registered Lucene index");
			assertEquals(1, luceneIndex.query("title:Title_3").size());
			assertEquals(3, gigaMap.size());
		}
	}

	// ── empty map (no regression, no empty commit) ──────────────────────────────

	@Test
	void registerOnEmptyMapThenAddStillIndexes()
	{
		final GigaMap<Article> gigaMap = GigaMap.New();

		try(final LuceneIndex<Article> luceneIndex = gigaMap.index().register(LuceneIndex.Category(standardContext())))
		{
			assertEquals(0, luceneIndex.query("content:eclipse").size());

			gigaMap.add(new Article("Title_1", "eclipse store"));

			assertEquals(1, luceneIndex.query("content:eclipse").size(),
				"Entities added after registration on an initially empty map must still be indexed");
		}
	}

	// ── id correctness: update / remove of a back-filled entity ─────────────────

	@Test
	void backfilledEntitiesCarryCorrectIdsForUpdateAndRemove()
	{
		final GigaMap<Article> gigaMap = GigaMap.New();

		final long id1 = gigaMap.add(new Article("original", "content one"));
		gigaMap.add(new Article("keep", "content two"));

		try(final LuceneIndex<Article> luceneIndex = gigaMap.index().register(LuceneIndex.Category(standardContext())))
		{
			assertEquals(1, luceneIndex.query("title:original").size());

			// update a back-filled entity: relies on ENTITY_ID_FIELD holding the real GigaMap id
			gigaMap.set(id1, new Article("replaced", "content one"));
			assertEquals(0, luceneIndex.query("title:original").size(),
				"Old document of a back-filled entity must be removed on update");
			assertEquals(1, luceneIndex.query("title:replaced").size(),
				"New document of a back-filled entity must be present after update");

			// remove a back-filled entity
			gigaMap.removeById(id1);
			assertEquals(0, luceneIndex.query("title:replaced").size(),
				"Document of a back-filled entity must be removed on removeById");
			assertEquals(1, luceneIndex.query("title:keep").size());
		}
	}

	// ── persistence round-trip: back-fill persists and is not re-indexed on reload ─

	@Test
	void backfillPersistsAndIsNotDoubleIndexedOnReload()
	{
		final GigaMap<Article> gigaMap = GigaMap.New();

		gigaMap.add(new Article("Title_1", "eclipse store"));
		gigaMap.add(new Article("Title_2", "eclipse foundation"));

		// in-graph index (null DirectoryCreator) so the back-filled data is stored with the GigaMap
		final LuceneIndex<Article> luceneIndex = gigaMap.index().register(LuceneIndex.Category(
			LuceneContext.New(null, AnalyzerCreator.Standard(), new ArticlePopulator())));

		assertEquals(2, luceneIndex.query("content:eclipse").size());

		try(final EmbeddedStorageManager storageManager = EmbeddedStorage.start(gigaMap, this.storagePath))
		{
			// store the back-filled index along with the map
		}
		luceneIndex.close();

		LuceneIndex<Article> reloaded = null;
		try(final EmbeddedStorageManager storageManager = EmbeddedStorage.start(GigaMap.<Article>New(), this.storagePath))
		{
			final GigaMap<Article> gigaMap2 = storageManager.root();
			reloaded = gigaMap2.index().get(LuceneIndex.class);

			assertEquals(2, reloaded.query("content:eclipse").size(),
				"Back-filled documents must survive a store/reload and must not be re-indexed (duplicated)");
			assertEquals(2, gigaMap2.size());
		}
		finally
		{
			if(reloaded != null)
			{
				reloaded.close();
			}
		}
	}

	// ── manual-commit context: back-fill must not force a commit ────────────────

	@Test
	void backfillWithManualCommitContext()
	{
		final GigaMap<Article> gigaMap = GigaMap.New();

		gigaMap.add(new Article("Title_1", "eclipse store"));
		gigaMap.add(new Article("Title_2", "eclipse foundation"));

		try(final LuceneIndex<Article> luceneIndex = gigaMap.index().register(
			LuceneIndex.Category(new ManualCommitContext())))
		{
			// the near-real-time reader sees the back-filled (uncommitted) documents immediately,
			// just like internalAdd under a manual-commit context
			assertEquals(2, luceneIndex.query("content:eclipse").size(),
				"Back-filled documents must be queryable under a manual-commit context");

			// an explicit commit must work and keep the data visible
			assertDoesNotThrow(luceneIndex::commit);
			assertEquals(2, luceneIndex.query("content:eclipse").size());
		}
	}

	// ── failed back-fill rolls back the registration ────────────────────────────

	@Test
	void failedBackfillRollsBackRegistration()
	{
		final GigaMap<Article> gigaMap = GigaMap.New();
		gigaMap.add(new Article("Title_1", "eclipse store"));

		// a populator that fails during back-fill of the pre-existing entity
		final LuceneContext<Article> failing = LuceneContext.New(
			DirectoryCreator.ByteBuffers(),
			new DocumentPopulator<Article>()
			{
				@Override
				public void populate(final Document document, final Article entity)
				{
					throw new IllegalStateException("boom");
				}
			});

		assertThrows(IllegalStateException.class,
			() -> gigaMap.index().register(LuceneIndex.Category(failing)));

		assertNull(gigaMap.index().get(LuceneIndex.class),
			"A back-fill failure must roll back the index-group registration");

		// the map remains usable: a subsequent successful registration back-fills normally
		try(final LuceneIndex<Article> luceneIndex = gigaMap.index().register(LuceneIndex.Category(standardContext())))
		{
			assertEquals(1, luceneIndex.query("content:eclipse").size());
		}
	}

	// ── fixtures ────────────────────────────────────────────────────────────────

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

	private static class ArticlePopulator extends DocumentPopulator<Article>
	{
		@Override
		public void populate(final Document document, final Article entity)
		{
			document.add(createTextField("title",   entity.getTitle()));
			document.add(createTextField("content", entity.getContent()));
		}
	}

	private static class Article
	{
		private final String title;
		private final String content;

		Article(final String title, final String content)
		{
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
