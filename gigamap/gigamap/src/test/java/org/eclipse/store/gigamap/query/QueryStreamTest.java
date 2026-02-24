package org.eclipse.store.gigamap.query;

/*-
 * #%L
 * EclipseStore GigaMap
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.IndexerString;
import org.junit.jupiter.api.Test;

public class QueryStreamTest
{

    @Test
    void testQueryStream()
    {
        GigaMap<TextContent> gigaMap = GigaMap.New();
        ContextIndexer contextIndexer = new ContextIndexer();
        gigaMap.index().bitmap().add(contextIndexer);

        TextContent textContent1 = new TextContent("test1");
        for (int i = 0; i < 100; i++) {
            gigaMap.add(textContent1);
        }
        List<TextContent> list = gigaMap.query().stream().distinct().toList();
        assertEquals(1, list.size());
    }

    @Test
    void testStreamFilter()
    {
        final GigaMap<TextContent> gigaMap = GigaMap.New();
        final ContextIndexer contextIndexer = new ContextIndexer();
        gigaMap.index().bitmap().add(contextIndexer);

        for (int i = 0; i < 100; i++) {
            gigaMap.add(new TextContent("test" + i));
        }

        try (Stream<TextContent> stream = gigaMap.query().stream()) {
            final List<TextContent> filtered = stream
                    .filter(tc -> tc.getText().contains("1"))
                    .toList();

            // Should match: test1, test10-19, test21, test31, etc.
            assertTrue(filtered.size() > 0);
            assertTrue(filtered.stream().allMatch(tc -> tc.getText().contains("1")));
        }
    }

    @Test
    void testStreamMap()
    {
        final GigaMap<TextContent> gigaMap = GigaMap.New();
        final ContextIndexer contextIndexer = new ContextIndexer();
        gigaMap.index().bitmap().add(contextIndexer);

        for (int i = 0; i < 10; i++) {
            gigaMap.add(new TextContent("content" + i));
        }

        try (Stream<TextContent> stream = gigaMap.query().stream()) {
            final List<String> texts = stream
                    .map(TextContent::getText)
                    .toList();

            assertEquals(10, texts.size());
            assertTrue(texts.stream().allMatch(s -> s.startsWith("content")));
        }
    }

    @Test
    void testStreamLimit()
    {
        final GigaMap<TextContent> gigaMap = GigaMap.New();
        final ContextIndexer contextIndexer = new ContextIndexer();
        gigaMap.index().bitmap().add(contextIndexer);

        for (int i = 0; i < 100; i++) {
            gigaMap.add(new TextContent("item" + i));
        }

        try (Stream<TextContent> stream = gigaMap.query().stream()) {
            final List<TextContent> limited = stream
                    .limit(5)
                    .toList();

            assertEquals(5, limited.size());
        }
    }

    @Test
    void testStreamSkip()
    {
        final GigaMap<TextContent> gigaMap = GigaMap.New();
        final ContextIndexer contextIndexer = new ContextIndexer();
        gigaMap.index().bitmap().add(contextIndexer);

        for (int i = 0; i < 20; i++) {
            gigaMap.add(new TextContent("element" + i));
        }

        try (Stream<TextContent> stream = gigaMap.query().stream()) {
            final List<TextContent> skipped = stream
                    .skip(15)
                    .toList();

            assertEquals(5, skipped.size());
        }
    }

    @Test
    void testStreamCount()
    {
        final GigaMap<TextContent> gigaMap = GigaMap.New();
        final ContextIndexer contextIndexer = new ContextIndexer();
        gigaMap.index().bitmap().add(contextIndexer);

        for (int i = 0; i < 42; i++) {
            gigaMap.add(new TextContent("data" + i));
        }

        try (Stream<TextContent> stream = gigaMap.query().stream()) {
            final long count = stream.count();
            assertEquals(42, count);
        }
    }

    @Test
    void testStreamDistinct()
    {
        final GigaMap<TextContent> gigaMap = GigaMap.New();
        final ContextIndexer contextIndexer = new ContextIndexer();
        gigaMap.index().bitmap().add(contextIndexer);

        final TextContent duplicate1 = new TextContent("duplicate");
        final TextContent duplicate2 = new TextContent("duplicate");

        for (int i = 0; i < 10; i++) {
            gigaMap.add(duplicate1);
            gigaMap.add(duplicate2);
        }

        try (Stream<TextContent> stream = gigaMap.query().stream()) {
            final List<TextContent> distinct = stream.distinct().toList();

            // Should have 2 distinct objects (even though same text)
            assertEquals(2, distinct.size());
        }
    }

    @Test
    void testStreamFindFirst()
    {
        final GigaMap<TextContent> gigaMap = GigaMap.New();
        final ContextIndexer contextIndexer = new ContextIndexer();
        gigaMap.index().bitmap().add(contextIndexer);

        for (int i = 0; i < 50; i++) {
            gigaMap.add(new TextContent("record" + i));
        }

        try (Stream<TextContent> stream = gigaMap.query().stream()) {
            final Optional<TextContent> first = stream.findFirst();

            assertTrue(first.isPresent());
            assertTrue(first.get().getText().startsWith("record"));
        }
    }

    @Test
    void testStreamAnyMatch()
    {
        final GigaMap<TextContent> gigaMap = GigaMap.New();
        final ContextIndexer contextIndexer = new ContextIndexer();
        gigaMap.index().bitmap().add(contextIndexer);

        for (int i = 0; i < 30; i++) {
            gigaMap.add(new TextContent("value" + i));
        }

        try (Stream<TextContent> stream = gigaMap.query().stream()) {
            final boolean hasValue25 = stream.anyMatch(tc -> "value25".equals(tc.getText()));
            assertTrue(hasValue25);
        }
    }

    @Test
    void testStreamAllMatch()
    {
        final GigaMap<TextContent> gigaMap = GigaMap.New();
        final ContextIndexer contextIndexer = new ContextIndexer();
        gigaMap.index().bitmap().add(contextIndexer);

        for (int i = 0; i < 15; i++) {
            gigaMap.add(new TextContent("prefix_" + i));
        }

        try (Stream<TextContent> stream = gigaMap.query().stream()) {
            final boolean allStartWithPrefix = stream.allMatch(tc -> tc.getText().startsWith("prefix_"));
            assertTrue(allStartWithPrefix);
        }
    }

    @Test
    void testStreamNoneMatch()
    {
        final GigaMap<TextContent> gigaMap = GigaMap.New();
        final ContextIndexer contextIndexer = new ContextIndexer();
        gigaMap.index().bitmap().add(contextIndexer);

        for (int i = 0; i < 20; i++) {
            gigaMap.add(new TextContent("item" + i));
        }

        try (Stream<TextContent> stream = gigaMap.query().stream()) {
            final boolean noneContainsXYZ = stream.noneMatch(tc -> tc.getText().contains("XYZ"));
            assertTrue(noneContainsXYZ);
        }
    }

    @Test
    void testStreamCollectToSet()
    {
        final GigaMap<TextContent> gigaMap = GigaMap.New();
        final ContextIndexer contextIndexer = new ContextIndexer();
        gigaMap.index().bitmap().add(contextIndexer);

        for (int i = 0; i < 25; i++) {
            gigaMap.add(new TextContent("entry" + i));
        }

        try (Stream<TextContent> stream = gigaMap.query().stream()) {
            final Set<String> texts = stream
                    .map(TextContent::getText)
                    .collect(Collectors.toSet());

            assertEquals(25, texts.size());
        }
    }

    @Test
    void testStreamGroupingBy()
    {
        final GigaMap<TextContent> gigaMap = GigaMap.New();
        final ContextIndexer contextIndexer = new ContextIndexer();
        gigaMap.index().bitmap().add(contextIndexer);

        for (int i = 0; i < 30; i++) {
            gigaMap.add(new TextContent("type" + (i % 3)));
        }

        try (Stream<TextContent> stream = gigaMap.query().stream()) {
            final Map<String, List<TextContent>> grouped = stream
                    .collect(Collectors.groupingBy(TextContent::getText));

            assertEquals(3, grouped.size());
            assertEquals(10, grouped.get("type0").size());
            assertEquals(10, grouped.get("type1").size());
            assertEquals(10, grouped.get("type2").size());
        }
    }

    @Test
    void testStreamSorted()
    {
        final GigaMap<TextContent> gigaMap = GigaMap.New();
        final ContextIndexer contextIndexer = new ContextIndexer();
        gigaMap.index().bitmap().add(contextIndexer);

        gigaMap.add(new TextContent("zebra"));
        gigaMap.add(new TextContent("apple"));
        gigaMap.add(new TextContent("banana"));
        gigaMap.add(new TextContent("cherry"));

        try (Stream<TextContent> stream = gigaMap.query().stream()) {
            final List<String> sorted = stream
                    .map(TextContent::getText)
                    .sorted()
                    .toList();

            assertEquals("apple", sorted.get(0));
            assertEquals("banana", sorted.get(1));
            assertEquals("cherry", sorted.get(2));
            assertEquals("zebra", sorted.get(3));
        }
    }

    @Test
    void testStreamWithQueryCondition()
    {
        final GigaMap<TextContent> gigaMap = GigaMap.New();
        final ContextIndexer contextIndexer = new ContextIndexer();
        gigaMap.index().bitmap().add(contextIndexer);

        for (int i = 0; i < 100; i++) {
            gigaMap.add(new TextContent("data" + i));
        }

        try (Stream<TextContent> stream = gigaMap.query()
                .and(contextIndexer.contains("data1"))
                .stream()) {
            final long count = stream.count();

            // Should match: data1, data10-19
            assertTrue(count > 0);
        }
    }

    @Test
    void testStreamPeek()
    {
        final GigaMap<TextContent> gigaMap = GigaMap.New();
        final ContextIndexer contextIndexer = new ContextIndexer();
        gigaMap.index().bitmap().add(contextIndexer);

        for (int i = 0; i < 10; i++) {
            gigaMap.add(new TextContent("doc" + i));
        }

        final List<String> peeked = new java.util.ArrayList<>();

        try (Stream<TextContent> stream = gigaMap.query().stream()) {
            final List<TextContent> result = stream
                    .peek(tc -> peeked.add(tc.getText()))
                    .toList();

            assertEquals(10, result.size());
            assertEquals(10, peeked.size());
        }
    }

    @Test
    void testStreamReduce()
    {
        final GigaMap<TextContent> gigaMap = GigaMap.New();
        final ContextIndexer contextIndexer = new ContextIndexer();
        gigaMap.index().bitmap().add(contextIndexer);

        for (int i = 0; i < 5; i++) {
            gigaMap.add(new TextContent("word" + i));
        }

        try (Stream<TextContent> stream = gigaMap.query().stream()) {
            final Optional<String> concatenated = stream
                    .map(TextContent::getText)
                    .reduce((a, b) -> a + "," + b);

            assertTrue(concatenated.isPresent());
            assertTrue(concatenated.get().contains("word"));
        }
    }

    @Test
    void testStreamFilterAndMap()
    {
        final GigaMap<TextContent> gigaMap = GigaMap.New();
        final ContextIndexer contextIndexer = new ContextIndexer();
        gigaMap.index().bitmap().add(contextIndexer);

        for (int i = 0; i < 50; i++) {
            gigaMap.add(new TextContent("element" + i));
        }

        try (Stream<TextContent> stream = gigaMap.query().stream()) {
            final List<String> result = stream
                    .filter(tc -> tc.getText().contains("2"))
                    .map(tc -> tc.getText().toUpperCase())
                    .toList();

            assertTrue(result.size() > 0);
            assertTrue(result.stream().allMatch(s -> s.contains("2")));
            assertTrue(result.stream().allMatch(s -> s.equals(s.toUpperCase())));
        }
    }

    @Test
    void testEmptyStreamOperations()
    {
        final GigaMap<TextContent> gigaMap = GigaMap.New();
        final ContextIndexer contextIndexer = new ContextIndexer();
        gigaMap.index().bitmap().add(contextIndexer);

        // Empty GigaMap
        try (Stream<TextContent> stream = gigaMap.query().stream()) {
            final long count = stream.count();
            assertEquals(0, count);
        }

        try (Stream<TextContent> stream = gigaMap.query().stream()) {
            final Optional<TextContent> first = stream.findFirst();
            assertFalse(first.isPresent());
        }
    }

    @Test
    void testStreamFlatMap()
    {
        final GigaMap<TextContent> gigaMap = GigaMap.New();
        final ContextIndexer contextIndexer = new ContextIndexer();
        gigaMap.index().bitmap().add(contextIndexer);

        gigaMap.add(new TextContent("a,b,c"));
        gigaMap.add(new TextContent("d,e,f"));
        gigaMap.add(new TextContent("g,h,i"));

        try (Stream<TextContent> stream = gigaMap.query().stream()) {
            final List<String> flatMapped = stream
                    .flatMap(tc -> java.util.Arrays.stream(tc.getText().split(",")))
                    .toList();

            assertEquals(9, flatMapped.size());
            assertTrue(flatMapped.contains("a"));
            assertTrue(flatMapped.contains("i"));
        }
    }

    @Test
    void testStreamFindAny()
    {
        final GigaMap<TextContent> gigaMap = GigaMap.New();
        final ContextIndexer contextIndexer = new ContextIndexer();
        gigaMap.index().bitmap().add(contextIndexer);

        for (int i = 0; i < 100; i++) {
            gigaMap.add(new TextContent("item" + i));
        }

        try (Stream<TextContent> stream = gigaMap.query().stream()) {
            final Optional<TextContent> any = stream.findAny();

            assertTrue(any.isPresent());
            assertTrue(any.get().getText().startsWith("item"));
        }
    }

    @Test
    void testStreamMax()
    {
        final GigaMap<TextContent> gigaMap = GigaMap.New();
        final ContextIndexer contextIndexer = new ContextIndexer();
        gigaMap.index().bitmap().add(contextIndexer);

        for (int i = 1; i <= 10; i++) {
            gigaMap.add(new TextContent("value" + i));
        }

        try (Stream<TextContent> stream = gigaMap.query().stream()) {
            final Optional<TextContent> max = stream
                    .max((a, b) -> a.getText().compareTo(b.getText()));

            assertTrue(max.isPresent());
            assertEquals("value9", max.get().getText());
        }
    }

    @Test
    void testStreamMin()
    {
        final GigaMap<TextContent> gigaMap = GigaMap.New();
        final ContextIndexer contextIndexer = new ContextIndexer();
        gigaMap.index().bitmap().add(contextIndexer);

        for (int i = 5; i <= 15; i++) {
            gigaMap.add(new TextContent("entry" + i));
        }

        try (Stream<TextContent> stream = gigaMap.query().stream()) {
            final Optional<TextContent> min = stream
                    .min((a, b) -> a.getText().compareTo(b.getText()));

            assertTrue(min.isPresent());
            assertEquals("entry10", min.get().getText());
        }
    }

    @Test
    void testStreamConcatWithOtherStream()
    {
        final GigaMap<TextContent> gigaMap = GigaMap.New();
        final ContextIndexer contextIndexer = new ContextIndexer();
        gigaMap.index().bitmap().add(contextIndexer);

        gigaMap.add(new TextContent("first"));
        gigaMap.add(new TextContent("second"));

        try (Stream<TextContent> stream1 = gigaMap.query().stream()) {
            final Stream<TextContent> stream2 = Stream.of(
                    new TextContent("third"),
                    new TextContent("fourth")
            );

            final List<String> combined = Stream.concat(stream1, stream2)
                    .map(TextContent::getText)
                    .toList();

            assertEquals(4, combined.size());
            assertTrue(combined.contains("first"));
            assertTrue(combined.contains("fourth"));
        }
    }

    @Test
    void testStreamMultipleConditions()
    {
        final GigaMap<TextContent> gigaMap = GigaMap.New();
        final ContextIndexer contextIndexer = new ContextIndexer();
        gigaMap.index().bitmap().add(contextIndexer);

        for (int i = 0; i < 100; i++) {
            gigaMap.add(new TextContent("item" + i));
        }

        try (Stream<TextContent> stream = gigaMap.query()
                .and(contextIndexer.contains("item1"))
                .stream()) {
            final long count = stream
                    .filter(tc -> !tc.getText().equals("item10"))
                    .count();

            // Should match: item1, item11-19 (excluding item10)
            assertTrue(count >= 10);
        }
    }

    @Test
    void testStreamToArray()
    {
        final GigaMap<TextContent> gigaMap = GigaMap.New();
        final ContextIndexer contextIndexer = new ContextIndexer();
        gigaMap.index().bitmap().add(contextIndexer);

        for (int i = 0; i < 5; i++) {
            gigaMap.add(new TextContent("element" + i));
        }

        try (Stream<TextContent> stream = gigaMap.query().stream()) {
            final Object[] array = stream.toArray();

            assertEquals(5, array.length);
            assertTrue(array[0] instanceof TextContent);
        }
    }

    @Test
    void testStreamCollectorsCounting()
    {
        final GigaMap<TextContent> gigaMap = GigaMap.New();
        final ContextIndexer contextIndexer = new ContextIndexer();
        gigaMap.index().bitmap().add(contextIndexer);

        for (int i = 0; i < 33; i++) {
            gigaMap.add(new TextContent("doc" + i));
        }

        try (Stream<TextContent> stream = gigaMap.query().stream()) {
            final Long count = stream.collect(Collectors.counting());

            assertEquals(33L, count);
        }
    }

    @Test
    void testStreamCollectorsJoining()
    {
        final GigaMap<TextContent> gigaMap = GigaMap.New();
        final ContextIndexer contextIndexer = new ContextIndexer();
        gigaMap.index().bitmap().add(contextIndexer);

        gigaMap.add(new TextContent("A"));
        gigaMap.add(new TextContent("B"));
        gigaMap.add(new TextContent("C"));

        try (Stream<TextContent> stream = gigaMap.query().stream()) {
            final String joined = stream
                    .map(TextContent::getText)
                    .collect(Collectors.joining(","));

            assertTrue(joined.contains("A"));
            assertTrue(joined.contains("B"));
            assertTrue(joined.contains("C"));
        }
    }

    @Test
    void testStreamPartitioning()
    {
        final GigaMap<TextContent> gigaMap = GigaMap.New();
        final ContextIndexer contextIndexer = new ContextIndexer();
        gigaMap.index().bitmap().add(contextIndexer);

        for (int i = 0; i < 20; i++) {
            gigaMap.add(new TextContent("val" + i));
        }

        try (Stream<TextContent> stream = gigaMap.query().stream()) {
            final Map<Boolean, List<TextContent>> partitioned = stream
                    .collect(Collectors.partitioningBy(tc -> tc.getText().contains("1")));

            assertTrue(partitioned.get(true).size() > 0);
            assertTrue(partitioned.get(false).size() > 0);
        }
    }

    @Test
    void testStreamSummaryStatistics()
    {
        final GigaMap<TextContent> gigaMap = GigaMap.New();
        final ContextIndexer contextIndexer = new ContextIndexer();
        gigaMap.index().bitmap().add(contextIndexer);

        for (int i = 1; i <= 10; i++) {
            gigaMap.add(new TextContent("text" + i));
        }

        try (Stream<TextContent> stream = gigaMap.query().stream()) {
            final java.util.IntSummaryStatistics stats = stream
                    .mapToInt(tc -> tc.getText().length())
                    .summaryStatistics();

            assertEquals(10, stats.getCount());
            assertTrue(stats.getMin() > 0);
            assertTrue(stats.getMax() > 0);
        }
    }

    @Test
    void testStreamLimitAfterFilter()
    {
        final GigaMap<TextContent> gigaMap = GigaMap.New();
        final ContextIndexer contextIndexer = new ContextIndexer();
        gigaMap.index().bitmap().add(contextIndexer);

        for (int i = 0; i < 100; i++) {
            gigaMap.add(new TextContent("record" + i));
        }

        try (Stream<TextContent> stream = gigaMap.query().stream()) {
            final List<TextContent> result = stream
                    .filter(tc -> tc.getText().contains("1"))
                    .limit(5)
                    .toList();

            assertEquals(5, result.size());
            assertTrue(result.stream().allMatch(tc -> tc.getText().contains("1")));
        }
    }

    @Test
    void testStreamDistinctAfterMap()
    {
        final GigaMap<TextContent> gigaMap = GigaMap.New();
        final ContextIndexer contextIndexer = new ContextIndexer();
        gigaMap.index().bitmap().add(contextIndexer);

        for (int i = 0; i < 30; i++) {
            gigaMap.add(new TextContent("category" + (i % 5)));
        }

        try (Stream<TextContent> stream = gigaMap.query().stream()) {
            final List<String> distinctCategories = stream
                    .map(TextContent::getText)
                    .distinct()
                    .toList();

            assertEquals(5, distinctCategories.size());
        }
    }

    @Test
    void testStreamMultipleOperationsChained()
    {
        final GigaMap<TextContent> gigaMap = GigaMap.New();
        final ContextIndexer contextIndexer = new ContextIndexer();
        gigaMap.index().bitmap().add(contextIndexer);

        for (int i = 0; i < 50; i++) {
            gigaMap.add(new TextContent("data" + i));
        }

        try (Stream<TextContent> stream = gigaMap.query().stream()) {
            final List<String> result = stream
                    .filter(tc -> tc.getText().contains("2"))
                    .map(TextContent::getText)
                    .map(String::toUpperCase)
                    .sorted()
                    .limit(10)
                    .toList();

            assertTrue(result.size() <= 10);
            assertTrue(result.stream().allMatch(s -> s.contains("2")));
            assertTrue(result.stream().allMatch(s -> s.equals(s.toUpperCase())));
        }
    }

    @Test
    void testStreamForEach()
    {
        final GigaMap<TextContent> gigaMap = GigaMap.New();
        final ContextIndexer contextIndexer = new ContextIndexer();
        gigaMap.index().bitmap().add(contextIndexer);

        for (int i = 0; i < 15; i++) {
            gigaMap.add(new TextContent("item" + i));
        }

        final java.util.concurrent.atomic.AtomicInteger counter = new java.util.concurrent.atomic.AtomicInteger(0);

        try (Stream<TextContent> stream = gigaMap.query().stream()) {
            stream.forEach(tc -> counter.incrementAndGet());
        }

        assertEquals(15, counter.get());
    }

    @Test
    void testStreamCollectToMap()
    {
        final GigaMap<TextContent> gigaMap = GigaMap.New();
        final ContextIndexer contextIndexer = new ContextIndexer();
        gigaMap.index().bitmap().add(contextIndexer);

        for (int i = 0; i < 10; i++) {
            gigaMap.add(new TextContent("key" + i));
        }

        try (Stream<TextContent> stream = gigaMap.query().stream()) {
            final Map<String, Integer> map = stream
                    .collect(Collectors.toMap(
                            TextContent::getText,
                            tc -> tc.getText().length()
                    ));

            assertEquals(10, map.size());
            assertTrue(map.containsKey("key0"));
            assertTrue(map.containsKey("key9"));
        }
    }


    private static class ContextIndexer extends IndexerString.Abstract<TextContent> {

        @Override
        protected String getString(TextContent entity)
        {
            return entity.getText();
        }
    }

    private static class TextContent {
        String text;

        public TextContent(String text)
        {
            this.text = text;
        }

        public String getText()
        {
            return text;
        }

        public void setText(String text)
        {
            this.text = text;
        }
    }
}
