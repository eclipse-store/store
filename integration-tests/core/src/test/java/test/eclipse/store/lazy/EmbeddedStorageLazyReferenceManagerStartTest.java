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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.serializer.monitoring.MonitoringManager;
import org.eclipse.serializer.persistence.binary.types.AbstractBinaryHandlerCustomValue;
import org.eclipse.serializer.persistence.binary.types.Binary;
import org.eclipse.serializer.persistence.types.PersistenceLoadHandler;
import org.eclipse.serializer.persistence.types.PersistenceStoreHandler;
import org.eclipse.serializer.reference.Lazy;
import org.eclipse.serializer.reference.LazyReferenceManager;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;

import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A storage start must start the global LazyReferenceManager that is global when the start completes. Changes of the
 * global manager's state during the start are provoked from the type handler of the root, which runs while the root is
 * loaded, i.e. between the beginning of the start and the activation of the manager.
 */
public class EmbeddedStorageLazyReferenceManagerStartTest
{
	static volatile Runnable onRootLoad;

	@TempDir
	Path dir;

	@TempDir
	Path otherDir;

	private LazyReferenceManager   previousManager;
	private EmbeddedStorageManager storage;
	private EmbeddedStorageManager otherStorage;

	@BeforeEach
	void rememberManager()
	{
		this.previousManager = LazyReferenceManager.get();
	}

	@AfterEach
	void restore()
	{
		onRootLoad = null;
		try
		{
			shutdown(this.storage);
			shutdown(this.otherStorage);
		}
		finally
		{
			if(LazyReferenceManager.get() != this.previousManager)
			{
				// a fresh manager: the previous one would take over its own references a second time
				LazyReferenceManager.set(LazyReferenceManager.New());
			}
		}
	}

	/**
	 * A set() during the start: the new global manager must be started, not the replaced one.
	 */
	@Test
	void managerReplacedDuringTheStartIsStarted()
	{
		this.storeRoot();
		final LazyReferenceManager replacement = manager();
		onRootLoad = () -> LazyReferenceManager.set(replacement);

		this.storage = this.startWithHandler(this.dir);

		assertSame(replacement, LazyReferenceManager.get(), "precondition: replaced during the start");
		assertTrue(replacement.isRunning(), "the global manager must run after the storage start");
	}

	/**
	 * The last other storage shuts down during the start and stops the manager: the starting storage must start it
	 * again instead of relying on its state before the start.
	 */
	@Test
	void managerStoppedByAnotherStorageDuringTheStartIsStarted()
	{
		LazyReferenceManager.set(manager());
		this.otherStorage = EmbeddedStorage.start(new Object[0], this.otherDir);
		assertTrue(LazyReferenceManager.get().isRunning(), "precondition: the other storage runs the manager");

		this.storeRoot();
		onRootLoad = () -> this.otherStorage.shutdown();

		this.storage = this.startWithHandler(this.dir);

		assertTrue(LazyReferenceManager.get().isRunning(), "the global manager must run after the storage start");
	}

	/**
	 * A set() from another thread while the storage activates the global manager: the replaced manager must not be
	 * started by the storage, only the successor runs and has the storage as controller.
	 * <p>
	 * The global manager is a recording one that lets another thread call set() when the storage adds its controller,
	 * and waits for it a little: with a separate read and start, the set() completes before the start.
	 */
	@Test
	void managerReplacedWhileTheStorageActivatesItIsNotStarted() throws InterruptedException
	{
		final LazyReferenceManager successor = manager();
		final Thread[]             setter    = new Thread[1];
		final RecordingManager     recording = new RecordingManager(() ->
		{
			setter[0] = new Thread(() -> LazyReferenceManager.set(successor));
			setter[0].start();
			try
			{
				// with the activation atomic to set(), the setter waits for it and this times out
				setter[0].join(500);
			}
			catch(final InterruptedException e)
			{
				Thread.currentThread().interrupt();
			}
		});
		LazyReferenceManager.set(recording.proxy());

		this.storage = EmbeddedStorage.start(new Object[0], this.dir);
		assertNotNull(setter[0], "precondition: the storage added its controller, which runs the hook");
		setter[0].join(5_000);

		assertSame(successor, LazyReferenceManager.get(), "precondition: replaced during the activation");
		assertFalse(recording.running, "the replaced manager must not run");
		assertTrue(successor.isRunning(), "the successor must run");
		assertTrue(hasController(successor, this.storage), "the successor must have the storage as controller");
	}

	private static boolean hasController(final LazyReferenceManager manager, final Object controller)
	{
		final boolean[] found = new boolean[1];
		manager.iterateControllers(c ->
		{
			if(c == controller)
			{
				found[0] = true;
			}
		});
		return found[0];
	}

