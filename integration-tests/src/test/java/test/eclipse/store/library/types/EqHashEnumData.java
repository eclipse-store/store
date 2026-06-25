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

import org.eclipse.serializer.collections.EqHashEnum;
import org.junit.jupiter.api.Assertions;

import static org.junit.jupiter.api.Assertions.assertIterableEquals;

public class EqHashEnumData implements BinaryHandlerTestData {

    EqHashEnum<Integer> value = EqHashEnum.New();

    @Override
    public EqHashEnumData fillSampleData() {
        value.add(1567);
        value.add(555);
        return this;
    }

    EqHashEnum<Integer> getValue() {
        return value;
    }

    @Override
    public void proveResults(Object o) {
        Assertions.assertNotNull(o);
        EqHashEnumData copy = (EqHashEnumData) o;
        assertIterableEquals(this.getValue(), copy.getValue());
    }
}
