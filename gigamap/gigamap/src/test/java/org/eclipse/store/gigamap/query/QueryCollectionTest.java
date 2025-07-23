package org.eclipse.store.gigamap.query;

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
import org.eclipse.store.gigamap.types.IndexerInteger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class QueryCollectionTest
{

    @Test
    void toListTest_returnTheCollectionWithAllElements()
    {
        final GigaMap<Integer> gigaMap = GigaMap.New();
        final ValueIndexer valueIndexer = new ValueIndexer();
        gigaMap.index().bitmap().add(valueIndexer);

        final List<Integer> list = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            gigaMap.add(i);
            list.add(i);
        }

        final List<Integer> list1 = gigaMap.query(valueIndexer.between(0,9)).toList();
        Assertions.assertIterableEquals(list, list1);
    }

    @Test
    void toListLimitTest_returnSubCollection()
    {
        final GigaMap<Integer> gigaMap = GigaMap.New();
        final ValueIndexer valueIndexer = new ValueIndexer();
        gigaMap.index().bitmap().add(valueIndexer);

        for (int i = 0; i < 10; i++) {
            gigaMap.add(i);
        }

        final List<Integer> list = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            list.add(i);
        }
        final List<Integer> list1 = gigaMap.query(valueIndexer.between(0,9)).toList(5);
        Assertions.assertIterableEquals(list, list1);
    }

    @Test
    void toListLimitOffsetTest_returnSubCollection()
    {
        final GigaMap<Integer> gigaMap = GigaMap.New();
        final ValueIndexer valueIndexer = new ValueIndexer();
        gigaMap.index().bitmap().add(valueIndexer);

        for (int i = 0; i < 10; i++) {
            gigaMap.add(i);
        }

        final List<Integer> list = new ArrayList<>();
        for (int i = 5; i < 10; i++) {
            list.add(i);
        }
        final List<Integer> list1 = gigaMap.query(valueIndexer.between(0,9)).toList(5,5);
        Assertions.assertIterableEquals(list, list1);
    }

    @Test
    void toSetTest_returnSetWithAllElements()
    {
        final GigaMap<Integer> gigaMap = GigaMap.New();
        final ValueIndexer valueIndexer = new ValueIndexer();
        gigaMap.index().bitmap().add(valueIndexer);

        final Set<Integer> set = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            gigaMap.add(i);
            set.add(i);
        }

        final Set<Integer> set1 = gigaMap.query(valueIndexer.between(0,9)).toSet();
        Assertions.assertEquals(set, set1);
    }

    @Test
    void toSetLimitTest_returnSetToLimit()
    {
        final GigaMap<Integer> gigaMap = GigaMap.New();
        final ValueIndexer valueIndexer = new ValueIndexer();
        gigaMap.index().bitmap().add(valueIndexer);

        for (int i = 0; i < 10; i++) {
            gigaMap.add(i);
        }

        final Set<Integer> set = new HashSet<>();
        for (int i = 0; i < 5; i++) {
            set.add(i);
        }
        final Set<Integer> set1 = gigaMap.query(valueIndexer.between(0,9)).toSet(5);
        Assertions.assertEquals(set, set1);
    }

    @Test
    void toSetLimitOffsetTest()
    {
        final GigaMap<Integer> gigaMap = GigaMap.New();
        final ValueIndexer valueIndexer = new ValueIndexer();
        gigaMap.index().bitmap().add(valueIndexer);

        for (int i = 0;  i < 10; i++) {
            gigaMap.add(i);
        }

        final Set<Integer> set = new HashSet<>();
        for (int i = 5; i < 10; i++) {
            set.add(i);
        }
        final Set<Integer> set1 = gigaMap.query(valueIndexer.between(0,9)).toSet(5,5);
        Assertions.assertEquals(set, set1);
    }

    @Test
    void toSetBoundaryTest_withLowerBoundaryElement_returnsSetWithElement() {
        final GigaMap<Integer> gigaMap = GigaMap.New();
        final ValueIndexer valueIndexer = new ValueIndexer();
        gigaMap.index().bitmap().add(valueIndexer);

        gigaMap.add(0);
        final Set<Integer> set = gigaMap.query(valueIndexer.between(0, 0)).toSet();
        Assertions.assertEquals(1, set.size());
        assertTrue(set.contains(0));
    }

    @Test
    void toSetBoundaryTest_withUpperBoundaryElement_returnsSetWithElement() {
        final GigaMap<Integer> gigaMap = GigaMap.New();
        final ValueIndexer valueIndexer = new ValueIndexer();
        gigaMap.index().bitmap().add(valueIndexer);

        gigaMap.add(9);
        final Set<Integer> set = gigaMap.query(valueIndexer.between(9, 9)).toSet();
        Assertions.assertEquals(1, set.size());
        assertTrue(set.contains(9));
    }

    @Test
    void toSetBoundaryTest_withElementsOnBoundaries_returnsSetWithBoundaryElements() {
        final GigaMap<Integer> gigaMap = GigaMap.New();
        final ValueIndexer valueIndexer = new ValueIndexer();
        gigaMap.index().bitmap().add(valueIndexer);

        gigaMap.add(0);
        gigaMap.add(9);
        final Set<Integer> set = gigaMap.query(valueIndexer.between(0, 9)).toSet();
        Assertions.assertEquals(2, set.size());
        assertTrue(set.contains(0));
        assertTrue(set.contains(9));
    }

    @Test
    void negativeOffsetTest()
    {
        final GigaMap<Integer> gigaMap = GigaMap.New();
        final ValueIndexer valueIndexer = new ValueIndexer();
        gigaMap.index().bitmap().add(valueIndexer);

        for (int i = 0; i < 10; i++) {
            gigaMap.add(i);
        }

        assertThrows(IllegalArgumentException.class,
                () -> gigaMap.query(valueIndexer.between(0,9)).toSet(-5,5));
    }

    @Test
    void negativeLimitTest()
    {
        final GigaMap<Integer> gigaMap = GigaMap.New();
        final ValueIndexer valueIndexer = new ValueIndexer();
        gigaMap.index().bitmap().add(valueIndexer);

        for (int i = 0; i < 10; i++) {
            gigaMap.add(i);
        }

        assertThrows(IllegalArgumentException.class,
            () -> gigaMap.query(valueIndexer.between(0, 9)).toSet(5, -5));
    }

    @Test
    void toSet_withDuplicateElements_returnsSetWithUniqueElements() {
        final GigaMap<Integer> gigaMap = GigaMap.New();
        final ValueIndexer valueIndexer = new ValueIndexer();
        gigaMap.index().bitmap().add(valueIndexer);

        for (int i = 0; i < 10; i++) {
            gigaMap.add(i);
            gigaMap.add(i); // Add duplicate elements
        }

        final Set<Integer> set = gigaMap.query(valueIndexer.between(0, 9)).toSet();
        Assertions.assertEquals(10, set.size());
        for (int i = 0; i < 10; i++) {
            assertTrue(set.contains(i));
        }
    }

    @Test
    void toSet_withLargeNumberOfElements_returnsCorrectSet() {
        final GigaMap<Integer> gigaMap = GigaMap.New();
        final ValueIndexer valueIndexer = new ValueIndexer();
        gigaMap.index().bitmap().add(valueIndexer);

        final Set<Integer> expectedSet = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            gigaMap.add(i);
            expectedSet.add(i);
        }

        final Set<Integer> set = gigaMap.query(valueIndexer.between(0, 999)).toSet();
        Assertions.assertEquals(expectedSet, set);
    }

    @Test
    void toSet_withNonExistentElements_returnsEmptySet() {
        final GigaMap<Integer> gigaMap = GigaMap.New();
        final ValueIndexer valueIndexer = new ValueIndexer();
        gigaMap.index().bitmap().add(valueIndexer);

        final Set<Integer> set = gigaMap.query(valueIndexer.between(100, 200)).toSet();
        Assertions.assertTrue(set.isEmpty());
    }

    @Test
    void toList_countElements()
    {
        final GigaMap<Integer> gigaMap = GigaMap.New();
        final ValueIndexer valueIndexer = new ValueIndexer();
        gigaMap.index().bitmap().add(valueIndexer);

        for (int i = 0; i < 10; i++) {
            gigaMap.add(i);
        }

        Assertions.assertEquals(10, gigaMap.query().count());

        List<Integer> integerList = gigaMap.query().toList();
        Assertions.assertEquals(10, integerList.size());
    }

    static class ValueIndexer extends IndexerInteger.Abstract<Integer>
    {
        @Override
        protected Integer getInteger(final Integer entity)
        {
            return entity;
        }
    }
}
