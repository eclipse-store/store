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

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.WeakHashMap;

import org.junit.jupiter.api.Assertions;

public class WeakHashMapData implements BinaryHandlerTestData {

    static private PrimitiveTypes primitiveTypes = PrimitiveTypes.fillSample();

    private WeakHashMap<Integer, PrimitiveTypes> primitiveTypeWeakHashMap;

    @Override
    public WeakHashMapData fillSampleData() {
        primitiveTypeWeakHashMap = new WeakHashMap<>();
        primitiveTypeWeakHashMap.put(105, WeakHashMapData.primitiveTypes);
        return this;
    }

    WeakHashMap<Integer, PrimitiveTypes> getPrimitiveTypeWeakHashMap() {
        return primitiveTypeWeakHashMap;
    }

    @Override
    public void proveResults(Object o) {
        Assertions.assertNotNull(o);
        WeakHashMapData copy = (WeakHashMapData) o;

        if (this.primitiveTypeWeakHashMap == null) {
            assertNull(copy.getPrimitiveTypeWeakHashMap());
        } else {
            assertTrue(copy.getPrimitiveTypeWeakHashMap()
                    .containsKey(105));
        }
    }
}
