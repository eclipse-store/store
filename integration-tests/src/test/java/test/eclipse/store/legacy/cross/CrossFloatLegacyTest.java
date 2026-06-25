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
import test.eclipse.store.legacy.legacy.cross.data.FloatLegacy;
import test.eclipse.store.legacy.legacy.cross.data.FloatLegacy2;

class CrossFloatLegacyTest extends AbstractLegacyTest{

    private String oldClass = classPackage + ".FloatLegacy";
    private String newClass = classPackage + ".FloatLegacy2";

    @Test
    void crossFloatLegacyTest() {

        FloatLegacy floatLegacy = FloatLegacy.fillSample();
        EmbeddedStorageManager storage = EmbeddedStorage.start(floatLegacy, location);
        storage.shutdown();

        FloatLegacy2 floatLegacy2 = new FloatLegacy2();
        storage = startStorage(floatLegacy2, oldClass, newClass);
        storage.store(floatLegacy2);
        assertTrue(floatLegacy2.getTo_double() > 0);
        storage.shutdown();

        floatLegacy = new FloatLegacy();
        storage = startStorage(floatLegacy, newClass, oldClass);
        storage.store(floatLegacy);
        assertTrue(floatLegacy.getTo_double() > 0);
        storage.shutdown();


    }
}
