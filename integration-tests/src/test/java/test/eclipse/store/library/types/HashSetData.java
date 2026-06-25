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

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

public class HashSetData implements BinaryHandlerTestData {
    private HashSet<Integer> intSet = new HashSet<>();
    private HashSet<HashSet<HashSet<PrimitiveTypes>>> threeSet = new HashSet<>();
    private HashSet<PrimitiveTypes> primitiveTypeSet = new HashSet<>();

    @Override
    public HashSetData fillSampleData() {
        intSet = createIntSet();
        threeSet = createThreeSet();
        primitiveTypeSet = createHashSetPrimitiveTypes();
        return this;
    }

    HashSet<Integer> createIntSet() {
        HashSet<Integer> intSet = new HashSet<>();
        intSet.add(6);
        return intSet;
    }

    HashSet<HashSet<HashSet<PrimitiveTypes>>> createThreeSet() {
        HashSet<HashSet<HashSet<PrimitiveTypes>>> set = new HashSet<>();

        HashSet<PrimitiveTypes> primitive = new HashSet<>();
        primitive.add(PrimitiveTypes.fillSample());

        HashSet<HashSet<PrimitiveTypes>> setOfSet = new HashSet<>();
        setOfSet.add(primitive);
        set.add(setOfSet);

        return set;
    }

    HashSet<PrimitiveTypes> createHashSetPrimitiveTypes() {
        HashSet<PrimitiveTypes> set = new HashSet<>();
        set.add(PrimitiveTypes.fillSample());
        return set;
    }

    HashSet<Integer> getIntSet() {
        return intSet;
    }

    HashSet<HashSet<HashSet<PrimitiveTypes>>> getThreeSet() {
        return threeSet;
    }

    HashSet<PrimitiveTypes> getPrimitiveTypeSet() {
        return primitiveTypeSet;
    }


    @Override
    public void proveResults(Object o) {
        Assertions.assertNotNull(o);
        HashSetData copy = (HashSetData) o;
        assertAll("Array list Tests", //
                () -> assertIterableEquals(this.getIntSet(), copy.getIntSet()), //
                () -> assertIterableEquals(this.getThreeSet(), copy.getThreeSet()), //
                () -> assertIterableEquals(this.getPrimitiveTypeSet(), copy.getPrimitiveTypeSet())
        );
    }
}
