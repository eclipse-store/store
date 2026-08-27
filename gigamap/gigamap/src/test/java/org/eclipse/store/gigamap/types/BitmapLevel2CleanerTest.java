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
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Verifies that {@link BitmapLevel2}'s {@link java.lang.ref.Cleaner} registration
 * actually releases the off-heap region after the wrapper becomes unreachable.
 * <p>
 * This is the regression guard against the original {@code finalize()} pattern
 * and against future refactors that accidentally make the cleanup state hold a
 * strong reference back to the {@code BitmapLevel2} wrapper. If the cleanup
 * captured {@code this}, the wrapper would never become phantom-reachable and
 * the cleaner would never run — this test would then fail with the timeout.
 */
class BitmapLevel2CleanerTest
{
	private static final int  INSTANCE_COUNT     = 200;
	private static final long CLEANUP_TIMEOUT_NS = TimeUnit.SECONDS.toNanos(5);
	private static final long POLL_INTERVAL_MS   = 50;

	@Test
	void cleanerReleasesOffHeapMemoryAfterGc() throws Exception
	{
		final Field cleanupField = BitmapLevel2.class.getDeclaredField("cleanup");
		cleanupField.setAccessible(true);

		final Field addressField = cleanupField.getType().getDeclaredField("address");
		addressField.setAccessible(true);

		// Allocate, then drop strong refs to the wrappers; only the cleanup
		// state-holders are retained so we can later inspect their address.
		final List<Object> cleanups = createInstancesAndCaptureCleanups(cleanupField, INSTANCE_COUNT);

		final long deadline = System.nanoTime() + CLEANUP_TIMEOUT_NS;
		while(System.nanoTime() < deadline)
		{
			System.gc();
			if(allAddressesZero(cleanups, addressField))
			{
				return;
			}
			Thread.sleep(POLL_INTERVAL_MS);
		}

		final long stillAlive = countNonZero(cleanups, addressField);
		fail(stillAlive + " of " + INSTANCE_COUNT
			+ " BitmapLevel2 cleanups did not run within "
			+ TimeUnit.NANOSECONDS.toSeconds(CLEANUP_TIMEOUT_NS) + "s — "
			+ "either the cleanup state holds a strong reference back to BitmapLevel2 "
			+ "or the GC has not reclaimed the wrappers yet.");
	}

	// Isolated in a helper so the wrapper locals fall out of scope cleanly on return.
	private static List<Object> createInstancesAndCaptureCleanups(
		final Field cleanupField,
		final int   count
	) throws IllegalAccessException
	{
		final List<Object> cleanups = new ArrayList<>(count);
		for(int i = 0; i < count; i++)
		{
			final BitmapLevel2 bm = BitmapLevel2.New(i);
			cleanups.add(cleanupField.get(bm));
		}
		return cleanups;
	}

	private static boolean allAddressesZero(final List<Object> cleanups, final Field addressField)
	{
		for(final Object cleanup : cleanups)
		{
			if(readAddress(cleanup, addressField) != 0L)
			{
				return false;
			}
		}
		return true;
	}

	private static long countNonZero(final List<Object> cleanups, final Field addressField)
	{
		long count = 0;
		for(final Object cleanup : cleanups)
		{
			if(readAddress(cleanup, addressField) != 0L)
			{
				count++;
			}
		}
		return count;
	}

	private static long readAddress(final Object cleanup, final Field addressField)
	{
		try
		{
			return addressField.getLong(cleanup);
		}
		catch(final IllegalAccessException e)
		{
			throw new AssertionError(e);
		}
	}

	/**
	 * Regression guard for the deserialization path
	 * ({@link BinaryHandlerBitmapLevel2#create} → {@link BinaryHandlerBitmapLevel2#updateState}).
	 * <p>
	 * The handler builds a {@code BitmapLevel2} in two steps: first a placeholder
	 * wrapper with address 0, then the real off-heap region is published via
	 * {@code instance.setLevel2Address(...)}. If that final write bypasses the
	 * setter (and writes the field directly), the Cleaner state stays at 0, the
	 * cleaner action becomes a no-op, and every deserialised-then-discarded
	 * {@code BitmapLevel2} silently leaks its region.
	 * <p>
	 * This test enforces three invariants:
	 * <ol>
	 * <li>Construction with a placeholder address initialises the cleanup state to that placeholder.</li>
	 * <li>{@code setLevel2Address} propagates the new address to the cleanup state.</li>
	 * <li>After the wrapper becomes unreachable, the cleaner releases the
	 *     post-setter address (proves the cleaner actually saw the propagated value).</li>
	 * </ol>
	 */
	@Test
	void setLevel2AddressPropagatesToCleanupAndCleanerReleasesNewAddress() throws Exception
	{
		final Field cleanupField = BitmapLevel2.class.getDeclaredField("cleanup");
		cleanupField.setAccessible(true);
		final Field addressField = cleanupField.getType().getDeclaredField("address");
		addressField.setAccessible(true);

		final Method allocateNew = BitmapLevel2.class.getDeclaredMethod("allocateNew", int.class);
		allocateNew.setAccessible(true);

		final long realAddress = (long)allocateNew.invoke(null, 0);

		final Object cleanup = simulateHandlerLifecycle(cleanupField, addressField, realAddress);

		final long deadline = System.nanoTime() + CLEANUP_TIMEOUT_NS;
		while(System.nanoTime() < deadline)
		{
			System.gc();
			if(readAddress(cleanup, addressField) == 0L)
			{
				return;
			}
			Thread.sleep(POLL_INTERVAL_MS);
		}

		fail("Cleaner did not release the address published via setLevel2Address (addr="
			+ realAddress + ") within "
			+ TimeUnit.NANOSECONDS.toSeconds(CLEANUP_TIMEOUT_NS) + "s");
	}

	// Isolated so the BitmapLevel2 wrapper local falls out of scope on return.
	private static Object simulateHandlerLifecycle(
		final Field cleanupField,
		final Field addressField,
		final long  realAddress
	) throws IllegalAccessException
	{
		// Mimic BinaryHandlerBitmapLevel2.create(): wrapper with placeholder address 0.
		final BitmapLevel2 bm      = new BitmapLevel2(false, 0L);
		final Object       cleanup = cleanupField.get(bm);
		assertEquals(0L, addressField.getLong(cleanup),
			"Pre-condition: cleanup state must mirror the constructor argument");

		// Mimic BinaryHandlerBitmapLevel2.updateState() final line.
		bm.setLevel2Address(realAddress);
		assertEquals(realAddress, addressField.getLong(cleanup),
			"setLevel2Address must propagate to cleanup.address — without this, "
			+ "BinaryHandlerBitmapLevel2.updateState would leak the deserialised region.");

		return cleanup;
	}
}
