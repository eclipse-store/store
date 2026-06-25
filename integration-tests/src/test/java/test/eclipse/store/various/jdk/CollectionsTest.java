package test.eclipse.store.various.jdk;

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

import java.nio.file.Path;
import java.util.*;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class CollectionsTest
{
    @TempDir
    Path tempDir;

    @Test
    void shouldStoreAndReloadArrayList()
    {
        List<String> list = new ArrayList<>();
        list.add("one");
        list.add("two");
        list.add(null);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(list, tempDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(tempDir)) {
            @SuppressWarnings("unchecked")
            List<String> loaded = (List<String>) storageManager.root();

            assertEquals(list, loaded);
        }
    }

    @Test
    void shouldStoreAndReloadLinkedList()
    {
        LinkedList<Integer> list = new LinkedList<>();
        list.add(1);
        list.add(2);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(list, tempDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(tempDir)) {
            @SuppressWarnings("unchecked")
            LinkedList<Integer> loaded = (LinkedList<Integer>) storageManager.root();

            assertEquals(list, loaded);
        }
    }

    @Test
    void shouldStoreAndReloadMapsAndSets()
    {
        Map<String, Integer> map = new HashMap<>();
        map.put("a", 1);
        map.put("b", 2);

        TreeMap<String, Integer> tree = new TreeMap<>();
        tree.put("z", 26);
        tree.put("a", 1);

        Set<String> set = new HashSet<>();
        set.add("x");
        set.add("y");

        ComplexCollections root = new ComplexCollections(map, tree, set);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(tempDir)) {
            ComplexCollections loaded = (ComplexCollections) storageManager.root();

            assertEquals(map, loaded.getMap());
            assertEquals(tree, loaded.getTree());
            assertEquals(set, loaded.getSet());
        }
    }

    private static class ComplexCollections
    {
        private Map<String, Integer> map;
        private TreeMap<String, Integer> tree;
        private Set<String> set;

        public ComplexCollections(Map<String, Integer> map, TreeMap<String, Integer> tree, Set<String> set)
        {
            this.map = map;
            this.tree = tree;
            this.set = set;
        }

        public ComplexCollections()
        {
        }

        public Map<String, Integer> getMap()
        {
            return map;
        }

        public TreeMap<String, Integer> getTree()
        {
            return tree;
        }

        public Set<String> getSet()
        {
            return set;
        }

        public void setMap(Map<String, Integer> map)
        {
            this.map = map;
        }

        public void setTree(TreeMap<String, Integer> tree)
        {
            this.tree = tree;
        }

        public void setSet(Set<String> set)
        {
            this.set = set;
        }
    }
}
