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

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;

import test.eclipse.store.legacy.legacy.cross.data.BooleanLegacy;
import test.eclipse.store.legacy.legacy.cross.data.BooleanLegacy2;

class CrossBooleanLegacyTest extends AbstractLegacyTest
{

    private String oldClass = classPackage + ".BooleanLegacy";
    private String newClass = classPackage + ".BooleanLegacy2";

    @Test
    void crossBooleanLegacyTest()
    {

        BooleanLegacy booleanLegacy = BooleanLegacy.fillSample();
        EmbeddedStorageManager storage = EmbeddedStorage.start(booleanLegacy, location);
        storage.shutdown();

        BooleanLegacy2 booleanLegacy2 = new BooleanLegacy2();
        storage = startStorage(booleanLegacy2, oldClass, newClass);
        storage.store(booleanLegacy2);
        assertTrue(booleanLegacy2.getTo_double() > 0);
        storage.shutdown();

        booleanLegacy = new BooleanLegacy();
        storage = startStorage(booleanLegacy, newClass, oldClass);
        storage.store(booleanLegacy);
        assertTrue(booleanLegacy.isTo_double());
        storage.shutdown();


    }
}
