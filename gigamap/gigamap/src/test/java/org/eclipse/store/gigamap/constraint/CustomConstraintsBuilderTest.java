package org.eclipse.store.gigamap.constraint;

/*-
 * #%L
 * EclipseStore GigaMap
 * %%
 * Copyright (C) 2023 - 2025 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import org.eclipse.store.gigamap.data.Entity;
import org.eclipse.store.gigamap.exceptions.ConstraintViolationException;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class CustomConstraintsBuilderTest
{
    final static String CONSTRAINT_VIOLATION_WORD = "abcBADdef";


    @Test
    void customConstraints_Builder(@TempDir Path tempDir)
    {

        final GigaMap<Entity> map = GigaMap.<Entity>Builder().withCustomConstraint(new NoBadValueConstraint()).build();

        map.add(Entity.Random().setWord("abc"));
        map.add(Entity.Random().setWord("def"));
        assertThrows(
                ConstraintViolationException.class,
                () -> map.add(Entity.Random().setWord(CONSTRAINT_VIOLATION_WORD))
        );

        try (EmbeddedStorageManager storage = EmbeddedStorage.start(map, tempDir)) {

        }

        try (EmbeddedStorageManager storage = EmbeddedStorage.start(tempDir)) {
            GigaMap<Entity> loadedMap = (GigaMap<Entity>) storage.root();
            assertThrows(
                    ConstraintViolationException.class,
                    () -> loadedMap.add(Entity.Random().setWord(CONSTRAINT_VIOLATION_WORD))
            );
            loadedMap.add(Entity.Random().setWord("good word"));
        }

    }

    @Test
    void customConstraints_UpdateApi_Builder(@TempDir Path tempDir)
    {

        final GigaMap<Entity> map = GigaMap.<Entity>Builder().withCustomConstraint(new NoBadValueConstraint()).build();

        map.add(Entity.Random().setWord("abc"));
        map.add(Entity.Random().setWord("def"));
        assertThrows(
                ConstraintViolationException.class,
                () -> map.add(Entity.Random().setWord(CONSTRAINT_VIOLATION_WORD))
        );

        try (EmbeddedStorageManager storage = EmbeddedStorage.start(map, tempDir)) {

        }

        GigaMap<Entity> loadedMap = GigaMap.New();
        try (EmbeddedStorageManager storage = EmbeddedStorage.start(loadedMap, tempDir)) {
            assertThrows(
                    ConstraintViolationException.class,
                    () -> loadedMap.add(Entity.Random().setWord(CONSTRAINT_VIOLATION_WORD))
            );
            loadedMap.add(Entity.Random().setWord("good word"));
        }

    }
}
