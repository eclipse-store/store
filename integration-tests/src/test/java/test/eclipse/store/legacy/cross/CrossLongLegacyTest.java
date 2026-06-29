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

import test.eclipse.store.legacy.legacy.cross.data.LongLegacy;
import test.eclipse.store.legacy.legacy.cross.data.LongLegacy2;

class CrossLongLegacyTest extends AbstractLegacyTest
{

    private String oldClass = classPackage + ".LongLegacy";
    private String newClass = classPackage + ".LongLegacy2";

    @Test
    void crossLongLegacyTest()
    {

        LongLegacy longLegacy = LongLegacy.fillSample();
        EmbeddedStorageManager storage = EmbeddedStorage.start(longLegacy, location);
        storage.shutdown();

        LongLegacy2 longLegacy2 = new LongLegacy2();
        storage = startStorage(longLegacy2, oldClass, newClass);
        storage.store(longLegacy2);
        assertTrue(longLegacy2.getTo_double() > 0);
        storage.shutdown();

        longLegacy = new LongLegacy();
        storage = startStorage(longLegacy, newClass, oldClass);
        storage.store(longLegacy);
        assertTrue(longLegacy.getTo_double() > 0);
        storage.shutdown();

    }
}
