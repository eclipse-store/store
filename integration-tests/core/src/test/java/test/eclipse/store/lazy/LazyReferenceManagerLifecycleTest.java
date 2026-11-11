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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.serializer.memory.MemoryStatistics;
import org.eclipse.serializer.memory.MemoryStatisticsProvider;
import org.eclipse.serializer.monitoring.MonitoringManager;
import org.eclipse.serializer.reference.Lazy;
import org.eclipse.serializer.reference.LazyReferenceManager;
import org.eclipse.serializer.reference._longReference;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;

import java.lang.ref.Reference;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Lifecycle of the LazyReferenceManager: replacing the global manager, stopping and restarting its thread, and
 * keeping the thread alive on failures. Clearing is made immediate by a check that clears every stored reference,
 * so no test waits for timeouts; cleanup threads run with short intervals.
 * <p>
 * Statistics are scripted for the cleanup threads created by a test only (thread name prefix, not existing before),
 * other threads get the previous provider.
 */
public class LazyReferenceManagerLifecycleTest
{
	private static final String THREAD_PREFIX = LazyReferenceManager.class.getSimpleName() + "@";
	private static final long   DEADLINE_MS   = 3_000;

	static final class Root
	{
		Lazy<byte[]> data;
	}

	@TempDir
	Path dir;

	private final AtomicInteger              statisticsCalls   = new AtomicInteger();
	private final AtomicReference<Throwable> statisticsFailure = new AtomicReference<>();
	private volatile long                    statisticsSleepMs;

	private Set<Thread>              preexistingThreads;
	private MemoryStatisticsProvider previousProvider;
	private LazyReferenceManager     previousManager;
	private EmbeddedStorageManager   storage;

	@BeforeEach
	void installScriptedProvider()
	{
		this.previousManager    = LazyReferenceManager.get();
		this.preexistingThreads = new HashSet<>(Thread.getAllStackTraces().keySet());
		final Thread testThread = Thread.currentThread();
		this.previousProvider = MemoryStatisticsProvider.set(new MemoryStatisticsProvider()
		{
			@Override
			public MemoryStatistics heapMemoryUsage()
			{
				final LazyReferenceManagerLifecycleTest test = LazyReferenceManagerLifecycleTest.this;
				final Thread                            thread = Thread.currentThread();
				if(thread != testThread && !test.isOwnCleanupThread(thread))
				{
					return test.previousProvider.heapMemoryUsage();
				}
				test.statisticsCalls.incrementAndGet();
				final Throwable failure = test.statisticsFailure.get();
				if(failure instanceof RuntimeException)
				{
					throw (RuntimeException)failure;
				}
				if(failure instanceof Error)
				{
					throw (Error)failure;
				}
				sleep(test.statisticsSleepMs);
				return MemoryStatistics.New(-1, 1_000_000, 1_000);
			}

			@Override
			public MemoryStatistics nonHeapMemoryUsage()
			{
				return LazyReferenceManagerLifecycleTest.this.previousProvider.nonHeapMemoryUsage();
			}
		});
	}

	@AfterEach
	void restore()
	{
		try
		{
			if(this.storage != null && this.storage.isRunning())
			{
				this.storage.shutdown();
			}
		}
		finally
		{
			MemoryStatisticsProvider.set(this.previousProvider);
			if(LazyReferenceManager.get() != this.previousManager)
			{
				/*
				 * A fresh default manager instead of the previous one: set() hands the running state over, and the
				 * previous manager would take over its own references a second time (registerAll does not deduplicate).
				 */
				LazyReferenceManager.set(LazyReferenceManager.New());
			}
		}
	}

	@Test
	void setWhileStorageRunsHandsOverAndKeepsClearing()
	{
		final Root root = new Root();
		root.data = Lazy.Reference(new byte[1024]);
		this.storage = EmbeddedStorage.start(root, this.dir);

		final LazyReferenceManager previous = LazyReferenceManager.get();
		assertTrue(previous.isRunning(), "precondition: the storage start runs the global manager");

		final LazyReferenceManager replacement = clearingManager(10);
		assertSame(previous, LazyReferenceManager.set(replacement));
		assertTrue(replacement.isRunning(), "the new manager takes over the running state");
		assertFalse(previous.isRunning(), "the previous manager is stopped");

		awaitTrue(() -> !root.data.isLoaded(), "the new manager must clear the stored reference");
		assertEquals(1024, root.data.get().length, "the cleared reference must reload");
	}

