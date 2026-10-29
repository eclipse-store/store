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

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.types.Storage;
import org.eclipse.store.storage.types.StorageFileWriter;
import org.eclipse.store.storage.types.StorageLiveDataFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link BitmapLevel3} caches a raw pointer to the level1 segment of the last add. Rebuilding a level2 block frees
 * its standalone level1 segments, so the cached pointer must not be used afterwards. Otherwise the next add in the
 * same 4096-id range sets its bit in freed memory: the entity is missing from queries, and the next successful
 * store persists the loss.
 * <p>
 * A successful store clears the cache after the commit. These tests cover the rebuilds that are not followed by a
 * successful commit.
 */
public class BitmapAddCacheStaleSegmentTest
{
	@Test
	void addAfterFailedStore(@TempDir final Path dir)
	{
		final boolean[] failWrites = new boolean[1];
		final GigaMap<Entity> map = newMap();
		try(EmbeddedStorageManager storage = start(dir, failWrites, map))
		{
			addEntities(map, 100);
			map.store();
			addEntities(map, 100); // the add cache points at a standalone segment again

			failWrites[0] = true;
			assertThrows(RuntimeException.class, map::store); // the rebuild happens before the write fails
			failWrites[0] = false;

			addEntities(map, 100);
			assertEquals(150, countKey0(map), "in memory");
			map.store();
		}
		assertEquals(150, countKey0AfterRestart(dir), "after restart");
	}

	/**
	 * The cached level2 block is rebuilt twice before the next add. A freed address can be handed out again, so
	 * comparing addresses would not detect the second rebuild reliably.
	 */
	@Test
	void addAfterTwoFailedStores(@TempDir final Path dir)
	{
		final boolean[] failWrites = new boolean[1];
		final GigaMap<Entity> map = newMap();
		try(EmbeddedStorageManager storage = start(dir, failWrites, map))
		{
			addEntities(map, 9000); // three level1 segments
			map.store();
			addEntities(map, 100);  // the add cache points at the third segment

			failWrites[0] = true;
			assertThrows(RuntimeException.class, map::store);
			map.removeById(10);     // the first segment becomes standalone, so the block is rebuilt again
			assertThrows(RuntimeException.class, map::store);
			failWrites[0] = false;

			addEntities(map, 100);
			assertEquals(4599, countKey0(map), "in memory");
			map.store();
		}
		assertEquals(4599, countKey0AfterRestart(dir), "after restart");
	}

	private static GigaMap<Entity> newMap()
	{
		final GigaMap<Entity> map = GigaMap.New();
		map.index().bitmap().add(new KeyIndexer());
		return map;
	}

	private static void addEntities(final GigaMap<Entity> map, final int count)
	{
		for(int i = 0; i < count; i++)
		{
			map.add(new Entity(i % 2));
		}
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

	private static EmbeddedStorageManager start(final Path dir, final boolean[] failWrites, final GigaMap<Entity> map)
	{
		final StorageFileWriter.Provider writers = () -> new StorageFileWriter()
		{
			@Override
			public long writeStore(final StorageLiveDataFile targetFile, final Iterable<? extends ByteBuffer> byteBuffers)
			{
				if(failWrites[0])
				{
					throw new UncheckedIOException(new IOException("injected write failure"));
				}
				return StorageFileWriter.super.writeStore(targetFile, byteBuffers);
			}
		};
		return EmbeddedStorage.Foundation(
			Storage.ConfigurationBuilder()
				.setStorageFileProvider(Storage.FileProvider(dir))
				.createConfiguration()
			)
			.setWriterProvider(writers)
			.start(map);
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
