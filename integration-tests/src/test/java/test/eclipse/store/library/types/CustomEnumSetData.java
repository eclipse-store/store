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

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CustomEnumSetData implements BinaryHandlerTestData {
    EnumSetData value = new EnumSetData();

    @Override
    public CustomEnumSetData fillSampleData() {
        value.setOriginal(EnumSet.of(Tasks.WORK, Tasks.HOBBY));
        return this;
    }

    EnumSetData getValue() {
        return value;
    }

    private enum Tasks {
        WORK(10), HOBBY(20);

        private int value;

        Tasks(int value) {
            this.value = value;
        }
    }

    static class EnumSetData  {
        Set<Tasks> original;

        public Set<Tasks> getOriginal() {
            return original;
        }

        public void setOriginal(Set<Tasks> original) {
            this.original = original;
        }
    }


    @Override
    public void proveResults(Object o) {
        Assertions.assertNotNull(o);
        CustomEnumSetData copy = (CustomEnumSetData) o;
        assertEquals(this.getValue().getOriginal(), copy.getValue().getOriginal());
    }
}
