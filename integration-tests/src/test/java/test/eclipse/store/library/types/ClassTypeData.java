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

import java.time.DayOfWeek;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class ClassTypeData implements BinaryHandlerTestData {
    Class clazz;

    // ===== proposed edge-cases (review & cherry-pick) =====
    // Class.equals is reference identity — Class objects are singletons per (ClassLoader, name). After
    // round-trip ES must resolve the deserialized Class to the same singleton this JVM already holds.
    // The fields below cover JDK encoding rules where Class.getName() differs from the canonical name:
    //   array classes ("[Ljava.lang.String;", "[[I"), nested interface (java.util.Map$Entry).
    //
    // NOT covered here — ES rejects these via PersistenceTypeHandlerEnsurer (by design, not a test gap):
    //   - primitives: int.class, void.class, …  → "Primitive types must be handled by default (dummy)
    //     handler implementations." (BinaryHandlerClass.instanceState → ensureTypeHandler)
    //   - Class.class itself                    → "Persisting Class instances requires a special-tailored
    //     PersistenceTypeHandler and cannot be done in a generic way."
    private Class arrayClass;
    private Class multiDimArrayClass;
    private Class interfaceClass;
    private Class enumClass;
    private Class objectClass;
    private Class nestedClass;

    @Override
    public ClassTypeData fillSampleData() {
        clazz = PrimitiveTypes.class;

        // ===== proposed edge-cases =====
        arrayClass = String[].class;
        multiDimArrayClass = int[][].class;
        interfaceClass = Runnable.class;
        enumClass = DayOfWeek.class;
        objectClass = Object.class;
        nestedClass = Map.Entry.class;

        return this;
    }

    Class getClazz() {
        return clazz;
    }

    // ===== proposed edge-cases — getters =====

    public Class getArrayClass() {
        return arrayClass;
    }

    public Class getMultiDimArrayClass() {
        return multiDimArrayClass;
    }

    public Class getInterfaceClass() {
        return interfaceClass;
    }

    public Class getEnumClass() {
        return enumClass;
    }

    public Class getObjectClass() {
        return objectClass;
    }

    public Class getNestedClass() {
        return nestedClass;
    }

    @Override
    public void proveResults(Object o) {
        Assertions.assertNotNull(o);
        ClassTypeData copy = (ClassTypeData) o;
        assertAll("Class type tests",
                () -> assertEquals(this.clazz, copy.getClazz()),

                // ===== proposed edge-case verifications =====
                // Class.equals is identity (==); ES must resolve every binary name back to the JVM's singleton.
                () -> {
                    if (this.arrayClass != null) {
                        assertEquals(String[].class, copy.getArrayClass(), "String[].class");
                    } else {
                        assertNull(copy.getArrayClass());
                    }
                },
                () -> {
                    if (this.multiDimArrayClass != null) {
                        assertEquals(int[][].class, copy.getMultiDimArrayClass(), "int[][].class (multi-dim primitive array)");
                    } else {
                        assertNull(copy.getMultiDimArrayClass());
                    }
                },
                () -> {
                    if (this.interfaceClass != null) {
                        assertEquals(Runnable.class, copy.getInterfaceClass(), "Runnable.class (interface)");
                    } else {
                        assertNull(copy.getInterfaceClass());
                    }
                },
                () -> {
                    if (this.enumClass != null) {
                        assertEquals(DayOfWeek.class, copy.getEnumClass(), "DayOfWeek.class (enum)");
                    } else {
                        assertNull(copy.getEnumClass());
                    }
                },
                () -> {
                    if (this.objectClass != null) {
                        assertEquals(Object.class, copy.getObjectClass(), "Object.class (hierarchy root)");
                    } else {
                        assertNull(copy.getObjectClass());
                    }
                },
                () -> {
                    if (this.nestedClass != null) {
                        assertEquals(Map.Entry.class, copy.getNestedClass(), "Map.Entry.class (nested static interface)");
                    } else {
                        assertNull(copy.getNestedClass());
                    }
                }
        );
    }
}
