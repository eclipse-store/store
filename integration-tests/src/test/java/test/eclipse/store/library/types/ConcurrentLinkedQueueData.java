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

import java.util.concurrent.ConcurrentLinkedQueue;

public class ConcurrentLinkedQueueData implements BinaryHandlerTestData {
    ConcurrentLinkedQueue<PrimitiveTypes> value = new ConcurrentLinkedQueue<>();

    @Override
    public ConcurrentLinkedQueueData fillSampleData() {
        value.add(PrimitiveTypes.fillSample());
        value.add(new PrimitiveTypes());
        return this;
    }

    ConcurrentLinkedQueue<PrimitiveTypes> getValue() {
        return value;
    }

    @Override
    public void proveResults(Object o) {
        Assertions.assertNotNull(o);
        ConcurrentLinkedQueueData copy = (ConcurrentLinkedQueueData) o;
        Assertions.assertIterableEquals(this.getValue(), copy.getValue());
    }
}
