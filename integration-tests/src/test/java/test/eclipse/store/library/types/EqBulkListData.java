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

import org.eclipse.serializer.collections.EqBulkList;
import org.eclipse.serializer.equality.Equalator;
import org.junit.jupiter.api.Assertions;

import static org.junit.jupiter.api.Assertions.assertIterableEquals;

public class EqBulkListData implements BinaryHandlerTestData {

    private EqBulkList<Integer> eqBulkListValue;

    @Override
    public EqBulkListData fillSampleData() {
        Integer first = 130;
        Integer second = 300;
        eqBulkListValue = new EqBulkList<>(new IntegerEquality(), first, second);
        return this;
    }

    EqBulkList<Integer> getEqBulkListValue() {
        return eqBulkListValue;
    }

    @Override
    public void proveResults(Object o) {
        Assertions.assertNotNull(o);
        EqBulkListData copy = (EqBulkListData) o;
        assertIterableEquals(this.getEqBulkListValue(), copy.getEqBulkListValue(), "EqBulkList");
    }

    static class IntegerEquality implements Equalator<Integer>
    {
        IntegerEquality() {
        }

        @Override
        public boolean equal(Integer integer, Integer t1) {
            return integer.equals(t1);
        }
    }

}
