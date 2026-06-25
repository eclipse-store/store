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

import java.util.Hashtable;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

public class HashtableData implements BinaryHandlerTestData {
    private Hashtable<Integer, Integer> intTable = createEmptyIntMap();
    private Hashtable<Integer, Hashtable<Integer, Hashtable<Integer, PrimitiveTypes>>> threeTable = createEmptyThreeMap();
    private Hashtable<Integer, PrimitiveTypes> primitiveTypeHashTable = new Hashtable<>();

    @Override
    public HashtableData fillSampleData() {
        intTable = createIntMap();
        threeTable = createThreeMap();
        primitiveTypeHashTable = new Hashtable<>();
        primitiveTypeHashTable.put(105, PrimitiveTypes.fillSample());
        return this;
    }

    Hashtable<Integer, Integer> createIntMap() {
        Hashtable<Integer, Integer> intMap = new Hashtable<>();
        intMap.put(100, 6);
        return intMap;
    }

    Hashtable<Integer, Integer> createEmptyIntMap() {
        Hashtable<Integer, Integer> intMap = new Hashtable<>();
        intMap.put(100, 0);
        return intMap;
    }

    Hashtable<Integer, Hashtable<Integer, Hashtable<Integer, PrimitiveTypes>>> createEmptyThreeMap() {
        Hashtable<Integer, Hashtable<Integer, Hashtable<Integer, PrimitiveTypes>>> map = new Hashtable<>();

        Hashtable<Integer, PrimitiveTypes> primitive = new Hashtable<>();
        primitive.put(100, new PrimitiveTypes());

        Hashtable<Integer, Hashtable<Integer, PrimitiveTypes>> hashMap = new Hashtable<>();
        hashMap.put(100, primitive);
        map.put(100, hashMap);

        return map;
    }

    Hashtable<Integer, Hashtable<Integer, Hashtable<Integer, PrimitiveTypes>>> createThreeMap() {
        Hashtable<Integer, Hashtable<Integer, Hashtable<Integer, PrimitiveTypes>>> map = new Hashtable<>();

        Hashtable<Integer, PrimitiveTypes> primitive = new Hashtable<>();
        primitive.put(100, PrimitiveTypes.fillSample());

        Hashtable<Integer, Hashtable<Integer, PrimitiveTypes>> hashMap = new Hashtable<>();
        hashMap.put(100, primitive);
        map.put(100, hashMap);

        return map;
    }

    Hashtable<Integer, Integer> getIntTable() {
        return intTable;
    }

    Hashtable<Integer, Hashtable<Integer, Hashtable<Integer, PrimitiveTypes>>> getThreeTable() {
        return threeTable;
    }

    Hashtable<Integer, PrimitiveTypes> getPrimitiveTypeHashTable() {
        return primitiveTypeHashTable;
    }

    @Override
    public void proveResults(Object o) {
        Assertions.assertNotNull(o);
        HashtableData copy = (HashtableData) o;

        assertAll("Array list Tests", //
                () -> assertIterableEquals(this.getIntTable().values(), copy.getIntTable().values()), //
                () -> assertEquals(this.getThreeTable().get(100).get(100).get(100), copy.getThreeTable().get(100).get(100).get(100)),
                () -> assertIterableEquals(this.getPrimitiveTypeHashTable().values(), copy.getPrimitiveTypeHashTable().values())
        );

    }
}
