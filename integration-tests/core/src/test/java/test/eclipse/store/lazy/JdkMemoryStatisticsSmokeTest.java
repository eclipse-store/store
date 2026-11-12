package test.eclipse.store.lazy;

/*-
 * #%L
 * EclipseStore Integration Tests - Core
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.eclipse.serializer.memory.MemoryStatistics;
import org.eclipse.serializer.memory.MemoryStatisticsProvider;
import org.eclipse.serializer.memory.sun.JdkMemoryStatistics;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.ref.Reference;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Smoke test of the heap statistics under the collector the test JVM runs with: the values must be consistent and
 * reading them must never fail. The behavior per collector is not measured here.
 */
public class JdkMemoryStatisticsSmokeTest
{
	@Test
	void heapStatisticsAfterACollectionAreConsistent()
	{
		System.gc();
		final MemoryStatistics statistics = JdkMemoryStatistics.createHeapMemoryStatistics();

		assertTrue(statistics.committed() > 0, "committed: " + statistics.committed());
		assertTrue(statistics.used() > 0, "used: " + statistics.used());
		assertTrue(
			statistics.usedAfterLastGc() > 0 && statistics.usedAfterLastGc() <= statistics.committed(),
			"used after the last GC: " + statistics.usedAfterLastGc() + ", committed: " + statistics.committed()
		);
		// -1: no GC data (runtime without jdk.management); 0: no collection yet (System.gc() disabled by -XX:+DisableExplicitGC)
		assertTrue(statistics.gcCount() >= -1, "GC count: " + statistics.gcCount());

		final long oldMax  = statistics.oldGenerationMax();
		final long oldUsed = statistics.oldGenerationUsedAfterLastGc();
		if(oldMax < 0)
		{
			// single-pool collectors (e.g. Shenandoah) have no old generation
			assertEquals(-1, oldUsed, "old generation used without an old generation");
		}
		else
		{
			assertTrue(oldMax > 0, "old generation maximum: " + oldMax);
			// -1: a pool reports no usage after its collections (supported fallback)
			assertTrue(oldUsed == -1 || oldUsed >= 0 && oldUsed <= oldMax, "old generation used " + oldUsed + " of " + oldMax);
		}
	}

	/**
	 * After 2 s without any collector activity the instantaneous usage counts. The time is measured from the end of
	 * the collection, not from the first query that notices it: a first query long after a collection must already
	 * use the instantaneous usage.
	 */
	@Test
	void firstQueryLongAfterACollectionUsesTheInstantaneousUsage() throws InterruptedException
	{
		System.gc();
		assumeTrue(
			JdkMemoryStatistics.createHeapMemoryStatistics().gcCount() > 0,
			"no collection data (System.gc() disabled or no jdk.management)"
		);

		// a collection without a query afterwards: the query after the wait is the first one to notice it
		System.gc();
		final long collectionsBefore = collectionCount();
		final long usedAfterGc       = heapUsed();

		// data allocated after the collection, so that the instantaneous usage differs from the usage after it
		final List<byte[]> retained = new ArrayList<>();
		for(int i = 0; i < 256; i++)
		{
			retained.add(new byte[64 * 1024]);
		}
		// G1 shows small allocations in the instantaneous usage only coarsely
		assumeTrue(heapUsed() > usedAfterGc + (4L << 20), "allocation not visible in the instantaneous usage");

		Thread.sleep(2_500);
		assumeTrue(collectionCount() == collectionsBefore, "collector activity during the wait");

		final MemoryStatistics statistics = JdkMemoryStatistics.createHeapMemoryStatistics();
		assertEquals(statistics.used(), statistics.usedAfterLastGc(), "2.5 s after the last collection");
		Reference.reachabilityFence(retained);
	}

	private static long heapUsed()
	{
		return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
	}

	// read from the beans directly: a query of the statistics would notice the collection before the wait
	private static long collectionCount()
	{
		long count = 0;
		for(final GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans())
		{
			count += Math.max(0, gc.getCollectionCount());
		}
		return count;
	}

	@Test
	void statisticsOfTheActiveProviderAndNonHeapDoNotFail()
	{
		final MemoryStatistics heap = MemoryStatisticsProvider.get().heapMemoryUsage();
		assertTrue(heap.committed() > 0);
		assertTrue(JdkMemoryStatistics.createNonHeapMemoryStatistics().used() > 0);
	}

	@Test
	void statisticsWithoutPostGcValuesUseTheInstantaneousUsage()
	{
		final MemoryStatistics statistics = MemoryStatistics.New(100, 80, 50);

		assertEquals(50, statistics.usedAfterLastGc());
		assertEquals(-1, statistics.gcCount());
		assertEquals(-1, statistics.oldGenerationUsedAfterLastGc());
		assertEquals(-1, statistics.oldGenerationMax());
	}
}
