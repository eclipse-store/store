package org.eclipse.store.gigamap.jvector;

/*-
 * #%L
 * EclipseStore GigaMap JVector
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

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ProjectingIterable} and its inner {@link ProjectingIterable.ProjectingIterator}.
 */
class ProjectingIterableTest
{
    // ==================== Basic Iteration Tests ====================

    @Test
    void testBasicProjection()
    {
        final List<Integer> source = Arrays.asList(1, 2, 3, 4, 5);
        final ProjectingIterable<Integer, String> iterable = new ProjectingIterable<>(
            source,
            Object::toString
        );

        final List<String> result = new ArrayList<>();
        for (final String s : iterable)
        {
            result.add(s);
        }

        assertEquals(Arrays.asList("1", "2", "3", "4", "5"), result);
    }

    @Test
    void testProjectionWithTransformation()
    {
        final List<Integer> source = Arrays.asList(1, 2, 3, 4, 5);
        final ProjectingIterable<Integer, Integer> iterable = new ProjectingIterable<>(
            source,
            n -> n * 2
        );

        final List<Integer> result = new ArrayList<>();
        for (final Integer n : iterable)
        {
            result.add(n);
        }

        assertEquals(Arrays.asList(2, 4, 6, 8, 10), result);
    }

    @Test
    void testProjectionWithComplexTransformation()
    {
        final List<String> source = Arrays.asList("hello", "world", "test");
        final ProjectingIterable<String, Integer> iterable = new ProjectingIterable<>(
            source,
            String::length
        );

        final List<Integer> result = new ArrayList<>();
        for (final Integer len : iterable)
        {
            result.add(len);
        }

        assertEquals(Arrays.asList(5, 5, 4), result);
    }

    // ==================== Empty Iterable Tests ====================

    @Test
    void testEmptyIterable()
    {
        final List<Integer> source = Collections.emptyList();
        final ProjectingIterable<Integer, String> iterable = new ProjectingIterable<>(
            source,
            Object::toString
        );

        final Iterator<String> iterator = iterable.iterator();
        assertFalse(iterator.hasNext());
    }

    @Test
    void testEmptyIterableForEach()
    {
        final List<Integer> source = Collections.emptyList();
        final ProjectingIterable<Integer, String> iterable = new ProjectingIterable<>(
            source,
            Object::toString
        );

        final List<String> result = new ArrayList<>();
        for (final String s : iterable)
        {
            result.add(s);
        }

        assertTrue(result.isEmpty());
    }

    // ==================== Single Element Tests ====================

    @Test
    void testSingleElement()
    {
        final List<Integer> source = Collections.singletonList(42);
        final ProjectingIterable<Integer, String> iterable = new ProjectingIterable<>(
            source,
            n -> "value:" + n
        );

        final Iterator<String> iterator = iterable.iterator();
        assertTrue(iterator.hasNext());
        assertEquals("value:42", iterator.next());
        assertFalse(iterator.hasNext());
    }

    // ==================== Iterator Behavior Tests ====================

    @Test
    void testHasNextDoesNotAdvance()
    {
        final List<Integer> source = Arrays.asList(1, 2, 3);
        final ProjectingIterable<Integer, Integer> iterable = new ProjectingIterable<>(
            source,
            n -> n * 10
        );

        final Iterator<Integer> iterator = iterable.iterator();

        // Multiple hasNext calls should not advance
        assertTrue(iterator.hasNext());
        assertTrue(iterator.hasNext());
        assertTrue(iterator.hasNext());

        // First next() should still return the first element
        assertEquals(10, iterator.next());
    }

    @Test
    void testNextWithoutHasNext()
    {
        final List<Integer> source = Arrays.asList(1, 2, 3);
        final ProjectingIterable<Integer, Integer> iterable = new ProjectingIterable<>(
            source,
            n -> n * 10
        );

        final Iterator<Integer> iterator = iterable.iterator();

        // Call next() without hasNext()
        assertEquals(10, iterator.next());
        assertEquals(20, iterator.next());
        assertEquals(30, iterator.next());
    }

