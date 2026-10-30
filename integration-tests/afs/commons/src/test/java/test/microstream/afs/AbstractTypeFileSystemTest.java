package test.microstream.afs;

/*-
 * #%L
 * EclipseStore Integration Tests - AFS - commons
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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import test.microstream.library.TypeEnum;

public abstract class AbstractTypeFileSystemTest {

    EmbeddedStorageManager storage;

    @ParameterizedTest
    @EnumSource(TypeEnum.class)
    public void saveAndLoadTest(final TypeEnum type) {
        System.out.println(type.getOriginal().getClass().getSimpleName());

        Object o = this.saveToMSAndReload(type.getOriginal(), type.createEmptyInstance());

        type.getOriginal().proveResults(o);

        this.storageShutdown();
    }

    public void storageShutdown() {
        this.storage.shutdown();
    }

    public abstract EmbeddedStorageManager startStorage(Object root);

    public Object saveToMSAndReload(final Object original, final Object loaded) {

        this.storage = this.startStorage(original);
        this.storage.storeRoot();
        this.storage.shutdown();

        this.storage = this.startStorage(loaded);
        return loaded;
    }
}
