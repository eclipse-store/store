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

import org.eclipse.serializer.collections.ConstList;
import org.junit.jupiter.api.Assertions;

import static org.junit.jupiter.api.Assertions.assertIterableEquals;

public class ConstListData implements BinaryHandlerTestData {

    ConstList<PrimitiveTypes> value = ConstList.New();

    @Override
    public ConstListData fillSampleData() {
        PrimitiveTypes p = new PrimitiveTypes();
        p.fillSampleData();
        value = ConstList.New(new PrimitiveTypes(), p, new PrimitiveTypes());
        return this;
    }

    ConstList<PrimitiveTypes> getValue() {
        return value;
    }

    @Override
    public void proveResults(Object o) {
        Assertions.assertNotNull(o);
        ConstListData copy = (ConstListData) o;
        assertIterableEquals(this.getValue(), copy.getValue());
    }
}
