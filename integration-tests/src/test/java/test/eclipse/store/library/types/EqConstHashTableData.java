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

import org.eclipse.serializer.collections.EqConstHashTable;
import org.eclipse.serializer.typing.KeyValue;
import org.junit.jupiter.api.Assertions;

import static org.junit.jupiter.api.Assertions.*;

public class EqConstHashTableData implements BinaryHandlerTestData {

    private EqConstHashTable<Integer, PrimitiveTypes> eqConstHashTable = EqConstHashTable.New();

    @Override
    public EqConstHashTableData fillSampleData() {
        eqConstHashTable = EqConstHashTable.NewCustom(2, 10, KeyValue.New(10, PrimitiveTypes.fillSample()));
        return this;
    }

    EqConstHashTable<Integer, PrimitiveTypes> getEqConstHashTable() {
        return eqConstHashTable;
    }

    @Override
    public void proveResults(Object o) {
        Assertions.assertNotNull(o);
        EqConstHashTableData copy = (EqConstHashTableData) o;
        assertAll("BinaryHandlerConstHashEnumTest",
                () -> assertEquals(this.getEqConstHashTable().intSize(), copy.getEqConstHashTable().intSize()),
                () -> assertIterableEquals(this.getEqConstHashTable().values(), copy.getEqConstHashTable().values())
        );
    }
}
