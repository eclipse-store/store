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

import test.eclipse.store.legacy.legacy.cross.data.CharLegacy;
import test.eclipse.store.legacy.legacy.cross.data.CharLegacy2;

class CrossCharLegacyTest extends AbstractLegacyTest
{

    private String oldClass = classPackage + ".CharLegacy";
    private String newClass = classPackage + ".CharLegacy2";

    @Test
    void crossCharLegacyTest()
    {

        CharLegacy charLegacy = CharLegacy.fillSample();
        EmbeddedStorageManager storage = EmbeddedStorage.start(charLegacy, location);
        storage.shutdown();

        CharLegacy2 charLegacy2 = new CharLegacy2();
        storage = startStorage(charLegacy2, oldClass, newClass);
        storage.store(charLegacy2);
        assertEquals(charLegacy.getCharTo_double(), charLegacy2.getCharTo_double());
        storage.shutdown();

        charLegacy = new CharLegacy();
        storage = startStorage(charLegacy, newClass, oldClass);
        storage.store(charLegacy);
        assertEquals(charLegacy.getCharTo_double(), charLegacy2.getCharTo_double());
        storage.shutdown();


    }
}
