package test.eclipse.store.collections.lazy.arraylist;

/*-
 * #%L
 * EclipseStore Integration Tests
 * %%
 * Copyright (C) 2023 - 2026 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.ListIterator;
import java.util.function.UnaryOperator;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.eclipse.serializer.collections.lazy.LazyArrayList;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class LazyArrayListSmokePersistenceTest
{


    @TempDir
    Path location;

    @Test
    public void addItem()
    {
        LazyArrayList<Integer> list = new LazyArrayList<>();

        try (EmbeddedStorageManager storage = EmbeddedStorage.start(list, location)) {

            assertTrue(list.isEmpty());
            assertEquals(0, list.size());
            assertTrue(list.add(1));
            storage.store(list);
            LazyArrayList<Integer> reloaded = (LazyArrayList<Integer>) storage.root();

            assertEquals(1, reloaded.size());
            assertEquals(1, reloaded.get(0));

            assertTrue(list.addAll(list));
            assertEquals(2, list.size());

            assertTrue(list.add(null));

            storage.store(list);
            list = (LazyArrayList<Integer>) storage.root();
            assertEquals(3, list.size());
        }

        LazyArrayList<Integer> copy = new LazyArrayList<>();

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(copy, location)) {
            assertEquals(3, list.size());
        }
    }

    @Test
    public void isEmpty()
    {
        LazyArrayList<Integer> list = new LazyArrayList<>();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(list, location)) {
            assertTrue(list.isEmpty());
        }
    }

    @Test
    public void contains()
    {
        String s = "Hello, i would to be a great object";

        LazyArrayList<String> list = new LazyArrayList<>();
        list.add(s);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(list, location)) {
        }

        LazyArrayList<String> copy = new LazyArrayList<>();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(copy, location)) {
            assertTrue(copy.contains(s));
        }

    }


    @Test
    public void iterator()
    {
        String s = "Hello, i would to be a great object";

        LazyArrayList<String> list = new LazyArrayList<>();
        list.add(s);
        list.add(s);
        list.add(s);

        LazyArrayList<String> copy = new LazyArrayList<>();

        ImmutablePair<LazyArrayList<String>, EmbeddedStorageManager> pair = null;
        try {
            pair = Util.storeAndLoadList(list, location);
            ListIterator<String> listIterator = pair.getKey()
                    .listIterator();

            int i = 0;

            while (listIterator.hasNext()) {
                i++;
                listIterator.next();
            }

            assertEquals(3, i);
        } finally {
            if ((pair != null) && (pair.getValue() != null)) {
                pair.getValue()
                        .shutdown();
            }

        }
    }

    @Test
    public void toArray()
    {
        List<Integer> list = new LazyArrayList<>();
        list.add(1);
        list.add(2);
        list.add(3);

        try (EmbeddedStorageManager storage = EmbeddedStorage.start(list, location)) {

        }

        List<Integer> copy = new LazyArrayList<>();
        try (EmbeddedStorageManager storage = EmbeddedStorage.start(copy, location)) {
            Object[] intArray = copy.toArray();


            Object[] expectIntArray = {1, 2, 3};
            assertArrayEquals(expectIntArray, intArray);

        }

    }

    @Test
    public void toArrayWithType()
    {
        LazyArrayList<Integer> list = new LazyArrayList<>();
        list.add(1);
        list.add(2);
        list.add(3);


        ImmutablePair<LazyArrayList<Integer>, EmbeddedStorageManager> pair = null;
        try {
            pair = Util.storeAndLoadList(list, location);
            LazyArrayList<Integer> copy = pair.getKey();

            Integer[] intArray = copy.toArray(new Integer[0]);

            Integer[] expectIntArray = {1, 2, 3};
            assertArrayEquals(expectIntArray, intArray);
        } finally {
            if ((pair != null) && (pair.getValue() != null)) {
                pair.getValue()
                        .shutdown();
            }

        }

    }

    @Test
    public void remove()
    {
        LazyArrayList<Integer> list = new LazyArrayList<>();
        list.add(1);
        list.add(2);
        list.add(3);


        ImmutablePair<LazyArrayList<Integer>, EmbeddedStorageManager> pair = null;
        try {
            pair = Util.storeAndLoadList(list, location);
            LazyArrayList<Integer> copy = pair.getKey();

            copy.remove(2);

            assertEquals(2, copy.size());

        } finally {
            if ((pair != null) && (pair.getValue() != null)) {
                pair.getValue()
                        .shutdown();
            }
        }

    }

    @Test
    public void containsAll()
    {
        LazyArrayList<Integer> list = new LazyArrayList<>();
        list.add(1);
        list.add(2);
        list.add(3);

        List<Integer> list2 = new ArrayList<>();
        list2.add(1);
        list2.add(2);
        list2.add(3);

        assertTrue(list.containsAll(list2));

        ImmutablePair<LazyArrayList<Integer>, EmbeddedStorageManager> pair = null;
        try {
            pair = Util.storeAndLoadList(list, location);
            LazyArrayList<Integer> copy = pair.getKey();

            assertTrue(copy.containsAll(list2));

        } finally {
            if ((pair != null) && (pair.getValue() != null)) {
                pair.getValue()
                        .shutdown();
            }
        }
    }

    @Test
    public void addAllIndex()
    {
        LazyArrayList<Integer> list = new LazyArrayList<>();
        list.add(1);
        list.add(2);
        list.add(3);

        List<Integer> list2 = new ArrayList<>();
        list2.add(1);
        list2.add(2);
        list2.add(3);

        LazyArrayList<Integer> copy;
        ImmutablePair<LazyArrayList<Integer>, EmbeddedStorageManager> pair = null;
        try {
            pair = Util.storeAndLoadList(list, location);
            copy = pair.getKey();

            assertTrue(copy.containsAll(list2));

        } finally {
            if ((pair != null) && (pair.getValue() != null)) {
                pair.getValue()
                        .shutdown();
            }
        }

        assertTrue(copy.addAll(2, list2));


        List<Integer> list3 = new LazyArrayList<>();
        list3.add(1);
        list3.add(2);
        list3.add(1);
        list3.add(2);
        list3.add(3);
        list3.add(3);

        assertIterableEquals(list3, copy);
    }

    @Test
    public void removeAllCollection()
    {
        LazyArrayList<Integer> list = new LazyArrayList<>();
        list.add(1);
        list.add(2);
        list.add(3);

        List<Integer> list2 = new ArrayList<>();
        list2.add(1);
        list2.add(2);
        list2.add(3);


        LazyArrayList<Integer> copy;
        ImmutablePair<LazyArrayList<Integer>, EmbeddedStorageManager> pair = null;
        try {
            pair = Util.storeAndLoadList(list, location);
            copy = pair.getKey();

            assertTrue(copy.containsAll(list2));

        } finally {
            if ((pair != null) && (pair.getValue() != null)) {
                pair.getValue()
                        .shutdown();
            }
        }

        assertTrue(copy.removeAll(list2));

        assertTrue(copy.isEmpty());
    }

    @Test
    public void retainAll()
    {
        LazyArrayList<Integer> list = new LazyArrayList<>();
        list.add(1);
        list.add(2);
        list.add(3);

        List<Integer> list2 = new ArrayList<>();
        list2.add(1);
        list2.add(3);


        LazyArrayList<Integer> copy;
        ImmutablePair<LazyArrayList<Integer>, EmbeddedStorageManager> pair = null;
        try {
            pair = Util.storeAndLoadList(list, location);
            copy = pair.getKey();

            copy.iterateLazyReferences(e -> e.get());


        } finally {
            if ((pair != null) && (pair.getValue() != null)) {
                pair.getValue()
                        .shutdown();
            }
        }

        assertTrue(copy.retainAll(list2));

        List<Integer> resultList = new LazyArrayList<>();
        resultList.add(1);
        resultList.add(3);

        assertIterableEquals(resultList, copy);
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

        LazyArrayList<Integer> list = new LazyArrayList<>();
        list.add(3);
        list.add(2);
        list.add(1);

        LazyArrayList<Integer> copy;
        ImmutablePair<LazyArrayList<Integer>, EmbeddedStorageManager> pair = null;
        try {
            pair = Util.storeAndLoadList(list, location);
            copy = pair.getKey();
            copy.sort(valueComparator);

            pair.getValue().store(copy);

        } finally {
            if ((pair != null) && (pair.getValue() != null)) {
                pair.getValue()
                        .shutdown();
            }
        }


        List<Integer> resultList = new LazyArrayList<>();
        resultList.add(1);
        resultList.add(2);
        resultList.add(3);

        assertIterableEquals(resultList, copy);
    }

}
