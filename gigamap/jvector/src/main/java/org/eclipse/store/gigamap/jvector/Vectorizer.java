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

import java.util.ArrayList;
import java.util.List;

/**
 * A type for converting objects of type E into their vector representations.
 * Implementations provide a way to map entities into numerical vectors for
 * similarity search via {@link VectorIndex}.
 *
 * <h2>The contract</h2>
 * {@link #vectorize(Object)} must reproduce the committed vector of a persisted entity: called on an entity
 * loaded from the storage, it returns the vector the entity had when it was stored, possibly at a cost (an
 * embedding API). The index relies on that in both modes below.
 * <p>
 * The index keeps every vector in a memory-mapped vector file, which it reads without any lock for scoring and
 * search. That file is a cache, never made durable when the storage commits, so the index can re-vectorize
 * any entity from it: after a crash it does so for the vectors written since the last checkpoint (see
 * {@link VectorIndexConfiguration#vectorCheckpointIntervalMs()}), and for every entity when the file is lost or
 * an in-memory index is loaded. A vectorizer that cannot reproduce a vector breaks the contract: a {@code null}
 * where a vector is required is dropped and counted at ERROR, so the breach is visible.
 * <p>
 * An expensive vectorizer therefore pays per repaired entity after a crash, and after a reboot that is every
 * entity in a chunk written since the last checkpoint. {@link #vectorKey(Object)} spares it the calls that an
 * update leaving the vector unchanged would otherwise cost.
 * <p>
 * <b>Change what an entity's vector derives from only through the GigaMap</b>: {@code update} or {@code apply}
 * for an in-place change, or {@code set}/{@code replace} with a new instance. A field mutated in place without
 * them is not seen by the index, which keeps serving the old vector, and the next recovery that re-vectorizes the
 * entity silently takes the new one. {@code set} rejects writing back the instance the id already holds, because
 * the keys it had before the mutation can no longer be derived.
 * <p>
 * <b>The storage holds no vectors.</b> Computed vectors are not persisted with the entities any more: the vector
 * file is the only copy outside the vectorizer. An on-disk index keeps its file across restarts, next to its
 * graph, and re-vectorizes only what a crash or a lost file requires. An <b>in-memory</b> index with a computed
 * vectorizer does the same if it has a configured directory or location ({@link VectorIndex#keepsIndexFiles()});
 * without one, and always when embedded, its file is temporary, so every load re-vectorizes every entity, under
 * the GigaMap monitor, which blocks reads and mutations of the map until it is done. For a vectorizer backed by an
 * embedding API, configure a directory, or accept one call per entity and a blocked map on every start. Either
 * way the vectorizer must stay deterministic: a model behind it that
 * changes returns other vectors for the same entities, and the index takes them as they come.
 *
 * <h2>Embedded and computed vectors</h2>
 * {@link #isEmbedded()} tells where the vector comes from. Both keep every vector in the vector file and score
 * the same way; the mode decides only whether an in-memory index keeps its vector file (computed vectors with a
 * configured directory or location do, see {@link VectorIndex#keepsIndexFiles()}) and how a reindex collects the
 * vectors (embedded: read from the entities again while re-adding; computed: vectorized once, before anything is
 * dropped).
 * <ul>
 *   <li><b>Embedded</b> ({@code isEmbedded() == true}): the vector is part of the entity, e.g.
 *       {@code entity.embedding()}, so reproducing it is cheap.</li>
 *   <li><b>Computed</b> ({@code isEmbedded() == false}, the default): the vector is computed or fetched from
 *       the entity, e.g. from an embedding API, which must return the same vector for the same persisted
 *       entity.</li>
 * </ul>
 * The vectorizer is persisted with the index, by reference, so whatever it needs to reproduce a vector has to
 * be reachable from the persisted instance.
 *
 * @param <E> the type of the entity to be vectorized
 */
public abstract class Vectorizer<E>
{
    /**
     * The {@link #vectorKey(Object)} meaning "this vectorizer has no version key, compare by content".
     */
    public static final long NO_KEY = Long.MIN_VALUE;

