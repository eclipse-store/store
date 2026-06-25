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

import org.eclipse.serializer.collections.ConstHashEnum;
import org.junit.jupiter.api.Assertions;

import static org.junit.jupiter.api.Assertions.*;

public class ConstHashEnumData implements BinaryHandlerTestData {

    ConstHashEnum<PrimitiveTypes> constHashEnum = ConstHashEnum.New();

    @Override
    public ConstHashEnumData fillSampleData() {
        PrimitiveTypes p = new PrimitiveTypes();
        p.fillSampleData();
        constHashEnum = ConstHashEnum.NewCustom(1.0f, new PrimitiveTypes(), p);
        return this;
    }

    ConstHashEnum<PrimitiveTypes> getConstHashEnum() {
        return constHashEnum;
    }

    @Override
    public void proveResults(Object o) {
        Assertions.assertNotNull(o);
        ConstHashEnumData copy = (ConstHashEnumData) o;

        assertAll("BinaryHandlerConstHashEnumTest",
                () -> assertEquals(this.getConstHashEnum().intSize(), copy.getConstHashEnum().intSize()),
                () -> assertIterableEquals(this.getConstHashEnum(), copy.getConstHashEnum())
        );
    }
}
