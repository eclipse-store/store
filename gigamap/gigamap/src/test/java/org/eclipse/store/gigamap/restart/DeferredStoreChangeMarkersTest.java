package org.eclipse.store.gigamap.restart;

/*-
 * #%L
 * EclipseStore GigaMap
 * %%
 * Copyright (C) 2023 - 2025 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.IndexerString;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.types.Storage;
import org.eclipse.store.storage.types.StorageChannelCountProvider;
import org.eclipse.store.storage.types.StorageConfiguration;
import org.eclipse.store.storage.types.StorageReferenceValidationPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DeferredStoreChangeMarkersTest
{
    @TempDir
    Path directory;

    @Test
    void earlierCommitMustNotClearLaterAddition()
    {
        final StorageConfiguration configuration = this.configuration();
        try (var storage = EmbeddedStorage.Foundation(configuration).start())
        {
            final GigaMap<Item> map = storage.ensureRoot(GigaMap::New);
            map.index().bitmap().add(new ValueIndexer());
            map.add(new Item("first"));
            final var first = storage.createStorer();
            map.store(first);
            map.add(new Item("second"));
            first.commit();
            map.store();
            for(int i = 0; i < 20; i++)
            {
                map.add(new Item("later-" + i));
                map.store();
            }
        }
        try (var storage = EmbeddedStorage.Foundation(configuration).start())
        {
            final GigaMap<Item> map = storage.root();
            assertEquals(22, map.size());
            assertNotNull(map.get(1), "the deferred commit cleared an uncommitted entity segment");
            assertEquals("second", map.get(1).value);
            assertEquals(1, map.query(new ValueIndexer().is("second")).count());
        }
    }

    @Test
    void earlierCommitMustNotForgetLaterInPlaceUpdate()
    {
        final StorageConfiguration configuration = this.configuration();
        try (var storage = EmbeddedStorage.Foundation(configuration).start())
        {
            final GigaMap<Item> map = storage.ensureRoot(GigaMap::New);
            map.index().bitmap().add(new ValueIndexer());
            final long id = map.add(new Item("before"));
            map.store();
            final var first = storage.createStorer();
            map.store(first);
            map.update(id, item -> item.value = "after");
            first.commit();
            map.store();
        }
        try (var storage = EmbeddedStorage.Foundation(configuration).start())
        {
            final GigaMap<Item> map = storage.root();
            assertEquals("after", map.get(0).value);
            assertEquals(1, map.query(new ValueIndexer().is("after")).count());
            assertEquals(0, map.query(new ValueIndexer().is("before")).count());
        }
    }

    @Test
    void earlierCommitMustNotClearLaterIndexRegistration()
    {
        final StorageConfiguration configuration = this.configuration();
        try (var storage = EmbeddedStorage.Foundation(configuration).start())
        {
            final GigaMap<Item> map = storage.ensureRoot(GigaMap::New);
            map.add(new Item("indexed"));
            final var first = storage.createStorer();
            map.store(first);
            map.index().bitmap().add(new ValueIndexer());
            first.commit();
            map.store();
        }
        try (var storage = EmbeddedStorage.Foundation(configuration).start())
        {
            final GigaMap<Item> map = storage.root();
            assertEquals(1, map.query(new ValueIndexer().is("indexed")).count());
        }
    }

    private StorageConfiguration configuration()
    {
        return Storage.ConfigurationBuilder()
            .setStorageFileProvider(Storage.FileProvider(this.directory))
            .setChannelCountProvider(StorageChannelCountProvider.New(8))
            .setReferenceValidationPolicy(StorageReferenceValidationPolicy.HEAL)
            .createConfiguration();
    }

    static final class Item
    {
        String value;

        Item(final String value)
        {
            this.value = value;
        }
    }

    static final class ValueIndexer extends IndexerString.Abstract<Item>
    {
        @Override
        protected String getString(final Item item)
        {
            return item.value;
        }
    }
}
