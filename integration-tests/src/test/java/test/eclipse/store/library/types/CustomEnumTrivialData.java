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

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CustomEnumTrivialData implements BinaryHandlerTestData {
    CustomEnumTrivialEnumData value;

    @Override
    public CustomEnumTrivialData fillSampleData() {
        value = CustomEnumTrivialEnumData.FIRST_VALUE;
        return this;
    }

    CustomEnumTrivialEnumData getValue() {
        return value;
    }


    private enum CustomEnumTrivialEnumData {
        FIRST_VALUE
    }

    @Override
    public void proveResults(Object o) {
        Assertions.assertNotNull(o);
        CustomEnumTrivialData copy = (CustomEnumTrivialData) o;
        assertEquals(this.getValue(), copy.getValue());
    }
}
