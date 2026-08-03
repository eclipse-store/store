package org.eclipse.store.gigamap.indexer.edge;

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

import org.eclipse.store.gigamap.exceptions.BitmapIndicesException;
import org.eclipse.store.gigamap.types.BinaryIndexerString;
import org.eclipse.store.gigamap.types.BitmapIndex;
import org.eclipse.store.gigamap.types.CustomConstraint;
import org.eclipse.store.gigamap.types.GigaIterator;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.IndexerString;
import org.eclipse.serializer.util.X;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the mutability guard of the index side ({@code BitmapIndices}, {@code CustomConstraints},
 * {@code GigaIndices}).
 * <p>
 * A structural index change must apply exactly the same classification of a read-only hold that an entity
 * write applies: an explicit {@code markReadOnly()}, a structural change during an in-progress iteration and a
 * self-held reader fail fast, while readers that are open on <i>other</i> threads are waited out instead of
 * being reported as an error. Before that was the case, {@code ensure(indexer)} failed unpredictably: it only
 * touches index structure on the first use of a key, so the same logical write succeeded many times and then
 * threw once, the first time a new key coincided with a reader open somewhere else.
 */
public class IndexRegistrationReadOnlyTest
{
	static class Item
	{
		String label;

		Item()
		{
			// required for deserialization
		}

		Item(final String label)
		{
			this.label = label;
		}
	}

	static final IndexerString<Item> LABEL = new IndexerString.Abstract<>()
	{
		@Override
		public String name()
		{
			return "label";
		}

		@Override
		protected String getString(final Item e)
		{
			return e.label;
		}
	};

	static final IndexerString<Item> OTHER = new IndexerString.Abstract<>()
	{
		@Override
		public String name()
		{
			return "other";
		}

		@Override
		protected String getString(final Item e)
		{
			return e.label;
		}
	};

	static final BinaryIndexerString<Item> UNIQUE_LABEL = new BinaryIndexerString.Abstract<>()
	{
		@Override
		public String name()
		{
			return "uniqueLabel";
		}

		@Override
		protected String getString(final Item e)
		{
			return e.label;
		}
	};

	// same name as LABEL but different logic, for BitmapIndices#update
	static final IndexerString<Item> LABEL_FIRST_LETTER = new IndexerString.Abstract<>()
	{
		@Override
		public String name()
		{
			return "label";
		}

		@Override
		protected String getString(final Item e)
		{
			return e.label.substring(0, 1);
		}
	};

	// CustomConstraint.AbstractBase#name() is final -> registered under the simple class name
	static class NoEmptyLabel extends CustomConstraint.AbstractSimple<Item>
	{
		@Override
		public boolean isViolated(final Item entity)
		{
			return entity.label.isEmpty();
		}
	}

	static class NoBlankLabel extends CustomConstraint.AbstractSimple<Item>
	{
		@Override
		public boolean isViolated(final Item entity)
		{
			return entity.label.isBlank();
		}
	}

	private static GigaMap<Item> newMap()
	{
		final GigaMap<Item> map = GigaMap.New();
		map.index().bitmap().ensure(LABEL);
		map.addAll(new Item("a1"), new Item("a2"), new Item("a3"));

		return map;
	}


	///////////////////////////////////////////////////////////////////////////
	// the reported bug //
	/////////////////////

	@Test
	void ensureIndexFromOtherThreadWaitsForOpenReader()
	{
		final GigaMap<Item> map = newMap();

		// Both statements are structural writes against the same GigaMap under the same read-only hold.
		// Before the fix only the second one failed, with
		// "BitmapIndicesException: Cannot modify indices: the parent GigaMap is read-only."
		assertWaitsForForeignReader(map, () -> map.add(new Item("b1")));
		assertWaitsForForeignReader(map, () -> map.index().bitmap().ensure(OTHER));

		assertNotNull(map.index().bitmap().get("other"));
		assertEquals(4, map.size());
		assertEquals(1, map.query(OTHER.is("b1")).count());
	}

	@Test
	void ensureAlreadyPresentIndexDoesNotWaitForOpenReader()
	{
		final GigaMap<Item> map = newMap();

		// The fast path performs no structural change, so it must not even reach the guard - a read-only
		// replica has to be able to run the identical startup schema declaration while readers are open.
		final BitmapIndex<Item, String> index = assertCompletesWithForeignReader(
			map,
			() -> map.index().bitmap().ensure(LABEL)
		);
		assertSame(map.index().bitmap().get("label"), index);
	}


