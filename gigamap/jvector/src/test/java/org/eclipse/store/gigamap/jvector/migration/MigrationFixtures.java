package org.eclipse.store.gigamap.jvector.migration;

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
 * Shared constants of the vector index migration fixtures. The same class, with the same name, exists in the
 * jvector module's test tree on the port branch, because the vectorizer and entity classes are persisted by name.
 */
public final class MigrationFixtures
{
    public static final int    COUNT     = 300;
    public static final int    DIMENSION = 8;
    public static final String INDEX     = "vectors";

    /**
     * The vector of entity {@code no}. Component 0 is the number itself, so every entity has a distinct vector
     * and is its own nearest neighbour under Euclidean distance.
     */
    public static float[] position(final int no)
    {
        return new float[]{
            no,
            no % 7,
            no * 0.5f,
            no % 3,
            (no * 7) % 11,
            (no * 13) % 17,
            no % 2,
            1.0f
        };
    }

    private MigrationFixtures()
    {
        // static only
    }
}
