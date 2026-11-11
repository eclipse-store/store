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

import java.nio.ByteBuffer;
import java.nio.file.Path;

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
