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
import org.eclipse.store.gigamap.exceptions.UniqueConstraintViolationException;
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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;


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
            GigaMap<Entity> map2 = storageManager.root();
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
            GigaMap<Entity> map2 = storageManager.root();
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
            GigaMap<Entity> map2 = storageManager.root();
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
            GigaMap<Entity> map2 = storageManager.root();
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
            GigaMap<Entity> map2 = storageManager.root();
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
            GigaMap<Entity> map2 = storageManager.root();
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
            GigaMap<Entity> map2 = storageManager.root();
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
            GigaMap<Entity> map2 = storageManager.root();
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

    // ---------------------------------------------------------------
    // Same indexer passed to multiple with...Index methods.
    //
    // build() must register the underlying bitmap index only once and
    // apply every requested role (plain bitmap / identity / unique).
    // ---------------------------------------------------------------

    @Test
    void sameIndexer_bitmapPlusIdentity()
    {
        final StringIndexer stringIndexer = new StringIndexer();

        final GigaMap<Entity> gigaMap = GigaMap.<Entity>Builder()
            .withBitmapIndex(stringIndexer)
            .withBitmapIdentityIndex(stringIndexer)
            .build();

        gigaMap.add(new Entity("1000", 1));
        gigaMap.add(new Entity("2000", 2));

        // bitmap behavior: query works
        assertEquals("1000", gigaMap.query(stringIndexer.is("1000")).findFirst().get().value);
        // identity is registered (single index, same name as the plain bitmap)
        assertEquals(1, gigaMap.index().bitmap().identityIndices().size());
        assertSame(
            gigaMap.index().bitmap().get(stringIndexer.keyType(), stringIndexer.name()),
            gigaMap.index().bitmap().identityIndices().get()
        );
    }

    @Test
    void sameIndexer_bitmapPlusUnique()
    {
        final StringIndexer stringIndexer = new StringIndexer();

        final GigaMap<Entity> gigaMap = GigaMap.<Entity>Builder()
            .withBitmapIndex(stringIndexer)
            .withBitmapUniqueIndex(stringIndexer)
            .build();

        gigaMap.add(new Entity("1000", 1));

        // bitmap behavior: query works
        assertEquals("1000", gigaMap.query(stringIndexer.is("1000")).findFirst().get().value);
        // unique behavior: duplicate is rejected
        assertThrows(UniqueConstraintViolationException.class,
            () -> gigaMap.add(new Entity("1000", 2)));
    }

    @Test
    void sameIndexer_identityPlusUnique()
    {
        final StringIndexer stringIndexer = new StringIndexer();

        final GigaMap<Entity> gigaMap = GigaMap.<Entity>Builder()
            .withBitmapIdentityIndex(stringIndexer)
            .withBitmapUniqueIndex(stringIndexer)
            .build();

        gigaMap.add(new Entity("1000", 1));

        // identity is registered, points to the same backing index as the unique constraint
        assertEquals(1, gigaMap.index().bitmap().identityIndices().size());
        assertSame(
            gigaMap.index().bitmap().identityIndices().get(),
            gigaMap.index().bitmap().uniqueConstraints().get()
        );
        // unique behavior: duplicate is rejected
        assertThrows(UniqueConstraintViolationException.class,
            () -> gigaMap.add(new Entity("1000", 2)));
    }

    @Test
    void sameIndexer_bitmapPlusIdentityPlusUnique()
    {
        final StringIndexer stringIndexer = new StringIndexer();

        final GigaMap<Entity> gigaMap = GigaMap.<Entity>Builder()
            .withBitmapIndex(stringIndexer)
            .withBitmapIdentityIndex(stringIndexer)
            .withBitmapUniqueIndex(stringIndexer)
            .build();

        gigaMap.add(new Entity("1000", 1));

        // a single underlying index serves all three roles
        assertEquals(1, gigaMap.index().bitmap().identityIndices().size());
        assertEquals(1, gigaMap.index().bitmap().uniqueConstraints().size());
        assertSame(
            gigaMap.index().bitmap().identityIndices().get(),
            gigaMap.index().bitmap().uniqueConstraints().get()
        );
        // bitmap query works
        assertEquals("1000", gigaMap.query(stringIndexer.is("1000")).findFirst().get().value);
        // unique behavior is enforced
        assertThrows(UniqueConstraintViolationException.class,
            () -> gigaMap.add(new Entity("1000", 2)));
    }

    @Test
    void distinctIndexers_acrossAllThreeMethods()
    {
        // regression check: distinct indexers in different methods still produce
        // three separate bitmap indices with the right roles.
        final StringIndexer plainIndexer    = new StringIndexer();
        final IdIndexer     identityIndexer = new IdIndexer();
        final UniqueIdIndexer uniqueIndexer = new UniqueIdIndexer();

        final GigaMap<Entity> gigaMap = GigaMap.<Entity>Builder()
            .withBitmapIndex(plainIndexer)
            .withBitmapIdentityIndex(identityIndexer)
            .withBitmapUniqueIndex(uniqueIndexer)
            .build();

        gigaMap.add(new Entity("1000", 1));

        assertEquals(1, gigaMap.index().bitmap().identityIndices().size());
        assertEquals(1, gigaMap.index().bitmap().uniqueConstraints().size());
        // unique constraint is the uniqueIndexer, not the identity one
        assertEquals(uniqueIndexer.name(),
            gigaMap.index().bitmap().uniqueConstraints().get().name());
        assertEquals(identityIndexer.name(),
            gigaMap.index().bitmap().identityIndices().get().name());
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

    // a second number indexer with a distinct name, for the distinct-indexers test
    private static class UniqueIdIndexer extends BinaryIndexerNumber.Abstract<Entity, Integer>
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
