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

import org.eclipse.serializer.memory.MemoryAccessor;
import org.eclipse.serializer.memory.MemorySizeProperties;
import org.eclipse.serializer.memory.XMemory;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Compressing a {@link BitmapLevel2} block during a store must leave the current block intact if it fails, because
 * the current block stays in use. Otherwise queries return wrong entities, and the next successful store persists
 * the damage.
 * <p>
 * The failure is injected through a memory accessor that delegates to the original one and throws once at the
 * chosen point, only on the test's own thread.
 */
public class BitmapCompressFailureTest
{
	static final int LEVEL1_ID_COUNT = 4096;
	static final int SEGMENTS        = 5;
	static final int KEYS            = 3;

	enum FailurePoint
	{
		COPIES_ALLOCATION   ("allocateMemory", "copyStandaloneSegments", true ),
		COPIES_FILL         ("copyRange"     , "copyStandaloneSegments", false),
		NEW_BLOCK_ALLOCATION("allocateMemory", "allocateLevel2Block"   , true ),
		NEW_BLOCK_FILL      ("copyRange"     , "copyCompressedData"    , false);

		final String  accessorMethod;
		final String  level2Method  ;
		final boolean outOfMemory   ;

		FailurePoint(final String accessorMethod, final String level2Method, final boolean outOfMemory)
		{
			this.accessorMethod = accessorMethod;
			this.level2Method   = level2Method  ;
			this.outOfMemory    = outOfMemory   ;
		}

		Throwable createFailure()
		{
			return this.outOfMemory
				? new OutOfMemoryError("injected at " + this)
				: new IllegalStateException("injected at " + this)
			;
		}
	}

	@Test
	void failedCopiesAllocation(@TempDir final Path dir)
	{
		assertFailedCompressLeavesIndexIntact(FailurePoint.COPIES_ALLOCATION, dir);
	}

	@Test
	void failureWhileFillingCopies(@TempDir final Path dir)
	{
		assertFailedCompressLeavesIndexIntact(FailurePoint.COPIES_FILL, dir);
	}

	@Test
	void failedNewBlockAllocation(@TempDir final Path dir)
	{
		assertFailedCompressLeavesIndexIntact(FailurePoint.NEW_BLOCK_ALLOCATION, dir);
	}

	@Test
	void failureWhileFillingNewBlock(@TempDir final Path dir)
	{
		assertFailedCompressLeavesIndexIntact(FailurePoint.NEW_BLOCK_FILL, dir);
	}

	private static void assertFailedCompressLeavesIndexIntact(final FailurePoint point, final Path dir)
	{
		final long expected;
		try(Injection injection = new Injection(point))
		{
			final GigaMap<Entity> map = newMap();
			try(EmbeddedStorageManager storage = EmbeddedStorage.start(map, dir))
			{
				expected = fillAndMakeStandalone(map);

				injection.arm();
				Throwable storeFailure = null;
				try
				{
					map.store();
				}
				catch(final Throwable t)
				{
					storeFailure = t;
				}
				injection.disarm();

				assertNotNull(injection.injected, "precondition: the failure was injected at " + point);
				assertSame(injection.injected, rootCause(storeFailure), "precondition: the store failed on the injection");
				assertEquals(expected, countKey0(map), "in memory after the failed store");
				assertEquals(0, injection.leakedAddresses().size(), "native memory allocated by the failed compress");

				map.store();
			}
		}
		assertEquals(expected, countKey0AfterRestart(dir), "after a retried store and a restart");
	}

	/**
	 * Key 0 is set on every third id in the first half of each level1 segment, so its segments mix trivial and
	 * non-trivial values. Compressing such a segment moves its data; a segment of non-trivial values only would
	 * compress to the same bytes and could not show damage.
	 * One removal per segment afterwards makes every key 0 segment standalone again, so the next store compresses.
	 */
	private static long fillAndMakeStandalone(final GigaMap<Entity> map)
	{
		long keyZero = 0;
		for(int i = 0; i < SEGMENTS * LEVEL1_ID_COUNT; i++)
		{
			final boolean zero = i % LEVEL1_ID_COUNT < LEVEL1_ID_COUNT / 2 && i % KEYS == 0;
			keyZero += zero ? 1 : 0;
			map.add(new Entity(zero ? 0 : 1));
		}
		map.store();

		for(int s = 0; s < SEGMENTS; s++)
		{
			final long base = (long)s * LEVEL1_ID_COUNT + 10;
			map.removeById(base + (KEYS - base % KEYS) % KEYS); // the first key 0 id after base
		}
		final long expected = keyZero - SEGMENTS;
		assertEquals(expected, countKey0(map), "precondition: key 0 count before the failing store");

		return expected;
	}

