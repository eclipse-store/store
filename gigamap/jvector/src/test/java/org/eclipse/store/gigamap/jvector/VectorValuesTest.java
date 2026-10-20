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

import io.github.jbellis.jvector.graph.RandomAccessVectorValues;
import io.github.jbellis.jvector.vector.VectorizationProvider;
import io.github.jbellis.jvector.vector.types.VectorFloat;
import io.github.jbellis.jvector.vector.types.VectorTypeSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerArray;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ListRandomAccessVectorValues}, {@link MappedVectorValues}, {@link NullSafeVectorValues} and
 * {@link ParallelGraphInsertion}.
 */
class VectorValuesTest
{
    private VectorTypeSupport vectorTypeSupport;

    @BeforeEach
    void setUp()
    {
        this.vectorTypeSupport = VectorizationProvider.getInstance().getVectorTypeSupport();
    }

    // ==================== ListRandomAccessVectorValues Tests ====================

    @Nested
    class ListRandomAccessVectorValuesTests
    {
        @Test
        void testSize()
        {
            final List<VectorFloat<?>> vectors = new ArrayList<>();
            vectors.add(vectorTypeSupport.createFloatVector(new float[]{1.0f, 2.0f, 3.0f}));
            vectors.add(vectorTypeSupport.createFloatVector(new float[]{4.0f, 5.0f, 6.0f}));
            vectors.add(vectorTypeSupport.createFloatVector(new float[]{7.0f, 8.0f, 9.0f}));

            final ListRandomAccessVectorValues values = new ListRandomAccessVectorValues(vectors, 3);

            assertEquals(3, values.size());
        }

        @Test
        void testSizeEmptyList()
        {
            final List<VectorFloat<?>> vectors = new ArrayList<>();
            final ListRandomAccessVectorValues values = new ListRandomAccessVectorValues(vectors, 3);

            assertEquals(0, values.size());
        }

        @Test
        void testDimension()
        {
            final List<VectorFloat<?>> vectors = new ArrayList<>();
            vectors.add(vectorTypeSupport.createFloatVector(new float[]{1.0f, 2.0f, 3.0f, 4.0f, 5.0f}));

            final ListRandomAccessVectorValues values = new ListRandomAccessVectorValues(vectors, 5);

            assertEquals(5, values.dimension());
        }

        @Test
        void testGetVectorValidOrdinal()
        {
            final float[] vector1 = {1.0f, 2.0f, 3.0f};
            final float[] vector2 = {4.0f, 5.0f, 6.0f};

            final List<VectorFloat<?>> vectors = new ArrayList<>();
            vectors.add(vectorTypeSupport.createFloatVector(vector1));
            vectors.add(vectorTypeSupport.createFloatVector(vector2));

            final ListRandomAccessVectorValues values = new ListRandomAccessVectorValues(vectors, 3);

            final VectorFloat<?> result0 = values.getVector(0);
            final VectorFloat<?> result1 = values.getVector(1);

            assertNotNull(result0);
            assertNotNull(result1);
            assertEquals(1.0f, result0.get(0), 0.001f);
            assertEquals(4.0f, result1.get(0), 0.001f);
        }

        @Test
        void testGetVectorNegativeOrdinalReturnsNull()
        {
            final List<VectorFloat<?>> vectors = new ArrayList<>();
            vectors.add(vectorTypeSupport.createFloatVector(new float[]{1.0f, 2.0f, 3.0f}));

            final ListRandomAccessVectorValues values = new ListRandomAccessVectorValues(vectors, 3);

            assertNull(values.getVector(-1));
            assertNull(values.getVector(-100));
        }

        @Test
        void testGetVectorOutOfBoundsReturnsNull()
        {
            final List<VectorFloat<?>> vectors = new ArrayList<>();
            vectors.add(vectorTypeSupport.createFloatVector(new float[]{1.0f, 2.0f, 3.0f}));
            vectors.add(vectorTypeSupport.createFloatVector(new float[]{4.0f, 5.0f, 6.0f}));

            final ListRandomAccessVectorValues values = new ListRandomAccessVectorValues(vectors, 3);

            assertNull(values.getVector(2));
            assertNull(values.getVector(100));
        }

