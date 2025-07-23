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

import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.IndexerYearMonth;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.*;

public class YearMonthIndexTest
{

    @Test
    void allInOneTest()
    {
        GigaMap<YMEntity> map = GigaMap.New();
        YMIndexer indexer = new YMIndexer();
        map.index().bitmap().add(indexer);

        YMEntity entity1 = new YMEntity(YearMonth.of(2024, 10));
        YMEntity entity3 = new YMEntity(null);

        map.add(entity1);
        map.add(entity3);

        assertAll(
                () -> assertEquals(1, map.query(indexer.isYearMonth(2024, 10)).count()),
                () -> assertThrows(IllegalArgumentException.class, () -> map.query(indexer.isYearMonth(2024, 50)).count()),

                () -> assertEquals(1, map.query(indexer.isYearMonth(2024, 10)).count()),
                () -> assertEquals(1, map.query(indexer.is((YearMonth) null)).count()),
                () -> assertEquals(1, map.query(indexer.isYear(2024)).count()),
                () -> assertEquals(1, map.query(indexer.isMonth(10)).count()),

                () -> assertThrows(IllegalArgumentException.class, () -> map.query(indexer.before(null)).count()),
                () -> assertEquals(1, map.query(indexer.before(YearMonth.of(2025, 1))).count()),

                () -> assertEquals(1, map.query(indexer.beforeEqual(YearMonth.of(2024, 10))).count()),
                () -> assertThrows(IllegalArgumentException.class, () -> map.query(indexer.beforeEqual(null)).count()),

                () -> assertEquals(1, map.query(indexer.after(YearMonth.of(2024, 9))).count()),
                () -> assertThrows(IllegalArgumentException.class, () -> map.query(indexer.after(null)).count()),

                () -> assertEquals(1, map.query(indexer.afterEqual(YearMonth.of(2024, 10))).count()),
                () -> assertThrows(IllegalArgumentException.class, () -> map.query(indexer.afterEqual(null)).count()),

                () -> assertThrows(IllegalArgumentException.class, () -> map.query(indexer.between(null, null)).count()),
                () -> assertThrows(IllegalArgumentException.class, () -> map.query(indexer.between(YearMonth.of(2024, 10), null)).count()),
                () -> assertThrows(IllegalArgumentException.class, () -> map.query(indexer.between(null, YearMonth.of(2024, 10))).count()),
                () -> assertEquals(1, map.query(indexer.between(YearMonth.of(2024, 10), YearMonth.of(2024, 10))).count())
        );
    }

    @Test
    void is()
    {
        GigaMap<YMEntity> map = GigaMap.New();
        YMIndexer indexer = new YMIndexer();
        map.index().bitmap().add(indexer);

        YMEntity entity1 = new YMEntity(YearMonth.of(2024, 10));
        YMEntity entity2 = new YMEntity(YearMonth.of(2023, 11));
        YMEntity entity3 = new YMEntity(null);

        map.add(entity1);
        map.add(entity2);
        map.add(entity3);

        assertEquals(1, map.query(indexer.is(YearMonth.of(2024, 10))).count());
        assertEquals(1, map.query(indexer.is(YearMonth.of(2023, 11))).count());
        assertEquals(1, map.query(indexer.is((YearMonth) null)).count());
    }

    @Test
    void yearTest()
    {
        YMIndexer indexer = new YMIndexer();
        GigaMap<YMEntity> map = GigaMap.New();
        map.index().bitmap().add(indexer);

        YMEntity entity1 = new YMEntity(YearMonth.of(2024, 10));
        YMEntity entity2 = new YMEntity(YearMonth.of(2023, 11));
        YMEntity entity3 = new YMEntity(YearMonth.of(2022, 10));

        map.add(entity1);
        map.add(entity2);
        map.add(entity3);

        assertEquals(1, map.query(indexer.isYear(2024)).count());
        assertEquals(1, map.query(indexer.isYear(2023)).count());
        assertEquals(1, map.query(indexer.isYear(2022)).count());
    }

    private static class YMIndexer extends IndexerYearMonth.Abstract<YMEntity>
    {

        @Override
        protected YearMonth getYearMonth(YMEntity entity)
        {
            return entity.getYearMonthField();
        }
    }

    private static class YMEntity
    {
        private final YearMonth yearMonthField;

        public YMEntity(YearMonth yearMonthField)
        {
            this.yearMonthField = yearMonthField;
        }

        public YearMonth getYearMonthField()
        {
            return yearMonthField;
        }

        @Override
        public String toString()
        {
            return "YMEntity{" +
                    "yearMonthField=" + yearMonthField +
                    '}';
        }
    }
}