	@Test
	void setWithoutRunningPredecessorDoesNotStart()
	{
		final LazyReferenceManager stopped = clearingManager(10);
		LazyReferenceManager.set(stopped);
		assertFalse(stopped.isRunning(), "precondition: nothing runs the global manager");

		final LazyReferenceManager replacement = clearingManager(10);
		LazyReferenceManager.set(replacement);
		assertFalse(replacement.isRunning(), "without a running predecessor the start stays with the storage");
	}

	/**
	 * A storage's controller waits for the storage state lock, which a storage start holds for its whole duration.
	 * A set() meanwhile must not block the creation of lazy references in other threads.
	 */
	@Test
	void setWaitingForAControllerDoesNotBlockLazyCreation() throws InterruptedException
	{
		final CountDownLatch          entered      = new CountDownLatch(1);
		final CountDownLatch          release      = new CountDownLatch(1);
		final AtomicReference<Thread> setterThread = new AtomicReference<>();
		// blocks only the thread calling set(): the running manager's own cleanup thread asks controllers, too
		final LazyReferenceManager.Controller slow = () ->
		{
			if(Thread.currentThread() == setterThread.get())
			{
				entered.countDown();
				awaitQuietly(release);
			}
			return true;
		};
		final LazyReferenceManager running = clearingManager(1_000_000).start();
		// first in the controller chain: controllers taken over from earlier managers are asked after it
		running.addController(slow);
		LazyReferenceManager.set(running);

		final Thread setter = new Thread(() -> LazyReferenceManager.set(clearingManager(1_000_000)));
		setterThread.set(setter);
		setter.start();
		try
		{
			assertTrue(entered.await(5, TimeUnit.SECONDS), "precondition: set() asks the controller");

			final Thread creator = new Thread(() -> Lazy.Reference(new Object()));
			creator.start();
			creator.join(2_000);
			assertFalse(creator.isAlive(), "Lazy creation blocked by set()");
		}
		finally
		{
			release.countDown();
			setter.join(5_000);
			// set() passes controllers on to the next manager: remove it everywhere, it would keep managers running
			running.removeController(slow);
			LazyReferenceManager.get().removeController(slow);
		}
	}

	/**
	 * A Lazy instance may get the global manager just before a set() and register after the takeover: the replaced
	 * manager forwards it, else it would stay with the stopped manager alone and never be cleared.
	 */
	@Test
	void registrationAtTheReplacedManagerIsForwarded()
	{
		final LazyReferenceManager first  = clearingManager(1_000_000);
		final LazyReferenceManager second = clearingManager(1_000_000);
		LazyReferenceManager.set(first);
		LazyReferenceManager.set(second);

		// registered at the global manager (second) by its creation already
		final Lazy<Object> late   = Lazy.New(new Object(), 3_750_000_000L, null);
		final long         before = count(second, late);
		first.register(late);

		assertEquals(before + 1, count(second, late), "the registration must reach the new global manager");
		assertEquals(0, count(first, late), "the replaced manager must not keep it");
	}

	/**
	 * A replaced manager that becomes global again registers itself instead of forwarding, so that two managers never
	 * forward to each other.
	 */
	@Test
	void managerGlobalAgainDoesNotForward()
	{
		final LazyReferenceManager first  = clearingManager(1_000_000);
		final LazyReferenceManager second = clearingManager(1_000_000);
		LazyReferenceManager.set(first);
		LazyReferenceManager.set(second);
		LazyReferenceManager.set(first);

		// registered at the global manager (first) by their creation already
		final Lazy<Object> direct          = Lazy.New(new Object(), 3_760_000_000L, null);
		final Lazy<Object> forwarded       = Lazy.New(new Object(), 3_760_000_001L, null);
		final long         directBefore    = count(first, direct);
		final long         forwardedBefore = count(first, forwarded);
		first.register(direct);
		second.register(forwarded);

		assertEquals(directBefore + 1, count(first, direct), "the global manager must register, not forward");
		assertEquals(forwardedBefore + 1, count(first, forwarded), "the replaced manager forwards to the global one");
		assertEquals(0, count(second, forwarded), "the replaced manager must not keep it");
	}

