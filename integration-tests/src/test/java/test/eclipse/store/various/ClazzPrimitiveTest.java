package test.eclipse.store.various;

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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.Serializable;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class ClazzPrimitiveTest
{
    // --- helper types used as Class<?> representatives ---

    interface SampleInterface {}

    enum SampleEnum { VALUE }

    @SuppressWarnings("InnerClassMayBeStatic")
    abstract static class SampleAbstractClass {}

    @interface SampleAnnotation {}

    // --- test data ---

    static Stream<Class<?>> allClassVariants()
    {
        return Stream.of(
            // Primitive types
            int.class,
            long.class,
            double.class,
            float.class,
            boolean.class,
            byte.class,
            short.class,
            char.class,
            // Void pseudo-type
            //void.class, //https://github.com/eclipse-serializer/serializer/issues/247
            // Boxed types
            Integer.class,
            Long.class,
            Double.class,
            Float.class,
            Boolean.class,
            Byte.class,
            Short.class,
            Character.class,
            Void.class,
            // Common reference types
            String.class,
            Object.class,
            Number.class,
            // Interface
            SampleInterface.class,
            Serializable.class,
            Iterable.class,
            // Enum
            SampleEnum.class,
            // Abstract class
            SampleAbstractClass.class,
            // Annotation type
            SampleAnnotation.class,
            // Array types – primitives
            int[].class,
            long[].class,
            double[].class,
            float[].class,
            boolean[].class,
            byte[].class,
            short[].class,
            char[].class,
            // Array types – reference
            String[].class,
            Object[].class,
            Integer[].class,
            // Multi-dimensional arrays
            int[][].class,
            String[][].class
        );
    }

    @TempDir
    Path tempDir;

    @ParameterizedTest(name = "clazzTest [{index}] {0}")
    @MethodSource("allClassVariants")
    void clazzTest(Class<?> clazz)
    {
        ClazzData root = new ClazzData(clazz);
        try (EmbeddedStorageManager manager = EmbeddedStorage.start(root, tempDir))
        {
            manager.storeRoot();
        }

        ClazzData root1 = new ClazzData();
        try (EmbeddedStorageManager manager = EmbeddedStorage.start(root1, tempDir))
        {
            System.out.println("Loaded class: " + root1.getClazz());
            assertEquals(clazz.getTypeName(), root1.getClazz().getTypeName(),
                "Type name mismatch for: " + clazz);
        }
    }

    // --- root object ---

    private static class ClazzData
    {
        private Class<?> clazz;

        public ClazzData(Class<?> clazz)
        {
            this.clazz = clazz;
        }

        public ClazzData()
        {
        }

        public Class<?> getClazz()
        {
            return clazz;
        }

        public void setClazz(Class<?> clazz)
        {
            this.clazz = clazz;
        }
    }
}
