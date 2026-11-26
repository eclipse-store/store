package org.eclipse.store.gigamap.types;

/*-
 * #%L
 * EclipseStore GigaMap
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

import org.eclipse.serializer.persistence.types.Persistence;
import org.eclipse.serializer.persistence.util.Reloader;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a mutation does while the map is being read: it waits for readers, iterations and query workers of other
 * threads, and fails if the mutating thread is one of the readers itself, since it would wait for itself. The
 * explicit read-only mode rejects mutations right away.
 * <p>
 * These holds are runtime state of the map instance. A reload of the instance must not drop them.
 */
@Timeout(120)
public class ReadHoldsTest
{
	static final int ENTITY_COUNT = 10_000;

	@Test
	void mutationOfAnotherThread_waitsForExecute() throws InterruptedException
	{
		final GigaMap<Entity> map        = newMap();
		final CountDownLatch  inConsumer = new CountDownLatch(1);
		final CountDownLatch  go         = new CountDownLatch(1);

		final AtomicReference<Throwable> executeFailure = new AtomicReference<>();
		final AtomicReference<Throwable> failure        = new AtomicReference<>();
		final Thread executing = start(() -> map.query(new KeyIndexer(), 0).execute(entity ->
		{
			inConsumer.countDown();
			awaitUninterruptibly(go);
		}), executeFailure);
		final Thread mutator;
		try
		{
			await(inConsumer, executeFailure);

			mutator = start(() -> map.add(new Entity(0)), failure);
			awaitWaiting(mutator);
			assertTrue(mutator.isAlive(), "the mutation must wait for the execution, failure: " + failure.get());
			assertEquals(ENTITY_COUNT, map.size());
		}
		finally
		{
			go.countDown();
		}
		join(executing, mutator);
		assertNull(executeFailure.get());
		assertNull(failure.get());
		assertEquals(ENTITY_COUNT + 1, map.size());
		assertFalse(map.isReadOnly());
	}

	@Test
	void mutationOfAnotherThread_waitsForAnOpenIterator() throws InterruptedException
	{
		final GigaMap<Entity> map = newMap();

		final AtomicReference<Throwable> failure = new AtomicReference<>();
		final Thread mutator;
		try(GigaIterator<Entity> iterator = map.query(new KeyIndexer(), 0).iterator())
		{
			assertTrue(iterator.hasNext());
			assertTrue(map.isReadOnly());

			mutator = start(() -> map.add(new Entity(0)), failure);
			awaitWaiting(mutator);
			assertTrue(mutator.isAlive(), "the mutation must wait for the open iterator, failure: " + failure.get());
			assertEquals(ENTITY_COUNT, map.size());
		}
		join(mutator);
		assertNull(failure.get());
		assertEquals(ENTITY_COUNT + 1, map.size());
		assertFalse(map.isReadOnly());
	}

	@Test
	void mutationOfAnotherThread_waitsForLogicsThatOutliveTheirQuery() throws InterruptedException
	{
		final GigaMap.Default<Entity> map = (GigaMap.Default<Entity>)newMap();
		final CountDownLatch          go  = new CountDownLatch(1);

		final AtomicReference<Throwable> failure = new AtomicReference<>();
		final Thread worker;
		final Thread mutator;
		try
		{
			worker = startLogicOutlivingItsQuery(map, go);
			assertTrue(map.isReadOnly(), "the query is over, but a logic still reads");

			mutator = start(() -> map.add(new Entity(0)), failure);
			awaitWaiting(mutator);
			assertTrue(mutator.isAlive(), "the mutation must wait for the running logic, failure: " + failure.get());
			assertEquals(ENTITY_COUNT, map.size());
		}
		finally
		{
			go.countDown();
		}
		join(worker, mutator);
		assertNull(failure.get());
		assertFalse(map.isReadOnly(), "the logic is done");
		assertEquals(ENTITY_COUNT + 1, map.size());
	}

	/**
	 * A waiting mutation polls only for logics that run after their query ended. An execution in progress notifies
	 * when it ends, so its gate must not make waiting mutations poll for its whole duration.
	 */
	@Test
	void waitTimeout_pollsOnlyForLogicsThatOutliveTheirQuery() throws InterruptedException
	{
		final ReadHolds      holds   = new ReadHolds(newMap());
		final QueryWorkers   workers = new QueryWorkers();
		final CountDownLatch started = new CountDownLatch(1);
		final CountDownLatch go      = new CountDownLatch(1);

		final AtomicReference<Throwable> failure = new AtomicReference<>();
		holds.registerWorkers(workers); // as an execution in progress does
		final Thread worker = start(workers.guard(() ->
		{
			started.countDown();
			awaitUninterruptibly(go);
		}), failure);
		try
		{
			await(started, failure);
			assertEquals(0, holds.waitTimeout(), "the execution notifies when it ends");

			assertTrue(holds.releaseWorkers(workers), "the logic still runs");
			assertEquals(1, holds.waitTimeout(), "a logic that outlives its query does not notify");
			assertTrue(holds.isReadOnly(), "mutations wait for it");
		}
		finally
		{
			go.countDown();
		}
		join(worker);
		assertNull(failure.get());
		// done, but not pruned yet: a mutation may have decided to wait while it still ran, and nobody notifies it
		assertEquals(1, holds.waitTimeout(), "a mutation that decided to wait before must not wait forever");
		assertFalse(holds.isReadOnly());
		assertEquals(0, holds.waitTimeout());
	}

