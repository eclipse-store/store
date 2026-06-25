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

import org.eclipse.serializer.collections.HashTable;
import org.junit.jupiter.api.Assertions;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

public class HashTableMSData implements BinaryHandlerTestData {
    private HashTable<Integer, Integer> intTable = createEmptyIntMap();
    private HashTable<Integer, HashTable<Integer, HashTable<Integer, PrimitiveTypes>>> threeTable = createEmptyThreeMap();
    private HashTable<Integer, PrimitiveTypes> primitiveTypeHashTable = HashTable.New();

    @Override
    public HashTableMSData fillSampleData() {
        intTable = createIntMap();
        threeTable = createThreeMap();
        primitiveTypeHashTable = HashTable.New();
        PrimitiveTypes p = new PrimitiveTypes();
        p.fillSampleData();
        primitiveTypeHashTable.put(105, p);
        return this;
    }


    HashTable<Integer, Integer> createEmptyIntMap() {
        HashTable<Integer, Integer> intMap = HashTable.New();
        intMap.put(100, 0);
        return intMap;
    }
    HashTable<Integer, Integer> createIntMap() {
        HashTable<Integer, Integer> intMap = HashTable.New();
        intMap.put(100, 6);
        return intMap;
    }

    HashTable<Integer, HashTable<Integer, HashTable<Integer, PrimitiveTypes>>> createEmptyThreeMap() {
        HashTable<Integer, HashTable<Integer, HashTable<Integer, PrimitiveTypes>>> map = HashTable.New();

        HashTable<Integer, PrimitiveTypes> primitive = HashTable.New();
        PrimitiveTypes p = new PrimitiveTypes();
        primitive.put(100, p);

        HashTable<Integer, HashTable<Integer, PrimitiveTypes>> hashMap = HashTable.New();
        hashMap.put(100, primitive);
        map.put(100, hashMap);

        return map;
    }

    HashTable<Integer, HashTable<Integer, HashTable<Integer, PrimitiveTypes>>> createThreeMap() {
        HashTable<Integer, HashTable<Integer, HashTable<Integer, PrimitiveTypes>>> map = HashTable.New();

        HashTable<Integer, PrimitiveTypes> primitive = HashTable.New();
        PrimitiveTypes p = new PrimitiveTypes();
        p.fillSampleData();
        primitive.put(100, p);

        HashTable<Integer, HashTable<Integer, PrimitiveTypes>> hashMap = HashTable.New();
        hashMap.put(100, primitive);
        map.put(100, hashMap);

        return map;
    }

    HashTable<Integer, Integer> getIntTable() {
        return intTable;
    }

    HashTable<Integer, HashTable<Integer, HashTable<Integer, PrimitiveTypes>>> getThreeTable() {
        return threeTable;
    }

    HashTable<Integer, PrimitiveTypes> getPrimitiveTypeHashTable() {
        return primitiveTypeHashTable;
    }

    @Override
    public void proveResults(Object o) {
        Assertions.assertNotNull(o);
        HashTableMSData copy = (HashTableMSData)o;
        assertAll("Array list Tests", //
                () -> assertIterableEquals(this.getIntTable().values(), copy.getIntTable().values()), //
                () -> assertEquals(this.getThreeTable().get(100).get(100).get(100), copy.getThreeTable().get(100).get(100).get(100)),
                () -> assertIterableEquals(this.getPrimitiveTypeHashTable().values(), copy.getPrimitiveTypeHashTable().values())
        );
    }
}
