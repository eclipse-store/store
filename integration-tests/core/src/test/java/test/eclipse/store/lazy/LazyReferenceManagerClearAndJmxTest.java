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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import org.eclipse.serializer.monitoring.MetricMonitor;
import org.eclipse.serializer.monitoring.MonitoringManager;
import org.eclipse.serializer.reference.Lazy;
import org.eclipse.serializer.reference.LazyReferenceManager;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;

import java.lang.management.ManagementFactory;
import java.nio.file.Path;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link LazyReferenceManager#clear()} (also the JMX operation unloadAll) clears only stored references without a
 * usage mark: an unstored reference cannot be reloaded, a usage mark pins changes not stored yet.
 * <p>
 * The JMX bean {@code org.eclipse.store:name=LazyReferenceManager} belongs to the running global manager. The managers
 * under test register through a {@link Recording} monitoring manager, so the holder of the bean name is known.
 */
public class LazyReferenceManagerClearAndJmxTest
{
	private static final String BEAN_NAME = "org.eclipse.store:name=LazyReferenceManager";

	static final class Root
	{
		String name = "root";
	}

	@TempDir
	Path dir;

	private LazyReferenceManager   previousManager;
	private EmbeddedStorageManager storage;

	@BeforeEach
	void rememberManager()
	{
		this.previousManager = LazyReferenceManager.get();
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
	void clearSkipsUnstoredAndUsedMarkedReferences()
	{
		final LazyReferenceManager manager    = LazyReferenceManager.New(Lazy.CheckerTimeout(Long.MAX_VALUE));
		final Lazy<Object>[]       references = mixedReferences(manager, 3_800_000_000L);

		manager.clear();

		assertOnlyFreeStoredCleared(references);
	}

	@Test
	void jmxUnloadAllSkipsUnstoredAndUsedMarkedReferences() throws Exception
	{
		awaitBeanNameFree();
		final Recording            monitoring = new Recording();
		final LazyReferenceManager manager    = recorded(monitoring);
		final Lazy<Object>[]       references = mixedReferences(manager, 3_900_000_000L);
		manager.start();
		try
		{
			assertTrue(monitoring.holdsBean(), "precondition: the started manager holds the JMX bean");
			server().invoke(beanName(), "unloadAll", null, null);
		}
		finally
		{
			manager.stop();
		}

		assertOnlyFreeStoredCleared(references);
	}

	@Test
	void notRunningManagerHoldsNoBean()
	{
		awaitBeanNameFree();
		final Recording monitoring = new Recording();
		LazyReferenceManager.set(recorded(monitoring));
		assertFalse(monitoring.holdsBean());
	}

	@Test
	void beanFollowsHandover()
	{
		awaitBeanNameFree();
		this.storage = EmbeddedStorage.start(new Root(), this.dir);
		assertTrue(beanRegistered(), "precondition: the running manager has a JMX bean");

		final Recording monitoring = new Recording();
		LazyReferenceManager.set(recorded(monitoring));
		assertTrue(monitoring.holdsBean(), "the JMX bean must belong to the new global manager");
	}

	@Test
	void beanBelongsToManagerSetBeforeStorageStart()
	{
		awaitBeanNameFree();
		final Recording monitoring = new Recording();
		LazyReferenceManager.set(recorded(monitoring));

		this.storage = EmbeddedStorage.start(new Root(), this.dir);
		assertTrue(monitoring.holdsBean(), "the JMX bean must belong to the manager set before the start");
	}

	@Test
	void beanFollowsStorageRestart()
	{
		awaitBeanNameFree();
		final Recording monitoring = new Recording();
		LazyReferenceManager.set(recorded(monitoring));
		this.storage = EmbeddedStorage.start(new Root(), this.dir);
		assertTrue(monitoring.holdsBean(), "precondition: the started manager holds the JMX bean");

		this.storage.shutdown();
		// without other controllers the shutdown stops the manager at once; with controllers taken over from earlier
		// managers its thread ends at the next check (20 ms interval)
		final long deadline = System.currentTimeMillis() + 3_000;
		while(monitoring.holdsBean() && System.currentTimeMillis() < deadline)
		{
			sleepQuietly(10);
		}
		assertFalse(monitoring.holdsBean(), "the stopped manager must release the JMX bean");

		this.storage.start();
		assertTrue(monitoring.holdsBean(), "the restarted manager must hold the JMX bean again");
	}

	/**
	 * Unstored, stored and used-marked stored references alternate, so the result does not depend on the iteration
	 * order (a failure on one reference used to stop the clearing of all following ones).
	 */
	@SuppressWarnings("unchecked")
	private static Lazy<Object>[] mixedReferences(final LazyReferenceManager manager, final long firstId)
	{
		final Lazy<Object>[] references = new Lazy[9];
		for(int i = 0; i < references.length; i++)
		{
			references[i] = i % 3 == 0
				? Lazy.Reference(new Object())
				: Lazy.New(new Object(), firstId + i, null)
			;
			if(i % 3 == 2)
			{
				references[i].markUsedFor(manager);
			}
			manager.register(references[i]);
		}
		return references;
	}

	private static void assertOnlyFreeStoredCleared(final Lazy<Object>[] references)
	{
		for(int i = 0; i < references.length; i++)
		{
			switch(i % 3)
			{
				case 0:
					assertTrue(references[i].isLoaded(), "an unstored reference must keep its subject: " + i);
					break;
				case 1:
					assertFalse(references[i].isLoaded(), "a stored reference must be cleared: " + i);
					break;
				default:
					assertTrue(references[i].isLoaded(), "a used-marked reference must keep its subject: " + i);
			}
		}
	}

	private static LazyReferenceManager recorded(final Recording monitoring)
	{
		return LazyReferenceManager.New(
			Lazy.CheckerTimeout(Long.MAX_VALUE),
			() -> 20,
			() -> 1_000_000,
			monitoring
		);
	}

	/**
	 * The bean name is JVM-wide. A manager stopped by its controllers (storages of other tests) releases it when its
	 * thread notices, within one interval; a still running foreign manager makes the JMX checks impossible.
	 */
	private static void awaitBeanNameFree()
	{
		final long deadline = System.currentTimeMillis() + 3_000;
		while(beanRegistered() && System.currentTimeMillis() < deadline)
		{
			try
			{
				Thread.sleep(20);
			}
			catch(final InterruptedException e)
			{
				Thread.currentThread().interrupt();
				return;
			}
		}
		assumeFalse(beanRegistered(), "the JMX bean name is held by a manager of another test");
	}

	private static boolean beanRegistered()
	{
		return server().isRegistered(beanName());
	}

	private static MBeanServer server()
	{
		return ManagementFactory.getPlatformMBeanServer();
	}

	private static void sleepQuietly(final long ms)
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

	private static ObjectName beanName()
	{
		try
		{
			return new ObjectName(BEAN_NAME);
		}
		catch(final MalformedObjectNameException e)
		{
			throw new IllegalStateException(e);
		}
	}

	/**
	 * Registers under the same name as the JMX monitoring manager of the global manager and records success.
	 */
	static final class Recording implements MonitoringManager
	{
		private boolean registered;

		synchronized boolean holdsBean()
		{
			return this.registered && beanRegistered();
		}

		@Override
		public synchronized void registerMonitor(final MetricMonitor metric)
		{
			try
			{
				server().registerMBean(metric, new ObjectName("org.eclipse.store:" + metric.getName()));
				this.registered = true;
			}
			catch(final InstanceAlreadyExistsException e)
			{
				// held by another manager: holdsBean() stays false
			}
			catch(final Exception e)
			{
				throw new IllegalStateException(e);
			}
		}

		@Override
		public synchronized void shutdown()
		{
			if(!this.registered)
			{
				return;
			}
			try
			{
				server().unregisterMBean(beanName());
			}
			catch(final Exception e)
			{
				throw new IllegalStateException(e);
			}
			this.registered = false;
		}
	}
}
