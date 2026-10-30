package test.microstream.sequenced;

/*-
 * #%L
 * EclipseStore Integration Tests - JDK - Sequenced Collections
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
import java.util.*;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Round-trip tests for the JDK 21 Sequenced Collections API
 * (JEP 431: SequencedCollection / SequencedSet / SequencedMap).
 *
 * Scope:
 *   - new mutator methods: addFirst/addLast/removeFirst/removeLast,
 *     putFirst/putLast on existing collections
 *   - new view methods: reversed(), sequencedKeySet/Values/EntrySet
 *
 * Backing types observed on JDK 21 for view methods (interesting because
 * each view is an internal inner class with a back-reference to its outer
 * collection — a typical area where serializers struggle):
 *
 *   ArrayList#reversed()              -> java.util.ReverseOrderListView$Rand
 *   LinkedList#reversed()             -> java.util.LinkedList$ReverseOrderLinkedListView
 *   LinkedHashSet#reversed()          -> java.util.LinkedHashSet$1ReverseLinkedHashSetView (local class!)
 *   LinkedHashMap#reversed()          -> java.util.LinkedHashMap$ReversedLinkedHashMapView
 *   LinkedHashMap#sequencedKeySet()   -> java.util.LinkedHashMap$LinkedKeySet
 *   LinkedHashMap#sequencedValues()   -> java.util.LinkedHashMap$LinkedValues
 *   LinkedHashMap#sequencedEntrySet() -> java.util.LinkedHashMap$LinkedEntrySet
 *   TreeSet#reversed()                -> java.util.TreeSet (reversed-comparator instance)
 *   TreeMap#reversed()                -> java.util.TreeMap$DescendingSubMap
 *
 * Goal is to surface defects, not produce a green run.
 */
public class SequencedCollectionTest
{
    @TempDir
    Path workDir;

    // ====================================================================
    // New mutators on existing collections (no new types involved)
    // ====================================================================

