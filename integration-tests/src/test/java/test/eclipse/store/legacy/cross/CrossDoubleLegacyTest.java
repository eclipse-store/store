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

import test.eclipse.store.legacy.legacy.cross.data.DoubleLegacy;
import test.eclipse.store.legacy.legacy.cross.data.DoubleLegacy2;

class CrossDoubleLegacyTest extends AbstractLegacyTest
{

    private String oldClass = classPackage + ".DoubleLegacy";
    private String newClass = classPackage + ".DoubleLegacy2";

    @Test
    void crossDoubleLegacyTest()
    {

        DoubleLegacy doubleLegacy = DoubleLegacy.fillSample();
        EmbeddedStorageManager storage = EmbeddedStorage.start(doubleLegacy, location);
        storage.shutdown();

        DoubleLegacy2 doubleLegacy2 = new DoubleLegacy2();
        storage = startStorage(doubleLegacy2, oldClass, newClass);
        storage.store(doubleLegacy2);
        assertTrue(doubleLegacy2.getTo_double() > 0);
        storage.shutdown();

        doubleLegacy = new DoubleLegacy();
        storage = startStorage(doubleLegacy, newClass, oldClass);
        storage.store(doubleLegacy);
        assertTrue(doubleLegacy.getTo_double() > 0);
        storage.shutdown();


    }
}