	@Test
	void settingTheGlobalManagerAgainIsRejected()
	{
		final LazyReferenceManager manager = clearingManager(1_000_000);
		LazyReferenceManager.set(manager);

		assertThrows(IllegalArgumentException.class, () -> LazyReferenceManager.set(manager));

		// registered at the global manager by its creation already; forwarding to itself would recurse endlessly
		final Lazy<Object> reference = Lazy.New(new Object(), 3_770_000_000L, null);
		final long         before    = count(manager, reference);
		manager.register(reference);
		assertEquals(before + 1, count(manager, reference), "the rejected set() must leave the manager registering");
	}

	/**
	 * Like a failing cycle, an Error from the interval provider (e.g. an OutOfMemoryError) must not end the thread.
	 */
	@Test
	void errorFromTheIntervalProviderDoesNotKillTheThread()
	{
		final AtomicInteger        intervalCalls = new AtomicInteger();
		final List<Lazy<Object>>   references    = storedReferences(1, 3_780_000_000L);
		final LazyReferenceManager manager       = LazyReferenceManager.New(
			Lazy.Checker((lazy, statistics, timeout) -> Boolean.TRUE),
			() ->
			{
				if(intervalCalls.incrementAndGet() == 1)
				{
					throw new OutOfMemoryError("simulated");
				}
				return 10;
			},
			_longReference.New(1_000_000),
			MonitoringManager.Disabled()
		);
		references.forEach(manager::register);
		manager.start();
		try
		{
			awaitTrue(() -> intervalCalls.get() >= 1, "precondition: the interval provider was asked");
			sleep(100);
			assertTrue(manager.isRunning(), "the manager must still run");
			assertEquals(1, this.ownCleanupThreads().size(), "the cleanup thread must still be alive");
		}
		finally
		{
			manager.stop();
			Reference.reachabilityFence(references);
		}
	}

	private static long count(final LazyReferenceManager manager, final Lazy<?> reference)
	{
		final long[] count = new long[1];
		manager.iterate(lazy ->
		{
			if(lazy == reference)
			{
				count[0]++;
			}
		});
		return count[0];
	}

	@Test
	void stopAndImmediateStartLeaveOneThread()
	{
		final LazyReferenceManager manager = clearingManager(20).start();
		try
		{
			awaitTrue(() -> this.ownCleanupThreads().size() == 1, "precondition: one cleanup thread");
			manager.stop();
			manager.start();
			awaitTrue(() -> this.ownCleanupThreads().size() == 1, "exactly one cleanup thread after stop and start");
			sleep(100);
			assertEquals(1, this.ownCleanupThreads().size(), "cleanup threads of this manager");
		}
		finally
		{
			manager.stop();
		}
	}

	@Test
	void managerStoppedByItsControllerCanBeStartedAgain()
	{
		final AtomicBoolean                   allowed    = new AtomicBoolean(true);
		final List<Lazy<Object>>              references = storedReferences(10, 3_500_000_000L);
		final LazyReferenceManager            manager    = clearingManager(10);
		// controllers are held weakly: this local keeps it reachable until the end (see finally)
		final LazyReferenceManager.Controller controller = allowed::get;
		manager.addController(controller);
		references.forEach(manager::register);
		try
		{
			manager.start();
			awaitTrue(() -> this.ownCleanupThreads().size() == 1, "precondition: one cleanup thread");

			// the thread ends on its own when no controller allows running
			allowed.set(false);
			awaitTrue(() -> this.ownCleanupThreads().isEmpty(), "the thread must end without a controller allowing it");

			// what a storage start does: start() must create a new thread instead of believing the old one runs
			allowed.set(true);
			manager.start();
			awaitTrue(() -> loaded(references) == 0, "clearing must resume after start()");
		}
		finally
		{
			manager.stop();
			Reference.reachabilityFence(controller);
		}
	}

	@Test
	void storageRestartRestartsClearing()
	{
		LazyReferenceManager.set(clearingManager(10));
		final Root root = new Root();
		root.data = Lazy.Reference(new byte[1024]);
		this.storage = EmbeddedStorage.start(root, this.dir);
		awaitTrue(() -> !root.data.isLoaded(), "precondition: the storage start starts clearing");

		this.storage.shutdown();

		this.storage.start();
		assertEquals(1024, root.data.get().length);
		awaitTrue(() -> !root.data.isLoaded(), "clearing must run again after the storage restart");
	}

	@Test
	void failingCycleDoesNotKillTheThread()
	{
		this.assertThreadSurvives(new RuntimeException("simulated statistics failure"));
	}

	@Test
	void outOfMemoryErrorDoesNotKillTheThread()
	{
		// an OutOfMemoryError is exactly the situation clearing is meant to relieve
		this.assertThreadSurvives(new OutOfMemoryError("simulated"));
	}