    @Test
    void testNextOnExhaustedIterator()
    {
        final List<Integer> source = Collections.singletonList(1);
        final ProjectingIterable<Integer, Integer> iterable = new ProjectingIterable<>(
            source,
            n -> n
        );

        final Iterator<Integer> iterator = iterable.iterator();
        iterator.next(); // Consume the only element

        assertThrows(NoSuchElementException.class, iterator::next);
    }

    // ==================== Multiple Iterations Tests ====================

    @Test
    void testMultipleIterations()
    {
        final List<Integer> source = Arrays.asList(1, 2, 3);
        final ProjectingIterable<Integer, String> iterable = new ProjectingIterable<>(
            source,
            Object::toString
        );

        // First iteration
        final List<String> result1 = new ArrayList<>();
        for (final String s : iterable)
        {
            result1.add(s);
        }

        // Second iteration
        final List<String> result2 = new ArrayList<>();
        for (final String s : iterable)
        {
            result2.add(s);
        }

        assertEquals(result1, result2);
        assertEquals(Arrays.asList("1", "2", "3"), result1);
    }

    @Test
    void testIndependentIterators()
    {
        final List<Integer> source = Arrays.asList(1, 2, 3);
        final ProjectingIterable<Integer, Integer> iterable = new ProjectingIterable<>(
            source,
            n -> n
        );

        final Iterator<Integer> iter1 = iterable.iterator();
        final Iterator<Integer> iter2 = iterable.iterator();

        // Advance iter1
        assertEquals(1, iter1.next());
        assertEquals(2, iter1.next());

        // iter2 should still be at the beginning
        assertEquals(1, iter2.next());

        // Both should be independent
        assertEquals(3, iter1.next());
        assertEquals(2, iter2.next());
    }

    // ==================== Remove Operation Tests ====================

    @Test
    void testRemove()
    {
        final List<Integer> source = new ArrayList<>(Arrays.asList(1, 2, 3, 4, 5));
        final ProjectingIterable<Integer, Integer> iterable = new ProjectingIterable<>(
            source,
            n -> n * 10
        );

        final Iterator<Integer> iterator = iterable.iterator();
        iterator.next(); // 10 (1)
        iterator.next(); // 20 (2)
        iterator.remove(); // Remove 2

        assertEquals(Arrays.asList(1, 3, 4, 5), source);
    }

    @Test
    void testRemoveMultiple()
    {
        final List<Integer> source = new ArrayList<>(Arrays.asList(1, 2, 3, 4, 5));
        final ProjectingIterable<Integer, Integer> iterable = new ProjectingIterable<>(
            source,
            n -> n
        );

        final Iterator<Integer> iterator = iterable.iterator();

        // Remove odd numbers
        while (iterator.hasNext())
        {
            final int value = iterator.next();
            if (value % 2 == 1)
            {
                iterator.remove();
            }
        }

        assertEquals(Arrays.asList(2, 4), source);
    }

    @Test
    void testRemoveBeforeNext()
    {
        final List<Integer> source = new ArrayList<>(Arrays.asList(1, 2, 3));
        final ProjectingIterable<Integer, Integer> iterable = new ProjectingIterable<>(
            source,
            n -> n
        );

        final Iterator<Integer> iterator = iterable.iterator();

        // Calling remove() before next() should throw
        assertThrows(IllegalStateException.class, iterator::remove);
    }

    @Test
    void testRemoveTwice()
    {
        final List<Integer> source = new ArrayList<>(Arrays.asList(1, 2, 3));
        final ProjectingIterable<Integer, Integer> iterable = new ProjectingIterable<>(
            source,
            n -> n
        );

        final Iterator<Integer> iterator = iterable.iterator();
        iterator.next();
        iterator.remove();

        // Calling remove() again without next() should throw
        assertThrows(IllegalStateException.class, iterator::remove);
    }

    // ==================== Type Transformation Tests ====================

    @Test
    void testIntegerToStringProjection()
    {
        final List<Integer> source = Arrays.asList(100, 200, 300);
        final ProjectingIterable<Integer, String> iterable = new ProjectingIterable<>(
            source,
            n -> "Number: " + n
        );

        final List<String> result = new ArrayList<>();
        iterable.forEach(result::add);

        assertEquals(Arrays.asList("Number: 100", "Number: 200", "Number: 300"), result);
    }