	@Test
	void mutationOfALogicThatOutlivesItsQuery_fails() throws InterruptedException
	{
		final GigaMap.Default<Entity> map     = (GigaMap.Default<Entity>)newMap();
		final QueryWorkers            workers = new QueryWorkers();
		final CountDownLatch          started = new CountDownLatch(1);
		final CountDownLatch          go      = new CountDownLatch(1);

		// it would wait for itself
		final AtomicReference<Throwable> threadFailure = new AtomicReference<>();
		final Thread worker = start(workers.guard(() ->
		{
			started.countDown();
			awaitUninterruptibly(go);
			map.add(new Entity(0));
		}), threadFailure);
		try
		{
			await(started, threadFailure);
			assertTrue(map.releaseWorkers(workers), "the logic is still running");
		}
		finally
		{
			go.countDown();
		}
		join(worker);

		// kept by the gate, as a consumer's failure is
		assertNull(threadFailure.get());
		assertThrows(IllegalStateException.class, workers::throwFailure);
		assertEquals(ENTITY_COUNT, map.size());

		// the gate is done: nothing is left behind
		map.add(new Entity(0));
		assertEquals(ENTITY_COUNT + 1, map.size());
	}

	@Test
	void mutationOfTheReadingThread_fails()
	{
		final GigaMap<Entity> map = newMap();

		// inside execute
		assertThrows(IllegalStateException.class, () ->
			map.query(new KeyIndexer(), 0).execute(entity -> map.add(new Entity(0)))
		);
		// inside iterate
		assertThrows(IllegalStateException.class, () ->
			map.iterate(entity -> map.removeById(0))
		);
		// with an own open iterator
		try(GigaIterator<Entity> iterator = map.query(new KeyIndexer(), 0).iterator())
		{
			assertTrue(iterator.hasNext());
			assertThrows(IllegalStateException.class, () -> map.add(new Entity(0)));
		}

		// nothing is left behind
		assertFalse(map.isReadOnly());
		assertEquals(ENTITY_COUNT, map.size());
		map.add(new Entity(0));
		assertEquals(ENTITY_COUNT + 1, map.size());
	}

	@Test
	void mutationOfAConsumerThreadOfAThreadedExecute_fails()
	{
		final GigaMap<Entity>         map     = newMap();
		final IterationThreadProvider threads = IterationThreadProvider.Creating(ThreadCountProvider.Fixed(2));
		final AtomicInteger           calls   = new AtomicInteger();

		final Consumer<Entity> mutatingOnce = entity ->
		{
			if(calls.getAndIncrement() == 0)
			{
				map.add(new Entity(0));
			}
		};

		// the consumer's failure reaches the caller
		assertThrows(IllegalStateException.class, () ->
			map.query(threads).and(new KeyIndexer().is(0)).execute(mutatingOnce, entity -> {})
		);
		assertEquals(ENTITY_COUNT, map.size());

		// nothing is left behind
		assertFalse(map.isReadOnly());
		map.add(new Entity(0));
		assertEquals(ENTITY_COUNT + 1, map.size());
	}

	@Test
	void waitingMutation_interrupted_failsAndKeepsTheInterruptStatus() throws InterruptedException
	{
		final GigaMap<Entity> map = newMap();

		final AtomicReference<Throwable> failure     = new AtomicReference<>();
		final AtomicBoolean              interrupted = new AtomicBoolean();
		final Thread mutator;
		try(GigaIterator<Entity> iterator = map.query(new KeyIndexer(), 0).iterator())
		{
			assertTrue(iterator.hasNext());

			mutator = start(() ->
			{
				try
				{
					map.add(new Entity(0));
				}
				finally
				{
					interrupted.set(Thread.currentThread().isInterrupted());
				}
			}, failure);
			awaitWaiting(mutator);
			assertTrue(mutator.isAlive(), "the mutation must wait for the open iterator, failure: " + failure.get());

			// gives up while the reader is still open
			mutator.interrupt();
			join(mutator);
		}
		assertTrue(failure.get() instanceof IllegalStateException, "got " + failure.get());
		assertTrue(interrupted.get(), "the interrupt status must be kept for the caller");
		assertEquals(ENTITY_COUNT, map.size());
	}

