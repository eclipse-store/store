package test.eclipse.store.handler.special.comparator;

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

import java.nio.file.Path;
import java.util.*;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class BinaryHandlerPriorityQueueComparatorTest
{
    
    @TempDir
    Path tempDir;

    @Test
    void binaryHandlerPriorityQueue_IntegerComparatorTest()
    {
        PriorityQueue<Integer> queue = new PriorityQueue<>(Comparator.reverseOrder());
        queue.add(5);
        queue.add(1);
        queue.add(3);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(queue, tempDir)) {
            // The queue is stored
        }

        PriorityQueue<Integer> root = new PriorityQueue<>();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {

            Assertions.assertNotNull(root.comparator(), "The default comparator should be exists.");

            root.add(2);
            storageManager.storeRoot();

            while (!root.isEmpty()) { //Remove all from queue, but not save it
                root.poll();
            }
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(tempDir)) {
            final PriorityQueue<Integer> rootLoaded = (PriorityQueue<Integer>) storageManager.root();

            Assertions.assertNotNull(rootLoaded.comparator(), "The default comparator should be exists.");

            List<Integer> expected = Arrays.asList(5, 3, 2, 1);
            List<Integer> actual = new ArrayList<>();
            while (!rootLoaded.isEmpty()) {
                actual.add(rootLoaded.poll());
            }

            Assertions.assertIterableEquals(expected, actual, "The elements should be in reverse order due to the comparator.");
        }


    }

    @Test
    void binaryHandlerPriorityQueue()
    {
        PriorityQueue<Integer> queue = new PriorityQueue<>();
        queue.add(5);
        queue.add(1);
        queue.add(3);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(queue, tempDir)) {
            // The queue is stored
        }

        PriorityQueue<Integer> root = new PriorityQueue<>();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {

            Assertions.assertNull(root.comparator(), "The default comparator should be null.");

            root.add(2);

            List<Integer> list = root.stream().toList();
            List<Integer> expected = List.of(1, 2, 3, 5);
            Assertions.assertIterableEquals(expected, list);

        }
    }

    @Test
    void binaryHandlerPriorityQueueTest()
    {
        PriorityQueue<String> queue = new PriorityQueue<>();
        queue.add("Hello");
        queue.add("Ahoj");

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(queue, tempDir)) {
            // The queue is stored
        }

        PriorityQueue<String> root = new PriorityQueue<>();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {

            Assertions.assertNull(root.comparator(), "The default comparator should be null.");

            root.add("AAzero");

            Assertions.assertEquals("AAzero", root.peek(), "Without a comparator, the first element should be 'Hello' due to the natural ordering of strings.");
        }
    }

    @Test
    void binaryHandlerPriorityQueue_comparatorString_updateApi_test()
    {
        PriorityQueue<String> queue = new PriorityQueue<>(new CustomStringComparator());
        queue.add("Hello");
        queue.add("Ahoj");

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(queue, tempDir)) {
            // The queue is stored
        }

        PriorityQueue<String> root = new PriorityQueue<>();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {

            Assertions.assertNotNull(root.comparator(), "The default comparator should be not null.");

            root.add("zero");

            Assertions.assertEquals("Ahoj", root.peek());
        }
    }

    @Test
    void binaryHandlerPriorityQueue_comparatorString_test()
    {
        PriorityQueue<String> queue = new PriorityQueue<>(new CustomStringComparator());
        queue.add("Hello");
        queue.add("Ahoj");

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(queue, tempDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(tempDir)) {
            final PriorityQueue<String> root = (PriorityQueue<String>) storageManager.root();

            Assertions.assertNotNull(root.comparator());

            root.add("zero");

            Assertions.assertEquals("Ahoj", root.peek());
        }


    }
}
