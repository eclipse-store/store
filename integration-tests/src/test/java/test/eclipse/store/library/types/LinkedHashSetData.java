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

import java.util.LinkedHashSet;

import static org.junit.jupiter.api.Assertions.*;

public class LinkedHashSetData implements BinaryHandlerTestData {
    private LinkedHashSet<Integer> intSet = new LinkedHashSet<>();
    private LinkedHashSet<LinkedHashSet<LinkedHashSet<PrimitiveTypes>>> threeSet = new LinkedHashSet<>();
    private LinkedHashSet<PrimitiveTypes> primitiveTypeSet = new LinkedHashSet<>();

    @Override
    public LinkedHashSetData fillSampleData() {
        intSet = createIntSet();
        threeSet = createThreeSet();
        primitiveTypeSet = createLinkedHashSetPrimitiveTypes();
        return this;
    }


    LinkedHashSet<Integer> createIntSet() {
        LinkedHashSet<Integer> intSet = new LinkedHashSet<>();
        intSet.add(6);
        return intSet;
    }

    LinkedHashSet<LinkedHashSet<LinkedHashSet<PrimitiveTypes>>> createThreeSet() {
        LinkedHashSet<LinkedHashSet<LinkedHashSet<PrimitiveTypes>>> set = new LinkedHashSet<>();

        LinkedHashSet<PrimitiveTypes> primitive = new LinkedHashSet<>();
        primitive.add(PrimitiveTypes.fillSample());

        LinkedHashSet<LinkedHashSet<PrimitiveTypes>> setOfSet = new LinkedHashSet<>();
        setOfSet.add(primitive);
        set.add(setOfSet);

        return set;
    }

    LinkedHashSet<PrimitiveTypes> createLinkedHashSetPrimitiveTypes() {
        LinkedHashSet<PrimitiveTypes> set = new LinkedHashSet<>();
        set.add(PrimitiveTypes.fillSample());
        return set;
    }

    LinkedHashSet<Integer> getIntSet() {
        return intSet;
    }

    LinkedHashSet<LinkedHashSet<LinkedHashSet<PrimitiveTypes>>> getThreeSet() {
        return threeSet;
    }

    LinkedHashSet<PrimitiveTypes> getPrimitiveTypeSet() {
        return primitiveTypeSet;
    }

    @Override
    public void proveResults(Object o) {
        Assertions.assertNotNull(o);
        LinkedHashSetData copy = (LinkedHashSetData) o;
        assertAll("Array list Tests", //
                () -> assertIterableEquals(this.getIntSet(), copy.getIntSet()), //
                () -> assertIterableEquals(this.getThreeSet(), copy.getThreeSet()), //
                () -> assertIterableEquals(this.getPrimitiveTypeSet(), copy.getPrimitiveTypeSet())
        );
    }
}
