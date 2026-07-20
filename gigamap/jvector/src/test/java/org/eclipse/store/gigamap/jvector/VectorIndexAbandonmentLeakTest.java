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

import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.lang.ref.WeakReference;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for internal#104: a background-enabled {@link VectorIndex} that is abandoned
 * without an explicit {@code close()} must not pin its object graph nor leak its daemon thread.
 * <p>
 * The manager holds the index through a {@link WeakReference} and a liveness watchdog self-terminates
 * the executor once the index has been collected, so a dropped index becomes fully reclaimable
 * (thread + graph) even with no storage in play. The storage-backed case additionally verifies that
 * shutting down the {@link EmbeddedStorageManager} closes the index groups without an explicit
 * {@code close()}.
 */
@Tag("slow")
class VectorIndexAbandonmentLeakTest
{
	private static final String BG_THREAD_PREFIX = "VectorIndex-Background-";

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

	private static VectorIndexConfiguration plainConfig()
	{
		return VectorIndexConfiguration.builder()
			.dimension(3)
			.similarityFunction(VectorSimilarityFunction.COSINE)
			.build();
	}

	private static VectorIndexConfiguration backgroundConfig()
	{
		return VectorIndexConfiguration.builder()
			.dimension(3)
			.similarityFunction(VectorSimilarityFunction.COSINE)
			.eventualIndexing(true)
			.optimizationIntervalMs(50L)
			.minChangesBetweenOptimizations(1)
			.build();
	}

	private static void addDocs(final GigaMap<Doc> map)
	{
		map.add(new Doc(new float[]{1.0f, 0.0f, 0.0f}));
		map.add(new Doc(new float[]{0.0f, 1.0f, 0.0f}));
	}

	private static int backgroundThreadCount()
	{
		return (int)Thread.getAllStackTraces().keySet().stream()
			.filter(t -> t.getName().startsWith(BG_THREAD_PREFIX))
			.count();
	}

	/**
	 * Builds a background-enabled vector index on a fresh in-memory map, forces it to initialize, then
	 * lets every strong reference (map, group, index) go out of scope on return. Only a
	 * {@link WeakReference} to the index survives.
	 */
	private static WeakReference<Object> buildAndAbandon(final VectorIndexConfiguration config)
	{
		final GigaMap<Doc> map = GigaMap.New();
		final VectorIndices<Doc> vi = map.index().register(VectorIndices.Category());
		vi.add("emb", config, new EmbeddingVectorizer());
		addDocs(map);
		vi.get("emb").search(new float[]{1.0f, 0.0f, 0.0f}, 2); // force lazy initialization
		return new WeakReference<>(vi.get("emb"));
	}

	/**
	 * Repeatedly triggers GC and waits until {@code condition} holds or the timeout elapses.
	 *
	 * @return {@code true} if the condition became true within the timeout
	 */
	private static boolean awaitGc(final BooleanSupplier condition, final long timeoutMs) throws InterruptedException
	{
		final long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
		while(System.nanoTime() < deadline)
		{
			System.gc();
			if(condition.getAsBoolean())
			{
				return true;
			}
			Thread.sleep(100L);
		}
		return condition.getAsBoolean();
	}

	@Test
	@Timeout(value = 120, unit = TimeUnit.SECONDS)
	void abandonedBackgroundIndexIsCollectable() throws Exception
	{
		final WeakReference<Object> backgroundRef = buildAndAbandon(backgroundConfig());
		final WeakReference<Object> plainRef      = buildAndAbandon(plainConfig());

		assertTrue(awaitGc(() -> plainRef.get() == null, 30_000L),
			"control: a vector index with no background feature must be collectable once abandoned");
		assertTrue(awaitGc(() -> backgroundRef.get() == null, 30_000L),
			"abandoned background-enabled vector index must not be pinned by its BackgroundTaskManager "
				+ "tasks -- internal#104");
		assertNull(backgroundRef.get());
	}

