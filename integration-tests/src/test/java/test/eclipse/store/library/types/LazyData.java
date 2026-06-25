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

import org.eclipse.serializer.reference.Lazy;
import org.junit.jupiter.api.Assertions;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LazyData implements BinaryHandlerTestData {
        Lazy<PrimitiveTypes> lazy = Lazy.Reference(new PrimitiveTypes());

        @Override
        public LazyData fillSampleData() {
            lazy.get().fillSampleData();
            return this;
        }

        public Lazy<PrimitiveTypes> getLazy() {
            return lazy;
        }

    @Override
    public void proveResults(Object o) {
        Assertions.assertNotNull(o);
        LazyData copy = (LazyData) o;
        assertFalse(copy.getLazy().isLoaded(), "is Loaded Before get");
        copy.getLazy().get();
        assertTrue(copy.getLazy().isLoaded(), "is loaded after get");
        lazy.get().proveResults(copy.lazy.get());
    }

    @Override
    public String toString()
    {
        return "LazyData{" +
                "lazy=" + lazy +
                "PrimitiveTypes=" + lazy.get() +
                '}';
    }
}
