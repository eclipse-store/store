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

import org.eclipse.serializer.memory.MemoryStatistics;
import org.eclipse.serializer.memory.MemoryStatisticsProvider;
import org.eclipse.serializer.memory.sun.JdkMemoryStatistics;

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
			assertTrue(oldUsed >= 0 && oldUsed <= oldMax, "old generation used " + oldUsed + " of " + oldMax);
		}
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