	@Test
	void nestedIterationsAndReaders_areAllReleased()
	{
		final GigaMap<Entity> map = newMap();

		final GigaIterator<Entity> first  = map.query(new KeyIndexer(), 0).iterator();
		final GigaIterator<Entity> second = map.query(new KeyIndexer(), 1).iterator();
		try
		{
			assertTrue(first.hasNext());
			assertTrue(second.hasNext());
			map.query(new KeyIndexer(), 0).execute(entity ->
				map.query(new KeyIndexer(), 1).execute(inner -> {})
			);
			assertTrue(map.isReadOnly());

			first.close();
			first.close();
			assertTrue(map.isReadOnly(), "the second iterator is still open");
		}
		finally
		{
			first.close();
			second.close();
		}
		assertFalse(map.isReadOnly());
		map.add(new Entity(0));
	}

	@Test
	void explicitReadOnly_rejectsMutationsUntilUnmarked()
	{
		final GigaMap<Entity> map = newMap();
		map.markReadOnly();
		map.markReadOnly();
		assertThrows(IllegalStateException.class, () -> map.add(new Entity(0)));
		map.unmarkReadOnly();
		assertTrue(map.isReadOnly());
		assertThrows(IllegalStateException.class, () -> map.add(new Entity(0)));
		map.unmarkReadOnly();
		assertFalse(map.isReadOnly());
		map.add(new Entity(0));
		assertThrows(IllegalStateException.class, map::unmarkReadOnly);
		assertEquals(ENTITY_COUNT + 1, map.size());
	}

	@Test
	void explicitReadOnly_rejectsRightAwayAlsoWhileAnotherThreadReads() throws InterruptedException
	{
		final GigaMap<Entity> map = newMap();
		map.markReadOnly();
		try(GigaIterator<Entity> iterator = map.query(new KeyIndexer(), 0).iterator())
		{
			assertTrue(iterator.hasNext());

			// does not wait for the reader first
			final AtomicReference<Throwable> failure = new AtomicReference<>();
			join(start(() -> map.add(new Entity(0)), failure));
			assertTrue(failure.get() instanceof IllegalStateException, "got " + failure.get());
		}
		finally
		{
			map.unmarkReadOnly();
		}
		assertEquals(ENTITY_COUNT, map.size());
	}

	@Test
	void reload_keepsAnOpenReaderAndItsHold(@TempDir final Path dir) throws InterruptedException
	{
		final GigaMap<Entity> map = newMap();
		try(EmbeddedStorageManager storage = EmbeddedStorage.start(map, dir))
		{
			map.store();

			final AtomicReference<Throwable> failure = new AtomicReference<>();
			final Thread mutator;

			// closing must not fail either for a reader the reload forgot
			try(GigaIterator<Entity> iterator = map.query(new KeyIndexer(), 0).iterator())
			{
				assertTrue(iterator.hasNext());

				reload(storage, map);
				assertTrue(map.isReadOnly(), "the open reader must survive the reload");

				mutator = start(() -> map.add(new Entity(0)), failure);
				awaitWaiting(mutator);
				assertTrue(mutator.isAlive(), "the mutation must still wait for the open iterator, failure: " + failure.get());
			}
			join(mutator);
			assertNull(failure.get());
			assertFalse(map.isReadOnly());
			assertEquals(ENTITY_COUNT + 1, map.size());
		}
	}

	@Test
	void reload_keepsLogicsThatOutliveTheirQuery(@TempDir final Path dir) throws InterruptedException
	{
		final GigaMap.Default<Entity> map = (GigaMap.Default<Entity>)newMap();
		try(EmbeddedStorageManager storage = EmbeddedStorage.start(map, dir))
		{
			map.store();
			final CountDownLatch go = new CountDownLatch(1);

			final AtomicReference<Throwable> failure = new AtomicReference<>();
			final Thread worker;
			final Thread mutator;
			try
			{
				worker = startLogicOutlivingItsQuery(map, go);
				reload(storage, map);

				mutator = start(() -> map.add(new Entity(0)), failure);
				awaitWaiting(mutator);
				assertTrue(mutator.isAlive(), "the mutation must still wait for the running logic, failure: " + failure.get());
			}
			finally
			{
				go.countDown();
			}
			join(worker, mutator);
			assertNull(failure.get());
			assertEquals(ENTITY_COUNT + 1, map.size());
		}
	}

