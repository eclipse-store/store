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
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Regression tests for the JVector background-index lifecycle: a background-enabled {@link VectorIndex}
 * that is abandoned without an explicit {@code close()} must not pin its object graph nor leak its daemon
 * thread, and closing indices on storage shutdown or removal must not dead-lock.
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

	private static final String INSERT_THREAD_PREFIX = "vector-insert-";

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

	// On-disk index + background persistence: index.close() runs doPersistToDisk on the background thread,
	// which is what turns "close under the parentMap monitor" into a deadlock.
	private static VectorIndexConfiguration onDiskBackgroundConfig(final Path indexDir)
	{
		return VectorIndexConfiguration.builder()
			.dimension(3)
			.similarityFunction(VectorSimilarityFunction.COSINE)
			.onDisk(true)
			.indexDirectory(indexDir)
			.persistenceIntervalMs(600_000L) // background persistence enabled; interval too long to tick in-test
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
	 * Like {@link #buildAndAbandon}, for an on-disk index whose background persistence has flushed its
	 * in-memory graph on the flush thread, requested by reaching the flush threshold, before every strong
	 * reference goes out of scope.
	 *
	 * @param flushThread receives the name of the thread the flush ran on
	 */
	private static WeakReference<Object> buildFlushAndAbandon(
		final Path                    indexDir   ,
		final AtomicReference<String> flushThread
	)
		throws InterruptedException
	{
		final VectorIndexConfiguration config = VectorIndexConfiguration.builder()
			.dimension(3)
			.similarityFunction(VectorSimilarityFunction.COSINE)
			.onDisk(true)
			.indexDirectory(indexDir)
			.persistenceIntervalMs(600_000L) // only the requested flush runs within the test
			.flushThreshold(10)
			.build();
		final GigaMap<Doc>       map = GigaMap.New();
		final VectorIndices<Doc> vi  = map.index().register(VectorIndices.Category());
		final VectorIndex.Default<Doc> index = (VectorIndex.Default<Doc>)vi.add("emb", config, new EmbeddingVectorizer());
		index.flushStageTestHook = stage ->
		{
			if(stage == VectorIndex.Default.FlushStage.PUBLISHED)
			{
				flushThread.set(Thread.currentThread().getName());
			}
		};

		final Random random = new Random(5);
		for(int i = 0; i < 30; i++)
		{
			map.add(new Doc(new float[]{random.nextFloat(), random.nextFloat(), random.nextFloat()}));
		}
		// Waits for the hook rather than the flush count, which is counted before the hook runs.
		final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30L);
		while(flushThread.get() == null && System.nanoTime() < deadline)
		{
			Thread.sleep(20L);
		}
		assertTrue(index.flushCount() > 0, "precondition: the threshold must have requested a flush");
		return new WeakReference<>(index);
	}

	/**
	 * Waits until no more background threads than {@code before} are alive, or the timeout elapses.
	 * <p>
	 * Closing an index shuts its executor down and awaits termination, but the worker thread may still be
	 * listed by {@link Thread#getAllStackTraces()} for a moment after that, so a check at a single instant
	 * is racy on a busy machine. A leaked thread stays alive for good, so a bounded wait tells the two apart.
	 *
	 * @return {@code true} if the count dropped to {@code before} or below within the timeout
	 */
	private static boolean awaitBackgroundThreadsReleased(final int before) throws InterruptedException
	{
		final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10L);
		while(backgroundThreadCount() > before)
		{
			if(System.nanoTime() >= deadline)
			{
				return false;
			}
			Thread.sleep(50L);
		}
		return true;
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
			"abandoned background-enabled vector index must not be pinned by its BackgroundTaskManager tasks");
		assertNull(backgroundRef.get());
	}

	private static int insertThreadCount()
	{
		return (int)Thread.getAllStackTraces().keySet().stream()
			.filter(t -> t.getName().startsWith(INSERT_THREAD_PREFIX))
			.filter(Thread::isAlive)
			.count();
	}

	/**
	 * Builds an index whose batch was large enough to start the workers of its insert pool, then lets
	 * every strong reference go out of scope on return.
	 */
	private static void buildInsertAndAbandon(final int seed)
	{
		final GigaMap<Doc>       map = GigaMap.New();
		final VectorIndices<Doc> vi  = map.index().register(VectorIndices.Category());
		vi.add("emb" + seed, plainConfig(), new EmbeddingVectorizer());
		final Random    random = new Random(seed);
		final List<Doc> docs   = new ArrayList<>();
		for(int i = 0; i < 2 * ParallelGraphInsertion.MIN_PARALLEL_NODES; i++)
		{
			docs.add(new Doc(new float[]{random.nextFloat(), random.nextFloat(), random.nextFloat()}));
		}
		map.addAll(docs);
	}

	/**
	 * The workers of an index's insert pool outlive its batches by design, so an index abandoned without a
	 * {@code close()} has to shut them down once it is collected, or they stay alive for good.
	 */
	@Test
	@Timeout(value = 120, unit = TimeUnit.SECONDS)
	void abandonedVectorIndicesReleaseInsertThreads() throws Exception
	{
		assumeTrue(ParallelGraphInsertion.parallelism() >= 2, "needs at least two cores to start the pool");
		final int n      = 10;
		final int before = insertThreadCount();

		for(int i = 0; i < n; i++)
		{
			buildInsertAndAbandon(i);
		}
		assertTrue(insertThreadCount() > before, "precondition: the batches must have started insert workers");

		final boolean released = awaitGc(() -> insertThreadCount() <= before, 60_000L);

		assertTrue(released,
			"abandoning " + n + " vector indices without close() leaked "
				+ (insertThreadCount() - before) + " '" + INSERT_THREAD_PREFIX + "' threads");
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
				+ "BackgroundTaskManager must self-terminate when its index is garbage-collected");
	}

	/**
	 * The flush thread of an on-disk index holds nothing of the index while idle, and ends on its own
	 * shortly after its last flush, so an abandoned index is collected and leaves no thread behind.
	 */
	@Test
	@Timeout(value = 120, unit = TimeUnit.SECONDS)
	void abandonedOnDiskIndexAfterABackgroundFlushIsCollectable(@TempDir final Path dir) throws Exception
	{
		final int                     before      = backgroundThreadCount();
		final AtomicReference<String> flushThread = new AtomicReference<>();

		final WeakReference<Object> ref = buildFlushAndAbandon(dir.resolve("index"), flushThread);

		assertEquals(BG_THREAD_PREFIX + "emb-flush", flushThread.get(), "the flush must run on the flush thread");
		assertTrue(awaitGc(() -> ref.get() == null, 30_000L),
			"an abandoned on-disk index must not be pinned by its flush thread or background task manager");
		assertTrue(awaitGc(() -> backgroundThreadCount() <= before, 60_000L),
			"abandoning an on-disk index leaked " + (backgroundThreadCount() - before) + " '"
				+ BG_THREAD_PREFIX + "' threads");
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

		assertTrue(awaitBackgroundThreadsReleased(before),
			"EmbeddedStorageManager.shutdown() must close vector index groups so no '"
				+ BG_THREAD_PREFIX + "' thread survives");
	}

	@Test
	@Timeout(value = 120, unit = TimeUnit.SECONDS)
	void storageShutdownDoesNotDeadlockWithOnDiskBackgroundPersist(@TempDir final Path dir) throws Exception
	{
		final int before = backgroundThreadCount();

		// On-disk background index with unsaved changes and the default persistOnShutdown=true: on shutdown
		// the background thread runs doPersistToDisk, which takes the builder write-lock and then the
		// parentMap monitor. If the close path still held the monitor across index.close() (which also wants
		// the write-lock), shutdown would dead-lock permanently.
		final EmbeddedStorageManager storage = EmbeddedStorage.start(dir.resolve("storage"));
		final GigaMap<Doc> map = GigaMap.New();
		storage.setRoot(map);
		final VectorIndices<Doc> vi = map.index().register(VectorIndices.Category());
		vi.add("emb", onDiskBackgroundConfig(dir.resolve("index")), new EmbeddingVectorizer());
		addDocs(map);                                          // leaves unsaved (dirty) index changes
		vi.get("emb").search(new float[]{1.0f, 0.0f, 0.0f}, 2); // force init + spawn the background thread
		storage.storeRoot();

		assertTrue(backgroundThreadCount() > before, "precondition: background thread must be running");

		// Preemptive timeout: a hard monitor deadlock cannot be interrupted, so run shutdown on a separate
		// thread that is abandoned on timeout, turning a regression into a clean failure instead of a hang.
		assertTimeoutPreemptively(Duration.ofSeconds(30), () -> { storage.shutdown(); },
			"storage.shutdown() dead-locked closing the on-disk background index");

		assertTrue(awaitBackgroundThreadsReleased(before),
			"shutdown must close the on-disk background index without dead-locking");
	}

	@Test
	@Timeout(value = 120, unit = TimeUnit.SECONDS)
	void removeIndexDoesNotDeadlockWithOnDiskBackgroundPersist(@TempDir final Path dir) throws Exception
	{
		final int before = backgroundThreadCount();

		try(final EmbeddedStorageManager storage = EmbeddedStorage.start(dir.resolve("storage")))
		{
			final GigaMap<Doc> map = GigaMap.New();
			storage.setRoot(map);
			final VectorIndices<Doc> vi = map.index().register(VectorIndices.Category());
			vi.add("emb", onDiskBackgroundConfig(dir.resolve("index")), new EmbeddingVectorizer());
			addDocs(map);
			vi.get("emb").search(new float[]{1.0f, 0.0f, 0.0f}, 2); // force init + spawn the background thread
			storage.storeRoot();

			assertTrue(backgroundThreadCount() > before, "precondition: background thread must be running");

			// removeIndex() closes the index; it must not hold the parentMap monitor across close().
			assertTimeoutPreemptively(Duration.ofSeconds(30), () -> { vi.removeIndex("emb"); },
				"removeIndex() dead-locked closing the on-disk background index");

			assertNull(vi.get("emb"), "index must be removed");
			assertTrue(awaitBackgroundThreadsReleased(before), "removeIndex must close the background index");
		}
	}

	@Test
	@Timeout(value = 120, unit = TimeUnit.SECONDS)
	void removeGroupDoesNotDeadlockWithOnDiskBackgroundPersist(@TempDir final Path dir) throws Exception
	{
		final int before = backgroundThreadCount();

		try(final EmbeddedStorageManager storage = EmbeddedStorage.start(dir.resolve("storage")))
		{
			final GigaMap<Doc> map = GigaMap.New();
			storage.setRoot(map);
			final VectorIndices<Doc> vi = map.index().register(VectorIndices.Category());
			vi.add("emb", onDiskBackgroundConfig(dir.resolve("index")), new EmbeddingVectorizer());
			addDocs(map);
			vi.get("emb").search(new float[]{1.0f, 0.0f, 0.0f}, 2); // force init + spawn the background thread
			storage.storeRoot();

			assertTrue(backgroundThreadCount() > before, "precondition: background thread must be running");

			// Whole-group removal closes each contained index; it must not hold the parentMap monitor across
			// close() (internalRemoveGroup must detach under the monitor but close outside it).
			assertTimeoutPreemptively(Duration.ofSeconds(30),
				() -> { map.index().remove(VectorIndices.Category()); },
				"index group removal dead-locked closing the on-disk background index");

			assertNull(map.index().get(VectorIndices.Category()), "group must be removed");
			assertTrue(awaitBackgroundThreadsReleased(before), "group removal must close the background index");
		}
	}
}
