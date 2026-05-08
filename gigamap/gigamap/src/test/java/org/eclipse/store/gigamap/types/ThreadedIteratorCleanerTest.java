package org.eclipse.store.gigamap.types;

/*-
 * #%L
 * EclipseStore GigaMap
 * %%
 * Copyright (C) 2023 - 2025 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Verifies that {@link ThreadedIterator}'s off-heap registry is released by
 * the {@link java.lang.ref.Cleaner} once the iterator becomes phantom-reachable,
 * instead of waiting for the deprecated {@code FinalizerThread}.
 * <p>
 * Regression guard against the {@code Cleanup} state holder accidentally
 * capturing the enclosing {@code ThreadedIterator} (e.g. via a non-static
 * inner class or an instance method reference). If it did, the wrapper
 * would never become unreachable and the cleaner would never fire — this
 * test would then time out.
 */
class ThreadedIteratorCleanerTest
{
	private static final long CLEANUP_TIMEOUT_NS = TimeUnit.SECONDS.toNanos(5);
	private static final long POLL_INTERVAL_MS   = 50;

	@Test
	void cleanerReleasesRegistryAfterGc() throws Exception
	{
		final Field cleanupField = ThreadedIterator.class.getDeclaredField("cleanup");
		cleanupField.setAccessible(true);
		final Field addressField = cleanupField.getType().getDeclaredField("address");
		addressField.setAccessible(true);

		// Build the iterator + capture the Cleanup state holder inside a helper
		// so the iterator local falls out of scope cleanly when the helper returns.
		// The wrapping is removed from activeReaders by the explicit close()
		// inside the helper, so the underlying ThreadedIterator becomes
		// phantom-reachable once the iterator local is gone.
		final Object cleanup = createIteratorAndCaptureCleanup(cleanupField);

		assertNotEquals(0L, addressField.getLong(cleanup),
			"Pre-condition: registry must be allocated before GC");

		final long deadline = System.nanoTime() + CLEANUP_TIMEOUT_NS;
		while(System.nanoTime() < deadline)
		{
			System.gc();
			if(addressField.getLong(cleanup) == 0L)
			{
				return;
			}
			Thread.sleep(POLL_INTERVAL_MS);
		}

		fail("ThreadedIterator cleanup did not run within "
			+ TimeUnit.NANOSECONDS.toSeconds(CLEANUP_TIMEOUT_NS)
			+ "s — either the Cleanup is capturing the iterator instance or GC has not "
			+ "reclaimed it yet.");
	}

	private static Object createIteratorAndCaptureCleanup(final Field cleanupField) throws Exception
	{
		final GigaMap<Person> map = GigaMap.New();
		final PersonNameIndexer nameIndexer = new PersonNameIndexer();
		map.index().bitmap().add(nameIndexer);
		for(int i = 0; i < 1000; i++)
		{
			map.add(new Person("Person" + i));
		}

		// Pooling + Fixed(2) forces the multithreaded path in
		// GigaMap.Default.createIterator → new ThreadedIterator(...).
		final IterationThreadProvider provider = IterationThreadProvider.Pooling(
			4, ThreadCountProvider.Fixed(2)
		);

		final GigaIterator<Person> iter = map.query(provider).and(nameIndexer.is("Person1")).iterator();
		final ThreadedIterator threaded = extractThreadedIterator(iter);
		final Object cleanup = cleanupField.get(threaded);

		// Explicit close removes the wrapping from activeReaders and disposes
		// worker threads, so by the time we return the iterator is no longer
		// pinned and no thread is using the registry.
		iter.close();

		return cleanup;
	}

	private static ThreadedIterator extractThreadedIterator(final GigaIterator<?> iter) throws Exception
	{
		// GigaIterator.Wrapping holds the underlying ResultIdIterator in its `idIterator` field.
		final Field idIteratorField = iter.getClass().getDeclaredField("idIterator");
		idIteratorField.setAccessible(true);
		final Object idIterator = idIteratorField.get(iter);
		if(!(idIterator instanceof ThreadedIterator))
		{
			throw new AssertionError("Expected GigaIterator.Wrapping over a ThreadedIterator, got "
				+ (idIterator == null ? "null" : idIterator.getClass().getName()));
		}
		return (ThreadedIterator)idIterator;
	}

	private static final class Person
	{
		final String name;

		Person(final String name)
		{
			this.name = name;
		}
	}

	private static final class PersonNameIndexer extends IndexerString.Abstract<Person>
	{
		@Override
		protected String getString(final Person entity)
		{
			return entity.name;
		}
	}
}
