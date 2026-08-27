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

import java.util.List;

/**
 * A type for converting objects of type E into their vector representations.
 * Implementations provide a way to map entities into numerical vectors for
 * similarity search via {@link VectorIndex}.
 * <p>
 * Vectorizers support two storage modes controlled by {@link #isEmbedded()}:
 * <p>
 * <b>Embedded mode</b> ({@code isEmbedded() == true}):
 * <ul>
 *   <li>Vectors are already stored within the entity (e.g., {@code entity.embedding()})</li>
 *   <li>VectorIndex does NOT store vectors separately - avoids duplicate storage</li>
 *   <li>Vectors are read from entities on demand via {@link #vectorize}</li>
 *   <li>More memory efficient when entities already contain their vectors</li>
 * </ul>
 * <p>
 * <b>Computed mode</b> ({@code isEmbedded() == false}, default):
 * <ul>
 *   <li>Vectors are computed or fetched externally (e.g., from an embedding API)</li>
 *   <li>VectorIndex stores vectors in a separate GigaMap for persistence</li>
 *   <li>Required when vectors are expensive to compute or entity doesn't store them</li>
 *   <li>Enables vector access without recomputation</li>
 * </ul>
 *
 * @param <E> the type of the entity to be vectorized
 */
public abstract class Vectorizer<E>
{
    /**
     * Converts the given entity into its numerical vector representation.
     *
     * @param entity the entity of type E to be vectorized
     * @return a float array representing the numerical vector of the entity
     */
    public abstract float[] vectorize(E entity);

    /**
     * Converts a list of entities into their respective numerical vector representations.
     * <p>
     * This default implementation just delegates to {@link #vectorize(Object)} for each entity.
     * <p>
     * If you want to implement an optimized custom batch vectorization logic, override this method.
     * <p>
     * The returned list must have exactly the same size as {@code entities}, with positional
     * correspondence (element <i>i</i> is the vector of entity <i>i</i>). When
     * {@link #allowsNullVectors()} returns {@code true}, individual elements may be {@code null}
     * (meaning the corresponding entity has no embedding); otherwise every element must be non-null.
     *
     * @param entities a list of entities of type E to be vectorized
     * @return a list of float arrays, where each array represents the numerical vector of the corresponding entity
     */
    public List<float[]> vectorizeAll(final List<? extends E> entities)
    {
        return entities.stream()
            .map(this::vectorize)
            .toList()
        ;
    }

    /**
     * Returns whether this vectorizer extracts vectors already embedded
     * in entities, rather than computing/fetching them externally.
     * <p>
     * When true, VectorIndex will NOT store vectors separately,
     * instead reading them from entities on demand via {@link #vectorize}.
     * <p>
     * When false (default), VectorIndex stores vectors in a separate
     * GigaMap for persistence and efficient access.
     *
     * @return true if vectors are embedded in entities, false if computed
     */
    public boolean isEmbedded()
    {
        return false;
    }

    /**
     * Returns whether this vectorizer is permitted to return {@code null} from
     * {@link #vectorize(Object)} (and, correspondingly, to include {@code null} elements in the
     * list returned by {@link #vectorizeAll(List)}).
     * <p>
     * When {@code false} (default), a {@code null} vector is treated as an error: an
     * {@link IllegalStateException} is thrown when the entity is added, updated, or re-indexed.
     * This fail-fast behavior catches accidental bugs in vectorizer implementations.
     * <p>
     * When {@code true}, a {@code null} vector means "this entity has no embedding". Such an
     * entity remains in the {@code GigaMap} and all of its other indices, but is excluded from
     * the vector index's HNSW graph and never appears in vector search results. In computed
     * mode no vector store entry is kept for it, and {@link VectorIndex#getVector(long)} returns
     * {@code null} for it. Vector transitions on update behave as follows:
     * <ul>
     *   <li>non-null &rarr; null: the entity is removed from the vector index</li>
     *   <li>null &rarr; non-null: the entity is added to the vector index</li>
     *   <li>null &rarr; null: no-op</li>
     * </ul>
     * An entity whose vector is {@code null} cannot be used as a similarity query
     * (see {@link VectorIndex#search(Object, int)}); doing so throws {@link IllegalArgumentException}.
     * <p>
     * The return value must be constant for the lifetime of the vectorizer / index — it is
     * consulted on every mutation, on reload/rebuild, and during PQ training; changing it at
     * runtime yields undefined behavior.
     *
     * @return true to permit {@code null} vectors (excluded from the index), false to fail fast
     */
    public boolean allowsNullVectors()
    {
        return false;
    }
}
