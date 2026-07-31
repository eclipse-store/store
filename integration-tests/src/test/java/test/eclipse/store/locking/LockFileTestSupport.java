package test.eclipse.store.locking;

/*-
 * #%L
 * EclipseStore Integration Tests
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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.types.Storage;

/**
 * Shared helpers for the storage lock-file integration tests: starting managers that model distinct
 * processes on the same directory, observing the lock-file worker thread, and asserting lock refusals.
 */
final class LockFileTestSupport
{
	static final String LOCK_THREAD_NAME = "StorageLockFileManager";

	private LockFileTestSupport()
	{
		throw new UnsupportedOperationException();
	}

	/**
	 * Starts a manager with an explicit database name and process identity, so a single JVM can model
	 * two independent processes competing for the same storage directory: distinct database names
	 * bypass the JVM-local database registry, leaving the lock file as the only remaining gate.
	 */
	static EmbeddedStorageManager startManager(
		final Path   directory       ,
		final String databaseName     ,
		final String processIdentity  ,
		final long   updateIntervalMs
	)
	{
		return EmbeddedStorage.Foundation(directory)
			.setDataBaseName(databaseName)
			.setLockFileSetupProvider(Storage.LockFileSetupProvider(updateIntervalMs))
			.setProcessIdentityProvider(() -> processIdentity)
			.start();
	}

	static long liveLockThreadCount()
	{
		return Thread.getAllStackTraces().keySet().stream()
			.filter(Thread::isAlive)
			.filter(thread -> !thread.isDaemon())
			.filter(thread -> LOCK_THREAD_NAME.equals(thread.getName()))
			.count();
	}

	static boolean awaitLockThreadCount(final long expected, final long timeoutMs) throws InterruptedException
	{
		final long deadline = System.nanoTime() + timeoutMs * 1_000_000L;
		do
		{
			if(liveLockThreadCount() == expected)
			{
				return true;
			}
			Thread.sleep(20);
		}
		while(System.nanoTime() < deadline);

		return liveLockThreadCount() == expected;
	}

	/**
	 * Asserts that opening the given manager is refused with a lock-related error. If it is wrongly
	 * admitted, the manager is shut down before the assertion fails so a stray manager cannot leak.
	 */
	static void assertRefused(final Callable<EmbeddedStorageManager> open)
	{
		EmbeddedStorageManager admitted = null;
		try
		{
			admitted = open.call();
		}
		catch(final Throwable refusal)
		{
			assertTrue(isLockRefusal(refusal), "expected a lock refusal, but was: " + refusal);
			return;
		}

		try
		{
			admitted.shutdown();
		}
		catch(final Throwable ignore)
		{
			// best-effort cleanup of the wrongly-admitted manager
		}
		fail("second manager was admitted but must have been refused");
	}

	private static boolean isLockRefusal(final Throwable t)
	{
		for(Throwable cause = t; cause != null; cause = cause.getCause())
		{
			final String message = cause.getMessage();
			if(message != null)
			{
				final String lower = message.toLowerCase();
				if(lower.contains("in use") || lower.contains("unreadable"))
				{
					return true;
				}
			}
		}
		return false;
	}

	static Object fieldValueOfType(final Object owner, final String typeName) throws Exception
	{
		final Class<?> target = Class.forName(typeName);
		for(Class<?> c = owner.getClass(); c != null && c != Object.class; c = c.getSuperclass())
		{
			for(final Field field : c.getDeclaredFields())
			{
				if(target.isAssignableFrom(field.getType()))
				{
					field.setAccessible(true);
					final Object value = field.get(owner);
					if(value != null)
					{
						return value;
					}
				}
			}
		}
		throw new AssertionError("no non-null field of type " + typeName + " found on " + owner.getClass());
	}

	static void forceStop(final Object lockFileManager)
	{
		if(lockFileManager == null)
		{
			return;
		}
		try
		{
			final Method stop = lockFileManager.getClass().getMethod("stop");
			stop.invoke(lockFileManager);
		}
		catch(final Throwable ignore)
		{
			// best-effort safety net so a failed assertion cannot leave a non-daemon thread hanging the fork
		}
	}
}
