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

import org.eclipse.serializer.collections.ConstHashTable;
import org.eclipse.serializer.typing.KeyValue;
import org.junit.jupiter.api.Assertions;

import static org.junit.jupiter.api.Assertions.*;

public class ConstHashTableData implements BinaryHandlerTestData {

    ConstHashTable<Integer, PrimitiveTypes> constHashTable = ConstHashTable.New();

    @Override
    public ConstHashTableData fillSampleData() {
        PrimitiveTypes p = new PrimitiveTypes();
        p.fillSampleData();
        constHashTable = ConstHashTable.NewCustom(2, 10, KeyValue.New(10, p));
        return this;
    }

    ConstHashTable<Integer, PrimitiveTypes> getConstHashTable() {
        return constHashTable;
    }

    @Override
    public void proveResults(Object o) {
        Assertions.assertNotNull(o);
        ConstHashTableData copy = (ConstHashTableData) o;
        assertAll("BinaryHandlerConstHashEnumTest",
                () -> assertEquals(this.getConstHashTable().intSize(), copy.getConstHashTable().intSize()),
                () -> assertIterableEquals(this.getConstHashTable().values(), copy.getConstHashTable().values())
        );
    }
}
