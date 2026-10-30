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

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;

import org.eclipse.serializer.reference.Lazy;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import test.eclipse.store.legacy.legacy.enumeration.data.EnumerationOrig;

/**
 * Tests verifying that enum values survive a round-trip through Eclipse Store
 * when they are held behind a {@link Lazy} reference.
 */
public class EnumBehindLazyTest
{
    // -------------------------------------------------------------------------
    // Single enum constant behind Lazy
    // -------------------------------------------------------------------------

    @Test
    void lazyEnum_FIRST_survives_roundtrip(@TempDir Path tempDir)
    {
        Root root = new Root(Lazy.Reference(EnumerationOrig.FIRST));

        try (EmbeddedStorageManager sm = EmbeddedStorage.start(root, tempDir)) {
            sm.storeRoot();
        }

        try (EmbeddedStorageManager sm = EmbeddedStorage.start(tempDir)) {
            Root loaded = (Root) sm.root();
            assertNotNull(loaded.lazy);
            assertEquals(EnumerationOrig.FIRST, loaded.lazy.get());
        }
    }

    @Test
    void lazyEnum_SECOND_survives_roundtrip(@TempDir Path tempDir)
    {
        Root root = new Root(Lazy.Reference(EnumerationOrig.SECOND));

        try (EmbeddedStorageManager sm = EmbeddedStorage.start(root, tempDir)) {
            sm.storeRoot();
        }

        try (EmbeddedStorageManager sm = EmbeddedStorage.start(tempDir)) {
            Root loaded = (Root) sm.root();
            assertNotNull(loaded.lazy);
            assertEquals(EnumerationOrig.SECOND, loaded.lazy.get());
        }
    }

    // -------------------------------------------------------------------------
    // Lazy holding null
    // -------------------------------------------------------------------------

    @Test
    void lazyEnum_null_survives_roundtrip(@TempDir Path tempDir)
    {
        Root root = new Root(Lazy.Reference(null));

        try (EmbeddedStorageManager sm = EmbeddedStorage.start(root, tempDir)) {
            sm.storeRoot();
        }

        try (EmbeddedStorageManager sm = EmbeddedStorage.start(tempDir)) {
            Root loaded = (Root) sm.root();
            assertNotNull(loaded.lazy);
            assertNull(loaded.lazy.get());
        }
    }

    // -------------------------------------------------------------------------
    // Lazy is unloaded (cleared) before re-loading from storage
    // -------------------------------------------------------------------------

    @Test
    void lazyEnum_cleared_then_reloaded_from_storage(@TempDir Path tempDir)
    {
        Root root = new Root(Lazy.Reference(EnumerationOrig.FIRST));

        try (EmbeddedStorageManager sm = EmbeddedStorage.start(root, tempDir)) {
            sm.storeRoot();
            // Clear the Lazy reference so that the value must be fetched from disk.
            root.lazy.clear();
            assertEquals(EnumerationOrig.FIRST, root.lazy.get());
        }
    }

    // -------------------------------------------------------------------------
    // Enum value changed in memory then re-stored
    // -------------------------------------------------------------------------

    @Test
    void lazyEnum_value_updated_and_persisted(@TempDir Path tempDir)
    {
        Root root = new Root(Lazy.Reference(EnumerationOrig.FIRST));

        try (EmbeddedStorageManager sm = EmbeddedStorage.start(root, tempDir)) {
            sm.storeRoot();
        }

        // Change to SECOND, re-store, then verify reload.
        try (EmbeddedStorageManager sm = EmbeddedStorage.start(tempDir)) {
            Root loaded = (Root) sm.root();
            loaded.lazy = Lazy.Reference(EnumerationOrig.SECOND);
            sm.store(loaded);
        }

        try (EmbeddedStorageManager sm = EmbeddedStorage.start(tempDir)) {
            Root loaded = (Root) sm.root();
            assertEquals(EnumerationOrig.SECOND, loaded.lazy.get());
        }
    }

    // -------------------------------------------------------------------------
    // Root holding two Lazy enum references
    // -------------------------------------------------------------------------

