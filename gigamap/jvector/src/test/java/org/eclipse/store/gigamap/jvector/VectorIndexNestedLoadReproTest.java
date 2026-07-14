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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Regression test for internal issue #87 (a regression from serializer#299).
 * <p>
 * A vector index rebuilds its transient HNSW graph inside its deserialization {@code complete()}
 * ({@code BinaryHandlerVectorIndexDefault.complete} -> {@code VectorIndex.ensureIndexInitialized}
 * -> {@code rebuildGraphFromStore} -> {@code collectStoredVectors} -> {@code parentMap().iterateIndexed}
 * -> {@code Lazy.get()}). That iteration performs a <b>nested object-graph load on the same thread</b>
 * while the enclosing roots load is still inside its own {@code completeInstances} phase.
 * <p>
 * Since serializer#299 deferred the loader's object-registry publication to a post-{@code complete}
 * phase, the outer loader's freshly built (but not yet published) instance of a shared entity is
 * invisible to that nested loader. The nested loader builds and publishes its own second instance
 * for the same object id; the outer loader's {@code registerBuiltInstances} then finds a foreign
 * instance under that id and throws {@code PersistenceExceptionConsistencyObject} — so the storage
 * fails to start.
 * <p>
 * The scenario needs a shared entity that is <b>both</b> built eagerly by the outer roots load and
 * re-loaded by the vector index's iteration: {@link Root} holds a direct reference to a
 * {@link Tag} (eager -> outer build) and a {@link GigaMap} of {@link Doc}s each also referencing the
 * same {@code Tag} (loaded by the vector index's embedded-mode iteration during {@code complete} ->
 * nested build). Against the unpatched jvector this fails at the second {@code start()}; once the
 * graph rebuild is deferred out of {@code complete()}, startup succeeds and search works.
 */
public class VectorIndexNestedLoadReproTest
{
	static final class Tag
	{
		String label;

		Tag(final String label)
		{
			this.label = label;
		}
	}

	static final class Doc
	{
		Tag     tag;
		float[] embedding;

		Doc(final Tag tag, final float[] embedding)
		{
			this.tag       = tag;
			this.embedding = embedding;
		}
	}

	static final class Root
	{
		Tag             sharedTag;
		GigaMap<Doc>    docs;
	}

	static final class DocVectorizer extends Vectorizer<Doc>
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

	private static Root newSeededRoot()
	{
		final Tag shared = new Tag("shared");

		final GigaMap<Doc> docs = GigaMap.New();
		final VectorIndices<Doc> vectorIndices = docs.index().register(VectorIndices.Category());
		vectorIndices.add("emb", config(), new DocVectorizer());

		docs.add(new Doc(shared, new float[]{1.0f, 0.0f, 0.0f}));
		docs.add(new Doc(shared, new float[]{0.0f, 1.0f, 0.0f}));
		docs.add(new Doc(shared, new float[]{0.0f, 0.0f, 1.0f}));

		final Root root = new Root();
		root.sharedTag = shared;   // eager reference from the root -> built by the outer roots load
		root.docs      = docs;     // each Doc also references `shared` -> re-loaded by the vector index
		return root;
	}

	@Test
	@Timeout(120)
	void restartWithSharedEntityAndVectorIndexMustStart(@TempDir final Path dir)
	{
		// ---- session 1: seed + store ----------------------------------------
		try(final EmbeddedStorageManager storage = EmbeddedStorage.start(dir))
		{
			storage.setRoot(newSeededRoot());
			storage.storeRoot();
		}

		// ---- session 2: restart (the failing path pre-fix) ------------------
		try(final EmbeddedStorageManager storage = EmbeddedStorage.start(dir))
		{
			@SuppressWarnings("unchecked")
			final Root root = (Root)storage.root();
			assertNotNull(root, "root must reload");
			assertNotNull(root.docs, "GigaMap must reload");
			assertEquals(3, root.docs.size(), "all docs must be present");

			// the shared entity must be a single instance across the root and the docs
			final VectorIndex<Doc> index = root.docs.index().get(VectorIndices.Category()).get("emb");
			assertNotNull(index, "vector index must reload");

			final VectorSearchResult<Doc> result = index.search(new float[]{1.0f, 0.0f, 0.0f}, 2);
			assertNotNull(result);
			assertEquals(2, result.size(), "search must work after restart");

			final Doc top = result.iterator().next().entity();
			assertNotNull(top);
			assertSame(root.sharedTag, top.tag, "the shared entity must resolve to a single instance");
		}
	}

	/**
	 * Same scenario, but two threads issue the very first {@code search()} concurrently right after
	 * restart — exercising the deferred, thread-safe first-access rebuild introduced by the fix.
	 */
	@Test
	@Timeout(120)
	void concurrentFirstSearchAfterRestart(@TempDir final Path dir) throws Exception
	{
		try(final EmbeddedStorageManager storage = EmbeddedStorage.start(dir))
		{
			storage.setRoot(newSeededRoot());
			storage.storeRoot();
		}

		try(final EmbeddedStorageManager storage = EmbeddedStorage.start(dir))
		{
			final Root root = (Root)storage.root();
			final VectorIndex<Doc> index = root.docs.index().get(VectorIndices.Category()).get("emb");

			final int threads = 4;
			final CountDownLatch ready = new CountDownLatch(threads);
			final CountDownLatch go    = new CountDownLatch(1);
			final AtomicReference<Throwable> failure = new AtomicReference<>();
			final Thread[] pool = new Thread[threads];
			for(int i = 0; i < threads; i++)
			{
				pool[i] = new Thread(() ->
				{
					try
					{
						ready.countDown();
						// bounded wait so a worker never blocks indefinitely if the start signal
						// is missed (e.g. the ready barrier below failed before countDown)
						if(!go.await(30, TimeUnit.SECONDS))
						{
							return;
						}
						final VectorSearchResult<Doc> r = index.search(new float[]{1.0f, 0.0f, 0.0f}, 2);
						assertEquals(2, r.size());
					}
					catch(final Throwable t)
					{
						failure.compareAndSet(null, t);
					}
				}, "first-search-" + i);
				// daemon so a stuck worker can never keep the JVM (and the test suite) alive
				pool[i].setDaemon(true);
				pool[i].start();
			}

			assertTrue(ready.await(30, TimeUnit.SECONDS), "threads must be ready");
			go.countDown();
			for(final Thread t : pool)
			{
				t.join(TimeUnit.SECONDS.toMillis(30));
				assertFalse(t.isAlive(), "search thread must finish");
			}
			if(failure.get() != null)
			{
				fail("concurrent first search failed", failure.get());
			}
		}
	}
}
