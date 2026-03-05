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

import org.eclipse.store.gigamap.types.BinaryIndexerLong;
import org.eclipse.store.gigamap.types.Condition;

import java.util.Arrays;
import java.util.Objects;

final class VectorEntry
{
    /*
     * Indexer for source entity IDs.
     */
    final static BinaryIndexerLong<VectorEntry> SOURCE_ENTITY_ID_INDEXER = new BinaryIndexerLong.Abstract<>()
    {
        @Override
        public String name()
        {
            return "sourceEntityId";
        }

        @Override
        protected Long getLong(final VectorEntry entry)
        {
            return entry.sourceEntityId;
        }
    };


    final long    sourceEntityId;
    final float[] vector        ;

    VectorEntry(final long sourceEntityId, final float[] vector)
    {
        this.sourceEntityId = sourceEntityId;
        this.vector         = vector        ;
    }

    @Override
    public boolean equals(final Object obj)
    {
        if (obj == this)
        {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass())
        {
            return false;
        }
        final VectorEntry that = (VectorEntry)obj;
        return this.sourceEntityId == that.sourceEntityId
            && Arrays.equals(this.vector, that.vector)
        ;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(this.sourceEntityId, Arrays.hashCode(this.vector));
    }

    @Override
    public String toString()
    {
        return "VectorEntry[" +
            "sourceEntityId=" + this.sourceEntityId + ", " +
            "vector=" + Arrays.toString(this.vector) + ']'
        ;
    }

}
