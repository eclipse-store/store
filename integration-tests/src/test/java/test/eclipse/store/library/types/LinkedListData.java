package test.eclipse.store.library.types;

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

import org.junit.jupiter.api.Assertions;
import test.eclipse.store.library.types.help.CheckService;
import test.eclipse.store.library.types.help.CheckServiceImpl;

import java.util.Arrays;
import java.util.LinkedList;

import static org.junit.jupiter.api.Assertions.*;

public class LinkedListData implements BinaryHandlerTestData {
    private LinkedList<Integer> intList = new LinkedList<>();
    private LinkedList<LinkedList<LinkedList<PrimitiveTypes>>> threeList;
    private LinkedList<Integer[]> listWithArray = createListWithEmptyArray();
    private LinkedList<Object> objectList;

    // ===== proposed edge-cases (review & cherry-pick) =====
    // LinkedList implements both List and Deque. The probes target the List boundaries
    // (empty / nulls / duplicates / large extent) plus a Deque-specific probe: a mix of
    // addFirst / addLast must round-trip in the exact iteration order.
    private LinkedList<Integer> emptyList;
    private LinkedList<Integer> nullsList;
    private LinkedList<Integer> duplicatesList;
    private LinkedList<Integer> largeList;
    private LinkedList<Integer> dequeMixedList;

    @Override
    public LinkedListData fillSampleData() {
        intList = createIntList();
        threeList = createThreeList();
        listWithArray = createListWithArray();
        objectList = createObjectList();

        // ===== proposed edge-cases =====
        emptyList = new LinkedList<>();
        nullsList = createNullsList();
        duplicatesList = createDuplicatesList();
        largeList = createLargeList();
        dequeMixedList = createDequeMixedList();

        return this;
    }

    LinkedList<Integer> createIntList() {
        LinkedList<Integer> intList = new LinkedList<>();
        intList.add(6);
        return intList;
    }

    LinkedList<LinkedList<LinkedList<PrimitiveTypes>>> createThreeList() {
        LinkedList<LinkedList<LinkedList<PrimitiveTypes>>> list = new LinkedList<>();

        LinkedList<PrimitiveTypes> primitiveList = new LinkedList<>();
        primitiveList.add(PrimitiveTypes.fillSample());

        LinkedList<LinkedList<PrimitiveTypes>> listOfList = new LinkedList<>();
        listOfList.add(primitiveList);
        list.add(listOfList);

        return list;
    }

    LinkedList<Integer[]> createListWithArray() {
        LinkedList<Integer[]> list = new LinkedList<>();
        Integer[] intArray = {0, 1, 2, 3, 4, 5, 6, -1, -5, -10};
        list.add(intArray);
        return list;
    }

    LinkedList<Integer[]> createListWithEmptyArray() {
        LinkedList<Integer[]> list = new LinkedList<>();
        Integer[] intArray = {0};
        list.add(intArray);
        return list;
    }

    LinkedList<Object> createObjectList() {
        Object o = createThreeList();
        LinkedList<Object> ao = new LinkedList<>();
        ao.add(o);
        return ao;
    }

    LinkedList<CheckService> createInterfaceList() {
        CheckService service = new CheckServiceImpl();
        LinkedList<CheckService> list = new LinkedList<>();
        list.add(service);
        return list;
    }

    LinkedList<Integer> getIntList() {
        return intList;
    }

    LinkedList<LinkedList<LinkedList<PrimitiveTypes>>> getThreeList() {
        return threeList;
    }

    LinkedList<Integer[]> getListWithArray() {
        return listWithArray;
    }

    LinkedList<Object> getObjectList() {
        return objectList;
    }

    // ===== proposed edge-cases — getters =====

    public LinkedList<Integer> getEmptyList() {
        return emptyList;
    }

    public LinkedList<Integer> getNullsList() {
        return nullsList;
    }

    public LinkedList<Integer> getDuplicatesList() {
        return duplicatesList;
    }

    public LinkedList<Integer> getLargeList() {
        return largeList;
    }

    public LinkedList<Integer> getDequeMixedList() {
        return dequeMixedList;
    }

    LinkedList<Integer> createNullsList() {
        LinkedList<Integer> l = new LinkedList<>();
        l.add(null);
        l.add(0);
        l.add(null);
        l.add(42);
        return l;
    }

    LinkedList<Integer> createDuplicatesList() {
        LinkedList<Integer> l = new LinkedList<>();
        l.add(7);
        l.add(7);
        l.add(7);
        return l;
    }

    LinkedList<Integer> createLargeList() {
        LinkedList<Integer> l = new LinkedList<>();
        for (int i = 0; i < 10_000; i++) {
            l.add(i);
        }
        return l;
    }

    LinkedList<Integer> createDequeMixedList() {
        // Deque API: mix addFirst / addLast — final iteration order must be [-1, 0, 1, 2, 3]
        LinkedList<Integer> l = new LinkedList<>();
        l.addLast(1);
        l.addLast(2);
        l.addFirst(0);
        l.addLast(3);
        l.addFirst(-1);
        return l;
    }

    @Override
    public void proveResults(Object o) {
        Assertions.assertNotNull(o);
        LinkedListData copy = (LinkedListData) o;
        assertAll("Array list Tests", //
                () -> assertIterableEquals(this.getIntList(), copy.getIntList()), //
                () -> assertIterableEquals(this.getThreeList(), copy.getThreeList()), //
                () -> assertArrayEquals(this.getListWithArray().get(0), copy.getListWithArray().get(0)),
                () -> assertIterableEquals(this.getObjectList(), copy.getObjectList()),

                // ===== proposed edge-case verifications =====
                () -> {
                    if (this.getEmptyList() != null) {
                        assertTrue(copy.getEmptyList().isEmpty(), "empty LinkedList remains empty");
                    } else {
                        assertNull(copy.getEmptyList());
                    }
                },
                () -> assertIterableEquals(this.getNullsList(), copy.getNullsList(), "LinkedList<Integer> with nulls"),
                () -> assertIterableEquals(this.getDuplicatesList(), copy.getDuplicatesList(), "LinkedList<Integer> with duplicates"),
                () -> {
                    if (this.getLargeList() != null) {
                        assertEquals(10_000, copy.getLargeList().size(), "large list size");
                        assertIterableEquals(this.getLargeList(), copy.getLargeList(), "large list content");
                    } else {
                        assertNull(copy.getLargeList());
                    }
                },
                () -> {
                    if (this.getDequeMixedList() != null) {
                        // After addLast(1), addLast(2), addFirst(0), addLast(3), addFirst(-1) → [-1, 0, 1, 2, 3]
                        assertIterableEquals(Arrays.asList(-1, 0, 1, 2, 3), copy.getDequeMixedList(), "Deque addFirst/addLast ordering");
                        assertEquals(Integer.valueOf(-1), copy.getDequeMixedList().getFirst(), "getFirst() = -1");
                        assertEquals(Integer.valueOf(3), copy.getDequeMixedList().getLast(), "getLast() = 3");
                    } else {
                        assertNull(copy.getDequeMixedList());
                    }
                }
        );
    }
}
