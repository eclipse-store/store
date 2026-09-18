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

import io.github.jbellis.jvector.vector.types.VectorFloat;

import java.util.List;

/**
 * Supplies the sample a quantizer is trained from, without exposing GigaMap details to the
 * compression managers.
 * <p>
 * Implemented by {@link VectorIndex.Default}, which is the only thing that knows whether the
 * vectors live on the entities themselves or in a separate vector store. Shared by
 * {@link PQCompressionManager} and {@link NVQCompressionManager} so that a single collection can
 * feed both: the collection walks the whole GigaMap and, in embedded mode, calls the user's
 * {@code Vectorizer} once per entity, which is far too expensive to do twice per persist.
 */
interface TrainingVectorProvider
{
    /**
     * Returns the current vector count.
     *
     * @return the vector count
     */
    public long getVectorCount();

    /**
     * Collects training vectors, materialising at most {@code limit} of them.
     * <p>
     * Entities without an embedding are skipped, so the returned list can be shorter than the
     * count reported by {@link #getVectorCount()}. Implementations must spread the sample across
     * the whole data set rather than taking the first {@code limit} entries, and must not
     * materialise more than {@code limit} vectors - the cap exists to bound peak heap on exactly
     * the large data sets this feature targets.
     * <p>
     * The returned list is <b>dense</b>: it holds only real vectors, in iteration order, with no
     * placeholder for a deletion hole or a missing embedding. That matters to every consumer.
     * Training against the ordinal-spaced view instead would feed the quantizer one placeholder per
     * hole and pull its parameters toward that placeholder.
     *
     * @param limit the maximum number of vectors to materialise
     * @return list of vectors to train from
     */
    public List<VectorFloat<?>> collectTrainingVectors(int limit);

}