        @Test
        void testIsValueSharedReturnsFalse()
        {
            final List<VectorFloat<?>> vectors = new ArrayList<>();
            vectors.add(vectorTypeSupport.createFloatVector(new float[]{1.0f, 2.0f, 3.0f}));

            final ListRandomAccessVectorValues values = new ListRandomAccessVectorValues(vectors, 3);

            assertFalse(values.isValueShared());
        }

        @Test
        void testCopyReturnsNewInstance()
        {
            final List<VectorFloat<?>> vectors = new ArrayList<>();
            vectors.add(vectorTypeSupport.createFloatVector(new float[]{1.0f, 2.0f, 3.0f}));
            vectors.add(vectorTypeSupport.createFloatVector(new float[]{4.0f, 5.0f, 6.0f}));

            final ListRandomAccessVectorValues values = new ListRandomAccessVectorValues(vectors, 3);
            final RandomAccessVectorValues copy = values.copy();

            assertNotSame(values, copy);
            assertTrue(copy instanceof ListRandomAccessVectorValues);
            assertEquals(values.size(), copy.size());
            assertEquals(values.dimension(), copy.dimension());
        }

        @Test
        void testCopySharesUnderlyingVectors()
        {
            final List<VectorFloat<?>> vectors = new ArrayList<>();
            vectors.add(vectorTypeSupport.createFloatVector(new float[]{1.0f, 2.0f, 3.0f}));

            final ListRandomAccessVectorValues values = new ListRandomAccessVectorValues(vectors, 3);
            final RandomAccessVectorValues copy = values.copy();

            // Both should return the same VectorFloat instance from the shared list
            assertSame(values.getVector(0), copy.getVector(0));
        }
    }

    // ==================== MappedVectorValues Tests ====================

    private VectorFile.Default openFile(final Path directory)
    {
        final VectorFile.Default file = VectorFile.Default.open(directory, "values", 3, 256L, 0L, 0L, "test");
        file.recovered();
        return file;
    }

    @Nested
    class MappedVectorValuesTests
    {
        @TempDir
        Path directory;

        @Test
        void getVector_writtenOrdinal_returnsACopyOfTheVector()
        {
            try(final VectorFile file = openFile(this.directory))
            {
                file.write(2, new float[]{1f, 2f, 3f}, Vectorizer.NO_KEY);
                final MappedVectorValues values = new MappedVectorValues(file, vectorTypeSupport);

                final VectorFloat<?> vector = values.getVector(2);
                assertEquals(3, vector.length());
                assertEquals(2f, vector.get(1));
                vector.set(1, 99f);
                assertEquals(2f, values.getVector(2).get(1), "each read is a copy");
            }
        }

        @Test
        void getVector_absentOrdinal_returnsNull()
        {
            try(final VectorFile file = openFile(this.directory))
            {
                file.write(0, new float[]{1f, 2f, 3f}, Vectorizer.NO_KEY);
                final MappedVectorValues values = new MappedVectorValues(file, vectorTypeSupport);

                assertNull(values.getVector(1));
                assertNull(values.getVector(1_000));
                file.write(0, null, Vectorizer.NO_KEY);
                assertNull(values.getVector(0));
            }
        }

        @Test
        void size_isAnUpperBoundOfTheWrittenOrdinals_andIsNotShared()
        {
            try(final VectorFile file = openFile(this.directory))
            {
                final MappedVectorValues values = new MappedVectorValues(file, vectorTypeSupport);
                assertEquals(0, values.size());

                file.write(9, new float[]{1f, 2f, 3f}, Vectorizer.NO_KEY);

                assertTrue(values.size() >= 10);
                assertEquals(3, values.dimension());
                assertFalse(values.isValueShared());
                assertSame(values, values.copy(), "stateless, so one instance serves every thread");
            }
        }

