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

import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.store.gigamap.jvector.Vectorizer;

/**
 * Computed mode: the vector is derived from the entity number. Counts its calls in a static, so the migration
 * test can assert that a migrated computed index never calls it. Persisted by name with the index.
 */
public class MigrationComputedVectorizer extends Vectorizer<MigrationDoc>
{
    public static final AtomicInteger CALLS = new AtomicInteger();

    @Override
    public float[] vectorize(final MigrationDoc entity)
    {
        CALLS.incrementAndGet();
        return MigrationFixtures.position(entity.no);
    }
}