    @Test
    void lazyEnum_two_lazy_refs_survive_roundtrip(@TempDir Path tempDir)
    {
        TwoLazyRoot root = new TwoLazyRoot(
                Lazy.Reference(EnumerationOrig.FIRST),
                Lazy.Reference(EnumerationOrig.SECOND)
        );

        try (EmbeddedStorageManager sm = EmbeddedStorage.start(root, tempDir)) {
            sm.storeRoot();
        }

        try (EmbeddedStorageManager sm = EmbeddedStorage.start(tempDir)) {
            TwoLazyRoot loaded = (TwoLazyRoot) sm.root();
            assertEquals(EnumerationOrig.FIRST, loaded.first.get());
            assertEquals(EnumerationOrig.SECOND, loaded.second.get());
        }
    }

    // -------------------------------------------------------------------------
    // Enum field inside an object that itself is behind Lazy
    // -------------------------------------------------------------------------

    @Test
    void lazyWrappedObject_containing_enum_survives_roundtrip(@TempDir Path tempDir)
    {
        Wrapper wrapper = new Wrapper(EnumerationOrig.FIRST);
        WrapperRoot root = new WrapperRoot(Lazy.Reference(wrapper));

        try (EmbeddedStorageManager sm = EmbeddedStorage.start(root, tempDir)) {
            sm.storeRoot();
        }

        try (EmbeddedStorageManager sm = EmbeddedStorage.start(tempDir)) {
            WrapperRoot loaded = (WrapperRoot) sm.root();
            assertNotNull(loaded.lazy);
            Wrapper w = loaded.lazy.get();
            assertNotNull(w);
            assertEquals(EnumerationOrig.FIRST, w.value);
        }
    }

    @Test
    void lazyWrappedObject_containing_both_enum_constants(@TempDir Path tempDir)
    {
        Wrapper w1 = new Wrapper(EnumerationOrig.FIRST);
        Wrapper w2 = new Wrapper(EnumerationOrig.SECOND);
        WrapperRoot root = new WrapperRoot(Lazy.Reference(w1));

        try (EmbeddedStorageManager sm = EmbeddedStorage.start(root, tempDir)) {
            sm.storeRoot();
        }

        // Overwrite with SECOND and re-persist
        try (EmbeddedStorageManager sm = EmbeddedStorage.start(tempDir)) {
            WrapperRoot loaded = (WrapperRoot) sm.root();
            loaded.lazy = Lazy.Reference(w2);
            sm.store(loaded);
        }

        try (EmbeddedStorageManager sm = EmbeddedStorage.start(tempDir)) {
            WrapperRoot loaded = (WrapperRoot) sm.root();
            assertEquals(EnumerationOrig.SECOND, loaded.lazy.get().value);
        }
    }

    // -------------------------------------------------------------------------
    // Enum field metadata preserved after round-trip
    // -------------------------------------------------------------------------

    @Test
    void lazyEnum_name_and_ordinal_preserved(@TempDir Path tempDir)
    {
        Root root = new Root(Lazy.Reference(EnumerationOrig.SECOND));

        try (EmbeddedStorageManager sm = EmbeddedStorage.start(root, tempDir)) {
            sm.storeRoot();
        }

        try (EmbeddedStorageManager sm = EmbeddedStorage.start(tempDir)) {
            Root loaded = (Root) sm.root();
            EnumerationOrig reloaded = loaded.lazy.get();
            assertEquals(EnumerationOrig.SECOND.name(), reloaded.name());
            assertEquals(EnumerationOrig.SECOND.ordinal(), reloaded.ordinal());
            assertEquals(EnumerationOrig.SECOND.getName(), reloaded.getName());
            assertEquals(EnumerationOrig.SECOND.getValue(), reloaded.getValue());
            assertEquals(EnumerationOrig.SECOND.getSecondValue(), reloaded.getSecondValue());
        }
    }

    // -------------------------------------------------------------------------
    // Helper data classes
    // -------------------------------------------------------------------------

    static class Root
    {
        Lazy<EnumerationOrig> lazy;

        Root(Lazy<EnumerationOrig> lazy)
        {
            this.lazy = lazy;
        }
    }

    static class TwoLazyRoot
    {
        Lazy<EnumerationOrig> first;
        Lazy<EnumerationOrig> second;

        TwoLazyRoot(Lazy<EnumerationOrig> first, Lazy<EnumerationOrig> second)
        {
            this.first = first;
            this.second = second;
        }
    }

    static class Wrapper
    {
        EnumerationOrig value;

        Wrapper(EnumerationOrig value)
        {
            this.value = value;
        }
    }

    static class WrapperRoot
    {
        Lazy<Wrapper> lazy;

        WrapperRoot(Lazy<Wrapper> lazy)
        {
            this.lazy = lazy;
        }
    }
}

