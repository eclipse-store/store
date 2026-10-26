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

import org.eclipse.store.gigamap.jvector.Vectorizer;

/**
 * Embedded mode: the entity holds its vector. Persisted by name with the index.
 */
public class MigrationEmbeddedVectorizer extends Vectorizer<MigrationDoc>
{
    @Override
    public float[] vectorize(final MigrationDoc entity)
    {
        return entity.vector;
    }

    @Override
    public boolean isEmbedded()
    {
        return true;
    }
}
