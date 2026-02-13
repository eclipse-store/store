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
import org.eclipse.store.gigamap.types.GigaMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ListRandomAccessVectorValues} and {@link GigaMapBackedVectorValues}.
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

    // ==================== GigaMapBackedVectorValues Tests ====================

    @Nested
    class GigaMapBackedVectorValuesTests
    {
        @Test
        void testSize()
        {
            final GigaMap<VectorEntry> vectorStore = GigaMap.New();
            vectorStore.add(new VectorEntry(0L, new float[]{1.0f, 2.0f, 3.0f}));
            vectorStore.add(new VectorEntry(1L, new float[]{4.0f, 5.0f, 6.0f}));
            vectorStore.add(new VectorEntry(2L, new float[]{7.0f, 8.0f, 9.0f}));

            final GigaMapBackedVectorValues values = new GigaMapBackedVectorValues(
                vectorStore, 3, vectorTypeSupport
            );

            assertEquals(3, values.size());
        }

        @Test
        void testSizeEmptyStore()
        {
            final GigaMap<VectorEntry> vectorStore = GigaMap.New();

            final GigaMapBackedVectorValues values = new GigaMapBackedVectorValues(
                vectorStore, 3, vectorTypeSupport
            );

            assertEquals(0, values.size());
        }

        @Test
        void testDimension()
        {
            final GigaMap<VectorEntry> vectorStore = GigaMap.New();

            final GigaMapBackedVectorValues values = new GigaMapBackedVectorValues(
                vectorStore, 128, vectorTypeSupport
            );

            assertEquals(128, values.dimension());
        }

        @Test
        void testGetVectorValidOrdinal()
        {
            final GigaMap<VectorEntry> vectorStore = GigaMap.New();
            vectorStore.add(new VectorEntry(0L, new float[]{1.0f, 2.0f, 3.0f}));
            vectorStore.add(new VectorEntry(1L, new float[]{4.0f, 5.0f, 6.0f}));

            final GigaMapBackedVectorValues values = new GigaMapBackedVectorValues(
                vectorStore, 3, vectorTypeSupport
            );

            // Entity IDs in GigaMap start at 0
            final VectorFloat<?> result0 = values.getVector(0);
            final VectorFloat<?> result1 = values.getVector(1);

            assertNotNull(result0);
            assertNotNull(result1);
            assertEquals(1.0f, result0.get(0), 0.001f);
            assertEquals(4.0f, result1.get(0), 0.001f);
        }

        @Test
        void testGetVectorNonExistentOrdinalReturnsNull()
        {
            final GigaMap<VectorEntry> vectorStore = GigaMap.New();
            vectorStore.add(new VectorEntry(0L, new float[]{1.0f, 2.0f, 3.0f}));

            final GigaMapBackedVectorValues values = new GigaMapBackedVectorValues(
                vectorStore, 3, vectorTypeSupport
            );

            // Ordinal 100 doesn't exist in the GigaMap
            assertNull(values.getVector(100));
        }

        @Test
        void testGetVectorConvertsToVectorFloat()
        {
            final VectorEntry originalVector = new VectorEntry(0L, new float[]{1.5f, 2.5f, 3.5f});
            final GigaMap<VectorEntry> vectorStore = GigaMap.New();
            vectorStore.add(originalVector);

            final GigaMapBackedVectorValues values = new GigaMapBackedVectorValues(
                vectorStore, 3, vectorTypeSupport
            );

            final VectorFloat<?> result = values.getVector(0);

            assertNotNull(result);
            assertEquals(3, result.length());
            assertEquals(1.5f, result.get(0), 0.001f);
            assertEquals(2.5f, result.get(1), 0.001f);
            assertEquals(3.5f, result.get(2), 0.001f);
        }

        @Test
        void testIsValueSharedReturnsFalse()
        {
            final GigaMap<VectorEntry> vectorStore = GigaMap.New();

            final GigaMapBackedVectorValues values = new GigaMapBackedVectorValues(
                vectorStore, 3, vectorTypeSupport
            );

            assertFalse(values.isValueShared());
        }

        @Test
        void testCopyReturnsNewInstance()
        {
            final GigaMap<VectorEntry> vectorStore = GigaMap.New();
            vectorStore.add(new VectorEntry(0L, new float[]{1.0f, 2.0f, 3.0f}));

            final GigaMapBackedVectorValues values = new GigaMapBackedVectorValues(
                vectorStore, 3, vectorTypeSupport
            );
            final RandomAccessVectorValues copy = values.copy();

            assertNotSame(values, copy);
            assertTrue(copy instanceof GigaMapBackedVectorValues);
            assertEquals(values.size(), copy.size());
            assertEquals(values.dimension(), copy.dimension());
        }

        @Test
        void testCopySharesUnderlyingStore()
        {
            final GigaMap<VectorEntry> vectorStore = GigaMap.New();
            vectorStore.add(new VectorEntry(0L, new float[]{1.0f, 2.0f, 3.0f}));

            final GigaMapBackedVectorValues values = new GigaMapBackedVectorValues(
                vectorStore, 3, vectorTypeSupport
            );
            final GigaMapBackedVectorValues copy = (GigaMapBackedVectorValues) values.copy();

            // Both should access the same underlying GigaMap
            assertSame(values.vectorStore, copy.vectorStore);
        }
    }

    // ==================== GigaMapBackedVectorValues.Caching Tests ====================

    @Nested
    class GigaMapBackedVectorValuesCachingTests
    {
        @Test
        void testCachingInheritsBaseProperties()
        {
            final GigaMap<VectorEntry> vectorStore = GigaMap.New();
            vectorStore.add(new VectorEntry(0L, new float[]{1.0f, 2.0f, 3.0f}));
            vectorStore.add(new VectorEntry(1L, new float[]{4.0f, 5.0f, 6.0f}));

            final GigaMapBackedVectorValues.Caching values = new GigaMapBackedVectorValues.Caching(
                vectorStore, 3, vectorTypeSupport
            );

            assertEquals(2, values.size());
            assertEquals(3, values.dimension());
            assertFalse(values.isValueShared());
        }

        @Test
        void testGetVectorCachesResult()
        {
            final GigaMap<VectorEntry> vectorStore = GigaMap.New();
            vectorStore.add(new VectorEntry(0L, new float[]{1.0f, 2.0f, 3.0f}));

            final GigaMapBackedVectorValues.Caching values = new GigaMapBackedVectorValues.Caching(
                vectorStore, 3, vectorTypeSupport
            );

            // First call should create and cache the VectorFloat
            final VectorFloat<?> first = values.getVector(0);
            // Second call should return the cached instance
            final VectorFloat<?> second = values.getVector(0);

            assertSame(first, second, "Caching should return the same VectorFloat instance");
        }

        @Test
        void testGetVectorCachesMultipleOrdinals()
        {
            final GigaMap<VectorEntry> vectorStore = GigaMap.New();
            vectorStore.add(new VectorEntry(0L, new float[]{1.0f, 2.0f, 3.0f}));
            vectorStore.add(new VectorEntry(1L, new float[]{4.0f, 5.0f, 6.0f}));
            vectorStore.add(new VectorEntry(2L, new float[]{7.0f, 8.0f, 9.0f}));

            final GigaMapBackedVectorValues.Caching values = new GigaMapBackedVectorValues.Caching(
                vectorStore, 3, vectorTypeSupport
            );

            // Access ordinals in different order
            final VectorFloat<?> v1First = values.getVector(1);
            final VectorFloat<?> v0First = values.getVector(0);
            final VectorFloat<?> v2First = values.getVector(2);

            // Access again - should return cached instances
            final VectorFloat<?> v0Second = values.getVector(0);
            final VectorFloat<?> v1Second = values.getVector(1);
            final VectorFloat<?> v2Second = values.getVector(2);

            assertSame(v0First, v0Second);
            assertSame(v1First, v1Second);
            assertSame(v2First, v2Second);
        }

        @Test
        void testGetVectorNonExistentReturnsNull()
        {
            final GigaMap<VectorEntry> vectorStore = GigaMap.New();
            vectorStore.add(new VectorEntry(0L, new float[]{1.0f, 2.0f, 3.0f}));

            final GigaMapBackedVectorValues.Caching values = new GigaMapBackedVectorValues.Caching(
                vectorStore, 3, vectorTypeSupport
            );

            assertNull(values.getVector(100));
        }

        @Test
        void testCopyReturnsNewCachingInstance()
        {
            final GigaMap<VectorEntry> vectorStore = GigaMap.New();
            vectorStore.add(new VectorEntry(0L, new float[]{1.0f, 2.0f, 3.0f}));

            final GigaMapBackedVectorValues.Caching values = new GigaMapBackedVectorValues.Caching(
                vectorStore, 3, vectorTypeSupport
            );
            final RandomAccessVectorValues copy = values.copy();

            assertNotSame(values, copy);
            assertTrue(copy instanceof GigaMapBackedVectorValues.Caching);
            assertEquals(values.size(), copy.size());
            assertEquals(values.dimension(), copy.dimension());
        }

        @Test
        void testCopyHasIndependentCache()
        {
            final GigaMap<VectorEntry> vectorStore = GigaMap.New();
            vectorStore.add(new VectorEntry(0L, new float[]{1.0f, 2.0f, 3.0f}));

            final GigaMapBackedVectorValues.Caching values = new GigaMapBackedVectorValues.Caching(
                vectorStore, 3, vectorTypeSupport
            );

            // Cache a vector in the original
            final VectorFloat<?> originalVector = values.getVector(0);

            // Create a copy
            final GigaMapBackedVectorValues.Caching copy =
                (GigaMapBackedVectorValues.Caching) values.copy();

            // The copy should create a new VectorFloat (independent cache)
            final VectorFloat<?> copyVector = copy.getVector(0);

            // Values should be equal but not the same instance (new cache lookup)
            assertNotSame(originalVector, copyVector,
                "Copy should have independent cache creating new VectorFloat instances");
            assertEquals(originalVector.get(0), copyVector.get(0), 0.001f);
        }

        @Test
        void testCachingSharesUnderlyingStore()
        {
            final GigaMap<VectorEntry> vectorStore = GigaMap.New();
            vectorStore.add(new VectorEntry(0L, new float[]{1.0f, 2.0f, 3.0f}));

            final GigaMapBackedVectorValues.Caching values = new GigaMapBackedVectorValues.Caching(
                vectorStore, 3, vectorTypeSupport
            );
            final GigaMapBackedVectorValues.Caching copy =
                (GigaMapBackedVectorValues.Caching) values.copy();

            // Both should access the same underlying GigaMap
            assertSame(values.vectorStore, copy.vectorStore);
        }

        @Test
        void testCachingExtendsGigaMapBackedVectorValues()
        {
            final GigaMap<VectorEntry> vectorStore = GigaMap.New();
            vectorStore.add(new VectorEntry(0L, new float[]{1.0f, 2.0f, 3.0f}));

            final GigaMapBackedVectorValues.Caching values = new GigaMapBackedVectorValues.Caching(
                vectorStore, 3, vectorTypeSupport
            );

            assertTrue(values instanceof GigaMapBackedVectorValues);
        }
    }
}