        /**
         * The index accepts Integer.MAX_VALUE as an ordinal; the dense bound one above it does not fit an int and
         * must fail as such rather than wrap to a negative size.
         */
        @Test
        void size_highestOrdinalIsMaxValue_failsInsteadOfWrapping()
        {
            // 65,536 slots per chunk, so the chunk table stays small at the top of the ordinal space.
            try(final VectorFile file = VectorFile.Default.open(this.directory, "max", 3, 64L << 16, 0L, 0L, "test"))
            {
                file.write(Integer.MAX_VALUE, new float[]{1f, 2f, 3f}, Vectorizer.NO_KEY);
                final MappedVectorValues values = new MappedVectorValues(file, vectorTypeSupport);

                assertEquals(3f, values.getVector(Integer.MAX_VALUE).get(2));
                assertThrows(ArithmeticException.class, values::size);
            }
        }

        /**
         * The last chunk of the ordinal space reaches Integer.MAX_VALUE. A reopened file must bound its ordinals by
         * the highest slot written, not by the end of that chunk, or a valid index fails on restart.
         */
        @Test
        void size_reopenedWithHighestOrdinalInTheLastChunk_isTheDenseBound()
        {
            final int  ordinal = Integer.MAX_VALUE - 100;
            final long nonce;
            final long epoch;
            try(final VectorFile file = VectorFile.Default.open(this.directory, "top", 3, 64L << 16, 0L, 0L, "test"))
            {
                file.recovered();
                nonce = file.nonce();
                file.write(ordinal, new float[]{1f, 2f, 3f}, Vectorizer.NO_KEY);
                epoch = file.seal();
                file.committed(epoch);
            }

            try(final VectorFile file = VectorFile.Default.open(this.directory, "top", 3, 64L << 16, nonce, epoch,
                "test"))
            {
                final MappedVectorValues values = new MappedVectorValues(file, vectorTypeSupport);

                assertEquals(ordinal + 1, values.size());
                assertEquals(2f, values.getVector(ordinal).get(1));
            }
        }

        @Test
        void getVectorInto_copiesStraightIntoTheDestination()
        {
            try(final VectorFile file = openFile(this.directory))
            {
                file.write(1, new float[]{4f, 5f, 6f}, Vectorizer.NO_KEY);
                final MappedVectorValues values      = new MappedVectorValues(file, vectorTypeSupport);
                final VectorFloat<?>     destination = vectorTypeSupport.createFloatVector(new float[5]);

                values.getVectorInto(1, destination, 2);

                assertEquals(0f, destination.get(1));
                assertEquals(4f, destination.get(2));
                assertEquals(6f, destination.get(4));
            }
        }

        @Test
        void nullSafe_getVectorInto_absentOrdinal_copiesThePlaceholder()
        {
            try(final VectorFile file = openFile(this.directory))
            {
                final NullSafeVectorValues values = new NullSafeVectorValues(
                    new MappedVectorValues(file, vectorTypeSupport), 3, vectorTypeSupport
                );
                final VectorFloat<?> destination = vectorTypeSupport.createFloatVector(new float[3]);

                values.getVectorInto(7, destination, 0);

                assertEquals(1e-6f, destination.get(0));
            }
        }

