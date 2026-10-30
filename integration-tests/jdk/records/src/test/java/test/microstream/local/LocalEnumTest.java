package test.microstream.local;

/*-
 * #%L
 * EclipseStore Integration Tests - JDK - Records
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

import org.eclipse.serializer.persistence.exceptions.PersistenceExceptionTypeNotPersistable;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

public class LocalEnumTest {

    @TempDir
    Path storageDir;

    EmbeddedStorageManager storageManager;

    @AfterEach
    public void closeAll() {
        if (storageManager == null) {
            return;
        }
        if (storageManager.isRunning()) {
            storageManager.shutdown();
        }
    }

    /**
     * one.microstream.persistence.exceptions.PersistenceExceptionTypeNotPersistable: Type not persistable:
     * "class test.microstream.local.LocalEnumTest$2Car". Details: Synthetic classes ($1 etc.) are not reliably
     * persistable since a simple reordering of source code elements would change the name identity of a class.
     * For a type system that has to rely upon resolving types by their identifying name, this would silently cause a
     * potentially fatal error. If handling synthetic classes (e.g. anonymous inner classes) is absolutely necessary,
     * a custom one.microstream.persistence.types.PersistenceTypeResolver can be used to remove the exception
     * and assume  complete responsibility for correctly handling synthetic class names.
     */
    @Test
    public void localEnumSaveTest() {

        enum Color {PURPLE, SAFETY_ORANGE}

        class Car {
            String Name;
            Color color;

            public Car(String name, Color color) {
                Name = name;
                this.color = color;
            }

            public Car() {
            }

            @Override
            public String toString() {
                return "Car{" +
                        "Name='" + Name + '\'' +
                        ", color=" + color +
                        '}';
            }
        }

        Car car = new Car("Volvo", Color.PURPLE);

        Assertions.assertThrows(PersistenceExceptionTypeNotPersistable.class, () -> EmbeddedStorage.start(car, storageDir));
    }

}
