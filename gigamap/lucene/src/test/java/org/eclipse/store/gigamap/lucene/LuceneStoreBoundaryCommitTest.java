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
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.MMapDirectory;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies that with {@link LuceneContext#autoCommit()} == {@code false} the Lucene commit is
 * coupled to the {@link GigaMap#store()} boundary instead of happening eagerly at mutation time
 * (issue #715).
 */
public class LuceneStoreBoundaryCommitTest
{
	@TempDir
	Path lucenePath;

	@TempDir
	Path storagePath;

	// ── entity ────────────────────────────────────────────────────────────────

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

	private static long committedDocsOnDisk(final Path path) throws Exception
	{
		// read-only reader over the last on-disk commit; does not need the writer lock and is
		// independent of the live near-real-time reader, so it reflects exactly what store()
		// (or an explicit commit()) flushed to disk.
		try(final Directory dir = new MMapDirectory(path);
		    final DirectoryReader reader = DirectoryReader.open(dir))
		{
			return reader.numDocs();
		}
	}


	// ── external (MMap) directory ───────────────────────────────────────────────

	@Test
	void mmapCommitIsCoupledToStore() throws Exception
	{
		final LuceneContext<Article> ctx = LuceneContext.New(
			DirectoryCreator.MMap(this.lucenePath),
			AnalyzerCreator.Standard(),
			new ArticlePopulator(),
			false // manual / store-coupled commit
		);

		final GigaMap<Article> map = GigaMap.New();
		final LuceneIndex<Article> idx = map.index().register(LuceneIndex.Category(ctx));

		try(final EmbeddedStorageManager sm = EmbeddedStorage.start(map, this.storagePath))
		{
			map.add(new Article("eclipse", "first"));

			// near-real-time reader sees the change, but nothing is committed to disk yet
			assertEquals(1, idx.query("title:eclipse").size(),
				"NRT reader must see the change before any commit");

			map.store();
			assertEquals(1, committedDocsOnDisk(this.lucenePath),
				"store() must commit pending writer changes to the external directory");

			// a further mutation without a store must NOT reach disk (no eager commit)
			map.add(new Article("store", "second"));
			assertEquals(2, idx.query("title:eclipse OR title:store").size(),
				"NRT reader must see the second change immediately");
			assertEquals(1, committedDocsOnDisk(this.lucenePath),
				"a mutation not followed by store() must not be committed to disk");

			map.store();
			assertEquals(2, committedDocsOnDisk(this.lucenePath),
				"the next store() must commit the second change to disk");
		}
		finally
		{
			idx.close();
		}
	}


	// ── embedded (graph) directory ──────────────────────────────────────────────

	@Test
	void graphDirectorySurvivesStoreWithManualCommit()
	{
		// null directoryCreator → GraphDirectory: index data lives in the persisted fileEntries
		// map. With autoCommit=false the writer must be flushed into that map at store() time,
		// otherwise store() would persist an empty/stale index (the bug fixed by #715).
		final GigaMap<Article> map = GigaMap.New();
		final LuceneIndex<Article> idx = map.index().register(LuceneIndex.Category(
			LuceneContext.New(null, AnalyzerCreator.Standard(), new ArticlePopulator(), false)));

		try(final EmbeddedStorageManager sm = EmbeddedStorage.start(map, this.storagePath))
		{
			map.add(new Article("eclipse", "graph directory"));
			map.add(new Article("store", "boundary"));
			map.store();
		}
		idx.close();

		LuceneIndex<Article> idx2 = null;
		try(final EmbeddedStorageManager sm = EmbeddedStorage.start(this.storagePath))
		{
			final GigaMap<Article> reloaded = sm.root();
			idx2 = reloaded.index().get(LuceneIndex.class);

			assertEquals(1, idx2.query("title:eclipse").size(),
				"graph index data must survive store() with autoCommit=false");
			assertEquals(2, reloaded.size(),
				"reloaded GigaMap must contain both entities");
		}
		finally
		{
			if(idx2 != null)
			{
				idx2.close();
			}
		}
	}
}