	private static Throwable rootCause(final Throwable t)
	{
		Throwable cause = t;
		while(cause != null && cause.getCause() != null && cause.getCause() != cause)
		{
			cause = cause.getCause();
		}
		return cause;
	}

	private static GigaMap<Entity> newMap()
	{
		final GigaMap<Entity> map = GigaMap.New();
		map.index().bitmap().add(new KeyIndexer());
		return map;
	}

	private static long countKey0(final GigaMap<Entity> map)
	{
		return map.query(new KeyIndexer(), 0).count();
	}

	@SuppressWarnings("unchecked")
	private static long countKey0AfterRestart(final Path dir)
	{
		try(EmbeddedStorageManager storage = EmbeddedStorage.start(dir))
		{
			return countKey0((GigaMap<Entity>)storage.root());
		}
	}

	/**
	 * Replaces the global memory accessor while open. While armed, it throws once at the failure point and records
	 * the native memory allocated and freed by {@code BitmapLevel2.compress}, both only on the arming thread.
	 */
	static final class Injection implements AutoCloseable
	{
		final MemoryAccessor original;
		final FailurePoint   point   ;
		final Set<Long>      allocated = new HashSet<>();
		final Set<Long>      freed     = new HashSet<>();

		volatile Thread armedThread;
		Throwable       injected   ;

		Injection(final FailurePoint point)
		{
			this.point    = point;
			this.original = XMemory.memoryAccessor();
			// the JDK default accessor provides the size properties as well, so it can be restored as it was
			assumeTrue(this.original instanceof MemorySizeProperties, "memory accessor without size properties");

			final MemoryAccessor proxy = (MemoryAccessor)Proxy.newProxyInstance(
				MemoryAccessor.class.getClassLoader(),
				new Class<?>[]{MemoryAccessor.class},
				(p, method, args) -> this.invoke(method.getName(), method, args)
			);
			XMemory.setMemoryHandling(proxy, (MemorySizeProperties)this.original);
		}

		private Object invoke(final String name, final Method method, final Object[] args)
			throws Throwable
		{
			final boolean watched = Thread.currentThread() == this.armedThread
				&& (name.equals("allocateMemory") || name.equals("freeMemory") || name.equals(this.point.accessorMethod))
				&& isCalledFrom("compress")
			;
			if(watched && this.injected == null && name.equals(this.point.accessorMethod)
				&& isCalledFrom(this.point.level2Method)
			)
			{
				throw this.injected = this.point.createFailure();
			}
			if(watched && name.equals("freeMemory"))
			{
				this.freed.add((Long)args[0]);
			}

			final Object result;
			try
			{
				result = method.invoke(this.original, args);
			}
			catch(final InvocationTargetException e)
			{
				throw e.getCause();
			}

			if(watched && name.equals("allocateMemory"))
			{
				this.allocated.add((Long)result);
			}
			return result;
		}

		private static boolean isCalledFrom(final String level2Method)
		{
			return StackWalker.getInstance().walk(frames -> frames.anyMatch(f ->
				f.getClassName().equals(BitmapLevel2.class.getName())
				&& f.getMethodName().equals(level2Method)
			));
		}

		void arm()
		{
			this.armedThread = Thread.currentThread();
		}

		void disarm()
		{
			this.armedThread = null;
		}

		Set<Long> leakedAddresses()
		{
			final Set<Long> leaked = new HashSet<>(this.allocated);
			leaked.removeAll(this.freed);
			return leaked;
		}

		@Override
		public void close()
		{
			XMemory.setMemoryHandling(this.original, (MemorySizeProperties)this.original);
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
