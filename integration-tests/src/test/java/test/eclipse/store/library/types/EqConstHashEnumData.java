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

import org.eclipse.serializer.collections.BulkList;
import org.eclipse.serializer.collections.EqConstHashEnum;
import org.junit.jupiter.api.Assertions;

import static org.junit.jupiter.api.Assertions.*;

public class EqConstHashEnumData implements BinaryHandlerTestData {

    EqConstHashEnum<PrimitiveTypes> constHashEnum = EqConstHashEnum.New();

    @Override
    public EqConstHashEnumData fillSampleData() {
        BulkList<PrimitiveTypes> bulkList = new BulkList<>();
        PrimitiveTypes p = new PrimitiveTypes();
        p.fillSampleData();
        bulkList.add(p);
        constHashEnum = EqConstHashEnum.New(bulkList);
        return this;
    }

    EqConstHashEnum<PrimitiveTypes> getConstHashEnum() {
        return constHashEnum;
    }

    @Override
    public void proveResults(Object o) {
        Assertions.assertNotNull(o);
        EqConstHashEnumData copy = (EqConstHashEnumData)o;
        assertAll("BinaryHandlerConstHashEnumTest",
                () -> assertEquals(this.getConstHashEnum().intSize(), copy.getConstHashEnum().intSize()),
                () -> assertIterableEquals(this.getConstHashEnum(), copy.getConstHashEnum())
        );
    }
}
