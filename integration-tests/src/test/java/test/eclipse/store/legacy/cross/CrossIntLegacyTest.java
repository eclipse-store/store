package test.eclipse.store.legacy.cross;

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

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import test.eclipse.store.legacy.legacy.cross.data.IntLegacy;
import test.eclipse.store.legacy.legacy.cross.data.IntLegacy2;

class CrossIntLegacyTest extends AbstractLegacyTest {

    private String oldClass = classPackage + ".IntLegacy";
    private String newClass = classPackage + ".IntLegacy2";

    @Test
    void crossIntLegacyTest() {

        IntLegacy intLegacy = IntLegacy.fillSample();

        EmbeddedStorageManager storage = EmbeddedStorage.start(intLegacy, location);
        storage.shutdown();

        IntLegacy2 intLegacy2 = new IntLegacy2();
        storage = startStorage(intLegacy2, oldClass, newClass);
        storage.store(intLegacy2);
        assertTrue(intLegacy2.getTo_double() > 0);
        storage.shutdown();

        intLegacy = new IntLegacy();
        storage = startStorage(intLegacy, newClass, oldClass);
        storage.store(intLegacy);
        assertTrue(intLegacy.getTo_double() > 0);
        storage.shutdown();
    }
}
