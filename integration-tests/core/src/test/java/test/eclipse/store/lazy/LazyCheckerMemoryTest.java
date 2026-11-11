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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.serializer.memory.MemoryStatistics;
import org.eclipse.serializer.memory.MemoryStatisticsProvider;
import org.eclipse.serializer.reference.Lazy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The default Lazy checker decides on the heap usage after the last garbage collection, not on the instantaneous
 * usage including uncollected garbage (concurrent collectors like ZGC let it climb to the committed size).
 * <p>
 * The checker is driven directly with scripted statistics. All references are created once and aged beyond the
 * grace time (1 s). With a 30 s timeout and that age, the age penalty clears from about 0.97 x limit and keeps
 * 0.5 x limit for 30 s, so the class may run long on a slow machine.
 */
public class LazyCheckerMemoryTest
{
	private static final long COMMITTED = 1_000_000L;

	private static Lazy<String> unstoredReference;
	private static Lazy<Object> keptStoredReference;
	private static Lazy<Object> clearedStoredReference;

	private MemoryStatisticsProvider previousProvider;
	private MemoryStatistics         current;

	@BeforeAll
	static void createAgedReferences() throws InterruptedException
	{
		unstoredReference      = Lazy.Reference("payload");
		keptStoredReference    = Lazy.New(new Object(), 1_000_000_001L, null);
		clearedStoredReference = Lazy.New(new Object(), 1_000_000_002L, null);
		Thread.sleep(1_100);
	}

	@BeforeEach
	void installScriptedProvider()
	{
		// JVM-wide: only the test thread gets the scripted values, other threads (e.g. a running global manager) not
		final Thread testThread = Thread.currentThread();
		this.previousProvider = MemoryStatisticsProvider.set(new MemoryStatisticsProvider()
		{
			@Override
			public MemoryStatistics heapMemoryUsage()
			{
				final LazyCheckerMemoryTest test = LazyCheckerMemoryTest.this;
				return Thread.currentThread() == testThread
					? test.current
					: test.previousProvider.heapMemoryUsage()
				;
			}

			@Override
			public MemoryStatistics nonHeapMemoryUsage()
			{
				return LazyCheckerMemoryTest.this.previousProvider.nonHeapMemoryUsage();
			}
		});
	}

	@AfterEach
	void restoreProvider()
	{
		MemoryStatisticsProvider.set(this.previousProvider);
	}

	@Test
	void instantaneousUsageAtCommittedDoesNotClearWhenRetainedUsageIsLow()
	{
		// the ZGC situation: used == committed, but most of it is garbage
		assertFalse(this.needsClearing(checker(), postGc(COMMITTED, COMMITTED * 3 / 10)));
	}

	@Test
	void agePenaltyBelowLimitIsUnchanged()
	{
		assertFalse(this.needsClearing(checker(), postGc(COMMITTED, COMMITTED / 2)));
		assertTrue(this.needsClearing(checker(), postGc(COMMITTED, COMMITTED * 97 / 100)));
	}

	@Test
	void retainedUsageAtLimitClearsImmediately()
	{
		assertTrue(this.needsClearing(checker(), postGc(COMMITTED, COMMITTED)));
	}

	@Test
	void statisticsWithoutPostGcValuesKeepTheFormerBehavior()
	{
		// 3-value statistics (custom providers, platforms without post-GC data): usedAfterLastGc == used
		assertTrue(this.needsClearing(checker(), MemoryStatistics.New(-1, COMMITTED, COMMITTED)));
	}

	@Test
	void timeoutClearing()
	{
		assertTrue(this.needsClearing(Lazy.Checker(1_000L, 1.0), postGc(COMMITTED, 0)));
	}

	@Test
	void disabledMemoryCheckNeverClearsByMemory()
	{
		assertFalse(this.needsClearing(Lazy.CheckerTimeout(10_000L), postGc(COMMITTED, COMMITTED)));
	}

	@Test
	void defaultQuotaLeavesHeadroom()
	{
		// default timeout (about 15 min): the age penalty is negligible, the default quota 0.8 decides
		assertTrue(this.needsClearing(Lazy.Checker(), postGc(COMMITTED, COMMITTED * 85 / 100)));
		assertFalse(this.needsClearing(Lazy.Checker(), postGc(COMMITTED, COMMITTED * 75 / 100)));
	}

	/**
	 * Parallel/Serial: after its last (full) collection the fixed-size old generation (two thirds of the heap) is
	 * 90 % full while the heap as a whole is only half used. Scaled to the committed heap it is above the limit.
	 */
	@Test
	void fullFixedSizeOldGenerationCountsAsFullHeap()
	{
		final long oldMax = COMMITTED * 2 / 3;

		assertTrue(this.needsClearing(Lazy.Checker(), oldGeneration(COMMITTED / 2, oldMax * 9 / 10, oldMax)));
		// the same heap without old-generation data: below the limit
		assertFalse(this.needsClearing(Lazy.Checker(), MemoryStatistics.New(COMMITTED, COMMITTED, COMMITTED / 2)));
		// old generation at 60 %: scaled 0.6 x committed, below the limit
		assertFalse(this.needsClearing(Lazy.Checker(), oldGeneration(COMMITTED / 2, oldMax * 6 / 10, oldMax)));
		// the heap-wide value above the limit decides even if the scaled old generation is lower
		assertTrue(this.needsClearing(Lazy.Checker(), oldGeneration(COMMITTED * 85 / 100, oldMax / 10, oldMax)));
	}

	/**
	 * An old generation that may span the whole heap (G1, generational ZGC: its maximum is the heap maximum) is
	 * covered by the heap-wide value and ignored.
	 */
	@Test
	void oldGenerationSpanningTheHeapIsIgnored()
	{
		assertFalse(this.needsClearing(Lazy.Checker(), oldGeneration(COMMITTED / 2, COMMITTED * 9 / 10, COMMITTED)));
	}

	@Test
	void storedReferencesAreClearedThroughLazyClear()
	{
		// the real clearing path: Lazy.clear(evaluator) consults the checker for stored, loaded references
		final Lazy.Checker checker = checker();

		this.current = postGc(COMMITTED, COMMITTED * 3 / 10);
		checker.beginCheckCycle();
		assertFalse(keptStoredReference.clear((Lazy.ClearingEvaluator)checker));
		checker.endCheckCycle();
		assertNotNull(keptStoredReference.peek());

		this.current = postGc(COMMITTED, COMMITTED);
		checker.beginCheckCycle();
		assertTrue(clearedStoredReference.clear((Lazy.ClearingEvaluator)checker));
		checker.endCheckCycle();
		assertNull(clearedStoredReference.peek());
	}

	private static Lazy.Checker checker()
	{
		return Lazy.Checker(30_000L, 1.0);
	}

	private static MemoryStatistics postGc(final long used, final long usedAfterLastGc)
	{
		return MemoryStatistics.New(-1, COMMITTED, used, usedAfterLastGc, 5, -1, -1);
	}

	private static MemoryStatistics oldGeneration(final long usedAfterLastGc, final long oldUsed, final long oldMax)
	{
		return MemoryStatistics.New(COMMITTED, COMMITTED, usedAfterLastGc, usedAfterLastGc, 5, oldUsed, oldMax);
	}

	private boolean needsClearing(final Lazy.Checker checker, final MemoryStatistics statistics)
	{
		this.current = statistics;
		checker.beginCheckCycle();
		try
		{
			return ((Lazy.ClearingEvaluator)checker).needsClearing(unstoredReference);
		}
		finally
		{
			checker.endCheckCycle();
		}
	}
}
