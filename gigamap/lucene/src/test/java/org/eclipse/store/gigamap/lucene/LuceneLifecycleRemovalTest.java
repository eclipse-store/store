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
import org.eclipse.store.gigamap.types.BitmapIndices;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.IndexGroup;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers structural lifecycle of the Lucene full-text index group: whole-group removal via
 * {@link org.eclipse.store.gigamap.types.GigaIndices#remove(org.eclipse.store.gigamap.types.IndexCategory)}
 * and redefinition via {@link LuceneIndex#update(LuceneContext)}.
 */
public class LuceneLifecycleRemovalTest
{
	static class Article
	{
		String title;
		String content;

		Article()
		{
			// for deserialization
		}

		Article(final String title, final String content)
		{
			this.title   = title;
			this.content = content;
		}
	}

	/** Indexes the title only. */
	static class TitleOnlyPopulator extends DocumentPopulator<Article>
	{
		@Override
		public void populate(final Document document, final Article entity)
		{
			document.add(createTextField("title", entity.title));
		}
	}

	// GraphDirectory context (data lives inside the GigaMap, so it survives EmbeddedStorage reload)
	private static LuceneContext<Article> titleOnly()
	{
		return LuceneContext.New(new TitleOnlyPopulator());
	}

	@SuppressWarnings("unchecked")
	private static GigaMap<Article> reload(final EmbeddedStorageManager m)
	{
		return (GigaMap<Article>)m.root();
	}

	@Test
	void removeWholeLuceneGroup()
	{
		final GigaMap<Article> map = GigaMap.New();
		final LuceneIndex<Article> idx = map.index().register(LuceneIndex.Category(titleOnly()));
		map.add(new Article("eclipse", "persistent"));
		assertEquals(1, idx.query("title:eclipse").size());

		assertTrue(map.index().remove(LuceneIndex.Category(titleOnly())));
		assertNull(map.index().get(LuceneIndex.Category(titleOnly())));

		// the lock was released: a fresh group can be registered again
		assertNotNull(map.index().register(LuceneIndex.Category(titleOnly())));
	}

	@Test
	void removeLuceneGroup_unknown_returnsFalse()
	{
		final GigaMap<Article> map = GigaMap.New(); // no lucene group registered
		assertFalse(map.index().remove(LuceneIndex.Category(titleOnly())));
	}

	@Test
	void removeLuceneGroup_readOnly_throws()
	{
		final GigaMap<Article> map = GigaMap.New();
		map.index().register(LuceneIndex.Category(titleOnly()));
		map.markReadOnly();

		assertThrows(RuntimeException.class, () -> map.index().remove(LuceneIndex.Category(titleOnly())));
		assertNotNull(map.index().get(LuceneIndex.Category(titleOnly())));

		map.unmarkReadOnly();
	}

	@Test
	void removeBitmapGroup_throws()
	{
		final GigaMap<Article> map = GigaMap.New();
		@SuppressWarnings("unchecked")
		final Class<? extends IndexGroup<Article>> bitmapType =
			(Class<? extends IndexGroup<Article>>)(Class<?>)BitmapIndices.class;

		assertThrows(IllegalArgumentException.class, () -> map.index().remove(bitmapType));
	}

	@Test
	void removeLuceneGroup_afterReload(@TempDir final Path dir)
	{
		try(final EmbeddedStorageManager m = EmbeddedStorage.start(dir))
		{
			final GigaMap<Article> map = GigaMap.New();
			m.setRoot(map);
			map.index().register(LuceneIndex.Category(titleOnly()));
			map.add(new Article("eclipse", "persistent"));
			m.storeRoot();
		}

		try(final EmbeddedStorageManager m = EmbeddedStorage.start(dir))
		{
			final GigaMap<Article> map = reload(m);
			assertTrue(map.index().remove(LuceneIndex.Category(titleOnly())));
			m.storeRoot();
		}

		try(final EmbeddedStorageManager m = EmbeddedStorage.start(dir))
		{
			final GigaMap<Article> map = reload(m);
			assertNull(map.index().get(LuceneIndex.Category(titleOnly()))); // removal persisted
			assertEquals(1, map.size());                                    // entity data intact
		}
	}
}
