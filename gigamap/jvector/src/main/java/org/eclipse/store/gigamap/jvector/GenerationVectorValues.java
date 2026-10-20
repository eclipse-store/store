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
import io.github.jbellis.jvector.vector.types.VectorTypeSupport;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The vectors one in-memory graph generation scores and is written with: the vector file, and, once the
 * generation is frozen for a flush, the vectors its nodes had at the freeze where a later mutation has changed
 * the file since.
 * <p>
 * A flush writes the frozen graph, cleaned up and with its inline vectors, under the witnesses sampled at the
 * freeze. A mutation after the freeze rewrites the vector file at once, so read straight from the file the
 * segment would hold the newer vector, or the placeholder of a removed one, for an entity whose committed state
 * the witnesses still describe. So a mutation of a node a frozen, unwritten generation holds first hands the
 * node's freeze-time vector to the generation ({@link #preserve(int, float[])}), copy-on-write: only the entities
 * mutated while a flush is in flight cost heap, and only until it is written.
 * <p>
 * Thread-safety: {@link #preserve(int, float[])} runs on the writer, under the parent monitor, before it rewrites
 * the slot; readers read the file first and the preserved vectors second, so a reader that sees the rewritten slot
 * also sees the preserved vector that overrides it.
 */
final class GenerationVectorValues extends MappedVectorValues
{
    // Stands for "had no vector at the freeze", since the map takes no null.
    private static final float[] ABSENT = new float[0];

    // Null while the generation is live: it reads the file only.
    private volatile ConcurrentHashMap<Integer, float[]> preserved;

    GenerationVectorValues(
        final VectorFile        file             ,
        final VectorTypeSupport vectorTypeSupport
    )
    {
        super(file, vectorTypeSupport);
    }

    /**
     * Marks the generation frozen, starting with the vectors the freeze copied for the operations it moved into
     * the generation, {@code null} for an ordinal that had none. Under the locks of the freeze.
     *
     * @param moved the vectors of the moved operations
     */
    void freeze(final Map<Integer, float[]> moved)
    {
        final ConcurrentHashMap<Integer, float[]> map = new ConcurrentHashMap<>();
        moved.forEach((ordinal, vector) -> map.put(ordinal, vector != null ? vector : ABSENT));
        this.preserved = map;
    }

    /**
     * @return whether the generation is frozen
     */
    boolean isFrozen()
    {
        return this.preserved != null;
    }

    /**
     * Keeps the freeze-time vector of an ordinal about to be rewritten, unless one is kept already. On the writer,
     * before the rewrite. Does nothing for a live generation.
     *
     * @param ordinal the ordinal
     * @param vector  its vector before the rewrite, or {@code null} if it had none
     */
    void preserve(
        final int     ordinal,
        final float[] vector
    )
    {
        final ConcurrentHashMap<Integer, float[]> map = this.preserved;
        if(map != null)
        {
            map.putIfAbsent(ordinal, vector != null ? vector : ABSENT);
        }
    }

    /**
     * Returns the vector of an ordinal for this generation: the preserved one if a mutation changed it since the
     * freeze, the file's otherwise.
     *
     * @param ordinal the ordinal
     * @return the vector, or {@code null} if it has none
     */
    float[] read(final int ordinal)
    {
        // The file first: see the class comment for why the order matters.
        final float[]                              current = this.file.read(ordinal);
        final ConcurrentHashMap<Integer, float[]> map     = this.preserved;
        final float[]                              kept    = map != null ? map.get(ordinal) : null;
        if(kept == null)
        {
            return current;
        }
        return kept == ABSENT ? null : kept;
    }

    // size() is the file's bound: preserved ordinals were written to the file before, so it covers them.

    @Override
    public VectorFloat<?> getVector(final int ordinal)
    {
        if(this.preserved == null)
        {
            return super.getVector(ordinal);
        }
        final float[] vector = this.read(ordinal);
        return vector == null ? null : this.vectorTypeSupport.createFloatVector(vector);
    }

    @Override
    boolean readInto(
        final int     ordinal,
        final float[] target ,
        final int     offset
    )
    {
        if(this.preserved == null)
        {
            return super.readInto(ordinal, target, offset);
        }
        final float[] vector = this.read(ordinal);
        if(vector == null)
        {
            return false;
        }
        System.arraycopy(vector, 0, target, offset, vector.length);
        return true;
    }
}