        @Test
        void getVector_concurrentReadersAndAWriter_readOnlyWholeVectors() throws Exception
        {
            try(final VectorFile file = openFile(this.directory))
            {
                file.write(0, new float[]{0f, 1f, 2f}, Vectorizer.NO_KEY);
                final MappedVectorValues values   = new MappedVectorValues(file, vectorTypeSupport);
                final ExecutorService    executor = Executors.newFixedThreadPool(3);
                try
                {
                    final List<Future<Boolean>> readers = new ArrayList<>();
                    for(int r = 0; r < 3; r++)
                    {
                        readers.add(executor.submit(() ->
                        {
                            for(int i = 0; i < 50_000; i++)
                            {
                                final VectorFloat<?> vector = values.getVector(0);
                                if(vector.get(1) != vector.get(0) + 1f || vector.get(2) != vector.get(0) + 2f)
                                {
                                    return false;
                                }
                            }
                            return true;
                        }));
                    }
                    for(int i = 0; i < 50_000; i++)
                    {
                        file.write(0, new float[]{i, i + 1f, i + 2f}, Vectorizer.NO_KEY);
                    }
                    for(final Future<Boolean> reader : readers)
                    {
                        assertTrue(reader.get(60, TimeUnit.SECONDS), "a reader saw a torn vector");
                    }
                }
                finally
                {
                    executor.shutdownNow();
                }
            }
        }
    }

    @Nested
    class MappedVectorValuesCachingTests
    {
        @TempDir
        Path directory;

        @Test
        void getVector_sameOrdinal_returnsTheCachedInstance()
        {
            try(final VectorFile file = openFile(this.directory))
            {
                file.write(0, new float[]{1f, 2f, 3f}, Vectorizer.NO_KEY);
                file.write(1, new float[]{4f, 5f, 6f}, Vectorizer.NO_KEY);
                final MappedVectorValues.Caching values = new MappedVectorValues.Caching(file, vectorTypeSupport, 4);

                final VectorFloat<?> first = values.getVector(1);
                assertSame(first, values.getVector(1));
                assertNotSame(first, values.getVector(0));
                assertNull(values.getVector(2));
                assertNull(values.getVector(2), "a hole is cached as one as well");
            }
        }

        @Test
        void copy_startsWithAnEmptyCache()
        {
            try(final VectorFile file = openFile(this.directory))
            {
                file.write(0, new float[]{1f, 2f, 3f}, Vectorizer.NO_KEY);
                final MappedVectorValues.Caching values = new MappedVectorValues.Caching(file, vectorTypeSupport, 4);
                final VectorFloat<?>             cached = values.getVector(0);

                final RandomAccessVectorValues copy = values.copy();

                assertNotSame(values, copy);
                assertNotSame(cached, copy.getVector(0));
            }
        }

        @Test
        void getVectorInto_readsThroughTheCache()
        {
            try(final VectorFile file = openFile(this.directory))
            {
                file.write(0, new float[]{1f, 2f, 3f}, Vectorizer.NO_KEY);
                final MappedVectorValues.Caching values      =
                    new MappedVectorValues.Caching(file, vectorTypeSupport, 4);
                final VectorFloat<?>             destination = vectorTypeSupport.createFloatVector(new float[3]);

                values.getVectorInto(0, destination, 0);

                assertEquals(3f, destination.get(2));
            }
        }
    }

    // ==================== NullSafeVectorValues Tests ====================

    @Nested
    class NullSafeVectorValuesTests
    {
        @Test
        void getVector_holeFromManyThreadsAndCopies_returnsOneFullyInitializedPlaceholder() throws Exception
        {
            final List<VectorFloat<?>> one = new ArrayList<>();
            one.add(vectorTypeSupport.createFloatVector(new float[]{1, 1, 1}));
            final RandomAccessVectorValues holes = new ListRandomAccessVectorValues(one, 3)
            {
                @Override
                public VectorFloat<?> getVector(final int ordinal)
                {
                    return ordinal == 0 ? super.getVector(0) : null;
                }
            };
            final NullSafeVectorValues values = new NullSafeVectorValues(holes, 3, vectorTypeSupport);

            // Shared by the parallel rebuild's workers: once created on the first hole, every thread and every copy
            // sees the same, completely published placeholder.
            final VectorFloat<?> placeholder = values.getVector(5);
            assertNotNull(placeholder);
            assertEquals(3, placeholder.length());
            for(int i = 0; i < 3; i++)
            {
                assertEquals(1e-6f, placeholder.get(i));
            }
            assertSame(placeholder, values.copy().getVector(7));

            final ExecutorService executor = Executors.newFixedThreadPool(4);
            try
            {
                final List<Future<VectorFloat<?>>> results = new ArrayList<>();
                for(int i = 0; i < 16; i++)
                {
                    final int ordinal = 1 + i;
                    results.add(executor.submit(() -> values.getVector(ordinal)));
                }
                for(final Future<VectorFloat<?>> result : results)
                {
                    assertSame(placeholder, result.get(10, TimeUnit.SECONDS));
                }
            }
            finally
            {
                executor.shutdownNow();
            }
        }
    }

