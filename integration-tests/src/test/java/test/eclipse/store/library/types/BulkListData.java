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
import org.junit.jupiter.api.Assertions;

import static org.junit.jupiter.api.Assertions.assertIterableEquals;


public class BulkListData implements BinaryHandlerTestData {

    private BulkList BulkListValue;

    @Override
    public BulkListData fillSampleData() {
        BulkListValue = new BulkList(456);
        return this;
    }

    @Override
    public BinaryHandlerTestData updateSampleData() {
        getBulkListValue().add(new StringBuffer().append("ahoj"));
        return this;
    }

    BulkList getBulkListValue() {
        return BulkListValue;
    }

    @Override
    public void proveResults(Object o) {
        Assertions.assertNotNull(o);
        BulkListData copy = (BulkListData) o;
        assertIterableEquals(this.getBulkListValue(), copy.getBulkListValue(), "BulkList");
    }
}
