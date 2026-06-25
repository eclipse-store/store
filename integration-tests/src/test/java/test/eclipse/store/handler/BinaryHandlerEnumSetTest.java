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

import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;

@Disabled
class BinaryHandlerEnumSetTest extends AbstractHandlerTest<BinaryHandlerEnumSetTest.EnumSetData> {

    BinaryHandlerEnumSetTest() {
        super(EnumSetData.class);
    }

    @Override
    public void proveResult(EnumSetData original, EnumSetData copy) {
        Assertions.assertEquals(original.getOriginal(), copy.getOriginal());
    }


    static enum Tasks {
        WORK(10), HOBBY(20);

        private int value;

        Tasks(int value) {
            this.value = value;
        }
    }

    static class EnumSetData implements BinaryHandlerTestData {
        Set<Tasks> original;

        @Override
        public void fillSampleData() {
            original = EnumSet.of(Tasks.WORK, Tasks.HOBBY);
        }

        public Set<Tasks> getOriginal() {
            return original;
        }
    }

}
