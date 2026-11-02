package test.eclipse.store.library.types;

/*-
 * #%L
 * EclipseStore Integration Tests - Fixtures - Types Library
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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.Collections;

import org.junit.jupiter.api.Assertions;

import test.eclipse.store.library.types.help.CheckService;
import test.eclipse.store.library.types.help.CheckServiceImpl;

public class ArrayListData implements BinaryHandlerTestData {
    private ArrayList<Integer> intList;
    private ArrayList<ArrayList<ArrayList<PrimitiveTypes>>> threeList;
    private ArrayList<Integer[]> listWithArray;
    private ArrayList<Object> objectList;
    private ArrayList<CheckService> interfaceList;
    private ArrayList<String> emptyArrayListString;

    // corner-case fields
    private ArrayList<Integer> intListWithNulls;
    private ArrayList<Integer> intListWithDuplicates;
    private ArrayList<Integer> intListLargeValues;
    private ArrayList<ArrayList<PrimitiveTypes>> nestedListWithNulls;
    private ArrayList<Integer[]> listWithArrayContainingNulls;
    private ArrayList<Object> heteroObjectList;
    private ArrayList<CheckService> interfaceListWithNull;

    // edge cases
    private ArrayList<Integer> oversizedCapacityList;
    private ArrayList<Integer> largeList;
    private ArrayList<Integer[]> listWithNullArrayEntries;
    private ArrayList<String> stringEdgeCasesList;

    @Override
    public ArrayListData fillSampleData() {
        intList = createIntList();
        threeList = createThreeList();
        listWithArray = createListWithArray();
        objectList = createObjectList();
        interfaceList = createInterfaceList();
        emptyArrayListString = new ArrayList<>();

        // corner-case initializations
        intListWithNulls = createIntListWithNulls();
        intListWithDuplicates = createIntListWithDuplicates();
        intListLargeValues = createIntListLargeValues();
        nestedListWithNulls = createNestedListWithNulls();
        listWithArrayContainingNulls = createListWithArrayContainingNulls();
        heteroObjectList = createHeteroObjectList();
        interfaceListWithNull = createInterfaceListWithNull();

        // edge cases
        oversizedCapacityList = createOversizedCapacityList();
        largeList = createLargeList();
        listWithNullArrayEntries = createListWithNullArrayEntries();
        stringEdgeCasesList = createStringEdgeCasesList();

        return this;
    }

    @Override
    public BinaryHandlerTestData updateSampleData() {
        intList.add(-50);
        threeList.add(createThreeList().get(0));
        listWithArray.add(createListWithArray().get(0));
        objectList.add(createObjectList().get(0));
        interfaceList.add(createInterfaceList().get(0));
        emptyArrayListString.add("something");

        // small updates for corner-cases
        intListWithNulls.add(null); // add another null
        intListWithDuplicates.add(intListWithDuplicates.get(0)); // add duplicate
        intListLargeValues.add(Integer.MAX_VALUE); // add another large value
        nestedListWithNulls.add(new ArrayList<>()); // add an empty inner list
        listWithArrayContainingNulls.add(new Integer[]{}); // add empty array
        heteroObjectList.add(null); // add explicit null to hetero list
        interfaceListWithNull.add(null); // add null entry

        // edge cases: touch where safe (no shared-ref / self-ref mutation)
        oversizedCapacityList.add(4);
        largeList.add(10_000);
        listWithNullArrayEntries.add(new Integer[]{null});
        // stringEdgeCasesList intentionally not mutated here; exotic chars (NUL, emoji) are covered in createStringEdgeCasesList

        return this;
    }

    ArrayList<Integer> createIntList() {
        ArrayList<Integer> intList = new ArrayList<>();
        intList.add(6);
        return intList;
    }

    ArrayList<ArrayList<ArrayList<PrimitiveTypes>>> createThreeList() {
        ArrayList<ArrayList<ArrayList<PrimitiveTypes>>> list = new ArrayList<>();
        ArrayList<PrimitiveTypes> primitiveList = new ArrayList<>();
        primitiveList.add(PrimitiveTypes.fillSample());
        ArrayList<ArrayList<PrimitiveTypes>> listOfList = new ArrayList<>();
        listOfList.add(primitiveList);
        list.add(listOfList);
        return list;
    }

    ArrayList<Integer[]> createListWithArray() {
        ArrayList<Integer[]> list = new ArrayList<>();
        Integer[] intArray = {0, 1, 2, 3, 4, 5, 6, -1, -5, -10};
        list.add(intArray);
        return list;
    }

    ArrayList<Object> createObjectList() {
        Object o = createThreeList();
        ArrayList<Object> ao = new ArrayList<>();
        ao.add(o);
        return ao;
    }

    ArrayList<CheckService> createInterfaceList() {
        CheckService service = new CheckServiceImpl();
        ArrayList<CheckService> list = new ArrayList<>();
        list.add(service);
        return list;
    }

    ArrayList<Integer> getIntList() {
        return intList;
    }

    ArrayList<ArrayList<ArrayList<PrimitiveTypes>>> getThreeList() {
        return threeList;
    }

    ArrayList<Integer[]> getListWithArray() {
        return listWithArray;
    }

    ArrayList<Object> getObjectList() {
        return objectList;
    }

    ArrayList<CheckService> getInterfaceList() {
        return interfaceList;
    }

    public ArrayList<String> getEmptyArrayListString() {
        return emptyArrayListString;
    }

    // getters for corner-case fields
    public ArrayList<Integer> getIntListWithNulls() {
        return intListWithNulls;
    }

    public ArrayList<Integer> getIntListWithDuplicates() {
        return intListWithDuplicates;
    }

    public ArrayList<Integer> getIntListLargeValues() {
        return intListLargeValues;
    }

    public ArrayList<ArrayList<PrimitiveTypes>> getNestedListWithNulls() {
        return nestedListWithNulls;
    }

    public ArrayList<Integer[]> getListWithArrayContainingNulls() {
        return listWithArrayContainingNulls;
    }

    public ArrayList<Object> getHeteroObjectList() {
        return heteroObjectList;
    }

    public ArrayList<CheckService> getInterfaceListWithNull() {
        return interfaceListWithNull;
    }

    @Override
    public void proveResults(Object o) {
        Assertions.assertNotNull(o);
        ArrayListData copy = (ArrayListData) o;
        assertAll("Array list Tests", //
                () -> assertIterableEquals(this.getIntList(), copy.getIntList(), "ArrayList<Integer>"), //
                () -> assertIterableEquals(this.getThreeList(), copy.getThreeList(), "ArrayList<ArrayList<ArrayList<PrimitiveTypes>>>") , //
                () -> {
                    if (this.getListWithArray() != null) {
                        assertArrayEquals(this.getListWithArray()
                                .get(0), copy.getListWithArray()
                                .get(0), "ArrayList<Integer[]> ");
                    } else {
                        assertNull(copy.getListWithArray());
                    }
                }
                ,
                () -> assertIterableEquals(this.getObjectList(), copy.getObjectList(), "ArrayList<Object>"),
                () -> {
                    if (this.getInterfaceList() != null) {
                        assertEquals(this.getInterfaceList()
                                .get(0)
                                .getValue(), copy.getInterfaceList()
                                .get(0)
                                .getValue(), "ArrayList<Interface>");
                    } else {
                        assertNull(copy.getInterfaceList());
                    }
                },
                () -> {
                    if (this.getEmptyArrayListString() != null) {
                        assertEquals(this.getEmptyArrayListString()
                                .size(), copy.getEmptyArrayListString()
                                .size());
                    } else {
                        assertNull(copy.getEmptyArrayListString());
                    }
                },

                // corner-case verifications
                () -> assertIterableEquals(this.getIntListWithNulls(), copy.getIntListWithNulls(), "ArrayList<Integer> with nulls"),
                () -> assertIterableEquals(this.getIntListWithDuplicates(), copy.getIntListWithDuplicates(), "ArrayList<Integer> with duplicates"),
                () -> assertIterableEquals(this.getIntListLargeValues(), copy.getIntListLargeValues(), "ArrayList<Integer> with large values"),
                () -> assertIterableEquals(this.getNestedListWithNulls(), copy.getNestedListWithNulls(), "Nested list with nulls and empty lists"),
                () -> {
                    if (this.getListWithArrayContainingNulls() != null) {
                        // check first array which contains a null element
                        Integer[] a = this.getListWithArrayContainingNulls().get(0);
                        Integer[] b = copy.getListWithArrayContainingNulls().get(0);
                        assertArrayEquals(a, b, "ArrayList<Integer[]> containing nulls");
                        // also check empty array that was added on update
                        if (this.getListWithArrayContainingNulls().size() > 1) {
                            assertArrayEquals(this.getListWithArrayContainingNulls().get(1), copy.getListWithArrayContainingNulls().get(1), "empty array");
                        }
                    } else {
                        assertNull(copy.getListWithArrayContainingNulls());
                    }
                },
                () -> assertIterableEquals(this.getHeteroObjectList(), copy.getHeteroObjectList(), "heterogeneous Object list including nulls"),
                () -> {
                    if (this.getInterfaceListWithNull() != null) {
                        // check that first real element equals and sizes match (nulls allowed)
                        assertEquals(this.getInterfaceListWithNull().size(), copy.getInterfaceListWithNull().size());
                        if (this.getInterfaceListWithNull().get(0) != null) {
                            assertEquals(this.getInterfaceListWithNull().get(0).getValue(), copy.getInterfaceListWithNull().get(0).getValue());
                        }
                    } else {
                        assertNull(copy.getInterfaceListWithNull());
                    }
                },

                // edge-case verifications
                () -> {
                    // capacity-vs-size: ES must store size, not capacity
                    if (this.getOversizedCapacityList() != null) {
                        assertEquals(this.getOversizedCapacityList().size(), copy.getOversizedCapacityList().size(), "oversized-capacity size");
                        assertIterableEquals(this.getOversizedCapacityList(), copy.getOversizedCapacityList(), "oversized-capacity content");
                    } else {
                        assertNull(copy.getOversizedCapacityList());
                    }
                },
                () -> assertIterableEquals(this.getLargeList(), copy.getLargeList(), "large list (10k)"),
                () -> {
                    // null entries in Integer[] slot
                    if (this.getListWithNullArrayEntries() != null) {
                        assertEquals(this.getListWithNullArrayEntries().size(), copy.getListWithNullArrayEntries().size(), "null-array-entries size");
                        for (int i = 0; i < this.getListWithNullArrayEntries().size(); i++) {
                            Integer[] a = this.getListWithNullArrayEntries().get(i);
                            Integer[] b = copy.getListWithNullArrayEntries().get(i);
                            if (a == null) {
                                assertNull(b, "null array slot at " + i);
                            } else {
                                assertArrayEquals(a, b, "Integer[] at " + i);
                            }
                        }
                    } else {
                        assertNull(copy.getListWithNullArrayEntries());
                    }
                },
                () -> assertIterableEquals(this.getStringEdgeCasesList(), copy.getStringEdgeCasesList(), "string edge cases (empty, NUL, surrogate pair, long)")
        );
    }

    // helper methods for corner-cases
    ArrayList<Integer> createIntListWithNulls() {
        ArrayList<Integer> l = new ArrayList<>();
        l.add(null);
        l.add(0);
        l.add(0); // duplicate zero
        return l;
    }

    ArrayList<Integer> createIntListWithDuplicates() {
        ArrayList<Integer> l = new ArrayList<>();
        l.add(7);
        l.add(7);
        l.add(7);
        return l;
    }

    ArrayList<Integer> createIntListLargeValues() {
        ArrayList<Integer> l = new ArrayList<>();
        l.add(Integer.MAX_VALUE);
        l.add(Integer.MIN_VALUE);
        return l;
    }

    ArrayList<ArrayList<PrimitiveTypes>> createNestedListWithNulls() {
        ArrayList<ArrayList<PrimitiveTypes>> l = new ArrayList<>();
        l.add(null); // null inner list
        l.add(new ArrayList<>()); // empty inner list
        ArrayList<PrimitiveTypes> filled = new ArrayList<>();
        filled.add(PrimitiveTypes.fillSample());
        l.add(filled);
        return l;
    }

    ArrayList<Integer[]> createListWithArrayContainingNulls() {
        ArrayList<Integer[]> l = new ArrayList<>();
        Integer[] a = {1, null, 3};
        Integer[] empty = {};
        l.add(a);
        l.add(empty);
        return l;
    }

    ArrayList<Object> createHeteroObjectList() {
        ArrayList<Object> l = new ArrayList<>();
        l.add("string");
        l.add(42);
        l.add(createThreeList());
        l.add(null);
        return l;
    }

    ArrayList<CheckService> createInterfaceListWithNull() {
        ArrayList<CheckService> l = new ArrayList<>();
        l.add(new CheckServiceImpl());
        l.add(null);
        return l;
    }

    // edge-case helpers and getters
    public ArrayList<Integer> getOversizedCapacityList() {
        return oversizedCapacityList;
    }

    public ArrayList<Integer> getLargeList() {
        return largeList;
    }

    public ArrayList<Integer[]> getListWithNullArrayEntries() {
        return listWithNullArrayEntries;
    }

    public ArrayList<String> getStringEdgeCasesList() {
        return stringEdgeCasesList;
    }

    ArrayList<Integer> createOversizedCapacityList() {
        ArrayList<Integer> l = new ArrayList<>(1000);
        l.add(1);
        l.add(2);
        l.add(3);
        return l;
    }

    ArrayList<Integer> createLargeList() {
        ArrayList<Integer> l = new ArrayList<>();
        for (int i = 0; i < 10_000; i++) {
            l.add(i);
        }
        return l;
    }

    ArrayList<Integer[]> createListWithNullArrayEntries() {
        ArrayList<Integer[]> l = new ArrayList<>();
        l.add(null);
        l.add(new Integer[]{1, 2, 3});
        return l;
    }

    ArrayList<String> createStringEdgeCasesList() {
        ArrayList<String> l = new ArrayList<>();
        l.add("");
        l.add("a" + ((char) 0) + "b"); // NUL inside string
        l.add(new String(Character.toChars(0x1F600))); // 4-byte UTF-8 (U+1F600 grinning face) via surrogate pair
        l.add(String.join("", Collections.nCopies(1000, "x"))); // long
        return l;
    }
}
