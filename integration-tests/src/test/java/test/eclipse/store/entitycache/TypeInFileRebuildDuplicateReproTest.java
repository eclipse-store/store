package test.eclipse.store.entitycache;

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
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.types.Storage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Reproducer for the {@code StorageLiveDataFile.rebuildTypeInFileTable} hash
 * key mismatch: the per-file type-in-file registry is keyed by
 * {@code System.identityHashCode(type)} on lookup and creation
 * (StorageLiveDataFile.java:218, :234), but the rebuild re-hashes entries by
 * {@code System.identityHashCode(entries)} — the identity of the
 * {@code TypeInFile} entry itself, not of its type (StorageLiveDataFile.java:251-252).
 *
 * <p>After the first rebuild (triggered once a single data file holds more
 * distinct types than the table range — initial length is 8) every existing
 * entry sits in a wrong bucket: subsequent lookups of those types miss and
 * {@code createTypeInFile} registers a <b>duplicate</b> {@code TypeInFile}
 * for the same (type, file) pair. The stale entries are unreachable forever
 * (there is no removal), the inflated count triggers further — again wrongly
 * keyed — rebuilds, and the hot {@code typeInFile()} lookup path degrades.
 *
 * <p>Unlike the sibling defect in {@code StorageEntityCache.putEntity}
 * (internal#77), this one is contained: {@code TypeInFile} is a pure
 * (type, file) flyweight, lookups verify {@code t.type == type}, and nothing
 * iterates the table — so the impact is memory/CPU degradation, not data
 * corruption. The fix is to re-hash by {@code entries.type}.
 *
 * <p><b>This test asserts the registry invariant — at most one
 * {@code TypeInFile} per type per file — and therefore FAILS while the defect
 * exists.</b> It inspects the live channel structures via reflection
 * (read-only, after a synchronous storage task has drained the channel).
 */
public class TypeInFileRebuildDuplicateReproTest
{
    @TempDir
    Path tempDir;

    EmbeddedStorageManager storage;

    @AfterEach
    public void afterTest()
    {
        if (this.storage != null && this.storage.isRunning()) {
            try {
                this.storage.shutdown();
            } catch (final Exception ignored) { /* best effort */ }
        }
    }

    @Test
    @Timeout(120)
    void typeInFileRegistryMustHoldOneEntryPerTypePerFile() throws Exception
    {
        this.storage = EmbeddedStorage.Foundation(
                        Storage.ConfigurationBuilder()
                                .setChannelCountProvider(Storage.ChannelCountProvider(1))
                                .setStorageFileProvider(Storage.FileProvider(this.tempDir))
                                .createConfiguration()
                )
                .start();

        // Phase A: bring more than 8 distinct entity types into the head file —
        // crossing the initial table range (8) triggers the wrongly-keyed rebuild.
        final DataRoot root = new DataRoot();
        root.items = new ArrayList<>(newInstanceOfEachType());
        this.storage.setRoot(root);
        this.storage.storeRoot();

        // Phase B: store new instances of the SAME types. For every type whose
        // entry was scattered by the rebuild, the lookup misses and a duplicate
        // TypeInFile is created.
        root.items.addAll(newInstanceOfEachType());
        this.storage.store(root.items);

        // Synchronous task as a barrier: all prior channel work is complete.
        this.storage.issueFullGarbageCollection();

        // Walk the live file structures and count TypeInFile entries per type.
        final List<String> duplicates = new ArrayList<>();
        int distinctTypesSeen = 0;

        final Object storageSystem  = field(this.storage, "storageSystem");
        final Object channelKeepers = field(storageSystem, "channelKeepers");
        for (int k = 0; k < Array.getLength(channelKeepers); k++) {
            final Object channel     = field(Array.get(channelKeepers, k), "channel");
            final Object fileManager = field(channel, "fileManager");
            final Object headFile    = field(fileManager, "headFile");
            assertNotNull(headFile, "channel must have a head file");

            Object file = headFile;
            do {
                final Map<Object, Integer> perType = new IdentityHashMap<>();
                final Object slots = field(file, "typeInFileSlots");
                for (int s = 0; s < Array.getLength(slots); s++) {
                    for (Object e = Array.get(slots, s); e != null; e = field(e, "hashNext")) {
                        perType.merge(field(e, "type"), 1, Integer::sum);
                    }
                }
                distinctTypesSeen = Math.max(distinctTypesSeen, perType.size());
                for (final Map.Entry<Object, Integer> t : perType.entrySet()) {
                    if (t.getValue() > 1) {
                        duplicates.add("file=" + file + " typeId=" + field(t.getKey(), "typeId")
                                + " entries=" + t.getValue());
                    }
                }
                file = field(file, "next");
            }
            while (file != headFile);
        }

        // Precondition: the rebuild threshold (initial table range 8) was crossed.
        assumeTrue(distinctTypesSeen > 8,
                "fewer than 9 distinct types in one file — rebuild not triggered, scenario not staged");

        assertTrue(duplicates.isEmpty(),
                "DUPLICATE TypeInFile registrations (same type registered more than once in one file's"
                        + " type-in-file table — rebuild re-hashed by entry identity instead of type identity): "
                        + duplicates);
    }

    private static List<Object> newInstanceOfEachType()
    {
        return List.of(
                new T01(), new T02(), new T03(), new T04(), new T05(), new T06(),
                new T07(), new T08(), new T09(), new T10(), new T11(), new T12()
        );
    }

    private static Object field(final Object instance, final String name) throws ReflectiveOperationException
    {
        for (Class<?> c = instance.getClass(); c != null; c = c.getSuperclass()) {
            try {
                final Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(instance);
            } catch (final NoSuchFieldException e) {
                // try superclass
            }
        }
        throw new NoSuchFieldException(instance.getClass().getName() + "." + name);
    }

    ///////////////////////////////////////////////////////////////////////////
    // data types //
    ////////////////

    public static class DataRoot
    {
        public ArrayList<Object> items;
    }

    // 12 distinct tiny entity types to cross the initial type-in-file table range (8)
    public static class T01 { public int v; }
    public static class T02 { public int v; }
    public static class T03 { public int v; }
    public static class T04 { public int v; }
    public static class T05 { public int v; }
    public static class T06 { public int v; }
    public static class T07 { public int v; }
    public static class T08 { public int v; }
    public static class T09 { public int v; }
    public static class T10 { public int v; }
    public static class T11 { public int v; }
    public static class T12 { public int v; }
}
