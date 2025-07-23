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
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;


public class AllTimeApiIndexTest
{
    AllTimeApiTypeEntity entity1;
    AllTimeApiTypeEntity entity2;
    AllTimeApiTypeEntity entity3;

    @Test
    void nullTest()
    {
        final GigaMap<AllTimeApiTypeEntity>       gigaMap       = GigaMap.New();
        final BitmapIndices<AllTimeApiTypeEntity> bitmapIndices = gigaMap.index().bitmap();
        IndexerGenerator.AnnotationBased(AllTimeApiTypeEntity.class).generateIndices(bitmapIndices);

        final AllTimeApiTypeEntity entity = new AllTimeApiTypeEntity(null, null, null, null);
        gigaMap.add(entity);

        assertEquals(1, gigaMap.query(bitmapIndices.getIndexerLocalDateTime("localDateTimeField").isNull()).count());

        assertEquals(1, gigaMap.query(bitmapIndices.getIndexerLocalDate("localDateField").isNull()).count());

        assertEquals(1, gigaMap.query(bitmapIndices.getIndexerLocalTime("localTimeField").isNull()).count());

        IndexerYearMonth<AllTimeApiTypeEntity> ymIndex = bitmapIndices.getIndexerYearMonth("yearMonthField");
        assertEquals(1, gigaMap.query(ymIndex.isNull()).count());
        assertEquals(0, gigaMap.query(ymIndex.beforeEqual(YearMonth.now())).count());
        assertEquals(0, gigaMap.query(ymIndex.afterEqual(YearMonth.now())).count());
    }

    @Test
    void alltimeApiIndextest()
    {
        final GigaMap<AllTimeApiTypeEntity> gigaMap = GigaMap.New();
        final BitmapIndices<AllTimeApiTypeEntity> bitmapIndices = gigaMap.index().bitmap();
        IndexerGenerator.AnnotationBased(AllTimeApiTypeEntity.class).generateIndices(bitmapIndices);

        this.fillGigaMap(gigaMap);

        this.checkIndices(gigaMap, bitmapIndices);
    }

    private void fillGigaMap(final GigaMap<AllTimeApiTypeEntity> gigaMap)
    {
        this.entity1 = new AllTimeApiTypeEntity(
            LocalDateTime.now(),
            LocalDate.now(),
            LocalTime.now(),
            YearMonth.now()
        );

        this.entity2 = new AllTimeApiTypeEntity(
            LocalDateTime.now(),
            LocalDate.now(),
            LocalTime.now(),
            YearMonth.now()
        );

        this.entity3 = new AllTimeApiTypeEntity(
            LocalDateTime.now(),
            LocalDate.now(),
            LocalTime.now(),
            YearMonth.now()
        );

        gigaMap.addAll(this.entity1, this.entity2, this.entity3);
    }

	private void checkIndices(final GigaMap<AllTimeApiTypeEntity> gigaMap, final BitmapIndices<AllTimeApiTypeEntity> bitmapIndices)
    {
        final IndexerLocalDateTime<AllTimeApiTypeEntity> localDateTimeIndex = bitmapIndices.getIndexerLocalDateTime("localDateTimeField");
        assertNotNull(localDateTimeIndex);
        List<AllTimeApiTypeEntity> list = gigaMap.query(localDateTimeIndex.is(this.entity1.getLocalDateTimeField())).toList();
        assertEquals(3, list.size());
        assertEquals(this.entity1.localDateTimeField, list.get(0).getLocalDateTimeField());


        final IndexerLocalDate<AllTimeApiTypeEntity> localDateIndex = bitmapIndices.getIndexerLocalDate("localDateField");
        assertNotNull(localDateIndex);
        list = gigaMap.query(localDateIndex.is(entity1.getLocalDateField())).toList();
        assertEquals(3, list.size());
        assertEquals(entity1.localDateField, list.get(0).getLocalDateField());

        final IndexerLocalTime<AllTimeApiTypeEntity> localTimeIndex = bitmapIndices.getIndexerLocalTime("localTimeField");
        assertNotNull(localTimeIndex);
        list = gigaMap.query(localTimeIndex.is(entity1.getLocalTimeField())).toList();
        assertEquals(3, list.size());
        assertEquals(entity1.localTimeField, list.get(0).getLocalTimeField());

        final IndexerYearMonth<AllTimeApiTypeEntity> yearMonthIndex = bitmapIndices.getIndexerYearMonth("yearMonthField");
        assertNotNull(yearMonthIndex);
        list = gigaMap.query(yearMonthIndex.is(entity1.getYearMonthField())).toList();
        assertEquals(3, list.size());
        assertEquals(entity1.yearMonthField, list.get(0).getYearMonthField());
    }
}