	@Test
	@Timeout(value = 120, unit = TimeUnit.SECONDS)
	void abandonedVectorIndicesReleaseBackgroundThreads() throws Exception
	{
		final int n      = 20;
		final int before = backgroundThreadCount();

		for(int i = 0; i < n; i++)
		{
			buildAndAbandon(backgroundConfig());
		}

		// The liveness watchdog self-terminates each abandoned index's executor once it is collected.
		final boolean released = awaitGc(() -> backgroundThreadCount() <= before, 60_000L);

		assertTrue(released,
			"abandoning " + n + " background-enabled vector indices without close() leaked "
				+ (backgroundThreadCount() - before) + " '" + BG_THREAD_PREFIX + "' threads; the "
				+ "BackgroundTaskManager must self-terminate when its index is garbage-collected -- internal#104");
	}

	@Test
	@Timeout(value = 120, unit = TimeUnit.SECONDS)
	void indicesClosedOnStorageShutdown(@TempDir final Path dir) throws Exception
	{
		final int before = backgroundThreadCount();

		final EmbeddedStorageManager storage = EmbeddedStorage.start(dir);
		final GigaMap<Doc> map = GigaMap.New();
		storage.setRoot(map);
		final VectorIndices<Doc> vi = map.index().register(VectorIndices.Category());
		vi.add("emb", backgroundConfig(), new EmbeddingVectorizer());
		addDocs(map);
		vi.get("emb").search(new float[]{1.0f, 0.0f, 0.0f}, 2); // force init + spawn the background thread
		storage.storeRoot();

		assertTrue(backgroundThreadCount() > before, "precondition: background thread must be running");

		// Shut down storage WITHOUT ever calling index.close(); the shutdown hook must close the groups.
		storage.shutdown();

		assertTrue(backgroundThreadCount() <= before,
			"EmbeddedStorageManager.shutdown() must close vector index groups so no '"
				+ BG_THREAD_PREFIX + "' thread survives -- internal#104");
	}

	@Test
	@Timeout(value = 120, unit = TimeUnit.SECONDS)
	void storageShutdownDoesNotDeadlockWithOnDiskBackgroundPersist(@TempDir final Path dir) throws Exception
	{
		final int  before     = backgroundThreadCount();
		final Path storageDir = dir.resolve("storage");
		final Path indexDir   = dir.resolve("index");

		// On-disk background index with unsaved changes and the default persistOnShutdown=true: on shutdown
		// the background thread runs doPersistToDisk, which takes the builder write-lock and then the
		// parentMap monitor. If the close path still held the monitor across index.close() (which also wants
		// the write-lock), shutdown would dead-lock permanently -- @Timeout turns that into a failure.
		final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
			.dimension(3)
			.similarityFunction(VectorSimilarityFunction.COSINE)
			.onDisk(true)
			.indexDirectory(indexDir)
			.persistenceIntervalMs(600_000L) // background persistence enabled; interval too long to tick in-test
			.build();

		final EmbeddedStorageManager storage = EmbeddedStorage.start(storageDir);
		final GigaMap<Doc> map = GigaMap.New();
		storage.setRoot(map);
		final VectorIndices<Doc> vi = map.index().register(VectorIndices.Category());
		vi.add("emb", config, new EmbeddingVectorizer());
		addDocs(map);                                          // leaves unsaved (dirty) index changes
		vi.get("emb").search(new float[]{1.0f, 0.0f, 0.0f}, 2); // force init + spawn the background thread
		storage.storeRoot();

		assertTrue(backgroundThreadCount() > before, "precondition: background thread must be running");

		// Preemptive timeout: a hard monitor deadlock cannot be interrupted, so run shutdown on a separate
		// thread that is abandoned on timeout, turning a regression into a clean failure instead of a hang.
		assertTimeoutPreemptively(Duration.ofSeconds(30), () -> { storage.shutdown(); },
			"storage.shutdown() dead-locked closing the on-disk background index -- internal#104");

		assertTrue(backgroundThreadCount() <= before,
			"shutdown must close the on-disk background index without dead-locking -- internal#104");
	}
}