	///////////////////////////////////////////////////////////////////////////
	// the other index-side guards //
	////////////////////////////////

	@Test
	void addIndexFromOtherThreadWaitsForOpenReader()
	{
		final GigaMap<Item> map = newMap();

		assertWaitsForForeignReader(map, () -> map.index().bitmap().add(OTHER));

		// the new index must have been back-filled from the entities that were already present
		assertEquals(1, map.query(OTHER.is("a1")).count());
	}

	@Test
	void removeIndexFromOtherThreadWaitsForOpenReader()
	{
		final GigaMap<Item> map = newMap();
		map.index().bitmap().add(OTHER);

		assertWaitsForForeignReader(map, () -> map.index().bitmap().removeIndex("other"));

		assertNull(map.index().bitmap().get("other"));
	}

	@Test
	void updateIndexerFromOtherThreadWaitsForOpenReader()
	{
		final GigaMap<Item> map = newMap();

		assertWaitsForForeignReader(map, () -> map.index().bitmap().update(LABEL_FIRST_LETTER));

		assertEquals(3, map.query(LABEL_FIRST_LETTER.is("a")).count());
	}

	@Test
	void addUniqueConstraintFromOtherThreadWaitsForOpenReader()
	{
		final GigaMap<Item> map = newMap();

		assertWaitsForForeignReader(map, () -> map.constraints().unique().addUniqueConstraint(UNIQUE_LABEL));

		assertEquals(1, map.index().bitmap().uniqueConstraints().size());
	}

	@Test
	void removeUniqueConstraintFromOtherThreadWaitsForOpenReader()
	{
		final GigaMap<Item> map = newMap();
		map.constraints().unique().addUniqueConstraint(UNIQUE_LABEL);

		assertWaitsForForeignReader(
			map,
			() -> assertTrue(map.index().bitmap().removeUniqueConstraint("uniqueLabel"))
		);

		assertTrue(map.index().bitmap().uniqueConstraints() == null
			|| map.index().bitmap().uniqueConstraints().isEmpty());
	}

	@Test
	void setIdentityIndicesFromOtherThreadWaitsForOpenReader()
	{
		final GigaMap<Item> map = newMap();

		assertWaitsForForeignReader(map, () -> map.index().bitmap().setIdentityIndices(X.Enum(LABEL)));

		assertEquals(1, map.index().bitmap().identityIndices().size());
	}

	@Test
	void addCustomConstraintFromOtherThreadWaitsForOpenReader()
	{
		final GigaMap<Item> map = newMap();

		assertWaitsForForeignReader(map, () -> map.constraints().custom().addConstraint(new NoEmptyLabel()));

		// CustomConstraints has no getter; a successful remove proves the constraint was registered.
		assertTrue(map.constraints().custom().removeConstraint("NoEmptyLabel"));
	}

	@Test
	void ensureCustomConstraintFromOtherThreadWaitsForOpenReader()
	{
		final GigaMap<Item> map = newMap();

		assertWaitsForForeignReader(map, () -> map.constraints().custom().ensureConstraint(new NoBlankLabel()));

		assertTrue(map.constraints().custom().removeConstraint("NoBlankLabel"));
	}

	@Test
	void removeCustomConstraintFromOtherThreadWaitsForOpenReader()
	{
		final GigaMap<Item> map = newMap();
		map.constraints().custom().addConstraint(new NoEmptyLabel());

		assertWaitsForForeignReader(
			map,
			() -> assertTrue(map.constraints().custom().removeConstraint("NoEmptyLabel"))
		);

		assertFalse(map.constraints().custom().removeConstraint("NoEmptyLabel"));
	}

	@Test
	void registerIndexGroupFromOtherThreadWaitsForOpenReader()
	{
		final GigaMap<Item> map = newMap();

		// The bitmap group is already registered, so register(...) is a no-op returning null - but the
		// mutability guard sits in front of that check and must wait rather than throw.
		assertWaitsForForeignReader(
			map,
			() -> assertNull(map.index().register(BitmapIndex.Category()))
		);

		assertNotNull(map.index().bitmap());
	}


	///////////////////////////////////////////////////////////////////////////
	// the fail-fast cases, which must stay fail-fast //
	//////////////////////////////////////////////////