    /**
     * Converts the given entity into its numerical vector representation.
     * <p>
     * Must reproduce the committed vector of a persisted entity, see the class comment: the index calls this
     * again for entities loaded from the storage whenever it has to repair its vector file.
     * <p>
     * The index is done with a computed vectorizer's result, or has copied it, before it calls the vectorizer
     * again, so the result may be a buffer the vectorizer reuses. An
     * {@linkplain #isEmbedded() embedded} vectorizer returns the entity's own array, which is not copied. The
     * index calls this concurrently, though: a search by entity
     * ({@link VectorIndex#search(Object, int)}) runs alongside mutations of the map and other searches. A
     * reused buffer must therefore be one per thread, e.g. held in a {@link ThreadLocal}; a single shared
     * buffer lets one call overwrite the vector another call is still returning.
     *
     * @param entity the entity of type E to be vectorized
     * @return a float array representing the numerical vector of the entity
     */
    public abstract float[] vectorize(E entity);

    /**
     * Returns a version key of the entity's vector, or {@link #NO_KEY} to compare vectors by content.
     * <p>
     * The index keeps the key with the vector. An update whose entity has the same key as the one indexed is
     * taken to leave the vector unchanged, and {@link #vectorize(Object)} is not called at all; without a key
     * the new vector is computed and compared with the indexed one. Equal vectors cost an update no graph work
     * either way, but a key also saves the vectorizer call, which matters when it is expensive.
     * <p>
     * A key must change whenever the vector changes, and must be cheap: it is asked for on every add and
     * update. A client whose vectors are versioned records can return the version or record id.
     * {@link #NO_KEY} itself is never a key.
     *
     * @param entity the entity
     * @return the key of its vector, or {@link #NO_KEY}
     */
    public long vectorKey(final E entity)
    {
        return NO_KEY;
    }

    /**
     * Converts a list of entities into their respective numerical vector representations.
     * <p>
     * This default implementation delegates to {@link #vectorize(Object)} for each entity. For a computed
     * vectorizer it copies each result, so a {@code vectorize} that reuses one buffer for every call still yields a
     * vector per entity; the vectors of an {@linkplain #isEmbedded() embedded} one are the entities' own arrays,
     * which a copy of a whole batch would only duplicate.
     * <p>
     * If you want to implement an optimized custom batch vectorization logic, override this method.
     * <p>
     * The returned list must have exactly the same size as {@code entities}, with positional
     * correspondence (element <i>i</i> is the vector of entity <i>i</i>). When
     * {@link #allowsNullVectors()} returns {@code true}, individual elements may be {@code null}
     * (meaning the corresponding entity has no embedding); otherwise every element must be non-null.
     * Each element must hold its entity's vector when the list is returned: the index copies the vectors
     * only then, so an implementation must not reuse one array for the vectors of several entities.
     *
     * @param entities a list of entities of type E to be vectorized
     * @return a list of float arrays, where each array represents the numerical vector of the corresponding entity
     */
    public List<float[]> vectorizeAll(final List<? extends E> entities)
    {
        final boolean       copy    = !this.isEmbedded();
        final List<float[]> vectors = new ArrayList<>(entities.size());
        for(final E entity : entities)
        {
            final float[] vector = this.vectorize(entity);
            vectors.add(copy && vector != null ? vector.clone() : vector);
        }
        return vectors;
    }

    /**
     * Returns whether this vectorizer extracts vectors already embedded in entities, rather than computing or
     * fetching them externally.
     * <p>
     * The index keeps every vector in its vector file and scores the same way in both modes. The mode decides
     * whether an in-memory index with a configured directory or location keeps its vector file across restarts
     * (only a computed one does, see {@link VectorIndex#keepsIndexFiles()}), and how a reindex collects the
     * vectors. Either way {@link #vectorize(Object)} must reproduce the committed vector of a persisted entity
     * (see the class comment).
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
     * the vector index's HNSW graph and never appears in vector search results. Its slot in the
     * vector file stays empty, and {@link VectorIndex#getVector(long)} returns
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