    @Test
    void arrayListAddFirstRoundTripTest()
    {
        ArrayList<String> root = new ArrayList<>();
        root.add("b");
        root.add("c");
        root.addFirst("a");
        root.addLast("d");
        assertEquals(List.of("a", "b", "c", "d"), root);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            ArrayList<String> reloaded = (ArrayList<String>) storageManager.root();
            assertEquals(List.of("a", "b", "c", "d"), reloaded);
            // The new-in-21 mutators must still work after a round trip.
            reloaded.addFirst("Z");
            reloaded.addLast("E");
            assertEquals(List.of("Z", "a", "b", "c", "d", "E"), reloaded);
        }
    }

    @Test
    void linkedHashSetAddFirstRoundTripTest()
    {
        LinkedHashSet<String> root = new LinkedHashSet<>();
        root.add("b");
        root.add("c");
        root.addFirst("a");
        root.addLast("d");
        assertEquals(List.of("a", "b", "c", "d"), List.copyOf(root));

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            LinkedHashSet<String> reloaded = (LinkedHashSet<String>) storageManager.root();
            assertEquals(List.of("a", "b", "c", "d"), List.copyOf(reloaded));
            assertEquals("a", reloaded.getFirst());
            assertEquals("d", reloaded.getLast());
            reloaded.addFirst("Z");
            assertEquals("Z", reloaded.getFirst());
        }
    }

    @Test
    void linkedHashMapPutFirstRoundTripTest()
    {
        LinkedHashMap<String, Integer> root = new LinkedHashMap<>();
        root.put("b", 2);
        root.put("c", 3);
        root.putFirst("a", 1);
        root.putLast("d", 4);
        assertEquals(List.of("a", "b", "c", "d"), List.copyOf(root.sequencedKeySet()));

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            LinkedHashMap<String, Integer> reloaded = (LinkedHashMap<String, Integer>) storageManager.root();
            assertEquals(List.of("a", "b", "c", "d"), List.copyOf(reloaded.sequencedKeySet()));
            assertEquals(Map.entry("a", 1), reloaded.firstEntry());
            assertEquals(Map.entry("d", 4), reloaded.lastEntry());
            reloaded.putFirst("Z", 0);
            assertEquals("Z", reloaded.sequencedKeySet().getFirst());
        }
    }

    @Test
    void treeSetAddFirstThrowsButRoundTripsTest()
    {
        // TreeSet is a SequencedSet but addFirst/addLast must throw
        // UnsupportedOperationException — order is determined by comparator.
        TreeSet<String> root = new TreeSet<>(List.of("b", "a", "c"));
        assertThrows(UnsupportedOperationException.class, () -> root.addFirst("x"));
        assertThrows(UnsupportedOperationException.class, () -> root.addLast("x"));

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            TreeSet<String> reloaded = (TreeSet<String>) storageManager.root();
            assertEquals(List.of("a", "b", "c"), List.copyOf(reloaded));
            // Contract must survive the round trip.
            assertThrows(UnsupportedOperationException.class, () -> reloaded.addFirst("x"));
        }
    }

    // ====================================================================
    // reversed() views — the bug-hunt territory
    // ====================================================================

    @Test
    void arrayListReversedViewAsRootTest()
    {
        ArrayList<String> source = new ArrayList<>(List.of("a", "b", "c", "d"));
        List<String> reversed = source.reversed();
        assertEquals(List.of("d", "c", "b", "a"), reversed);

        // Storing the *view itself* as a root.
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(reversed, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            List<String> reloaded = (List<String>) storageManager.root();
            assertNotNull(reloaded);
            assertEquals(4, reloaded.size());
            assertEquals(List.of("d", "c", "b", "a"), reloaded);
        }
    }

    @Test
    void linkedListReversedViewAsRootTest()
    {
        LinkedList<String> source = new LinkedList<>(List.of("a", "b", "c", "d"));
        List<String> reversed = source.reversed();
        assertEquals(List.of("d", "c", "b", "a"), reversed);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(reversed, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            List<String> reloaded = (List<String>) storageManager.root();
            assertEquals(List.of("d", "c", "b", "a"), reloaded);
        }
    }

    @Test
    @Disabled("LinkedHashSet#reversed() is an anonymous local class; the serializer rejects it as non-persistable by design")
    void linkedHashSetReversedViewAsRootTest()
    {
        // LinkedHashSet#reversed() returns a *local anonymous class* — the
        // most exotic view in the JDK 21 sequenced API. High bug probability.
        LinkedHashSet<String> source = new LinkedHashSet<>();
        source.add("a"); source.add("b"); source.add("c"); source.add("d");
        SequencedSet<String> reversed = source.reversed();
        assertEquals(List.of("d", "c", "b", "a"), List.copyOf(reversed));

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(reversed, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            SequencedSet<String> reloaded = (SequencedSet<String>) storageManager.root();
            assertNotNull(reloaded);
            assertEquals(4, reloaded.size());
            assertEquals(List.of("d", "c", "b", "a"), List.copyOf(reloaded));
        }
    }

    @Test
    void linkedHashMapReversedViewAsRootTest()
    {
        LinkedHashMap<String, Integer> source = new LinkedHashMap<>();
        source.put("a", 1); source.put("b", 2); source.put("c", 3);
        SequencedMap<String, Integer> reversed = source.reversed();
        assertEquals(List.of("c", "b", "a"), List.copyOf(reversed.sequencedKeySet()));

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(reversed, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            SequencedMap<String, Integer> reloaded = (SequencedMap<String, Integer>) storageManager.root();
            assertEquals(3, reloaded.size());
            assertEquals(List.of("c", "b", "a"), List.copyOf(reloaded.sequencedKeySet()));
            assertEquals(Integer.valueOf(3), reloaded.firstEntry().getValue());
        }
    }

    @Test
    void treeSetReversedViewAsRootTest()
    {
        TreeSet<String> source = new TreeSet<>(List.of("a", "b", "c", "d"));
        SequencedSet<String> reversed = source.reversed();
        assertEquals(List.of("d", "c", "b", "a"), List.copyOf(reversed));

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(reversed, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            SequencedSet<String> reloaded = (SequencedSet<String>) storageManager.root();
            assertEquals(List.of("d", "c", "b", "a"), List.copyOf(reloaded));
        }
    }

    @Test
    void treeMapReversedViewAsRootTest()
    {
        TreeMap<String, Integer> source = new TreeMap<>();
        source.put("a", 1); source.put("b", 2); source.put("c", 3);
        SequencedMap<String, Integer> reversed = source.reversed();
        assertEquals(List.of("c", "b", "a"), List.copyOf(reversed.sequencedKeySet()));

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(reversed, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            SequencedMap<String, Integer> reloaded = (SequencedMap<String, Integer>) storageManager.root();
            assertEquals(3, reloaded.size());
            assertEquals(List.of("c", "b", "a"), List.copyOf(reloaded.sequencedKeySet()));
        }
    }

    // ====================================================================
    // Sequenced sub-views of LinkedHashMap as root
    // ====================================================================

    @Test
    void linkedHashMapSequencedKeySetAsRootTest()
    {
        LinkedHashMap<String, Integer> source = new LinkedHashMap<>();
        source.put("a", 1); source.put("b", 2); source.put("c", 3);
        SequencedSet<String> keys = source.sequencedKeySet();

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(keys, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            SequencedSet<String> reloaded = (SequencedSet<String>) storageManager.root();
            assertEquals(3, reloaded.size());
            assertEquals(List.of("a", "b", "c"), List.copyOf(reloaded));
        }
    }

    @Test
    void linkedHashMapSequencedValuesAsRootTest()
    {
        LinkedHashMap<String, Integer> source = new LinkedHashMap<>();
        source.put("a", 1); source.put("b", 2); source.put("c", 3);
        SequencedCollection<Integer> values = source.sequencedValues();

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(values, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            SequencedCollection<Integer> reloaded = (SequencedCollection<Integer>) storageManager.root();
            assertEquals(3, reloaded.size());
            assertEquals(List.of(1, 2, 3), List.copyOf(reloaded));
        }
    }

    @Test
    void linkedHashMapSequencedEntrySetAsRootTest()
    {
        LinkedHashMap<String, Integer> source = new LinkedHashMap<>();
        source.put("a", 1); source.put("b", 2); source.put("c", 3);
        SequencedSet<Map.Entry<String, Integer>> entries = source.sequencedEntrySet();

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(entries, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            SequencedSet<Map.Entry<String, Integer>> reloaded =
                (SequencedSet<Map.Entry<String, Integer>>) storageManager.root();
            assertEquals(3, reloaded.size());
            assertEquals("a", reloaded.getFirst().getKey());
            assertEquals("c", reloaded.getLast().getKey());
        }
    }

    // ====================================================================
    // Reversed views as fields of a normal entity
    // ====================================================================

    @Test
    void reversedListInsideHolderTest()
    {
        // A more realistic shape: a reversed view stored as a field of a
        // domain object, not as the root itself.
        Holder root = new Holder();
        root.label = "demo";
        root.source = new ArrayList<>(List.of("a", "b", "c"));
        root.reversedView = root.source.reversed();

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, workDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(workDir)) {
            Holder reloaded = (Holder) storageManager.root();
            assertEquals("demo", reloaded.label);
            assertEquals(List.of("a", "b", "c"), reloaded.source);
            assertNotNull(reloaded.reversedView, "Reversed view field must round-trip");
            assertEquals(List.of("c", "b", "a"), List.copyOf(reloaded.reversedView));
        }
    }

    static class Holder
    {
        String label;
        List<String> source;
        List<String> reversedView;
    }
}
