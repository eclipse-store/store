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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;

import org.apache.lucene.document.Document;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Lucene projection of internal issue #119: {@code update()} of a committed entity whose new value
 * produces a document Lucene rejects - the mundane case being a single term longer than Lucene's hard
 * 32766-byte limit ("Document contains at least one immense term") - used to trigger GigaMap's
 * destructive {@code apply()} fallback, which removed the committed entity, and the next
 * {@code store()} persisted that loss.
 * <p>
 * The entity's own data is perfectly valid; only the derived index rejected a document. The exception
 * must escape, the deletion must not - deleting the entity would destroy committed data over an
 * index-layer input limit the user has never heard of.
 */
public class LuceneImmenseTermRetentionTest
{
	/** Longer than Lucene's 32766-byte term limit. */
	private static final String IMMENSE = "x".repeat(40_000);

	public static class Article
	{
		String title;

		Article(final String title)
		{
			this.title = title;
		}
	}

	static class ArticlePopulator extends DocumentPopulator<Article>
	{
		@Override
		public void populate(final Document document, final Article entity)
		{
			// untokenized field: the whole title is one term, so a long title is an "immense term"
			document.add(createStringField("exact", entity.title));
		}
	}

	private static GigaMap<Article> newIndexedMap()
	{
		final GigaMap<Article> map = GigaMap.New();
		map.index().register(LuceneIndex.Category(LuceneContext.New(
			DirectoryCreator.ByteBuffers(),
			new ArticlePopulator()
		)));
		return map;
	}

	@Test
	@Timeout(60)
	void updateWithOverlongValueRetainsTheCommittedEntity()
	{
		final GigaMap<Article> map = newIndexedMap();

		final Article article = new Article("ok");
		final long    id      = map.add(article);
		assertEquals(1, map.size());

		// control: ADDING an entity with the immense value fails loudly, nothing is lost
		assertThrows(RuntimeException.class, () -> map.add(new Article(IMMENSE)));
		assertEquals(1, map.size(), "control: a failed add must not affect existing data");

		// the k.o. path: updating the EXISTING committed entity to the immense value
		assertThrows(RuntimeException.class, () -> map.update(id, a -> a.title = IMMENSE));

		assertSame(article, map.get(id), "the committed entity must survive the index's rejection");
		assertEquals(1, map.size());
		assertEquals(IMMENSE, article.title, "the in-place mutation is kept");
	}

	@Test
	@Timeout(60)
	void retainedEntitySurvivesStoreAndRestart(@TempDir final Path dir)
	{
		final long id;
		try(final EmbeddedStorageManager storage = EmbeddedStorage.start(newIndexedMap(), dir))
		{
			@SuppressWarnings("unchecked")
			final GigaMap<Article> map = (GigaMap<Article>)storage.root();

			id = map.add(new Article("ok"));
			map.store(); // the entity is now committed and durable

			assertThrows(RuntimeException.class, () -> map.update(id, a -> a.title = IMMENSE));

			map.store(); // persist whatever state apply() left behind
		}

		try(final EmbeddedStorageManager storage = EmbeddedStorage.start(GigaMap.<Article>New(), dir))
		{
			@SuppressWarnings("unchecked")
			final GigaMap<Article> map = (GigaMap<Article>)storage.root();

			assertNotNull(map.get(id), "the committed entity must survive store() and restart");
			assertEquals(1, map.size());
		}
	}
}
