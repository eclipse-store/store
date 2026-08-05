package test.eclipse.store.configuration;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.serializer.afs.types.AFile;
import org.eclipse.serializer.persistence.types.PersistenceTypeDictionaryFileHandler;
import org.eclipse.serializer.persistence.types.PersistenceTypeDictionaryIoHandler;
import org.eclipse.serializer.persistence.types.PersistenceTypeDictionaryStorer;
import org.eclipse.store.afs.nio.types.NioFileSystem;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageFoundation;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.types.Storage;
import org.eclipse.store.storage.types.StorageLiveFileProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Regression test for the coalesced type dictionary export.
 * <p>
 * A single {@code storeRoot} may discover and register hundreds of distinct types while serializing a
 * large root graph. The type dictionary must be exported exactly once per store barrier, not once per
 * registered type: the crash-safe export (temp-file write + fsync + delete + move) is comparatively
 * expensive, so per-type exports made a first store of a large graph take seconds.
 */
class TypeDictionaryExportCoalescingTest
{
	@TempDir
	Path location;

	EmbeddedStorageManager storageManager;

	@AfterEach
	public void closeStorage()
	{
		if (this.storageManager != null && !this.storageManager.isShutdown())
		{
			this.storageManager.shutdown();
		}
	}

	/**
	 * Stores a root graph made of many distinct types and asserts the type dictionary is exported exactly
	 * once per store barrier, and that the stored data survives a restart - proving the single coalesced
	 * export produced a complete dictionary.
	 */
	@Test
	void singleExportPerStoreBarrier()
	{
		final CountingFileHandlerCreator countingCreator = new CountingFileHandlerCreator();

		final NioFileSystem fileSystem = NioFileSystem.New();
		final EmbeddedStorageFoundation<?> foundation = EmbeddedStorage.Foundation(
			Storage.Configuration(
				StorageLiveFileProvider.Builder(fileSystem)
					.setDirectory(fileSystem.ensureDirectoryPath(this.location.toString()))
					.setFileHandlerCreator(countingCreator)
					.createFileProvider()
			)
		);

		// First run: build a graph of many distinct types and store it.
		final List<Object> root = new ArrayList<>();
		for (int i = 0; i < 100; i++)
		{
			root.add(new TypeA(i));
			root.add(new TypeB(i, "b" + i));
			root.add(new TypeC(new TypeD(i), new TypeE(i)));
			root.add(new TypeF(i, new TypeG(i), new TypeH("h" + i)));
		}
		this.storageManager = foundation.createEmbeddedStorageManager(root).start();
		this.storageManager.storeRoot();

		assertEquals(1, countingCreator.exportCount,
			"One storeRoot registering hundreds of types must produce exactly one type dictionary export");

		this.storageManager.shutdown();
		this.storageManager = null;

		// Second run: restart against the same directory; the coalesced dictionary must be complete.
		final EmbeddedStorageManager reloaded = EmbeddedStorage.start(this.location);
		this.storageManager = reloaded;
		final Object loadedRoot = reloaded.root();
		assertNotNull(loadedRoot, "root must load after restart");
		@SuppressWarnings("unchecked")
		final List<Object> loaded = (List<Object>) loadedRoot;
		assertEquals(400, loaded.size(), "all entities must reload");
		assertEquals("h99", ((TypeF) loaded.get(399)).h.value, "deep data must reload");
	}

	/**
	 * Decorates the default type dictionary file handler so every persistent export is counted while the
	 * real crash-safe file write still happens.
	 */
	static final class CountingFileHandlerCreator implements PersistenceTypeDictionaryFileHandler.Creator
	{
		int exportCount;

		@Override
		public PersistenceTypeDictionaryIoHandler createTypeDictionaryIoHandler(
			final AFile file,
			final PersistenceTypeDictionaryStorer writeListener
		)
		{
			return new CountingIoHandler(
				PersistenceTypeDictionaryFileHandler.New(file, writeListener),
				this
			);
		}
	}

	static final class CountingIoHandler implements PersistenceTypeDictionaryIoHandler
	{
		private final PersistenceTypeDictionaryIoHandler delegate;
		private final CountingFileHandlerCreator counter;

		CountingIoHandler(final PersistenceTypeDictionaryIoHandler delegate, final CountingFileHandlerCreator counter)
		{
			this.delegate = delegate;
			this.counter = counter;
		}

		@Override
		public String loadTypeDictionary()
		{
			return this.delegate.loadTypeDictionary();
		}

		@Override
		public void storeTypeDictionary(final String typeDictionaryString)
		{
			this.counter.exportCount++;
			this.delegate.storeTypeDictionary(typeDictionaryString);
		}
	}

	static final class TypeA
	{
		final int value;

		TypeA(final int value)
		{
			this.value = value;
		}
	}

	static final class TypeB
	{
		final int value;
		final String name;

		TypeB(final int value, final String name)
		{
			this.value = value;
			this.name = name;
		}
	}

	static final class TypeC
	{
		final TypeD d;
		final TypeE e;

		TypeC(final TypeD d, final TypeE e)
		{
			this.d = d;
			this.e = e;
		}
	}

	static final class TypeD
	{
		final int value;

		TypeD(final int value)
		{
			this.value = value;
		}
	}

	static final class TypeE
	{
		final int value;

		TypeE(final int value)
		{
			this.value = value;
		}
	}

	static final class TypeF
	{
		final int value;
		final TypeG g;
		final TypeH h;

		TypeF(final int value, final TypeG g, final TypeH h)
		{
			this.value = value;
			this.g = g;
			this.h = h;
		}
	}

	static final class TypeG
	{
		final int value;

		TypeG(final int value)
		{
			this.value = value;
		}
	}

	static final class TypeH
	{
		final String value;

		TypeH(final String value)
		{
			this.value = value;
		}
	}
}
