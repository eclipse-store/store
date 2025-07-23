package org.eclipse.store.gigamap.indexer.annotation;

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

import org.eclipse.store.gigamap.types.*;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


public class AllTypeIndexTest
{
    @TempDir
    Path tempDir;

    @Test
    void nullValuesTest()
    {
        GigaMap<AllTypeEntity>       gigaMap       = GigaMap.New();
        BitmapIndices<AllTypeEntity> bitmapIndices = gigaMap.index().bitmap();
        IndexerGenerator.AnnotationBased(AllTypeEntity.class).generateIndices(bitmapIndices);

        AllTypeEntity entity = new AllTypeEntityBuilder()
                .setStringField(null)
                .setIntField(null)
                .setLongField(null)
                .setDoubleField(null)
                .setFloatField(null)
                .setBooleanField(null)
                .setCharField(null)
                .setShortField(null)
                .createAllTypeEntity();
        gigaMap.add(entity);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(gigaMap, tempDir)) {
        }
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(tempDir)) {
            GigaMap<AllTypeEntity> map2 = (GigaMap<AllTypeEntity>) storageManager.root();

            final IndexerString<AllTypeEntity> stringIndex = map2.index().bitmap().getIndexerString("stringField");
            List<AllTypeEntity> list = map2.query(stringIndex.isNull()).toList();
            assertEquals(1, list.size());
        }

    }

    @Test
    void allAnnotationBaseTypeIndexTest()
    {
        GigaMap<AllTypeEntity> gigaMap = GigaMap.New();
        BitmapIndices<AllTypeEntity> bitmapIndices = gigaMap.index().bitmap();
        IndexerGenerator.AnnotationBased(AllTypeEntity.class).generateIndices(bitmapIndices);

        fillGigaMap(gigaMap);

        checkIndices(gigaMap, bitmapIndices);


        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(gigaMap, tempDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(tempDir)) {
            GigaMap<AllTypeEntity> map2 = (GigaMap<AllTypeEntity>) storageManager.root();

            checkIndices(map2, map2.index().bitmap());
        }

    }

    private void checkIndices(GigaMap<AllTypeEntity> gigaMap, BitmapIndices<AllTypeEntity> bitmapIndices)
    {
        final IndexerInteger<AllTypeEntity> intIndex = bitmapIndices.getIndexerInteger("intField");
        List<AllTypeEntity>                 list     = gigaMap.query(intIndex.is(1)).toList();
        assertEquals(1, list.size());
        assertEquals(1, list.get(0).getIntField());

        final IndexerString<AllTypeEntity> stringIndex = bitmapIndices.getIndexerString("stringField");
        list = gigaMap.query(stringIndex.is("string2")).toList();
        assertEquals(1, list.size());
        assertEquals("string2", list.get(0).getStringField());

        final IndexerLong<AllTypeEntity> longIndex = bitmapIndices.getIndexerLong("longField");
        list = gigaMap.query(longIndex.is(3L)).toList();
        assertEquals(1, list.size());
        assertEquals(3L, list.get(0).getLongField());

        final IndexerDouble<AllTypeEntity> doubleIndex = bitmapIndices.getIndexerDouble("doubleField");
        list = gigaMap.query(doubleIndex.is(3.0)).toList();
        assertEquals(1, list.size());
        assertEquals(3.0, list.get(0).getDoubleField());

        final IndexerFloat<AllTypeEntity> floatIndex = bitmapIndices.getIndexerFloat("floatField");
        list = gigaMap.query(floatIndex.is(3.0f)).toList();
        assertEquals(1, list.size());
        assertEquals(3.0f, list.get(0).getFloatField());

        final IndexerBoolean<AllTypeEntity> booleanIndex = bitmapIndices.getIndexerBoolean("booleanField");
        list = gigaMap.query(booleanIndex.is(true)).toList();
        assertEquals(2, list.size());
        assertTrue(list.get(0).isBooleanField());

        final IndexerCharacter<AllTypeEntity> charIndex = bitmapIndices.getIndexerCharacter("charField");
        list = gigaMap.query(charIndex.is('d')).toList();
        assertEquals(1, list.size());
        assertEquals('d', list.get(0).getCharField());

        final IndexerShort<AllTypeEntity> shortIndex = bitmapIndices.getIndexerShort("shortField");
        list = gigaMap.query(shortIndex.is((short) 3)).toList();
        assertEquals(1, list.size());
        assertEquals((short) 3, list.get(0).getShortField());

    }

    private void fillGigaMap(GigaMap<AllTypeEntity> gigaMap)
    {
        AllTypeEntity entity = new AllTypeEntityBuilder()
                .setStringField("string")
                .setIntField(1)
                .setLongField(1L)
                .setDoubleField(1.0)
                .setFloatField(1.0f)
                .setBooleanField(true)
                .setCharField('c')
                .setShortField((short) 1)
                .createAllTypeEntity();
        gigaMap.add(entity);

        //add more entities
        gigaMap.add(new AllTypeEntityBuilder()
                .setStringField("string2")
                .setIntField(2)
                .setLongField(2L)
                .setDoubleField(2.0)
                .setFloatField(2.0f)
                .setBooleanField(false)
                .setCharField('d')
                .setShortField((short) 2)
                .createAllTypeEntity());
        gigaMap.add(new AllTypeEntityBuilder()
                .setStringField("string3")
                .setIntField(3)
                .setLongField(3L)
                .setDoubleField(3.0)
                .setFloatField(3.0f)
                .setBooleanField(true)
                .setCharField('e')
                .setShortField((short) 3)
                .createAllTypeEntity()
        );
    }
}
