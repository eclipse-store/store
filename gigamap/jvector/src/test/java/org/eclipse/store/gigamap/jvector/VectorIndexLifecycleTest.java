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
import org.eclipse.store.gigamap.types.GigaIterator;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.IndexGroup;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
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
	void addIndex_fromOtherThread_waitsForOpenReader()
	{
		final GigaMap<Doc> map = GigaMap.New();
		final VectorIndices<Doc> vi = map.index().register(VectorIndices.Category());
		addDocs(map);

		// Registering a vector index is a structural write, so it must wait for a reader open on another
		// thread instead of failing - exactly like an entity write does.
		assertWaitsForForeignReader(map, () -> vi.add("emb", config(), new EmbeddingVectorizer()));

		assertNotNull(vi.get("emb"));
		// The entities that were already there must be back-filled exactly once (internal #123).
		assertEquals(3, vi.get("emb").search(new float[]{1.0f, 0.0f, 0.0f}, 3).size());
	}

	@Test
	void ensureIndex_fromOtherThread_waitsForOpenReader()
	{
		final GigaMap<Doc> map = GigaMap.New();
		final VectorIndices<Doc> vi = map.index().register(VectorIndices.Category());
		addDocs(map);

		assertWaitsForForeignReader(map, () -> vi.ensure("emb", config(), new EmbeddingVectorizer()));

		assertNotNull(vi.get("emb"));
		assertEquals(3, vi.get("emb").search(new float[]{1.0f, 0.0f, 0.0f}, 3).size());
	}

	@Test
	void removeIndex_fromOtherThread_waitsForOpenReader()
	{
		final GigaMap<Doc> map = GigaMap.New();
		final VectorIndices<Doc> vi = map.index().register(VectorIndices.Category());
		vi.add("emb", config(), new EmbeddingVectorizer());
		addDocs(map);

		assertWaitsForForeignReader(map, () -> assertTrue(vi.removeIndex("emb")));

		assertNull(vi.get("emb"));
	}

	@Test
	void registerIndexGroup_fromOtherThread_waitsForOpenReader()
	{
		final GigaMap<Doc> map = GigaMap.New();
		addDocs(map);

		assertWaitsForForeignReader(map, () -> assertNotNull(map.index().register(VectorIndices.Category())));

		assertNotNull(map.index().get(VectorIndices.Category()));
	}

	@Test
	void removeIndexGroup_fromOtherThread_waitsForOpenReader()
	{
		final GigaMap<Doc> map = GigaMap.New();
		map.index().register(VectorIndices.Category());
		addDocs(map);

		assertWaitsForForeignReader(map, () -> assertTrue(map.index().remove(VectorIndices.Category())));

		assertNull(map.index().get(VectorIndices.Category()));
	}

	/**
	 * Runs {@code operation} on a dedicated thread while another thread holds an open reader on {@code map},
	 * and asserts that it neither throws nor completes early: it has to block until the foreign reader is
	 * closed, which is what an entity write does in the same situation.
	 */
	private static void assertWaitsForForeignReader(final GigaMap<Doc> map, final Executable operation)
	{
		final CountDownLatch             readerOpened  = new CountDownLatch(1);
		final CountDownLatch             releaseReader = new CountDownLatch(1);
		final CountDownLatch             operationDone = new CountDownLatch(1);
		final AtomicReference<Throwable> holderError   = new AtomicReference<>();
		final AtomicReference<Throwable> writerError   = new AtomicReference<>();

		final Thread holder = new Thread(() ->
		{
			// #iterator() registers the reader right away; hasNext() is deliberately NOT called, because it
			// closes the iterator as soon as there is no next element - which would release the hold again
			// on an empty map.
			try(final GigaIterator<Doc> reader = map.iterator())
			{
				assertNotNull(reader);
				readerOpened.countDown();
				releaseReader.await(30, TimeUnit.SECONDS);
			}
			catch(final Throwable t)
			{
				holderError.set(t);
			}
		}, "reader-holder");

		final Thread writer = new Thread(() ->
		{
			try
			{
				operation.execute();
			}
			catch(final Throwable t)
			{
				writerError.set(t);
			}
			finally
			{
				operationDone.countDown();
			}
		}, "index-writer");

		assertTimeoutPreemptively(Duration.ofSeconds(60), () ->
		{
			holder.start();
			assertTrue(readerOpened.await(10, TimeUnit.SECONDS), "the foreign reader must be open");

			writer.start();
			// The operation can only get here if it did NOT wait: either it threw or it went through while a
			// reader was open. Both are wrong, and #writerError names which one.
			assertFalse(
				operationDone.await(500, TimeUnit.MILLISECONDS),
				() -> "the operation must wait for the foreign reader to close, but it finished"
					+ (writerError.get() == null ? " immediately." : " by throwing " + writerError.get())
			);

			releaseReader.countDown();
			assertTrue(
				operationDone.await(30, TimeUnit.SECONDS),
				"the operation must proceed once the foreign reader is closed"
			);

			holder.join(10_000);
		});

		assertNull(holderError.get(), () -> "the reader holder failed: " + holderError.get());
		assertNull(writerError.get(), () -> "the operation failed: " + writerError.get());
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

	/**
	 * Registering the index <b>after</b> the entities exist - the natural ordering when a vector index is
	 * added to an existing dataset - and then reloading: the back-filled graph has to survive persistence,
	 * and the deferred post-load rebuild must not index the entities a second time (internal #123).
	 */
	@Test
	void addIndex_onPopulatedMap_survivesReload(@TempDir final Path dir)
	{
		try(final EmbeddedStorageManager storage = EmbeddedStorage.start(dir))
		{
			final GigaMap<Doc> map = GigaMap.New();
			storage.setRoot(map);
			addDocs(map);

			final VectorIndices<Doc> vi = map.index().register(VectorIndices.Category());
			final VectorIndex<Doc> index = vi.add("emb", config(), new EmbeddingVectorizer());

			assertEquals(3, index.search(new float[]{1.0f, 0.0f, 0.0f}, 3).size());
			storage.storeRoot();
		}

		try(final EmbeddedStorageManager storage = EmbeddedStorage.start(dir))
		{
			final GigaMap<Doc> map = storage.root();
			final VectorIndices<Doc> vi = map.index().get(VectorIndices.Category());

			assertEquals(3, map.size());
			assertEquals(3, vi.get("emb").search(new float[]{1.0f, 0.0f, 0.0f}, 3).size());
		}
	}
}