    // ==================== ParallelGraphInsertion Tests ====================

    @Nested
    class ParallelGraphInsertionTests
    {
        @Test
        void forEach_largeCount_visitsEveryIndexOnce()
        {
            final int                       count   = ParallelGraphInsertion.MIN_PARALLEL_NODES * 8;
            final AtomicIntegerArray        visits  = new AtomicIntegerArray(count);
            final Set<String>               threads = ConcurrentHashMap.newKeySet();

            ParallelGraphInsertion.forEach(count, i ->
            {
                visits.incrementAndGet(i);
                threads.add(Thread.currentThread().getName());
            }, "parallel-insertion-test");

            for(int i = 0; i < count; i++)
            {
                assertEquals(1, visits.get(i), "index " + i);
            }
            if(ParallelGraphInsertion.parallelism() > 1)
            {
                assertTrue(threads.stream().allMatch(name -> name.startsWith("parallel-insertion-test-")),
                    "ran on " + threads);
            }
        }

        @Test
        void forEach_smallCount_runsOnCallingThread()
        {
            final String      caller  = Thread.currentThread().getName();
            final Set<String> threads = ConcurrentHashMap.newKeySet();
            final int[]       visited = {0};

            ParallelGraphInsertion.forEach(ParallelGraphInsertion.MIN_PARALLEL_NODES - 1, i ->
            {
                threads.add(Thread.currentThread().getName());
                visited[0]++;
            }, "parallel-insertion-test");

            assertEquals(ParallelGraphInsertion.MIN_PARALLEL_NODES - 1, visited[0]);
            assertEquals(Set.of(caller), threads);
        }

        @Test
        void forEach_parallelismOne_runsLargeCountOnCallingThread()
        {
            final String      caller  = Thread.currentThread().getName();
            final Set<String> threads = ConcurrentHashMap.newKeySet();
            final int         count   = ParallelGraphInsertion.MIN_PARALLEL_NODES * 4;
            final int[]       visited = {0};

            ParallelGraphInsertion.forEach(count, i ->
            {
                threads.add(Thread.currentThread().getName());
                visited[0]++;
            }, "parallel-insertion-test", 1);

            assertEquals(count, visited[0]);
            assertEquals(Set.of(caller), threads);
        }

        @Test
        void forEach_parallelismTwo_usesAtMostTwoWorkers()
        {
            final Set<String> threads = ConcurrentHashMap.newKeySet();

            ParallelGraphInsertion.forEach(ParallelGraphInsertion.MIN_PARALLEL_NODES * 8,
                i -> threads.add(Thread.currentThread().getName()), "parallel-insertion-test", 2);

            assertTrue(threads.size() <= 2, "ran on " + threads);
        }

        @Test
        void forEach_actionThrows_propagates()
        {
            final IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> ParallelGraphInsertion.forEach(ParallelGraphInsertion.MIN_PARALLEL_NODES * 2, i ->
                {
                    if(i == 300)
                    {
                        throw new IllegalStateException("boom");
                    }
                }, "parallel-insertion-test"));

            // ForkJoinTask.join and the parallel stream may each rethrow a copy of a worker's exception that carries
            // the previous one as its cause, so the original can sit more than one level down.
            Throwable original = failure;
            while(!"boom".equals(original.getMessage()) && original.getCause() != null)
            {
                original = original.getCause();
            }
            assertInstanceOf(IllegalStateException.class, original);
            assertEquals("boom", original.getMessage());
        }
    }
}
