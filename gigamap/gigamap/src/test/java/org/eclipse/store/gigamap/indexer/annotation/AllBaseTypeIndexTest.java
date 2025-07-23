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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class AllBaseTypeIndexTest
{

    static UUID uid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

    @TempDir
    Path tempDir;

    @Test
    void allAnnotationBaseTypeIndexTest()
    {
        final GigaMap<AllIBaseTypeEntity> gigaMap = GigaMap.New();
        final BitmapIndices<AllIBaseTypeEntity> bitmapIndices = gigaMap.index().bitmap();
        IndexerGenerator.AnnotationBased(AllIBaseTypeEntity.class).generateIndices(bitmapIndices);

        this.fillGigaMap(gigaMap);

        this.checkIndices(gigaMap, bitmapIndices);


        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(gigaMap, this.tempDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(this.tempDir)) {
            final GigaMap<AllIBaseTypeEntity> map2 = (GigaMap<AllIBaseTypeEntity>) storageManager.root();

            this.checkIndices(map2, map2.index().bitmap());
        }

    }

    private void checkIndices(final GigaMap<AllIBaseTypeEntity> gigaMap, final BitmapIndices<AllIBaseTypeEntity> bitmapIndices)
    {
        final IndexerInteger<AllIBaseTypeEntity> intIndex = bitmapIndices.getIndexerInteger("intField");
        List<AllIBaseTypeEntity>                 list     = gigaMap.query(intIndex.is(1)).toList();
        assertEquals(1, list.size());
        assertEquals(1, list.get(0).getIntField());

        final IndexerString<AllIBaseTypeEntity> stringIndex = bitmapIndices.getIndexerString("stringField");
        list = gigaMap.query(stringIndex.is("string2")).toList();
        assertEquals(1, list.size());
        assertEquals("string2", list.get(0).getStringField());

        final IndexerLong<AllIBaseTypeEntity> longIndex = bitmapIndices.getIndexerLong("longField");
        list = gigaMap.query(longIndex.is(3L)).toList();
        assertEquals(1, list.size());
        assertEquals(3L, list.get(0).getLongField());

        final IndexerDouble<AllIBaseTypeEntity> doubleIndex = bitmapIndices.getIndexerDouble("doubleField");
        list = gigaMap.query(doubleIndex.is(3.0)).toList();
        assertEquals(1, list.size());
        assertEquals(3.0, list.get(0).getDoubleField());

        final IndexerFloat<AllIBaseTypeEntity> floatIndex = bitmapIndices.getIndexerFloat("floatField");
        list = gigaMap.query(floatIndex.is(3.0f)).toList();
        assertEquals(1, list.size());
        assertEquals(3.0f, list.get(0).getFloatField());

        final IndexerBoolean<AllIBaseTypeEntity> booleanIndex = bitmapIndices.getIndexerBoolean("booleanField");
        list = gigaMap.query(booleanIndex.is(true)).toList();
        assertEquals(2, list.size());
        assertTrue(list.get(0).isBooleanField());

        final IndexerCharacter<AllIBaseTypeEntity> charIndex = bitmapIndices.getIndexerCharacter("charField");
        list = gigaMap.query(charIndex.is('d')).toList();
        assertEquals(1, list.size());
        assertEquals('d', list.get(0).getCharField());

        final IndexerByte<AllIBaseTypeEntity> byteIndex = bitmapIndices.getIndexerByte("byteField");
        list = gigaMap.query(byteIndex.is((byte) 3)).toList();
        assertEquals(1, list.size());
        assertEquals((byte) 3, list.get(0).getByteField());

        final IndexerShort<AllIBaseTypeEntity> shortIndex = bitmapIndices.getIndexerShort("shortField");
        list = gigaMap.query(shortIndex.is((short) 3)).toList();
        assertEquals(1, list.size());
        assertEquals((short) 3, list.get(0).getShortField());

        final BinaryIndexerUUID<AllIBaseTypeEntity> uuidIndex = bitmapIndices.getIndexerUUID("uuidField");
        assertNotNull(uuidIndex);
        list = gigaMap.query(uuidIndex.is(uid)).toList();
        assertEquals(3, list.size());
        assertEquals(uid, list.get(0).getUuidField());
        
        IndexerMultiValue<AllIBaseTypeEntity, String> multiValueIndex = bitmapIndices.getIndexerMultiValue("arrayField");
        assertNotNull(multiValueIndex);
        list = gigaMap.query(multiValueIndex.in("a1", "a2")).toList();
        assertEquals(2, list.size());
        
        multiValueIndex = bitmapIndices.getIndexerMultiValue("listField");
        assertNotNull(multiValueIndex);
        list = gigaMap.query(multiValueIndex.in("a1", "a2")).toList();
        assertEquals(2, list.size());
    }

    private void fillGigaMap(final GigaMap<AllIBaseTypeEntity> gigaMap)
    {
        final AllIBaseTypeEntity entity = new AllIBaseTypeEntityBuilder()
                .setStringField("string")
                .setIntField(1)
                .setLongField(1L)
                .setDoubleField(1.0)
                .setFloatField(1.0f)
                .setBooleanField(true)
                .setCharField('c')
                .setByteField((byte) 1)
                .setShortField((short) 1)
                .setUuidField(uid)
                .setArrayField("a1", "b1", "c1")
                .build();
        gigaMap.add(entity);

        //add more entities
        gigaMap.add(new AllIBaseTypeEntityBuilder()
                .setStringField("string2")
                .setIntField(2)
                .setLongField(2L)
                .setDoubleField(2.0)
                .setFloatField(2.0f)
                .setBooleanField(false)
                .setCharField('d')
                .setByteField((byte) 2)
                .setShortField((short) 2)
                .setUuidField(uid)
                .setArrayField("a2", "b2", "c2")
                .build());
        gigaMap.add(new AllIBaseTypeEntityBuilder()
                .setStringField("string3")
                .setIntField(3)
                .setLongField(3L)
                .setDoubleField(3.0)
                .setFloatField(3.0f)
                .setBooleanField(true)
                .setCharField('e')
                .setByteField((byte) 3)
                .setShortField((short) 3)
                .setUuidField(uid)
                .setArrayField("a3", "b3", "c3")
                .build());
    }
}
