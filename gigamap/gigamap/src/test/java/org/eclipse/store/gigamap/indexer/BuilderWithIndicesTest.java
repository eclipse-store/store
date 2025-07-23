package org.eclipse.store.gigamap.indexer;

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

import org.eclipse.serializer.collections.ConstHashEnum;
import org.eclipse.store.gigamap.types.BinaryIndexerNumber;
import org.eclipse.store.gigamap.types.BinaryIndexerString;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.Indexer;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.Serializable;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;


public class BuilderWithIndicesTest
{

    @TempDir
    Path storagePath;

    @Test
    void withBitmapUniqueIndices_iterable()
    {
        StringIndexer stringIndexer = new StringIndexer();
        IdIndexer idIndexer = new IdIndexer();

        List<? extends Indexer.Abstract<Entity, ?>> indexers = List.of(stringIndexer, idIndexer);

        GigaMap<Entity> gigaMap = GigaMap.<Entity>Builder().
                withBitmapUniqueIndices(indexers).
                build();

        gigaMap.add(new Entity("1000", 1));
        assertEquals("1000", gigaMap.query().findFirst().get().value);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(gigaMap, storagePath)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(gigaMap, storagePath)) {
            GigaMap<Entity> map2 = (GigaMap<Entity>) storageManager.root();
            assertEquals("1000", map2.query().findFirst().get().value);
        }
    }

    @Test
    void withBitmapUniqueIndices()
    {
        StringIndexer stringIndexer = new StringIndexer();
        IdIndexer idIndexer = new IdIndexer();

        GigaMap<Entity> gigaMap = GigaMap.<Entity>Builder().
                withBitmapUniqueIndices(stringIndexer, idIndexer).
                build();

        gigaMap.add(new Entity("1000", 1));
        assertEquals("1000", gigaMap.query().findFirst().get().value);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(gigaMap, storagePath)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(gigaMap, storagePath)) {
            GigaMap<Entity> map2 = (GigaMap<Entity>) storageManager.root();
            assertEquals("1000", map2.query().findFirst().get().value);
        }
    }

    @Test
    void withBitmapUniqueIndex()
    {
        StringIndexer stringIndexer = new StringIndexer();

        GigaMap<Entity> gigaMap = GigaMap.<Entity>Builder().
                withBitmapUniqueIndex(stringIndexer).
                build();

        gigaMap.add(new Entity("1000", 1));
        assertEquals("1000", gigaMap.query().findFirst().get().value);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(gigaMap, storagePath)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(gigaMap, storagePath)) {
            GigaMap<Entity> map2 = (GigaMap<Entity>) storageManager.root();
            assertEquals("1000", map2.query().findFirst().get().value);
        }
    }

    @Test
    void withBitmapIdentityIndices_varargs()
    {
        StringIndexer stringIndexer = new StringIndexer();
        IdIndexer idIndexer = new IdIndexer();

        GigaMap<Entity> gigaMap = GigaMap.<Entity>Builder().
                withBitmapIdentityIndices(stringIndexer, idIndexer).
                build();

        gigaMap.add(new Entity("1000", 1));
        assertEquals("1000", gigaMap.query().findFirst().get().value);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(gigaMap, storagePath)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(gigaMap, storagePath)) {
            GigaMap<Entity> map2 = (GigaMap<Entity>) storageManager.root();
            assertEquals("1000", map2.query().findFirst().get().value);
        }
    }

    @Test
    void withBitmapIdentityIndices_XGettingEnum()
    {
        StringIndexer stringIndexer = new StringIndexer();
        IdIndexer idIndexer = new IdIndexer();

        ConstHashEnum<? extends Indexer.Abstract<Entity, ? extends Serializable>> indexers = ConstHashEnum.New(stringIndexer, idIndexer);

        GigaMap<Entity> gigaMap = GigaMap.<Entity>Builder().
                withBitmapIdentityIndices(indexers).
                build();

        gigaMap.add(new Entity("1000", 1));
        assertEquals("1000", gigaMap.query().findFirst().get().value);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(gigaMap, storagePath)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(gigaMap, storagePath)) {
            GigaMap<Entity> map2 = (GigaMap<Entity>) storageManager.root();
            assertEquals("1000", map2.query().findFirst().get().value);
        }
    }

    @Test
    void withBitmapIdentityIndex()
    {
        StringIndexer stringIndexer = new StringIndexer();

        GigaMap<Entity> gigaMap = GigaMap.<Entity>Builder().
                withBitmapIdentityIndex(stringIndexer).
                build();

        gigaMap.add(new Entity("1000", 1));
        assertEquals("1000", gigaMap.query().findFirst().get().value);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(gigaMap, storagePath)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(gigaMap, storagePath)) {
            GigaMap<Entity> map2 = (GigaMap<Entity>) storageManager.root();
            assertEquals("1000", map2.query().findFirst().get().value);
        }
    }

    @Test
    void withBitmapIndices_varargs()
    {
        StringIndexer stringIndexer = new StringIndexer();
        IdIndexer idIndexer = new IdIndexer();

        GigaMap<Entity> gigaMap = GigaMap.<Entity>Builder().
                withBitmapIndices(stringIndexer, idIndexer).
                build();

        gigaMap.add(new Entity("1000", 1));
        assertEquals("1000", gigaMap.query(stringIndexer.is("1000")).findFirst().get().value);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(gigaMap, storagePath)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(gigaMap, storagePath)) {
            GigaMap<Entity> map2 = (GigaMap<Entity>) storageManager.root();
            assertEquals("1000", map2.query(stringIndexer.is("1000")).findFirst().get().value);
        }

    }

    @Test
    void withBitmapIndices_iterableParam()
    {
        StringIndexer stringIndexer = new StringIndexer();
        IdIndexer idIndexer = new IdIndexer();

        List<? extends Indexer.Abstract<Entity, ? extends Serializable>> indexers = List.of(stringIndexer, idIndexer);

        GigaMap<Entity> gigaMap = GigaMap.<Entity>Builder().
                withBitmapIndices(indexers).
                build();

        gigaMap.add(new Entity("1000", 1));
        assertEquals("1000", gigaMap.query(stringIndexer.is("1000")).findFirst().get().value);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(gigaMap, storagePath)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(gigaMap, storagePath)) {
            GigaMap<Entity> map2 = (GigaMap<Entity>) storageManager.root();
            assertEquals("1000", map2.query(stringIndexer.is("1000")).findFirst().get().value);
        }
    }

    @Test
    void withValueEquality_test()
    {
        StringIndexer stringIndexer = new StringIndexer();
        IdIndexer idIndexer = new IdIndexer();

        List<? extends Indexer.Abstract<Entity, ? extends Serializable>> indexers = List.of(stringIndexer, idIndexer);

        GigaMap<Entity> gigaMap = GigaMap.<Entity>Builder().
                withBitmapIndices(indexers).
                withValueEquality().
                build();

        gigaMap.add(new Entity("1000", 1));
        gigaMap.add(new Entity("1000", 2));

        gigaMap.remove(new Entity("1000", 1));
        gigaMap.remove(new Entity("1000", 2));

        assertEquals(0, gigaMap.size());

    }

    @Test
    void withIdentityEquality_test()
    {
        StringIndexer stringIndexer = new StringIndexer();
        IdIndexer idIndexer = new IdIndexer();

        List<? extends Indexer.Abstract<Entity, ? extends Serializable>> indexers = List.of(stringIndexer, idIndexer);

        GigaMap<Entity> gigaMap = GigaMap.<Entity>Builder().
                withBitmapIndices(indexers).
                withIdentityEquality().
                build();

        Entity entity = new Entity("1000", 1);
        gigaMap.add(entity);
        Entity entity2 = new Entity("1000", 2);
        gigaMap.add(entity2);

        assertEquals(-1, gigaMap.remove(new Entity("1000", 1)));
        assertEquals(-1, gigaMap.remove(new Entity("1000", 2)));

        assertEquals(2, gigaMap.size());

        gigaMap.remove(entity);
        gigaMap.remove(entity2);

        assertEquals(0, gigaMap.size());
    }

    private static class StringIndexer extends BinaryIndexerString.Abstract<Entity>
    {
    	@Override
    	protected String getString(final Entity entity)
        {
            return entity.value;
        }
    }

    private static class IdIndexer extends BinaryIndexerNumber.Abstract<Entity, Integer>
    {
        @Override
        protected Integer getNumber(final Entity entity)
        {
            return entity.id;
        }
    }


    private static class Entity {
        private final String value;
        private final int id;

        public Entity(String value, int id)
        {
            this.value = value;
            this.id = id;
        }

        @Override
        public boolean equals(Object o)
        {
            if (o == null || getClass() != o.getClass()) return false;
            Entity entity = (Entity) o;
            return id == entity.id && Objects.equals(value, entity.value);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(value, id);
        }
    }

}
