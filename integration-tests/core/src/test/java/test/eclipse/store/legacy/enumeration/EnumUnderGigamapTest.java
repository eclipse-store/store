package test.eclipse.store.legacy.enumeration;

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
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Path;

import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import test.eclipse.store.legacy.legacy.enumeration.data.EnumerationOrig;

public class EnumUnderGigamapTest
{
    // -------------------------------------------------------------------------
    // Basic: both enum constants stored and reloaded
    // -------------------------------------------------------------------------

    @Test
    void gigaMap_stores_and_reloads_both_enum_constants(@TempDir Path tempDir)
    {
        GigaMap<EnumerationOrig> gigaMap = GigaMap.New();
        gigaMap.add(EnumerationOrig.FIRST);
        gigaMap.add(EnumerationOrig.SECOND);

        try (EmbeddedStorageManager sm = EmbeddedStorage.start(gigaMap, tempDir)) {
            // persist implicitly via GigaMap's own storage
        }

        GigaMap<EnumerationOrig> reloaded = GigaMap.New();
        try (EmbeddedStorageManager sm = EmbeddedStorage.start(reloaded, tempDir)) {
            assertEquals(2, reloaded.size());
            assertEquals(EnumerationOrig.FIRST, reloaded.get(0));
            assertEquals(EnumerationOrig.SECOND, reloaded.get(1));
        }
    }

    // -------------------------------------------------------------------------
    // Single element: only FIRST
    // -------------------------------------------------------------------------

    @Test
    void gigaMap_single_element_FIRST(@TempDir Path tempDir)
    {
        GigaMap<EnumerationOrig> gigaMap = GigaMap.New();
        gigaMap.add(EnumerationOrig.FIRST);

        try (EmbeddedStorageManager sm = EmbeddedStorage.start(gigaMap, tempDir)) {
        }

        GigaMap<EnumerationOrig> reloaded = GigaMap.New();
        try (EmbeddedStorageManager sm = EmbeddedStorage.start(reloaded, tempDir)) {
            assertEquals(1, reloaded.size());
            assertEquals(EnumerationOrig.FIRST, reloaded.get(0));
        }
    }

    // -------------------------------------------------------------------------
    // Single element: only SECOND
    // -------------------------------------------------------------------------

    @Test
    void gigaMap_single_element_SECOND(@TempDir Path tempDir)
    {
        GigaMap<EnumerationOrig> gigaMap = GigaMap.New();
        gigaMap.add(EnumerationOrig.SECOND);

        try (EmbeddedStorageManager sm = EmbeddedStorage.start(gigaMap, tempDir)) {
        }

        GigaMap<EnumerationOrig> reloaded = GigaMap.New();
        try (EmbeddedStorageManager sm = EmbeddedStorage.start(reloaded, tempDir)) {
            assertEquals(1, reloaded.size());
            assertEquals(EnumerationOrig.SECOND, reloaded.get(0));
        }
    }

    // -------------------------------------------------------------------------
    // Enum metadata (name, ordinal, domain fields) preserved after reload
    // -------------------------------------------------------------------------

    @Test
    void gigaMap_enum_metadata_preserved_after_reload(@TempDir Path tempDir)
    {
        GigaMap<EnumerationOrig> gigaMap = GigaMap.New();
        gigaMap.add(EnumerationOrig.FIRST);
        gigaMap.add(EnumerationOrig.SECOND);

        try (EmbeddedStorageManager sm = EmbeddedStorage.start(gigaMap, tempDir)) {
        }

        GigaMap<EnumerationOrig> reloaded = GigaMap.New();
        try (EmbeddedStorageManager sm = EmbeddedStorage.start(reloaded, tempDir)) {
            EnumerationOrig first = reloaded.get(0);
            EnumerationOrig second = reloaded.get(1);

            assertEquals(EnumerationOrig.FIRST.name(), first.name());
            assertEquals(EnumerationOrig.FIRST.ordinal(), first.ordinal());
            assertEquals(EnumerationOrig.FIRST.getName(), first.getName());
            assertEquals(EnumerationOrig.FIRST.getValue(), first.getValue());
            assertEquals(EnumerationOrig.FIRST.getSecondValue(), first.getSecondValue());

            assertEquals(EnumerationOrig.SECOND.name(), second.name());
            assertEquals(EnumerationOrig.SECOND.ordinal(), second.ordinal());
            assertEquals(EnumerationOrig.SECOND.getName(), second.getName());
            assertEquals(EnumerationOrig.SECOND.getValue(), second.getValue());
            assertEquals(EnumerationOrig.SECOND.getSecondValue(), second.getSecondValue());
        }
    }

    // -------------------------------------------------------------------------
    // Multiple reload cycles
    // -------------------------------------------------------------------------

    @Test
    void gigaMap_survives_multiple_reload_cycles(@TempDir Path tempDir)
    {
        GigaMap<EnumerationOrig> gigaMap = GigaMap.New();
        gigaMap.add(EnumerationOrig.FIRST);
        gigaMap.add(EnumerationOrig.SECOND);

        try (EmbeddedStorageManager sm = EmbeddedStorage.start(gigaMap, tempDir)) {
        }

        for (int cycle = 0; cycle < 3; cycle++) {
            GigaMap<EnumerationOrig> reloaded = GigaMap.New();
            try (EmbeddedStorageManager sm = EmbeddedStorage.start(reloaded, tempDir)) {
                assertEquals(2, reloaded.size(), "cycle " + cycle);
                assertEquals(EnumerationOrig.FIRST, reloaded.get(0));
                assertEquals(EnumerationOrig.SECOND, reloaded.get(1));
            }
        }
    }

    // -------------------------------------------------------------------------
    // Enum identity: reloaded constant is the same JVM object
    // -------------------------------------------------------------------------

    @Test
    void gigaMap_reloaded_enum_is_same_jvm_instance(@TempDir Path tempDir)
    {
        GigaMap<EnumerationOrig> gigaMap = GigaMap.New();
        gigaMap.add(EnumerationOrig.FIRST);

        try (EmbeddedStorageManager sm = EmbeddedStorage.start(gigaMap, tempDir)) {
        }

        GigaMap<EnumerationOrig> reloaded = GigaMap.New();
        try (EmbeddedStorageManager sm = EmbeddedStorage.start(reloaded, tempDir)) {
            // Enums are singletons; the reloaded reference must be identical.
            assertNotNull(reloaded.get(0));
            assertEquals(EnumerationOrig.FIRST, reloaded.get(0));
        }
    }

    // -------------------------------------------------------------------------
    // GigaMap containing a wrapper object that holds an enum field
    // -------------------------------------------------------------------------

    @Test
    void gigaMap_wrapper_with_enum_field_survives_roundtrip(@TempDir Path tempDir)
    {
        GigaMap<EnumHolder> gigaMap = GigaMap.New();
        gigaMap.add(new EnumHolder(EnumerationOrig.FIRST));
        gigaMap.add(new EnumHolder(EnumerationOrig.SECOND));

        try (EmbeddedStorageManager sm = EmbeddedStorage.start(gigaMap, tempDir)) {
        }

        GigaMap<EnumHolder> reloaded = GigaMap.New();
        try (EmbeddedStorageManager sm = EmbeddedStorage.start(reloaded, tempDir)) {
            assertEquals(2, reloaded.size());
            assertEquals(EnumerationOrig.FIRST, reloaded.get(0).value);
            assertEquals(EnumerationOrig.SECOND, reloaded.get(1).value);
        }
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    static class EnumHolder
    {
        EnumerationOrig value;

        EnumHolder(EnumerationOrig value)
        {
            this.value = value;
        }
    }
}
