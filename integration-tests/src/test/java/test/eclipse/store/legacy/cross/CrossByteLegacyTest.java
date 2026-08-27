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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;

import test.eclipse.store.legacy.legacy.cross.data.ByteLegacy;
import test.eclipse.store.legacy.legacy.cross.data.ByteLegacy2;

class CrossByteLegacyTest extends AbstractLegacyTest
{

    private String oldClass = classPackage + ".ByteLegacy";
    private String newClass = classPackage + ".ByteLegacy2";

    @Test
    void crossByteLegacyTest()
    {

        ByteLegacy byteLegacy = ByteLegacy.fillSample();

        EmbeddedStorageManager storage = EmbeddedStorage.start(byteLegacy, location);
        storage.shutdown();

        ByteLegacy2 byteLegacy2 = new ByteLegacy2();
        storage = startStorage(byteLegacy2, oldClass, newClass);
        storage.store(byteLegacy2);
        assertEquals(byteLegacy.getByteTo_double(), byteLegacy2.getByteTo_double());
        storage.shutdown();

        byteLegacy = new ByteLegacy();
        storage = startStorage(byteLegacy, newClass, oldClass);
        storage.store(byteLegacy);
        assertEquals(byteLegacy.getByteTo_double(), byteLegacy2.getByteTo_double());
        storage.shutdown();


    }
}