	@Test
	void ensureIndexWhileHoldingOwnReaderThrows()
	{
		final GigaMap<Item> map = newMap();

		assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
		{
			final GigaIterator<Item> reader = map.iterator();
			reader.hasNext();

			// Waiting for a reader the calling thread holds itself would block forever, so this must fail
			// fast - exactly like an entity write does.
			final BitmapIndicesException thrown = assertThrows(
				BitmapIndicesException.class,
				() -> map.index().bitmap().ensure(OTHER)
			);
			assertInstanceOf(IllegalStateException.class, thrown.getCause());
			assertTrue(thrown.getCause().getMessage().contains("Self-deadlock"));

			reader.close();
		});

		assertNull(map.index().bitmap().get("other"));
	}

	@Test
	void ensureIndexDuringIterationThrows()
	{
		final GigaMap<Item> map = newMap();

		// A structural change while an iteration walks the map is not supported and must fail fast, not
		// wait: the iteration's read-only hold is never released by another thread.
		assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
		{
			final BitmapIndicesException thrown = assertThrows(
				BitmapIndicesException.class,
				() -> map.iterate(e -> map.index().bitmap().ensure(OTHER))
			);
			assertInstanceOf(IllegalStateException.class, thrown.getCause());
		});

		assertNull(map.index().bitmap().get("other"));
	}

	@Test
	void ensureIndexOnExplicitlyReadOnlyMapThrows()
	{
		final GigaMap<Item> map = newMap();
		map.markReadOnly();

		// An explicit read-only mark is a deliberate, open-ended state - waiting for it would block until
		// some other thread happens to unmark it, so it must fail fast.
		assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
		{
			final BitmapIndicesException thrown = assertThrows(
				BitmapIndicesException.class,
				() -> map.index().bitmap().ensure(OTHER)
			);
			assertInstanceOf(IllegalStateException.class, thrown.getCause());
			assertTrue(thrown.getCause().getMessage().contains("read only mode"));
		});

		map.unmarkReadOnly();
		assertNull(map.index().bitmap().get("other"));
	}


	///////////////////////////////////////////////////////////////////////////
	// idempotency of ensure() across the wait //
	////////////////////////////////////////////

	@Test
	void concurrentEnsureOfSameIndexerReturnsTheSameIndex()
	{
		final GigaMap<Item> map = newMap();

		final int                              threadCount = 8;
		final CountDownLatch                   start       = new CountDownLatch(1);
		final CountDownLatch                   done        = new CountDownLatch(threadCount);
		final List<BitmapIndex<Item, String>>  results     = new ArrayList<>();
		final AtomicReference<Throwable>       error       = new AtomicReference<>();

		assertTimeoutPreemptively(Duration.ofSeconds(15), () ->
		{
			for(int i = 0; i < threadCount; i++)
			{
				new Thread(() ->
				{
					try
					{
						start.await(5, TimeUnit.SECONDS);
						final BitmapIndex<Item, String> index = map.index().bitmap().ensure(OTHER);
						synchronized(results)
						{
							results.add(index);
						}
					}
					catch(final Throwable t)
					{
						error.compareAndSet(null, t);
					}
					finally
					{
						done.countDown();
					}
				}, "ensure-" + i).start();
			}

			start.countDown();
			assertTrue(done.await(10, TimeUnit.SECONDS), "all ensure() calls must finish");
		});

		assertNull(error.get(), () -> "concurrent ensure() must not fail: " + error.get());
		assertEquals(threadCount, results.size());
		final BitmapIndex<Item, String> registered = map.index().bitmap().get("other");
		results.forEach(index -> assertSame(registered, index));
	}


	///////////////////////////////////////////////////////////////////////////
	// argument validation across the guard //
	/////////////////////////////////////////

	@Test
	void nullIndexerIsRejectedConsistently()
	{
		final GigaMap<Item> map = newMap();

		// The guard builds its message from the indexer's name, so every entry point has to validate the
		// indexer before dereferencing it - otherwise the caller gets a NullPointerException from whichever
		// dereference happens to come first instead of the documented IllegalArgumentException.
		assertThrows(IllegalArgumentException.class, () -> map.index().bitmap().add(null));
		assertThrows(IllegalArgumentException.class, () -> map.index().bitmap().addWithoutInitialization(null));
		assertThrows(IllegalArgumentException.class, () -> map.index().bitmap().ensure(null));
		assertThrows(IllegalArgumentException.class, () -> map.index().bitmap().update(null));
		assertThrows(IllegalArgumentException.class, () -> map.constraints().unique().ensureUniqueConstraint(null));
	}

	@Test
	void ensureConstraintsAcceptsASingleUseIterable()
	{
		final GigaMap<Item> map = newMap();

		// ensureConstraints collects the absent set twice (before and after the mutability guard, which may
		// release the monitor), so it must traverse the passed Iterable exactly once: a single-use one would
		// come up empty on the second pass and the constraint would be silently skipped.
		final Iterable<CustomConstraint<? super Item>> singleUse =
			Stream.<CustomConstraint<? super Item>>of(new NoEmptyLabel())::iterator;

		map.constraints().custom().ensureConstraints(singleUse);

		assertTrue(map.constraints().custom().removeConstraint("NoEmptyLabel"));
	}


	///////////////////////////////////////////////////////////////////////////
	// harness //
	////////////

	/**
	 * Runs {@code operation} on a dedicated thread while another thread holds an open reader on {@code map},
	 * and asserts that it neither throws nor completes: it has to block until the foreign reader is closed,
	 * which is what an entity write does in the same situation.
	 */
	private static void assertWaitsForForeignReader(final GigaMap<Item> map, final Executable operation)
	{
		assertWithForeignReader(map, operation, true);
	}

	/**
	 * Same setup as {@link #assertWaitsForForeignReader}, but asserts the opposite: the operation performs no
	 * structural change and must therefore complete while the foreign reader is still open.
	 */
	private static <T> T assertCompletesWithForeignReader(
		final GigaMap<Item>            map      ,
		final ThrowingSupplier<T>      operation
	)
	{
		final AtomicReference<T> result = new AtomicReference<>();
		assertWithForeignReader(map, () -> result.set(operation.get()), false);

		return result.get();
	}

	private static void assertWithForeignReader(
		final GigaMap<Item> map           ,
		final Executable    operation     ,
		final boolean       expectBlocking
	)
	{
		final CountDownLatch             readerOpened = new CountDownLatch(1);
		final CountDownLatch             releaseReader = new CountDownLatch(1);
		final CountDownLatch             operationDone = new CountDownLatch(1);
		final AtomicReference<Throwable> holderError   = new AtomicReference<>();
		final AtomicReference<Throwable> writerError   = new AtomicReference<>();

		// A different thread holds an open reader, then releases it on signal.
		final Thread holder = new Thread(() ->
		{
			try(final GigaIterator<Item> reader = map.iterator())
			{
				reader.hasNext();
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

		assertTimeoutPreemptively(Duration.ofSeconds(30), () ->
		{
			holder.start();
			assertTrue(readerOpened.await(10, TimeUnit.SECONDS), "the foreign reader must be open");

			writer.start();
			if(expectBlocking)
			{
				// The operation can only get here if it did NOT wait: either it threw (the reported bug) or
				// it went through while a reader was open. Both are wrong, and #writerError names which one.
				assertFalse(
					operationDone.await(500, TimeUnit.MILLISECONDS),
					() -> "the operation must wait for the foreign reader to close, but it finished"
						+ (writerError.get() == null ? " immediately." : " by throwing " + writerError.get())
				);
			}
			else
			{
				assertTrue(
					operationDone.await(5, TimeUnit.SECONDS),
					"the operation performs no structural change and must not wait for the foreign reader"
				);
			}

			releaseReader.countDown();
			assertTrue(
				operationDone.await(10, TimeUnit.SECONDS),
				"the operation must proceed once the foreign reader is closed"
			);

			holder.join(10_000);
		});

		assertNull(holderError.get(), () -> "the reader holder failed: " + holderError.get());
		assertNull(writerError.get(), () -> "the operation failed: " + writerError.get());
	}

	private interface ThrowingSupplier<T>
	{
		T get() throws Throwable;
	}


	///////////////////////////////////////////////////////////////////////////
	// sanity check of the fixture itself //
	///////////////////////////////////////

	@Test
	void openReaderIsActuallyDetectedAsReadOnly()
	{
		final GigaMap<Item> map = newMap();

		assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
		{
			try(final GigaIterator<Item> reader = map.iterator())
			{
				reader.hasNext();
				assertTrue(map.isReadOnly(), "an open reader must put the map into a read-only hold");
			}
			assertFalse(map.isReadOnly());
			assertDoesNotThrow(() -> map.index().bitmap().ensure(OTHER));
		});
	}
}
