package org.eclipse.store.gigamap.indexer.annotation.custom;

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

import org.eclipse.serializer.hashing.XHashing;
import org.eclipse.store.gigamap.types.BitmapIndices;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.Indexer;
import org.eclipse.store.gigamap.types.IndexerGenerator;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;


public class CustomTypeIndexTest
{
    @TempDir
    Path tempDir;

    @Test
    void allAnnotationBaseTypeIndexTest()
    {
        final GigaMap<CustomTypeEntity>       gigaMap       = GigaMap.New(XHashing.hashEqualityValue());
        final BitmapIndices<CustomTypeEntity> bitmapIndices = gigaMap.index().bitmap();
        IndexerGenerator.AnnotationBased(CustomTypeEntity.class).generateIndices(bitmapIndices);

        gigaMap.add(new CustomTypeEntity(new CustomType("a")));
        gigaMap.add(new CustomTypeEntity(new CustomType("b")));
        
        this.checkIndices(gigaMap, bitmapIndices);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(gigaMap, this.tempDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(this.tempDir)) {
            final GigaMap<CustomTypeEntity> map2 = (GigaMap<CustomTypeEntity>) storageManager.root();
            this.checkIndices(map2, map2.index().bitmap());
        }

    }

    @Test
    void allAnnotationBaseTypeIndex_updateApi_Test()
    {
        final GigaMap<CustomTypeEntity> gigaMap = GigaMap.New(XHashing.hashEqualityValue());
        final BitmapIndices<CustomTypeEntity> bitmapIndices = gigaMap.index().bitmap();
        IndexerGenerator.AnnotationBased(CustomTypeEntity.class).generateIndices(bitmapIndices);

        gigaMap.add(new CustomTypeEntity(new CustomType("a")));
        gigaMap.add(new CustomTypeEntity(new CustomType("b")));

        this.checkIndices(gigaMap, bitmapIndices);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(gigaMap, this.tempDir)) {
        }

        final GigaMap<CustomTypeEntity> map2 = GigaMap.New();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(map2, this.tempDir)) {
            this.checkIndices(map2, map2.index().bitmap());
        }

    }

    private void checkIndices(final GigaMap<CustomTypeEntity> gigaMap, final BitmapIndices<CustomTypeEntity> bitmapIndices)
    {
        Indexer<CustomTypeEntity, CustomType> indexer = bitmapIndices.getIndexerForKey(CustomType.class, "customType");
        List<CustomTypeEntity>                list    = gigaMap.query(indexer.is(new CustomType("a"))).toList();
        assertEquals(1, list.size());
        assertEquals("a", list.get(0).customType().value());
    }
    
}
