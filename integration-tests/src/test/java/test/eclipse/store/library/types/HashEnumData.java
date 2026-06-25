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

import org.eclipse.serializer.collections.HashEnum;
import org.junit.jupiter.api.Assertions;

import static org.junit.jupiter.api.Assertions.assertIterableEquals;

public class HashEnumData implements BinaryHandlerTestData {

    HashEnum<Integer> value = HashEnum.New();

    @Override
    public HashEnumData fillSampleData() {
        value.add(1567);
        value.add(555);
        return this;
    }

    @Override
    public void proveResults(Object o) {
        Assertions.assertNotNull(o);
        HashEnumData copy = (HashEnumData) o;
        assertIterableEquals(this.value, copy.value);
    }
}
