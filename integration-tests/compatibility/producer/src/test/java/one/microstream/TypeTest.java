package one.microstream;

/*-
 * #%L
 * EclipseStore Integration Tests - Compatibility - Producer
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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import org.eclipse.serializer.persistence.exceptions.PersistenceExceptionTypeNotPersistable;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import test.microstream.library.TypeEnum;


public class TypeTest {

    static Path targetDirectory = Path.of(System.getProperty("compatibility.storage.dir", "target/storage"));

    public EmbeddedStorageManager startStorage(Object root, Path actualDirectory) {
        return EmbeddedStorage.start(root, actualDirectory);
    }

    EmbeddedStorageManager storage;

    /**
     * Writes one storage per type with the producer's Eclipse Store version. Reading it back is the
     * consumer's job (current snapshot, possibly another JDK): an old release's own reload quirks must
     * not fail the compatibility check.
     */
    @ParameterizedTest
    @EnumSource(TypeEnum.class)
    public void writeStorage(TypeEnum type) {
        System.out.println(type.getOriginal().getClass().getSimpleName());

        if (type == TypeEnum.CopyOnWriteArraySet) {
            System.out.println("Skip for: " + type.getOriginal().getClass().getSimpleName());
            return;
        }

        Object original = type.getOriginal();
        Path actualDirectory = targetDirectory.resolve(original.getClass().getSimpleName());
        try {
            storage = startStorage(original, actualDirectory);
            storage.storeRoot();
        } catch (PersistenceExceptionTypeNotPersistable e) {
            // the producer release has no type handler for this type yet (e.g. Throwable in 1.0):
            // nothing to compare, the consumer skips the missing storage as well
            if (storage != null && storage.isRunning()) {
                storage.shutdown();
            }
            deleteRecursively(actualDirectory);
            Assumptions.abort("not persistable with this Eclipse Store release: " + e.getMessage());
        }
        storage.shutdown();
    }

    private static void deleteRecursively(Path dir) {
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