	private void assertThreadSurvives(final Throwable failure)
	{
		final List<Lazy<Object>> references = storedReferences(10, 3_600_000_000L);
		this.statisticsFailure.set(failure);
		final LazyReferenceManager manager = clearingManager(10);
		references.forEach(manager::register);
		manager.start();
		try
		{
			awaitTrue(() -> this.statisticsCalls.get() >= 5, "the thread must keep cycling while every cycle fails");
			assertTrue(manager.isRunning());
			assertEquals(references.size(), loaded(references), "nothing is cleared while every cycle fails");

			this.statisticsFailure.set(null);
			awaitTrue(() -> loaded(references) == 0, "clearing must resume after the failure ends");
		}
		finally
		{
			manager.stop();
		}
	}

	@Test
	void nonPositiveIntervalDoesNotSpin()
	{
		// a cycle without any registered reference ends before it fetches the statistics
		final List<Lazy<Object>> references = storedReferences(1, 3_650_000_000L);
		final LazyReferenceManager manager = clearingManager(0);
		references.forEach(manager::register);
		manager.start();
		try
		{
			sleep(300);
			final int calls = this.statisticsCalls.get();
			assertTrue(calls > 0, "precondition: the thread must run cycles");
			// at least 1 ms per cycle: a spinning thread makes millions of calls
			assertTrue(calls <= 600, "statistics calls in 300 ms: " + calls);
		}
		finally
		{
			manager.stop();
			Reference.reachabilityFence(references);
		}
	}

	@Test
	void slowStatisticsDoNotConsumeTheCheckBudget()
	{
		// 5 ms per statistics call, 1 ms budget: with the budget started before the statistics about 1 reference per cycle
		this.statisticsSleepMs = 5;
		final List<Lazy<Object>> references = storedReferences(10_000, 3_700_000_000L);
		final LazyReferenceManager manager = clearingManager(1_000_000);
		references.forEach(manager::register);

		manager.cleanUp(1_000_000);

		final long cleared = references.size() - loaded(references);
		// with the budget started before the statistics, exactly one reference (the first check) is cleared
		assertTrue(cleared > 1, "cleared in one 1 ms cycle: " + cleared);
	}

	/**
	 * A manager whose checker clears every stored, loaded reference without a grace time, after fetching the
	 * statistics like the default checker.
	 */
	private static LazyReferenceManager clearingManager(final long intervalMs)
	{
		return LazyReferenceManager.New(
			Lazy.Checker((lazy, statistics, timeout) -> Boolean.TRUE),
			_longReference.New(intervalMs),
			_longReference.New(1_000_000),
			MonitoringManager.Disabled()
		);
	}

	private static List<Lazy<Object>> storedReferences(final int count, final long firstId)
	{
		final List<Lazy<Object>> references = new ArrayList<>();
		for(int i = 0; i < count; i++)
		{
			// an object id makes it a stored reference; it is never reloaded here
			references.add(Lazy.New(new Object(), firstId + i, null));
		}
		return references;
	}

	private static long loaded(final List<Lazy<Object>> references)
	{
		return references.stream().filter(reference -> reference.isLoaded()).count();
	}

	private boolean isOwnCleanupThread(final Thread thread)
	{
		return thread.getName().startsWith(THREAD_PREFIX) && !this.preexistingThreads.contains(thread);
	}

	private Set<Thread> ownCleanupThreads()
	{
		final Set<Thread> threads = new HashSet<>();
		for(final Thread thread : Thread.getAllStackTraces().keySet())
		{
			if(thread.isAlive() && this.isOwnCleanupThread(thread))
			{
				threads.add(thread);
			}
		}
		return threads;
	}

	private static void awaitTrue(final BooleanSupplier condition, final String message)
	{
		final long deadline = System.currentTimeMillis() + DEADLINE_MS;
		while(!condition.getAsBoolean())
		{
			assertTrue(System.currentTimeMillis() < deadline, message);
			sleep(10);
		}
	}

	private static void awaitQuietly(final CountDownLatch latch)
	{
		try
		{
			latch.await(10, TimeUnit.SECONDS);
		}
		catch(final InterruptedException e)
		{
			Thread.currentThread().interrupt();
		}
	}

	private static void sleep(final long ms)
	{
		try
		{
			Thread.sleep(ms);
		}
		catch(final InterruptedException e)
		{
			Thread.currentThread().interrupt();
		}
	}
}
