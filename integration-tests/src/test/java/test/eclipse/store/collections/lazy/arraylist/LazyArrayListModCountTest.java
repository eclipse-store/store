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

import org.eclipse.serializer.collections.lazy.LazyArrayList;
import org.eclipse.serializer.exceptions.IllegalAccessRuntimeException;
import org.eclipse.serializer.reflect.XReflect;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


@Disabled
public class LazyArrayListModCountTest {

    private static Field modCount;

    @BeforeAll
    static void initTests() {
        modCount = XReflect.setAccessible(LazyArrayList.class, XReflect.getAnyField(LazyArrayList.class, "modCount"));
    }

    @Test
    void add() {
        final LazyArrayList<ListEntry> lal = LazyArrayListPersistenceTest.createLazyList(4, 0);
        final int initialModCount = getField_int(modCount, lal);

        lal.add(new ListEntry("new entry"));

        assertEquals(initialModCount + 1, getField_int(modCount, lal));
    }

    @Test
    void addIndex() {
        final LazyArrayList<ListEntry> lal = LazyArrayListPersistenceTest.createLazyList(4, 2);
        final int initialModCount = getField_int(modCount, lal);

        lal.add(1, new ListEntry("new entry"));

        assertEquals(initialModCount + 1, getField_int(modCount, lal));
    }

    @Test
    void addAll() {
        final LazyArrayList<ListEntry> lal = LazyArrayListPersistenceTest.createLazyList(4, 2);
        final int initialModCount = getField_int(modCount, lal);

        lal.addAll(List.of(new ListEntry("new entry 1"), new ListEntry("new entry 2"), new ListEntry("new entry 3"), new ListEntry("new entry 4")));

        assertEquals(initialModCount + 1, getField_int(modCount, lal));
    }

    @Test
    void addAllIndex() {
        final LazyArrayList<ListEntry> lal = LazyArrayListPersistenceTest.createLazyList(4, 2);
        final int initialModCount = getField_int(modCount, lal);

        lal.addAll(1, List.of(new ListEntry("new entry 1"), new ListEntry("new entry 2"), new ListEntry("new entry 3"), new ListEntry("new entry 4")));

        assertEquals(initialModCount + 1, getField_int(modCount, lal));
    }

    @Test
    void clear() {
        final LazyArrayList<ListEntry> lal = LazyArrayListPersistenceTest.createLazyList(4, 4);
        final int initialModCount = getField_int(modCount, lal);

        lal.clear();

        assertEquals(initialModCount + 1, getField_int(modCount, lal));
    }

    @Test
    void consolidate_nothingTodo() {
        final LazyArrayList<ListEntry> lal = LazyArrayListPersistenceTest.createLazyList(4, 4);
        final int initialModCount = getField_int(modCount, lal);

        lal.consolidate();

        assertEquals(initialModCount, getField_int(modCount, lal));
    }

    @Test
    void consolidate() {
        final LazyArrayList<ListEntry> lal = LazyArrayListPersistenceTest.createLazyList(2, 6);
        lal.removeAll(List.of(new ListEntry("Entry-1"), new ListEntry("Entry-2"), new ListEntry("Entry-3"), new ListEntry("Entry-4")));
        final int initialModCount = getField_int(modCount, lal);

        lal.consolidate();

        assertEquals(initialModCount + 2, getField_int(modCount, lal));
    }

    @Test
    void remove() {
        final LazyArrayList<ListEntry> lal = LazyArrayListPersistenceTest.createLazyList(4, 4);
        final int initialModCount = getField_int(modCount, lal);

        lal.remove(new ListEntry("Entry-1"));

        assertEquals(initialModCount + 1, getField_int(modCount, lal));
    }

    @Test
    void removeIndex() {
        final LazyArrayList<ListEntry> lal = LazyArrayListPersistenceTest.createLazyList(4, 4);
        final int initialModCount = getField_int(modCount, lal);

        lal.remove(2);

        assertEquals(initialModCount + 1, getField_int(modCount, lal));
    }

    @Test
    void removeAll() {
        final LazyArrayList<ListEntry> lal = LazyArrayListPersistenceTest.createLazyList(4, 7);
        final int initialModCount = getField_int(modCount, lal);

        lal.removeAll(List.of(new ListEntry("Entry-1"), new ListEntry("Entry-2"), new ListEntry("Entry-3"), new ListEntry("Entry-4")));

        assertTrue(initialModCount < getField_int(modCount, lal));
    }

    @Test
    void retainAll() {
        final LazyArrayList<ListEntry> lal = LazyArrayListPersistenceTest.createLazyList(4, 7);
        final int initialModCount = getField_int(modCount, lal);

        lal.retainAll(List.of(new ListEntry("Entry-1"), new ListEntry("Entry-2"), new ListEntry("Entry-3"), new ListEntry("Entry-3")));

        assertTrue(initialModCount < getField_int(modCount, lal));
    }

    @Test
    void removeIf() {
        final LazyArrayList<ListEntry> lal = LazyArrayListPersistenceTest.createLazyList(4, 7);
        final int initialModCount = getField_int(modCount, lal);

        lal.removeIf(e -> (e.id.contains("2") || e.id.contains("4")));

        assertTrue(initialModCount < getField_int(modCount, lal));
    }

    @Test
    void iteratorRemove() {
        final LazyArrayList<ListEntry> lal = LazyArrayListPersistenceTest.createLazyList(4, 7);
        final int initialModCount = getField_int(modCount, lal);

        final Iterator<ListEntry> iter = lal.iterator();
        iter.next();
        while (iter.hasNext()) {
            iter.next();
            iter.remove();
        }

        assertTrue(initialModCount < getField_int(modCount, lal));
    }

    @Test
    void iteratorAdd() {
        final LazyArrayList<ListEntry> lal = LazyArrayListPersistenceTest.createLazyList(4, 7);
        final int initialModCount = getField_int(modCount, lal);

        final ListIterator<ListEntry> iter = lal.listIterator(6);
        iter.add(new ListEntry("ListEntry"));

        assertTrue(initialModCount < getField_int(modCount, lal));
    }

    public static int getField_int(final Field f, final Object obj) throws IllegalAccessRuntimeException
    {
        try
        {
            return f.getInt(obj);
        }
        catch(final IllegalAccessException e)
        {
            throw new IllegalAccessRuntimeException(e);
        }
    }
}
