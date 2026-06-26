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

import test.eclipse.store.legacy.legacy.cross.data.ShortLegacy;
import test.eclipse.store.legacy.legacy.cross.data.ShortLegacy2;

class CrossShortLegacyTest extends AbstractLegacyTest
{

    private String oldClass = classPackage + ".ShortLegacy";
    private String newClass = classPackage + ".ShortLegacy2";

    @Test
    void crossShortLegacyTest()
    {

        ShortLegacy shortLegacy = ShortLegacy.fillSample();
        EmbeddedStorageManager storage = EmbeddedStorage.start(shortLegacy, location);
        storage.shutdown();

        ShortLegacy2 shortLegacy2 = new ShortLegacy2();
        storage = startStorage(shortLegacy2, oldClass, newClass);
        storage.store(shortLegacy2);
        assertTrue(shortLegacy2.getTo_double() > 0);
        storage.shutdown();

        shortLegacy = new ShortLegacy();
        storage = startStorage(shortLegacy, newClass, oldClass);
        storage.store(shortLegacy);
        assertTrue(shortLegacy.getTo_double() > 0);
        storage.shutdown();

    }
}
