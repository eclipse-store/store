package test.eclipse.store.collections.lazy.unit;

/*-
 * #%L
 * MicroStream Base
 * %%
 * Copyright (C) 2019 - 2022 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.serializer.collections.lazy.LazyArrayList;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class LazyArrayListUnitTest
{

    @Test
    public void addItem()
    {
        LazyArrayList<Integer> list = new LazyArrayList<>();

        assertTrue(list.isEmpty());
        assertEquals(0, list.size());
        assertTrue(list.add(1));
        assertEquals(1, list.size());
        assertEquals(1, list.get(0));

        assertTrue(list.addAll(list));
        assertEquals(2, list.size());

        assertTrue(list.add(null));
        assertEquals(3, list.size());
    }

    @Test
    void addAll()
    {
        LazyArrayList<String> lazyArrayList = Util.generateLazyArrayList(10, 100);
        LazyArrayList<String> list = new LazyArrayList<>();
        assertFalse(lazyArrayList.addAll(list));
    }

    @Test
    void addAllEmpty()
    {
        LazyArrayList<String> lazyArrayList = Util.generateLazyArrayList(10, 100);
        LazyArrayList<String> list2 = new LazyArrayList<>();
        lazyArrayList.addAll(0, list2);
        assertEquals(100, lazyArrayList.size());
    }

    @Test
    void addAllToEnd()
    {
        LazyArrayList<String> lazyArrayList = Util.generateLazyArrayList(10, 100);
        LazyArrayList<String> list2 = Util.generateLazyArrayList(10, 100);
        lazyArrayList.addAll(100, list2);
        assertEquals(200, lazyArrayList.size());
    }

    @Test
    void addAllInside()
    {
        LazyArrayList<String> lazyArrayList = Util.generateLazyArrayList(10, 100);
        LazyArrayList<String> list2 = Util.generateLazyArrayList(10, 100);
        lazyArrayList.addAll(50, list2);
        assertEquals(200, lazyArrayList.size());
    }


    @Test
    void addItemSimple()
    {
        LazyArrayList<String> lazyArrayList = Util.generateLazyArrayList(10, 100);
        lazyArrayList.add(100, "SomeString");
        assertEquals(101, lazyArrayList.size());
    }

    @Test
    void getSegmentCountTest()
    {
        LazyArrayList<String> lazyArrayList = Util.generateLazyArrayList(10, 100);
        assertEquals(10, lazyArrayList.getSegmentCount());
    }

    @Test
    void iterateSegmentsTest()
    {
        LazyArrayList<String> lazyArrayList = Util.generateLazyArrayList(10, 100);
        for (LazyArrayList<String>.Segment segment : lazyArrayList.segments()) {
            assertTrue(segment.isLoaded());
        }
    }

    @Test
    void getMaxSegmentSize()
    {
        LazyArrayList<String> lazyArrayList = Util.generateLazyArrayList(10, 100);
        assertEquals(10, lazyArrayList.getMaxSegmentSize());
    }

    @Test
    public void isEmpty()
    {
        LazyArrayList<Integer> list = new LazyArrayList<>();
        assertTrue(list.isEmpty());
    }

    @Test
    public void contains()
    {
        String s = "Hello, i would to be a great object";

        LazyArrayList<String> list = new LazyArrayList<>();
        list.add(s);

        assertTrue(list.contains(s));

    }

    @Test
    public void containsHuge()
    {
        LazyArrayList<String> list = Stream.generate(() -> "Java")
                .limit(35000)
                .collect(Collectors.toCollection(LazyArrayList::new));

        assertTrue(list.contains("Java"));

    }

    @Test
    public void iterator()
    {
        String s = "Hello, i would to be a great object";

        LazyArrayList<String> list = new LazyArrayList<>();
        list.add(s);
        list.add(s);
        list.add(s);

        ListIterator<String> listIterator = list.listIterator();

        int i = 0;

        while (listIterator.hasNext()) {
            i++;
            listIterator.next();
        }

        assertEquals(3, i);
    }

    @Test
    public void toArray()
    {
        List<Integer> list = new LazyArrayList<>();
        list.add(1);
        list.add(2);
        list.add(3);
        Object[] intArray = list.toArray();

        Object[] expectIntArray = {1, 2, 3};
        assertArrayEquals(expectIntArray, intArray);
    }

    @Test
    public void toArrayWithType()
    {
        List<Integer> list = new LazyArrayList<>();
        list.add(1);
        list.add(2);
        list.add(3);
        Integer[] intArray = list.toArray(new Integer[0]);

        Integer[] expectIntArray = {1, 2, 3};
        assertArrayEquals(expectIntArray, intArray);
    }

    @Test
    public void toArrayWithTypeLongerArray()
    {
        List<Integer> list = new LazyArrayList<>();
        list.add(1);
        list.add(2);
        list.add(3);
        Integer[] intArray = list.toArray(new Integer[5]);
        assertEquals(5, intArray.length);
        Integer[] expectIntArray = {1, 2, 3, null, null};
        assertArrayEquals(expectIntArray, intArray);
    }

    @Test
    public void remove()
    {
        List<Integer> list = new LazyArrayList<>();
        list.add(1);
        list.add(2);
        list.add(3);
        list.remove(2);

        assertEquals(2, list.size());
    }

    @Test
    void removeObject()
    {
        LazyArrayList<String> lazyArrayList = Util.generateLazyArrayList(10, 100);
        String s = lazyArrayList.get(50);
        assertTrue(lazyArrayList.remove(s));
        assertEquals(99, lazyArrayList.size());
    }

    @Test
    void removeAllSameObject()
    {
        LazyArrayList<String> lazyArrayList = Stream.generate(() -> "Java")
                .limit(100)
                .collect(Collectors.toCollection(LazyArrayList::new));
        assertTrue(lazyArrayList.remove("Java"));
        assertEquals(99, lazyArrayList.size());
    }

    @Test
    void removeSegmentIndex()
    {
        LazyArrayList<String> list = Util.generateLazyArrayList(10, 101);
        assertNotNull(list.remove(100));
    }

    @Test
    void removeSegmentValue()
    {
        LazyArrayList<String> list = Util.generateLazyArrayList(10, 101);
        String s = list.get(100);
        assertNotNull(list.remove(s));
    }


    @Test
    void removeObject_notExists()
    {
        LazyArrayList<String> lazyArrayList = Util.generateLazyArrayList(10, 100);
        String s = "fkldsj;f jadslkf jsdalkjf alsdjf ;aksjfld";
        assertFalse(lazyArrayList.remove(s));
        assertEquals(100, lazyArrayList.size());
    }

    @Test
    void removeIfTest()
    {
        LazyArrayList<String> lazyArrayList = Stream.generate(() -> "Java")
                .limit(100)
                .collect(Collectors.toCollection(LazyArrayList::new));
        assertTrue(lazyArrayList.removeIf((v) -> v.equals("Java")));
        assertTrue(lazyArrayList.isEmpty());
    }

    @Test
    void consolidate()
    {
        LazyArrayList<String> lazyArrayList = Util.generateLazyArrayList(10, 100);
        lazyArrayList.remove(54);
        lazyArrayList.remove(82);
        assertTrue(lazyArrayList.consolidate());
    }

    @Test
    void iterateLazyReferences()
    {
        LazyArrayList<String> lazyArrayList = Util.generateLazyArrayList(10, 100);
        lazyArrayList.iterateLazyReferences((i) -> {
            assertTrue(i.isLoaded());
        });
    }

    @Test
    void loadedFirstIterator()
    {
        LazyArrayList<String> lazyArrayList = Util.generateLazyArrayList(10, 100);
        for (Iterator<String> it = lazyArrayList.loadedFirstIterator(); it.hasNext(); ) {
            String stringIterator = it.next();
            assertNotNull(stringIterator);
        }
        for (Iterator<String> it = lazyArrayList.loadedFirstIterator(); it.hasNext(); ) {
            String stringIterator = it.next();
            assertNotNull(stringIterator);
        }
    }

    @Test
    void loadedFirstIterator_remove()
    {
        LazyArrayList<String> lazyArrayList = Util.generateLazyArrayList(10, 100);
        for (Iterator<String> it = lazyArrayList.loadedFirstIterator(); it.hasNext(); ) {
            String stringIterator = it.next();
            it.remove();
        }
        assertTrue(lazyArrayList.isEmpty());
    }

    @Test
    void spliteratorCharacteristics()
    {
        LazyArrayList<String> lazyArrayList = Util.generateLazyArrayList(10, 100);
        assertNotEquals(0, lazyArrayList.spliterator()
                .characteristics());
    }

    @Test
    void segmentsSpliteratorCharacteristics()
    {
        LazyArrayList<String> lazyArrayList = Util.generateLazyArrayList(10, 100);
        assertNotEquals(0, lazyArrayList.segmentSpliterator()
                .characteristics());
    }

    @Test
    void segmentSpliteratorTest()
    {
        LazyArrayList<String> lazyArrayList = Util.generateLazyArrayList(10, 100);
        Spliterator<String> stringSpliterator = lazyArrayList.segmentSpliterator();
        assertTrue(stringSpliterator.tryAdvance(Assertions::assertNotNull));
    }

    @Test
    public void containsAll()
    {
        List<Integer> list = new LazyArrayList<>();
        list.add(1);
        list.add(2);
        list.add(3);

        List<Integer> list2 = new ArrayList<>();
        list2.add(1);
        list2.add(2);
        list2.add(3);

        assertTrue(list.containsAll(list2));
    }

    @Test
    public void addAllIndex()
    {
        List<Integer> list = new LazyArrayList<>();
        list.add(1);
        list.add(2);
        list.add(3);

        List<Integer> list2 = new ArrayList<>();
        list2.add(1);
        list2.add(2);
        list2.add(3);

        assertTrue(list.addAll(2, list2));

        List<Integer> list3 = new LazyArrayList<>();
        list3.add(1);
        list3.add(2);
        list3.add(1);
        list3.add(2);
        list3.add(3);
        list3.add(3);

        assertIterableEquals(list3, list);
    }

    @Test
    public void removeAllCollection()
    {
        List<Integer> list = new LazyArrayList<>();
        list.add(1);
        list.add(2);
        list.add(3);

        List<Integer> list2 = new ArrayList<>();
        list2.add(1);
        list2.add(2);
        list2.add(3);

        assertTrue(list.removeAll(list2));

        assertTrue(list.isEmpty());
    }

    @Test
    public void retainAll()
    {
        List<Integer> list = new LazyArrayList<>();
        list.add(1);
        list.add(2);
        list.add(3);

        List<Integer> list2 = new ArrayList<>();
        list2.add(1);
        list2.add(3);

        assertTrue(list.retainAll(list2));

        List<Integer> resultList = new LazyArrayList<>();
        resultList.add(1);
        resultList.add(3);

        assertIterableEquals(resultList, list);
    }


    @Test
    public void replaceAllUnaryOperator()
    {
        class Op implements UnaryOperator<String>
        {
            public String apply(String str)
            {
                return str.toUpperCase();
            }
        }

        List<String> list = new LazyArrayList<>();
        list.add("Java");
        list.add("JavaScript");
        //System.out.println("Contents of the list: " + list);
        list.replaceAll(new Op());

        List<String> expectedList = new LazyArrayList<>();
        expectedList.add("JAVA");
        expectedList.add("JAVASCRIPT");

        assertIterableEquals(expectedList, list);
    }

    @Test
    public void sort()
    {

        Comparator<Integer> valueComparator = (Integer o1, Integer o2) -> o1 - o2;

        List<Integer> list = new LazyArrayList<>();
        list.add(3);
        list.add(2);
        list.add(1);

        list.sort(valueComparator);

        List<Integer> resultList = new LazyArrayList<>();
        resultList.add(1);
        resultList.add(2);
        resultList.add(3);

        assertIterableEquals(resultList, list);
    }

    @Test
    public void clear()
    {
        List<Integer> list = new LazyArrayList<>();
        list.add(3);
        list.add(2);
        list.add(1);

        list.clear();

        assertTrue(list.isEmpty());
    }

    @Test
    public void equals()
    {
        List<Integer> list = new LazyArrayList<>();
        list.add(1);
        list.add(2);
        list.add(3);

        List<Integer> list2 = new ArrayList<>();
        list2.add(1);
        list2.add(2);
        list2.add(3);

        assertTrue(list.equals(list2));


        list2.add(4);
        assertFalse(list.equals(list2));
    }

    @Test
    public void hashCodeTest()
    {
        List<Integer> list = new LazyArrayList<>();
        list.add(1);
        list.add(2);
        list.add(3);

        List<Integer> list2 = new ArrayList<>();
        list2.add(1);
        list2.add(2);
        list2.add(3);

        assertEquals(list2.hashCode(), list.hashCode());

        list2.add(4);
        assertNotEquals(list2.hashCode(), list.hashCode());
    }

    @Test
    public void get()
    {
        List<Integer> list = new LazyArrayList<>();
        list.add(1);
        list.add(2);
        list.add(3);

        assertEquals(2, list.get(1));
    }

    @Test
    public void getWithIndexOutOfBoundException()
    {
        List<Integer> list = new LazyArrayList<>();
        list.add(1);
        list.add(2);
        list.add(3);

        assertAll("OutOfBoundException tests",
                () -> assertThrows(IndexOutOfBoundsException.class, () -> list.get(20)),
                () -> assertThrows(IndexOutOfBoundsException.class, () -> list.get(-5)),
                () -> assertThrows(IndexOutOfBoundsException.class, () -> list.get(list.size())));
    }

    @Test
    public void setAndGetBack()
    {
        List<Integer> list = new LazyArrayList<>();
        list.add(1);
        list.add(2);
        list.add(3);

        assertEquals(3, list.set(2, 100));

        List<Integer> resultList = new LazyArrayList<>();
        resultList.add(1);
        resultList.add(2);
        resultList.add(100);

        assertIterableEquals(resultList, list);
    }

    @Test
    public void setIndexBoundOfException()
    {
        List<Integer> list = new LazyArrayList<>();
        list.add(1);
        list.add(2);
        list.add(3);

        assertAll("OutOfBoundException tests",
                () -> assertThrows(IndexOutOfBoundsException.class, () -> list.set(20, 50)),
                () -> assertThrows(IndexOutOfBoundsException.class, () -> list.set(-5, 50)),
                () -> assertThrows(IndexOutOfBoundsException.class, () -> list.set(list.size(), 50))
        );
    }

    @Test
    public void addWithIndex()
    {
        List<Integer> list = new LazyArrayList<>();
        list.add(1);
        list.add(2);
        list.add(3);

        list.add(0, 100);

        List<Integer> resultList = new LazyArrayList<>();
        resultList.add(100);
        resultList.add(1);
        resultList.add(2);
        resultList.add(3);

        assertIterableEquals(resultList, list);
    }

    @Test
    public void removeWithIndex()
    {
        List<Integer> list = new LazyArrayList<>();
        list.add(1);
        list.add(2);
        list.add(3);

        assertEquals(2, list.remove(1));

        List<Integer> resultList = new LazyArrayList<>();
        resultList.add(1);
        resultList.add(3);

        assertIterableEquals(resultList, list);
    }


    @Test
    public void indexOf()
    {
        List<Integer> list = new LazyArrayList<>();
        list.add(1);
        list.add(2);
        list.add(3);

        assertEquals(1, list.indexOf(2));
    }

    @Test
    public void lastIndexOf()
    {
        List<Integer> list = new LazyArrayList<>();
        list.add(1);
        list.add(1);
        list.add(1);

        assertAll("lastIndexOf",
                () -> assertEquals(2, list.lastIndexOf(1)),
                () -> assertEquals(-1, list.lastIndexOf(-50)));
    }

    @Test
    public void listIteratorIndex()
    {
        List<Integer> list = new LazyArrayList<>();
        list.add(1);
        list.add(1);
        list.add(1);

        ListIterator<Integer> listIterator = list.listIterator(1);

        int i = 0;

        while (listIterator.hasNext()) {
            i++;
            listIterator.next();
        }

        assertEquals(2, i);
    }

    @Test
    public void sublist()
    {
        List<Integer> list = new LazyArrayList<>();
        list.add(1);
        list.add(2);
        list.add(3);
        list.add(4);
        list.add(5);

        List<Integer> sublist = list.subList(2, 4);

        List<Integer> resultList = new LazyArrayList<>();
        resultList.add(3);
        resultList.add(4);

        assertAll("Method Sublist",
                () -> assertIterableEquals(resultList, sublist),
                () -> assertThrows(IndexOutOfBoundsException.class, () -> list.subList(-1, 5)),
                () -> assertThrows(IndexOutOfBoundsException.class, () -> list.subList(1, 50)),
                () -> assertThrows(IndexOutOfBoundsException.class, () -> list.subList(100, 50)));
    }

    public LazyArrayList<Article> generateElements()
    {
        return Stream.generate(() -> new Article("Java"))
                .limit(35000)
                .collect(Collectors.toCollection(LazyArrayList::new));
    }

    @Test
    public void givenSpliterator_whenAppliedToAListOfArticle_thenSplittedInHalf()
    {
        LazyArrayList<Article> articles = generateElements();
        Spliterator<Article> split1 = articles.spliterator();
        Spliterator<Article> split2 = split1.trySplit();

        assertAll("splitIterator tests",
                () -> assertEquals(35000, articles.size()),
                () -> assertEquals(17000, split1.estimateSize()),
                () -> assertEquals(18000, split2.estimateSize()));
    }

    public class Article
    {
        private int id;
        private String name;

        public Article(String name)
        {
            this.name = name;
        }

        // standard constructors/getters/setters
    }


    @Test
    public void copyOf()
    {
        List<Integer> list = new LazyArrayList<>();
        list.add(1);
        list.add(2);
        list.add(3);
        list.add(4);
        list.add(5);

        List<Integer> resultList = List.copyOf(list);

        assertIterableEquals(list, resultList);
    }
}
