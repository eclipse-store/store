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

import java.util.concurrent.ConcurrentSkipListSet;

import static org.junit.jupiter.api.Assertions.assertIterableEquals;

public class ConcurrentSkipListSetData implements BinaryHandlerTestData {
    ConcurrentSkipListSet<String> value = new ConcurrentSkipListSet<>();

    @Override
    public ConcurrentSkipListSetData fillSampleData() {
        value.add("first");
        value.add("second");
        return this;
    }

    ConcurrentSkipListSet<String> getValue() {
        return value;
    }

    @Override
    public void proveResults(Object o) {
        Assertions.assertNotNull(o);
        ConcurrentSkipListSetData copy = (ConcurrentSkipListSetData) o;
        assertIterableEquals(this.getValue(), copy.getValue());
    }
}