	@Test
	void reload_keepsAnExecutionsHoldAndTheExplicitMark(@TempDir final Path dir)
	{
		final GigaMap<Entity> map = newMap();
		try(EmbeddedStorageManager storage = EmbeddedStorage.start(map, dir))
		{
			map.store();

			// reload from inside a running execution: its hold must still be there and be released normally
			final AtomicInteger calls = new AtomicInteger();
			final AtomicReference<Throwable> mutation = new AtomicReference<>();
			map.query(new KeyIndexer(), 0).execute(entity ->
			{
				if(calls.getAndIncrement() == 0)
				{
					reload(storage, map);
					try
					{
						map.add(new Entity(0));
					}
					catch(final Throwable t)
					{
						mutation.set(t);
					}
				}
			});
			assertTrue(mutation.get() instanceof IllegalStateException, "got " + mutation.get());
			assertFalse(map.isReadOnly());

			map.markReadOnly();
			try
			{
				reload(storage, map);
				assertTrue(map.isReadOnly(), "the explicit mark must survive the reload");
				assertThrows(IllegalStateException.class, () -> map.add(new Entity(0)));
			}
			finally
			{
				map.unmarkReadOnly();
			}
			assertFalse(map.isReadOnly());
			map.add(new Entity(0));
			assertEquals(ENTITY_COUNT + 1, map.size());
		}
	}



	///////////////////////////////////////////////////////////////////////////
	// fixture //
	////////////

	private static GigaMap<Entity> newMap()
	{
		final GigaMap<Entity> map = GigaMap.New();
		map.index().bitmap().add(new KeyIndexer());
		for(int i = 0; i < ENTITY_COUNT; i++)
		{
			map.add(new Entity(i % 2));
		}
		return map;
	}

	// reloads the state of the very same map instance from the storage
	private static void reload(final EmbeddedStorageManager storage, final GigaMap<Entity> map)
	{
		// an instance the storage does not know would silently not be reloaded
		assertTrue(
			Persistence.IdType.OID.isInRange(storage.persistenceManager().lookupObjectId(map)),
			"the map must be stored for a reload"
		);
		Reloader.New(storage.persistenceManager()).reloadFlat(map);
	}

	/**
	 * Leaves the map in the state after a threaded query has ended while one of its logics is still reading:
	 * the logic runs on the returned thread until the passed latch is released.
	 */
	private static Thread startLogicOutlivingItsQuery(final GigaMap.Default<Entity> map, final CountDownLatch go)
		throws InterruptedException
	{
		final QueryWorkers   workers = new QueryWorkers();
		final CountDownLatch started = new CountDownLatch(1);

		final AtomicReference<Throwable> failure = new AtomicReference<>();
		final Thread worker = start(workers.guard(() ->
		{
			started.countDown();
			awaitUninterruptibly(go);
		}), failure);
		await(started, failure);
		assertTrue(map.releaseWorkers(workers), "the logic is still running");

		return worker;
	}

	private static Thread start(final Runnable logic, final AtomicReference<Throwable> failure)
	{
		final Thread thread = new Thread(() ->
		{
			try
			{
				logic.run();
			}
			catch(final Throwable t)
			{
				failure.set(t);
			}
		});
		thread.setDaemon(true);
		thread.start();

		return thread;
	}

	// for a latch another thread counts down: fails with that thread's failure instead of waiting on
	private static void await(final CountDownLatch latch, final AtomicReference<Throwable> failure)
		throws InterruptedException
	{
		assertTrue(latch.await(30, TimeUnit.SECONDS), "not reached, failure of the other thread: " + failure.get());
	}

	// Returns once the thread waits (a mutation waiting for the map to become mutable) or has ended. A thread
	// that wrongly does not wait ends, so the caller's check that it is alive cannot pass by timing.
	private static void awaitWaiting(final Thread thread) throws InterruptedException
	{
		while(thread.isAlive()
			&& thread.getState() != Thread.State.WAITING
			&& thread.getState() != Thread.State.TIMED_WAITING
		)
		{
			Thread.sleep(1);
		}
	}

	private static void join(final Thread... threads) throws InterruptedException
	{
		for(final Thread thread : threads)
		{
			thread.join(30_000);
			assertFalse(thread.isAlive(), "thread did not finish");
		}
	}

	private static void awaitUninterruptibly(final CountDownLatch latch)
	{
		boolean interrupted = false;
		while(true)
		{
			try
			{
				latch.await();
				break;
			}
			catch(final InterruptedException e)
			{
				interrupted = true;
			}
		}
		if(interrupted)
		{
			Thread.currentThread().interrupt();
		}
	}

	static class Entity
	{
		final int key;

		Entity(final int key)
		{
			this.key = key;
		}
	}

	static class KeyIndexer extends IndexerInteger.Abstract<Entity>
	{
		@Override
		protected Integer getInteger(final Entity entity)
		{
			return entity.key;
		}
	}
}
