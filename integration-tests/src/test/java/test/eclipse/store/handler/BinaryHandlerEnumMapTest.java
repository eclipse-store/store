package test.eclipse.store.handler;

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

import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;

@Disabled
class BinaryHandlerEnumMapTest extends AbstractHandlerTest<BinaryHandlerEnumMapTest.EnumMapData> {

    BinaryHandlerEnumMapTest() {
        super(EnumMapData.class);
    }

    @Override
    public void proveResult(EnumMapData original, EnumMapData copy) {
        Assertions.assertEquals(original.getOriginal(), copy.getOriginal());
    }


    static enum Tasks {
        WORK(10), HOBBY(20);

        private int value;

        Tasks(int value) {
            this.value = value;
        }
    }

    static class EnumMapData implements BinaryHandlerTestData {
        Map<Tasks, String> original = new EnumMap<>(Tasks.class);

        @Override
        public void fillSampleData() {
            original.put(Tasks.WORK, "work");
            original.put(Tasks.HOBBY, "hobby");
        }

        public Map<Tasks, String> getOriginal() {
            return original;
        }
    }

}
