package org.eclipse.store.gigamap.jvector;

/*-
 * #%L
 * EclipseStore GigaMap JVector
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

import org.eclipse.store.gigamap.types.BitmapIndices;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.IndexGroup;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Tag;
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
 * Covers the structural lifecycle of vector indices: {@link VectorIndices#removeIndex(String)} and
 * whole-group removal via
 * {@link org.eclipse.store.gigamap.types.GigaIndices#remove(org.eclipse.store.gigamap.types.IndexCategory)}.
 */
@Tag("slow")
class VectorIndexLifecycleTest
{
	static final class Doc
	{
		final float[] embedding;

		Doc()
		{
			this.embedding = null; // for deserialization
		}

		Doc(final float[] embedding)
		{
			this.embedding = embedding;
		}
	}

	static class EmbeddingVectorizer extends Vectorizer<Doc>
	{
		@Override
		public float[] vectorize(final Doc entity)
		{
			return entity.embedding;
		}

		@Override
		public boolean isEmbedded()
		{
			return true;
		}
	}

	private static VectorIndexConfiguration config()
	{
		return VectorIndexConfiguration.builder()
			.dimension(3)
			.similarityFunction(VectorSimilarityFunction.COSINE)
			.build();
	}

	private static void addDocs(final GigaMap<Doc> map)
	{
		map.add(new Doc(new float[]{1.0f, 0.0f, 0.0f}));
		map.add(new Doc(new float[]{0.0f, 1.0f, 0.0f}));
		map.add(new Doc(new float[]{0.0f, 0.0f, 1.0f}));
	}

	@Test
	void removeIndex_returnsTrue_andIndexGone()
	{
		final GigaMap<Doc> map = GigaMap.New();
		final VectorIndices<Doc> vi = map.index().register(VectorIndices.Category());
		vi.add("emb", config(), new EmbeddingVectorizer());
		addDocs(map);

		assertEquals(3, vi.get("emb").search(new float[]{1.0f, 0.0f, 0.0f}, 3).size());

		assertTrue(vi.removeIndex("emb"));
		assertNull(vi.get("emb"));
		assertFalse(vi.removeIndex("emb")); // idempotent
	}

	@Test
	void removeIndex_siblingIndexUnaffected()
	{
		final GigaMap<Doc> map = GigaMap.New();
		final VectorIndices<Doc> vi = map.index().register(VectorIndices.Category());
		vi.add("a", config(), new EmbeddingVectorizer());
		vi.add("b", config(), new EmbeddingVectorizer());
		addDocs(map);

		vi.removeIndex("a");

		assertNull(vi.get("a"));
		assertNotNull(vi.get("b"));
		assertEquals(3, vi.get("b").search(new float[]{1.0f, 0.0f, 0.0f}, 3).size());
	}

	@Test
	void removeIndex_unknownName_returnsFalse()
	{
		final GigaMap<Doc> map = GigaMap.New();
		final VectorIndices<Doc> vi = map.index().register(VectorIndices.Category());

		assertFalse(vi.removeIndex("nope"));
	}

	@Test
	void removeIndex_readOnly_throws()
	{
		final GigaMap<Doc> map = GigaMap.New();
		final VectorIndices<Doc> vi = map.index().register(VectorIndices.Category());
		vi.add("emb", config(), new EmbeddingVectorizer());
		addDocs(map);
		map.markReadOnly();

		assertThrows(RuntimeException.class, () -> vi.removeIndex("emb"));
		assertNotNull(vi.get("emb"));

		map.unmarkReadOnly();
	}

	@Test
	void removeWholeVectorGroup()
	{
		final GigaMap<Doc> map = GigaMap.New();
		final VectorIndices<Doc> vi = map.index().register(VectorIndices.Category());
		vi.add("emb", config(), new EmbeddingVectorizer());
		addDocs(map);

		assertTrue(map.index().remove(VectorIndices.Category())); // closes child indices
		assertNull(map.index().get(VectorIndices.Category()));
		assertFalse(map.index().remove(VectorIndices.Category())); // already gone
	}

	@Test
	void removeBitmapGroup_throws()
	{
		final GigaMap<Doc> map = GigaMap.New();
		@SuppressWarnings("unchecked")
		final Class<? extends IndexGroup<Doc>> bitmapType =
			(Class<? extends IndexGroup<Doc>>)(Class<?>)BitmapIndices.class;

		assertThrows(IllegalArgumentException.class, () -> map.index().remove(bitmapType));
	}

	@Test
	void removeIndex_afterReload(@TempDir final Path dir)
	{
		try(final EmbeddedStorageManager storage = EmbeddedStorage.start(dir))
		{
			final GigaMap<Doc> map = GigaMap.New();
			storage.setRoot(map);
			final VectorIndices<Doc> vi = map.index().register(VectorIndices.Category());
			vi.add("emb", config(), new EmbeddingVectorizer());
			addDocs(map);
			storage.storeRoot();
		}

		try(final EmbeddedStorageManager storage = EmbeddedStorage.start(dir))
		{
			final GigaMap<Doc> map = storage.root();
			final VectorIndices<Doc> vi = map.index().get(VectorIndices.Category());
			assertTrue(vi.removeIndex("emb"));
			storage.storeRoot();
		}

		try(final EmbeddedStorageManager storage = EmbeddedStorage.start(dir))
		{
			final GigaMap<Doc> map = storage.root();
			final VectorIndices<Doc> vi = map.index().get(VectorIndices.Category());
			assertNull(vi.get("emb"));   // removal persisted
			assertEquals(3, map.size()); // entity data intact
		}
	}
}
