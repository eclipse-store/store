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

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A failed Lucene back-fill must not leave documents behind in an external directory (internal#143): neither
 * a failed registration, whose group is discarded, nor a failed {@link GigaMap#reindex()}, whose partial
 * rebuild must not be committed by the next write.
 * <p>
 * The failure is transient: the populator throws for one entity only while {@link #FAIL} is set.
 */
class LuceneBackfillFailureTest
{
	private static final int COUNT  = 10;
	private static final int POISON = 5;

	static final AtomicBoolean FAIL = new AtomicBoolean();

	@TempDir
	Path temp;

	static final class Article
	{
		final int no;

		Article(final int no)
		{
			this.no = no;
		}
	}

	static final class FlakyPopulator extends DocumentPopulator<Article>
	{
		@Override
		public void populate(final Document document, final Article entity)
		{
			if(FAIL.get() && entity.no == POISON)
			{
				throw new IllegalStateException("simulated populator failure for entity " + entity.no);
			}
			document.add(createStringField("kind", "article"));
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

	/** entity ids of all indexed documents, duplicates included */
	private static List<Long> documentIds(final LuceneIndex<Article> index)
	{
		final List<Long> ids = new ArrayList<>();
		index.query("kind:article", COUNT * 10, (id, entity, score) -> ids.add(id));
		return ids;
	}

	private static int committedDocuments(final Path dir) throws IOException
	{
		try(final Directory directory = FSDirectory.open(dir))
		{
			if(!DirectoryReader.indexExists(directory))
			{
				return 0;
			}
			try(final DirectoryReader reader = DirectoryReader.open(directory))
			{
				return reader.numDocs();
			}
		}
	}

	@AfterEach
	void reset()
	{
		FAIL.set(false);
	}

	@Test
	void failedRegistrationCommitsNothingAndARetryIndexesEachEntityOnce() throws IOException
	{
		final Path                   dir     = this.temp.resolve("lucene");
		final GigaMap<Article>       map     = populatedMap();
		final LuceneContext<Article> context = LuceneContext.New(DirectoryCreator.MMap(dir), new FlakyPopulator());

		FAIL.set(true);
		assertThrows(IllegalStateException.class, () -> map.index().register(LuceneIndex.Category(context)));
		FAIL.set(false);

		assertEquals(0, committedDocuments(dir));

		try(final LuceneIndex<Article> index = map.index().register(LuceneIndex.Category(context)))
		{
			assertEquals(COUNT, documentIds(index).size());
		}
	}

	@Test
	void failedReindexIsNotCommittedByTheNextWrite() throws IOException
	{
		final Path                   dir     = this.temp.resolve("lucene");
		final GigaMap<Article>       map     = populatedMap();
		final LuceneContext<Article> context = LuceneContext.New(DirectoryCreator.MMap(dir), new FlakyPopulator());

		try(final LuceneIndex<Article> index = map.index().register(LuceneIndex.Category(context)))
		{
			FAIL.set(true);
			assertThrows(IllegalStateException.class, map::reindex);
			FAIL.set(false);

			map.add(new Article(COUNT)); // an auto-commit write
			assertEquals(COUNT + 1, documentIds(index).size());
		}

		assertEquals(COUNT + 1, committedDocuments(dir));
	}
}
