
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Verifies that {@link ThreadedIterator}'s {@code close()} releases its off-heap
 * registry synchronously via {@link java.lang.ref.Cleaner.Cleanable#clean()},
 * rather than waiting for the FinalizerThread to run.
 * <p>
 * Regression guard for the §3e improvement of the Phase 2 finalize→Cleaner
 * refactor. Before that change, {@code ThreadedIterator.close()} only disposed
 * worker threads — segments were freed exclusively by {@code finalize()}.
 */
class ThreadedIteratorCleanerTest
{
	@Test
	void closeReleasesRegistrySynchronously() throws Exception
	{
		final GigaMap<Person> map = GigaMap.New();
		final PersonNameIndexer nameIndexer = new PersonNameIndexer();
		map.index().bitmap().add(nameIndexer);
		for(int i = 0; i < 1000; i++)
		{
			map.add(new Person("Person" + i));
		}

		// Pooling provider with Fixed(2) threads forces the multithreaded path
		// in GigaMap.Default.createIterator → new ThreadedIterator(...).
		final IterationThreadProvider provider = IterationThreadProvider.Pooling(
			4, ThreadCountProvider.Fixed(2)
		);

		final GigaIterator<Person> iter = map.query(provider).and(nameIndexer.is("Person1")).iterator();

		// The query path with a non-null condition + NoOp idMatcher + threadCount > 0
		// returns a GigaIterator.Wrapping wrapping a ThreadedIterator.
		final ThreadedIterator threaded = extractThreadedIterator(iter);
		assertNotNull(threaded, "Expected GigaIterator.Wrapping wrapping a ThreadedIterator");

		final Field cleanupField = ThreadedIterator.class.getDeclaredField("cleanup");
		cleanupField.setAccessible(true);
		final Object cleanup = cleanupField.get(threaded);

		final Field addressField = cleanup.getClass().getDeclaredField("address");
		addressField.setAccessible(true);

		final long addressBeforeClose = addressField.getLong(cleanup);
		assertNotEquals(0L, addressBeforeClose,
			"Pre-condition: registry must be allocated before close()");

		iter.close();

		final long addressAfterClose = addressField.getLong(cleanup);
		assertEquals(0L, addressAfterClose,
			"close() must release the registry synchronously via cleanable.clean(); "
			+ "if this fails, ThreadedIterator.close() is leaking segments to the FinalizerThread.");

		// cleanable.clean() is at-most-once; second close must be a no-op.
		assertDoesNotThrow(iter::close, "close() must be idempotent");
	}

	private static ThreadedIterator extractThreadedIterator(final GigaIterator<?> iter) throws Exception
	{
		// GigaIterator.Wrapping holds the underlying ResultIdIterator in its `idIterator` field.
		final Field idIteratorField = iter.getClass().getDeclaredField("idIterator");
		idIteratorField.setAccessible(true);
		final Object idIterator = idIteratorField.get(iter);
		return idIterator instanceof ThreadedIterator
			? (ThreadedIterator)idIterator
			: null;
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
