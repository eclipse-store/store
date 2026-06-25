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

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

import static org.junit.jupiter.api.Assertions.assertIterableEquals;

public class CopyOnWriteArraySetData implements BinaryHandlerTestData {

        CopyOnWriteArraySet<PrimitiveTypes> value = new CopyOnWriteArraySet<>();

        @Override
        public CopyOnWriteArraySetData fillSampleData() {
            PrimitiveTypes p = new PrimitiveTypes();
            p.fillSampleData();

            value.add(new PrimitiveTypes());
            value.add(p);
            value.add(new PrimitiveTypes());

            return this;
        }

    CopyOnWriteArraySet<PrimitiveTypes> getValue() {
            return value;
        }

    @Override
    public void proveResults(Object o) {
        Assertions.assertNotNull(o);
        CopyOnWriteArraySetData copy = (CopyOnWriteArraySetData) o;

        assertIterableEquals(this.getValue(), copy.getValue());
    }
}
