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

import org.eclipse.store.gigamap.types.BitmapIndices;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.IndexerGenerator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LocalDateTest
{

    @Test
    void testLocalDate_nullValue_index_annotation()
    {
        final GigaMap<LocalDatePojo> gigaMap = GigaMap.New();
        final BitmapIndices<LocalDatePojo> bitmapIndices = gigaMap.index().bitmap();
        IndexerGenerator.AnnotationBased(LocalDatePojo.class).generateIndices(bitmapIndices);

        final LocalDatePojo pojo = new LocalDatePojo(null);
        gigaMap.add(pojo);
        
        final long count = gigaMap.query(bitmapIndices.getIndexerLocalDate("date").isNull()).count();
        assertEquals(1, count);
    }
}
