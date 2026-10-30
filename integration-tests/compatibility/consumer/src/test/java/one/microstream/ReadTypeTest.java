package one.microstream;

/*-
 * #%L
 * EclipseStore Integration Tests - Compatibility - Consumer
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

import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import test.microstream.library.TypeEnum;


public class ReadTypeTest {
    static Path targetDirectory = Path.of(System.getProperty("compatibility.storage.dir", "target/storage"));

    public EmbeddedStorageManager startStorage(Object root, Path actualDirectory) {
        return EmbeddedStorage.start(root, actualDirectory);
    }

    EmbeddedStorageManager storage;

    @ParameterizedTest
    @EnumSource(value = TypeEnum.class, names = {"BitSet"}, mode = EnumSource.Mode.EXCLUDE)
    public void saveAndLoadTest(TypeEnum type) {
        System.out.println(type.getOriginal().getClass().getSimpleName());

        if (type == TypeEnum.CopyOnWriteArraySet) {
            System.out.println("Skip for: " + type.getOriginal().getClass().getSimpleName());

            return;
        }

        Object o = type.createEmptyInstance();
        Assumptions.assumeTrue(Files.isDirectory(targetDirectory.resolve(o.getClass().getSimpleName())),
            "the producer release could not persist this type, nothing to read");
        Object loaded = saveToMSAndReload(o);

        type.getOriginal().proveResults(loaded);

        storageShutdown();
    }

    public void storageShutdown() {
        storage.shutdown();
    }

    public Object saveToMSAndReload(Object loaded) {
        Path actualDirectory = targetDirectory.resolve(loaded.getClass().getSimpleName());

        storage = startStorage(loaded, actualDirectory);
        return loaded;
    }

}