	/**
	 * A minimal manager that records its running state and controllers, and runs a hook when a controller is added
	 * for the first time (controllers taken over from the previous manager by set() do not count).
	 */
	static final class RecordingManager
	{
		final List<Lazy<?>>                         references  = Collections.synchronizedList(new ArrayList<>());
		final List<LazyReferenceManager.Controller> controllers = Collections.synchronizedList(new ArrayList<>());
		final Runnable                              onFirstController;
		final AtomicBoolean                         hookRun     = new AtomicBoolean();
		volatile boolean                            running;

		RecordingManager(final Runnable onFirstController)
		{
			this.onFirstController = onFirstController;
		}

		@SuppressWarnings("unchecked")
		LazyReferenceManager proxy()
		{
			return (LazyReferenceManager)Proxy.newProxyInstance(
				LazyReferenceManager.class.getClassLoader(),
				new Class<?>[]{LazyReferenceManager.class},
				(proxy, method, args) ->
				{
					switch(method.getName())
					{
						case "register":
							this.references.add((Lazy<?>)args[0]);
							return null;
						case "registerAll":
							((LazyReferenceManager)args[0]).iterate((Consumer<Lazy<?>>)this.references::add);
							((LazyReferenceManager)args[0]).iterateControllers(
								(Consumer<LazyReferenceManager.Controller>)this.controllers::add
							);
							return proxy;
						case "addController":
							this.controllers.add((LazyReferenceManager.Controller)args[0]);
							if(this.hookRun.compareAndSet(false, true))
							{
								this.onFirstController.run();
							}
							return proxy;
						case "removeController":
							return this.controllers.remove(args[0]);
						case "iterate":
							new ArrayList<>(this.references).forEach((Consumer<Lazy<?>>)args[0]);
							return args[0];
						case "iterateControllers":
							new ArrayList<>(this.controllers).forEach((Consumer<LazyReferenceManager.Controller>)args[0]);
							return args[0];
						case "start":
							this.running = true;
							return proxy;
						case "stop":
							this.running = false;
							return proxy;
						case "isRunning":
							return this.running;
						case "hashCode":
							return System.identityHashCode(proxy);
						case "equals":
							return proxy == args[0];
						case "toString":
							return "recording manager";
						default:
							return null;
					}
				}
			);
		}
	}

	private void storeRoot()
	{
		final EmbeddedStorageManager initial = EmbeddedStorage.Foundation(this.dir)
			.onConnectionFoundation(f -> f.registerCustomTypeHandlers(new HookRootHandler()))
			.start(new HookRoot(7))
		;
		initial.shutdown();
	}

	private EmbeddedStorageManager startWithHandler(final Path directory)
	{
		return EmbeddedStorage.Foundation(directory)
			.onConnectionFoundation(f -> f.registerCustomTypeHandlers(new HookRootHandler()))
			.start()
		;
	}

	private static LazyReferenceManager manager()
	{
		return LazyReferenceManager.New(
			Lazy.CheckerTimeout(Long.MAX_VALUE),
			() -> 1_000,
			() -> 1_000_000,
			MonitoringManager.Disabled()
		);
	}

	private static void shutdown(final EmbeddedStorageManager storage)
	{
		if(storage != null && storage.isRunning())
		{
			storage.shutdown();
		}
	}

	static final class HookRoot
	{
		final long value;

		HookRoot(final long value)
		{
			this.value = value;
		}
	}

	/**
	 * Runs {@link EmbeddedStorageLazyReferenceManagerStartTest#onRootLoad} when the root is loaded.
	 */
	static final class HookRootHandler extends AbstractBinaryHandlerCustomValue<HookRoot, Long>
	{
		HookRootHandler()
		{
			super(HookRoot.class, CustomFields(bytes("value")));
		}

		@Override
		public boolean hasVaryingPersistedLengthInstances()
		{
			return false;
		}

		@Override
		public void store(
			final Binary                  data    ,
			final HookRoot                instance,
			final long                    objectId,
			final PersistenceStoreHandler handler
		)
		{
			data.store_bytes(this.typeId(), objectId, ByteBuffer.allocate(Long.BYTES).putLong(instance.value).array());
		}

		@Override
		public HookRoot create(final Binary data, final PersistenceLoadHandler handler)
		{
			final Runnable hook = onRootLoad;
			if(hook != null)
			{
				hook.run();
			}
			return new HookRoot(ByteBuffer.wrap(data.build_bytes()).getLong());
		}

		@Override
		public Long getValidationStateFromInstance(final HookRoot instance)
		{
			return instance.value;
		}

		@Override
		public Long getValidationStateFromBinary(final Binary data)
		{
			return ByteBuffer.wrap(data.build_bytes()).getLong();
		}
	}
}