    @Test
    void testStringToIntegerProjection()
    {
        final List<String> source = Arrays.asList("10", "20", "30");
        final ProjectingIterable<String, Integer> iterable = new ProjectingIterable<>(
            source,
            Integer::parseInt
        );

        final List<Integer> result = new ArrayList<>();
        iterable.forEach(result::add);

        assertEquals(Arrays.asList(10, 20, 30), result);
    }

    @Test
    void testObjectToObjectProjection()
    {
        final List<TestRecord> source = Arrays.asList(
            new TestRecord("Alice", 25),
            new TestRecord("Bob", 30),
            new TestRecord("Charlie", 35)
        );

        final ProjectingIterable<TestRecord, String> iterable = new ProjectingIterable<>(
            source,
            TestRecord::name
        );

        final List<String> result = new ArrayList<>();
        iterable.forEach(result::add);

        assertEquals(Arrays.asList("Alice", "Bob", "Charlie"), result);
    }

    @Test
    void testProjectionToSameType()
    {
        final List<Integer> source = Arrays.asList(1, 2, 3);
        final ProjectingIterable<Integer, Integer> iterable = new ProjectingIterable<>(
            source,
            n -> n + 100
        );

        final List<Integer> result = new ArrayList<>();
        iterable.forEach(result::add);

        assertEquals(Arrays.asList(101, 102, 103), result);
    }

    // ==================== Null Handling Tests ====================

    @Test
    void testProjectionReturningNull()
    {
        final List<Integer> source = Arrays.asList(1, 2, 3);
        final ProjectingIterable<Integer, String> iterable = new ProjectingIterable<>(
            source,
            n -> n == 2 ? null : n.toString()
        );

        final List<String> result = new ArrayList<>();
        iterable.forEach(result::add);

        assertEquals(Arrays.asList("1", null, "3"), result);
    }

    @Test
    void testSourceWithNullElements()
    {
        final List<String> source = Arrays.asList("a", null, "c");
        final ProjectingIterable<String, String> iterable = new ProjectingIterable<>(
            source,
            s -> s == null ? "NULL" : s.toUpperCase()
        );

        final List<String> result = new ArrayList<>();
        iterable.forEach(result::add);

        assertEquals(Arrays.asList("A", "NULL", "C"), result);
    }

    // ==================== Chained Projection Tests ====================

    @Test
    void testChainedProjections()
    {
        final List<Integer> source = Arrays.asList(1, 2, 3);

        // First projection: Integer -> String
        final ProjectingIterable<Integer, String> first = new ProjectingIterable<>(
            source,
            n -> "num" + n
        );

        // Second projection: String -> Integer (length)
        final ProjectingIterable<String, Integer> second = new ProjectingIterable<>(
            first,
            String::length
        );

        final List<Integer> result = new ArrayList<>();
        second.forEach(result::add);

        // "num1", "num2", "num3" all have length 4
        assertEquals(Arrays.asList(4, 4, 4), result);
    }

    // ==================== Large Dataset Tests ====================

    @Test
    void testLargeDataset()
    {
        final int size = 10000;
        final List<Integer> source = new ArrayList<>(size);
        for (int i = 0; i < size; i++)
        {
            source.add(i);
        }

        final ProjectingIterable<Integer, Integer> iterable = new ProjectingIterable<>(
            source,
            n -> n * 2
        );

        int count = 0;
        int sum = 0;
        for (final Integer n : iterable)
        {
            count++;
            sum += n;
        }

        assertEquals(size, count);
        // Sum of 0*2 + 1*2 + ... + 9999*2 = 2 * (0 + 1 + ... + 9999) = 2 * (9999 * 10000 / 2) = 99990000
        assertEquals(99990000, sum);
    }

    // ==================== ForEach Tests ====================

    @Test
    void testForEachConsumer()
    {
        final List<Integer> source = Arrays.asList(1, 2, 3, 4, 5);
        final ProjectingIterable<Integer, Integer> iterable = new ProjectingIterable<>(
            source,
            n -> n * n
        );

        final List<Integer> result = new ArrayList<>();
        iterable.forEach(result::add);

        assertEquals(Arrays.asList(1, 4, 9, 16, 25), result);
    }

    // ==================== Helper Classes ====================

    private record TestRecord(String name, int age) {}
}
