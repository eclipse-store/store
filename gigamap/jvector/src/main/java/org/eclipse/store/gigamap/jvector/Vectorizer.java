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
}
