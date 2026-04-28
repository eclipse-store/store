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

import org.eclipse.store.gigamap.types.Condition;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.IndexerString;
import org.eclipse.store.gigamap.types.IndexerYearMonth;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.List;

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

    /**
     * Regression test for issue #653 / #654: composing an {@link IndexerYearMonth} range
     * condition via {@link Condition#and(Condition)} (i) with another range condition and
     * (ii) with a condition from a different indexer type must produce the same result as
     * composing via {@link org.eclipse.store.gigamap.types.GigaQuery#and(Condition)}.
     */
    @Test
    void rangeCompositionViaConditionAnd()
    {
        final NamedYMIndexer ymIdx   = new NamedYMIndexer();
        final NameIndex      nameIdx = new NameIndex();

        final GigaMap<NamedYMEntity> map = GigaMap.<NamedYMEntity>Builder()
            .withBitmapIndex(ymIdx)
            .withBitmapIndex(nameIdx)
            .build();
        map.add(new NamedYMEntity("Alice",   YearMonth.of(2023,  3)));
        map.add(new NamedYMEntity("Bob",     YearMonth.of(2023,  9)));
        map.add(new NamedYMEntity("Charlie", YearMonth.of(2024,  3)));
        map.add(new NamedYMEntity("Dave",    YearMonth.of(2024, 11)));

        final YearMonth lower = YearMonth.of(2023, 6); // exclusive
        final YearMonth upper = YearMonth.of(2024, 9); // exclusive

        // (i) range AND range
        final Condition<NamedYMEntity> after  = ymIdx.after(lower);
        final Condition<NamedYMEntity> before = ymIdx.before(upper);

        final List<NamedYMEntity> rangeAnd = map.query(after.and(before)).toList();
        final List<NamedYMEntity> rangeQ   = map.query().and(after).and(before).toList();

        assertEquals(2, rangeAnd.size(), "Condition.and() must respect both bounds");
        assertEquals(rangeQ.size(), rangeAnd.size(), "Condition.and() and GigaQuery.and() must agree");
        rangeAnd.forEach(e -> assertNotEquals("Alice", e.name()));
        rangeAnd.forEach(e -> assertNotEquals("Dave",  e.name()));

        // (ii) range AND condition from a different indexer
        final Condition<NamedYMEntity> bobMatch = nameIdx.is("Bob");

        final List<NamedYMEntity> mixedAnd = map.query(after.and(bobMatch)).toList();
        final List<NamedYMEntity> mixedQ   = map.query().and(after).and(bobMatch).toList();

        assertEquals(1, mixedAnd.size(), "range AND non-range condition must intersect correctly");
        assertEquals("Bob", mixedAnd.get(0).name());
        assertEquals(mixedQ.size(), mixedAnd.size());
    }

    private static class YMIndexer extends IndexerYearMonth.Abstract<YMEntity>
    {

        @Override
        protected YearMonth getYearMonth(YMEntity entity)
        {
            return entity.getYearMonthField();
        }
    }

    private static class NamedYMIndexer extends IndexerYearMonth.Abstract<NamedYMEntity>
    {
        @Override
        protected YearMonth getYearMonth(final NamedYMEntity entity)
        {
            return entity.yearMonth();
        }
    }

    private static class NameIndex extends IndexerString.Abstract<NamedYMEntity>
    {
        @Override
        protected String getString(final NamedYMEntity entity)
        {
            return entity.name();
        }
    }

    private static class NamedYMEntity
    {
        private final String    name;
        private final YearMonth yearMonth;

        NamedYMEntity(final String name, final YearMonth yearMonth)
        {
            this.name      = name;
            this.yearMonth = yearMonth;
        }

        String name()
        {
            return this.name;
        }

        YearMonth yearMonth()
        {
            return this.yearMonth;
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
